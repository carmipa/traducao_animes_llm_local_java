package org.traducao.projeto.traducao.infrastructure.contexto;

import org.junit.jupiter.api.Test;
import org.traducao.projeto.traducao.domain.exceptions.ContextoNaoEncontradoException;
import org.traducao.projeto.traducao.domain.ports.ProvedorContexto;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GerenciadorContextoTest {

    private record ProvedorFake(String id, String nomeExibicao) implements ProvedorContexto {
        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getNomeExibicao() {
            return nomeExibicao;
        }

        @Override
        public String obterPromptSistema() {
            return "prompt-" + id;
        }
    }

    private GerenciadorContexto criarComProvedores() {
        return new GerenciadorContexto(List.of(
                new ProvedorFake("zelda_fake", "Zelda Fake"),
                new ProvedorFake("danmachi", "DanMachi (Geral)"),
                new ProvedorFake("aria_fake", "Aria Fake")
        ));
    }

    @Test
    void usaDanmachiComoContextoPadraoIndependentementeDaOrdemAlfabetica() {
        GerenciadorContexto gerenciador = criarComProvedores();

        assertThat(gerenciador.getIdContextoPadrao()).isEqualTo("danmachi");
        assertThat(gerenciador.obterPromptAtivo()).isEqualTo("prompt-danmachi");
    }

    @Test
    void definirContextoAtivoTrocaParaOContextoSolicitado() {
        GerenciadorContexto gerenciador = criarComProvedores();

        gerenciador.definirContextoAtivo("aria_fake");

        assertThat(gerenciador.obterPromptAtivo()).isEqualTo("prompt-aria_fake");
        assertThat(gerenciador.obterNomeContextoAtivo()).isEqualTo("Aria Fake");
    }

    @Test
    void definirContextoAtivoComIdNuloOuVazioMantemContextoAtual() {
        GerenciadorContexto gerenciador = criarComProvedores();
        gerenciador.definirContextoAtivo("aria_fake");

        gerenciador.definirContextoAtivo(null);
        assertThat(gerenciador.obterPromptAtivo()).isEqualTo("prompt-aria_fake");

        gerenciador.definirContextoAtivo("  ");
        assertThat(gerenciador.obterPromptAtivo()).isEqualTo("prompt-aria_fake");
    }

    @Test
    void definirContextoAtivoComIdDesconhecidoLancaExcecaoEmVezDeCairNoPadrao() {
        GerenciadorContexto gerenciador = criarComProvedores();
        gerenciador.definirContextoAtivo("aria_fake");

        assertThatThrownBy(() -> gerenciador.definirContextoAtivo("contexto_que_nao_existe"))
                .isInstanceOf(ContextoNaoEncontradoException.class)
                .hasMessageContaining("contexto_que_nao_existe");

        // contexto ativo nao deve ter sido alterado pela tentativa invalida
        assertThat(gerenciador.obterPromptAtivo()).isEqualTo("prompt-aria_fake");
    }

    @Test
    void existeContextoIndicaCorretamenteIdsValidosEInvalidos() {
        GerenciadorContexto gerenciador = criarComProvedores();

        assertThat(gerenciador.existeContexto("danmachi")).isTrue();
        assertThat(gerenciador.existeContexto("inexistente")).isFalse();
        assertThat(gerenciador.existeContexto(null)).isFalse();
    }

    @Test
    void construtorComListaVaziaNaoLancaENaoTemContextoPadrao() {
        GerenciadorContexto gerenciador = new GerenciadorContexto(List.of());

        assertThat(gerenciador.getIdContextoPadrao()).isNull();
        assertThat(gerenciador.obterPromptAtivo()).contains("tradutor especialista");
    }
}
