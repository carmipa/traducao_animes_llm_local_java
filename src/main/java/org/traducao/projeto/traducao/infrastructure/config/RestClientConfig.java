package org.traducao.projeto.traducao.infrastructure.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * Aplica timeouts de conexao/leitura ao {@link org.springframework.web.client.RestClient.Builder}
 * autoconfigurado pelo Spring Boot, antes dele chegar ao {@code MistralClientAdapter}.
 * Sem isso, uma chamada ao LLM local que trava (ex: servidor LM Studio sem
 * resposta) deixaria a Virtual Thread bloqueada indefinidamente.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClientCustomizer timeoutRestClientCustomizer(LlmProperties propriedades) {
        return builder -> {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout((int) propriedades.connectTimeout().toMillis());
            requestFactory.setReadTimeout((int) propriedades.readTimeout().toMillis());
            builder.requestFactory(requestFactory);
        };
    }
}
