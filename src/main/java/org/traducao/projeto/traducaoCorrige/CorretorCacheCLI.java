package org.traducao.projeto.traducaoCorrige;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.traducao.projeto.traducao.infrastructure.config.TradutorProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * CommandLineRunner que realiza a limpeza do cache de tradução integrado ao fluxo do Spring.
 * Ativado quando a propriedade app.modo é configurada como "CORRIGIR_CACHE".
 */
@Component
@ConditionalOnProperty(name = "app.modo", havingValue = "CORRIGIR_CACHE")
public class CorretorCacheCLI implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CorretorCacheCLI.class);

    private final TradutorProperties propriedades;
    private final ObjectMapper mapper;

    // Cores ANSI para o console
    private static final String RESET = "\u001B[0m";
    private static final String VERDE = "\u001B[32m";
    private static final String AMARELO = "\u001B[33m";
    private static final String AZUL = "\u001B[34m";
    private static final String VERMELHO = "\u001B[31m";
    private static final String CIANO = "\u001B[36m";

    public CorretorCacheCLI(TradutorProperties propriedades, ObjectMapper mapper) {
        this.propriedades = propriedades;
        // Faz cópia do mapper do Spring e garante a formatação identada ao persistir
        this.mapper = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println(CIANO + "==========================================================" + RESET);
        System.out.println(CIANO + "         CORRETOR DE CACHE DE TRADUÇÃO DE ANIMES          " + RESET);
        System.out.println(CIANO + "==========================================================" + RESET);

        String entradaUsuario = propriedades.diretorioEntrada();
        Path diretorioCache = Path.of(entradaUsuario != null && !entradaUsuario.isBlank() ? entradaUsuario : "cache");

        System.out.println("Diretório de cache selecionado: " + AZUL + diretorioCache.toAbsolutePath() + RESET);

        if (!Files.exists(diretorioCache)) {
            System.out.println(VERMELHO + "Erro: A pasta especificada não foi localizada no disco." + RESET);
            return;
        }

        int[] totalArquivosProcessados = {0};
        int[] totalLinhasCorrigidas = {0};

        try (Stream<Path> caminhos = Files.walk(diretorioCache)) {
            caminhos.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".cache.json"))
                    .forEach(arquivo -> {
                        processarArquivoCache(arquivo, totalArquivosProcessados, totalLinhasCorrigidas);
                    });

            System.out.println(CIANO + "==========================================================" + RESET);
            System.out.println(VERDE + "Processamento concluído com sucesso!" + RESET);
            System.out.println("Total de arquivos de cache analisados: " + AZUL + totalArquivosProcessados[0] + RESET);
            System.out.println("Total de falas em inglês (fallbacks) limpas: " + AMARELO + totalLinhasCorrigidas[0] + RESET);
            System.out.println(CIANO + "==========================================================" + RESET);
            System.out.println("Agora as linhas corrigidas serão reenviadas ao LLM em uma nova tradução.");

        } catch (IOException e) {
            System.out.println(VERMELHO + "Erro ao varrer a pasta de cache: " + e.getMessage() + RESET);
        }
    }

    private void processarArquivoCache(Path arquivo, int[] totalArquivos, int[] totalLinhas) {
        totalArquivos[0]++;
        String nomeArquivo = arquivo.getFileName().toString();
        System.out.println("Analisando: " + AZUL + nomeArquivo + RESET);

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
                System.out.println(VERDE + "  -> " + linhasCorrigidasNesteArquivo + " falas limpas e salvas." + RESET);
            } else {
                System.out.println("  -> Nenhuma inconsistência encontrada neste arquivo.");
            }

        } catch (IOException e) {
            System.out.println(VERMELHO + "  -> Erro ao ler/escrever o arquivo de cache: " + e.getMessage() + RESET);
        }
    }
}
