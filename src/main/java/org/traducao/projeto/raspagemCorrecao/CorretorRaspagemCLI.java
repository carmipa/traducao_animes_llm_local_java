package org.traducao.projeto.raspagemCorrecao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.traducao.projeto.traducao.infrastructure.config.TradutorProperties;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * CommandLineRunner que realiza a tradução das falas residuais pendentes em inglês
 * utilizando raspagem na API gratuita e sem chaves do Google Translate.
 * Ativado quando a propriedade app.modo é configurada como "RASPAGEM_CORRECAO".
 */
@Component
@ConditionalOnProperty(name = "app.modo", havingValue = "RASPAGEM_CORRECAO")
public class CorretorRaspagemCLI implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CorretorRaspagemCLI.class);

    private final TradutorProperties propriedades;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    // Cores ANSI para o console
    private static final String RESET = "\u001B[0m";
    private static final String VERDE = "\u001B[32m";
    private static final String AMARELO = "\u001B[33m";
    private static final String AZUL = "\u001B[34m";
    private static final String VERMELHO = "\u001B[31m";
    private static final String CIANO = "\u001B[36m";

    // Lista de termos e magias conhecidas de DanMachi que devem permanecer inalterados
    private static final Set<String> TERMOS_IGNORADOS = Set.of(
        "fire bolt", "argo vesta", "caelus hildr", "hildrsleif", "dios aedes vesta",
        "vana freya", "vana seith", "vana seith.", "zeo gullveig", "hildis vini",
        "agallis arvesynth", "remiste felis", "uchide no kozuchi", "feles cruz",
        "dubh daol", "zekka", "gralineze fromel", "gokoh", "astrea record"
    );

    public CorretorRaspagemCLI(TradutorProperties propriedades, ObjectMapper mapper) {
        this.propriedades = propriedades;
        this.mapper = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println(CIANO + "==========================================================" + RESET);
        System.out.println(CIANO + "      CORRETOR DE CACHE VIA GOOGLE TRANSLATE (RASPAGEM)   " + RESET);
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
            System.out.println("Total de falas em inglês corrigidas via Google: " + AMARELO + totalLinhasCorrigidas[0] + RESET);
            System.out.println(CIANO + "==========================================================" + RESET);
            System.out.println("Agora rode a opção de Traduzir novamente para compilar as legendas finais instantaneamente.");

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
                    // Executa a heurística para pular nomes e magias que não devem ser traduzidos
                    if (deveIgnorar(original)) {
                        continue;
                    }

                    System.out.println("  -> Traduzindo linha " + entrada.get("indice") + " [" + entrada.get("estilo") + "]:");
                    System.out.println("     Inglês: " + AMARELO + original + RESET);

                    String traduzidoNovo = traduzirViaGoogle(original);
                    System.out.println("     Português: " + VERDE + traduzidoNovo + RESET);

                    entrada.put("traduzido", traduzidoNovo);
                    entrada.put("idiomaTraduzido", "pt-br");
                    
                    linhasCorrigidasNesteArquivo++;
                    modificado = true;

                    // Pausa de cortesia para evitar rate limit do Google Translate
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
                System.out.println(VERDE + "  [OK] " + linhasCorrigidasNesteArquivo + " falas traduzidas e salvas no cache." + RESET + "\n");
            } else {
                System.out.println("  -> Nenhuma inconsistência encontrada neste arquivo.\n");
            }

        } catch (IOException e) {
            System.out.println(VERMELHO + "  -> Erro ao ler/escrever o arquivo de cache: " + e.getMessage() + RESET + "\n");
        }
    }

    private boolean deveIgnorar(String texto) {
        // Remove tags de formatação ASS para fazer uma checagem limpa
        String textoLimpo = texto.replaceAll("\\{[^}]+\\}", "").strip();
        textoLimpo = textoLimpo.replaceAll("[^\\w\\s\\d]", "").strip(); // Remove pontuações

        if (textoLimpo.isEmpty()) {
            return true;
        }

        String[] palavras = textoLimpo.split("\\s+");
        
        // 1. Uma única palavra (ex: "Bell", "Freya", "Syr", "Huh")
        if (palavras.length <= 1) {
            return true;
        }

        // 2. Duas palavras e ambas começam com maiúscula (ex: "Bell Cranel", "Liliruca Arde")
        if (palavras.length == 2 && 
            Character.isUpperCase(palavras[0].charAt(0)) && 
            Character.isUpperCase(palavras[1].charAt(0))) {
            return true;
        }

        // 3. Magias e termos conhecidos
        if (TERMOS_IGNORADOS.contains(textoLimpo.toLowerCase())) {
            return true;
        }

        return false;
    }

    private String traduzirViaGoogle(String textoOriginal) {
        List<String> tags = new ArrayList<>();
        
        // Matcher para encontrar as tags de estilo do ASS {...}
        Pattern patternTags = Pattern.compile("\\{[^}]+\\}");
        Matcher matcher = patternTags.matcher(textoOriginal);
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;
        
        while (matcher.find()) {
            sb.append(textoOriginal, lastEnd, matcher.start());
            tags.add(matcher.group());
            sb.append(" [T").append(tags.size() - 1).append("] ");
            lastEnd = matcher.end();
        }
        sb.append(textoOriginal, lastEnd, textoOriginal.length());
        String textoMascarado = sb.toString();

        // Trata quebras de linha \N
        boolean temQuebra = textoMascarado.contains("\\N");
        if (temQuebra) {
            textoMascarado = textoMascarado.replace("\\N", " [B] ");
        }

        textoMascarado = textoMascarado.replaceAll("\\s+", " ").strip();

        if (textoMascarado.isEmpty()) {
            return textoOriginal;
        }

        try {
            String query = URLEncoder.encode(textoMascarado, StandardCharsets.UTF_8);
            String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=pt&dt=t&q=" + query;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                log.warn("Erro HTTP na chamada do Google Translate: {}", response.statusCode());
                return textoOriginal;
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode segments = root.get(0);
            StringBuilder resultadoTraduzido = new StringBuilder();
            
            if (segments != null && segments.isArray()) {
                for (JsonNode segment : segments) {
                    JsonNode text = segment.get(0);
                    if (text != null && !text.isNull()) {
                        resultadoTraduzido.append(text.asText());
                    }
                }
            }

            String traduzido = resultadoTraduzido.toString();

            // Restaurar quebras de linha \N
            if (temQuebra) {
                traduzido = traduzido.replaceAll("(?i)\\s*\\[b\\]\\s*", "\\\\N");
            }

            // Restaurar tags de estilo {...}
            for (int i = 0; i < tags.size(); i++) {
                String pattern = "(?i)\\s*\\[t" + i + "\\]\\s*";
                traduzido = traduzido.replaceAll(pattern, Matcher.quoteReplacement(tags.get(i)));
            }

            // Sanitizar a quebra de linha contra erros comuns de espaçamento
            traduzido = traduzido.replace("\\ N", "\\N").replace("\\ n", "\\N");

            return traduzido;

        } catch (Exception e) {
            log.error("Erro na comunicação com a API do Google Translate: {}", e.getMessage());
            return textoOriginal;
        }
    }
}
