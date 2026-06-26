import { logNoConsole } from '../js/app.js';

export function initAnalise() {
    const form = document.getElementById('form-analise');
    if (!form) return;

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        
        const entrada = document.getElementById('analise-entrada').value.trim();
        const saida = document.getElementById('analise-saida').value.trim();
        
        logNoConsole('console-analise', 'Solicitando auditoria de mídia...', 'info');
        logNoConsole('console-analise', `Diretório: ${entrada}`, 'info');
        if (saida) logNoConsole('console-analise', `Saída dos relatórios: ${saida}`, 'info');

        try {
            const res = await fetch('/api/analisar', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ entrada, saida })
            });

            if (!res.ok) {
                const erroTexto = await res.text();
                throw new Error(erroTexto || 'Erro interno no servidor');
            }

            const data = await res.json();
            logNoConsole('console-analise', 'Auditoria de mídia disparada com sucesso em segundo plano!', 'sucesso');
            
            if (data.mensagem) {
                logNoConsole('console-analise', data.mensagem, 'info');
            }

            // Inicia o monitoramento de progresso se necessário
            iniciarMonitoramentoProgresso();

        } catch (err) {
            logNoConsole('console-analise', `Erro ao iniciar auditoria: ${err.message}`, 'erro');
        }
    });
}

function iniciarMonitoramentoProgresso() {
    // Implementação futura do polling de status do analisador
    logNoConsole('console-analise', 'Acompanhando execução do analisador via SSE/Polling...', 'info');
}
