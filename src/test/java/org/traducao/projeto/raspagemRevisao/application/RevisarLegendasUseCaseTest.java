package org.traducao.projeto.raspagemRevisao.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.traducao.projeto.raspagemCorrecao.infrastructure.GoogleTranslateScraper;
import org.traducao.projeto.telemetria.TelemetriaService;
import org.traducao.projeto.traducao.application.ValidadorTraducaoService;
import org.traducao.projeto.traducao.domain.ports.MistralPort;
import org.traducao.projeto.traducao.infrastructure.cache.EntradaCache;
import org.traducao.projeto.traducao.infrastructure.contexto.GerenciadorContexto;
import org.traducao.projeto.traducao.infrastructure.legenda.MascaradorTags;
import org.traducao.projeto.traducao.infrastructure.legenda.EscritorLegendaAss;
import org.traducao.projeto.traducao.infrastructure.legenda.LeitorLegendaAss;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RevisarLegendasUseCaseTest {

    @TempDir
    Path tempDir;

    private RevisarLegendasUseCase criarUseCase(GoogleTranslateScraper google) {
        return new RevisarLegendasUseCase(
            new LeitorLegendaAss(),
            new EscritorLegendaAss(),
            google,
            new AuditorProblemasLegendaService(
                new ValidadorTraducaoService(), new DetectorConcordanciaService()),
            new ValidadorTraducaoService(),
            new ObjectMapper(),
            mock(MistralPort.class),
            new MascaradorTags(),
            mock(GerenciadorContexto.class),
            mock(TelemetriaService.class)
        );
    }

    @Test
    void encontraLegendaEngPareadaParaPtbrTrack3() throws Exception {
        String baseMidia = "[Sokudo] DanMachi S05E01 [1080p BD AV1][Dual Audio]";
        Path legendasDir = tempDir.resolve("legendas");
        Files.createDirectories(legendasDir);
        Files.writeString(
            legendasDir.resolve(baseMidia + "_Track2.ass"),
            """
                [Script Info]
                ScriptType: v4.00+

                [Events]
                Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
                Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hello world
                """
        );
        Files.writeString(
            legendasDir.resolve(baseMidia + "_PTBR_Track3.ass"),
            """
                [Script Info]
                ScriptType: v4.00+

                [Events]
                Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
                Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Olá mundo
                """
        );

        GoogleTranslateScraper google = mock(GoogleTranslateScraper.class);
        when(google.traduzir(anyString())).thenAnswer(inv -> inv.getArgument(0));

        RevisarLegendasUseCase useCase = criarUseCase(google);

        PrintStream originalOut = System.out;
        ByteArrayOutputStream captura = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captura, true, StandardCharsets.UTF_8));
        try {
            useCase.executar(legendasDir, null, tempDir.resolve("cache"), null);
        } finally {
            System.setOut(originalOut);
        }

        String saida = captura.toString(StandardCharsets.UTF_8);
        assertThat(saida).contains("Legenda .ass EN:");
        assertThat(saida).contains("_Track2.ass");
        assertThat(saida).contains("1 falas auditadas");
    }

    @Test
    void encontraOriginalEngNoCacheQuandoNaoHaAss() throws Exception {
        String baseMidia = "[Sokudo] DanMachi S05E01 [1080p BD AV1][Dual Audio]";
        Path legendasDir = tempDir.resolve("legendas");
        Path cacheDir = tempDir.resolve("cache/[Sokudo] DanMachi/Season 05");
        Files.createDirectories(legendasDir);
        Files.createDirectories(cacheDir);

        Files.writeString(
            legendasDir.resolve(baseMidia + "_PTBR_Track3.ass"),
            """
                [Script Info]
                ScriptType: v4.00+

                [Events]
                Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
                Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Olá mundo
                """
        );

        List<EntradaCache> entradas = List.of(
            new EntradaCache(1, "Default", "Hello world", "Olá mundo", "en", "pt")
        );
        new ObjectMapper().writeValue(
            cacheDir.resolve(
                "[Sokudo] Dungeon ni Deai wo Motomeru no wa Machigatteiru Darou ka V - S05E01 [1080p BD AV1][Dual Audio]_ENG.cache.json"
            ).toFile(),
            entradas
        );

        GoogleTranslateScraper google = mock(GoogleTranslateScraper.class);
        when(google.traduzir(anyString())).thenAnswer(inv -> inv.getArgument(0));

        RevisarLegendasUseCase useCase = criarUseCase(google);

        PrintStream originalOut = System.out;
        ByteArrayOutputStream captura = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captura, true, StandardCharsets.UTF_8));
        try {
            useCase.executar(legendasDir, null, tempDir.resolve("cache"), null);
        } finally {
            System.setOut(originalOut);
        }

        String saida = captura.toString(StandardCharsets.UTF_8);
        assertThat(saida).contains("Cache carregado:");
        assertThat(saida).contains("_ENG.cache.json");
        assertThat(saida).contains("1 falas auditadas");
        assertThat(saida).doesNotContain("Legenda .ass EN:");
    }

    @Test
    void rejeitaPastaCacheSemAss() throws Exception {
        Path cacheDir = tempDir.resolve("cache/[Sokudo] DanMachi/Season 05");
        Files.createDirectories(cacheDir);
        Files.writeString(cacheDir.resolve("DanMachi_S05E01_ENG.cache.json"), "[]");

        RevisarLegendasUseCase useCase = criarUseCase(mock(GoogleTranslateScraper.class));

        assertThat(useCase.validarPastaEntrada(cacheDir)).isPresent()
            .get()
            .asString()
            .contains("CACHE")
            .contains(".cache.json");
    }
}
