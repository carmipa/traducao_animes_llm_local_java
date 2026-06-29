package org.traducao.projeto.legendasExtracao.infrastructure.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.traducao.projeto.legendasExtracao.domain.ExtratorException;
import org.traducao.projeto.legendasExtracao.domain.FaixaLegenda;
import org.traducao.projeto.legendasExtracao.domain.ports.ExtratorVideoPort;
import org.traducao.projeto.legendasExtracao.infrastructure.config.ExtratorProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Extrai legendas de contêineres que o MKVToolNix não lê (mkvextract só opera
 * sobre Matroska/WebM). Cobre MP4, MOV, AVI e afins via ffmpeg/ffprobe.
 */
@Component
public class FfmpegAdapter implements ExtratorVideoPort {
    private static final Logger log = LoggerFactory.getLogger(FfmpegAdapter.class);
    private static final Set<String> EXTENSOES_SUPORTADAS = Set.of(
        ".mp4", ".m4v", ".mov", ".avi", ".ts", ".m2ts", ".flv", ".wmv"
    );

    private final String ffmpegPath;
    private final String ffprobePath;
    private final ObjectMapper objectMapper;

    public FfmpegAdapter(ExtratorProperties properties, ObjectMapper objectMapper) {
        this.ffmpegPath = properties.resolverFfmpegPath();
        this.ffprobePath = properties.resolverFfprobePath();
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean suporta(Path arquivoVideo) {
        String nome = arquivoVideo.toString().toLowerCase();
        return EXTENSOES_SUPORTADAS.stream().anyMatch(nome::endsWith);
    }

    @Override
    public void validarInfraestrutura() {
        try {
            Process p1 = new ProcessBuilder(ffprobePath, "-version").start();
            if (p1.waitFor() != 0) throw new ExtratorException("ffprobe falhou na validação.");

            Process p2 = new ProcessBuilder(ffmpegPath, "-version").start();
            if (p2.waitFor() != 0) throw new ExtratorException("ffmpeg falhou na validação.");
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new ExtratorException("Não foi possível localizar ffmpeg/ffprobe no sistema.", e);
        }
    }

    @Override
    public List<FaixaLegenda> identificarFaixas(Path videoPath) {
        List<FaixaLegenda> faixas = new ArrayList<>();
        List<String> cmd = List.of(
            ffprobePath,
            "-v", "quiet",
            "-print_format", "json",
            "-show_streams",
            videoPath.toAbsolutePath().toString()
        );

        try {
            Process process = new ProcessBuilder(cmd).start();
            byte[] stdoutBytes = process.getInputStream().readAllBytes();
            byte[] stderrBytes = process.getErrorStream().readAllBytes();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                String stderr = new String(stderrBytes, StandardCharsets.UTF_8);
                throw new ExtratorException("ffprobe falhou com código " + exitCode + ". Erro: " + stderr);
            }

            String jsonString = new String(stdoutBytes, StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(jsonString);
            JsonNode streamsNode = root.path("streams");

            if (streamsNode.isArray()) {
                for (JsonNode stream : streamsNode) {
                    if ("subtitle".equalsIgnoreCase(stream.path("codec_type").asText())) {
                        int id = stream.path("index").asInt();
                        String codec = stream.path("codec_name").asText("");
                        String codecId = stream.path("codec_long_name").asText(codec);
                        
                        JsonNode tags = stream.path("tags");
                        String idioma = tags.path("language").asText("und");
                        String nome = tags.path("title").asText("Sem Titulo");
                        if ("Sem Titulo".equals(nome)) {
                            nome = tags.path("TITLE").asText("Sem Titulo");
                        }

                        JsonNode disposition = stream.path("disposition");
                        boolean isDefault = disposition.path("default").asInt(0) == 1;
                        boolean isForced = disposition.path("forced").asInt(0) == 1;

                        faixas.add(new FaixaLegenda(id, "subtitles", codec, codecId, idioma, nome, isDefault, isForced));
                    }
                }
            }

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new ExtratorException("Falha ao invocar ffprobe para identificar: " + videoPath, e);
        }

        return faixas;
    }

    @Override
    public void extrairTrilha(Path videoPath, int streamIndex, Path caminhoSaida) {
        List<String> cmd = List.of(
            ffmpegPath,
            "-y", "-v", "error",
            "-i", videoPath.toAbsolutePath().toString(),
            "-map", "0:" + streamIndex,
            "-c:s", "copy",
            caminhoSaida.toAbsolutePath().toString()
        );

        try {
            Process process = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();
            
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new ExtratorException("ffmpeg falhou: " + output);
            }

            if (!Files.exists(caminhoSaida)) {
                throw new ExtratorException("Extração finalizou mas arquivo não foi encontrado em: " + caminhoSaida);
            }

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new ExtratorException("Falha ao invocar ffmpeg para o arquivo: " + videoPath, e);
        }
    }
}
