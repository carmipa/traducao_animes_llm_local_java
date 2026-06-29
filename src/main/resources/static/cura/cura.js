import { logNoConsole, mostrarAlerta } from '../js/app.js';

export function initCura() {
    const btnIniciarCura = document.getElementById('btn-iniciar-cura');
    const inputEntrada = document.getElementById('cura-entrada');

    if (!btnIniciarCura || !inputEntrada) return;

    btnIniciarCura.addEventListener('click', async () => {
        const diretorio = inputEntrada.value.trim();
        if (!diretorio) {
            mostrarAlerta('Informe a pasta do anime primeiro!', 'erro');
            return;
        }

        logNoConsole('console-cura', `Iniciando cura estrutural de tags para: ${diretorio}`, 'info');
        btnIniciarCura.disabled = true;

        try {
            const res = await fetch('/api/cura-tags', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ diretorio })
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
