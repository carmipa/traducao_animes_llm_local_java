import { logNoConsole } from '../js/app.js';

export function initRemuxer() {
    const form = document.getElementById('form-remuxer');
    if (!form) return;

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        
        const entrada = document.getElementById('remuxer-videos').value.trim();
        const saida = document.getElementById('remuxer-legendas').value.trim();
        
        logNoConsole('console-remuxer', 'Solicitando remux de vídeos com legendas traduzidas...', 'info');
        logNoConsole('console-remuxer', `Pasta de Vídeos: ${entrada}`, 'info');
        if (saida) logNoConsole('console-remuxer', `Pasta de Legendas: ${saida}`, 'info');

        try {
            const res = await fetch('/api/remuxar', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ entrada, saida })
            });

            if (!res.ok) {
                const erroTexto = await res.text();
                throw new Error(erroTexto || 'Erro interno ao iniciar remuxer');
            }

            const data = await res.json();
            logNoConsole('console-remuxer', 'Processamento do remuxer iniciado com sucesso em segundo plano!', 'sucesso');
            if (data.mensagem) {
                logNoConsole('console-remuxer', data.mensagem, 'info');
            }

        } catch (err) {
            logNoConsole('console-remuxer', `Erro ao iniciar remuxer: ${err.message}`, 'erro');
        }
    });
}
