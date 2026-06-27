package org.traducao.projeto.traducao.infrastructure.adapters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.traducao.projeto.traducao.domain.Lote;
import org.traducao.projeto.traducao.domain.StatusLlm;
import org.traducao.projeto.traducao.domain.TraducaoLote;
import org.traducao.projeto.traducao.domain.exceptions.RespostaLlmVaziaException;
import org.traducao.projeto.traducao.domain.ports.MistralPort;
import org.traducao.projeto.traducao.infrastructure.config.LlmProperties;
import org.traducao.projeto.traducao.infrastructure.contexto.GerenciadorContexto;
import org.traducao.projeto.traducao.infrastructure.dtos.RecordsMistral.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class MistralClientAdapter implements MistralPort {

    private static final Logger log = LoggerFactory.getLogger(MistralClientAdapter.class);

    private static final int MAX_TENTATIVAS = 3;
    private static final long PAUSA_ENTRE_TENTATIVAS_MS = 2_000;

    private final RestClient restClient;
    private final LlmProperties propriedades;
    private final GerenciadorContexto gerenciadorContexto;

    public MistralClientAdapter(RestClient.Builder builder, LlmProperties propriedades, GerenciadorContexto gerenciadorContexto) {
        this.propriedades = propriedades;
        this.gerenciadorContexto = gerenciadorContexto;
        // Os timeouts de conexao/leitura sao aplicados no builder pelo
        // RestClientCustomizer (ver RestClientConfig), nao aqui: assim este
        // adapter so faz baseUrl+build, o que mantem o builder mockavel em
        // testes com MockRestServiceServer.bindTo(builder).
        this.restClient = builder.baseUrl(propriedades.baseUrl()).build();
    }

    @Override
    public StatusLlm verificarDisponibilidade() {
        String modeloConfigurado = propriedades.model();
        try {
            ListaModelos resposta = restClient.get()
                .uri("/models")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(ListaModelos.class);

            List<ModeloDisponivel> modelos = resposta != null ? resposta.data() : null;
            if (modelos == null || modelos.isEmpty()) {
                return new StatusLlm(true, false,
                    "Servidor LLM em " + propriedades.baseUrl() + " respondeu, mas nenhum modelo está carregado em memória.");
            }

            // O nome/ID do modelo é configurável (Mistral, Gemma, Llama etc.) — a
            // comparação não pode supor um provedor específico.
            boolean modeloCarregado = modelos.stream()
                .map(ModeloDisponivel::id)
                .filter(id -> id != null)
                .anyMatch(id -> id.equalsIgnoreCase(modeloConfigurado) || id.contains(modeloConfigurado));

            if (!modeloCarregado) {
                String idsDisponiveis = modelos.stream()
                    .map(ModeloDisponivel::id)
                    .collect(Collectors.joining(", "));
                return new StatusLlm(true, false,
                    "Modelo configurado (\"" + modeloConfigurado + "\") não está entre os carregados no servidor. "
                        + "Carregados atualmente: [" + idsDisponiveis + "]");
            }

            return new StatusLlm(true, true,
                "Servidor LLM online e modelo \"" + modeloConfigurado + "\" carregado em memória.");
        } catch (Exception e) {
            return new StatusLlm(false, false,
                "Não foi possível conectar ao servidor LLM em " + propriedades.baseUrl() + ": " + e.getMessage());
        }
    }

    @Override
    public TraducaoLote traduzir(Lote lote) {
        String prompt = montarPrompt(lote);
        ChatRequest request = new ChatRequest(
            propriedades.model(),
            List.of(
                new Mensagem("system", gerenciadorContexto.obterPromptAtivo()),
                new Mensagem("user", prompt)
            ),
            propriedades.temperature(),
            propriedades.maxTokens()
        );

        Exception ultimaFalha = null;
        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS; tentativa++) {
            try {
                log.debug("Enviando lote {} ao LLM ({} linha(s)) — tentativa {}/{}",
                    lote.idLote(), lote.linhasOriginais().size(), tentativa, MAX_TENTATIVAS);
                long inicio = System.currentTimeMillis();

                RespostaLlm resposta = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(RespostaLlm.class);

                long duracaoMs = System.currentTimeMillis() - inicio;

                if (resposta == null || resposta.choices() == null || resposta.choices().isEmpty()) {
                    throw new RespostaLlmVaziaException("Resposta vazia do LLM para o lote " + lote.idLote());
                }

                Mensagem mensagem = resposta.choices().getFirst().message();
                String traduzidoText = mensagem != null ? mensagem.content() : null;
                if (traduzidoText == null || traduzidoText.isBlank()) {
                    throw new RespostaLlmVaziaException("Conteudo vazio retornado pelo LLM para o lote " + lote.idLote());
                }

                List<String> linhasTraduzidas = extrairLinhasTraduzidas(traduzidoText);
                log.info("Lote {} traduzido em {} ms ({} -> {} linha(s))",
                    lote.idLote(), duracaoMs, lote.linhasOriginais().size(), linhasTraduzidas.size());

                return new TraducaoLote(lote.idLote(), linhasTraduzidas, true, null);

            } catch (RespostaLlmVaziaException e) {
                log.warn(e.getMessage());
                return new TraducaoLote(lote.idLote(), null, false, e.getMessage());
            } catch (RestClientResponseException e) {
                ultimaFalha = e;
                String mensagem = "LLM respondeu com erro HTTP " + e.getStatusCode() + " para o lote " + lote.idLote();
                log.warn("{} (tentativa {}/{}): {}", mensagem, tentativa, MAX_TENTATIVAS, e.getMessage());
            } catch (ResourceAccessException e) {
                ultimaFalha = e;
                log.warn("Falha de rede/timeout ao chamar o LLM para o lote {} (tentativa {}/{}): {}",
                    lote.idLote(), tentativa, MAX_TENTATIVAS, e.getMessage());
            } catch (Exception e) {
                ultimaFalha = e;
                if (e.getMessage() != null && e.getMessage().contains("interrupt")) {
                    log.warn("Erro ao traduzir o lote {} - Thread abortada (tentativa {}/{})",
                        lote.idLote(), tentativa, MAX_TENTATIVAS);
                } else {
                    log.warn("Erro ao traduzir o lote {} (tentativa {}/{}): {}",
                        lote.idLote(), tentativa, MAX_TENTATIVAS, e.getMessage());
                }
            }

            if (tentativa < MAX_TENTATIVAS) {
                try {
                    Thread.sleep(PAUSA_ENTRE_TENTATIVAS_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        String mensagemFinal = "Erro ao traduzir o lote " + lote.idLote()
            + " após " + MAX_TENTATIVAS + " tentativa(s)";
        if (ultimaFalha != null) {
            mensagemFinal += ": " + ultimaFalha.getMessage();
        }
        log.error(mensagemFinal);
        return new TraducaoLote(lote.idLote(), null, false, mensagemFinal);
    }

    private String montarPrompt(Lote lote) {
        int totalLinhas = lote.linhasOriginais().size();
        String linhas = String.join("\n", lote.linhasOriginais());
        return "Traduza estas " + totalLinhas + " linha(s), uma por linha. Responda com exatamente "
            + totalLinhas + " linha(s) de saida, na mesma ordem:\n" + linhas;
    }

    private List<String> extrairLinhasTraduzidas(String texto) {
        String normalizado = texto.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (normalizado.startsWith("```") && normalizado.endsWith("```")) {
            normalizado = removerCercaMarkdown(normalizado).strip();
        }

        String[] linhas = normalizado.split("\n", -1);
        List<String> resultado = new ArrayList<>(linhas.length);
        for (String linha : linhas) {
            resultado.add(linha.stripTrailing());
        }
        return resultado;
    }

    private String removerCercaMarkdown(String texto) {
        int primeiraQuebra = texto.indexOf('\n');
        int ultimaCerca = texto.lastIndexOf("```");
        if (primeiraQuebra < 0 || ultimaCerca <= primeiraQuebra) {
            return texto;
        }
        return texto.substring(primeiraQuebra + 1, ultimaCerca);
    }
}
