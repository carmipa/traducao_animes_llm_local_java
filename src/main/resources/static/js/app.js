/**
 * ==========================================================================
 * KRONOS CORE - ORQUESTRADOR GLOBAL FRONTEND (SPA & SSE STREAM LOGS)
 * ==========================================================================
 */

import { initAnalise } from '../analise/analise.js';
import { initExtracao } from '../extracao/extracao.js';
import { initTraducao } from '../traducao/traducao.js';
import { initCorrecao } from '../correcao/correcao.js';
import { initRevisao } from '../revisao/revisao.js';
import { initRemuxer } from '../remuxer/remuxer.js';
import { initMapa } from '../mapa/mapa.js';
import { initTelemetria } from '../telemetria/telemetria.js';

// Definições de Títulos e Subtítulos por seção do menu
const CONFIG_SECOES = {
    inicio: {
        titulo: "Painel Inicial",
        subtitulo: "Orquestrador automatizado e pipeline industrial de processamento de animes"
    },
    analise: {
        titulo: "1. Análise de Mídia",
        subtitulo: "Auditoria técnica de codecs, sincronia e taxas de bits de vídeos"
    },
    extracao: {
        titulo: "2. Extração de Legendas",
        subtitulo: "Extração industrial de faixas de legendas embutidas em MKVs"
    },
    traducao: {
        titulo: "3. Tradução Local via LLM",
        subtitulo: "Traduzir legendas originais em inglês usando inteligência artificial local"
    },
    correcao: {
        titulo: "4. Correção do Cache de Tradução",
        subtitulo: "Limpeza de inconsistências e preenchimento via raspagem do Google Tradutor"
    },
    revisao: {
        titulo: "6. Revisão de Legendas",
        subtitulo: "Concordância PT-BR via LLM local e correção de inglês residual via Google"
    },
    remuxer: {
        titulo: "7. Remuxer Industrial",
        subtitulo: "Junção de vídeos originais e novas legendas traduzidas em novos MKVs"
    },
    mapa: {
        titulo: "8. Mapeamento do Projeto",
        subtitulo: "Auditoria de taxonomia e visualização da árvore de estrutura do código"
    },
    telemetria: {
        titulo: "9. Painel de Telemetria",
        subtitulo: "Métricas de tokens, velocidade, hits de cache e logs em tempo real"
    }
};

document.addEventListener('DOMContentLoaded', () => {
    inicializarNavegacao();
    inicializarModulos();
    atualizarStatusConexao();
    buscarContadoresGlobais();
    conectarFluxoLugsSSE();
});

/**
 * Controla a troca de abas/painéis na sidebar e atualiza os títulos
 */
function inicializarNavegacao() {
    const botoesMenu = document.querySelectorAll('.nav-item');
    const paineis = document.querySelectorAll('.panel');
    const tituloPagina = document.getElementById('page-title');
    const subtituloPagina = document.getElementById('page-subtitle');

    botoesMenu.forEach(botao => {
        botao.addEventListener('click', () => {
            const target = botao.getAttribute('data-target');
            
            // 1. Atualizar classe ativa dos botões do menu
            botoesMenu.forEach(b => b.classList.remove('active'));
            botao.classList.add('active');

            // 2. Exibir painel correto
            paineis.forEach(painel => {
                painel.classList.remove('active');
                if (painel.id === `panel-${target}`) {
                    painel.classList.add('active');
                }
            });

            // 3. Atualizar títulos no cabeçalho
            if (CONFIG_SECOES[target]) {
                tituloPagina.textContent = CONFIG_SECOES[target].titulo;
                subtituloPagina.textContent = CONFIG_SECOES[target].subtitulo;
            }

            // Ações extras ao abrir painéis específicos
            if (target === 'telemetria') {
                document.getElementById('btn-refresh-telemetria').click();
            }
        });
    });
}

/**
 * Inicializa cada um dos módulos JavaScript específicos das pastas
 */
function inicializarModulos() {
    initAnalise();
    initExtracao();
    initTraducao();
    initCorrecao();
    initRevisao();
    initRemuxer();
    initMapa();
    initTelemetria();
}

/**
 * Conecta ao Server-Sent Events (SSE) para receber os logs do terminal em tempo real
 */
function conectarFluxoLugsSSE() {
    console.log('Iniciando escuta de Server-Sent Events (SSE) para logs...');
    const eventSource = new EventSource('/api/logs/stream');

    // O backend publica cada operação em segundo plano sob um canal SSE com
    // o próprio nome (ver LogStreamService#definirCanalAtual no servidor),
    // então a rota para o console certo é direta — não depende de qual aba
    // está aberta no navegador no momento em que a linha de log chega.
    const consoleMap = {
        'analise': 'console-analise',
        'extracao': 'console-extracao',
        'traducao': 'console-traducao',
        'correcao': 'console-correcao',
        'revisao': 'console-revisao',
        'remuxer': 'console-remuxer'
    };

    for (const [canal, consoleId] of Object.entries(consoleMap)) {
        eventSource.addEventListener(canal, (event) => {
            logNoConsoleFormatado(consoleId, event.data);
            verificarAlertaSSE(event.data);
        });
    }

    // Canal genérico de fallback, para qualquer log que não pertença a uma
    // operação específica das abas acima.
    eventSource.addEventListener('console', (event) => {
        const activeNav = document.querySelector('.nav-item.active');
        if (!activeNav) return;

        const target = activeNav.getAttribute('data-target');
        const consoleId = consoleMap[target];
        if (consoleId) {
            logNoConsoleFormatado(consoleId, event.data);
            verificarAlertaSSE(event.data);
        }
    });

    eventSource.addEventListener('sistema', (event) => {
        console.log('SSE Sistema:', event.data);
    });

    eventSource.onerror = (err) => {
        console.warn('Erro na conexão de stream SSE, tentando reconectar em 5s...', err);
        eventSource.close();
        setTimeout(conectarFluxoLugsSSE, 5000);
    };
}

/**
 * Verifica se o servidor Spring Boot está respondendo
 */
async function atualizarStatusConexao() {
    const indicador = document.querySelector('.status-indicator');
    const statusText = document.querySelector('.status-text');
    
    try {
        const res = await fetch('/api/status', { method: 'GET' });
        if (res.ok) {
            indicador.className = 'status-indicator online';
            statusText.textContent = 'Backend Online';
        } else {
            throw new Error();
        }
    } catch (e) {
        indicador.className = 'status-indicator offline';
        statusText.textContent = 'Backend Offline';
    }

    // Repete a verificação a cada 10 segundos
    setTimeout(atualizarStatusConexao, 10000);
}

/**
 * Carrega estatísticas rápidas no cabeçalho
 */
async function buscarContadoresGlobais() {
    try {
        const res = await fetch('/api/telemetria');
        if (res.ok) {
            const dados = await res.json();
            
            // Atualiza cabeçalho global
            const cacheCount = document.getElementById('stat-cache-count');
            if (cacheCount && dados.cacheCount !== undefined) {
                cacheCount.textContent = `${dados.cacheCount} Arquivos`;
            }
            
            // Atualiza widget da home (Painel Inicial)
            const dashCacheCount = document.getElementById('dashboard-cache-count');
            if (dashCacheCount && dados.cacheCount !== undefined) {
                dashCacheCount.textContent = `${dados.cacheCount} Arquivos`;
            }
        }
    } catch (e) {
        console.warn('Não foi possível obter os contadores globais da telemetria.');
        const cacheCount = document.getElementById('stat-cache-count');
        if (cacheCount) cacheCount.textContent = 'Indisponível';
        
        const dashCacheCount = document.getElementById('dashboard-cache-count');
        if (dashCacheCount) dashCacheCount.textContent = 'Indisponível';
    }
}

/**
 * Escapa caracteres especiais de HTML para impedir que conteúdo vindo do
 * backend (nomes de arquivo, mensagens de erro, texto raspado do Google
 * Translate) seja interpretado como markup/script ao cair no innerHTML.
 */
function escapeHtml(texto) {
    return texto
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

/**
 * Realiza o parse de códigos ANSI para tags HTML estilizadas
 */
function ansiParaHtml(texto) {
    let html = escapeHtml(texto);

    // Sanitização contra caracteres de controle de cursor
    html = html.replace(/\r?\033\[K/g, '');
    html = html.replace(/\r/g, '');
    
    // Substitui quebras de linha literais se houver
    html = html.replace(/\\n/g, '<br>');

    // Negritos e Resets
    html = html.replace(/\033\[1m/g, '<span style="font-weight: 700;">');
    html = html.replace(/\u001b\[1m/g, '<span style="font-weight: 700;">');
    html = html.replace(/\033\[0m/g, '</span>');
    html = html.replace(/\u001b\[0m/g, '</span>');

    // Mapeamento de Cores ANSI
    const cores = {
        '30': 'var(--text-muted)',
        '31': 'rgba(239, 68, 68, 0.95)', // Vermelho elegante
        '32': 'var(--accent-green)',
        '33': 'var(--accent-yellow)',
        '34': 'var(--accent-blue)',
        '35': 'var(--accent-purple)',
        '36': 'var(--accent-cyan)',
        '37': 'var(--text-primary)',
        '90': 'var(--text-muted)'
    };

    for (let code in cores) {
        const regex1 = new RegExp('\\033\\[' + code + 'm', 'g');
        const regex2 = new RegExp('\\u001b\\[' + code + 'm', 'g');
        const replacement = `<span style="color: ${cores[code]};">`;
        html = html.replace(regex1, replacement);
        html = html.replace(regex2, replacement);
    }

    return html;
}

/**
 * Auxiliar para formatar e exibir mensagens nos painéis de console (Padrão SSE)
 */
function logNoConsoleFormatado(consoleId, rawMessage) {
    const consoleDiv = document.getElementById(consoleId);
    if (!consoleDiv) return;

    // Remove mensagem "Aguardando..." se existir
    const sysMsg = consoleDiv.querySelector('.system-message');
    if (sysMsg) {
        consoleDiv.removeChild(sysMsg);
    }

    const timestamp = new Date().toLocaleTimeString();
    const htmlMensagem = ansiParaHtml(rawMessage);
    
    const linhaLog = document.createElement('div');
    linhaLog.className = 'log-line';
    linhaLog.innerHTML = `<span style="color: var(--text-muted); font-size: 0.75rem;">[${timestamp}]</span> ${htmlMensagem}`;
    
    consoleDiv.appendChild(linhaLog);
    
    // Proteção extrema contra travamentos: Limita a 1000 linhas visíveis no console HTML
    while (consoleDiv.childElementCount > 1000) {
        consoleDiv.removeChild(consoleDiv.firstChild);
    }

    consoleDiv.scrollTop = consoleDiv.scrollHeight;
}

/**
 * Método genérico clássico para logs manuais do frontend
 */
export function logNoConsole(consoleId, mensagem, tipo = 'info') {
    let corAnsi = '\u001b[37m'; // Branco padrão
    if (tipo === 'erro') corAnsi = '\u001b[31m';
    if (tipo === 'aviso') corAnsi = '\u001b[33m';
    if (tipo === 'sucesso') corAnsi = '\u001b[32m';
    if (tipo === 'info') corAnsi = '\u001b[36m'; // Ciano para comandos do sistema

    logNoConsoleFormatado(consoleId, `${corAnsi}${mensagem}\u001b[0m`);
}

// Configura funcionalidade de limpar os consoles
document.querySelectorAll('.btn-clear-console').forEach(btn => {
    btn.addEventListener('click', () => {
        const consoleId = btn.getAttribute('data-target');
        const consoleDiv = document.getElementById(consoleId);
        if (consoleDiv) {
            consoleDiv.innerHTML = '<div class="system-message">Console limpo. Aguardando novos logs...</div>';
        }
    });
});

/**
 * Exibe um alerta flutuante (Toast) na tela
 */
export function mostrarAlerta(mensagem, tipo = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `toast toast-${tipo}`;

    let icon = 'ℹ️';
    if (tipo === 'erro') icon = '❌';
    if (tipo === 'sucesso') icon = '✅';
    if (tipo === 'aviso') icon = '⚠️';

    toast.innerHTML = `
        <div class="toast-content">
            <strong>${icon}</strong> &nbsp; ${escapeHtml(mensagem)}
        </div>
        <button class="toast-close" title="Fechar">&times;</button>
    `;

    container.appendChild(toast);
    
    // Força reflow para animação
    toast.offsetHeight;
    toast.classList.add('show');

    // Auto-destruir após 7 segundos
    const timeout = setTimeout(() => fecharToast(toast), 7000);

    toast.querySelector('.toast-close').addEventListener('click', () => {
        clearTimeout(timeout);
        fecharToast(toast);
    });
}

function fecharToast(toast) {
    toast.classList.remove('show');
    toast.classList.add('hiding');
    toast.addEventListener('transitionend', () => {
        if (toast.parentNode) {
            toast.parentNode.removeChild(toast);
        }
    });
}

/**
 * Analisa as mensagens recebidas via SSE e dispara alertas caso sejam de erro fatal ou sucesso
 */
export function verificarAlertaSSE(mensagem) {
    // Remove códigos ANSI limpos para exibir no Toast
    const msgLimpa = mensagem.replace(/\u001B\[[0-9;]*m/g, '').replace(/\033\[[0-9;]*m/g, '').trim();
    
    if (msgLimpa.includes('[ERRO]') || msgLimpa.includes('[FAIL]')) {
        const erroMsg = msgLimpa.replace(/\[ERRO\]|\[FAIL\]/, '').trim();
        mostrarAlerta(erroMsg, 'erro');
    } 
    else if (msgLimpa.includes('PROCESSAMENTO FINALIZADO') || msgLimpa.includes('[SUCESSO]')) {
        mostrarAlerta(msgLimpa, 'sucesso');
        const btnRefresh = document.getElementById('btn-refresh-telemetria');
        if (btnRefresh) btnRefresh.click();
    }
}

/**
 * Monitora inputs de caminho/pasta e selects de contexto para carregar dinamicamente os metadados e capas de animes
 */
function inicializarMetadadosDinamicos() {
    const mapeamentoFormularios = [
        { inputId: 'analise-entrada', selectId: 'analise-contexto', bannerId: 'meta-banner-analise' },
        { inputId: 'traducao-entrada', selectId: 'traducao-contexto', bannerId: 'meta-banner-traducao' },
        { inputId: 'correcao-entrada', selectId: 'correcao-contexto', bannerId: 'meta-banner-correcao' },
        { inputId: 'revisao-entrada', selectId: 'revisao-contexto', bannerId: 'meta-banner-revisao' }
    ];

    // Popula automaticamente os selects secundários (análise e correção) se necessário
    carregarContextosAuxiliares(['analise-contexto', 'correcao-contexto']);

    mapeamentoFormularios.forEach(item => {
        const input = document.getElementById(item.inputId);
        const select = item.selectId ? document.getElementById(item.selectId) : null;

        const atualizar = () => {
            let termo = '';
            if (input && input.value.trim().length > 3) {
                termo = input.value.trim();
            } else if (select && select.value && select.selectedIndex >= 0) {
                const optText = select.options[select.selectedIndex].text;
                if (optText && !optText.includes('Carregando')) {
                    termo = optText;
                }
            }

            if (termo && termo.length > 2) {
                carregarMetadataAnime(termo, item.bannerId);
            }
        };

        if (input) {
            input.addEventListener('change', atualizar);
            input.addEventListener('blur', atualizar);
        }

        if (select) {
            select.addEventListener('change', atualizar);
            // Também dispara quando o select for preenchido via API
            const observer = new MutationObserver(() => {
                setTimeout(atualizar, 300);
            });
            observer.observe(select, { childList: true });
        }
    });
}

async function carregarContextosAuxiliares(idsSelects) {
    try {
        const response = await fetch('/api/contextos');
        if (!response.ok) return;
        const contextos = await response.json();
        if (!Array.isArray(contextos) || contextos.length === 0) return;

        idsSelects.forEach(id => {
            const select = document.getElementById(id);
            if (!select) return;

            select.innerHTML = '<option value="" selected>-- Selecione uma obra para visualizar --</option>';
            contextos.forEach(ctx => {
                const opt = document.createElement('option');
                opt.value = ctx.id;
                opt.textContent = ctx.nome;
                select.appendChild(opt);
            });
        });
    } catch (e) {
        console.warn('Falha ao carregar contextos auxiliares:', e);
    }
}

async function carregarMetadataAnime(caminho, bannerId) {
    const banner = document.getElementById(bannerId);
    if (!banner) return;

    try {
        const resp = await fetch(`/api/metadata?caminho=${encodeURIComponent(caminho)}`);
        if (!resp.ok) {
            banner.classList.add('hidden');
            return;
        }

        const meta = await resp.json();
        renderizarBannerMetadata(banner, meta);
    } catch (e) {
        console.warn('Erro ao carregar metadata:', e);
        banner.classList.add('hidden');
    }
}

function renderizarBannerMetadata(banner, meta) {
    if (!meta || !meta.titulo) {
        banner.classList.add('hidden');
        return;
    }

    const posterHtml = meta.posterUrl 
        ? `<div class="meta-poster-container"><img src="${escapeHtml(meta.posterUrl)}" alt="${escapeHtml(meta.titulo)}" class="meta-poster-img" onerror="this.src='img/kronos_logo.png'"></div>`
        : '';

    const scoreHtml = meta.score ? `<span class="meta-badge score">⭐ ${meta.score}</span>` : '';
    const anoHtml = meta.ano ? `<span class="meta-badge">📅 ${meta.ano}</span>` : '';
    const epsHtml = meta.episodios ? `<span class="meta-badge">📺 ${meta.episodios} eps</span>` : '';
    const subTitle = meta.tituloJapones || meta.tituloIngles || '';

    let generosHtml = '';
    if (meta.generos && meta.generos.length > 0) {
        generosHtml = meta.generos.slice(0, 3).map(g => `<span class="meta-badge genre">${escapeHtml(g)}</span>`).join('');
    }

    banner.innerHTML = `
        ${posterHtml}
        <div class="meta-info-container">
            <div class="meta-header-titles">
                <div class="meta-title-main">${escapeHtml(meta.titulo)}</div>
                ${subTitle ? `<div class="meta-title-sub">${escapeHtml(subTitle)}</div>` : ''}
            </div>
            <div class="meta-badges-row">
                ${scoreHtml}
                ${anoHtml}
                ${epsHtml}
                ${generosHtml}
            </div>
            ${meta.sinopse ? `<div class="meta-synopsis">${escapeHtml(meta.sinopse)}</div>` : ''}
        </div>
    `;

    banner.classList.remove('hidden');
}

// Inicializa no carregamento do DOM
document.addEventListener('DOMContentLoaded', inicializarMetadadosDinamicos);

