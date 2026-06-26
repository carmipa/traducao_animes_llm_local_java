package org.traducao.projeto.legendasExtracao.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.traducao.projeto.legendasExtracao.application.strategy.ExtratorStrategy;
import org.traducao.projeto.legendasExtracao.domain.ExtratorException;
import org.traducao.projeto.legendasExtracao.domain.FaixaLegenda;
import org.traducao.projeto.legendasExtracao.domain.FormatoLegenda;
import org.traducao.projeto.legendasExtracao.domain.RelatorioExtracao;
import org.traducao.projeto.legendasExtracao.infrastructure.adapters.MkvToolNixAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class ExtrairLegendaUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExtrairLegendaUseCase.class);

    private final MkvToolNixAdapter mkvAdapter;
    private final List<ExtratorStrategy> strategies;

    public ExtrairLegendaUseCase(MkvToolNixAdapter mkvAdapter, List<ExtratorStrategy> strategies) {
        this.mkvAdapter = mkvAdapter;
        this.strategies = strategies;
    }

    public RelatorioExtracao executar(Path pastaVideos, FormatoLegenda formato) {
        RelatorioExtracao relatorio = new RelatorioExtracao(formato);

        if (!Files.isDirectory(pastaVideos)) {
            throw new ExtratorException("Pasta de vídeos não existe ou não é um diretório: " + pastaVideos);
        }

        mkvAdapter.validarInfraestrutura();

        ExtratorStrategy strategy = strategies.stream()
                .filter(s -> s.suporta(formato))
                .findFirst()
                .orElseThrow(() -> new ExtratorException("Nenhuma estratégia suporta o formato " + formato));

        Path pastaSaida = pastaVideos.resolve("legendas_extraidas_" + formato.name().toLowerCase());
        try {
            Files.createDirectories(pastaSaida);
        } catch (IOException e) {
            throw new ExtratorException("Falha ao criar pasta de saída: " + pastaSaida, e);
        }

        try (Stream<Path> stream = Files.list(pastaVideos)) {
            List<Path> mkvs = stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().toLowerCase().endsWith(".mkv"))
                .sorted()
                .toList();
            
            for (Path mkv : mkvs) {
                relatorio.registrarDetectado();
                log.debug("Processando {}", mkv.getFileName());

                try {
                    List<FaixaLegenda> faixas = mkvAdapter.identificarFaixas(mkv);
                    Optional<FaixaLegenda> faixaAlvo = strategy.selecionarMelhorFaixa(faixas);

                    if (faixaAlvo.isPresent()) {
                        FaixaLegenda f = faixaAlvo.get();
                        String nomeBase = mkv.getFileName().toString().replaceFirst("[.][^.]+$", "");
                        String arquivoSaida = nomeBase + "_Track" + f.id() + "." + formato.getExtensaoSaida();
                        Path caminhoSaida = pastaSaida.resolve(arquivoSaida);

                        mkvAdapter.extrairTrilha(mkv, f.id(), caminhoSaida);
                        relatorio.registrarExtraido();
                        log.info("Legenda extraída: {} -> {}", mkv.getFileName(), arquivoSaida);
                    } else {
                        relatorio.registrarSemLegenda();
                        log.warn("Nenhuma faixa {} encontrada no vídeo: {}", formato, mkv.getFileName());
                    }
                } catch (ExtratorException e) {
                    relatorio.registrarFalha();
                    log.error("Falha ao processar {}: {}", mkv.getFileName(), e.getMessage());
                } catch (Exception e) {
                    relatorio.registrarFalha();
                    log.error("Erro inesperado em {}: {}", mkv.getFileName(), e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            throw new ExtratorException("Falha ao ler o diretório " + pastaVideos, e);
        }

        return relatorio;
    }
}
