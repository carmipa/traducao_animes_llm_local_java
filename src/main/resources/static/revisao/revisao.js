import { logNoConsole } from '../js/app.js';

const STORAGE_PASTA_PT = 'revisao.ultimaPastaPt';

let contextosCarregados = false;

function parecePastaCache(entrada) {
    const normalizada = entrada.replace(/\//g, '\\').toLowerCase();
    return normalizada.includes('\\cache\\')
        || normalizada.endsWith('\\cache')
        || normalizada.includes('.cache.json');
}

function pareceTextoDeConsole(valor) {
    return /Logs da Revis[aã]o|Corrigir via Scraping|Console limpo|\[\d{1,2}:\d{2}:\d{2}\]|Pasta PT:/i.test(valor);
}

function pareceCaminhoPasta(valor) {
    if (!valor || valor.length > 260) return false;
    if (pareceTextoDeConsole(valor)) return false;
    if (/[\r\n]/.test(valor)) return false;
    return /^([A-Za-z]:\\|\\\\|\/)/.test(valor.trim());
}

function extrairCaminhoWindows(valor) {
    const candidatos = valor.match(/(?:[A-Za-z]:\\(?:[^<>:"|?*\r\n\[\]]|\\.)+|\\\\[^<>:"|?*\r\n\[\]]+)/g);
    if (!candidatos || candidatos.length === 0) return null;
    return candidatos[candidatos.length - 1].replace(/[\s.]+$/, '');
}

function lerCaminhoDoInput(input, rotulo) {
    if (!(input instanceof HTMLInputElement)) {
        throw new Error(`Campo "${rotulo}" indisponível na interface. Recarregue a página.`);
    }

    let valor = input.value.trim();
    if (!valor) return '';

    if (!pareceCaminhoPasta(valor)) {
        const recuperado = extrairCaminhoWindows(valor);
        if (recuperado && pareceCaminhoPasta(recuperado)) {
            input.value = recuperado;
            logNoConsole(
                'console-revisao',
                `${rotulo}: caminho recuperado automaticamente (${recuperado})`,
                'aviso'
            );
            return recuperado;
        }

        throw new Error(
            `${rotulo} inválido. Informe apenas o caminho da pasta (ex.: E:\\animes\\DANMACHI\\legendas_ptbr). `
                + 'Evite colar logs ou textos da tela no campo.'
        );
    }

    return valor;
}

function lerPastasDoFormulario(inputPt, inputEn) {
    const entrada = lerCaminhoDoInput(inputPt, 'Pasta PT');
    if (!entrada) {
        throw new Error('Informe a pasta com as legendas traduzidas.');
    }

    let entradaEn = '';
    if (inputEn instanceof HTMLInputElement && inputEn.value.trim()) {
        entradaEn = lerCaminhoDoInput(inputEn, 'Pasta EN');
    }

    if (parecePastaCache(entrada) || (entradaEn && parecePastaCache(entradaEn))) {
        throw new Error(
            'Use pastas com arquivos .ass de legenda — não a pasta cache/ do projeto. '
                + 'Ex.: E:\\animes\\DANMACHI\\temporada_5\\legendas_extraidas_ass'
        );
    }

    sessionStorage.setItem(STORAGE_PASTA_PT, entrada);
    return { entrada, entradaEn };
}

async function enviarRevisao(endpoint, payload, mensagemInicio) {
    logNoConsole('console-revisao', mensagemInicio, 'info');
    logNoConsole('console-revisao', `Pasta PT: ${payload.entrada}`, 'info');
    if (payload.saida) {
        logNoConsole('console-revisao', `Pasta EN: ${payload.saida}`, 'info');
    }
    if (payload.contextoId) {
        logNoConsole('console-revisao', `Contexto: ${payload.contextoId}`, 'info');
    }

    const res = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });

    if (!res.ok) {
        const erroTexto = await res.text();
        let msg = 'Erro ao iniciar revisão';
        try {
            const parsed = JSON.parse(erroTexto);
            if (parsed.mensagem) msg = parsed.mensagem;
        } catch (ignored) {
            if (erroTexto) msg = erroTexto;
        }
        throw new Error(msg);
    }

    const data = await res.json();
    logNoConsole('console-revisao', 'Processamento iniciado no servidor!', 'sucesso');
    if (data.mensagem) {
        logNoConsole('console-revisao', data.mensagem, 'info');
    }
}

async function carregarContextos() {
    const select = document.getElementById('revisao-contexto');
    if (!select) return;

    try {
        const response = await fetch('/api/contextos');
        if (!response.ok) {
            throw new Error('Resposta HTTP ' + response.status);
        }

        const contextos = await response.json();
        if (!Array.isArray(contextos) || contextos.length === 0) {
            throw new Error('Nenhum contexto cadastrado no servidor.');
        }

        select.innerHTML = '';
        contextos.forEach(ctx => {
            const opt = document.createElement('option');
            opt.value = ctx.id;
            opt.textContent = ctx.nome;
            if (ctx.padrao) opt.selected = true;
            select.appendChild(opt);
        });

        contextosCarregados = true;
    } catch (err) {
        console.error('Erro ao carregar contextos de revisão:', err);
        select.innerHTML = '<option value="">Erro ao carregar — recarregue a página</option>';
        contextosCarregados = false;
    }
}

export function initRevisao() {
    const form = document.getElementById('form-revisao');
    const inputPt = document.getElementById('revisao-entrada');
    const inputEn = document.getElementById('revisao-entrada-en');
    const btnConcordancia = document.getElementById('btn-revisao-concordancia');
    const contextoSelect = document.getElementById('revisao-contexto');

    if (!form || !(inputPt instanceof HTMLInputElement)) return;

    carregarContextos();

    const ultimaPasta = sessionStorage.getItem(STORAGE_PASTA_PT);
    if (!inputPt.value.trim() && ultimaPasta) {
        inputPt.value = ultimaPasta;
    }

    if (btnConcordancia) {
        btnConcordancia.addEventListener('click', async () => {
            if (!contextosCarregados || !contextoSelect?.value) {
                logNoConsole(
                    'console-revisao',
                    'Aguarde o carregamento do contexto de lore ou recarregue a página.',
                    'erro'
                );
                return;
            }

            try {
                const { entrada, entradaEn } = lerPastasDoFormulario(inputPt, inputEn);
                const payload = {
                    entrada,
                    contextoId: contextoSelect.value
                };
                if (entradaEn) payload.saida = entradaEn;

                await enviarRevisao(
                    '/api/revisar-legendas-concordancia',
                    payload,
                    'Iniciando revisão de concordância PT-BR (LLM)...'
                );
            } catch (err) {
                logNoConsole('console-revisao', `Erro: ${err.message}`, 'erro');
            }
        });
    }

    form.addEventListener('submit', async (e) => {
        e.preventDefault();

        try {
            const { entrada, entradaEn } = lerPastasDoFormulario(inputPt, inputEn);
            const payload = { entrada };
            if (entradaEn) payload.saida = entradaEn;

            await enviarRevisao(
                '/api/revisar-legendas',
                payload,
                'Iniciando revisão via Google Tradutor...'
            );
        } catch (err) {
            logNoConsole('console-revisao', `Erro: ${err.message}`, 'erro');
        }
    });
}
