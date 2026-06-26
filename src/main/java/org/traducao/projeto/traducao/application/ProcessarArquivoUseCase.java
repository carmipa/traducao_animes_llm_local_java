package org.traducao.projeto.traducao.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.traducao.projeto.traducao.domain.Lote;
import org.traducao.projeto.traducao.domain.TraducaoLote;
import org.traducao.projeto.traducao.domain.exceptions.AlucinacaoDetectadaException;
import org.traducao.projeto.traducao.domain.exceptions.ArquivoLegendaException;
import org.traducao.projeto.traducao.domain.legenda.DocumentoLegenda;
import org.traducao.projeto.traducao.domain.legenda.EventoLegenda;
import org.traducao.projeto.traducao.domain.exceptions.TraducaoParcialException;
import org.traducao.projeto.traducao.infrastructure.cache.CacheTraducaoService;
import org.traducao.projeto.traducao.infrastructure.cache.EntradaCache;
import org.traducao.projeto.traducao.infrastructure.config.TradutorProperties;
import org.traducao.projeto.traducao.infrastructure.legenda.EscritorLegendaAss;
import org.traducao.projeto.traducao.infrastructure.legenda.LeitorLegendaAss;
import org.traducao.projeto.traducao.infrastructure.legenda.MascaradorTags;
import org.traducao.projeto.traducao.presentation.ui.ConsoleUILogger;
import org.traducao.projeto.traducao.presentation.ui.PastasExecucao;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Orquestra a tradução de um único arquivo de legenda: le -> reaproveita o
 * cache existente -> traduz só o que falta (deduplicando falas repetidas) ->
 * valida -> escreve a legenda final em PT-BR -> grava/atualiza o cache.
 * <p>
 * Correções manuais feitas pelo usuário no JSON de cache são respeitadas na
 * próxima execução: uma fala cujo texto original já tem tradução não-vazia no
 * cache nunca é reenviada ao LLM.
 */
@Service
public class ProcessarArquivoUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessarArquivoUseCase.class);

    private final LeitorLegendaAss leitor;
    private final EscritorLegendaAss escritor;
    private final MascaradorTags mascarador;
    private final CacheTraducaoService cacheService;
    private final ProcessarEpisodioUseCase processarEpisodioUseCase;
    private final ValidadorTraducaoService validador;
    private final TradutorProperties propriedades;
    private final ConsoleUILogger uiLogger;
    private final PastasExecucao pastasExecucao;

    public ProcessarArquivoUseCase(
        LeitorLegendaAss leitor,
        EscritorLegendaAss escritor,
        MascaradorTags mascarador,
        CacheTraducaoService cacheService,
        ProcessarEpisodioUseCase processarEpisodioUseCase,
        ValidadorTraducaoService validador,
        TradutorProperties propriedades,
        ConsoleUILogger uiLogger,
        PastasExecucao pastasExecucao
    ) {
        this.leitor = leitor;
        this.escritor = escritor;
        this.mascarador = mascarador;
        this.cacheService = cacheService;
        this.processarEpisodioUseCase = processarEpisodioUseCase;
        this.validador = validador;
        this.propriedades = propriedades;
        this.uiLogger = uiLogger;
        this.pastasExecucao = pastasExecucao;
    }

    public Path processar(Path arquivoEntrada) throws InterruptedException, ExecutionException {
        log.info("Lendo arquivo de legenda: {}", arquivoEntrada);
        DocumentoLegenda documento = leitor.ler(arquivoEntrada);

        Path arquivoCache = resolverArquivoCache(arquivoEntrada);
        Map<String, String> cacheExistente = cacheService.carregar(arquivoCache);

        List<EventoLegenda> eventosTraduziveis = documento.eventos().stream()
            .filter(this::isTraduzivel)
            .toList();
        log.info("{} fala(s) traduzível(eis) encontrada(s) em {}", eventosTraduziveis.size(), arquivoEntrada.getFileName());

        LinkedHashSet<String> textosTraduziveisDistintos = new LinkedHashSet<>();
        eventosTraduziveis.forEach(evento -> textosTraduziveisDistintos.add(evento.texto()));

        Map<String, String> cacheReaproveitavel = new HashMap<>();
        LinkedHashSet<String> textosPendentes = new LinkedHashSet<>();
        int cacheSuspeito = 0;
        for (String textoOriginal : textosTraduziveisDistintos) {
            String cacheado = cacheExistente.get(textoOriginal);
            if (cacheado != null && isCacheReaproveitavel(textoOriginal, cacheado)) {
                cacheReaproveitavel.put(textoOriginal, cacheado);
            } else {
                if (cacheado != null) {
                    cacheSuspeito++;
                }
                textosPendentes.add(textoOriginal);
            }
        }
        log.info("{} fala(s) distinta(s) reaproveitada(s) do cache, {} suspeita(s), {} pendente(s) de tradução",
            cacheReaproveitavel.size(), cacheSuspeito, textosPendentes.size());
        uiLogger.registrarFalasCache(cacheReaproveitavel.size());

        Map<String, String> traducoesNovas;
        try {
            traducoesNovas = traduzirPendentes(textosPendentes, arquivoEntrada.getFileName().toString());
            uiLogger.registrarFalasNovas(traducoesNovas.size());
        } catch (TraducaoParcialException e) {
            Map<String, String> traducoesParciais = e.getDicionarioParcial();
            if (traducoesParciais != null && !traducoesParciais.isEmpty()) {
                log.info("Salvando {} traducoes parciais no cache antes de abortar o episodio", traducoesParciais.size());
                Map<String, String> combinadasParciais = new HashMap<>(cacheReaproveitavel);
                combinadasParciais.putAll(traducoesParciais);

                List<EntradaCache> entradasCacheParcial = new ArrayList<>();
                for (EventoLegenda evento : documento.eventos()) {
                    if (isTraduzivel(evento)) {
                        String txtFinal = combinadasParciais.get(evento.texto());
                        if (txtFinal != null) {
                            entradasCacheParcial.add(new EntradaCache(
                                evento.indice(), evento.estilo(), evento.texto(), txtFinal,
                                propriedades.idiomaOriginal(), propriedades.idiomaTraduzido()));
                        }
                    }
                }
                if (!entradasCacheParcial.isEmpty()) {
                    cacheService.salvar(arquivoCache, entradasCacheParcial);
                }
            }
            throw e;
        }

        Map<String, String> traducoesCombinadas = new HashMap<>(cacheReaproveitavel);
        traducoesCombinadas.putAll(traducoesNovas);

        List<EventoLegenda> eventosFinais = new ArrayList<>(documento.eventos().size());
        List<EntradaCache> entradasCache = new ArrayList<>();
        for (EventoLegenda evento : documento.eventos()) {
            if (!isTraduzivel(evento)) {
                eventosFinais.add(evento);
                continue;
            }
            String textoFinal = traducoesCombinadas.get(evento.texto());
            if (textoFinal == null) {
                throw new ArquivoLegendaException(
                    "Falha interna: nenhuma tradução encontrada para a fala do evento " + evento.indice()
                        + " em " + arquivoEntrada);
            }
            try {
                validador.validarFala(textoFinal);
            } catch (AlucinacaoDetectadaException e) {
                // Não derruba milhares de falas já traduzidas por causa de 1 suspeita
                // nesta revalidação final: mantém o texto e sinaliza para revisão manual.
                log.warn("Fala suspeita mantida na revalidação final do evento {}: {}. Texto: \"{}\"",
                    evento.indice(), e.getMessage(), textoFinal);
                uiLogger.log("[ WARN ] Fala suspeita mantida (revise manualmente no cache): " + textoFinal);
            }
            eventosFinais.add(evento.comTexto(textoFinal));
            entradasCache.add(new EntradaCache(
                evento.indice(), evento.estilo(), evento.texto(), textoFinal,
                propriedades.idiomaOriginal(), propriedades.idiomaTraduzido()));
        }

        DocumentoLegenda documentoFinal = new DocumentoLegenda(
            documento.cabecalho(), eventosFinais, documento.quebraDeLinha(), documento.comBom());

        Path arquivoSaida = resolverArquivoSaida(arquivoEntrada);
        escritor.escrever(arquivoSaida, documentoFinal);
        cacheService.salvar(arquivoCache, entradasCache);

        log.info("Arquivo traduzido salvo em {} (cache em {})", arquivoSaida, arquivoCache);
        return arquivoSaida;
    }

    private Map<String, String> traduzirPendentes(LinkedHashSet<String> textosPendentes, String nomeArquivo)
            throws InterruptedException, ExecutionException {
        if (textosPendentes.isEmpty()) {
            return Map.of();
        }

        Map<String, List<String>> tagsPorTexto = new LinkedHashMap<>();
        Map<String, String> textoMascaradoPorOriginal = new LinkedHashMap<>();
        for (String original : textosPendentes) {
            MascaradorTags.Mascarado mascarado = mascarador.mascarar(original);
            tagsPorTexto.put(original, mascarado.tags());
            textoMascaradoPorOriginal.put(original, mascarado.texto());
        }

        List<String> textosPendentesOrdenados = new ArrayList<>(textosPendentes);
        int tamanhoLote = propriedades.tamanhoLote();

        List<List<String>> chunksOriginais = new ArrayList<>();
        List<Lote> lotes = new ArrayList<>();
        for (int i = 0; i < textosPendentesOrdenados.size(); i += tamanhoLote) {
            List<String> chunkOriginais = textosPendentesOrdenados.subList(i, Math.min(i + tamanhoLote, textosPendentesOrdenados.size()));
            List<String> chunkMascarados = chunkOriginais.stream().map(textoMascaradoPorOriginal::get).toList();
            chunksOriginais.add(chunkOriginais);
            lotes.add(new Lote(lotes.size() + 1, chunkMascarados));
        }

        uiLogger.iniciarLotes(lotes.size(), nomeArquivo);
        List<TraducaoLote> resultados;
        try {
            resultados = processarEpisodioUseCase.processarEpisodio(lotes);
        } catch (TraducaoParcialException e) {
            Map<String, String> traducoesParciais = new HashMap<>();
            if (e.getLotesSalvos() != null) {
                for (TraducaoLote tl : e.getLotesSalvos()) {
                    int k = tl.idLote() - 1; 
                    List<String> chunkOriginais = chunksOriginais.get(k);
                    List<String> traduzidoMascaradoLinhas = tl.linhasTraduzidas();
                    if (traduzidoMascaradoLinhas != null && chunkOriginais.size() == traduzidoMascaradoLinhas.size()) {
                        for (int j = 0; j < chunkOriginais.size(); j++) {
                            String original = chunkOriginais.get(j);
                            String traduzidoMascarado = traduzidoMascaradoLinhas.get(j);
                            traducoesParciais.put(original, desmascararComFallback(original, traduzidoMascarado, tagsPorTexto.get(original)));
                        }
                    }
                }
            }
            throw new TraducaoParcialException(e.getMessage(), traducoesParciais, e.getCause());
        } finally {
            uiLogger.finalizar();
        }

        Map<String, String> traducoesNovas = new HashMap<>();
        for (int k = 0; k < lotes.size(); k++) {
            List<String> chunkOriginais = chunksOriginais.get(k);
            List<String> traduzidoMascaradoLinhas = resultados.get(k).linhasTraduzidas();
            for (int j = 0; j < chunkOriginais.size(); j++) {
                String original = chunkOriginais.get(j);
                String traduzidoMascarado = traduzidoMascaradoLinhas.get(j);
                traducoesNovas.put(original, desmascararComFallback(original, traduzidoMascarado, tagsPorTexto.get(original)));
            }
        }
        return traducoesNovas;
    }

    /**
     * Restaura as tags numa fala traduzida; se o LLM corrompeu/perdeu marcadores
     * [[TAGn]] (alucinação isolada numa única fala), não derruba o lote/episódio
     * inteiro por causa disso: mantém o texto original (sem tradução) só para essa
     * fala e sinaliza para revisão manual no cache.
     */
    private String desmascararComFallback(String original, String traduzidoMascarado, List<String> tags) {
        try {
            return mascarador.desmascarar(traduzidoMascarado, tags);
        } catch (AlucinacaoDetectadaException e) {
            log.warn("Tags corrompidas pelo LLM nesta fala — mantendo o texto original sem tradução. Motivo: {}. Original: \"{}\"",
                e.getMessage(), original);
            uiLogger.log("[ WARN ] Tags corrompidas pelo LLM — fala mantida sem tradução (revise manualmente): " + original);
            return original;
        }
    }

    private boolean isTraduzivel(EventoLegenda evento) {
        return evento.isDialogo() && evento.temTexto() 
            && !propriedades.estiloIgnorado(evento.estilo())
            && mascarador.contemTextoTraduzivel(evento.texto());
    }

    private boolean isCacheReaproveitavel(String original, String traduzido) {
        if (traduzido == null || traduzido.isBlank()) {
            return false;
        }
        if (normalizarParaComparacao(original).equals(normalizarParaComparacao(traduzido))) {
            if (deveManterIdentico(original)) {
                return true;
            }
            return false;
        }
        try {
            validador.validarFala(traduzido);
            return true;
        } catch (AlucinacaoDetectadaException e) {
            log.warn("Cache ignorado porque parece conter fala ainda nao traduzida: {}", traduzido);
            return false;
        }
    }

    private boolean deveManterIdentico(String texto) {
        String textoLimpo = texto.replaceAll("\\{[^}]+\\}", "").strip();
        textoLimpo = textoLimpo.replaceAll("[^\\w\\s\\d]", "").strip();

        if (textoLimpo.isEmpty()) {
            return true;
        }

        String[] palavras = textoLimpo.split("\\s+");
        if (palavras.length <= 1) {
            return true;
        }

        if (palavras.length == 2 && 
            Character.isUpperCase(palavras[0].charAt(0)) && 
            Character.isUpperCase(palavras[1].charAt(0))) {
            return true;
        }

        String textoMinusculo = textoLimpo.toLowerCase();
        List<String> termosIgnorados = List.of(
            "fire bolt", "argo vesta", "caelus hildr", "hildrsleif", "dios aedes vesta",
            "vana freya", "vana seith", "vana seith.", "zeo gullveig", "hildis vini",
            "agallis arvesynth", "remiste felis", "uchide no kozuchi", "feles cruz",
            "dubh daol", "zekka", "gralineze fromel", "gokoh", "astrea record"
        );
        return termosIgnorados.contains(textoMinusculo);
    }

    private String normalizarParaComparacao(String texto) {
        return texto == null ? "" : texto.replaceAll("\\s+", " ").trim();
    }

    private Path resolverArquivoSaida(Path entrada) {
        String nome = entrada.getFileName().toString();
        String extensao = nome.toLowerCase().endsWith(".ssa") ? ".ssa" : ".ass";
        String base = nome.substring(0, nome.length() - extensao.length());
        String baseSemSufixoIngles = base.replaceFirst("(?i)_ENG$", "");
        return pastasExecucao.diretorioSaida().resolve(baseSemSufixoIngles + "_PT-BR" + extensao);
    }

    private Path resolverArquivoCache(Path entrada) {
        String nome = entrada.getFileName().toString();
        String extensao = nome.toLowerCase().endsWith(".ssa") ? ".ssa" : ".ass";
        String base = nome.substring(0, nome.length() - extensao.length());
        return pastasExecucao.diretorioCache().resolve(base + ".cache.json");
    }
}
