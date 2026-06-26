package org.traducao.projeto.remuxer.presentation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.traducao.projeto.traducao.infrastructure.config.TradutorProperties;
import org.traducao.projeto.traducao.presentation.ui.PastasExecucao;
import org.traducao.projeto.remuxer.application.RemuxarLoteUseCase;
import org.traducao.projeto.remuxer.domain.RelatorioRemux;
import org.traducao.projeto.remuxer.presentation.ui.ConsoleRemuxerLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RemuxerCLITest {

    private final RemuxarLoteUseCase remuxarLoteUseCase = mock(RemuxarLoteUseCase.class);
    private final ConsoleRemuxerLogger logger = mock(ConsoleRemuxerLogger.class);
    private final PastasExecucao pastasExecucao = new PastasExecucao();

    private RemuxerCLI criarCli(TradutorProperties propriedades) {
        return new RemuxerCLI(remuxarLoteUseCase, logger, pastasExecucao, propriedades);
    }

    @Test
    void naoLancaQuandoDiretorioEntradaNaoConfigurado() {
        TradutorProperties propriedades = new TradutorProperties(null, "", "", 20, List.of(), "en", "pt-br");
        RemuxerCLI cli = criarCli(propriedades);

        assertThatCode(cli::run).doesNotThrowAnyException();
        verifyNoInteractions(remuxarLoteUseCase);
    }

    @Test
    void naoLancaQuandoPastaDeVideosNaoExiste(@TempDir Path tempDir) {
        TradutorProperties propriedades = new TradutorProperties(
            tempDir.resolve("nao-existe").toString(), tempDir.toString(), "", 20, List.of(), "en", "pt-br");
        RemuxerCLI cli = criarCli(propriedades);

        assertThatCode(cli::run).doesNotThrowAnyException();
        verifyNoInteractions(remuxarLoteUseCase);
    }

    @Test
    void naoLancaQuandoPastaDeLegendasNaoExiste(@TempDir Path tempDir) throws IOException {
        Path pastaVideos = tempDir.resolve("videos");
        Files.createDirectories(pastaVideos);
        TradutorProperties propriedades = new TradutorProperties(
            pastaVideos.toString(), tempDir.resolve("legendas-fantasma").toString(), "", 20, List.of(), "en", "pt-br");
        RemuxerCLI cli = criarCli(propriedades);

        assertThatCode(cli::run).doesNotThrowAnyException();
        verifyNoInteractions(remuxarLoteUseCase);
    }

    @Test
    void configuraPastasEChamaUseCaseQuandoAmbosOsDiretoriosExistem(@TempDir Path tempDir) throws IOException {
        Path pastaVideos = tempDir.resolve("videos");
        Path pastaLegendas = tempDir.resolve("legendas");
        Files.createDirectories(pastaVideos);
        Files.createDirectories(pastaLegendas);
        TradutorProperties propriedades = new TradutorProperties(
            pastaVideos.toString(), pastaLegendas.toString(), "", 20, List.of(), "en", "pt-br");
        when(remuxarLoteUseCase.executar(any(), any())).thenReturn(new RelatorioRemux());
        RemuxerCLI cli = criarCli(propriedades);

        assertThatCode(cli::run).doesNotThrowAnyException();

        assertThat(pastasExecucao.diretorioEntrada()).isEqualTo(pastaVideos);
        verify(remuxarLoteUseCase).executar(pastaVideos, pastaLegendas);
    }
}
