package org.traducao.projeto.traducao.domain.ports;

public interface ProvedorContexto {
    /**
     * Retorna o ID único para seleção via UI.
     */
    String getId();

    /**
     * Retorna o nome amigável para exibição no combo box da UI.
     */
    String getNomeExibicao();

    /**
     * Retorna o prompt de sistema completo para o LLM, com regras gerais e lore especifico da midia.
     */
    String obterPromptSistema();
}
