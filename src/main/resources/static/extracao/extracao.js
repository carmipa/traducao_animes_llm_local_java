import { logNoConsole } from '../js/app.js';

export function initExtracao() {
    const form = document.getElementById('form-extracao');
    if (!form) return;

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        
        const entrada = document.getElementById('extracao-entrada').value.trim();
        const formato = document.getElementById('extracao-formato').value;
        
        logNoConsole('console-extracao', `Solicitando extração de legendas no formato [${formato}]...`, 'info');
        logNoConsole('console-extracao', `Diretório: ${entrada}`, 'info');

        try {
            const res = await fetch('/api/extrair', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ entrada, formato })
            });

            if (!res.ok) {
                const erroTexto = await res.text();
                throw new Error(erroTexto || 'Erro interno ao iniciar extração');
            }

            const data = await res.json();
            logNoConsole('console-extracao', 'Extração de legendas iniciada com sucesso em segundo plano!', 'sucesso');
            if (data.mensagem) {
                logNoConsole('console-extracao', data.mensagem, 'info');
            }

        } catch (err) {
            logNoConsole('console-extracao', `Erro ao iniciar extração: ${err.message}`, 'erro');
        }
    });
}
