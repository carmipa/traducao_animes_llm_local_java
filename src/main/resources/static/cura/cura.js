import { logNoConsole, mostrarAlerta } from '../js/app.js';

export function initCura() {
    const btnIniciarCura = document.getElementById('btn-iniciar-cura');
    const inputOriginal = document.getElementById('cura-entrada-original');
    const inputTraduzida = document.getElementById('cura-entrada-traduzida');
    const selectContexto = document.getElementById('cura-contexto');

    if (!btnIniciarCura || !inputOriginal || !inputTraduzida) return;

    btnIniciarCura.addEventListener('click', async () => {
        const diretorioOriginal = inputOriginal.value.trim();
        const diretorioTraduzido = inputTraduzida.value.trim();
        if (!diretorioOriginal || !diretorioTraduzido) {
            mostrarAlerta('Informe as pastas com as legendas originais e traduzidas!', 'erro');
            return;
        }

        const contextoId = selectContexto ? selectContexto.value : '';

        logNoConsole('console-cura', `Iniciando cura estrutural de tags. Original: ${diretorioOriginal} | Traduzida: ${diretorioTraduzido}`, 'info');
        if (contextoId) {
            logNoConsole('console-cura', `Contexto LLM ativo: ${contextoId} (também corrige erros de tradução)`, 'info');
        }
        btnIniciarCura.disabled = true;

        try {
            const body = { diretorioOriginal, diretorioTraduzido };
            if (contextoId) body.contextoId = contextoId;

            const res = await fetch('/api/cura-tags', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            });

            if (!res.ok) {
                const erro = await res.json();
                throw new Error(erro.erro || 'Falha desconhecida no servidor');
            }

            const data = await res.json();
            logNoConsole('console-cura', data.mensagem || 'Cura concluída.', 'sucesso');
            mostrarAlerta('Cura de legendas finalizada!', 'sucesso');

        } catch (err) {
            logNoConsole('console-cura', `Erro durante a cura: ${err.message}`, 'erro');
            mostrarAlerta('Erro ao curar tags.', 'erro');
        } finally {
            btnIniciarCura.disabled = false;
        }
    });
}
