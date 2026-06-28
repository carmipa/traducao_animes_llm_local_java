package org.traducao.projeto.apiDadosAnime.application;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.traducao.projeto.apiDadosAnime.infrastructure.adapters.TmdbApiClientAdapter;

import static org.assertj.core.api.Assertions.assertThat;

class ObterMetadataAnimeUseCaseTest {

    private final ObterMetadataAnimeUseCase useCase = new ObterMetadataAnimeUseCase(
            new TmdbApiClientAdapter(org.springframework.web.client.RestClient.builder(), new ObjectMapper(), ""),
            null,
            new ObjectMapper());

    @Test
    void extraiNomeTermoBuscaComSucesso() {
        String entrada1 = "cache/[Sokudo] DanMachi/Season 05/[Sokudo] Dungeon ni Deai wo Motomeru no wa Machigatteiru Darou ka V - S05E05 [1080p BD AV1][Dual Audio]_ENG.cache.json";
        String resultado1 = useCase.extrairNomeTermoBusca(entrada1);
        assertThat(resultado1).contains("Dungeon ni Deai wo Motomeru no wa Machigatteiru Darou ka");
        assertThat(resultado1).doesNotContainIgnoringCase("cache").doesNotContainIgnoringCase("json")
            .doesNotContainIgnoringCase("eng");

        String entrada2 = "E:\\animes\\[Sokudo] Gundam CCA [1080p]";
        String resultado2 = useCase.extrairNomeTermoBusca(entrada2);
        assertThat(resultado2).isEqualTo("Gundam CCA");
    }

    @Test
    void removeExtensaoDeArquivoDoTermoDeBusca() {
        String resultado = useCase.extrairNomeTermoBusca("E:\\animes\\[SubsPlease] Frieren - 12 (1080p) [ABCD1234].mkv");
        assertThat(resultado).isEqualTo("Frieren 12");

        String resultadoAss = useCase.extrairNomeTermoBusca("E:\\animes\\Mobile.Suit.Gundam.CCA_Track2.ass");
        assertThat(resultadoAss).isEqualTo("Mobile Suit Gundam CCA");
    }
}
