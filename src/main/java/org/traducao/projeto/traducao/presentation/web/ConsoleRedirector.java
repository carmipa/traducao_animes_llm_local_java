package org.traducao.projeto.traducao.presentation.web;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Interceptador global de System.out.
 * Redireciona tudo que é impresso no console padrão para o LogStreamService (SSE)
 * sem deixar de imprimir no console físico (terminal do CMD/PowerShell original).
 */
@Component
public class ConsoleRedirector {

    private final LogStreamService logStreamService;
    private final PrintStream originalOut;

    public ConsoleRedirector(LogStreamService logStreamService) {
        this.logStreamService = logStreamService;
        this.originalOut = System.out;
        
        // Ativa o redirecionamento
        System.setOut(new PrintStream(new DoubleOutputStream(originalOut, this::publicarLog), true, StandardCharsets.UTF_8));
    }

    private void publicarLog(String logMsg) {
        // Envia a mensagem limpa via Server-Sent Events, no canal da
        // operação em segundo plano que estiver em execução no momento
        // (ver LogStreamService#definirCanalAtual).
        logStreamService.publicarLog(logMsg);
    }

    /**
     * OutputStream interno que espelha os bytes gravados no fluxo original
     * e acumula buffers de linhas finalizadas com '\n' para despacho via SSE.
     */
    private static class DoubleOutputStream extends OutputStream {
        private final OutputStream original;
        private final java.util.function.Consumer<String> consumer;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        public DoubleOutputStream(OutputStream original, java.util.function.Consumer<String> consumer) {
            this.original = original;
            this.consumer = consumer;
        }

        @Override
        public void write(int b) throws IOException {
            original.write(b);
            if (b == '\n') {
                flushBuffer();
            } else if (b != '\r') {
                buffer.write(b);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            original.write(b, off, len);
            for (int i = 0; i < len; i++) {
                int ch = b[off + i];
                if (ch == '\n') {
                    flushBuffer();
                } else if (ch != '\r') {
                    buffer.write(ch);
                }
            }
        }

        private void flushBuffer() {
            if (buffer.size() > 0) {
                String line = buffer.toString(StandardCharsets.UTF_8);
                consumer.accept(line);
                buffer.reset();
            }
        }
    }
}
