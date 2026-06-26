package org.traducao.projeto.traducao.infrastructure.legenda;

import org.springframework.stereotype.Component;
import org.traducao.projeto.traducao.domain.exceptions.ArquivoLegendaException;
import org.traducao.projeto.traducao.domain.legenda.DocumentoLegenda;
import org.traducao.projeto.traducao.domain.legenda.EventoLegenda;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reconstroi o arquivo .ass a partir do {@link DocumentoLegenda}, repetindo o
 * cabecalho original e as linhas nao traduziveis byte a byte, e so trocando o
 * campo Text dos eventos Dialogue pela versao traduzida.
 */
@Component
public class EscritorLegendaAss {

    private static final char BOM = '﻿';

    public void escrever(Path destino, DocumentoLegenda documento) {
        StringBuilder conteudo = new StringBuilder();
        if (documento.comBom()) {
            conteudo.append(BOM);
        }
        conteudo.append(documento.cabecalho());

        for (EventoLegenda evento : documento.eventos()) {
            conteudo.append(evento.prefixo());
            if (evento.temTexto()) {
                conteudo.append(evento.texto());
            }
            conteudo.append(documento.quebraDeLinha());
        }

        try {
            Path pasta = destino.toAbsolutePath().getParent();
            if (pasta != null) {
                Files.createDirectories(pasta);
            }
            Files.writeString(destino, conteudo.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ArquivoLegendaException("Falha ao escrever arquivo de legenda: " + destino, e);
        }
    }
}
