package org.traducao.projeto.legendasExtracao.domain;

public class RelatorioExtracao {
    private int arquivosDetectados = 0;
    private int legendasExtraidas = 0;
    private int arquivosSemLegenda = 0;
    private int falhasInesperadas = 0;
    
    private final FormatoLegenda formatoAlvo;

    public RelatorioExtracao(FormatoLegenda formatoAlvo) {
        this.formatoAlvo = formatoAlvo;
    }

    public void registrarDetectado() {
        this.arquivosDetectados++;
    }

    public void registrarExtraido() {
        this.legendasExtraidas++;
    }

    public void registrarSemLegenda() {
        this.arquivosSemLegenda++;
    }

    public void registrarFalha() {
        this.falhasInesperadas++;
    }

    public int getArquivosDetectados() { return arquivosDetectados; }
    public int getLegendasExtraidas() { return legendasExtraidas; }
    public int getArquivosSemLegenda() { return arquivosSemLegenda; }
    public int getFalhasInesperadas() { return falhasInesperadas; }
    public FormatoLegenda getFormatoAlvo() { return formatoAlvo; }
}
