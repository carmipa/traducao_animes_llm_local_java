package org.traducao.projeto.legendasExtracao.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "extrator")
public record ExtratorProperties(
    String mkvmergePath,
    String mkvextractPath,
    String ffmpegPath,
    String ffprobePath
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

    public String resolverFfmpegPath() {
        if (ffmpegPath == null || ffmpegPath.isBlank()) {
            return "ffmpeg";
        }
        return ffmpegPath;
    }

    public String resolverFfprobePath() {
        if (ffprobePath == null || ffprobePath.isBlank()) {
            return "ffprobe";
        }
        return ffprobePath;
    }
}
