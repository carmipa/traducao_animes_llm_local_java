package org.traducao.projeto.animes.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.traducao.projeto.animes.domain.Lote;
import org.traducao.projeto.animes.domain.TraducaoLote;
import org.traducao.projeto.animes.domain.ports.MistralPort;
import org.traducao.projeto.animes.infrastructure.cache.CacheTraducaoService;
import org.traducao.projeto.animes.infrastructure.cache.EntradaCache;
import org.traducao.projeto.animes.infrastructure.config.TradutorProperties;
import org.traducao.projeto.animes.infrastructure.legenda.EscritorLegendaAss;
import org.traducao.projeto.animes.infrastructure.legenda.LeitorLegendaAss;
import org.traducao.projeto.animes.infrastructure.legenda.MascaradorTags;
import org.traducao.projeto.animes.presentation.ui.ConsoleUILogger;
import org.traducao.projeto.animes.presentation.ui.PastasExecucao;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessarArquivoUseCaseTest {

    private static final String CONTEUDO_ASS = String.join("\n", List.of(
        "[Script Info]",
        "Title: Teste",
        "",
        "[V4+ Styles]",
        "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding",
        "Style: Default,Arial,60,&H00FFFFFF,&H0000FFFF,&H00000000,&H7F404040,-1,0,0,0,100,100,0,0,1,0,0,2,-153,-153,66,0",
        "",
        "[Events]",
        "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text",
        "Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hello!",
        "Dialogue: 0,0:00:03.00,0:00:04.00,Default,,0,0,0,,Hello!",
        "Dialogue: 0,0:00:05.00,0:00:06.00,Default,,0,0,0,,Goodbye.",
        "Comment: 0,0:00:07.00,0:00:08.00,Default,,0,0,0,,Nota interna",
        "Dialogue: 0,0:00:09.00,0:00:10.00,Song JP,,0,0,0,,Hontou no koto sa"
    )) + "\n";

    private final MistralPort mistralPort = mock(MistralPort.class);
    private final ConsoleUILogger uiLogger = mock(ConsoleUILogger.class);
    private final CacheTraducaoService cacheService = new CacheTraducaoService(new ObjectMapper());

    private ProcessarArquivoUseCase criarUseCase(Path saida, Path cache) {
        ValidadorTraducaoService validador = new ValidadorTraducaoService();
        ProcessarEpisodioUseCase episodioUseCase = new ProcessarEpisodioUseCase(mistralPort, validador, uiLogger);
        TradutorProperties propriedades = new TradutorProperties(
            null, saida.toString(), cache.toString(), 10, List.of("Song JP"), "en", "pt-br");
        PastasExecucao pastasExecucao = new PastasExecucao();
        pastasExecucao.configurar(saida.getParent().resolve("entrada").toString(), saida.toString(), cache.toString(), propriedades);
        return new ProcessarArquivoUseCase(
            new LeitorLegendaAss(), new EscritorLegendaAss(), new MascaradorTags(),
            cacheService, episodioUseCase, validador, propriedades, uiLogger, pastasExecucao);
    }

    private void mockarTraducaoFake() {
        when(mistralPort.traduzir(any())).thenAnswer(invocation -> {
            Lote lote = invocation.getArgument(0);
            List<String> traduzidas = lote.linhasOriginais().stream()
                .map(linha -> switch (linha) {
                    case "Hello!" -> "Olá!";
                    case "Goodbye." -> "Adeus.";
                    default -> linha;
                })
                .toList();
            return new TraducaoLote(lote.idLote(), traduzidas, true, null);
        });
    }

    @Test
    void traduzArquivoNovoDeduplicandoFalasRepetidasEPulandoComentariosEEstilosIgnorados(@TempDir Path tempDir) throws Exception {
        Path entrada = tempDir.resolve("entrada.ass");
        Files.writeString(entrada, CONTEUDO_ASS, StandardCharsets.UTF_8);
        Path saida = tempDir.resolve("saida");
        Path cache = tempDir.resolve("cache");
        mockarTraducaoFake();

        Path arquivoSaida = criarUseCase(saida, cache).processar(entrada);

        String conteudoSaida = Files.readString(arquivoSaida, StandardCharsets.UTF_8);
        assertThat(conteudoSaida).contains("Default,,0,0,0,,Olá!").doesNotContain("Hello!");
        assertThat(conteudoSaida).contains("Default,,0,0,0,,Adeus.");
        assertThat(conteudoSaida).contains("Nota interna");
        assertThat(conteudoSaida).contains("Hontou no koto sa");

        // "Hello!" aparece 2x no arquivo mas e uma fala so -> 1 unica chamada ao LLM para as 2 falas distintas pendentes.
        verify(mistralPort, times(1)).traduzir(any());

        Map<String, String> cacheCarregado = cacheService.carregar(cache.resolve("entrada.cache.json"));
        assertThat(cacheCarregado).containsEntry("Hello!", "Olá!").containsEntry("Goodbye.", "Adeus.");
    }

    @Test
    void reaproveitaCacheExistenteESoTraduzOQueFalta(@TempDir Path tempDir) throws Exception {
        Path entrada = tempDir.resolve("entrada.ass");
        Files.writeString(entrada, CONTEUDO_ASS, StandardCharsets.UTF_8);
        Path saida = tempDir.resolve("saida");
        Path cache = tempDir.resolve("cache");

        // Simula uma correcao manual do usuario no cache de uma execucao anterior.
        cacheService.salvar(cache.resolve("entrada.cache.json"), List.of(
            new EntradaCache(0, "Default", "Hello!", "Oi, corrigido!", "en", "pt-br")
        ));
        mockarTraducaoFake();

        Path arquivoSaida = criarUseCase(saida, cache).processar(entrada);

        String conteudoSaida = Files.readString(arquivoSaida, StandardCharsets.UTF_8);
        assertThat(conteudoSaida).contains("Default,,0,0,0,,Oi, corrigido!");
        assertThat(conteudoSaida).contains("Default,,0,0,0,,Adeus.");

        // "Hello!" veio do cache (corrigido manualmente): so "Goodbye." precisou ir ao LLM.
        verify(mistralPort, times(1)).traduzir(any());
    }
}
