package org.traducao.projeto.legendasExtracao.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.traducao.projeto.legendasExtracao.application.strategy.ExtratorStrategy;
import org.traducao.projeto.legendasExtracao.domain.ExtratorException;
import org.traducao.projeto.legendasExtracao.domain.FaixaLegenda;
import org.traducao.projeto.legendasExtracao.domain.FormatoLegenda;
import org.traducao.projeto.legendasExtracao.domain.RelatorioExtracao;
import org.traducao.projeto.legendasExtracao.domain.ports.ExtratorVideoPort;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class ExtrairLegendaUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExtrairLegendaUseCase.class);

    private final List<ExtratorVideoPort> adaptadoresVideo;
    private final List<ExtratorStrategy> strategies;

    public ExtrairLegendaUseCase(List<ExtratorVideoPort> adaptadoresVideo, List<ExtratorStrategy> strategies) {
        this.adaptadoresVideo = adaptadoresVideo;
        this.strategies = strategies;
    }

    public RelatorioExtracao executar(Path pastaVideos, FormatoLegenda formato) {
        return executar(pastaVideos, null, formato);
    }

    public RelatorioExtracao executar(Path pastaVideos, Path pastaSaidaCustomizada, FormatoLegenda formato) {
        RelatorioExtracao relatorio = new RelatorioExtracao(formato);

        if (!Files.exists(pastaVideos)) {
            throw new ExtratorException("Pasta de vídeos ou arquivo não existe: " + pastaVideos);
        }

        ExtratorStrategy strategy = strategies.stream()
                .filter(s -> s.suporta(formato))
                .findFirst()
                .orElseThrow(() -> new ExtratorException("Nenhuma estratégia suporta o formato " + formato));

        Path pastaSaida = (pastaSaidaCustomizada != null && !pastaSaidaCustomizada.toString().isBlank())
                ? pastaSaidaCustomizada
                : (Files.isDirectory(pastaVideos) 
                    ? pastaVideos.resolve("legendas_extraidas_" + formato.name().toLowerCase())
                    : (pastaVideos.getParent() != null ? pastaVideos.getParent().resolve("legendas_extraidas_" + formato.name().toLowerCase()) : Path.of("legendas_extraidas_" + formato.name().toLowerCase())));
        try {
            Files.createDirectories(pastaSaida);
        } catch (IOException e) {
            throw new ExtratorException("Falha ao criar pasta de saída: " + pastaSaida, e);
        }

        List<Path> videos = encontrarVideos(pastaVideos);

        Set<ExtratorVideoPort> adaptadoresEmUso = new HashSet<>();
        videos.forEach(v -> resolverAdaptador(v).ifPresent(adaptadoresEmUso::add));
        adaptadoresEmUso.forEach(ExtratorVideoPort::validarInfraestrutura);

        for (Path video : videos) {
            relatorio.registrarDetectado();
            log.debug("Processando {}", video.getFileName());

            ExtratorVideoPort adaptador = resolverAdaptador(video).orElseThrow();

            try {
                List<FaixaLegenda> faixas = adaptador.identificarFaixas(video);
                Optional<FaixaLegenda> faixaAlvo = strategy.selecionarMelhorFaixa(faixas);

                if (faixaAlvo.isPresent()) {
                    FaixaLegenda f = faixaAlvo.get();
                    String nomeBase = video.getFileName().toString().replaceFirst("[.][^.]+$", "");
                    String arquivoSaida = nomeBase + "_Track" + f.id() + "." + formato.getExtensaoSaida();
                    Path caminhoSaida = pastaSaida.resolve(arquivoSaida);

                    adaptador.extrairTrilha(video, f.id(), caminhoSaida);
                    relatorio.registrarExtraido();
                    log.info("Legenda extraída: {} -> {}", video.getFileName(), arquivoSaida);
                } else {
                    relatorio.registrarSemLegenda();
                    log.warn("Nenhuma faixa {} encontrada no vídeo: {}", formato, video.getFileName());
                }
            } catch (ExtratorException e) {
                relatorio.registrarFalha();
                log.error("Falha ao processar {}: {}", video.getFileName(), e.getMessage());
            } catch (Exception e) {
                relatorio.registrarFalha();
                log.error("Erro inesperado em {}: {}", video.getFileName(), e.getMessage(), e);
            }
        }

        return relatorio;
    }

    private List<Path> encontrarVideos(Path entrada) {
        if (Files.isRegularFile(entrada)) {
            return resolverAdaptador(entrada).isPresent() ? List.of(entrada) : List.of();
        }

        if (!Files.isDirectory(entrada)) {
            throw new ExtratorException("Pasta de vídeos não existe ou não é um diretório: " + entrada);
        }

        try (Stream<Path> walk = Files.walk(entrada)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(p -> resolverAdaptador(p).isPresent())
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new ExtratorException("Falha ao ler o diretório " + entrada, e);
        }
    }

    private Optional<ExtratorVideoPort> resolverAdaptador(Path video) {
        return adaptadoresVideo.stream().filter(a -> a.suporta(video)).findFirst();
    }
}
