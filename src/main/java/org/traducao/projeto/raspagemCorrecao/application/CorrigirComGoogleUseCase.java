package org.traducao.projeto.raspagemCorrecao.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.traducao.projeto.raspagemCorrecao.infrastructure.GoogleTranslateScraper;
import org.traducao.projeto.telemetria.OperacaoTelemetria;
import org.traducao.projeto.telemetria.TelemetriaService;
import org.traducao.projeto.traducao.presentation.ui.AnsiCores;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class CorrigirComGoogleUseCase {

    private static final Logger log = LoggerFactory.getLogger(CorrigirComGoogleUseCase.class);

    private final ObjectMapper mapper;
    private final GoogleTranslateScraper googleScraper;
    private final TelemetriaService telemetriaService;

    private static final Set<String> TERMOS_IGNORADOS = Set.of(
        "fire bolt", "argo vesta", "caelus hildr", "hildrsleif", "dios aedes vesta",
        "vana freya", "vana seith", "vana seith.", "zeo gullveig", "hildis vini",
        "agallis arvesynth", "remiste felis", "uchide no kozuchi", "feles cruz",
        "dubh daol", "zekka", "gralineze fromel", "gokoh", "astrea record"
    );

    public CorrigirComGoogleUseCase(
        ObjectMapper mapper,
        GoogleTranslateScraper googleScraper,
        TelemetriaService telemetriaService
    ) {
        this.mapper = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        this.googleScraper = googleScraper;
        this.telemetriaService = telemetriaService;
    }

    public int executar(Path diretorioCache) {
        long inicioMs = System.currentTimeMillis();
        out("Iniciando correção via Scraping Google Translate em: " + diretorioCache.toAbsolutePath());

        if (!Files.exists(diretorioCache)) {
            out(AnsiCores.RED + "Erro: A pasta especificada não foi localizada no disco." + AnsiCores.RESET);
            return 0;
        }

        int[] totalArquivosProcessados = {0};
        int[] totalLinhasCorrigidas = {0};

        try (Stream<Path> caminhos = Files.walk(diretorioCache)) {
            caminhos.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".cache.json"))
                    .forEach(arquivo -> {
                        processarArquivoCache(arquivo, totalArquivosProcessados, totalLinhasCorrigidas);
                    });

            out("Total de arquivos de cache analisados: " + totalArquivosProcessados[0]);
            out("Total de falas em inglês corrigidas via Google: " + totalLinhasCorrigidas[0]);

        } catch (IOException e) {
            out(AnsiCores.RED + "Erro ao varrer a pasta de cache: " + e.getMessage() + AnsiCores.RESET);
        }

        long duracaoMs = System.currentTimeMillis() - inicioMs;
        OperacaoTelemetria operacao = TelemetriaService.criarOperacao(
            "Correção Google (cache)",
            diretorioCache.toAbsolutePath().toString(),
            duracaoMs,
            totalArquivosProcessados[0],
            totalLinhasCorrigidas[0],
            totalLinhasCorrigidas[0]
        );
        String relatorio = """
            CORREÇÃO VIA GOOGLE TRANSLATE (CACHE)
            =====================================
            Pasta: %s
            Duração: %s
            Arquivos de cache analisados: %d
            Falas corrigidas via Google: %d
            """.formatted(
            diretorioCache.toAbsolutePath(),
            formatarDuracaoMs(duracaoMs),
            totalArquivosProcessados[0],
            totalLinhasCorrigidas[0]
        );
        telemetriaService.finalizarOperacao(
            operacao, diretorioCache, "correcao_google_cache", relatorio);
        out("Relatório salvo em: " + TelemetriaService.resolverPastaRelatorios(diretorioCache));

        return totalLinhasCorrigidas[0];
    }

    private String formatarDuracaoMs(long ms) {
        long segundos = ms / 1000;
        return segundos >= 60 ? (segundos / 60) + "min " + (segundos % 60) + "s" : segundos + "s";
    }

    private void out(String mensagem) {
        System.out.println(mensagem);
        log.info(mensagem);
    }

    private void processarArquivoCache(Path arquivo, int[] totalArquivos, int[] totalLinhas) {
        totalArquivos[0]++;
        String nomeArquivo = arquivo.getFileName().toString();
        System.out.println("Analisando: " + nomeArquivo);

        try {
            List<Map<String, Object>> entradas = mapper.readValue(arquivo.toFile(),
                    new TypeReference<List<Map<String, Object>>>() {});

            int linhasCorrigidasNesteArquivo = 0;
            boolean modificado = false;

            for (Map<String, Object> entrada : entradas) {
                String original = (String) entrada.get("original");
                String traduzido = (String) entrada.get("traduzido");

                if (original != null && !original.isBlank() && original.equals(traduzido)) {
                    if (deveIgnorar(original)) {
                        continue;
                    }

                    System.out.println("  -> Traduzindo linha " + entrada.get("indice") + " [" + entrada.get("estilo") + "]:");
                    System.out.println("     Inglês: " + AnsiCores.YELLOW + original + AnsiCores.RESET);

                    String traduzidoNovo = googleScraper.traduzir(original);
                    System.out.println("     Português: " + AnsiCores.GREEN + traduzidoNovo + AnsiCores.RESET);

                    entrada.put("traduzido", traduzidoNovo);
                    entrada.put("idiomaTraduzido", "pt-br");

                    linhasCorrigidasNesteArquivo++;
                    modificado = true;

                    try {
                        Thread.sleep(400);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            if (modificado) {
                mapper.writeValue(arquivo.toFile(), entradas);
                totalLinhas[0] += linhasCorrigidasNesteArquivo;
                System.out.println(AnsiCores.GREEN + "  [OK] " + linhasCorrigidasNesteArquivo + " falas traduzidas e salvas no cache." + AnsiCores.RESET + "\n");
            } else {
                System.out.println("  -> Nenhuma inconsistência encontrada neste arquivo.\n");
            }

        } catch (IOException e) {
            System.out.println(AnsiCores.RED + "  -> Erro ao ler/escrever o arquivo de cache: " + e.getMessage() + AnsiCores.RESET + "\n");
        }
    }

    private boolean deveIgnorar(String texto) {
        String textoLimpo = texto.replaceAll("\\{[^}]+\\}", "").strip();
        textoLimpo = textoLimpo.replaceAll("[^\\w\\s\\d]", "").strip();

        if (textoLimpo.isEmpty()) {
            return true;
        }

        String[] palavras = textoLimpo.split("\\s+");
        if (palavras.length <= 1) {
            return true;
        }

        if (palavras.length == 2 &&
            Character.isUpperCase(palavras[0].charAt(0)) &&
            Character.isUpperCase(palavras[1].charAt(0))) {
            return true;
        }

        return TERMOS_IGNORADOS.contains(textoLimpo.toLowerCase());
    }
}
