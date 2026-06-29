package org.traducao.projeto.remuxer.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.traducao.projeto.remuxer.domain.RelatorioRemux;
import org.traducao.projeto.remuxer.domain.RemuxTarefa;
import org.traducao.projeto.remuxer.domain.RemuxerException;
import org.traducao.projeto.remuxer.infrastructure.adapters.MkvmergeAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RemuxarLoteUseCaseTest {

    private final MkvmergeAdapter mkvmergeAdapter = mock(MkvmergeAdapter.class);
    private final MapeadorMidiaService mapeadorMidiaService = mock(MapeadorMidiaService.class);
    private final RemuxarLoteUseCase useCase = new RemuxarLoteUseCase(mkvmergeAdapter, mapeadorMidiaService);

    @Test
    void contabilizaErroDeInfraQuandoValidacaoDeAmbienteFalha(@TempDir Path tempDir) {
        doThrow(new RemuxerException("mkvmerge nao encontrado")).when(mkvmergeAdapter).validarInfraestrutura();

        RelatorioRemux relatorio = useCase.executar(tempDir.resolve("videos"), tempDir.resolve("legendas"));

        assertThat(relatorio.getErrosInfraestrutura()).isEqualTo(1);
        assertThat(relatorio.getDataHoraFim()).isNotNull();
        verifyNoInteractions(mapeadorMidiaService);
    }

    @Test
    void contabilizaErroDeInfraQuandoPastasDeOrigemNaoExistem(@TempDir Path tempDir) {
        RelatorioRemux relatorio = useCase.executar(tempDir.resolve("videos-fantasma"), tempDir.resolve("legendas-fantasma"));

        assertThat(relatorio.getErrosInfraestrutura()).isEqualTo(1);
        verifyNoInteractions(mapeadorMidiaService);
    }

    @Test
    void naoRegistraErrosQuandoFilaDeProcessamentoEstaVazia(@TempDir Path tempDir) throws IOException {
        Path pastaVideos = criarPasta(tempDir, "videos");
        Path pastaLegendas = criarPasta(tempDir, "legendas");
        when(mapeadorMidiaService.construirFilaProcessamento(any(), any(), any())).thenReturn(List.of());

        RelatorioRemux relatorio = useCase.executar(pastaVideos, pastaLegendas);

        assertThat(relatorio.getMkvProcessadosSucesso()).isZero();
        assertThat(relatorio.getDataHoraFim()).isNotNull();
    }

    @Test
    void registraSucessoEBytesGeradosQuandoRemuxFunciona(@TempDir Path tempDir) throws IOException {
        Path pastaVideos = criarPasta(tempDir, "videos");
        Path pastaLegendas = criarPasta(tempDir, "legendas");
        Files.writeString(pastaVideos.resolve("ep01.mkv"), "video original");
        Files.writeString(pastaLegendas.resolve("ep01_PTBR.ass"), "legenda valida");
        Path caminhoSaida = pastaVideos.resolve("mkv_final_ptbr").resolve("ep01_PTBR.mkv");
        RemuxTarefa tarefa = new RemuxTarefa("ep01.mkv", pastaVideos.resolve("ep01.mkv"),
            pastaLegendas.resolve("ep01_PTBR.ass"), caminhoSaida);
        when(mapeadorMidiaService.construirFilaProcessamento(any(), any(), any())).thenReturn(List.of(tarefa));
        doAnswer(invocation -> {
            Files.createDirectories(caminhoSaida.getParent());
            Files.writeString(caminhoSaida, "mkv final com legenda embutida");
            return null;
        }).when(mkvmergeAdapter).executarRemux(tarefa);

        RelatorioRemux relatorio = useCase.executar(pastaVideos, pastaLegendas);

        assertThat(relatorio.getMkvDetectados()).isEqualTo(1);
        assertThat(relatorio.getLegendasPareadas()).isEqualTo(1);
        assertThat(relatorio.getMkvProcessadosSucesso()).isEqualTo(1);
        assertThat(relatorio.getBytesMkvGeradosTotal()).isGreaterThan(0);
        assertThat(relatorio.getArquivosIgnorados()).isZero();
    }

    @Test
    void contabilizaErroRuntimeQuandoMkvmergeFalhaSemAbortarLote(@TempDir Path tempDir) throws IOException {
        Path pastaVideos = criarPasta(tempDir, "videos");
        Path pastaLegendas = criarPasta(tempDir, "legendas");
        Files.writeString(pastaVideos.resolve("ep01.mkv"), "video");
        Files.writeString(pastaLegendas.resolve("ep01_PTBR.ass"), "legenda valida");
        RemuxTarefa tarefa = new RemuxTarefa("ep01.mkv", pastaVideos.resolve("ep01.mkv"),
            pastaLegendas.resolve("ep01_PTBR.ass"), pastaVideos.resolve("mkv_final_ptbr/ep01_PTBR.mkv"));
        when(mapeadorMidiaService.construirFilaProcessamento(any(), any(), any())).thenReturn(List.of(tarefa));
        doThrow(new RemuxerException("mkvmerge falhou com exitCode 1")).when(mkvmergeAdapter).executarRemux(tarefa);

        RelatorioRemux relatorio = useCase.executar(pastaVideos, pastaLegendas);

        assertThat(relatorio.getErrosMkvmergeRuntime()).isEqualTo(1);
        assertThat(relatorio.getMkvProcessadosSucesso()).isZero();
    }

    @Test
    void contabilizaErroInesperadoQuandoExcecaoNaoMapeadaOcorre(@TempDir Path tempDir) throws IOException {
        Path pastaVideos = criarPasta(tempDir, "videos");
        Path pastaLegendas = criarPasta(tempDir, "legendas");
        Files.writeString(pastaVideos.resolve("ep01.mkv"), "video");
        Files.writeString(pastaLegendas.resolve("ep01_PTBR.ass"), "legenda valida");
        RemuxTarefa tarefa = new RemuxTarefa("ep01.mkv", pastaVideos.resolve("ep01.mkv"),
            pastaLegendas.resolve("ep01_PTBR.ass"), pastaVideos.resolve("mkv_final_ptbr/ep01_PTBR.mkv"));
        when(mapeadorMidiaService.construirFilaProcessamento(any(), any(), any())).thenReturn(List.of(tarefa));
        doThrow(new RuntimeException("falha de hardware")).when(mkvmergeAdapter).executarRemux(tarefa);

        RelatorioRemux relatorio = useCase.executar(pastaVideos, pastaLegendas);

        assertThat(relatorio.getErrosInesperados()).isEqualTo(1);
        assertThat(relatorio.getMkvProcessadosSucesso()).isZero();
    }

    private Path criarPasta(Path raiz, String nome) throws IOException {
        Path pasta = raiz.resolve(nome);
        Files.createDirectories(pasta);
        return pasta;
    }
}
