package org.traducao.projeto.traducao.infrastructure.config;

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

    /**
     * Se nao for informado (nem por config nem pelo console), o cache fica em
     * "cache/<pasta-do-anime>/<subpasta>" na raiz do projeto — mesma convenção
     * relativa usada por logging.file.name (logs/tradutor.log) — em vez de
     * pedir esse caminho ao usuário a cada execução.
     */
    public Path resolverDiretorioCache() {
        if (diretorioCache != null && !diretorioCache.isBlank()) {
            return Path.of(diretorioCache);
        }
        return Path.of("cache").resolve(nomeAnimeAPartirDaEntrada());
    }

    private Path nomeAnimeAPartirDaEntrada() {
        Path entrada = Path.of(diretorioEntrada);
        Path pai = entrada.getParent();
        if (pai == null || pai.getFileName() == null) {
            return Path.of(entrada.getFileName() != null ? entrada.getFileName().toString() : "default");
        }
        Path avo = pai.getParent();
        if (avo == null || avo.getFileName() == null) {
            return Path.of(pai.getFileName().toString());
        }
        return Path.of(avo.getFileName().toString(), pai.getFileName().toString());
    }

    public boolean estiloIgnorado(String estilo) {
        return estilosIgnorados.stream().anyMatch(e -> e.equalsIgnoreCase(estilo));
    }
}
