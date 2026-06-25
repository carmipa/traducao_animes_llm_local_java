package org.traducao.animes.application;

import org.junit.jupiter.api.Test;
import org.traducao.animes.domain.Lote;
import org.traducao.animes.domain.TraducaoLote;
import org.traducao.animes.domain.exceptions.AlucinacaoDetectadaException;
import org.traducao.animes.domain.exceptions.DivergenciaLinhasException;
import org.traducao.animes.domain.exceptions.TradutorException;
import org.traducao.animes.domain.ports.MistralPort;
import org.traducao.animes.presentation.ui.ConsoleUILogger;

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
    void abortaQuandoQuantidadeDeLinhasTraduzidasDivergeDoOriginal() {
        when(mistralPort.traduzir(any())).thenReturn(new TraducaoLote(1, List.of("Só uma linha"), true, null));

        List<Lote> lotes = List.of(new Lote(1, List.of("Linha 1", "Linha 2")));

        assertThatThrownBy(() -> useCase.processarEpisodio(lotes))
            .hasCauseInstanceOf(DivergenciaLinhasException.class);
    }

    @Test
    void abortaQuandoValidadorDetectaAlucinacao() {
        when(mistralPort.traduzir(any()))
            .thenReturn(new TraducaoLote(1, List.of("I don't know what you mean"), true, null));

        List<Lote> lotes = List.of(new Lote(1, List.of("Texto original")));

        assertThatThrownBy(() -> useCase.processarEpisodio(lotes))
            .hasCauseInstanceOf(AlucinacaoDetectadaException.class);
    }
}
