package org.traducao.projeto.legendas.presentation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.traducao.projeto.animes.infrastructure.config.TradutorProperties;
import org.traducao.projeto.animes.presentation.ui.PastasExecucao;
import org.traducao.projeto.legendas.application.ExtrairLegendaUseCase;
import org.traducao.projeto.legendas.domain.FormatoLegenda;
import org.traducao.projeto.legendas.domain.RelatorioExtracao;
import org.traducao.projeto.legendas.presentation.ui.ConsoleExtratorLogger;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExtratorCLITest {

    private final ExtrairLegendaUseCase extrairLegendaUseCase = mock(ExtrairLegendaUseCase.class);
    private final ConsoleExtratorLogger logger = mock(ConsoleExtratorLogger.class);
    private final PastasExecucao pastasExecucao = new PastasExecucao();

    private ExtratorCLI criarCli(TradutorProperties propriedades) {
        return new ExtratorCLI(extrairLegendaUseCase, logger, pastasExecucao, propriedades);
    }

    @Test
    void naoLancaQuandoDiretorioEntradaNaoConfigurado() {
        TradutorProperties propriedades = new TradutorProperties(null, "", "", 20, List.of(), "en", "pt-br");
        ExtratorCLI cli = criarCli(propriedades);

        assertThatCode(cli::run).doesNotThrowAnyException();
        verifyNoInteractions(extrairLegendaUseCase);
    }

    @Test
    void naoLancaQuandoDiretorioNaoExiste(@TempDir Path tempDir) {
        TradutorProperties propriedades = new TradutorProperties(
            tempDir.resolve("nao-existe").toString(), "", "", 20, List.of(), "en", "pt-br");
        ExtratorCLI cli = criarCli(propriedades);

        assertThatCode(cli::run).doesNotThrowAnyException();
        verifyNoInteractions(extrairLegendaUseCase);
    }

    @Test
    void configuraPastasEChamaUseCaseQuandoDiretorioValido(@TempDir Path tempDir) {
        TradutorProperties propriedades = new TradutorProperties(tempDir.toString(), "", "", 20, List.of(), "en", "pt-br");
        when(extrairLegendaUseCase.executar(eq(tempDir), eq(FormatoLegenda.ASS)))
            .thenReturn(new RelatorioExtracao(FormatoLegenda.ASS));
        ExtratorCLI cli = criarCli(propriedades);

        assertThatCode(cli::run).doesNotThrowAnyException();

        assertThat(pastasExecucao.diretorioEntrada()).isEqualTo(tempDir);
        verify(extrairLegendaUseCase).executar(tempDir, FormatoLegenda.ASS);
    }
}
