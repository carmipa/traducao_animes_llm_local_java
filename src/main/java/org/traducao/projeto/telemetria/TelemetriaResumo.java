package org.traducao.projeto.telemetria;

import java.util.List;

/**
 * Resumo serializável da telemetria acumulada na sessão atual do servidor,
 * consumido pelo painel "Telemetria" da interface web.
 */
public record TelemetriaResumo(
    int cacheCount,
    int totalEpisodios,
    int totalLinhas,
    long tempoMedioPorLinhaMs,
    int totalCacheHits,
    List<OperacaoHistorico> historicoOperacoes
) {}
