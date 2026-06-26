package org.traducao.projeto.legendasExtracao.domain;

public enum FormatoLegenda {
    ASS("ass", "ASS/SSA (SubStation Alpha)"),
    PGS("sup", "PGS (Presentation Graphic Stream)"),
    SRT("srt", "SRT (SubRip Text)");

    private final String extensaoSaida;
    private final String descricao;

    FormatoLegenda(String extensaoSaida, String descricao) {
        this.extensaoSaida = extensaoSaida;
        this.descricao = descricao;
    }

    public String getExtensaoSaida() {
        return extensaoSaida;
    }

    public String getDescricao() {
        return descricao;
    }

    public static FormatoLegenda fromString(String formatoStr) {
        if (formatoStr == null || formatoStr.isBlank()) return ASS;
        return switch (formatoStr.toUpperCase()) {
            case "PGS" -> PGS;
            case "SRT" -> SRT;
            default -> ASS;
        };
    }
}
