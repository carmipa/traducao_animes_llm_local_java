package org.traducao.projeto.traducao.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.traducao.projeto.traducao.domain.exceptions.ArquivoLegendaException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persiste, por arquivo de legenda, o par (texto original em ingles -> texto
 * traduzido) em JSON. Serve a dois propositos: (1) permitir que o usuario
 * revise/corrija falhas de traducao manualmente editando o JSON e (2) evitar
 * chamar o LLM de novo para falas ja traduzidas em uma execucao anterior -
 * uma correcao manual no cache e respeitada na proxima execucao.
 */
@Component
public class CacheTraducaoService {

    private static final Logger log = LoggerFactory.getLogger(CacheTraducaoService.class);

    private final ObjectMapper objectMapper;

    public CacheTraducaoService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, String> carregar(Path arquivoCache) {
        if (!Files.exists(arquivoCache)) {
            return new HashMap<>();
        }
        try {
            List<EntradaCache> entradas = objectMapper.readValue(arquivoCache.toFile(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, EntradaCache.class));
            Map<String, String> mapa = new HashMap<>();
            for (EntradaCache entrada : entradas) {
                if (entrada.traduzido() != null && !entrada.traduzido().isBlank()) {
                    mapa.put(entrada.original(), entrada.traduzido());
                }
            }
            log.info("Cache carregado de {} ({} entradas reaproveitaveis)", arquivoCache, mapa.size());
            return mapa;
        } catch (IOException e) {
            log.warn("Falha ao ler cache existente em {}, ignorando e traduzindo do zero. Causa: {}",
                arquivoCache, e.getMessage());
            return new HashMap<>();
        }
    }

    public void salvar(Path arquivoCache, List<EntradaCache> entradas) {
        try {
            Path pasta = arquivoCache.toAbsolutePath().getParent();
            if (pasta != null) {
                Files.createDirectories(pasta);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(arquivoCache.toFile(), entradas);
            log.info("Cache de traducao salvo em {} ({} entradas)", arquivoCache, entradas.size());
        } catch (IOException e) {
            throw new ArquivoLegendaException("Falha ao salvar cache de traducao: " + arquivoCache, e);
        }
    }
}
