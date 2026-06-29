package org.traducao.projeto.raspagemRevisao.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.traducao.projeto.raspagemCorrecao.infrastructure.GoogleTranslateScraper;
import org.traducao.projeto.raspagemRevisao.domain.ResultadoDeteccaoConcordancia;
import org.traducao.projeto.telemetria.OperacaoTelemetria;
import org.traducao.projeto.telemetria.TelemetriaService;
import org.traducao.projeto.traducao.application.ValidadorTraducaoService;
import org.traducao.projeto.traducao.domain.exceptions.AlucinacaoDetectadaException;
import org.traducao.projeto.traducao.domain.legenda.DocumentoLegenda;
import org.traducao.projeto.traducao.domain.legenda.EventoLegenda;
import org.traducao.projeto.traducao.domain.ports.MistralPort;
import org.traducao.projeto.traducao.infrastructure.cache.EntradaCache;
import org.traducao.projeto.traducao.infrastructure.contexto.GerenciadorContexto;
import org.traducao.projeto.traducao.infrastructure.legenda.EscritorLegendaAss;
import org.traducao.projeto.traducao.infrastructure.legenda.LeitorLegendaAss;
import org.traducao.projeto.traducao.infrastructure.legenda.MascaradorTags;
import org.traducao.projeto.traducao.presentation.ui.AnsiCores;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class RevisarLegendasUseCase {

    public enum ModoRevisaoLegendas {
        GOOGLE,
        LLM_CONCORDANCIA
    }

    private static final Set<String> EXTENSOES = Set.of(".ass", ".ssa");
    private static final long PAUSA_GOOGLE_MS = 400;
    private static final Pattern CODIGO_EPISODIO = Pattern.compile("(?i)(S\\d{1,2}E\\d{1,3})");
    private static final Pattern SUFIXO_PTBR_TRACK = Pattern.compile("(?i)_PT-?BR(_Track\\d+)?$");

    private final LeitorLegendaAss leitor;
    private final EscritorLegendaAss escritor;
    private final GoogleTranslateScraper googleScraper;
    private final AuditorProblemasLegendaService auditor;
    private final ValidadorTraducaoService validador;
    private final ObjectMapper mapper;
    private final MistralPort mistralPort;
    private final MascaradorTags mascaradorTags;
    private final GerenciadorContexto gerenciadorContexto;
    private final TelemetriaService telemetriaService;

    public RevisarLegendasUseCase(
        LeitorLegendaAss leitor,
        EscritorLegendaAss escritor,
        GoogleTranslateScraper googleScraper,
        AuditorProblemasLegendaService auditor,
        ValidadorTraducaoService validador,
        ObjectMapper mapper,
        MistralPort mistralPort,
        MascaradorTags mascaradorTags,
        GerenciadorContexto gerenciadorContexto,
        TelemetriaService telemetriaService
    ) {
        this.leitor = leitor;
        this.escritor = escritor;
        this.googleScraper = googleScraper;
        this.auditor = auditor;
        this.validador = validador;
        this.mapper = mapper;
        this.mistralPort = mistralPort;
        this.mascaradorTags = mascaradorTags;
        this.gerenciadorContexto = gerenciadorContexto;
        this.telemetriaService = telemetriaService;
    }

    /**
     * Valida a pasta informada antes de iniciar a revisão (API/CLI).
     * Retorna mensagem de erro quando a pasta não contém legendas .ass/.ssa.
     */
    public Optional<String> validarPastaEntrada(Path pasta) {
        if (pasta == null || pasta.toString().isBlank()) {
            return Optional.of("Pasta com legendas traduzidas em português (.ass) é obrigatória.");
        }
        if (!Files.isDirectory(pasta)) {
            return Optional.of("Pasta não encontrada: " + pasta.toAbsolutePath());
        }

        try (Stream<Path> stream = Files.list(pasta)) {
            List<Path> arquivos = stream.filter(Files::isRegularFile).toList();
            if (arquivos.stream().anyMatch(this::temExtensaoSuportada)) {
                return Optional.empty();
            }

            long cacheJson = arquivos.stream()
                .filter(p -> p.getFileName().toString().endsWith(".cache.json"))
                .count();
            String abs = pasta.toAbsolutePath().toString().replace('\\', '/').toLowerCase();
            boolean pareceCache = cacheJson > 0
                || abs.contains("/cache/")
                || abs.endsWith("/cache");

            if (pareceCache) {
                return Optional.of(
                    "Esta pasta parece ser de CACHE ("
                        + cacheJson + " arquivo(s) .cache.json, nenhum .ass/.ssa). "
                        + "Informe a pasta com os arquivos de legenda traduzidos (.ass), por exemplo: "
                        + "E:\\animes\\DANMACHI\\temporada_5\\legendas_extraidas_ass.");
            }
            return Optional.of(
                "Nenhum arquivo .ass/.ssa encontrado em: " + pasta.toAbsolutePath());
        } catch (IOException e) {
            return Optional.of("Erro ao ler pasta: " + e.getMessage());
        }
    }

    /**
     * @param pastaLegendasPt pasta com arquivos .ass/.ssa já traduzidos (obrigatório)
     * @param pastaLegendasEn pasta com legendas originais em inglês (opcional; busca na mesma pasta do PT)
     * @param pastaCache        pasta do cache *_ENG.cache.json (opcional; padrão {@code cache})
     * @param pastaSaida        pasta de saída (opcional; padrão = sobrescreve na pasta PT)
     */
    public ResultadoRevisaoLegendas executar(
        Path pastaLegendasPt,
        Path pastaLegendasEn,
        Path pastaCache,
        Path pastaSaida
    ) {
        return executar(pastaLegendasPt, pastaLegendasEn, pastaCache, pastaSaida,
            ModoRevisaoLegendas.GOOGLE, null);
    }

    public ResultadoRevisaoLegendas executar(
        Path pastaLegendasPt,
        Path pastaLegendasEn,
        Path pastaCache,
        Path pastaSaida,
        ModoRevisaoLegendas modo,
        String contextoId
    ) {
        long inicioMs = System.currentTimeMillis();
        if (modo == ModoRevisaoLegendas.LLM_CONCORDANCIA) {
            out("Iniciando revisão de concordância PT-BR (LLM) em legendas: "
                + pastaLegendasPt.toAbsolutePath());
            aplicarContextoLlm(contextoId);
        } else {
            out("Iniciando revisão de legendas traduzidas em: " + pastaLegendasPt.toAbsolutePath());
        }

        if (!Files.isDirectory(pastaLegendasPt)) {
            out(AnsiCores.RED + "Erro: pasta de legendas traduzidas inválida." + AnsiCores.RESET);
            return new ResultadoRevisaoLegendas(0, 0);
        }

        Path pastaEn = pastaLegendasEn != null ? pastaLegendasEn : pastaLegendasPt;
        Path cacheDir = pastaCache != null ? pastaCache : Path.of("cache");
        Path saidaDir = pastaSaida != null ? pastaSaida : pastaLegendasPt;

        int[] arquivosProcessados = {0};
        int[] falasCorrigidas = {0};
        int[] falasComProblema = {0};
        int[] falasAuditadas = {0};
        int[] falasSemOriginal = {0};

        try (Stream<Path> stream = Files.list(pastaLegendasPt)) {
            List<Path> arquivos = stream
                .filter(Files::isRegularFile)
                .filter(this::temExtensaoSuportada)
                .filter(this::eLegendaTraduzida)
                .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                .toList();

            if (arquivos.isEmpty()) {
                Optional<String> erro = validarPastaEntrada(pastaLegendasPt);
                out(AnsiCores.YELLOW + erro.orElse("Nenhum arquivo .ass/.ssa traduzido encontrado na pasta.")
                    + AnsiCores.RESET);
                registrarTelemetria(pastaLegendasPt, inicioMs, 0, 0, 0, 0, 0, modo);
                return new ResultadoRevisaoLegendas(0, 0);
            }

            out("Originais EN: .ass em " + pastaEn.toAbsolutePath()
                + " (se existir) + cache/ em " + cacheDir.toAbsolutePath());

            for (Path arquivoPt : arquivos) {
                processarArquivo(
                    arquivoPt, pastaEn, cacheDir, saidaDir, modo,
                    arquivosProcessados, falasCorrigidas, falasComProblema,
                    falasAuditadas, falasSemOriginal);
            }
        } catch (IOException e) {
            out(AnsiCores.RED + "Erro ao listar legendas: " + e.getMessage() + AnsiCores.RESET);
            registrarTelemetria(pastaLegendasPt, inicioMs, arquivosProcessados[0], falasComProblema[0],
                falasCorrigidas[0], falasAuditadas[0], falasSemOriginal[0], modo);
            return new ResultadoRevisaoLegendas(0, 0);
        }

        out("Arquivos analisados: " + arquivosProcessados[0]);
        out("Falas auditadas: " + falasAuditadas[0]);
        out("Falas sem original EN (ignoradas): " + falasSemOriginal[0]);
        out("Falas com problemas detectados: " + falasComProblema[0]);
        if (modo == ModoRevisaoLegendas.LLM_CONCORDANCIA) {
            out("Falas corrigidas via LLM e salvas: " + falasCorrigidas[0]);
        } else {
            out("Falas corrigidas via Google e salvas: " + falasCorrigidas[0]);
        }
        registrarTelemetria(pastaLegendasPt, inicioMs, arquivosProcessados[0], falasComProblema[0],
            falasCorrigidas[0], falasAuditadas[0], falasSemOriginal[0], modo);
        return new ResultadoRevisaoLegendas(arquivosProcessados[0], falasCorrigidas[0]);
    }

    private void aplicarContextoLlm(String contextoId) {
        if (contextoId != null && !contextoId.isBlank()) {
            if (gerenciadorContexto.existeContexto(contextoId)) {
                gerenciadorContexto.definirContextoAtivo(contextoId);
                out(AnsiCores.CYAN + "Contexto ativo: "
                    + gerenciadorContexto.obterNomeContextoAtivo() + AnsiCores.RESET);
            } else {
                out(AnsiCores.YELLOW + "Contexto desconhecido \""
                    + contextoId + "\" — usando contexto padrão." + AnsiCores.RESET);
            }
        }
    }

    private void registrarTelemetria(
        Path pastaLegendasPt,
        long inicioMs,
        int arquivos,
        int problemas,
        int corrigidas,
        int auditadas,
        int semOriginal,
        ModoRevisaoLegendas modo
    ) {
        long duracaoMs = System.currentTimeMillis() - inicioMs;
        boolean llm = modo == ModoRevisaoLegendas.LLM_CONCORDANCIA;
        String nomeOperacao = llm
            ? "Revisão Concordância (.ass LLM)"
            : "Revisão Legendas (.ass Google)";
        OperacaoTelemetria operacao = TelemetriaService.criarOperacao(
            nomeOperacao,
            pastaLegendasPt.toAbsolutePath().toString(),
            duracaoMs,
            arquivos,
            problemas,
            corrigidas
        );
        String relatorio = llm ? """
            REVISÃO DE CONCORDÂNCIA PT-BR (.ass via LLM)
            ============================================
            Pasta: %s
            Duração: %s
            Arquivos analisados: %d
            Falas auditadas: %d
            Falas sem original EN (ignoradas): %d
            Problemas detectados: %d
            Falas corrigidas via LLM: %d
            """.formatted(
            pastaLegendasPt.toAbsolutePath(),
            formatarDuracaoMs(duracaoMs),
            arquivos,
            auditadas,
            semOriginal,
            problemas,
            corrigidas
        ) : """
            REVISÃO DE LEGENDAS (.ass)
            ==========================
            Pasta: %s
            Duração: %s
            Arquivos analisados: %d
            Falas auditadas: %d
            Falas sem original EN (ignoradas): %d
            Problemas detectados: %d
            Falas corrigidas via Google: %d
            """.formatted(
            pastaLegendasPt.toAbsolutePath(),
            formatarDuracaoMs(duracaoMs),
            arquivos,
            auditadas,
            semOriginal,
            problemas,
            corrigidas
        );
        telemetriaService.finalizarOperacao(
            operacao, pastaLegendasPt,
            llm ? "revisao_concordancia_legendas" : "revisao_legendas",
            relatorio);
        out("Relatório salvo em: " + TelemetriaService.resolverPastaRelatorios(pastaLegendasPt));
    }

    private String formatarDuracaoMs(long ms) {
        long segundos = ms / 1000;
        return segundos >= 60 ? (segundos / 60) + "min " + (segundos % 60) + "s" : segundos + "s";
    }

    private void out(String mensagem) {
        System.out.println(mensagem);
    }

    private void processarArquivo(
        Path arquivoPt,
        Path pastaLegendasEn,
        Path cacheDir,
        Path saidaDir,
        ModoRevisaoLegendas modo,
        int[] totalArquivos,
        int[] totalCorrigidas,
        int[] totalProblemas,
        int[] totalAuditadas,
        int[] totalSemOriginal
    ) {
        totalArquivos[0]++;
        out("\nAnalisando legenda: " + arquivoPt.getFileName());

        DocumentoLegenda documentoPt = leitor.ler(arquivoPt);
        Path arquivoEn = resolverArquivoOriginal(arquivoPt, pastaLegendasEn);
        Map<Integer, String> originaisPorIndice = carregarOriginaisDeLegenda(arquivoEn);

        Path cachePath = resolverArquivoCache(arquivoPt, cacheDir);
        List<EntradaCache> entradasCache = carregarEntradasCache(cachePath);
        Map<String, String> originalPorTraduzido = indexarOriginalPorTraduzido(entradasCache);
        for (EntradaCache entrada : entradasCache) {
            if (entrada.original() != null && !entrada.original().isBlank()) {
                originaisPorIndice.putIfAbsent(entrada.indice(), entrada.original());
            }
        }

        if (!entradasCache.isEmpty()) {
            out("  Cache carregado: " + cachePath.getFileName()
                + " (" + entradasCache.size() + " entradas EN)");
        } else {
            out(AnsiCores.YELLOW + "  Aviso: cache EN não encontrado. Procurado em: "
                + cacheDir.toAbsolutePath()
                + " (esperado algo como " + cachePath.getFileName() + ")"
                + AnsiCores.RESET);
        }
        if (Files.isRegularFile(arquivoEn) && !originaisPorIndice.isEmpty()) {
            out("  Legenda .ass EN: " + arquivoEn.getFileName());
        }

        List<EventoLegenda> eventosAtualizados = new ArrayList<>();
        int corrigidasNesteArquivo = 0;
        int problemasNesteArquivo = 0;
        int falasAuditadas = 0;
        int falasSemOriginal = 0;
        boolean modificado = false;

        for (EventoLegenda evento : documentoPt.eventos()) {
            if (!evento.isDialogo() || evento.texto() == null || evento.texto().isBlank()) {
                eventosAtualizados.add(evento);
                continue;
            }

            if (deveIgnorarAuditoria(evento, evento.texto())) {
                eventosAtualizados.add(evento);
                continue;
            }

            String textoNormalizado = evento.texto();
            String textoCorrigidoKaraoke = consertarKaraokeQuebrado(textoNormalizado);
            if (!textoNormalizado.equals(textoCorrigidoKaraoke)) {
                evento = evento.comTexto(textoCorrigidoKaraoke);
                modificado = true;
                corrigidasNesteArquivo++;
                out("  -> Karaoke corrigido na linha " + evento.indice() + ":");
                out("     De : " + textoNormalizado);
                out("     Para: " + textoCorrigidoKaraoke);
            }

            String traducaoAtual = evento.texto();
            String originalEn = originaisPorIndice.get(evento.indice());
            if (originalEn == null || originalEn.isBlank()) {
                originalEn = originalPorTraduzido.get(normalizarTexto(traducaoAtual));
            }
            boolean temOriginalEn = originalEn != null && !originalEn.isBlank();
            if (!temOriginalEn) {
                falasSemOriginal++;
                totalSemOriginal[0]++;
                if (modo != ModoRevisaoLegendas.LLM_CONCORDANCIA) {
                    eventosAtualizados.add(evento);
                    continue;
                }
            }

            falasAuditadas++;
            totalAuditadas[0]++;

            ResultadoDeteccaoConcordancia auditoria = auditor.auditar(originalEn, traducaoAtual);
            if (!auditoria.suspeito()) {
                eventosAtualizados.add(evento);
                continue;
            }

            problemasNesteArquivo++;
            totalProblemas[0]++;

            out("  -> Linha " + evento.indice() + " [" + evento.estilo() + "]:");
            out("     EN: " + AnsiCores.YELLOW + originalEn + AnsiCores.RESET);
            out("     PT: " + AnsiCores.YELLOW + traducaoAtual + AnsiCores.RESET);
            auditoria.motivos().forEach(m ->
                out("     " + AnsiCores.DIM + "• " + m + AnsiCores.RESET));

            String novaTraducao;
            if (modo == ModoRevisaoLegendas.LLM_CONCORDANCIA) {
                Optional<String> revisadoOpt = tentarRevisarConcordancia(
                    originalEn, traducaoAtual, auditoria.motivos());
                if (revisadoOpt.isEmpty()) {
                    out("     " + AnsiCores.RED
                        + "Revisão não aplicada (LLM indisponível ou resposta inválida)."
                        + AnsiCores.RESET);
                    eventosAtualizados.add(evento);
                    continue;
                }
                novaTraducao = revisadoOpt.get();
                if (novaTraducao.equals(traducaoAtual)) {
                    out("     " + AnsiCores.DIM + "LLM manteve o texto original." + AnsiCores.RESET);
                    eventosAtualizados.add(evento);
                    continue;
                }
            } else {
                novaTraducao = googleScraper.traduzir(originalEn);
                pausaGoogle();

                if (novaTraducao.equals(originalEn) || novaTraducao.equals(traducaoAtual)) {
                    out("     " + AnsiCores.DIM + "Google manteve texto igual; sem alteração." + AnsiCores.RESET);
                    eventosAtualizados.add(evento);
                    continue;
                }
            }

            try {
                validador.validarFala(novaTraducao);
            } catch (AlucinacaoDetectadaException e) {
                out("     " + AnsiCores.RED + "Correção descartada: " + e.getMessage() + AnsiCores.RESET);
                eventosAtualizados.add(evento);
                continue;
            }

            ResultadoDeteccaoConcordancia posCorrecao = auditor.auditar(originalEn, novaTraducao);
            if (posCorrecao.suspeito() && posCorrecao.motivos().size() >= auditoria.motivos().size()) {
                String motivo = modo == ModoRevisaoLegendas.LLM_CONCORDANCIA
                    ? "Correção descartada: LLM não melhorou o problema."
                    : "Correção descartada: Google não melhorou o problema.";
                out("     " + AnsiCores.YELLOW + motivo + AnsiCores.RESET);
                eventosAtualizados.add(evento);
                continue;
            }

            out("     PT corrigido: " + AnsiCores.GREEN + novaTraducao + AnsiCores.RESET);
            eventosAtualizados.add(evento.comTexto(novaTraducao));
            corrigidasNesteArquivo++;
            modificado = true;
        }

        if (modificado) {
            DocumentoLegenda revisado = new DocumentoLegenda(
                documentoPt.cabecalho(),
                eventosAtualizados,
                documentoPt.quebraDeLinha(),
                documentoPt.comBom()
            );
            Path destino = saidaDir.resolve(arquivoPt.getFileName());
            escritor.escrever(destino, revisado);
            totalCorrigidas[0] += corrigidasNesteArquivo;
            out(AnsiCores.GREEN + "  [OK] " + corrigidasNesteArquivo
                + " fala(s) corrigida(s). Salvo em: " + destino.getFileName() + AnsiCores.RESET);
        } else if (problemasNesteArquivo > 0) {
            out(AnsiCores.YELLOW + "  Problemas encontrados, mas nenhuma correção aplicada."
                + AnsiCores.RESET);
        } else if (falasAuditadas == 0 && falasSemOriginal > 0) {
            out(AnsiCores.YELLOW + "  -> Nenhuma fala auditada ("
                + falasSemOriginal + " ignoradas por falta de original EN)." + AnsiCores.RESET);
        } else {
            out("  -> Nenhum problema detectado neste arquivo ("
                + falasAuditadas + " falas auditadas).");
        }
    }

    private boolean deveIgnorarAuditoria(EventoLegenda evento, String texto) {
        String estilo = evento.estilo() != null ? evento.estilo().toLowerCase() : "";
        if (estilo.contains("sign")) {
            return true;
        }
        String visivel = extrairTextoVisivel(texto);
        return estilo.contains("romaji") && visivel.equalsIgnoreCase("you");
    }

    private String consertarKaraokeQuebrado(String texto) {
        if (texto == null) return null;
        // Se encontrar chaves que contêm texto não iniciando com \ ou = (ex: {=2})
        // e substituí-las por \N seguido do texto.
        // O regex \{([^\\=}][^}]*)\} captura {conteúdo} onde 'conteúdo' não inicia com \, = ou }
        return texto.replaceAll("\\{([^\\\\=}][^}]*)\\}", "\\\\N$1");
    }

    private String extrairTextoVisivel(String texto) {
        return texto.replaceAll("\\{[^}]*\\}", "").replace("\\N", " ").trim();
    }

    private String normalizarTexto(String texto) {
        return texto == null ? "" : texto.replaceAll("\\s+", " ").trim();
    }

    private Path resolverArquivoCache(Path arquivoPt, Path cacheDir) {
        String baseLegenda = nomeBaseLegenda(arquivoPt);
        String baseMidia = normalizarBaseLegenda(baseLegenda);
        String codigoEpisodio = extrairCodigoEpisodio(baseLegenda);

        for (String candidato : candidatosNomeCache(baseLegenda, baseMidia)) {
            Path direto = cacheDir.resolve(candidato);
            if (Files.isRegularFile(direto)) {
                return direto;
            }
        }

        if (Files.isDirectory(cacheDir)) {
            try (Stream<Path> stream = Files.walk(cacheDir)) {
                return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> correspondeCache(p.getFileName().toString(), baseMidia, codigoEpisodio))
                    .findFirst()
                    .orElse(cacheDir.resolve(baseMidia + "_ENG.cache.json"));
            } catch (IOException e) {
                return cacheDir.resolve(baseMidia + "_ENG.cache.json");
            }
        }

        return cacheDir.resolve(baseMidia + "_ENG.cache.json");
    }

    private List<String> candidatosNomeCache(String baseLegenda, String baseMidia) {
        Set<String> candidatos = new LinkedHashSet<>();
        candidatos.add(baseMidia + "_ENG.cache.json");
        candidatos.add(baseMidia + ".cache.json");
        candidatos.add(baseLegenda + ".cache.json");
        candidatos.add(baseLegenda + "_ENG.cache.json");
        return List.copyOf(candidatos);
    }

    private boolean correspondeCache(String nomeArquivo, String baseMidia, String codigoEpisodio) {
        if (!nomeArquivo.toLowerCase().endsWith(".cache.json")) {
            return false;
        }
        String stem = nomeArquivo.substring(0, nomeArquivo.length() - ".cache.json".length());
        if (normalizarBaseLegenda(stem).equalsIgnoreCase(baseMidia)) {
            return true;
        }
        return codigoEpisodio != null
            && nomeArquivo.toUpperCase().contains(codigoEpisodio)
            && nomeArquivo.toUpperCase().contains("_ENG");
    }

    private String nomeBaseLegenda(Path arquivoPt) {
        String nome = arquivoPt.getFileName().toString();
        String ext = extensaoLegenda(nome);
        return nome.substring(0, nome.length() - ext.length());
    }

    private List<EntradaCache> carregarEntradasCache(Path cachePath) {
        if (!Files.isRegularFile(cachePath)) {
            return new ArrayList<>();
        }
        try {
            return mapper.readValue(cachePath.toFile(), new TypeReference<List<EntradaCache>>() {});
        } catch (IOException e) {
            out(AnsiCores.YELLOW + "  Aviso: não foi possível ler cache "
                + cachePath.getFileName() + AnsiCores.RESET);
            return new ArrayList<>();
        }
    }

    private Map<String, String> indexarOriginalPorTraduzido(List<EntradaCache> entradas) {
        Map<String, String> mapa = new HashMap<>();
        for (EntradaCache entrada : entradas) {
            if (entrada.traduzido() == null || entrada.traduzido().isBlank()) {
                continue;
            }
            if (entrada.original() == null || entrada.original().isBlank()) {
                continue;
            }
            mapa.putIfAbsent(normalizarTexto(entrada.traduzido()), entrada.original());
        }
        return mapa;
    }

    private Map<Integer, String> carregarOriginaisDeLegenda(Path arquivoEn) {
        Map<Integer, String> mapa = new HashMap<>();
        if (!Files.isRegularFile(arquivoEn)) {
            return mapa;
        }

        DocumentoLegenda docEn = leitor.ler(arquivoEn);
        for (EventoLegenda evento : docEn.eventos()) {
            if (evento.isDialogo() && evento.texto() != null && !evento.texto().isBlank()) {
                mapa.put(evento.indice(), evento.texto());
            }
        }
        return mapa;
    }

    private Path resolverArquivoOriginal(Path arquivoPt, Path pastaLegendasEn) {
        String nome = arquivoPt.getFileName().toString();
        String ext = extensaoLegenda(nome);
        String baseSemPt = normalizarBaseLegenda(nome.substring(0, nome.length() - ext.length()));
        String codigoEpisodio = extrairCodigoEpisodio(baseSemPt);

        Set<String> candidatos = new LinkedHashSet<>();
        candidatos.add(baseSemPt + ext);
        candidatos.add(baseSemPt + "_ENG" + ext);
        candidatos.add(baseSemPt + "_Eng" + ext);
        for (int track = 1; track <= 9; track++) {
            candidatos.add(baseSemPt + "_Track" + track + ext);
        }

        Matcher ptbrTrack = SUFIXO_PTBR_TRACK.matcher(nome.substring(0, nome.length() - ext.length()));
        if (ptbrTrack.find()) {
            String baseMidia = nome.substring(0, ptbrTrack.start());
            candidatos.add(baseMidia + "_Track2" + ext);
            candidatos.add(baseMidia + "_Track1" + ext);
            candidatos.add(baseMidia + ext);
        }

        for (String candidato : candidatos) {
            Path path = pastaLegendasEn.resolve(candidato);
            if (Files.isRegularFile(path) && !path.equals(arquivoPt) && !eLegendaTraduzida(path)) {
                return path;
            }
        }

        if (codigoEpisodio != null && Files.isDirectory(pastaLegendasEn)) {
            try (Stream<Path> stream = Files.list(pastaLegendasEn)) {
                return stream
                    .filter(Files::isRegularFile)
                    .filter(this::temExtensaoSuportada)
                    .filter(p -> !p.equals(arquivoPt))
                    .filter(p -> !eLegendaTraduzida(p))
                    .filter(p -> p.getFileName().toString().toUpperCase().contains(codigoEpisodio))
                    .min(Comparator.comparingInt(p -> preferenciaArquivoOriginal(p.getFileName().toString())))
                    .orElse(pastaLegendasEn.resolve(baseSemPt + ext));
            } catch (IOException e) {
                return pastaLegendasEn.resolve(baseSemPt + ext);
            }
        }

        return pastaLegendasEn.resolve(baseSemPt + ext);
    }

    private String sugerirNomeOriginal(Path arquivoPt) {
        String nome = arquivoPt.getFileName().toString();
        String ext = extensaoLegenda(nome);
        String base = nome.substring(0, nome.length() - ext.length());
        Matcher ptbrTrack = SUFIXO_PTBR_TRACK.matcher(base);
        if (ptbrTrack.find()) {
            return base.substring(0, ptbrTrack.start()) + "_Track2" + ext;
        }
        return normalizarBaseLegenda(base) + "_Track2" + ext;
    }

    private int preferenciaArquivoOriginal(String nome) {
        String n = nome.toLowerCase();
        if (n.contains("_track2")) {
            return 0;
        }
        if (n.contains("_eng")) {
            return 1;
        }
        if (n.contains("_track1")) {
            return 2;
        }
        return 10;
    }

    private boolean eLegendaTraduzida(Path arquivo) {
        String nome = arquivo.getFileName().toString().toLowerCase();
        return nome.contains("_ptbr") || nome.contains("_pt-br");
    }

    private String normalizarBaseLegenda(String base) {
        return base
            .replaceFirst("(?i)_PT-?BR(_Track\\d+)?$", "")
            .replaceFirst("(?i)_Track\\d+$", "")
            .replaceFirst("(?i)_ENG$", "");
    }

    private String extensaoLegenda(String nome) {
        return nome.toLowerCase().endsWith(".ssa") ? ".ssa" : ".ass";
    }

    private String extrairCodigoEpisodio(String nome) {
        Matcher matcher = CODIGO_EPISODIO.matcher(nome);
        return matcher.find() ? matcher.group(1).toUpperCase() : null;
    }

    private Optional<String> tentarRevisarConcordancia(
        String original, String traduzido, List<String> motivos
    ) {
        String textoOriginal = original != null ? original : "";
        MascaradorTags.Mascarado mascOriginal = mascaradorTags.mascarar(textoOriginal);
        MascaradorTags.Mascarado mascTraduzido = mascaradorTags.mascarar(traduzido);

        Optional<String> resposta = mistralPort.revisarConcordancia(
            mascOriginal.texto(),
            mascTraduzido.texto(),
            motivos
        );

        if (resposta.isEmpty()) {
            return Optional.empty();
        }

        try {
            String desmascarado = mascaradorTags.desmascarar(resposta.get(), mascTraduzido.tags());
            validador.validarFala(desmascarado);
            return Optional.of(desmascarado);
        } catch (AlucinacaoDetectadaException e) {
            return Optional.empty();
        }
    }

    private void pausaGoogle() {
        try {
            Thread.sleep(PAUSA_GOOGLE_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean temExtensaoSuportada(Path arquivo) {
        String nome = arquivo.getFileName().toString().toLowerCase();
        return EXTENSOES.stream().anyMatch(nome::endsWith);
    }
}
