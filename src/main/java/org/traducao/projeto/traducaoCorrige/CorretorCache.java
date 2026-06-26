package org.traducao.projeto.traducaoCorrige;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Programa Utilitário que realiza a limpeza seletiva do cache de tradução.
 * Remove traduções que falharam e foram salvas com o texto original em inglês (fallbacks),
 * permitindo que sejam reprocessadas com a nova lógica e prompts corrigidos.
 */
public class CorretorCache {

    // Cores ANSI para o console
    private static final String RESET = "\u001B[0m";
    private static final String VERDE = "\u001B[32m";
    private static final String AMARELO = "\u001B[33m";
    private static final String AZUL = "\u001B[34m";
    private static final String VERMELHO = "\u001B[31m";
    private static final String CIANO = "\u001B[36m";

    public static void main(String[] args) {
        System.out.println(CIANO + "==========================================================" + RESET);
        System.out.println(CIANO + "         CORRETOR DE CACHE DE TRADUÇÃO DE ANIMES          " + RESET);
        System.out.println(CIANO + "==========================================================" + RESET);

        Path diretorioCache = Path.of("cache");
        if (!Files.exists(diretorioCache)) {
            System.out.println(VERMELHO + "Erro: A pasta 'cache' não foi localizada no diretório atual (" + 
                               Path.of(".").toAbsolutePath() + ")." + RESET);
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        // Ativa a formatação "pretty print" para manter o JSON legível ao salvar
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        int[] totalArquivosProcessados = {0};
        int[] totalLinhasCorrigidas = {0};

        try (Stream<Path> caminhos = Files.walk(diretorioCache)) {
            caminhos.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".cache.json"))
                    .forEach(arquivo -> {
                        processarArquivoCache(arquivo, mapper, totalArquivosProcessados, totalLinhasCorrigidas);
                    });

            System.out.println(CIANO + "==========================================================" + RESET);
            System.out.println(VERDE + "Processamento concluído com sucesso!" + RESET);
            System.out.println("Total de arquivos de cache analisados: " + AZUL + totalArquivosProcessados[0] + RESET);
            System.out.println("Total de falas em inglês (fallbacks) limpas: " + AMARELO + totalLinhasCorrigidas[0] + RESET);
            System.out.println(CIANO + "==========================================================" + RESET);
            System.out.println("Agora, ao rodar o tradutor novamente, apenas as linhas corrigidas serão processadas pelo LLM.");

        } catch (IOException e) {
            System.out.println(VERMELHO + "Erro ao varrer a pasta de cache: " + e.getMessage() + RESET);
        }
    }

    private static void processarArquivoCache(Path arquivo, ObjectMapper mapper, int[] totalArquivos, int[] totalLinhas) {
        totalArquivos[0]++;
        String nomeArquivo = arquivo.getFileName().toString();
        System.out.println("Analisando: " + AZUL + nomeArquivo + RESET);

        try {
            // Deserializa o cache em uma lista de Mapas para manipulação dinâmica simples e segura
            List<Map<String, Object>> entradas = mapper.readValue(arquivo.toFile(), 
                    new TypeReference<List<Map<String, Object>>>() {});

            int linhasCorrigidasNesteArquivo = 0;
            boolean modificado = false;

            for (Map<String, Object> entrada : entradas) {
                String original = (String) entrada.get("original");
                String traduzido = (String) entrada.get("traduzido");

                // Se o texto original for idêntico ao traduzido, significa que houve fallback para o inglês.
                // Ignora strings vazias ou nulas.
                if (original != null && !original.isBlank() && original.equals(traduzido)) {
                    // Esvazia a tradução para forçar o reprocessamento da linha
                    entrada.put("traduzido", "");
                    linhasCorrigidasNesteArquivo++;
                    modificado = true;
                }
            }

            if (modificado) {
                // Grava o arquivo de volta no disco com a formatação idêntica à do projeto
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
