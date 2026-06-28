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

    private int totalFalasCache = 0;
    private int totalFalasNovas = 0;
    private int totalAvisos = 0;

    public synchronized void iniciarLotes(int totalLotes, String nomeEpisodio) {
        fecharBarraComSeguranca();
        try {
            pb = new ProgressBarBuilder()
                    .setTaskName("Traduzindo " + nomeEpisodio)
                    .setInitialMax(totalLotes)
                    .setStyle(ProgressBarStyle.COLORFUL_UNICODE_BLOCK) // Upgrade visual aqui!
                    .setUpdateIntervalMillis(100)
                    .build();
        } catch (RuntimeException e) {
            log.warn("Não foi possível iniciar a barra de progresso (terminal incompatível); continuando sem ela: {}", e.getMessage());
            pb = null;
        }
    }

    /**
     * Imprime um separador bem visível indicando o início da tradução de um novo
     * episódio, para diferenciar claramente esse marco das mensagens linha-a-linha
     * (lote a lote) que vêm a seguir no mesmo console efêmero.
     */
    public synchronized void tituloEpisodio(String nomeEpisodio, int indiceAtual, int totalEpisodios) {
        fecharBarraComSeguranca();
        String cabecalho = String.format("EPISÓDIO %d/%d: %s", indiceAtual, totalEpisodios, nomeEpisodio);
        String linha = "=".repeat(Math.max(cabecalho.length() + 8, 70));

        System.out.println();
        System.out.println(ANSI_CYAN + linha + ANSI_RESET);
        System.out.println(AnsiCores.BOLD + AnsiCores.YELLOW + ">>> " + cabecalho + ANSI_RESET);
        System.out.println(ANSI_CYAN + linha + ANSI_RESET);

        log.info(linha);
        log.info(">>> {}", cabecalho);
    }

    public synchronized void log(String mensagem) {
        // INFO fica sem cor (herda o foreground padrão do terminal): cor é
        // reservada para o que precisa de atenção (sucesso/aviso/erro) e para
        // cabeçalhos, evitando fadiga visual em telas com muita linha de info.
        String cor = null;

        if (mensagem.contains("[ FAIL ]") || mensagem.contains("Erro") || mensagem.contains("Falha")) {
            log.warn(mensagem);
            cor = ANSI_RED;
        } else if (mensagem.contains("[ OK ]") || mensagem.contains("Sucesso") || mensagem.contains("Concluido") || mensagem.contains("concluido")) {
            log.info(mensagem);
            cor = ANSI_GREEN;
        } else if (mensagem.contains("[ WARN ]")) {
            log.warn(mensagem);
            cor = ANSI_YELLOW;
            totalAvisos++;
        } else {
            log.info(mensagem);
        }

        // Aplica a cor na string final para o console visual do usuário
        String msgVisual = cor != null ? cor + mensagem + ANSI_RESET : mensagem;

        if (pb == null) {
            System.out.println(msgVisual);
            return;
        }

        // Emula o tqdm.write(): pausa o redesenho automático da barra (que corre
        // numa thread própria a cada 100ms) para que ele não escreva por cima
        // desta mensagem no meio da impressão — a versão anterior só dava um
        // "tick" (stepBy(0)) sem nenhuma garantia de exclusão mútua com aquela
        // thread, o que corrompia a barra ou a deixava com leitura desatualizada
        // (parecia "travada" numa porcentagem antiga).
        //
        // A biblioteca de terceiros (me.tongfei:progressbar) pode lançar
        // exceções de renderização dependendo do terminal/console (ex.:
        // `--console=plain` do Gradle). Isso é puramente cosmético e NUNCA deve
        // abortar a tradução em andamento — por isso qualquer falha aqui apenas
        // desativa a barra para o resto do episódio, em vez de propagar.
        boolean mensagemImpressa = false;
        try {
            pb.pause();
            System.out.print("\r\033[K");
            System.out.println(msgVisual);
            mensagemImpressa = true;
            pb.resume();
            pb.refresh();
        } catch (RuntimeException e) {
            log.warn("Barra de progresso falhou ao renderizar; desativando-a para o restante deste episódio: {}", e.getMessage());
            fecharBarraComSeguranca();
            if (!mensagemImpressa) {
                System.out.println(msgVisual);
            }
        }
    }

    public synchronized void passoConcluido(int lotes) {
        if (pb == null) {
            return;
        }
        try {
            pb.stepBy(lotes);
            pb.refresh();
        } catch (RuntimeException e) {
            log.warn("Barra de progresso falhou ao avançar; desativando-a para o restante deste episódio: {}", e.getMessage());
            fecharBarraComSeguranca();
        }
    }

    public synchronized void finalizar() {
        fecharBarraComSeguranca();
    }

    private void fecharBarraComSeguranca() {
        if (pb == null) {
            return;
        }
        try {
            pb.close();
        } catch (RuntimeException e) {
            log.warn("Falha ao fechar a barra de progresso (ignorada): {}", e.getMessage());
        } finally {
            pb = null;
        }
    }

    public synchronized void registrarFalasCache(int quantidade) {
        totalFalasCache += quantidade;
    }

    public synchronized void registrarFalasNovas(int quantidade) {
        totalFalasNovas += quantidade;
    }

    public synchronized int totalFalasCache() {
        return totalFalasCache;
    }

    public synchronized int totalFalasNovas() {
        return totalFalasNovas;
    }

    public synchronized int totalAvisos() {
        return totalAvisos;
    }
}
