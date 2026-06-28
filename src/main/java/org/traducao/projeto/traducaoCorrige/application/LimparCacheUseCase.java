package org.traducao.projeto.traducaoCorrige.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.traducao.projeto.telemetria.OperacaoTelemetria;
import org.traducao.projeto.telemetria.TelemetriaService;
import org.traducao.projeto.traducao.presentation.ui.AnsiCores;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class LimparCacheUseCase {

    private static final Logger log = LoggerFactory.getLogger(LimparCacheUseCase.class);
    private final ObjectMapper mapper;
    private final TelemetriaService telemetriaService;

    public LimparCacheUseCase(ObjectMapper mapper, TelemetriaService telemetriaService) {
        this.mapper = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        this.telemetriaService = telemetriaService;
    }

    public int executar(Path diretorioCache) {
        long inicioMs = System.currentTimeMillis();
        out("Iniciando limpeza de cache na pasta: " + diretorioCache.toAbsolutePath());
        
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
            out("Total de falas em inglês (fallbacks) limpas: " + totalLinhasCorrigidas[0]);

        } catch (IOException e) {
            out(AnsiCores.RED + "Erro ao varrer a pasta de cache: " + e.getMessage() + AnsiCores.RESET);
        }

        long duracaoMs = System.currentTimeMillis() - inicioMs;
        OperacaoTelemetria operacao = TelemetriaService.criarOperacao(
            "Limpeza de Cache",
            diretorioCache.toAbsolutePath().toString(),
            duracaoMs,
            totalArquivosProcessados[0],
            totalLinhasCorrigidas[0],
            totalLinhasCorrigidas[0]
        );
        String relatorio = """
            LIMPEZA DE CACHE
            ================
            Pasta: %s
            Duração: %s
            Arquivos de cache analisados: %d
            Falas em inglês (fallbacks) limpas: %d
            """.formatted(
            diretorioCache.toAbsolutePath(),
            formatarDuracaoMs(duracaoMs),
            totalArquivosProcessados[0],
            totalLinhasCorrigidas[0]
        );
        telemetriaService.finalizarOperacao(
            operacao, diretorioCache, "limpeza_cache", relatorio);
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
                    entrada.put("traduzido", "");
                    linhasCorrigidasNesteArquivo++;
                    modificado = true;
                }
            }

            if (modificado) {
                mapper.writeValue(arquivo.toFile(), entradas);
                totalLinhas[0] += linhasCorrigidasNesteArquivo;
                System.out.println(AnsiCores.GREEN + "  -> " + linhasCorrigidasNesteArquivo + " falas limpas e salvas." + AnsiCores.RESET);
            } else {
                System.out.println("  -> Nenhuma inconsistência encontrada neste arquivo.");
            }

        } catch (IOException e) {
            System.out.println(AnsiCores.RED + "  -> Erro ao ler/escrever o arquivo de cache: " + e.getMessage() + AnsiCores.RESET);
        }
    }
}
