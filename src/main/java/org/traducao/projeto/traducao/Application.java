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
import org.traducao.projeto.remuxer.application.MapeadorMidiaService;
import org.traducao.projeto.remuxer.application.RemuxarLoteUseCase;
import org.traducao.projeto.remuxer.infrastructure.adapters.MkvmergeAdapter;
import org.traducao.projeto.remuxer.infrastructure.config.RemuxerProperties;
import org.traducao.projeto.remuxer.presentation.RemuxerCLI;
import org.traducao.projeto.remuxer.presentation.ui.ConsoleRemuxerLogger;
import org.traducao.projeto.traducaoCorrige.CorretorCacheCLI;
import org.traducao.projeto.legendasExtracao.application.ExtrairLegendaUseCase;
import org.traducao.projeto.legendasExtracao.application.strategy.ExtratorAssStrategy;
import org.traducao.projeto.legendasExtracao.application.strategy.ExtratorPgsStrategy;
import org.traducao.projeto.legendasExtracao.application.strategy.ExtratorSrtStrategy;
import org.traducao.projeto.legendasExtracao.infrastructure.adapters.MkvToolNixAdapter;
import org.traducao.projeto.legendasExtracao.infrastructure.config.ExtratorProperties;
import org.traducao.projeto.legendasExtracao.presentation.ExtratorCLI;
import org.traducao.projeto.legendasExtracao.presentation.ui.ConsoleExtratorLogger;
import org.traducao.projeto.raspagemCorrecao.CorretorRaspagemCLI;
import org.traducao.projeto.analisadorMidia.presentation.AnalisadorMidiaCLI;
import org.traducao.projeto.analisadorMidia.application.AnalisarMidiaUseCase;
import org.traducao.projeto.analisadorMidia.infrastructure.adapters.FfprobeAdapter;
import org.traducao.projeto.analisadorMidia.presentation.ui.ConsoleAnalisadorLogger;
import org.traducao.projeto.telemetria.TelemetriaService;
import org.traducao.projeto.mapaProjeto.presentation.MapaProjetoCLI;
import org.traducao.projeto.mapaProjeto.application.MapeadorDiretorioUseCase;
import org.traducao.projeto.mapaProjeto.application.GeradorMapaProjetoUseCase;
import org.traducao.projeto.raspagemCorrecao.application.CorrigirComGoogleUseCase;
import org.traducao.projeto.traducaoCorrige.application.LimparCacheUseCase;
import org.traducao.projeto.traducao.presentation.web.ApiController;
import org.traducao.projeto.traducao.presentation.web.BrowserLauncher;
import org.traducao.projeto.traducao.presentation.web.ConsoleRedirector;
import org.traducao.projeto.traducao.presentation.web.LogStreamService;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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
    CorretorRaspagemCLI.class,
    AnalisadorMidiaCLI.class,
    AnalisarMidiaUseCase.class,
    FfprobeAdapter.class,
    ConsoleAnalisadorLogger.class,
    TelemetriaService.class,
    MapaProjetoCLI.class,
    MapeadorDiretorioUseCase.class,
    GeradorMapaProjetoUseCase.class,
    LimparCacheUseCase.class,
    CorrigirComGoogleUseCase.class,
    LogStreamService.class,
    ConsoleRedirector.class,
    BrowserLauncher.class,
    ApiController.class
})
public class Application {

    private static final String ARG_ENTRADA = "--tradutor.diretorio-entrada=";
    private static final String ARG_SAIDA = "--tradutor.diretorio-saida=";

    public static void main(String[] args) {
        forcarSaidaUtf8();
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
        boolean temEntradaArg = false;
        for (String arg : args) {
            if (arg.startsWith(ARG_ENTRADA)) {
                temEntradaArg = true;
                break;
            }
        }

        if (temEntradaArg) {
            if (temValorNaoVazio(args, ARG_ENTRADA)) {
                return args;
            }
            return null; // Argumento de entrada foi passado vazio, retorna null
        }

        // Por padrão, se nenhum argumento de console for fornecido,
        // iniciamos diretamente no modo WEB (servidor + navegador automático) sem travar.
        List<String> lista = new ArrayList<>(Arrays.asList(args));
        lista.add("--app.modo=" + "WEB");
        lista.add("--server.port=8080");
        lista.add("--spring.main.web-application-type=servlet");
        lista.add(ARG_ENTRADA + "cache"); // Placeholder para passar na validação de entrada
        return lista.toArray(String[]::new);
    }

    /**
     * Reconfigura System.out/System.err para UTF-8 explicito, em vez de
     * confiar em stdout.encoding/stderr.encoding (no Windows, essas
     * propriedades seguem a codepage nativa do console — tipicamente
     * CP1252/CP850 — mesmo com file.encoding=UTF-8). Sem isso, acentuação em
     * português (ex: "tradução", "vídeo") sai corrompida no console/log,
     * independente de como o app é iniciado (gradlew bootRun, java -jar, IDE).
     */
    private static void forcarSaidaUtf8() {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
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
