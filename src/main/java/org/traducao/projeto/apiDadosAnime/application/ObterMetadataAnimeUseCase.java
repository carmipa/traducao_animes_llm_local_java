package org.traducao.projeto.apiDadosAnime.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.traducao.projeto.apiDadosAnime.domain.model.AnimeMetadata;
import org.traducao.projeto.apiDadosAnime.infrastructure.adapters.JikanApiClientAdapter;
import org.traducao.projeto.apiDadosAnime.infrastructure.adapters.TmdbApiClientAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Service
public class ObterMetadataAnimeUseCase {

    private static final Logger log = LoggerFactory.getLogger(ObterMetadataAnimeUseCase.class);
    private static final Path PASTA_CACHE_METADATA = Path.of("cache", "metadata");

    private final TmdbApiClientAdapter tmdbAdapter;
    private final JikanApiClientAdapter jikanAdapter;
    private final ObjectMapper mapper;

    public ObterMetadataAnimeUseCase(
            TmdbApiClientAdapter tmdbAdapter,
            JikanApiClientAdapter jikanAdapter,
            ObjectMapper mapper) {
        this.tmdbAdapter = tmdbAdapter;
        this.jikanAdapter = jikanAdapter;
        this.mapper = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public Optional<AnimeMetadata> executar(String caminhoOuNome) {
        if (caminhoOuNome == null || caminhoOuNome.isBlank()) {
            return Optional.empty();
        }

        String nomeSanitizado = extrairNomeTermoBusca(caminhoOuNome);
        if (nomeSanitizado.isBlank()) {
            return Optional.empty();
        }

        Path arquivoCache = resolverArquivoCache(nomeSanitizado);
        if (Files.isRegularFile(arquivoCache)) {
            try {
                AnimeMetadata metadata = mapper.readValue(arquivoCache.toFile(), AnimeMetadata.class);
                return Optional.of(metadata);
            } catch (IOException e) {
                log.warn("Falha ao ler cache de metadata em {}: {}", arquivoCache, e.getMessage());
            }
        }

        Optional<AnimeMetadata> obtidoOpt = Optional.empty();
        if (tmdbAdapter.isConfigurado()) {
            obtidoOpt = tmdbAdapter.buscarPorNome(nomeSanitizado);
        }

        if (obtidoOpt.isEmpty()) {
            obtidoOpt = jikanAdapter.buscarPorNome(nomeSanitizado);
        }

        if (obtidoOpt.isPresent()) {
            salvarEmCache(arquivoCache, obtidoOpt.get());
        }

        return obtidoOpt;
    }

    public String extrairNomeTermoBusca(String entrada) {
        String texto = entrada.replace('\\', '/');
        if (texto.contains("/")) {
            String[] partes = texto.split("/");
            for (int i = partes.length - 1; i >= 0; i--) {
                String p = partes[i].trim();
                if (!p.isBlank() && !p.equalsIgnoreCase("cache") && !p.startsWith("Season") && !p.startsWith("season")) {
                    texto = p;
                    break;
                }
            }
        }

        // Remove extensao(es) do nome do arquivo (ex.: ".cache.json", ".mkv", ".ass")
        // antes de tokenizar, senao "cache"/"json"/"mkv" sobram como ruido na busca.
        texto = texto.replaceAll("(?:\\.[A-Za-z0-9]{1,5})+$", "");

        texto = texto.replaceAll("\\[[^\\]]*\\]", " ")
                     .replaceAll("\\([^\\)]*\\)", " ")
                     // Separadores primeiro: "_ENG" so e removido pela lista de ruido
                     // abaixo se o "_" já tiver virado espaço (\b não separa "_E").
                     .replaceAll("[_.-]", " ")
                     .replaceAll("(?i)\\b(Season|S)\\s*\\d+\\b|\\bE\\d{1,3}\\b", " ")
                     .replaceAll("(?i)\\b(1080p|720p|4k|BD|AV1|HEVC|x264|x265|Dual Audio|Multi-Audio|ENG|PTBR|PT-BR|Track\\d+)\\b", " ")
                     .replaceAll("\\s+", " ")
                     .trim();

        return texto;
    }

    private Path resolverArquivoCache(String nomeSanitizado) {
        String chaveValida = nomeSanitizado.toLowerCase().replaceAll("[^a-z0-9]", "_").replaceAll("_+", "_");
        if (chaveValida.isBlank()) {
            chaveValida = "anime_desconhecido";
        }
        return PASTA_CACHE_METADATA.resolve(chaveValida + ".json");
    }

    private void salvarEmCache(Path arquivoCache, AnimeMetadata metadata) {
        try {
            Files.createDirectories(PASTA_CACHE_METADATA);
            mapper.writeValue(arquivoCache.toFile(), metadata);
        } catch (IOException e) {
            log.warn("Falha ao salvar cache de metadata em {}: {}", arquivoCache, e.getMessage());
        }
    }
}
