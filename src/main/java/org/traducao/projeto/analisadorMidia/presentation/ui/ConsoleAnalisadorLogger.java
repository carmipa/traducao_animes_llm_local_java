package org.traducao.projeto.analisadorMidia.presentation.ui;

import org.springframework.stereotype.Component;
import org.traducao.projeto.analisadorMidia.domain.*;
import org.traducao.projeto.traducao.presentation.ui.AnsiCores;

import java.nio.file.Path;

@Component
public class ConsoleAnalisadorLogger {

    public void cabecalho(String titulo) {
        String divisor = AnsiCores.colorir("=".repeat(80), AnsiCores.BLUE);
        System.out.println(divisor);
        System.out.println(AnsiCores.colorir("  " + titulo, AnsiCores.YELLOW, true));
        System.out.println(divisor);
        System.out.flush();
    }

    public void cabecalhoGrande(String titulo) {
        String divisor = AnsiCores.colorir("=".repeat(80), AnsiCores.MAGENTA);
        System.out.println("\n" + divisor);
        System.out.println(AnsiCores.colorir("  >>> " + titulo.toUpperCase() + " <<<", AnsiCores.WHITE, true));
        System.out.println(divisor);
        System.out.flush();
    }

    public void info(String msg) {
        System.out.println(AnsiCores.colorir("  [INFO] ", AnsiCores.CYAN) + msg);
        System.out.flush();
    }

    public void sucesso(String msg) {
        System.out.println(AnsiCores.colorir("  [OK] ", AnsiCores.GREEN, true) + AnsiCores.colorir(msg, AnsiCores.GREEN));
        System.out.flush();
    }

    public void alerta(String msg) {
        System.out.println(AnsiCores.colorir("  [AVISO] ", AnsiCores.YELLOW, true) + AnsiCores.colorir(msg, AnsiCores.YELLOW));
        System.out.flush();
    }

    public void erro(String msg) {
        System.out.println(AnsiCores.colorir("  [ERRO CRÍTICO] ", AnsiCores.RED, true) + AnsiCores.colorir(msg, AnsiCores.RED));
        System.out.flush();
    }

    public void imprimirResultado(AuditoriaResultado res) {
        System.out.println("\n" + AnsiCores.colorir("=".repeat(80), AnsiCores.BLUE));
        System.out.println(AnsiCores.colorir("AUDITORIA TÉCNICA: " + res.nomeArquivo(), AnsiCores.CYAN, true));
        System.out.println(AnsiCores.colorir("=".repeat(80), AnsiCores.BLUE));

        // 1. Container/Geral
        System.out.println("\n" + AnsiCores.colorir("ESTRUTURA GERAL", AnsiCores.MAGENTA, true));
        System.out.println("- Formato do Container: " + AnsiCores.colorir(res.container().formato(), AnsiCores.WHITE));
        
        double tamanhoGB = res.container().tamanhoBytes() / (1024.0 * 1024.0 * 1024.0);
        double tamanhoMB = res.container().tamanhoBytes() / (1024.0 * 1024.0);
        System.out.printf("- Tamanho: %s%n", AnsiCores.colorir(String.format("%.2f GiB (%.0f MB)", tamanhoGB, tamanhoMB), AnsiCores.WHITE));
        System.out.println("- Duração Total: " + AnsiCores.colorir(formatarSegundos(res.container().duracaoSegundos()), AnsiCores.WHITE));
        
        long brGeral = res.container().bitrateGeral();
        System.out.println("- Bitrate Geral: " + AnsiCores.colorir(brGeral > 0 ? (brGeral / 1000) + " kbps" : "N/A", AnsiCores.WHITE));
        System.out.println("- Aplicação de Escrita: " + AnsiCores.colorir(res.container().aplicacaoEscrita(), AnsiCores.WHITE));

        // 2. Fluxos de Vídeo
        System.out.println("\n" + AnsiCores.colorir("FLUXOS DE VÍDEO", AnsiCores.MAGENTA, true));
        for (VideoInfo v : res.videos()) {
            System.out.printf("  Fluxo %d (Track ID: %d)%n", v.index(), v.index());
            System.out.println("    Codec: " + AnsiCores.colorir(v.codecId() + " (" + v.format() + ")", AnsiCores.WHITE));
            System.out.println("    Resolução: " + AnsiCores.colorir(v.width() + "x" + v.height() + "p", AnsiCores.WHITE));
            System.out.println("    Profundidade de Cor: " + AnsiCores.colorir(v.bitDepth() + " bits", AnsiCores.WHITE));
            System.out.printf("    FPS: %s%n", AnsiCores.colorir(String.format("%.3f fps", v.fps()), AnsiCores.WHITE));
            System.out.println("    Aspect Ratio: " + AnsiCores.colorir(v.displayAspectRatio(), AnsiCores.WHITE));
            System.out.println("    Bitrate: " + AnsiCores.colorir(v.bitrate() > 0 ? (v.bitrate() / 1000) + " kbps" : "N/A", AnsiCores.WHITE));
        }

        // 3. Fluxos de Áudio
        System.out.println("\n" + AnsiCores.colorir("FLUXOS DE ÁUDIO", AnsiCores.MAGENTA, true));
        for (AudioInfo a : res.audios()) {
            System.out.printf("  Fluxo %d (Track ID: %d)%n", a.index(), a.index());
            System.out.println("    Idioma: " + AnsiCores.colorir(a.idioma(), AnsiCores.WHITE));
            System.out.println("    Codec/Formato: " + AnsiCores.colorir(a.format(), AnsiCores.WHITE));
            System.out.println("    Canais: " + AnsiCores.colorir(String.valueOf(a.channels()), AnsiCores.WHITE));
            System.out.printf("    Aostragem: %s%n", AnsiCores.colorir(String.format("%.1f kHz", a.sampleRateKHz()), AnsiCores.WHITE));
            System.out.println("    Bitrate: " + AnsiCores.colorir(a.bitrate() > 0 ? (a.bitrate() / 1000) + " kbps" : "N/A", AnsiCores.WHITE));
            System.out.println("    Título: " + AnsiCores.colorir(a.titulo(), AnsiCores.WHITE));
        }

        // 4. Fluxos de Legenda
        System.out.println("\n" + AnsiCores.colorir("FAIXAS DE LEGENDAS", AnsiCores.MAGENTA, true));
        if (res.legendas().isEmpty()) {
            System.out.println(AnsiCores.colorir("    NENHUMA LEGENDA ENCONTRADA", AnsiCores.RED, true));
            System.out.println(AnsiCores.colorir("    - Arquivo é uma RAW (sem legenda softsub)", AnsiCores.YELLOW));
            System.out.println(AnsiCores.colorir("    - Ou a legenda está fixada na imagem (hardsub)", AnsiCores.YELLOW));
        } else {
            for (LegendaInfo leg : res.legendas()) {
                System.out.printf("  Legenda %d (Track ID: %d)%n", leg.indexRelativo() + 1, leg.index());
                System.out.println("    Idioma: " + AnsiCores.colorir(leg.idioma(), AnsiCores.WHITE));
                System.out.println("    Formato: " + AnsiCores.colorir(leg.formato(), AnsiCores.WHITE));

                String corTipo = obterCorPorTipo(leg.tipoCurto());
                System.out.println("    Tipo: " + AnsiCores.colorir(leg.tipoCompleto(), corTipo));
                System.out.println("    Codec ID: " + AnsiCores.colorir(leg.codecId(), AnsiCores.WHITE));
                System.out.println("    Título: " + AnsiCores.colorir(leg.titulo(), AnsiCores.WHITE));

                if (leg.diferencaFimSegundos() != null) {
                    System.out.printf("    Duração Legenda: %s %s%n",
                        AnsiCores.colorir(formatarSegundos(leg.duracaoEfetivaSegundos()), AnsiCores.WHITE),
                        AnsiCores.colorir("(via " + leg.metodoDuracao() + ")", AnsiCores.YELLOW)
                    );
                    System.out.printf("    Diferença Fim: %s (Video - Legenda)%n",
                        AnsiCores.colorir(String.format("%+.3fs", leg.diferencaFimSegundos()), AnsiCores.WHITE)
                    );
                    System.out.printf("    Taxa de Drift: %s%n",
                        AnsiCores.colorir(String.format("%.3f s/hora", leg.driftRatio()), AnsiCores.WHITE)
                    );

                    String corVeredicto = obterCorPorVeredicto(leg.veredicto());
                    System.out.println("    Veredicto de Sincronia: " + AnsiCores.colorir(leg.veredicto(), corVeredicto, true));
                }
            }
        }

        // 5. Resumo Final
        System.out.println("\n" + AnsiCores.colorir("RESUMO FINAL", AnsiCores.MAGENTA, true));
        int totalFaixas = 1 + res.videos().size() + res.audios().size() + res.legendas().size();
        System.out.println("  Total de Faixas: " + AnsiCores.colorir(String.valueOf(totalFaixas), AnsiCores.WHITE));
        System.out.println("    Vídeo(s): " + AnsiCores.colorir(String.valueOf(res.videos().size()), AnsiCores.CYAN));
        System.out.println("    Áudio(s): " + AnsiCores.colorir(String.valueOf(res.audios().size()), AnsiCores.GREEN));
        System.out.println("    Legenda(s): " + AnsiCores.colorir(String.valueOf(res.legendas().size()), AnsiCores.YELLOW));

        for (LegendaInfo leg : res.legendas()) {
            String tituloStr = leg.titulo() != null && !leg.titulo().isBlank() ? " - " + leg.titulo() : "";
            String corTipo = obterCorPorTipo(leg.tipoCurto());
            
            System.out.printf("      [%d] %s: %s | %s: %s | %s: %s%s%n",
                leg.index(),
                AnsiCores.colorir("Idioma", AnsiCores.CYAN),
                AnsiCores.colorir(leg.idioma(), AnsiCores.WHITE),
                AnsiCores.colorir("Tipo", AnsiCores.CYAN),
                AnsiCores.colorir(leg.tipoCurto(), corTipo, true),
                AnsiCores.colorir("Formato", AnsiCores.CYAN),
                AnsiCores.colorir(leg.formato(), AnsiCores.WHITE),
                tituloStr
            );
        }

        System.out.println("\n" + AnsiCores.colorir("Auditoria finalizada com sucesso!", AnsiCores.GREEN));
        System.out.println(AnsiCores.colorir("=".repeat(80), AnsiCores.BLUE) + "\n");
        System.out.flush();
    }

    private String obterCorPorTipo(String tipoCurto) {
        if (tipoCurto == null) return AnsiCores.WHITE;
        return switch (tipoCurto.toUpperCase()) {
            case "ASS", "SSA" -> AnsiCores.YELLOW;
            case "PGS", "VOBSUB", "DVB", "HARDSUB" -> AnsiCores.RED;
            case "SRT", "WEBVTT", "MOV_TEXT" -> AnsiCores.GREEN;
            default -> AnsiCores.WHITE;
        };
    }

    private String obterCorPorVeredicto(String veredicto) {
        if (veredicto == null) return AnsiCores.WHITE;
        if (veredicto.contains("Sincronizada") || veredicto.contains("Parcial")) {
            return AnsiCores.GREEN;
        }
        if (veredicto.contains("Estiramento") || veredicto.contains("FPS")) {
            return AnsiCores.RED;
        }
        return AnsiCores.YELLOW;
    }

    private String formatarSegundos(Double seconds) {
        if (seconds == null || seconds <= 0.0) {
            return "N/A";
        }
        long h = (long) (seconds / 3600.0);
        long m = (long) ((seconds % 3600.0) / 60.0);
        double s = seconds % 60.0;
        return String.format("%02d:%02d:%06.3f", h, m, s);
    }
}
