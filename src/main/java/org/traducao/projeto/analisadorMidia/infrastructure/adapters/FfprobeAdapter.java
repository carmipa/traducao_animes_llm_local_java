package org.traducao.projeto.analisadorMidia.infrastructure.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.traducao.projeto.analisadorMidia.domain.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Component
public class FfprobeAdapter {

    private static final Logger log = LoggerFactory.getLogger(FfprobeAdapter.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Executa ffprobe no vídeo e obtém o JSON com as informações gerais e faixas.
     */
    public AuditoriaResultado analisarMidia(Path caminhoVideo) {
        List<String> cmd = List.of(
            "ffprobe", "-v", "quiet", "-print_format", "json", "-show_format", "-show_streams",
            caminhoVideo.toAbsolutePath().toString()
        );

        try {
            log.debug("Executando: {}", String.join(" ", cmd));
            Process process = new ProcessBuilder(cmd).start();

            byte[] stdoutBytes = process.getInputStream().readAllBytes();
            byte[] stderrBytes = process.getErrorStream().readAllBytes();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                String stderr = new String(stderrBytes, StandardCharsets.UTF_8);
                throw new AnalisadorException("ffprobe falhou com código " + exitCode + ". Erro: " + stderr);
            }

            String jsonString = new String(stdoutBytes, StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(jsonString);

            // Parsing do Container
            JsonNode formatNode = root.get("format");
            ContainerInfo container = parseContainer(formatNode);

            // Parsing das faixas (streams)
            List<VideoInfo> videos = new ArrayList<>();
            List<AudioInfo> audios = new ArrayList<>();
            List<LegendaInfo> legendas = new ArrayList<>();

            JsonNode streamsNode = root.get("streams");
            int legendaIdx = 0; // index relativo para ffprobe select_streams s:<idx>

            if (streamsNode != null && streamsNode.isArray()) {
                for (JsonNode stream : streamsNode) {
                    int index = stream.path("index").asInt(-1);
                    String codecType = stream.path("codec_type").asText("");

                    if ("video".equals(codecType)) {
                        videos.add(parseVideo(stream, index));
                    } else if ("audio".equals(codecType)) {
                        audios.add(parseAudio(stream, index));
                    } else if ("subtitle".equals(codecType)) {
                        legendas.add(parseLegenda(stream, index, legendaIdx++));
                    }
                }
            }

            return new AuditoriaResultado(
                caminhoVideo,
                caminhoVideo.getFileName().toString(),
                container,
                videos,
                audios,
                legendas,
                new ArrayList<>()
            );

        } catch (IOException e) {
            throw new AnalisadorException("Erro de E/S ao executar o ffprobe em " + caminhoVideo, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AnalisadorException("Execução do ffprobe foi interrompida para " + caminhoVideo, e);
        }
    }

    /**
     * Executa ffprobe para extrair os timestamps de pacotes de uma legenda.
     * Retorna um array com [primeiroPts, ultimoPts].
     */
    public double[] obterTimestampsLegenda(Path caminhoVideo, int indexRelativoLegenda) {
        List<String> cmd = List.of(
            "ffprobe", "-v", "quiet", "-select_streams", "s:" + indexRelativoLegenda,
            "-show_entries", "packet=pts_time,duration_time", "-of", "json",
            caminhoVideo.toAbsolutePath().toString()
        );

        try {
            log.debug("Executando para pacotes de legenda s:{}: {}", indexRelativoLegenda, String.join(" ", cmd));
            Process process = new ProcessBuilder(cmd).start();

            byte[] stdoutBytes = process.getInputStream().readAllBytes();
            byte[] stderrBytes = process.getErrorStream().readAllBytes();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                String stderr = new String(stderrBytes, StandardCharsets.UTF_8);
                log.warn("ffprobe ao ler pacotes de legenda falhou com código {}. Erro: {}", exitCode, stderr);
                return null;
            }

            String jsonString = new String(stdoutBytes, StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(jsonString);
            JsonNode packetsNode = root.get("packets");

            if (packetsNode == null || !packetsNode.isArray() || packetsNode.isEmpty()) {
                return null;
            }

            Double primeiroPts = null;
            Double ultimoPts = null;

            // Busca primeiro pacote com pts_time válido
            for (JsonNode packet : packetsNode) {
                String ptsStr = packet.path("pts_time").asText(null);
                if (ptsStr != null) {
                    try {
                        primeiroPts = Double.parseDouble(ptsStr);
                        break;
                    } catch (NumberFormatException ignored) {}
                }
            }

            // Busca último pacote com pts_time válido de trás para frente
            for (int i = packetsNode.size() - 1; i >= 0; i--) {
                JsonNode packet = packetsNode.get(i);
                String ptsStr = packet.path("pts_time").asText(null);
                if (ptsStr != null) {
                    try {
                        double pts = Double.parseDouble(ptsStr);
                        double duration = 0.0;
                        String durStr = packet.path("duration_time").asText(null);
                        if (durStr != null) {
                            try {
                                duration = Double.parseDouble(durStr);
                            } catch (NumberFormatException ignored) {}
                        }
                        ultimoPts = pts + duration;
                        break;
                    } catch (NumberFormatException ignored) {}
                }
            }

            if (primeiroPts != null && ultimoPts != null) {
                return new double[]{primeiroPts, ultimoPts};
            }

        } catch (Exception e) {
            log.warn("Erro ao obter timestamps de pacotes para a legenda s:{}. Erro: {}", indexRelativoLegenda, e.getMessage());
        }

        return null;
    }

    private ContainerInfo parseContainer(JsonNode formatNode) {
        if (formatNode == null) {
            return new ContainerInfo("N/A", 0L, 0.0, 0L, "N/A");
        }

        String formato = formatNode.path("format_name").asText("N/A");
        long tamanhoBytes = formatNode.path("size").asLong(0L);
        double duracao = formatNode.path("duration").asDouble(0.0);
        long bitrate = formatNode.path("bit_rate").asLong(0L);
        
        String encoder = "N/A";
        JsonNode tags = formatNode.get("tags");
        if (tags != null) {
            encoder = tags.path("encoder").asText(tags.path("ENCODER").asText("N/A"));
        }

        return new ContainerInfo(formato, tamanhoBytes, duracao, bitrate, encoder);
    }

    private VideoInfo parseVideo(JsonNode stream, int index) {
        String codecId = stream.path("codec_name").asText("N/A").toUpperCase();
        String format = stream.path("codec_long_name").asText("N/A");
        int width = stream.path("width").asInt(0);
        int height = stream.path("height").asInt(0);

        // Identifica profundidade de cor por pix_fmt (ex: yuv420p10le -> 10 bits)
        int bitDepth = 8;
        String pixFmt = stream.path("pix_fmt").asText("");
        if (pixFmt.contains("10le") || pixFmt.contains("10be") || pixFmt.contains("10")) {
            bitDepth = 10;
        } else if (pixFmt.contains("12")) {
            bitDepth = 12;
        }

        // Calcula o FPS
        double fps = 0.0;
        String rFrameRate = stream.path("r_frame_rate").asText("0/0");
        if (rFrameRate.contains("/")) {
            try {
                String[] parts = rFrameRate.split("/");
                double num = Double.parseDouble(parts[0]);
                double den = Double.parseDouble(parts[1]);
                if (den > 0) {
                    fps = num / den;
                }
            } catch (Exception ignored) {}
        }

        String dar = stream.path("display_aspect_ratio").asText("N/A");
        long bitrate = stream.path("bit_rate").asLong(0L);

        return new VideoInfo(index, codecId, format, width, height, bitDepth, fps, dar, bitrate);
    }

    private AudioInfo parseAudio(JsonNode stream, int index) {
        String codec = stream.path("codec_name").asText("N/A").toUpperCase();
        int channels = stream.path("channels").asInt(0);
        double sampleRate = stream.path("sample_rate").asDouble(0.0) / 1000.0;
        long bitrate = stream.path("bit_rate").asLong(0L);

        String idioma = "Desconhecido";
        String titulo = "(Sem titulo)";

        JsonNode tags = stream.get("tags");
        if (tags != null) {
            idioma = tags.path("language").asText(tags.path("LANGUAGE").asText("Desconhecido"));
            titulo = tags.path("title").asText(tags.path("TITLE").asText("(Sem titulo)"));
        }

        return new AudioInfo(index, idioma, codec, channels, sampleRate, bitrate, titulo);
    }

    private LegendaInfo parseLegenda(JsonNode stream, int index, int indexRelativo) {
        String format = stream.path("codec_name").asText("N/A").toUpperCase();
        String codecId = stream.path("codec_name").asText("N/A");

        String idioma = "Desconhecido";
        String titulo = "(Sem titulo)";
        double duracao = stream.path("duration").asDouble(0.0);

        JsonNode tags = stream.get("tags");
        if (tags != null) {
            idioma = tags.path("language").asText(tags.path("LANGUAGE").asText("Desconhecido"));
            titulo = tags.path("title").asText(tags.path("TITLE").asText("(Sem titulo)"));
            
            // Tenta obter duracao das tags de duracao do mkv
            String durationTag = tags.path("DURATION").asText(tags.path("duration").asText(""));
            if (!durationTag.isBlank() && duracao <= 0.0) {
                duracao = converterDuracaoTagParaSegundos(durationTag);
            }
        }

        return new LegendaInfo(
            index, indexRelativo, idioma, format, codecId, titulo,
            null, null, duracao > 0.0 ? duracao : null, null,
            "Metadados", null, null, null, null
        );
    }

    private double converterDuracaoTagParaSegundos(String durTag) {
        try {
            durTag = durTag.replace(',', '.');
            String[] parts = durTag.split(":");
            if (parts.length == 3) {
                double h = Double.parseDouble(parts[0]);
                double m = Double.parseDouble(parts[1]);
                double s = Double.parseDouble(parts[2]);
                return h * 3600.0 + m * 60.0 + s;
            } else if (parts.length == 2) {
                double m = Double.parseDouble(parts[0]);
                double s = Double.parseDouble(parts[1]);
                return m * 60.0 + s;
            }
        } catch (Exception ignored) {}
        return 0.0;
    }
}
