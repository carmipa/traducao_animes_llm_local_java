package org.traducao.projeto.legendas.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.traducao.projeto.legendas.application.strategy.ExtratorAssStrategy;
import org.traducao.projeto.legendas.application.strategy.ExtratorStrategy;
import org.traducao.projeto.legendas.domain.ExtratorException;
import org.traducao.projeto.legendas.domain.FaixaLegenda;
import org.traducao.projeto.legendas.domain.FormatoLegenda;
import org.traducao.projeto.legendas.domain.RelatorioExtracao;
import org.traducao.projeto.legendas.infrastructure.adapters.MkvToolNixAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExtrairLegendaUseCaseTest {

    private final MkvToolNixAdapter mkvAdapter = mock(MkvToolNixAdapter.class);
    private final List<ExtratorStrategy> strategies = List.of(new ExtratorAssStrategy());
    private final ExtrairLegendaUseCase useCase = new ExtrairLegendaUseCase(mkvAdapter, strategies);

    @Test
    void lancaQuandoPastaDeVideosNaoExiste(@TempDir Path tempDir) {
        Path naoExiste = tempDir.resolve("nao-existe");

        assertThatThrownBy(() -> useCase.executar(naoExiste, FormatoLegenda.ASS))
            .isInstanceOf(ExtratorException.class);
    }

    @Test
    void lancaQuandoNenhumaEstrategiaSuportaOFormato(@TempDir Path tempDir) {
        ExtrairLegendaUseCase useCaseSemPgs = new ExtrairLegendaUseCase(mkvAdapter, List.of(new ExtratorAssStrategy()));

        assertThatThrownBy(() -> useCaseSemPgs.executar(tempDir, FormatoLegenda.PGS))
            .isInstanceOf(ExtratorException.class)
            .hasMessageContaining("Nenhuma estratégia");
    }

    @Test
    void registraSemLegendaQuandoNenhumaFaixaAssEncontrada(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("ep01.mkv"), "fake");
        when(mkvAdapter.identificarFaixas(any())).thenReturn(List.of(
            new FaixaLegenda(0, "subtitles", "HDMV PGS", "S_HDMV/PGS", "eng", "Signs", false, false)
        ));

        RelatorioExtracao relatorio = useCase.executar(tempDir, FormatoLegenda.ASS);

        assertThat(relatorio.getArquivosDetectados()).isEqualTo(1);
        assertThat(relatorio.getArquivosSemLegenda()).isEqualTo(1);
        assertThat(relatorio.getLegendasExtraidas()).isZero();
    }

    @Test
    void extraiFaixaEncontradaParaArquivoDeSaidaCorreto(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("ep01.mkv"), "fake");
        when(mkvAdapter.identificarFaixas(any())).thenReturn(List.of(
            new FaixaLegenda(2, "subtitles", "SubStation Alpha", "S_TEXT/ASS", "eng", "Dialogue", false, false)
        ));

        RelatorioExtracao relatorio = useCase.executar(tempDir, FormatoLegenda.ASS);

        assertThat(relatorio.getLegendasExtraidas()).isEqualTo(1);
        Path pastaSaida = tempDir.resolve("legendas_extraidas_ass");
        verify(mkvAdapter).extrairTrilha(eq(tempDir.resolve("ep01.mkv")), eq(2), eq(pastaSaida.resolve("ep01_Track2.ass")));
    }

    @Test
    void naoAbortaDemaisArquivosQuandoUmFalha(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.mkv"), "fake");
        Files.writeString(tempDir.resolve("b.mkv"), "fake");
        FaixaLegenda faixaAss = new FaixaLegenda(0, "subtitles", "SubStation Alpha", "S_TEXT/ASS", "eng", "Dialogue", false, false);
        when(mkvAdapter.identificarFaixas(any())).thenReturn(List.of(faixaAss));
        doThrow(new ExtratorException("mkvextract falhou"))
            .when(mkvAdapter).extrairTrilha(eq(tempDir.resolve("a.mkv")), anyInt(), any());

        RelatorioExtracao relatorio = useCase.executar(tempDir, FormatoLegenda.ASS);

        assertThat(relatorio.getArquivosDetectados()).isEqualTo(2);
        assertThat(relatorio.getFalhasInesperadas()).isEqualTo(1);
        assertThat(relatorio.getLegendasExtraidas()).isEqualTo(1);
        verify(mkvAdapter, times(2)).extrairTrilha(any(), anyInt(), any());
    }
}
