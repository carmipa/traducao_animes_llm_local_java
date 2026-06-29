package org.traducao.projeto.remuxer.domain;

import java.time.LocalDateTime;

public class RelatorioRemux {
    private LocalDateTime dataHoraInicio;
    private LocalDateTime dataHoraFim;
    private int mkvDetectados;
    private int mkvProcessadosSucesso;
    private int legendasPareadas;
    private int errosInfraestrutura;
    private int errosMkvmergeRuntime;
    private int errosPermissaoIo;
    private int errosInesperados;
    private int errosLegendaInvalida;
    private long bytesMkvGeradosTotal;
    private int arquivosIgnorados;

    public RelatorioRemux() {
        this.dataHoraInicio = LocalDateTime.now();
    }

    public void finalizar() {
        this.dataHoraFim = LocalDateTime.now();
    }

    public void registrarDeteccao(int totalMkv, int totalPareadas) {
        this.mkvDetectados = totalMkv;
        this.legendasPareadas = totalPareadas;
    }

    public void registrarIgnorado() {
        this.arquivosIgnorados++;
    }

    public void registrarSucesso(long bytes) {
        this.mkvProcessadosSucesso++;
        this.bytesMkvGeradosTotal += bytes;
    }

    public void registrarErroInfra() { this.errosInfraestrutura++; }
    public void registrarErroRuntime() { this.errosMkvmergeRuntime++; }
    public void registrarErroIo() { this.errosPermissaoIo++; }
    public void registrarErroInesperado() { this.errosInesperados++; }
    public void registrarErroLegendaInvalida() { this.errosLegendaInvalida++; }

    /** Soma de todas as categorias de erro registradas (infra + runtime + io + inesperado + legenda inválida). */
    public int getTotalErros() {
        return errosInfraestrutura + errosMkvmergeRuntime + errosPermissaoIo + errosInesperados + errosLegendaInvalida;
    }

    // Getters para relatório final
    public LocalDateTime getDataHoraInicio() { return dataHoraInicio; }
    public LocalDateTime getDataHoraFim() { return dataHoraFim; }
    public int getMkvDetectados() { return mkvDetectados; }
    public int getMkvProcessadosSucesso() { return mkvProcessadosSucesso; }
    public int getLegendasPareadas() { return legendasPareadas; }
    public int getErrosInfraestrutura() { return errosInfraestrutura; }
    public int getErrosMkvmergeRuntime() { return errosMkvmergeRuntime; }
    public int getErrosPermissaoIo() { return errosPermissaoIo; }
    public int getErrosInesperados() { return errosInesperados; }
    public int getErrosLegendaInvalida() { return errosLegendaInvalida; }
    public long getBytesMkvGeradosTotal() { return bytesMkvGeradosTotal; }
    public int getArquivosIgnorados() { return arquivosIgnorados; }
}
