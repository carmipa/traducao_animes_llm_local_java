package org.traducao.projeto.legendasExtracao.presentation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.traducao.projeto.traducao.infrastructure.config.TradutorProperties;
import org.traducao.projeto.traducao.presentation.ui.AnsiCores;
import org.traducao.projeto.traducao.presentation.ui.ConsoleEntrada;
import org.traducao.projeto.traducao.presentation.ui.PastasExecucao;
import org.traducao.projeto.legendasExtracao.application.ExtrairLegendaUseCase;
import org.traducao.projeto.legendasExtracao.domain.FormatoLegenda;
import org.traducao.projeto.legendasExtracao.domain.RelatorioExtracao;
import org.traducao.projeto.legendasExtracao.presentation.ui.ConsoleExtratorLogger;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
@ConditionalOnProperty(name = "app.modo", havingValue = "EXTRAIR")
public class ExtratorCLI implements CommandLineRunner {

    private final ExtrairLegendaUseCase extrairLegendaUseCase;
    private final ConsoleExtratorLogger logger;
    private final PastasExecucao pastasExecucao;
    private final TradutorProperties propriedades;

    @Value("${app.extrator.formato:ASS}")
    private String formatoConfigurado;

    public ExtratorCLI(ExtrairLegendaUseCase extrairLegendaUseCase, ConsoleExtratorLogger logger,
                        PastasExecucao pastasExecucao, TradutorProperties propriedades) {
        this.extrairLegendaUseCase = extrairLegendaUseCase;
        this.logger = logger;
        this.pastasExecucao = pastasExecucao;
        this.propriedades = propriedades;
    }

    @Override
    public void run(String... args) {
        FormatoLegenda formato = FormatoLegenda.fromString(formatoConfigurado);
        logger.cabecalho("ESTEIRA DE EXTRAÇÃO INTELIGENTE DE SOFTSUBS: VÍDEO ➔ " + formato.name());

        if (propriedades.diretorioEntrada() == null || propriedades.diretorioEntrada().isBlank()) {
            logger.erro("Pasta de vídeos não configurada.");
            ConsoleEntrada.imprimirErroSaida();
            return;
        }
        pastasExecucao.configurar(propriedades.diretorioEntrada(), propriedades.diretorioSaida(), propriedades.diretorioCache(), propriedades);

        Path pastaVideos = pastasExecucao.diretorioEntrada();
        logger.info("Analisando vídeos em: " + pastaVideos);

        if (!Files.isDirectory(pastaVideos)) {
            logger.erro("Pasta de vídeos não existe ou não é um diretório: " + pastaVideos);
            return;
        }

        RelatorioExtracao relatorio = extrairLegendaUseCase.executar(pastaVideos, formato);

        logger.cabecalho("RELATÓRIO DE EXTRAÇÃO");
        System.out.printf("  Total de arquivos de vídeo detectados : %d%n", relatorio.getArquivosDetectados());
        System.out.printf("  Arquivos sem legendas %-3s         : %s%n", formato.name(),
            AnsiCores.colorir(String.valueOf(relatorio.getArquivosSemLegenda()), AnsiCores.YELLOW));
        System.out.printf("  Legendas extraídas com sucesso    : %s%n",
            AnsiCores.colorir(String.valueOf(relatorio.getLegendasExtraidas()), AnsiCores.GREEN));
        if (relatorio.getFalhasInesperadas() > 0) {
            System.out.printf("  Falhas de extração                : %s%n",
                AnsiCores.colorir(String.valueOf(relatorio.getFalhasInesperadas()), AnsiCores.RED));
        }
        System.out.println("=".repeat(80));
        logger.sucesso("Processamento finalizado!");
    }
}
