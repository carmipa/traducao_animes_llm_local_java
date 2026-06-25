package org.traducao.projeto.remuxer.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "remuxer")
public record RemuxerProperties(
    String mkvmergePath
) {
    public String resolverMkvmergePath() {
        if (mkvmergePath == null || mkvmergePath.isBlank()) {
            return "mkvmerge"; // Tenta usar do PATH
        }
        return mkvmergePath;
    }
}
