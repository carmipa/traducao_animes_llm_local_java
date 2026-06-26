package org.traducao.projeto.traducao.infrastructure.adapters;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.traducao.projeto.traducao.domain.Lote;
import org.traducao.projeto.traducao.domain.TraducaoLote;
import org.traducao.projeto.traducao.infrastructure.config.LlmProperties;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MistralClientAdapterTest {

    private LlmProperties propriedades(String baseUrl) {
        return new LlmProperties(baseUrl, "mistral-nemo", 0.3, 2000, Duration.ofSeconds(2), Duration.ofSeconds(2));
    }

    @Test
    void traduzComSucessoQuandoLlmRespondeBem() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MistralClientAdapter adapter = new MistralClientAdapter(builder, propriedades("http://localhost"));

        String corpoResposta = """
            {"choices":[{"message":{"role":"assistant","content":"Olá!\\nAdeus."}}]}
            """;
        server.expect(requestTo("http://localhost/chat/completions"))
            .andRespond(withSuccess(corpoResposta, MediaType.APPLICATION_JSON));

        TraducaoLote resultado = adapter.traduzir(new Lote(1, List.of("Hello!", "Goodbye.")));

        assertThat(resultado.sucesso()).isTrue();
        assertThat(resultado.linhasTraduzidas()).containsExactly("Olá!", "Adeus.");
    }

    @Test
    void normalizaRespostaComCrLfLinhasVaziasExternasECercaMarkdown() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MistralClientAdapter adapter = new MistralClientAdapter(builder, propriedades("http://localhost"));

        String corpoResposta = """
            {"choices":[{"message":{"role":"assistant","content":"```\\r\\n\\r\\nOlá!\\r\\nAdeus.\\r\\n\\r\\n```"}}]}
            """;
        server.expect(requestTo("http://localhost/chat/completions"))
            .andRespond(withSuccess(corpoResposta, MediaType.APPLICATION_JSON));

        TraducaoLote resultado = adapter.traduzir(new Lote(1, List.of("Hello!", "Goodbye.")));

        assertThat(resultado.sucesso()).isTrue();
        assertThat(resultado.linhasTraduzidas()).containsExactly("Olá!", "Adeus.");
    }

    @Test
    void retornaFalhaQuandoChoiceNaoTemMensagem() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MistralClientAdapter adapter = new MistralClientAdapter(builder, propriedades("http://localhost"));

        server.expect(requestTo("http://localhost/chat/completions"))
            .andRespond(withSuccess("{\"choices\":[{}]}", MediaType.APPLICATION_JSON));

        TraducaoLote resultado = adapter.traduzir(new Lote(1, List.of("Hello!")));

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.mensagemErro()).contains("vazio");
    }

    @Test
    void retornaFalhaQuandoServidorRespondeErroHttp() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MistralClientAdapter adapter = new MistralClientAdapter(builder, propriedades("http://localhost"));

        for (int i = 0; i < 3; i++) {
            server.expect(requestTo("http://localhost/chat/completions")).andRespond(withServerError());
        }

        TraducaoLote resultado = adapter.traduzir(new Lote(1, List.of("Hello!")));

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.mensagemErro()).contains("após 3 tentativa(s)");
    }

    @Test
    void retornaFalhaQuandoRespostaNaoTemChoices() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MistralClientAdapter adapter = new MistralClientAdapter(builder, propriedades("http://localhost"));

        server.expect(requestTo("http://localhost/chat/completions"))
            .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        TraducaoLote resultado = adapter.traduzir(new Lote(1, List.of("Hello!")));

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.mensagemErro()).contains("vazia");
    }

    @Test
    void falhaDeConexaoRetornaTraducaoLoteComFalhaEmVezDeLancar() {
        RestClient.Builder builder = RestClient.builder();
        LlmProperties propriedadesInalcancaveis = propriedades("http://127.0.0.1:1");
        MistralClientAdapter adapter = new MistralClientAdapter(builder, propriedadesInalcancaveis);

        TraducaoLote resultado = adapter.traduzir(new Lote(1, List.of("Hello!")));

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.mensagemErro()).isNotBlank();
    }
}
