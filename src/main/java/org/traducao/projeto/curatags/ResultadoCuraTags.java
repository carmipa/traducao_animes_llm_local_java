package org.traducao.projeto.curatags;

import java.util.List;

public record ResultadoCuraTags(
    int curados,
    int corrigidosLlm,
    int semAlteracao,
    int semPar,
    int totalErros,
    List<String> erros
) {
    public boolean teveErros() {
        return totalErros > 0;
    }

    public int totalArquivos() {
        return curados + semAlteracao;
    }
}
