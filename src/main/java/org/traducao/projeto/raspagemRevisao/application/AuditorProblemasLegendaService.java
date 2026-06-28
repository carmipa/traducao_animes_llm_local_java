package org.traducao.projeto.raspagemRevisao.application;

import org.springframework.stereotype.Service;
import org.traducao.projeto.raspagemRevisao.domain.ResultadoDeteccaoConcordancia;
import org.traducao.projeto.traducao.application.ValidadorTraducaoService;
import org.traducao.projeto.traducao.domain.exceptions.AlucinacaoDetectadaException;

import java.util.ArrayList;
import java.util.List;

/**
 * Agrega detecção de resíduo em inglês e erros de concordância PT-BR.
 */
@Service
public class AuditorProblemasLegendaService {

    private final ValidadorTraducaoService validador;
    private final DetectorConcordanciaService detectorConcordancia;

    public AuditorProblemasLegendaService(
        ValidadorTraducaoService validador,
        DetectorConcordanciaService detectorConcordancia
    ) {
        this.validador = validador;
        this.detectorConcordancia = detectorConcordancia;
    }

    public ResultadoDeteccaoConcordancia auditar(String originalIngles, String traducaoPt) {
        List<String> motivos = new ArrayList<>();

        try {
            validador.validarFala(traducaoPt);
        } catch (AlucinacaoDetectadaException e) {
            motivos.add(e.getMessage());
        }

        ResultadoDeteccaoConcordancia concordancia = detectorConcordancia.analisar(originalIngles, traducaoPt);
        if (concordancia.suspeito()) {
            motivos.addAll(concordancia.motivos());
        }

        if (motivos.isEmpty()) {
            return ResultadoDeteccaoConcordancia.limpo();
        }
        return new ResultadoDeteccaoConcordancia(true, List.copyOf(motivos));
    }
}
