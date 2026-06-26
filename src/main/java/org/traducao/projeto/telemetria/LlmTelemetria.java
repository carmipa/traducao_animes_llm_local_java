package org.traducao.projeto.telemetria;

import java.util.List;

public record LlmTelemetria(
    String nomeEpisodio,
    String modeloLlm,
    Integer totalLinhas,
    Integer falasTraduzidas,
    Integer falasDoCache,
    Long tempoTotalMs,
    List<String> errosOcorridos
) {}
