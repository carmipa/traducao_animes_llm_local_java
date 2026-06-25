package org.traducao.projeto.animes.presentation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.traducao.projeto.animes.application.ProcessarArquivoUseCase;
import org.traducao.projeto.animes.domain.exceptions.TradutorException;
import org.traducao.projeto.animes.domain.exceptions.TraducaoParcialException;
import org.traducao.projeto.animes.infrastructure.config.TradutorProperties;
import org.traducao.projeto.animes.presentation.ui.ConsoleEntrada;
import org.traducao.projeto.animes.presentation.ui.ConsoleUILogger;
import org.traducao.projeto.animes.presentation.ui.PastasExecucao;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Ponto de entrada da CLI: varre a pasta de entrada por arquivos .ass/.ssa
 * e traduz cada um sequencialmente.
 * <p>
 * Se {@code tradutor.diretorio-entrada} estiver vazio, o {@link Application#main}
 * pede os caminhos via {@link ConsoleEntrada}
 * antes do Spring subir.
 * <p>
 * Arquivos são processados um por vez de propósito: todos compartilham o
 * mesmo LLM local (GPU única). Lotes dentro de cada episódio também são
 * sequenciais (ver {@code ProcessarEpisodioUseCase}).
 */
@Component
@ConditionalOnProperty(name = "app.modo", havingValue = "TRADUZIR", matchIfMissing = true)
public class TradutorCLI implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TradutorCLI.class);
    private static final Set<String> EXTENSOES_SUPORTADAS = Set.of(".ass", ".ssa");

    private final ProcessarArquivoUseCase processarArquivoUseCase;
    private final ConsoleUILogger uiLogger;
    private final TradutorProperties propriedades;
    private final PastasExecucao pastasExecucao;

    public TradutorCLI(
        ProcessarArquivoUseCase processarArquivoUseCase,
        ConsoleUILogger uiLogger,
        TradutorProperties propriedades,
        PastasExecucao pastasExecucao
    ) {
        this.processarArquivoUseCase = processarArquivoUseCase;
        this.uiLogger = uiLogger;
        this.propriedades = propriedades;
        this.pastasExecucao = pastasExecucao;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!resolverPastas()) {
            return;
        }

        uiLogger.log("Iniciando Tradutor Local...");

        Path diretorioEntrada = pastasExecucao.diretorioEntrada();
        if (!Files.isDirectory(diretorioEntrada)) {
            log.error("Pasta de entrada não existe ou não é um diretório: {}", diretorioEntrada);
            uiLogger.log("❌ Pasta de entrada não existe ou não é um diretório: " + diretorioEntrada);
            return;
        }

        List<Path> arquivos;
        try (Stream<Path> stream = Files.list(diretorioEntrada)) {
            arquivos = stream
                .filter(Files::isRegularFile)
                .filter(this::temExtensaoSuportada)
                .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                .toList();
        } catch (IOException e) {
            log.error("Falha ao listar arquivos em {}", diretorioEntrada, e);
            uiLogger.log("❌ Falha ao listar arquivos em " + diretorioEntrada + ": " + e.getMessage());
            return;
        }

        if (arquivos.isEmpty()) {
            log.warn("Nenhum arquivo .ass/.ssa encontrado em {}", diretorioEntrada);
            uiLogger.log("Nenhum arquivo .ass/.ssa encontrado em " + diretorioEntrada);
            return;
        }

        log.info("{} arquivo(s) de legenda encontrado(s) em {}", arquivos.size(), diretorioEntrada);
        uiLogger.log(arquivos.size() + " arquivo(s) encontrado(s). Iniciando tradução...");

        int sucesso = 0;
        int falha = 0;
        for (Path arquivo : arquivos) {
            uiLogger.log("[ INFO ] Processando " + arquivo.getFileName() + "...");
            try {
                processarArquivoUseCase.processar(arquivo);
                uiLogger.log("[ OK ] " + arquivo.getFileName() + " traduzido com sucesso.");
                sucesso++;
            } catch (TraducaoParcialException e) {
                int salvas = e.getDicionarioParcial() != null ? e.getDicionarioParcial().size() : 0;
                log.warn("Processamento parcial em {}: {} traduções salvas. {}", arquivo.getFileName(), salvas, e.getMessage());
                uiLogger.log("[ PARCIAL ] " + arquivo.getFileName() + " (Salvas: " + salvas + " antes de abortar)");
                falha++;
            } catch (TradutorException e) {
                log.error("Falha crítica ao processar {}: {}", arquivo.getFileName(), e.getMessage());
                uiLogger.log("[ FAIL ] Falha em " + arquivo.getFileName() + ": " + e.getMessage());
                falha++;
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                if (e.getCause() instanceof TradutorException te) {
                    errorMsg = te.getMessage();
                }
                log.error("Erro ao processar {}: {}", arquivo.getFileName(), errorMsg);
                uiLogger.log("[ FAIL ] Erro em " + arquivo.getFileName() + " (" + errorMsg + ")");
                falha++;
            }
        }

        log.info("Processamento finalizado: {} sucesso(s), {} falha(s) de {} arquivo(s)", sucesso, falha, arquivos.size());
        uiLogger.log(String.format("Concluido: %d sucesso(s), %d falha(s) de %d arquivo(s).", sucesso, falha, arquivos.size()));
    }

    private boolean resolverPastas() {
        String entrada = propriedades.diretorioEntrada();
        String saida = propriedades.diretorioSaida();
        String cache = propriedades.diretorioCache();

        if (entrada == null || entrada.isBlank()) {
            log.error("Pasta de entrada nao configurada (deveria ter sido lida no Application.main)");
            ConsoleEntrada.imprimirErroSaida();
            return false;
        }

        pastasExecucao.configurar(entrada, saida, cache, propriedades);
        log.info("Pastas: entrada={}, saída={}, cache={}",
            pastasExecucao.diretorioEntrada(),
            pastasExecucao.diretorioSaida(),
            pastasExecucao.diretorioCache());
        uiLogger.log("Entrada: " + pastasExecucao.diretorioEntrada());
        uiLogger.log("Saída: " + pastasExecucao.diretorioSaida());
        return true;
    }

    private boolean temExtensaoSuportada(Path arquivo) {
        String nome = arquivo.getFileName().toString().toLowerCase();
        return EXTENSOES_SUPORTADAS.stream().anyMatch(nome::endsWith);
    }
}
