package org.traducao.animes.presentation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.traducao.animes.application.ProcessarArquivoUseCase;
import org.traducao.animes.domain.exceptions.TradutorException;
import org.traducao.animes.infrastructure.config.TradutorProperties;
import org.traducao.animes.presentation.ui.ConsoleUILogger;
import org.traducao.animes.presentation.ui.PastasExecucao;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TradutorCLITest {

    private final ProcessarArquivoUseCase processarArquivoUseCase = mock(ProcessarArquivoUseCase.class);
    private final ConsoleUILogger uiLogger = mock(ConsoleUILogger.class);
    private final PastasExecucao pastasExecucao = new PastasExecucao();

    private TradutorCLI criarCli(TradutorProperties propriedades) {
        return new TradutorCLI(processarArquivoUseCase, uiLogger, propriedades, pastasExecucao);
    }

    @Test
    void naoLancaQuandoDiretorioEntradaNaoConfigurado() {
        TradutorProperties propriedades = new TradutorProperties(null, "", "", 20, List.of(), "en", "pt-br");
        TradutorCLI cli = criarCli(propriedades);

        assertThatCode(cli::run).doesNotThrowAnyException();
        verifyNoInteractions(processarArquivoUseCase);
    }

    @Test
    void naoLancaQuandoDiretorioNaoExiste(@TempDir Path tempDir) {
        TradutorProperties propriedades = new TradutorProperties(
            tempDir.resolve("nao-existe").toString(), "", "", 20, List.of(), "en", "pt-br");
        TradutorCLI cli = criarCli(propriedades);

        assertThatCode(cli::run).doesNotThrowAnyException();
        verifyNoInteractions(processarArquivoUseCase);
    }

    @Test
    void ignoraArquivosSemExtensaoSuportada(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("notas.txt"), "irrelevante");
        TradutorProperties propriedades = new TradutorProperties(tempDir.toString(), "", "", 20, List.of(), "en", "pt-br");
        TradutorCLI cli = criarCli(propriedades);

        cli.run();

        verifyNoInteractions(processarArquivoUseCase);
    }

    @Test
    void continuaProcessandoOutrosArquivosQuandoUmFalha(@TempDir Path tempDir) throws Exception {
        Path arquivoA = tempDir.resolve("a.ass");
        Path arquivoB = tempDir.resolve("b.ass");
        Files.writeString(arquivoA, "conteudo a");
        Files.writeString(arquivoB, "conteudo b");

        when(processarArquivoUseCase.processar(arquivoA)).thenThrow(new TradutorException("falhou"));
        when(processarArquivoUseCase.processar(arquivoB)).thenReturn(arquivoB);

        TradutorProperties propriedades = new TradutorProperties(tempDir.toString(), "", "", 20, List.of(), "en", "pt-br");
        TradutorCLI cli = criarCli(propriedades);

        assertThatCode(cli::run).doesNotThrowAnyException();

        verify(processarArquivoUseCase).processar(arquivoA);
        verify(processarArquivoUseCase).processar(arquivoB);
    }
}
