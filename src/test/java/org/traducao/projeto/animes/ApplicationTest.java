package org.traducao.projeto.animes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationTest {

    @Test
    void mantemArgsQuandoEntradaJaInformada() {
        String[] args = {"--tradutor.diretorio-entrada=D:\\legendas"};
        String[] resultado = Application.prepararArgumentosComPastasDoConsole(args);
        assertThat(resultado).containsExactly("--tradutor.diretorio-entrada=D:\\legendas");
    }

    @Test
    void ignoraArgEntradaVazio() {
        String[] args = {"--tradutor.diretorio-entrada="};
        // Sem stdin interativo nos testes: solicitarPastas retorna vazio
        String[] resultado = Application.prepararArgumentosComPastasDoConsole(args);
        assertThat(resultado).isNull();
    }
}
