package org.traducao.projeto.legendas.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "extrator")
public record ExtratorProperties(
    String mkvmergePath,
    String mkvextractPath
) {
    public String resolverMkvmergePath() {
        if (mkvmergePath == null || mkvmergePath.isBlank()) {
            return "mkvmerge";
        }
        return mkvmergePath;
    }

    public String resolverMkvextractPath() {
        if (mkvextractPath == null || mkvextractPath.isBlank()) {
            return "mkvextract";
        }
        return mkvextractPath;
    }
}
