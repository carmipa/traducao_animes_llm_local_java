export function initTelemetria() {
    const btnRefresh = document.getElementById('btn-refresh-telemetria');
    if (btnRefresh) {
        btnRefresh.addEventListener('click', carregarDadosTelemetria);
    }

    const btnExportar = document.getElementById('btn-exportar-telemetria');
    if (btnExportar) {
        btnExportar.addEventListener('click', () => {
            window.open('/api/telemetria/exportar', '_blank');
        });
    }
    
    // Atualiza automaticamente a cada 30 segundos se o painel estiver visível
    setInterval(() => {
        const panel = document.getElementById('panel-telemetria');
        if (panel && panel.classList.contains('active')) {
            carregarDadosTelemetria();
        }
    }, 30000);
}

async function carregarDadosTelemetria() {
    const episodiosVal = document.getElementById('t-episodios');
    const linhasVal = document.getElementById('t-linhas');
    const tempoLinhaVal = document.getElementById('t-tempo-linha');
    const tokensVal = document.getElementById('t-tokens');
    const cacheHitsVal = document.getElementById('t-cache-hits');
    const alucinacoesVal = document.getElementById('t-alucinacoes');
    const tableBody = document.getElementById('telemetria-table-body');

    try {
        const res = await fetch('/api/telemetria');
        if (!res.ok) throw new Error('Não foi possível obter dados de telemetria');

        const data = await res.json();

        // Atualiza os cards estatísticos
        if (episodiosVal) episodiosVal.textContent = data.totalEpisodios ?? 0;
        if (linhasVal) linhasVal.textContent = data.totalLinhas ?? 0;
        if (tempoLinhaVal) tempoLinhaVal.textContent = `${data.tempoMedioPorLinhaMs ?? 0} ms`;
        if (tokensVal) tokensVal.textContent = data.totalTokensEstimados ?? 0;
        if (cacheHitsVal) cacheHitsVal.textContent = data.totalCacheHits ?? 0;
        if (alucinacoesVal) alucinacoesVal.textContent = data.totalAlucinacoesCorrigidas ?? 0;

        // Atualiza a tabela detalhada
        if (tableBody) {
            if (data.historicoOperacoes && data.historicoOperacoes.length > 0) {
                tableBody.innerHTML = '';
                data.historicoOperacoes.forEach(op => {
                    const row = document.createElement('tr');

                    const tdOperacao = document.createElement('td');
                    const forte = document.createElement('strong');
                    forte.textContent = op.nomeOperacao ?? '-';
                    tdOperacao.appendChild(forte);

                    const tdDetalhe = document.createElement('td');
                    tdDetalhe.textContent = op.detalheOperacao || '-';

                    const tdDuracao = document.createElement('td');
                    tdDuracao.textContent = op.duracaoFormatada || '-';

                    const tdTaxa = document.createElement('td');
                    tdTaxa.textContent = op.taxaSucesso ? `${op.taxaSucesso}%` : '100%';

                    row.append(tdOperacao, tdDetalhe, tdDuracao, tdTaxa);
                    tableBody.appendChild(row);
                });
            } else {
                tableBody.innerHTML = `
                    <tr>
                        <td colspan="4" class="table-empty">Nenhuma métrica operacional registrada recentemente.</td>
                    </tr>
                `;
            }
        }

    } catch (err) {
        console.error('Erro ao atualizar telemetria:', err);
    }
}
