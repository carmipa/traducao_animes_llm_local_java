package org.traducao.projeto.apiDadosAnime.domain.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiDadosAnimeExceptionsTest {

    @Test
    @DisplayName("Deve validar a hierarquia das exceções de API de dados de anime")
    void deveValidarApiDadosAnimeExceptions() {
        ApiDadosAnimeException baseEx = new ApiDadosAnimeException("Erro na API");
        assertThat(baseEx.getErrorId()).isNotNull();

        AnimeNaoEncontradoException notFoundEx = new AnimeNaoEncontradoException("Anime não encontrado");
        assertThat(notFoundEx).isInstanceOf(ApiDadosAnimeException.class);
    }
}
