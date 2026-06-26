package org.traducao.projeto.mapaProjeto.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class GeradorMapaProjetoUseCase {

    private static final Logger log = LoggerFactory.getLogger(GeradorMapaProjetoUseCase.class);
    
    private static final Set<String> PASTAS_IGNORAR = Set.of(
        ".git", ".venv", "__pycache__", ".idea", ".cursor", ".claude", "docs", "multiplexar", 
        "legendas-traduzidas-ptbr", ".gradle", "build", "bin", "cache"
    );

    public void executar(Path pastaRaiz) {
        log.info("Iniciando Gerador de Mapa do Projeto para: {}", pastaRaiz.toAbsolutePath());

        List<String> linhas = new ArrayList<>();
        linhas.add("# MAPA ESTRUTURAL DO PROJETO - TRACKER ANIMES");
        linhas.add("Gerado em: " + pastaRaiz.getFileName().toString());
        linhas.add("Este documento serve como mapa de contexto para LLMs atualizarem a documentação oficial.");
        linhas.add("---");
        linhas.add("");

        try {
            // Lista e ordena pastas imediatas na raiz
            List<Path> pastasProjeto;
            try (Stream<Path> list = Files.list(pastaRaiz)) {
                pastasProjeto = list
                    .filter(Files::isDirectory)
                    .filter(p -> !PASTAS_IGNORAR.contains(p.getFileName().toString()))
                    .sorted()
                    .collect(Collectors.toList());
            }

            for (Path pasta : pastasProjeto) {
                String nomePasta = pasta.getFileName().toString();
                linhas.add(String.format("## 📁 Pasta: `%s/`", nomePasta));

                // Procura arquivos .java e .py recursivamente dentro da pasta
                List<Path> arquivos = encontrarArquivosFontes(pasta);
                
                if (arquivos.isEmpty()) {
                    linhas.add("*(Nenhum script Python ou Java nesta pasta)*\n");
                    continue;
                }

                for (Path arq : arquivos) {
                    String caminhoRel = pastaRaiz.toAbsolutePath().relativize(arq.toAbsolutePath()).toString().replace('\\', '/');
                    String doc = extrairComentarioTopo(arq);

                    linhas.add(String.format("### 📄 Arquivo: `%s`", caminhoRel));
                    if (doc != null && !doc.isBlank()) {
                        linhas.add("```text");
                        linhas.add(doc);
                        linhas.add("```");
                    } else {
                        linhas.add("*(Sem docstring ou cabeçalho explicativo)*");
                    }
                    linhas.add("");
                }

                linhas.add("---");
            }

            // Grava em mapa_projeto.md
            Path destino = pastaRaiz.resolve("mapa_projeto.md");
            Files.write(destino, linhas);
            log.info("Mapa estrutural do projeto salvo com sucesso em: {}", destino.toAbsolutePath());

        } catch (IOException e) {
            log.error("Erro ao gerar o mapa do projeto: {}", e.getMessage(), e);
        }
    }

    private List<Path> encontrarArquivosFontes(Path pasta) {
        try (Stream<Path> walk = Files.walk(pasta)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(p -> {
                    String nome = p.getFileName().toString().toLowerCase();
                    return nome.endsWith(".py") || nome.endsWith(".java");
                })
                .sorted()
                .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Erro ao varrer subpasta {}: {}", pasta.getFileName(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private String extrairComentarioTopo(Path arquivo) {
        String nomeLower = arquivo.getFileName().toString().toLowerCase();
        
        try {
            List<String> linhas = Files.lines(arquivo)
                .limit(40) // Analisa as primeiras 40 linhas
                .collect(Collectors.toList());

            if (nomeLower.endsWith(".py")) {
                return extrairComentarioPython(linhas);
            } else if (nomeLower.endsWith(".java")) {
                return extrairComentarioJava(linhas);
            }
        } catch (IOException e) {
            log.warn("Falha ao ler arquivo {}: {}", arquivo.getFileName(), e.getMessage());
        }
        return null;
    }

    private String extrairComentarioPython(List<String> linhas) {
        List<String> docstring = new ArrayList<>();
        boolean lendoDocstringTripla = false;

        for (String linha : linhas) {
            String linhaStrip = linha.strip();

            // Pula shebang ou encoding
            if (linhaStrip.startsWith("#!") || linhaStrip.contains("coding:")) {
                continue;
            }

            // Trata docstrings triplas
            if (linhaStrip.contains("\"\"\"") || linhaStrip.contains("'''")) {
                if (!lendoDocstringTripla) {
                    lendoDocstringTripla = true;
                    String conteudo = linhaStrip.replace("\"\"\"", "").replace("'''", "").strip();
                    if (!conteudo.isEmpty()) {
                        docstring.add(conteudo);
                    }
                } else {
                    lendoDocstringTripla = false;
                }
                continue;
            }

            if (lendoDocstringTripla) {
                docstring.add(linha.stripTrailing());
                continue;
            }

            // Trata comentários simples (#)
            if (linhaStrip.startsWith("#")) {
                docstring.add(linhaStrip.replaceAll("^#+\\s*", ""));
            } else if (linhaStrip.isEmpty() && docstring.isEmpty()) {
                continue;
            } else if (!linhaStrip.startsWith("#") && !docstring.isEmpty()) {
                // Código normal encontrado após comentários, encerra
                break;
            }
        }

        return docstring.isEmpty() ? null : String.join("\n", docstring).strip();
    }

    private String extrairComentarioJava(List<String> linhas) {
        List<String> docstring = new ArrayList<>();
        boolean lendoBloco = false;

        for (String linha : linhas) {
            String linhaStrip = linha.strip();

            // Detecta e trata Javadocs ou comentários de bloco (/* e /**)
            if (linhaStrip.contains("/*")) {
                lendoBloco = true;
                String conteudo = linhaStrip.substring(linhaStrip.indexOf("/*") + 2).replace("*", "").strip();
                if (!conteudo.isEmpty()) {
                    docstring.add(conteudo);
                }
                if (linhaStrip.contains("*/")) {
                    lendoBloco = false;
                    // Se fechou na mesma linha, limpa e tira o */
                    if (!docstring.isEmpty()) {
                        String ultima = docstring.removeLast();
                        ultima = ultima.replace("*/", "").strip();
                        if (!ultima.isEmpty()) docstring.add(ultima);
                    }
                }
                continue;
            }

            if (lendoBloco) {
                if (linhaStrip.contains("*/")) {
                    lendoBloco = false;
                    String conteudo = linhaStrip.replace("*/", "").replace("*", "").strip();
                    if (!conteudo.isEmpty()) {
                        docstring.add(conteudo);
                    }
                } else {
                    // Remove asteriscos típicos de Javadoc no início da linha
                    String limpa = linhaStrip.replaceAll("^\\*+\\s*", "");
                    docstring.add(limpa);
                }
                continue;
            }

            // Trata comentários simples (//)
            if (linhaStrip.startsWith("//")) {
                docstring.add(linhaStrip.substring(2).strip());
            } else if (linhaStrip.isEmpty() && docstring.isEmpty()) {
                continue;
            } else if (linhaStrip.startsWith("package ") || linhaStrip.startsWith("import ") || 
                       linhaStrip.contains("class ") || linhaStrip.contains("interface ") || 
                       linhaStrip.contains("record ")) {
                // Declarações de código após os comentários, encerra
                if (!docstring.isEmpty()) {
                    break;
                }
            }
        }

        return docstring.isEmpty() ? null : String.join("\n", docstring).strip();
    }
}
