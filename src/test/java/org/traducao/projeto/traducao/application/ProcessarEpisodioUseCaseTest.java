package org.traducao.projeto.traducao.application;

import org.junit.jupiter.api.Test;
import org.traducao.projeto.traducao.domain.Lote;
import org.traducao.projeto.traducao.domain.TraducaoLote;
import org.traducao.projeto.traducao.domain.exceptions.TradutorException;
import org.traducao.projeto.traducao.domain.ports.MistralPort;
import org.traducao.projeto.traducao.presentation.ui.ConsoleUILogger;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessarEpisodioUseCaseTest {

    private final MistralPort mistralPort = mock(MistralPort.class);
    private final ConsoleUILogger uiLogger = mock(ConsoleUILogger.class);
    private final ValidadorTraducaoService validador = new ValidadorTraducaoService();
    private final ProcessarEpisodioUseCase useCase = new ProcessarEpisodioUseCase(mistralPort, validador, uiLogger);

    @Test
    void retornaListaVaziaQuandoNaoHaLotes() throws Exception {
        assertThat(useCase.processarEpisodio(List.of())).isEmpty();
    }

    @Test
    void traduzTodosOsLotesComSucesso() throws Exception {
        when(mistralPort.traduzir(any())).thenAnswer(invocation -> {
            Lote lote = invocation.getArgument(0);
            return new TraducaoLote(lote.idLote(), lote.linhasOriginais(), true, null);
        });

        List<Lote> lotes = List.of(
            new Lote(1, List.of("Olá")),
            new Lote(2, List.of("Adeus"))
        );

        List<TraducaoLote> resultado = useCase.processarEpisodio(lotes);

        assertThat(resultado).hasSize(2);
        assertThat(resultado).allMatch(TraducaoLote::sucesso);
    }

    @Test
    void abortaTodosQuandoUmLoteFalhaNaComunicacao() {
        when(mistralPort.traduzir(any())).thenReturn(new TraducaoLote(1, null, false, "timeout"));

        List<Lote> lotes = List.of(new Lote(1, List.of("Olá")));

        assertThatThrownBy(() -> useCase.processarEpisodio(lotes))
            .hasCauseInstanceOf(TradutorException.class);
    }

    @Test
    void dividePeloMeioQuandoQuantidadeDeLinhasTraduzidasDivergeDoOriginal() throws Exception {
        // Lote de 2 falas onde o LLM "funde" tudo numa linha só: em vez de abortar,
        // o caso passa a dividir em sub-lotes de 1 linha cada e tenta de novo.
        when(mistralPort.traduzir(any())).thenAnswer(invocation -> {
            Lote lote = invocation.getArgument(0);
            if (lote.linhasOriginais().size() > 1) {
                return new TraducaoLote(lote.idLote(), List.of("Só uma linha"), true, null);
            }
            String traduzida = "Linha 1".equals(lote.linhasOriginais().getFirst()) ? "Linha 1 PT" : "Linha 2 PT";
            return new TraducaoLote(lote.idLote(), List.of(traduzida), true, null);
        });

        List<Lote> lotes = List.of(new Lote(1, List.of("Linha 1", "Linha 2")));

        List<TraducaoLote> resultado = useCase.processarEpisodio(lotes);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.getFirst().linhasTraduzidas()).containsExactly("Linha 1 PT", "Linha 2 PT");
    }

    @Test
    void mantemTextoOriginalQuandoValidadorDetectaAlucinacaoMesmoIsolandoAFala() throws Exception {
        // Fala única (lote já no tamanho mínimo) que o LLM insiste em devolver com
        // resíduo em inglês mesmo após as tentativas extras: mantém o original em vez
        // de abortar o episódio inteiro por causa de 1 fala.
        when(mistralPort.traduzir(any()))
            .thenReturn(new TraducaoLote(1, List.of("I don't know what you mean"), true, null));

        List<Lote> lotes = List.of(new Lote(1, List.of("Texto original")));

        List<TraducaoLote> resultado = useCase.processarEpisodio(lotes);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.getFirst().linhasTraduzidas()).containsExactly("Texto original");
    }
}
