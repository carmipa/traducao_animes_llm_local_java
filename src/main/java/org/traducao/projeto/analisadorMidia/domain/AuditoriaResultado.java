package org.traducao.projeto.analisadorMidia.domain;

import java.nio.file.Path;
import java.util.List;

public record AuditoriaResultado(
    Path caminhoArquivo,
    String nomeArquivo,
    ContainerInfo container,
    List<VideoInfo> videos,
    List<AudioInfo> audios,
    List<LegendaInfo> legendas,
    List<String> logsAuditoria
) {}
