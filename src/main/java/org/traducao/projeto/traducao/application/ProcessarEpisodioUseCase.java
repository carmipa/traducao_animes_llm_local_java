package org.traducao.projeto.traducao.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.traducao.projeto.traducao.domain.Lote;
import org.traducao.projeto.traducao.domain.TraducaoLote;
import org.traducao.projeto.traducao.domain.exceptions.AlucinacaoDetectadaException;
import org.traducao.projeto.traducao.domain.exceptions.DivergenciaLinhasException;
import org.traducao.projeto.traducao.domain.exceptions.TradutorException;
import org.traducao.projeto.traducao.domain.ports.MistralPort;
import org.traducao.projeto.traducao.presentation.ui.ConsoleUILogger;
import org.traducao.projeto.traducao.domain.exceptions.TraducaoParcialException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;


@Service
public class ProcessarEpisodioUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessarEpisodioUseCase.class);
    private static final String MDC_LOTE_ID = "loteId";

    // Quantas tentativas extras (alem da primeira) sao feitas numa fala isolada
    // (lote de tamanho 1) antes de desistir e manter o texto original sem traducao.
    private static final int MAX_TENTATIVAS_LINHA_UNICA = 2;

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
                throw new TraducaoParcialException(
                    e.getMessage(), 
                    resultado, 
                    e
                );
            }
        }

        log.info("Processamento concluído: {} lote(s) traduzido(s) com sucesso", resultado.size());
        return resultado;
    }

    /**
     * Traduz e valida um lote, tolerando alucinações de contagem de linhas e
     * resíduo/preâmbulo: em vez de abortar o episódio inteiro por causa de um
     * único lote problemático, divide-o recursivamente em partes menores e
     * tenta de novo. Só uma falha de comunicação real (HTTP/timeout, já
     * esgotadas as tentativas do {@link MistralPort}) aborta o episódio.
     */
    private TraducaoLote traduzirEValidar(Lote lote) {
        MDC.put(MDC_LOTE_ID, String.valueOf(lote.idLote()));
        try {
            List<String> traduzidas = traduzirComDivisao(lote);

            log.debug("Lote {} validado com sucesso", lote.idLote());
            uiLogger.log("✅ Lote " + lote.idLote() + " traduzido com sucesso.");
            uiLogger.passoConcluido(1);

            return new TraducaoLote(lote.idLote(), traduzidas, true, null);
        } catch (TradutorException e) {
            log.error("Falha crítica no lote {}: {}", lote.idLote(), e.getMessage());
            uiLogger.log("❌ ERRO CRÍTICO no Lote " + lote.idLote() + ": " + e.getMessage());
            throw e;
        } finally {
            MDC.remove(MDC_LOTE_ID);
        }
    }

    /**
     * Tenta traduzir o lote de uma vez; se o LLM devolver a contagem errada de
     * linhas ou uma fala com resíduo/preâmbulo, divide o lote pela metade e
     * tenta cada metade recursivamente, isolando o trecho problemático em vez
     * de descartar o lote inteiro (que pode ter 20+ falas, das quais só 1
     * costuma ser a culpada).
     */
    private List<String> traduzirComDivisao(Lote lote) {
        if (lote.linhasOriginais().size() <= 1) {
            return traduzirLinhaUnicaComFallback(lote);
        }

        try {
            return traduzirERevalidarBruto(lote);
        } catch (DivergenciaLinhasException | AlucinacaoDetectadaException e) {
            int total = lote.linhasOriginais().size();
            int meio = total / 2;
            log.warn("Lote {} (tamanho {}) falhou na validação ({}). Dividindo em 2 partes e tentando novamente...",
                lote.idLote(), total, e.getMessage());
            uiLogger.log("[ WARN ] Lote " + lote.idLote() + " dividido após falha de validação: " + e.getMessage());

            Lote primeiraMetade = new Lote(lote.idLote(), lote.linhasOriginais().subList(0, meio));
            Lote segundaMetade = new Lote(lote.idLote(), lote.linhasOriginais().subList(meio, total));

            List<String> traduzidas = new ArrayList<>(traduzirComDivisao(primeiraMetade));
            traduzidas.addAll(traduzirComDivisao(segundaMetade));
            return traduzidas;
        }
    }

    /**
     * Última instância para uma única fala: tenta mais algumas vezes e, se o
     * LLM continuar devolvendo lixo, mantém o texto original sem tradução em
     * vez de derrubar o episódio inteiro por causa de uma fala só. A fala fica
     * sinalizada no log/console para revisão manual (o cache JSON aceita
     * correções manuais, ver {@link ProcessarArquivoUseCase}).
     */
    private List<String> traduzirLinhaUnicaComFallback(Lote lote) {
        if (lote.linhasOriginais().isEmpty()) {
            return List.of();
        }

        RuntimeException ultimaFalha = null;
        for (int tentativa = 1; tentativa <= 1 + MAX_TENTATIVAS_LINHA_UNICA; tentativa++) {
            try {
                return traduzirERevalidarBruto(lote);
            } catch (DivergenciaLinhasException | AlucinacaoDetectadaException e) {
                ultimaFalha = e;
            }
        }

        String original = lote.linhasOriginais().getFirst();
        log.warn("Lote {}: fala não pôde ser traduzida com confiança após tentativas extras ({}). " +
                "Mantendo o texto original sem tradução: \"{}\"",
            lote.idLote(), ultimaFalha != null ? ultimaFalha.getMessage() : "motivo desconhecido", original);
        uiLogger.log("[ WARN ] Fala mantida sem tradução no Lote " + lote.idLote()
            + " (revise manualmente no cache): " + original);
        return List.of(original);
    }

    private List<String> traduzirERevalidarBruto(Lote lote) {
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

        return resultado.linhasTraduzidas();
    }
}
