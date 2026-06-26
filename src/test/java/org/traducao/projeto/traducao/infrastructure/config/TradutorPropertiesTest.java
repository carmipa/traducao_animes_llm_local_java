package org.traducao.projeto.traducao.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TradutorPropertiesTest {

    @Test
    void calculaCacheAutomaticamenteComNomeDoAnimeESubpasta() {
        TradutorProperties propriedades = new TradutorProperties(
            "C:\\animes\\[Sokudo] DanMachi\\Season 05\\legendas_eng",
            "", "", 20, List.of(), "en", "pt-br");

        assertThat(propriedades.resolverDiretorioCache())
            .isEqualTo(Path.of("cache", "[Sokudo] DanMachi", "Season 05"));
    }

    @Test
    void calculaCacheComApenasUmNivelQuandoNaoHaPastaAvo() {
        TradutorProperties propriedades = new TradutorProperties(
            "C:\\MeuAnime\\legendas_eng",
            "", "", 20, List.of(), "en", "pt-br");

        assertThat(propriedades.resolverDiretorioCache())
            .isEqualTo(Path.of("cache", "MeuAnime"));
    }

    @Test
    void respeitaDiretorioCacheInformadoExplicitamente() {
        TradutorProperties propriedades = new TradutorProperties(
            "C:\\animes\\[Sokudo] DanMachi\\Season 05\\legendas_eng",
            "", "D:\\cache-customizado", 20, List.of(), "en", "pt-br");

        assertThat(propriedades.resolverDiretorioCache())
            .isEqualTo(Path.of("D:\\cache-customizado"));
    }
}
