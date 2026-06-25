package org.traducao.animes.infrastructure.legenda;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.traducao.animes.domain.exceptions.ArquivoLegendaException;
import org.traducao.animes.domain.legenda.DocumentoLegenda;
import org.traducao.animes.domain.legenda.EventoLegenda;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeitorLegendaAssTest {

    private static final String BOM = "﻿";

    private final LeitorLegendaAss leitor = new LeitorLegendaAss();
    private final EscritorLegendaAss escritor = new EscritorLegendaAss();

    private static final String[] LINHAS = {
        "[Script Info]",
        "; Comentario gerado pelo Aegisub",
        "Title: Teste",
        "ScriptType: v4.00+",
        "",
        "[V4+ Styles]",
        "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding",
        "Style: Default,Arial,60,&H00FFFFFF,&H0000FFFF,&H00000000,&H7F404040,-1,0,0,0,100,100,0,0,1,0,0,2,-153,-153,66,0",
        "",
        "[Events]",
        "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text",
        "Dialogue: 0,0:01:38.78,0:01:39.35,Dialogue,,0,0,0,,{\\i1}Space.",
        "Dialogue: 0,0:01:40.42,0:01:43.92,Dialogue,,0,0,0,,The second home of humanity.",
        "Comment: 0,0:24:09.80,0:24:12.93,Default,,0,0,0,,\"Next Episode\\NTHE KNIGHT\"",
        "Dialogue: 1,0:00:09.28,0:00:16.28,Song JP,,0,0,0,,Hontou no koto sa"
    };

    private String construirAss(String quebra) {
        return String.join(quebra, LINHAS) + quebra;
    }

    @Test
    void parseiaEventosComTiposEstilosETextoCorretos(@TempDir Path tempDir) throws IOException {
        Path arquivo = tempDir.resolve("teste.ass");
        Files.writeString(arquivo, BOM + construirAss("\r\n"), StandardCharsets.UTF_8);

        DocumentoLegenda documento = leitor.ler(arquivo);

        assertThat(documento.comBom()).isTrue();
        assertThat(documento.quebraDeLinha()).isEqualTo("\r\n");
        assertThat(documento.eventos()).hasSize(4);

        EventoLegenda primeiro = documento.eventos().get(0);
        assertThat(primeiro.isDialogo()).isTrue();
        assertThat(primeiro.estilo()).isEqualTo("Dialogue");
        assertThat(primeiro.texto()).isEqualTo("{\\i1}Space.");

        EventoLegenda comentario = documento.eventos().get(2);
        assertThat(comentario.tipoLinha()).isEqualTo("Comment");
        assertThat(comentario.isDialogo()).isFalse();
        assertThat(comentario.temTexto()).isTrue();

        EventoLegenda songJp = documento.eventos().get(3);
        assertThat(songJp.isDialogo()).isTrue();
        assertThat(songJp.estilo()).isEqualTo("Song JP");
        assertThat(songJp.texto()).isEqualTo("Hontou no koto sa");
    }

    @Test
    void lancaQuandoArquivoNaoTemSecaoEvents(@TempDir Path tempDir) throws IOException {
        Path arquivo = tempDir.resolve("invalido.ass");
        Files.writeString(arquivo, "[Script Info]\nTitle: sem eventos\n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> leitor.ler(arquivo))
            .isInstanceOf(ArquivoLegendaException.class);
    }

    @Test
    void reconstroiArquivoIdenticoQuandoTextoNaoMuda(@TempDir Path tempDir) throws IOException {
        String conteudoOriginal = construirAss("\r\n");
        Path entrada = tempDir.resolve("entrada.ass");
        Files.writeString(entrada, conteudoOriginal, StandardCharsets.UTF_8);

        DocumentoLegenda documento = leitor.ler(entrada);

        Path saida = tempDir.resolve("saida.ass");
        escritor.escrever(saida, documento);

        String conteudoSaida = Files.readString(saida, StandardCharsets.UTF_8);
        assertThat(conteudoSaida).isEqualTo(conteudoOriginal);
    }

    @Test
    void escreveTraducaoSubstituindoApenasOTextoDosEventosDialogo(@TempDir Path tempDir) throws IOException {
        Path entrada = tempDir.resolve("entrada.ass");
        Files.writeString(entrada, construirAss("\n"), StandardCharsets.UTF_8);

        DocumentoLegenda documento = leitor.ler(entrada);
        EventoLegenda primeiro = documento.eventos().get(0);
        EventoLegenda comentario = documento.eventos().get(2);

        var eventosTraduzidos = documento.eventos().stream()
            .map(e -> e == primeiro ? e.comTexto("{\\i1}Espaço.") : e)
            .toList();
        DocumentoLegenda documentoTraduzido = new DocumentoLegenda(
            documento.cabecalho(), eventosTraduzidos, documento.quebraDeLinha(), documento.comBom());

        Path saida = tempDir.resolve("saida.ass");
        escritor.escrever(saida, documentoTraduzido);

        String conteudoSaida = Files.readString(saida, StandardCharsets.UTF_8);
        assertThat(conteudoSaida).contains("Dialogue: 0,0:01:38.78,0:01:39.35,Dialogue,,0,0,0,,{\\i1}Espaço.");
        // O Comment, que nao foi traduzido, deve permanecer byte a byte igual ao original.
        assertThat(conteudoSaida).contains(comentario.prefixo() + comentario.texto());
    }
}
