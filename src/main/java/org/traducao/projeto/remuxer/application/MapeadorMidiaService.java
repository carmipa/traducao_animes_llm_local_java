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
import java.util.stream.Stream;

@Service
public class MapeadorMidiaService {
    
    private static final Logger log = LoggerFactory.getLogger(MapeadorMidiaService.class);

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
                String nomeBase = mkv.getFileName().toString().replaceFirst("[.][^.]+$", "");
                String nomeLimpoBase = nomeBase.replace("_PTBR", "").replace("_ENG", "").replace("_PT-BR", "");

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
                            return f.startsWith(nomeBase) || f.startsWith(nomeLimpoBase);
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
                    String nomeSaida = nomeLimpoBase + "_PTBR.mkv";
                    Path caminhoSaida = pastaSaida.resolve(nomeSaida);
                    fila.add(new RemuxTarefa(mkv.getFileName().toString(), mkv, legendaEncontrada, caminhoSaida));
                    log.debug("Pareado com sucesso: {} -> {}", mkv.getFileName(), legendaEncontrada.getFileName());
                } else {
                    log.warn("Legenda ausente para: {}", mkv.getFileName());
                }
            }
        } catch (IOException e) {
            throw new RemuxerException("Erro ao listar arquivos mkv", e);
        }

        return fila;
    }
}
