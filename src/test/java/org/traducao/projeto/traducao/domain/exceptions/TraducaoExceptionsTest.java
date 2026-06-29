package org.traducao.projeto.traducao.domain.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraducaoExceptionsTest {

    @Test
    @DisplayName("Deve validar a hierarquia e metadados de TradutorException e filhas")
    void deveValidarTradutorExceptions() {
        TradutorException baseEx = new TradutorException("Falha genérica do tradutor");
        assertThat(baseEx.getErrorId()).isNotNull();
        assertThat(baseEx.getTimestamp()).isNotNull();

        LmStudioOfflineException lmEx = new LmStudioOfflineException("LM Studio indisponível");
        assertThat(lmEx).isInstanceOf(TradutorException.class);
        assertThat(lmEx.getErrorId()).isNotNull();

        AlucinacaoDetectadaException aluEx = new AlucinacaoDetectadaException("Alucinação em lote");
        assertThat(aluEx).isInstanceOf(TradutorException.class);

        ArquivoLegendaException arqEx = new ArquivoLegendaException("Arquivo corrompido");
        assertThat(arqEx).isInstanceOf(TradutorException.class);

        ContextoNaoEncontradoException ctxEx = new ContextoNaoEncontradoException("Contexto ausente");
        assertThat(ctxEx).isInstanceOf(TradutorException.class);

        DivergenciaLinhasException divEx = new DivergenciaLinhasException("Linhas desalinhadas");
        assertThat(divEx).isInstanceOf(TradutorException.class);

        LlmFalhaComunicacaoException comEx = new LlmFalhaComunicacaoException("Timeout HTTP");
        assertThat(comEx).isInstanceOf(TradutorException.class);

        RespostaLlmVaziaException vazEx = new RespostaLlmVaziaException("Resposta em branco");
        assertThat(vazEx).isInstanceOf(TradutorException.class);

        TraducaoParcialException parcEx = new TraducaoParcialException("Tradução incompleta", java.util.Collections.emptyList(), new RuntimeException("Causa"));
        assertThat(parcEx).isInstanceOf(TradutorException.class);
    }
}
