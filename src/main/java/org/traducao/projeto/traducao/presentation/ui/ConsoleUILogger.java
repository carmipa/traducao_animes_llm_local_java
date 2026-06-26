package org.traducao.projeto.traducao.presentation.ui;

import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Wrapper thread-safe em torno da barra de progresso (estilo tqdm). Todo
 * acesso a {@code pb} e sincronizado porque mensagens podem chegar
 * durante a tradução de um episódio.
 * <p>
 * O console e efêmero (a barra de progresso sobrescreve linhas antigas), por
 * isso toda mensagem também é espelhada no logger SLF4J, que persiste em
 * arquivo (ver {@code logging.file.name}) e sobrevive para análise posterior.
 */
@Component
public class ConsoleUILogger {

    private static final Logger log = LoggerFactory.getLogger(ConsoleUILogger.class);

    // Cores ANSI — ver AnsiCores
    private static final String ANSI_RESET = AnsiCores.RESET;
    private static final String ANSI_RED = AnsiCores.RED;
    private static final String ANSI_GREEN = AnsiCores.GREEN;
    private static final String ANSI_YELLOW = AnsiCores.YELLOW;
    private static final String ANSI_CYAN = AnsiCores.CYAN;

    private ProgressBar pb;

    public synchronized void iniciarLotes(int totalLotes, String nomeEpisodio) {
        if (pb != null) {
            pb.close();
        }
        pb = new ProgressBarBuilder()
                .setTaskName("Traduzindo " + nomeEpisodio)
                .setInitialMax(totalLotes)
                .setStyle(ProgressBarStyle.COLORFUL_UNICODE_BLOCK) // Upgrade visual aqui!
                .setUpdateIntervalMillis(100)
                .build();
    }

    public synchronized void log(String mensagem) {
        String cor = ANSI_CYAN; // Cor padrão das informações
        
        if (mensagem.contains("[ FAIL ]") || mensagem.contains("Erro") || mensagem.contains("Falha")) {
            log.warn(mensagem);
            cor = ANSI_RED;
        } else if (mensagem.contains("[ OK ]") || mensagem.contains("Sucesso") || mensagem.contains("Concluido") || mensagem.contains("concluido")) {
            log.info(mensagem);
            cor = ANSI_GREEN;
        } else if (mensagem.contains("[ WARN ]")) {
            log.warn(mensagem);
            cor = ANSI_YELLOW;
        } else {
            log.info(mensagem);
        }

        // Aplica a cor na string final para o console visual do usuário
        String msgVisual = cor + mensagem + ANSI_RESET;

        if (pb != null) {
            // Emula o tqdm.write(), onde a mensagem é impressa acima da barra sem quebrar a tela
            pb.stepBy(0); // Força um tick no estado
            System.out.println("\r\033[K" + msgVisual);
            pb.stepBy(0);
        } else {
            System.out.println(msgVisual);
        }
    }

    public synchronized void passoConcluido(int lotes) {
        if (pb != null) {
            pb.stepBy(lotes);
        }
    }

    public synchronized void finalizar() {
        if (pb != null) {
            pb.close();
            pb = null;
        }
    }
}
