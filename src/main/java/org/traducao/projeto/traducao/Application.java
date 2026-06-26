package org.traducao.projeto.traducao;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.traducao.projeto.traducao.application.ProcessarArquivoUseCase;
import org.traducao.projeto.traducao.application.ProcessarEpisodioUseCase;
import org.traducao.projeto.traducao.application.ValidadorTraducaoService;
import org.traducao.projeto.traducao.infrastructure.adapters.MistralClientAdapter;
import org.traducao.projeto.traducao.infrastructure.cache.CacheTraducaoService;
import org.traducao.projeto.traducao.infrastructure.config.LlmProperties;
import org.traducao.projeto.traducao.infrastructure.config.RestClientConfig;
import org.traducao.projeto.traducao.infrastructure.config.TradutorProperties;
import org.traducao.projeto.traducao.infrastructure.legenda.EscritorLegendaAss;
import org.traducao.projeto.traducao.infrastructure.legenda.LeitorLegendaAss;
import org.traducao.projeto.traducao.infrastructure.legenda.MascaradorTags;
import org.traducao.projeto.traducao.presentation.TradutorCLI;
import org.traducao.projeto.traducao.presentation.ui.ConsoleEntrada;
import org.traducao.projeto.traducao.presentation.ui.ConsoleUILogger;
import org.traducao.projeto.traducao.presentation.ui.PastasExecucao;
import org.traducao.projeto.legendas.application.ExtrairLegendaUseCase;
import org.traducao.projeto.legendas.application.strategy.ExtratorAssStrategy;
import org.traducao.projeto.legendas.application.strategy.ExtratorPgsStrategy;
import org.traducao.projeto.legendas.application.strategy.ExtratorSrtStrategy;
import org.traducao.projeto.legendas.infrastructure.adapters.MkvToolNixAdapter;
import org.traducao.projeto.legendas.infrastructure.config.ExtratorProperties;
import org.traducao.projeto.legendas.presentation.ExtratorCLI;
import org.traducao.projeto.legendas.presentation.ui.ConsoleExtratorLogger;
import org.traducao.projeto.remuxer.application.MapeadorMidiaService;
import org.traducao.projeto.remuxer.application.RemuxarLoteUseCase;
import org.traducao.projeto.remuxer.infrastructure.adapters.MkvmergeAdapter;
import org.traducao.projeto.remuxer.infrastructure.config.RemuxerProperties;
import org.traducao.projeto.remuxer.presentation.RemuxerCLI;
import org.traducao.projeto.remuxer.presentation.ui.ConsoleRemuxerLogger;
import org.traducao.projeto.traducaoCorrige.CorretorCacheCLI;
import org.traducao.projeto.raspagemCorrecao.CorretorRaspagemCLI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Ponto de entrada da aplicacao ({@code public static void main}).
 * <p>
 * A pasta de legendas e lida aqui via {@link System#in} / {@link System#out}
 * <b>antes</b> do Spring Boot subir — mesmo fluxo dos scripts Python com
 * {@code input()}. Depois os caminhos viram argumentos {@code --tradutor.*}
 * para o {@link TradutorProperties}.
 */
@SpringBootApplication
@EnableConfigurationProperties({LlmProperties.class, TradutorProperties.class, RemuxerProperties.class, ExtratorProperties.class})
@Import({
    TradutorCLI.class,
    ConsoleUILogger.class,
    PastasExecucao.class,
    MistralClientAdapter.class,
    RestClientConfig.class,
    ValidadorTraducaoService.class,
    ProcessarEpisodioUseCase.class,
    ProcessarArquivoUseCase.class,
    LeitorLegendaAss.class,
    EscritorLegendaAss.class,
    MascaradorTags.class,
    CacheTraducaoService.class,
    RemuxerCLI.class,
    ConsoleRemuxerLogger.class,
    RemuxarLoteUseCase.class,
    MapeadorMidiaService.class,
    MkvmergeAdapter.class,
    ExtratorCLI.class,
    ConsoleExtratorLogger.class,
    ExtrairLegendaUseCase.class,
    ExtratorAssStrategy.class,
    ExtratorPgsStrategy.class,
    ExtratorSrtStrategy.class,
    MkvToolNixAdapter.class,
    CorretorCacheCLI.class,
    CorretorRaspagemCLI.class
})
public class Application {

    private static final String ARG_ENTRADA = "--tradutor.diretorio-entrada=";
    private static final String ARG_SAIDA = "--tradutor.diretorio-saida=";

    public static void main(String[] args) {
        String[] argsComPastas = prepararArgumentosComPastasDoConsole(args);
        if (argsComPastas == null) {
            System.exit(1);
        }
        SpringApplication.run(Application.class, argsComPastas);
    }

    /**
     * Se a pasta de entrada nao veio na linha de comando, pergunta no console
     * antes de iniciar o Spring (stdin ainda livre, sem logs do Boot no meio).
     */
    static String[] prepararArgumentosComPastasDoConsole(String[] args) {
        if (temValorNaoVazio(args, ARG_ENTRADA)) {
            return args;
        }

        Optional<ConsoleEntrada.CaminhosPastas> caminhos = ConsoleEntrada.solicitarPastas();
        if (caminhos.isEmpty()) {
            ConsoleEntrada.imprimirErroSaida();
            return null;
        }

        ConsoleEntrada.CaminhosPastas informados = caminhos.get();
        List<String> lista = new ArrayList<>(Arrays.asList(args));
        lista.add("--app.modo=" + informados.modo());
        lista.add(ARG_ENTRADA + informados.entrada());
        if (informados.saida() != null && !informados.saida().isBlank()) {
            lista.add(ARG_SAIDA + informados.saida());
        }
        if (informados.formato() != null && !informados.formato().isBlank()) {
            lista.add("--app.extrator.formato=" + informados.formato());
        }
        return lista.toArray(String[]::new);
    }

    private static boolean temValorNaoVazio(String[] args, String prefixo) {
        for (String arg : args) {
            if (arg.startsWith(prefixo) && arg.length() > prefixo.length()) {
                return true;
            }
        }
        return false;
    }
}
