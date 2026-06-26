package org.traducao.projeto.traducao.infrastructure.legenda;

import org.springframework.stereotype.Component;
import org.traducao.projeto.traducao.domain.exceptions.ArquivoLegendaException;
import org.traducao.projeto.traducao.domain.legenda.DocumentoLegenda;
import org.traducao.projeto.traducao.domain.legenda.EventoLegenda;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Le arquivos .ass/.ssa preservando byte a byte tudo que nao for o campo Text
 * dos eventos Dialogue (estilos, timestamps, secoes de metadados). So o campo
 * Text e exposto para traducao; o resto e reconstruido identico pelo
 * {@link EscritorLegendaAss}.
 */
@Component
public class LeitorLegendaAss {

    private static final char BOM = '﻿';

    public DocumentoLegenda ler(Path arquivo) {
        String conteudo;
        try {
            conteudo = Files.readString(arquivo, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ArquivoLegendaException("Falha ao ler arquivo de legenda: " + arquivo, e);
        }

        boolean comBom = !conteudo.isEmpty() && conteudo.charAt(0) == BOM;
        if (comBom) {
            conteudo = conteudo.substring(1);
        }

        String quebraDeLinha = conteudo.contains("\r\n") ? "\r\n" : "\n";
        String[] linhas = conteudo.split("\r\n|\n", -1);

        int indiceEvents = -1;
        for (int i = 0; i < linhas.length; i++) {
            if (linhas[i].trim().equalsIgnoreCase("[Events]")) {
                indiceEvents = i;
                break;
            }
        }
        if (indiceEvents < 0) {
            throw new ArquivoLegendaException(
                "Arquivo nao parece ser uma legenda .ass/.ssa valida (secao [Events] nao encontrada): " + arquivo);
        }

        int indiceFormat = -1;
        for (int i = indiceEvents + 1; i < linhas.length; i++) {
            if (linhas[i].trim().startsWith("Format:")) {
                indiceFormat = i;
                break;
            }
        }
        if (indiceFormat < 0) {
            throw new ArquivoLegendaException("Secao [Events] sem linha 'Format:' em: " + arquivo);
        }

        List<String> camposFormato = Arrays.stream(
                linhas[indiceFormat].substring(linhas[indiceFormat].indexOf(':') + 1).split(","))
            .map(String::trim)
            .toList();
        int numCampos = camposFormato.size();
        int indiceEstilo = camposFormato.indexOf("Style");

        String cabecalho = String.join(quebraDeLinha, Arrays.asList(linhas).subList(0, indiceFormat + 1)) + quebraDeLinha;

        List<EventoLegenda> eventos = new ArrayList<>();
        int indiceAtual = 0;
        for (int i = indiceFormat + 1; i < linhas.length; i++) {
            if (i == linhas.length - 1 && linhas[i].isEmpty()) {
                // ultima "linha" vazia gerada pelo split por causa da quebra final do arquivo.
                continue;
            }
            eventos.add(parseLinha(linhas[i], indiceAtual++, numCampos, indiceEstilo));
        }

        return new DocumentoLegenda(cabecalho, eventos, quebraDeLinha, comBom);
    }

    private EventoLegenda parseLinha(String linha, int indice, int numCampos, int indiceEstilo) {
        int idxColon = linha.indexOf(':');
        if (idxColon < 0) {
            return new EventoLegenda(indice, "", "", linha, null);
        }

        String tipo = linha.substring(0, idxColon);
        if (!tipo.equals("Dialogue") && !tipo.equals("Comment")) {
            return new EventoLegenda(indice, "", "", linha, null);
        }

        String resto = linha.substring(idxColon + 1);
        if (resto.startsWith(" ")) {
            resto = resto.substring(1);
        }

        String[] partes = resto.split(",", numCampos);
        if (partes.length < numCampos) {
            return new EventoLegenda(indice, "", "", linha, null);
        }

        String estilo = indiceEstilo >= 0 ? partes[indiceEstilo].trim() : "";
        String prefixoCampos = String.join(",", Arrays.copyOf(partes, numCampos - 1));
        String texto = partes[numCampos - 1];
        String prefixo = tipo + ": " + prefixoCampos + ",";
        return new EventoLegenda(indice, tipo, estilo, prefixo, texto);
    }
}
