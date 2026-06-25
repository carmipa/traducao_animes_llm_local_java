package org.traducao.projeto.remuxer.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.traducao.projeto.remuxer.domain.RemuxTarefa;
import org.traducao.projeto.remuxer.domain.RemuxerException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MapeadorMidiaServiceTest {

    private final MapeadorMidiaService service = new MapeadorMidiaService();

    @Test
    void lancaQuandoDiretoriosDeOrigemNaoExistem(@TempDir Path tempDir) {
        Path pastaVideos = tempDir.resolve("videos");
        Path pastaLegendas = tempDir.resolve("legendas");

        assertThatThrownBy(() -> service.construirFilaProcessamento(pastaVideos, pastaLegendas, tempDir))
            .isInstanceOf(RemuxerException.class);
    }

    @Test
    void pareiaLegendaPeloSufixoPtbrSimples(@TempDir Path tempDir) throws IOException {
        Path pastaVideos = criarPasta(tempDir, "videos");
        Path pastaLegendas = criarPasta(tempDir, "legendas");
        Path pastaSaida = tempDir.resolve("saida");
        Files.writeString(pastaVideos.resolve("ep01.mkv"), "video");
        Files.writeString(pastaLegendas.resolve("ep01_PTBR.ass"), "legenda");

        List<RemuxTarefa> fila = service.construirFilaProcessamento(pastaVideos, pastaLegendas, pastaSaida);

        assertThat(fila).hasSize(1);
        RemuxTarefa tarefa = fila.get(0);
        assertThat(tarefa.caminhoLegenda()).isEqualTo(pastaLegendas.resolve("ep01_PTBR.ass"));
        assertThat(tarefa.caminhoSaida()).isEqualTo(pastaSaida.resolve("ep01_PTBR.mkv"));
    }

    @Test
    void pareiaQuandoVideoTemSufixoEngELegendaPtbr(@TempDir Path tempDir) throws IOException {
        Path pastaVideos = criarPasta(tempDir, "videos");
        Path pastaLegendas = criarPasta(tempDir, "legendas");
        Path pastaSaida = tempDir.resolve("saida");
        Files.writeString(pastaVideos.resolve("ep02_ENG.mkv"), "video");
        Files.writeString(pastaLegendas.resolve("ep02_PTBR.srt"), "legenda");

        List<RemuxTarefa> fila = service.construirFilaProcessamento(pastaVideos, pastaLegendas, pastaSaida);

        assertThat(fila).hasSize(1);
        assertThat(fila.get(0).caminhoLegenda()).isEqualTo(pastaLegendas.resolve("ep02_PTBR.srt"));
        assertThat(fila.get(0).caminhoSaida()).isEqualTo(pastaSaida.resolve("ep02_PTBR.mkv"));
    }

    @Test
    void ignoraVideoSemLegendaCorrespondente(@TempDir Path tempDir) throws IOException {
        Path pastaVideos = criarPasta(tempDir, "videos");
        Path pastaLegendas = criarPasta(tempDir, "legendas");
        Files.writeString(pastaVideos.resolve("sem_legenda.mkv"), "video");

        List<RemuxTarefa> fila = service.construirFilaProcessamento(pastaVideos, pastaLegendas, tempDir.resolve("saida"));

        assertThat(fila).isEmpty();
    }

    @Test
    void processaApenasArquivosMkvIgnorandoOutrasExtensoes(@TempDir Path tempDir) throws IOException {
        Path pastaVideos = criarPasta(tempDir, "videos");
        Path pastaLegendas = criarPasta(tempDir, "legendas");
        Files.writeString(pastaVideos.resolve("ep01.mkv"), "video");
        Files.writeString(pastaVideos.resolve("leiame.txt"), "nota");
        Files.writeString(pastaLegendas.resolve("ep01_PTBR.ass"), "legenda");

        List<RemuxTarefa> fila = service.construirFilaProcessamento(pastaVideos, pastaLegendas, tempDir.resolve("saida"));

        assertThat(fila).hasSize(1);
        assertThat(fila.get(0).nomeVideo()).isEqualTo("ep01.mkv");
    }

    private Path criarPasta(Path raiz, String nome) throws IOException {
        Path pasta = raiz.resolve(nome);
        Files.createDirectories(pasta);
        return pasta;
    }
}
