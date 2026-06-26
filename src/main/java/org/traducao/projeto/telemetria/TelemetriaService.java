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

@Service
public class TelemetriaService {

    private static final Logger log = LoggerFactory.getLogger(TelemetriaService.class);
    private static final String NOME_ARQUIVO_TELEMETRIA = "telemetria_compartilhada.json";
    
    private final ObjectMapper objectMapper;
    private final List<MidiaTelemetria> loteMidia = new ArrayList<>();
    private final List<LlmTelemetria> loteLlm = new ArrayList<>();

    public TelemetriaService() {
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public synchronized void registrarMidia(MidiaTelemetria midia) {
        if (midia != null) {
            loteMidia.add(midia);
        }
    }

    public synchronized void registrarTraducao(LlmTelemetria traducao) {
        if (traducao != null) {
            loteLlm.add(traducao);
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
            Map<String, MidiaTelemetria> bancoMidia = new LinkedHashMap<>();
            Map<String, LlmTelemetria> bancoLlm = new LinkedHashMap<>();

            // 1. Carrega dados existentes se o arquivo já existir no disco
            if (Files.exists(caminhoTelemetria)) {
                try {
                    JsonNode root = objectMapper.readTree(caminhoTelemetria.toFile());
                    
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
                    
                    log.info("Carregadas entradas anteriores: {} mídias, {} traduções do arquivo.", 
                        bancoMidia.size(), bancoLlm.size());
                } catch (IOException e) {
                    log.warn("Não foi possível ler a telemetria consolidada existente. Criando um novo banco. Erro: {}", e.getMessage());
                }
            }

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
}
