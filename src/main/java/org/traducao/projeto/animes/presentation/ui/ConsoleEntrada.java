package org.traducao.projeto.animes.presentation.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class ConsoleEntrada {

    private static final BufferedReader LEITOR = new BufferedReader(
        new InputStreamReader(System.in, StandardCharsets.UTF_8)
    );

    public record CaminhosPastas(String modo, String entrada, String saida, String formato) {}

    private ConsoleEntrada() {}

    public static Optional<CaminhosPastas> solicitarPastas() {
        imprimirBanner();

        try {
            String modoOpcao = lerOpcional(
                AnsiCores.colorir("Escolha (1/2/3) [Enter = 1]: ", AnsiCores.CYAN)
            );
            
            String modo = "1".equals(modoOpcao) || "".equals(modoOpcao) ? "TRADUZIR" : 
                          "3".equals(modoOpcao) ? "EXTRAIR" : "REMUXAR";
            
            imprimir("");
            if (modo.equals("TRADUZIR")) {
                imprimir(AnsiCores.colorir(">>> MODO TRADUZIR SELECIONADO <<<", AnsiCores.GREEN, true));
                return solicitarPastasTraducao(modo);
            } else if (modo.equals("EXTRAIR")) {
                imprimir(AnsiCores.colorir(">>> MODO EXTRAIR LEGENDAS SELECIONADO <<<", AnsiCores.MAGENTA, true));
                return solicitarPastasExtrator(modo);
            } else {
                imprimir(AnsiCores.colorir(">>> MODO REMUXAR (MKVMERGE) SELECIONADO <<<", AnsiCores.CYAN, true));
                return solicitarPastasRemuxer(modo);
            }
        } catch (IOException e) {
            imprimir(AnsiCores.colorir("ERRO ao ler do console: " + e.getMessage(), AnsiCores.RED, true));
            return Optional.empty();
        }
    }
    
    private static Optional<CaminhosPastas> solicitarPastasTraducao(String modo) throws IOException {
        String entrada = lerObrigatorio(
            AnsiCores.colorir(">>> Pasta com os arquivos (.ass/.ssa): ", AnsiCores.GREEN, true)
        );
        if (entrada == null) return Optional.empty();

        String saida = lerOpcional(
            AnsiCores.colorir(">>> Pasta de SAIDA (Enter = automatico): ", AnsiCores.CYAN)
        );
        if (saida == null) return Optional.empty();

        imprimir("");
        imprimir(AnsiCores.colorir("Pastas OK. Subindo o tradutor...", AnsiCores.GREEN, true));
        return Optional.of(new CaminhosPastas(modo, entrada, saida, null));
    }
    
    private static Optional<CaminhosPastas> solicitarPastasRemuxer(String modo) throws IOException {
        String entrada = lerObrigatorio(
            AnsiCores.colorir(">>> Pasta com os vídeos originais (.mkv): ", AnsiCores.GREEN, true)
        );
        if (entrada == null) return Optional.empty();

        String saida = lerOpcional(
            AnsiCores.colorir(">>> Pasta com legendas PTBR (Enter = automatico): ", AnsiCores.CYAN)
        );
        if (saida == null) return Optional.empty();

        imprimir("");
        imprimir(AnsiCores.colorir("Pastas OK. Subindo o remuxer...", AnsiCores.GREEN, true));
        return Optional.of(new CaminhosPastas(modo, entrada, saida, null));
    }

    private static Optional<CaminhosPastas> solicitarPastasExtrator(String modo) throws IOException {
        String entrada = lerObrigatorio(
            AnsiCores.colorir(">>> Pasta com os vídeos originais (.mkv): ", AnsiCores.GREEN, true)
        );
        if (entrada == null) return Optional.empty();

        imprimir(AnsiCores.colorir("Formatos suportados: ASS, PGS, SRT", AnsiCores.DIM));
        String formato = lerOpcional(
            AnsiCores.colorir(">>> Qual formato extrair? [Enter = ASS]: ", AnsiCores.MAGENTA)
        );
        if (formato == null || formato.isBlank()) formato = "ASS";

        imprimir("");
        imprimir(AnsiCores.colorir("Tudo OK. Subindo o extrator de " + formato.toUpperCase() + "...", AnsiCores.GREEN, true));
        return Optional.of(new CaminhosPastas(modo, entrada, "", formato.toUpperCase()));
    }

    public static void imprimirErroSaida() {
        imprimir("");
        imprimir(AnsiCores.colorir("ERRO: interrupção ou erro no console.", AnsiCores.RED, true));
        imprimir(AnsiCores.colorir("Rode:  .\\gradlew.bat bootRun --console=plain", AnsiCores.YELLOW));
        imprimir("");
    }

    private static void imprimirBanner() {
        String linha = AnsiCores.colorir("=".repeat(62), AnsiCores.BLUE);
        imprimir("");
        imprimir(linha);
        imprimir(AnsiCores.colorir("  PIPELINE INDUSTRIAL DE TRADUÇÃO E REMUX", AnsiCores.YELLOW, true));
        imprimir(linha);
        imprimir("");
        imprimir(AnsiCores.colorir("O que você deseja fazer?", AnsiCores.WHITE));
        imprimir(AnsiCores.colorir("  [1] Traduzir legendas com LLM Local", AnsiCores.GREEN));
        imprimir(AnsiCores.colorir("  [2] Remuxar legendas traduzidas nos MKVs", AnsiCores.CYAN));
        imprimir(AnsiCores.colorir("  [3] Extrair legendas embutidas de MKVs", AnsiCores.MAGENTA));
        imprimir("");
    }

    private static String lerObrigatorio(String prompt) throws IOException {
        String linha = lerLinha(prompt);
        if (linha == null) {
            imprimir(AnsiCores.colorir("ERRO: stdin fechado.", AnsiCores.RED, true));
            return null;
        }
        linha = linha.trim().replaceAll("[\"']", ""); // Strip quotes
        if (linha.isEmpty()) {
            imprimir(AnsiCores.colorir("ERRO: caminho vazio.", AnsiCores.RED, true));
            return null;
        }
        return linha;
    }

    private static String lerOpcional(String prompt) throws IOException {
        String linha = lerLinha(prompt);
        if (linha == null) return null;
        return linha.trim().replaceAll("[\"']", "");
    }

    private static String lerLinha(String prompt) throws IOException {
        System.out.print(prompt);
        System.out.flush();
        String linha = LEITOR.readLine();
        System.out.println(); // Previne que a barra de progresso do Gradle apague/sobrescreva a linha
        return linha;
    }

    private static void imprimir(String linha) {
        System.out.println(linha);
        System.out.flush();
    }
}

