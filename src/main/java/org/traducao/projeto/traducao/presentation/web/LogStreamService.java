package org.traducao.projeto.traducao.presentation.web;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Serviço responsável por gerenciar conexões Server-Sent Events (SSE)
 * e despachar mensagens de log em tempo real para os clientes web conectados.
 * Cada linha publicada também é persistida em {@code logs/console-web.log},
 * já que o console do navegador (diferente de {@code logs/tradutor.log}) não
 * sobrevive a um reload de página ou ao fechamento da aba.
 */
@Service
public class LogStreamService {

    private static final Path ARQUIVO_LOG = Path.of("logs", "console-web.log");
    private static final char CHAR_ESC = (char) 27;
    private static final char CHAR_COLCHETE = (char) 91;
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    // Canal da operação em segundo plano atualmente em execução no
    // ExecutorService único do ApiController. Como só uma tarefa roda por
    // vez, definir isto como primeira linha de cada tarefa é suficiente
    // para rotear corretamente as linhas de System.out que ela imprime,
    // mesmo que o usuário troque de aba no navegador enquanto ela executa.
    private volatile String canalAtual = "console";

    public SseEmitter registrar() {
        SseEmitter emitter = new SseEmitter(1800000L); // Timeout de 30 minutos
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));

        // Envia um evento de boas-vindas para testar a conexão imediata
        try {
            emitter.send(SseEmitter.event()
                    .name("sistema")
                    .data("Conexão de fluxo de logs estabelecida."));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    public void definirCanalAtual(String canal) {
        this.canalAtual = canal;
    }

    public void publicarLog(String mensagem) {
        publicarLog(canalAtual, mensagem);
    }

    public void publicarLog(String canal, String mensagem) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(canal)
                        .data(mensagem));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
        persistirEmArquivo(canal, mensagem);
    }

    private void persistirEmArquivo(String canal, String mensagem) {
        String linhaLimpa = removerCodigosAnsi(mensagem);
        if (linhaLimpa.isBlank()) {
            return;
        }
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        String linha = "%s [%s] %s%n".formatted(timestamp, canal, linhaLimpa);
        try {
            Files.createDirectories(ARQUIVO_LOG.getParent());
            Files.writeString(ARQUIVO_LOG, linha, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Não loga via SLF4J aqui de propósito: o ConsoleRedirector encaminha
            // todo log (incluindo deste logger) de volta para publicarLog(),
            // o que causaria recursão se essa escrita continuasse falhando.
        }
    }

    /**
     * Remove sequências de cor ANSI (ESC + colchete + dígitos/ponto-e-vírgula
     * + 'm') do texto antes de persistir, deixando o arquivo de log em texto
     * puro. Feito caractere a caractere (em vez de regex) porque o par
     * ESC+colchete não sobrevive de forma confiável a ferramentas de
     * edição/exibição de texto.
     */
    private String removerCodigosAnsi(String texto) {
        StringBuilder limpo = new StringBuilder(texto.length());
        int i = 0;
        while (i < texto.length()) {
            char c = texto.charAt(i);
            if (c == CHAR_ESC && i + 1 < texto.length() && texto.charAt(i + 1) == CHAR_COLCHETE) {
                int j = i + 2;
                while (j < texto.length() && (Character.isDigit(texto.charAt(j)) || texto.charAt(j) == ';')) {
                    j++;
                }
                if (j < texto.length() && texto.charAt(j) == 'm') {
                    i = j + 1;
                    continue;
                }
            }
            limpo.append(c);
            i++;
        }
        return limpo.toString();
    }
}
