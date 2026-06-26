import { logNoConsole } from '../js/app.js';

export function initCorrecao() {
    const btnLimpar = document.getElementById('btn-limpar-cache');
    const btnScraping = document.getElementById('btn-scraping-google');
    
    if (btnLimpar) {
        btnLimpar.addEventListener('click', async () => {
            const entrada = document.getElementById('correcao-entrada').value.trim();
            logNoConsole('console-correcao', 'Disparando limpeza de cache de tradução...', 'info');
            if (entrada) logNoConsole('console-correcao', `Pasta de Cache: ${entrada}`, 'info');

            try {
                const res = await fetch('/api/corrigir-cache', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ entrada })
                });

                if (!res.ok) {
                    const erro = await res.text();
                    throw new Error(erro || 'Erro ao limpar cache');
                }

                const data = await res.json();
                logNoConsole('console-correcao', 'Limpeza de cache executada com sucesso!', 'sucesso');
                if (data.mensagem) {
                    logNoConsole('console-correcao', data.mensagem, 'info');
                }
            } catch (err) {
                logNoConsole('console-correcao', `Erro na limpeza: ${err.message}`, 'erro');
            }
        });
    }

    if (btnScraping) {
        btnScraping.addEventListener('click', async () => {
            const entrada = document.getElementById('correcao-entrada').value.trim();
            logNoConsole('console-correcao', 'Disparando corretor via Scraping Google Tradutor...', 'info');
            if (entrada) logNoConsole('console-correcao', `Pasta de Cache: ${entrada}`, 'info');

            try {
                const res = await fetch('/api/corrigir-scraping', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ entrada })
                });

                if (!res.ok) {
                    const erro = await res.text();
                    throw new Error(erro || 'Erro no scraping de correção');
                }

                const data = await res.json();
                logNoConsole('console-correcao', 'Processamento de raspagem de correção iniciado!', 'sucesso');
                if (data.mensagem) {
                    logNoConsole('console-correcao', data.mensagem, 'info');
                }
            } catch (err) {
                logNoConsole('console-correcao', `Erro no scraping: ${err.message}`, 'erro');
            }
        });
    }
}
