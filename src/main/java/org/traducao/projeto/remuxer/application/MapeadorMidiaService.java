package org.traducao.projeto.remuxer.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.traducao.projeto.remuxer.domain.RemuxTarefa;
import org.traducao.projeto.remuxer.domain.RemuxerException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class MapeadorMidiaService {
    
    private static final Logger log = LoggerFactory.getLogger(MapeadorMidiaService.class);
    private static final Pattern PATTERN_EPISODIO = Pattern.compile("(?i)(S\\d{1,2}E\\d{1,3}|E\\d{1,3})");

    public List<RemuxTarefa> construirFilaProcessamento(Path pastaVideos, Path pastaLegendas, Path pastaSaida) {
        log.debug("Escaneando diretório de vídeos: {}", pastaVideos);
        List<RemuxTarefa> fila = new ArrayList<>();

        if (!Files.exists(pastaVideos) || !Files.exists(pastaLegendas)) {
            throw new RemuxerException("Diretórios de origem não encontrados.");
        }

        try (Stream<Path> stream = Files.list(pastaVideos)) {
            List<Path> mkvs = stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().toLowerCase().endsWith(".mkv"))
                .sorted()
                .toList();
            
            for (Path mkv : mkvs) {
                String nomeArq = mkv.getFileName().toString();
                String nomeBase = nomeArq.replaceFirst("[.][^.]+$", "");
                String nomeLimpoBase = nomeBase.replace("_PTBR", "").replace("_ENG", "").replace("_PT-BR", "");

                String tagEpisodio = extrairTagEpisodio(nomeArq);

                Path legendaEncontrada = null;
                try (Stream<Path> streamLegendas = Files.list(pastaLegendas)) {
                    List<Path> candidatas = streamLegendas
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            String f = p.getFileName().toString().toLowerCase();
                            return f.endsWith(".ass") || f.endsWith(".srt");
                        })
                        .filter(p -> {
                            String f = p.getFileName().toString();
                            if (f.startsWith(nomeBase) || f.startsWith(nomeLimpoBase)) {
                                return true;
                            }
                            if (tagEpisodio != null && !tagEpisodio.isBlank()) {
                                String tagLeg = extrairTagEpisodio(f);
                                return tagEpisodio.equalsIgnoreCase(tagLeg);
                            }
                            return false;
                        })
                        .toList();

                    // Priorizar as que possuem PT-BR ou PTBR no nome, depois as outras
                    legendaEncontrada = candidatas.stream()
                        .filter(p -> p.getFileName().toString().toUpperCase().contains("PT-BR") || 
                                     p.getFileName().toString().toUpperCase().contains("PTBR"))
                        .findFirst()
                        .orElse(candidatas.isEmpty() ? null : candidatas.get(0));
                } catch (IOException e) {
                    log.warn("Erro ao ler diretório de legendas para pareamento", e);
                }

                if (legendaEncontrada != null) {
                    String nomeSaida = nomeLimpoBase.endsWith("_PTBR") ? nomeLimpoBase + ".mkv" : nomeLimpoBase + "_PTBR.mkv";
                    Path caminhoSaida = pastaSaida.resolve(nomeSaida);
                    fila.add(new RemuxTarefa(mkv.getFileName().toString(), mkv, legendaEncontrada, caminhoSaida));
                    log.info("Pareado com sucesso: {} -> {}", mkv.getFileName(), legendaEncontrada.getFileName());
                } else {
                    log.warn("Legenda ausente para: {}", mkv.getFileName());
                }
            }
        } catch (IOException e) {
            throw new RemuxerException("Erro ao listar arquivos mkv", e);
        }

        return fila;
    }

    private String extrairTagEpisodio(String nome) {
        Matcher m = PATTERN_EPISODIO.matcher(nome);
        if (m.find()) {
            return m.group(1).toUpperCase();
        }
        return null;
    }
}
