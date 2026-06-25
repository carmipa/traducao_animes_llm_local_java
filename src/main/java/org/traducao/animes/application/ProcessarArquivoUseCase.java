package org.traducao.animes.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.traducao.animes.domain.Lote;
import org.traducao.animes.domain.TraducaoLote;
import org.traducao.animes.domain.exceptions.ArquivoLegendaException;
import org.traducao.animes.domain.legenda.DocumentoLegenda;
import org.traducao.animes.domain.legenda.EventoLegenda;
import org.traducao.animes.infrastructure.cache.CacheTraducaoService;
import org.traducao.animes.infrastructure.cache.EntradaCache;
import org.traducao.animes.infrastructure.config.TradutorProperties;
import org.traducao.animes.infrastructure.legenda.EscritorLegendaAss;
import org.traducao.animes.infrastructure.legenda.LeitorLegendaAss;
import org.traducao.animes.infrastructure.legenda.MascaradorTags;
import org.traducao.animes.presentation.ui.ConsoleUILogger;
import org.traducao.animes.presentation.ui.PastasExecucao;

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

        LinkedHashSet<String> textosPendentes = new LinkedHashSet<>();
        for (EventoLegenda evento : eventosTraduziveis) {
            String cacheado = cacheExistente.get(evento.texto());
            if (cacheado == null) {
                textosPendentes.add(evento.texto());
            }
        }
        log.info("{} fala(s) já no cache, {} fala(s) distinta(s) pendente(s) de tradução",
            eventosTraduziveis.size() - textosPendentes.size(), textosPendentes.size());

        Map<String, String> traducoesNovas;
        try {
            traducoesNovas = traduzirPendentes(textosPendentes, arquivoEntrada.getFileName().toString());
        } catch (org.traducao.animes.domain.exceptions.TraducaoParcialException e) {
            Map<String, String> traducoesParciais = e.getDicionarioParcial();
            if (traducoesParciais != null && !traducoesParciais.isEmpty()) {
                log.info("Salvando {} traducoes parciais no cache antes de abortar o episodio", traducoesParciais.size());
                Map<String, String> combinadasParciais = new HashMap<>(cacheExistente);
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

        Map<String, String> traducoesCombinadas = new HashMap<>(cacheExistente);
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
            validador.validarFala(textoFinal);
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
        } catch (org.traducao.animes.domain.exceptions.TraducaoParcialException e) {
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
                            String textoFinal = mascarador.desmascarar(traduzidoMascarado, tagsPorTexto.get(original));
                            traducoesParciais.put(original, textoFinal);
                        }
                    }
                }
            }
            throw new org.traducao.animes.domain.exceptions.TraducaoParcialException(e.getMessage(), traducoesParciais, e.getCause());
        } finally {
            uiLogger.finalizar();
        }

        Map<String, String> traducoesNovas = new HashMap<>();
        try {
            for (int k = 0; k < lotes.size(); k++) {
                List<String> chunkOriginais = chunksOriginais.get(k);
                List<String> traduzidoMascaradoLinhas = resultados.get(k).linhasTraduzidas();
                for (int j = 0; j < chunkOriginais.size(); j++) {
                    String original = chunkOriginais.get(j);
                    String traduzidoMascarado = traduzidoMascaradoLinhas.get(j);
                    String textoFinal = mascarador.desmascarar(traduzidoMascarado, tagsPorTexto.get(original));
                    traducoesNovas.put(original, textoFinal);
                }
            }
        } catch (Exception e) {
            throw new org.traducao.animes.domain.exceptions.TraducaoParcialException(e.getMessage(), traducoesNovas, e);
        }
        return traducoesNovas;
    }

    private boolean isTraduzivel(EventoLegenda evento) {
        return evento.isDialogo() && evento.temTexto() && !propriedades.estiloIgnorado(evento.estilo());
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
