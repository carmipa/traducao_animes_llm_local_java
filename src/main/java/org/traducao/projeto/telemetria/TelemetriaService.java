package org.traducao.projeto.telemetria;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class TelemetriaService {

    private static final Logger log = LoggerFactory.getLogger(TelemetriaService.class);
    private static final String NOME_ARQUIVO_TELEMETRIA = "telemetria_compartilhada.json";

    // Local canônico dentro do próprio projeto onde a telemetria é sempre
    // mesclada e persistida a cada registro, para sobreviver a restarts do
    // servidor e não depender só do lote em memória (que é limpo a cada
    // análise via limparLote()). É o que o painel web lê em gerarResumo().
    private static final Path PASTA_TELEMETRIA_PROJETO = Path.of("logs");

    private final ObjectMapper objectMapper;
    private final List<MidiaTelemetria> loteMidia = new ArrayList<>();
    private final List<LlmTelemetria> loteLlm = new ArrayList<>();

    public TelemetriaService() {
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public synchronized void registrarMidia(MidiaTelemetria midia) {
        if (midia != null) {
            loteMidia.add(midia);
            salvar(PASTA_TELEMETRIA_PROJETO);
        }
    }

    public synchronized void registrarTraducao(LlmTelemetria traducao) {
        if (traducao != null) {
            loteLlm.add(traducao);
            salvar(PASTA_TELEMETRIA_PROJETO);
        }
    }

    public synchronized void limparLote() {
        loteMidia.clear();
        loteLlm.clear();
    }

    /**
     * Mescla o lote atual com a telemetria existente na pasta de relatórios e salva.
     */
    public synchronized Path salvar(Path pastaRelatorios) {
        if (pastaRelatorios == null) {
            log.warn("Pasta de relatórios nula. Não foi possível salvar a telemetria.");
            return null;
        }

        try {
            if (!Files.exists(pastaRelatorios)) {
                Files.createDirectories(pastaRelatorios);
            }

            Path caminhoTelemetria = pastaRelatorios.resolve(NOME_ARQUIVO_TELEMETRIA);

            // 1. Carrega dados existentes se o arquivo já existir no disco
            Map<String, MidiaTelemetria> bancoMidia = new LinkedHashMap<>();
            Map<String, LlmTelemetria> bancoLlm = new LinkedHashMap<>();
            carregarBancoPersistido(caminhoTelemetria, bancoMidia, bancoLlm);

            // 2. Mescla os novos registros do lote atual
            for (MidiaTelemetria m : loteMidia) {
                bancoMidia.put(m.nomeArquivo(), m);
            }
            for (LlmTelemetria l : loteLlm) {
                bancoLlm.put(l.nomeEpisodio(), l);
            }

            // 3. Monta o objeto final para persistir
            ObjectNode rootNode = objectMapper.createObjectNode();
            
            ArrayNode midiasArray = objectMapper.valueToTree(new ArrayList<>(bancoMidia.values()));
            ArrayNode llmArray = objectMapper.valueToTree(new ArrayList<>(bancoLlm.values()));
            
            rootNode.set("midias", midiasArray);
            rootNode.set("traducoesLlm", llmArray);

            objectMapper.writeValue(caminhoTelemetria.toFile(), rootNode);
            
            log.info("Telemetria unificada salva com sucesso: {} mídias, {} traduções em: {}", 
                bancoMidia.size(), bancoLlm.size(), caminhoTelemetria);
            
            return caminhoTelemetria;

        } catch (IOException e) {
            log.error("Erro ao salvar a telemetria unificada no disco: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Monta o resumo serializável consumido pelo painel "Telemetria" da
     * interface web. Lê o histórico canônico persistido em
     * {@code logs/telemetria_compartilhada.json} (mesclado a cada chamada de
     * {@link #registrarMidia} / {@link #registrarTraducao}), por isso reflete
     * o total acumulado do projeto e sobrevive a restarts do servidor — não
     * só o lote em memória da sessão atual, que {@link #limparLote()} zera a
     * cada nova análise de mídia. A contagem de arquivos de cache é sempre
     * lida diretamente do diretório informado.
     */
    public synchronized TelemetriaResumo gerarResumo(Path diretorioCache) {
        int cacheCount = contarArquivosCache(diretorioCache);

        Map<String, MidiaTelemetria> bancoMidia = new LinkedHashMap<>();
        Map<String, LlmTelemetria> bancoLlm = new LinkedHashMap<>();
        carregarBancoPersistido(PASTA_TELEMETRIA_PROJETO.resolve(NOME_ARQUIVO_TELEMETRIA), bancoMidia, bancoLlm);
        for (MidiaTelemetria m : loteMidia) {
            bancoMidia.put(m.nomeArquivo(), m);
        }
        for (LlmTelemetria l : loteLlm) {
            bancoLlm.put(l.nomeEpisodio(), l);
        }

        int totalLinhas = bancoLlm.values().stream().mapToInt(l -> valorOuZero(l.totalLinhas())).sum();
        int totalCacheHits = bancoLlm.values().stream().mapToInt(l -> valorOuZero(l.falasDoCache())).sum();
        long tempoTotalMs = bancoLlm.values().stream().mapToLong(l -> l.tempoTotalMs() != null ? l.tempoTotalMs() : 0L).sum();
        long tempoMedioPorLinhaMs = totalLinhas > 0 ? tempoTotalMs / totalLinhas : 0L;

        List<OperacaoHistorico> historico = new ArrayList<>();
        for (LlmTelemetria l : bancoLlm.values()) {
            historico.add(new OperacaoHistorico("Tradução LLM", l.nomeEpisodio(), formatarDuracaoMs(l.tempoTotalMs()), null));
        }
        for (MidiaTelemetria m : bancoMidia.values()) {
            historico.add(new OperacaoHistorico("Análise de Mídia", m.nomeArquivo(), null, null));
        }

        return new TelemetriaResumo(cacheCount, bancoLlm.size(), totalLinhas, tempoMedioPorLinhaMs, totalCacheHits, historico);
    }

    /**
     * Carrega o JSON consolidado existente (se houver) em {@code caminho}
     * para dentro dos mapas informados, indexados por nome de arquivo/episódio.
     */
    private void carregarBancoPersistido(Path caminho, Map<String, MidiaTelemetria> bancoMidia, Map<String, LlmTelemetria> bancoLlm) {
        if (!Files.exists(caminho)) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(caminho.toFile());

            JsonNode midiasNode = root.get("midias");
            if (midiasNode != null && midiasNode.isArray()) {
                List<MidiaTelemetria> anterioresMidia = objectMapper.convertValue(
                    midiasNode,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, MidiaTelemetria.class)
                );
                for (MidiaTelemetria m : anterioresMidia) {
                    bancoMidia.put(m.nomeArquivo(), m);
                }
            }

            JsonNode llmNode = root.get("traducoesLlm");
            if (llmNode != null && llmNode.isArray()) {
                List<LlmTelemetria> anterioresLlm = objectMapper.convertValue(
                    llmNode,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, LlmTelemetria.class)
                );
                for (LlmTelemetria l : anterioresLlm) {
                    bancoLlm.put(l.nomeEpisodio(), l);
                }
            }

            log.info("Carregadas entradas anteriores: {} mídias, {} traduções do arquivo {}.",
                bancoMidia.size(), bancoLlm.size(), caminho);
        } catch (IOException e) {
            log.warn("Não foi possível ler a telemetria consolidada existente em {}. Erro: {}", caminho, e.getMessage());
        }
    }

    private int contarArquivosCache(Path diretorioCache) {
        if (diretorioCache == null || !Files.isDirectory(diretorioCache)) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(diretorioCache)) {
            return (int) walk.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".cache.json"))
                .count();
        } catch (IOException e) {
            log.warn("Não foi possível contar os arquivos de cache em {}: {}", diretorioCache, e.getMessage());
            return 0;
        }
    }

    private int valorOuZero(Integer valor) {
        return valor != null ? valor : 0;
    }

    private String formatarDuracaoMs(Long ms) {
        if (ms == null) {
            return null;
        }
        long segundos = ms / 1000;
        return segundos >= 60 ? (segundos / 60) + "min " + (segundos % 60) + "s" : segundos + "s";
    }
}
