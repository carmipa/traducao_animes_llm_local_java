package org.traducao.animes.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.List;

@ConfigurationProperties(prefix = "tradutor")
public record TradutorProperties(
    String diretorioEntrada,
    String diretorioSaida,
    String diretorioCache,
    int tamanhoLote,
    List<String> estilosIgnorados,
    String idiomaOriginal,
    String idiomaTraduzido
) {
    public TradutorProperties {
        if (tamanhoLote <= 0) {
            tamanhoLote = 20;
        }
        if (estilosIgnorados == null) {
            estilosIgnorados = List.of("Song JP");
        }
        if (idiomaOriginal == null || idiomaOriginal.isBlank()) {
            idiomaOriginal = "en";
        }
        if (idiomaTraduzido == null || idiomaTraduzido.isBlank()) {
            idiomaTraduzido = "pt-br";
        }
    }

    public Path resolverDiretorioSaida() {
        if (diretorioSaida != null && !diretorioSaida.isBlank()) {
            return Path.of(diretorioSaida);
        }
        Path entrada = Path.of(diretorioEntrada);
        Path pai = entrada.getParent();
        if (pai == null || entrada.getFileName() == null) {
            return entrada.resolve("traduzido");
        }
        String nomePasta = entrada.getFileName().toString();
        String nomeSaida = nomePasta.toLowerCase().contains("eng")
            ? nomePasta.replaceAll("(?i)eng", "pt-br")
            : nomePasta + "_pt-br";
        return pai.resolve(nomeSaida);
    }

    public Path resolverDiretorioCache() {
        if (diretorioCache != null && !diretorioCache.isBlank()) {
            return Path.of(diretorioCache);
        }
        return resolverDiretorioSaida().resolve("cache");
    }

    public boolean estiloIgnorado(String estilo) {
        return estilosIgnorados.stream().anyMatch(e -> e.equalsIgnoreCase(estilo));
    }
}
