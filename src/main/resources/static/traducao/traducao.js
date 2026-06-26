import { logNoConsole } from '../js/app.js';

export function initTraducao() {
    const form = document.getElementById('form-traducao');
    if (!form) return;

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        
        const entrada = document.getElementById('traducao-entrada').value.trim();
        const saida = document.getElementById('traducao-saida').value.trim();
        
        logNoConsole('console-traducao', 'Iniciando pipeline de tradução local via LLM...', 'info');
        logNoConsole('console-traducao', `Pasta Original: ${entrada}`, 'info');
        if (saida) logNoConsole('console-traducao', `Pasta de Saída: ${saida}`, 'info');

        try {
            const res = await fetch('/api/traduzir', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ entrada, saida })
            });

            if (!res.ok) {
                const erroTexto = await res.text();
                throw new Error(erroTexto || 'Erro interno ao iniciar tradução');
            }

            const data = await res.json();
            logNoConsole('console-traducao', 'Tradução iniciada com sucesso em segundo plano!', 'sucesso');
            if (data.mensagem) {
                logNoConsole('console-traducao', data.mensagem, 'info');
            }

            iniciarAcompanhamentoTraducao();

        } catch (err) {
            logNoConsole('console-traducao', `Erro ao iniciar tradução: ${err.message}`, 'erro');
        }
    });
}

function iniciarAcompanhamentoTraducao() {
    logNoConsole('console-traducao', 'Acompanhando execução do tradutor local...', 'info');
}
