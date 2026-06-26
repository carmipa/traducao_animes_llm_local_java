package org.traducao.projeto.analisadorMidia.domain;

public record LegendaInfo(
    Integer index,
    Integer indexRelativo,
    String idioma,
    String formato,
    String codecId,
    String titulo,
    String tipoCompleto,
    String tipoCurto,
    Double duracaoMetadadosSegundos,
    Double duracaoPacotesSegundos,
    String metodoDuracao,
    Double duracaoEfetivaSegundos,
    Double diferencaFimSegundos,
    Double driftRatio,
    String veredicto
) {}
