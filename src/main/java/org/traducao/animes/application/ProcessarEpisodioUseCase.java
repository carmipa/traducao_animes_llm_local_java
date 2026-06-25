package org.traducao.animes.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.traducao.animes.domain.Lote;
import org.traducao.animes.domain.TraducaoLote;
import org.traducao.animes.domain.exceptions.DivergenciaLinhasException;
import org.traducao.animes.domain.exceptions.TradutorException;
import org.traducao.animes.domain.ports.MistralPort;
import org.traducao.animes.presentation.ui.ConsoleUILogger;

import java.util.List;
import java.util.concurrent.ExecutionException;


@Service
public class ProcessarEpisodioUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessarEpisodioUseCase.class);
    private static final String MDC_LOTE_ID = "loteId";

    private final MistralPort mistralPort;
    private final ValidadorTraducaoService validador;
    private final ConsoleUILogger uiLogger;

    public ProcessarEpisodioUseCase(MistralPort mistralPort, ValidadorTraducaoService validador, ConsoleUILogger uiLogger) {
        this.mistralPort = mistralPort;
        this.validador = validador;
        this.uiLogger = uiLogger;
    }

    public List<TraducaoLote> processarEpisodio(List<Lote> lotes) throws InterruptedException, ExecutionException {
        if (lotes.isEmpty()) {
            return List.of();
        }

        log.info("Iniciando processamento de {} lote(s) de forma sequencial (preservando LM Studio/GPU)", lotes.size());
        
        java.util.List<TraducaoLote> resultado = new java.util.ArrayList<>();
        for (Lote lote : lotes) {
            try {
                TraducaoLote tl = traduzirEValidar(lote);
                resultado.add(tl);
            } catch (Exception e) {
                // Aborta e guarda as traduções parciais que passaram!
                throw new org.traducao.animes.domain.exceptions.TraducaoParcialException(
                    e.getMessage(), 
                    resultado, 
                    e
                );
            }
        }

        log.info("Processamento concluído: {} lote(s) traduzido(s) com sucesso", resultado.size());
        return resultado;
    }

    private TraducaoLote traduzirEValidar(Lote lote) {
        MDC.put(MDC_LOTE_ID, String.valueOf(lote.idLote()));
        try {
            TraducaoLote resultado = mistralPort.traduzir(lote);

            if (!resultado.sucesso() || resultado.linhasTraduzidas() == null) {
                throw new TradutorException("Lote " + lote.idLote() + " falhou na comunicação: " + resultado.mensagemErro());
            }

            if (resultado.linhasTraduzidas().size() != lote.linhasOriginais().size()) {
                throw new DivergenciaLinhasException(
                    "Lote " + lote.idLote() + " retornou " + resultado.linhasTraduzidas().size()
                        + " linha(s), esperado " + lote.linhasOriginais().size()
                        + ". Provável alucinação do LLM fundindo ou quebrando falas, o que desalinharia a legenda.");
            }

            for (String fala : resultado.linhasTraduzidas()) {
                validador.validarFala(fala);
            }

            log.debug("Lote {} validado com sucesso", lote.idLote());
            uiLogger.log("✅ Lote " + lote.idLote() + " traduzido com sucesso.");
            uiLogger.passoConcluido(1);

            return resultado;
        } catch (TradutorException e) {
            log.error("Falha crítica no lote {}: {}", lote.idLote(), e.getMessage());
            uiLogger.log("❌ ERRO CRÍTICO no Lote " + lote.idLote() + ": " + e.getMessage());
            throw e;
        } finally {
            MDC.remove(MDC_LOTE_ID);
        }
    }
}
