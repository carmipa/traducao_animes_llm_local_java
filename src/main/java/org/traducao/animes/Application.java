package org.traducao.animes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.traducao.animes.application.ProcessarArquivoUseCase;
import org.traducao.animes.application.ProcessarEpisodioUseCase;
import org.traducao.animes.application.ValidadorTraducaoService;
import org.traducao.animes.infrastructure.adapters.MistralClientAdapter;
import org.traducao.animes.infrastructure.cache.CacheTraducaoService;
import org.traducao.animes.infrastructure.config.LlmProperties;
import org.traducao.animes.infrastructure.config.RestClientConfig;
import org.traducao.animes.infrastructure.config.TradutorProperties;
import org.traducao.animes.infrastructure.legenda.EscritorLegendaAss;
import org.traducao.animes.infrastructure.legenda.LeitorLegendaAss;
import org.traducao.animes.infrastructure.legenda.MascaradorTags;
import org.traducao.animes.presentation.TradutorCLI;
import org.traducao.animes.presentation.ui.ConsoleEntrada;
import org.traducao.animes.presentation.ui.ConsoleUILogger;
import org.traducao.animes.presentation.ui.PastasExecucao;

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
@EnableConfigurationProperties({LlmProperties.class, TradutorProperties.class})
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
    CacheTraducaoService.class
})
public class Application {

    private static final String ARG_ENTRADA = "--tradutor.diretorio-entrada=";
    private static final String ARG_SAIDA = "--tradutor.diretorio-saida=";
    private static final String ARG_CACHE = "--tradutor.diretorio-cache=";

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
        lista.add(ARG_ENTRADA + informados.entrada());
        if (!informados.saida().isBlank()) {
            lista.add(ARG_SAIDA + informados.saida());
        }
        if (!informados.cache().isBlank()) {
            lista.add(ARG_CACHE + informados.cache());
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
