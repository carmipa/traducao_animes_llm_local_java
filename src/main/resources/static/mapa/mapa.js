import { logNoConsole } from '../js/app.js';

export function initMapa() {
    const btnGerar = document.getElementById('btn-gerar-mapa');
    const btnCopiar = document.getElementById('btn-copiar-mapa');
    const viewer = document.getElementById('viewer-mapa');

    if (btnGerar) {
        btnGerar.addEventListener('click', async () => {
            if (viewer) viewer.textContent = 'Mapeando estrutura do projeto. Por favor, aguarde...';
            
            try {
                const res = await fetch('/api/mapa', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' }
                });

                if (!res.ok) {
                    const erro = await res.text();
                    throw new Error(erro || 'Erro ao gerar o mapa');
                }

                const data = await res.json();
                if (viewer) {
                    viewer.textContent = data.conteudo || 'Nenhum mapa retornado.';
                }
                
                logNoConsole('console-analise', 'Mapa do projeto atualizado com sucesso!', 'sucesso');
            } catch (err) {
                if (viewer) viewer.textContent = `Erro ao gerar mapa: ${err.message}`;
                logNoConsole('console-analise', `Erro ao gerar mapa do projeto: ${err.message}`, 'erro');
            }
        });
    }

    if (btnCopiar) {
        btnCopiar.addEventListener('click', () => {
            if (!viewer || viewer.textContent.startsWith('Estrutura não gerada') || viewer.textContent.startsWith('Mapeando estrutura')) {
                alert('Gere o mapa primeiro antes de copiar!');
                return;
            }

            navigator.clipboard.writeText(viewer.textContent)
                .then(() => {
                    const originalText = btnCopiar.textContent;
                    btnCopiar.textContent = 'Copiado!';
                    setTimeout(() => {
                        btnCopiar.textContent = originalText;
                    }, 2000);
                })
                .catch(err => {
                    alert('Erro ao copiar o mapa para a área de transferência: ' + err);
                });
        });
    }
}
