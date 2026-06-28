package org.traducao.projeto.telemetria;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
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
    private static final DateTimeFormatter TIMESTAMP_RELATORIO =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final ObjectMapper objectMapper;
    private final List<MidiaTelemetria> loteMidia = new ArrayList<>();
    private final List<LlmTelemetria> loteLlm = new ArrayList<>();
    private final List<OperacaoTelemetria> loteOperacoes = new ArrayList<>();

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

    public synchronized void registrarOperacao(OperacaoTelemetria operacao) {
        if (operacao != null) {
            loteOperacoes.add(operacao);
            persistirCanonico();
            log.info("Telemetria de operação registrada: {} — {} ({} arquivos, {} detectados, {} corrigidos)",
                operacao.tipo(), operacao.detalhe(), valorOuZero(operacao.arquivosProcessados()),
                valorOuZero(operacao.itensDetectados()), valorOuZero(operacao.itensCorrigidos()));
        }
    }

    /**
     * Registra telemetria canônica em {@code logs/}, grava relatório .txt/.json em
     * {@code relatorios/} (mesmo padrão da análise de mídia) e copia o JSON unificado
     * para a pasta de relatórios da operação.
     */
    public synchronized void finalizarOperacao(
        OperacaoTelemetria operacao,
        Path pastaEntrada,
        String prefixoRelatorio,
        String conteudoRelatorio
    ) {
        if (operacao == null) {
            return;
        }
        registrarOperacao(operacao);
        Path pastaRelatorios = resolverPastaRelatorios(pastaEntrada);
        try {
            salvarRelatorioOperacao(pastaRelatorios, prefixoRelatorio, operacao, conteudoRelatorio);
            salvar(pastaRelatorios);
            log.info("Relatório e telemetria da operação persistidos em: {}", pastaRelatorios);
        } catch (IOException e) {
            log.warn("Falha ao salvar relatório da operação em {}: {}", pastaRelatorios, e.getMessage());
        }
    }

    public static Path resolverPastaRelatorios(Path entrada) {
        if (entrada == null) {
            return Path.of("relatorios", "operacao").toAbsolutePath();
        }
        String nomeDir = entrada.getFileName().toString();
        if (nomeDir.isBlank()) {
            nomeDir = "operacao";
        }
        return Path.of("relatorios", nomeDir).toAbsolutePath();
    }

    public void salvarRelatorioOperacao(
        Path pastaRelatorios,
        String prefixo,
        OperacaoTelemetria operacao,
        String conteudoRelatorio
    ) throws IOException {
        Files.createDirectories(pastaRelatorios);
        String timestamp = TIMESTAMP_RELATORIO.format(LocalDateTime.now());
        Path arquivoTxt = pastaRelatorios.resolve(prefixo + "_" + timestamp + ".txt");
        Path arquivoJson = pastaRelatorios.resolve(prefixo + "_" + timestamp + ".json");
        Files.writeString(arquivoTxt, conteudoRelatorio, StandardCharsets.UTF_8);
        objectMapper.writeValue(arquivoJson.toFile(), operacao);
        log.info("Relatório textual salvo em: {}", arquivoTxt);
        log.info("Relatório JSON salvo em: {}", arquivoJson);
    }

    public synchronized void limparLote() {
        loteMidia.clear();
        loteLlm.clear();
        loteOperacoes.clear();
    }

    /**
     * Persiste a telemetria canônica em {@code logs/telemetria_compartilhada.json}.
     * Se {@code pastaRelatorios} for informada e diferente de {@code logs/}, copia
     * o arquivo unificado para lá (padrão usado pela análise de mídia).
     */
    public synchronized Path salvar(Path pastaRelatorios) {
        Path caminhoCanonico = persistirCanonico();
        if (caminhoCanonico == null || pastaRelatorios == null) {
            return caminhoCanonico;
        }
        if (pastaRelatorios.normalize().toAbsolutePath()
            .equals(PASTA_TELEMETRIA_PROJETO.normalize().toAbsolutePath())) {
            return caminhoCanonico;
        }
        try {
            Files.createDirectories(pastaRelatorios);
            Path destino = pastaRelatorios.resolve(NOME_ARQUIVO_TELEMETRIA);
            Files.copy(caminhoCanonico, destino, StandardCopyOption.REPLACE_EXISTING);
            log.info("Cópia da telemetria unificada salva em: {}", destino);
            return destino;
        } catch (IOException e) {
            log.error("Erro ao copiar telemetria para {}: {}", pastaRelatorios, e.getMessage(), e);
            return caminhoCanonico;
        }
    }

    private synchronized Path persistirCanonico() {
        try {
            if (!Files.exists(PASTA_TELEMETRIA_PROJETO)) {
                Files.createDirectories(PASTA_TELEMETRIA_PROJETO);
            }

            Path caminhoTelemetria = PASTA_TELEMETRIA_PROJETO.resolve(NOME_ARQUIVO_TELEMETRIA);

            Map<String, MidiaTelemetria> bancoMidia = new LinkedHashMap<>();
            Map<String, LlmTelemetria> bancoLlm = new LinkedHashMap<>();
            List<OperacaoTelemetria> bancoOperacoes = new ArrayList<>();
            carregarBancoPersistido(caminhoTelemetria, bancoMidia, bancoLlm, bancoOperacoes);

            for (MidiaTelemetria m : loteMidia) {
                bancoMidia.put(m.nomeArquivo(), m);
            }
            for (LlmTelemetria l : loteLlm) {
                bancoLlm.put(l.nomeEpisodio(), l);
            }
            bancoOperacoes.addAll(loteOperacoes);
            loteOperacoes.clear();

            ObjectNode rootNode = objectMapper.createObjectNode();
            rootNode.set("midias", objectMapper.valueToTree(new ArrayList<>(bancoMidia.values())));
            rootNode.set("traducoesLlm", objectMapper.valueToTree(new ArrayList<>(bancoLlm.values())));
            rootNode.set("operacoes", objectMapper.valueToTree(bancoOperacoes));

            objectMapper.writeValue(caminhoTelemetria.toFile(), rootNode);

            log.info("Telemetria unificada salva com sucesso: {} mídias, {} traduções, {} operações em: {}",
                bancoMidia.size(), bancoLlm.size(), bancoOperacoes.size(), caminhoTelemetria);

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
     * {@link #registrarMidia} / {@link #registrarTraducao} / {@link #registrarOperacao}), por isso reflete
     * o total acumulado do projeto e sobrevive a restarts do servidor — não
     * só o lote em memória da sessão atual, que {@link #limparLote()} zera a
     * cada nova análise de mídia. A contagem de arquivos de cache é sempre
     * lida diretamente do diretório informado.
     */
    public synchronized TelemetriaResumo gerarResumo(Path diretorioCache) {
        int cacheCount = contarArquivosCache(diretorioCache);

        Map<String, MidiaTelemetria> bancoMidia = new LinkedHashMap<>();
        Map<String, LlmTelemetria> bancoLlm = new LinkedHashMap<>();
        List<OperacaoTelemetria> bancoOperacoes = new ArrayList<>();
        carregarBancoPersistido(PASTA_TELEMETRIA_PROJETO.resolve(NOME_ARQUIVO_TELEMETRIA), bancoMidia, bancoLlm, bancoOperacoes);
        for (MidiaTelemetria m : loteMidia) {
            bancoMidia.put(m.nomeArquivo(), m);
        }
        for (LlmTelemetria l : loteLlm) {
            bancoLlm.put(l.nomeEpisodio(), l);
        }
        bancoOperacoes.addAll(loteOperacoes);

        int totalLinhas = bancoLlm.values().stream().mapToInt(l -> valorOuZero(l.totalLinhas())).sum();
        int totalCacheHits = bancoLlm.values().stream().mapToInt(l -> valorOuZero(l.falasDoCache())).sum();
        long tempoTotalMs = bancoLlm.values().stream().mapToLong(l -> l.tempoTotalMs() != null ? l.tempoTotalMs() : 0L).sum();
        long tempoMedioPorLinhaMs = totalLinhas > 0 ? tempoTotalMs / totalLinhas : 0L;

        List<OperacaoHistorico> historico = montarHistorico(bancoOperacoes, bancoLlm, bancoMidia);

        return new TelemetriaResumo(cacheCount, bancoLlm.size(), totalLinhas, tempoMedioPorLinhaMs, totalCacheHits, historico);
    }

    private List<OperacaoHistorico> montarHistorico(
        List<OperacaoTelemetria> operacoes,
        Map<String, LlmTelemetria> traducoes,
        Map<String, MidiaTelemetria> midias
    ) {
        List<OperacaoHistorico> historico = new ArrayList<>();

        List<OperacaoTelemetria> operacoesOrdenadas = new ArrayList<>(operacoes);
        operacoesOrdenadas.sort(Comparator.comparing(
            OperacaoTelemetria::registradoEm,
            Comparator.nullsLast(Comparator.reverseOrder())
        ));
        for (OperacaoTelemetria op : operacoesOrdenadas) {
            historico.add(new OperacaoHistorico(
                op.tipo(),
                formatarDetalheOperacao(op),
                formatarDuracaoMs(op.tempoTotalMs()),
                calcularTaxaSucesso(op.itensDetectados(), op.itensCorrigidos())
            ));
        }

        for (LlmTelemetria l : traducoes.values()) {
            historico.add(new OperacaoHistorico("Tradução LLM", l.nomeEpisodio(), formatarDuracaoMs(l.tempoTotalMs()), null));
        }
        for (MidiaTelemetria m : midias.values()) {
            historico.add(new OperacaoHistorico("Análise de Mídia", m.nomeArquivo(), null, null));
        }
        return historico;
    }

    private String formatarDetalheOperacao(OperacaoTelemetria op) {
        StringBuilder sb = new StringBuilder(op.detalhe() != null ? op.detalhe() : "-");
        if (valorOuZero(op.arquivosProcessados()) > 0) {
            sb.append(" | arquivos: ").append(op.arquivosProcessados());
        }
        if (valorOuZero(op.itensDetectados()) > 0 || valorOuZero(op.itensCorrigidos()) > 0) {
            sb.append(" | detectados: ").append(valorOuZero(op.itensDetectados()));
            sb.append(", corrigidos: ").append(valorOuZero(op.itensCorrigidos()));
        }
        return sb.toString();
    }

    private Integer calcularTaxaSucesso(Integer detectados, Integer corrigidos) {
        int totalDetectados = valorOuZero(detectados);
        if (totalDetectados <= 0) {
            return null;
        }
        return Math.min(100, (valorOuZero(corrigidos) * 100) / totalDetectados);
    }

    public static OperacaoTelemetria criarOperacao(
        String tipo,
        String detalhe,
        long tempoTotalMs,
        int arquivosProcessados,
        int itensDetectados,
        int itensCorrigidos
    ) {
        return new OperacaoTelemetria(
            tipo,
            detalhe,
            tempoTotalMs,
            arquivosProcessados,
            itensDetectados,
            itensCorrigidos,
            Instant.now().toString()
        );
    }

    /**
     * Carrega o JSON consolidado existente (se houver) em {@code caminho}
     * para dentro dos mapas informados, indexados por nome de arquivo/episódio.
     */
    private void carregarBancoPersistido(
        Path caminho,
        Map<String, MidiaTelemetria> bancoMidia,
        Map<String, LlmTelemetria> bancoLlm,
        List<OperacaoTelemetria> bancoOperacoes
    ) {
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

            JsonNode operacoesNode = root.get("operacoes");
            if (operacoesNode != null && operacoesNode.isArray()) {
                List<OperacaoTelemetria> anterioresOperacoes = objectMapper.convertValue(
                    operacoesNode,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, OperacaoTelemetria.class)
                );
                bancoOperacoes.addAll(anterioresOperacoes);
            }

            log.info("Carregadas entradas anteriores: {} mídias, {} traduções, {} operações do arquivo {}.",
                bancoMidia.size(), bancoLlm.size(), bancoOperacoes.size(), caminho);
        } catch (IOException e) {
            log.warn("Não foi possível ler a telemetria consolidada existente em {}. Erro: {}", caminho, e.getMessage());
        }
    }

    private void carregarBancoPersistido(Path caminho, Map<String, MidiaTelemetria> bancoMidia, Map<String, LlmTelemetria> bancoLlm) {
        carregarBancoPersistido(caminho, bancoMidia, bancoLlm, new ArrayList<>());
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
