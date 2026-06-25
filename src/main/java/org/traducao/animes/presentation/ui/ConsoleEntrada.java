package org.traducao.animes.presentation.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Leitura interativa via {@link System#in} e {@link System#out}, estilo {@code input()} do Python.
 * Classe estatica (sem Spring) para rodar no {@code Application.main} antes do contexto subir.
 */
public final class ConsoleEntrada {

    private static final BufferedReader LEITOR = new BufferedReader(
        new InputStreamReader(System.in, StandardCharsets.UTF_8)
    );

    public record CaminhosPastas(String entrada, String saida, String cache) {}

    private ConsoleEntrada() {}

    public static Optional<CaminhosPastas> solicitarPastas() {
        imprimirBanner();

        try {
            String entrada = lerObrigatorio(
                AnsiCores.colorir(">>> Digite o caminho da pasta com os arquivos .ass/.ssa: ", AnsiCores.GREEN, true)
            );
            if (entrada == null) {
                return Optional.empty();
            }

            imprimir("");
            imprimir(AnsiCores.colorir(
                "Pasta de SAIDA (legendas traduzidas). Enter = calculo automatico.",
                AnsiCores.DIM
            ));
            String saida = lerOpcional(
                AnsiCores.colorir(">>> Pasta de SAIDA (Enter = automatico): ", AnsiCores.CYAN)
            );
            if (saida == null) {
                return Optional.empty();
            }

            imprimir("");
            imprimir(AnsiCores.colorir(
                "Pasta de CACHE (opcional). Enter = <pasta de saida>/cache.",
                AnsiCores.DIM
            ));
            String cache = lerOpcional(
                AnsiCores.colorir(">>> Pasta de CACHE (Enter = automatico): ", AnsiCores.CYAN)
            );
            if (cache == null) {
                return Optional.empty();
            }

            imprimir("");
            imprimir(AnsiCores.colorir("Pastas OK. Subindo o tradutor...", AnsiCores.GREEN, true));
            imprimir("");

            return Optional.of(new CaminhosPastas(entrada, saida, cache));
        } catch (IOException e) {
            imprimir(AnsiCores.colorir("ERRO ao ler do console: " + e.getMessage(), AnsiCores.RED, true));
            return Optional.empty();
        }
    }

    public static void imprimirErroSaida() {
        imprimir("");
        imprimir(AnsiCores.colorir("ERRO: pasta de entrada nao informada.", AnsiCores.RED, true));
        imprimir(AnsiCores.colorir("Rode:  .\\gradlew.bat bootRun --console=plain", AnsiCores.YELLOW));
        imprimir(AnsiCores.colorir(
            "Ou:    .\\gradlew.bat bootRun --args=\"--tradutor.diretorio-entrada=D:\\caminho\"",
            AnsiCores.YELLOW
        ));
        imprimir("");
    }

    private static void imprimirBanner() {
        String linha = AnsiCores.colorir("=".repeat(62), AnsiCores.BLUE);
        imprimir("");
        imprimir(linha);
        imprimir(AnsiCores.colorir("  TRADUTOR DE ANIMES - configuracao de pastas", AnsiCores.YELLOW, true));
        imprimir(linha);
        imprimir("");
        imprimir(AnsiCores.colorir(
            "Informe onde estao os arquivos de legenda que serao traduzidos.",
            AnsiCores.WHITE
        ));
        imprimir(AnsiCores.colorir(
            "Exemplo: D:\\animes\\meu_anime\\legendas_eng",
            AnsiCores.DIM
        ));
        imprimir("");
    }

    private static String lerObrigatorio(String prompt) throws IOException {
        String linha = lerLinha(prompt);
        if (linha == null) {
            imprimir(AnsiCores.colorir(
                "ERRO: stdin fechado (Gradle nao repassou o teclado).", AnsiCores.RED, true));
            imprimir(AnsiCores.colorir("Use: .\\gradlew.bat bootRun --console=plain", AnsiCores.YELLOW));
            return null;
        }
        linha = linha.trim();
        if (linha.isEmpty()) {
            imprimir(AnsiCores.colorir("ERRO: caminho vazio.", AnsiCores.RED, true));
            return null;
        }
        return linha;
    }

    private static String lerOpcional(String prompt) throws IOException {
        String linha = lerLinha(prompt);
        if (linha == null) {
            imprimir(AnsiCores.colorir("ERRO: stdin fechado.", AnsiCores.RED, true));
            return null;
        }
        return linha.trim();
    }

    /** Bloqueia ate o usuario pressionar Enter (igual input() no Python). */
    private static String lerLinha(String prompt) throws IOException {
        System.out.print(prompt);
        System.out.flush();
        return LEITOR.readLine();
    }

    private static void imprimir(String linha) {
        System.out.println(linha);
        System.out.flush();
    }
}
