package org.traducao.projeto.curatags;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.traducao.projeto.traducao.domain.legenda.DocumentoLegenda;
import org.traducao.projeto.traducao.domain.legenda.EventoLegenda;
import org.traducao.projeto.traducao.infrastructure.contexto.GerenciadorContexto;
import org.traducao.projeto.traducao.infrastructure.legenda.EscritorLegendaAss;
import org.traducao.projeto.traducao.infrastructure.legenda.LeitorLegendaAss;
import org.traducao.projeto.traducao.presentation.ui.AnsiCores;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class CuraTagsUseCase {

    private static final Logger log = LoggerFactory.getLogger(CuraTagsUseCase.class);

    private final LeitorLegendaAss leitor;
    private final EscritorLegendaAss escritor;
    private final SanitizadorTagsService sanitizador;
    private final CorretorTraducaoLlmService corretorLlm;
    private final GerenciadorContexto gerenciadorContexto;

    public CuraTagsUseCase(
        LeitorLegendaAss leitor,
        EscritorLegendaAss escritor,
        SanitizadorTagsService sanitizador,
        CorretorTraducaoLlmService corretorLlm,
        GerenciadorContexto gerenciadorContexto
    ) {
        this.leitor = leitor;
        this.escritor = escritor;
        this.sanitizador = sanitizador;
        this.corretorLlm = corretorLlm;
        this.gerenciadorContexto = gerenciadorContexto;
    }

    public ResultadoCuraTags curarPasta(Path pastaBase, String contextoId) {
        return curarPasta(pastaBase, pastaBase, contextoId);
    }

    public ResultadoCuraTags curarPasta(Path pastaOriginal, Path pastaTraduzida, String contextoId) {
        if (!Files.isDirectory(pastaOriginal) || !Files.isDirectory(pastaTraduzida)) {
            String msg = "Pastas não encontradas — esperava " + pastaOriginal + " e " + pastaTraduzida;
            System.out.println(AnsiCores.YELLOW + msg + AnsiCores.RESET);
            return new ResultadoCuraTags(0, 0, 0, 0, 1, List.of(msg));
        }

        boolean llmHabilitado = aplicarContextoLlm(contextoId);

        System.out.println(AnsiCores.CYAN + "\n=== Iniciando Cura de Tags de Legendas ===" + AnsiCores.RESET);
        System.out.println("Pasta original (en): " + pastaOriginal);
        System.out.println("Pasta traduzida (pt-br): " + pastaTraduzida);
        if (llmHabilitado) {
            System.out.println(AnsiCores.CYAN + "Correção de tradução via LLM ativa (contexto: "
                + gerenciadorContexto.obterNomeContextoAtivo() + ")" + AnsiCores.RESET);
        }

        int[] curados = {0};
        int[] corrigidosLlm = {0};
        int[] semAlteracao = {0};
        int[] semPar = {0};
        List<String> erros = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(pastaOriginal)) {
            List<Path> originais = stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".ass"))
                    .toList();

            for (Path arqOriginal : originais) {
                curarArquivo(arqOriginal, pastaTraduzida, llmHabilitado, curados, corrigidosLlm, semAlteracao, semPar, erros);
            }
        } catch (IOException e) {
            log.error("Erro ao percorrer pasta original de legendas: {}", pastaOriginal, e);
            erros.add("Erro ao percorrer pasta original: " + e.getMessage());
        }

        ResultadoCuraTags resultado = new ResultadoCuraTags(
            curados[0], corrigidosLlm[0], semAlteracao[0], semPar[0], erros.size(), erros);
        if (erros.isEmpty()) {
            System.out.println(AnsiCores.GREEN + "\n✓ Cura de legendas concluída: " + curados[0]
                + " curado(s), " + corrigidosLlm[0] + " corrigido(s) via LLM, "
                + semAlteracao[0] + " já perfeito(s)." + AnsiCores.RESET);
        } else {
            System.out.println(AnsiCores.RED + "\n⚠ Cura de legendas concluída com " + erros.size()
                + " erro(s): " + curados[0] + " curado(s), " + corrigidosLlm[0] + " corrigido(s) via LLM, "
                + semAlteracao[0] + " já perfeito(s)." + AnsiCores.RESET);
        }
        log.info("Cura de tags finalizada em {}: {} curados, {} corrigidos via LLM, {} sem alteração, {} sem par traduzido, {} erro(s)",
            pastaOriginal.getFileName(), curados[0], corrigidosLlm[0], semAlteracao[0], semPar[0], erros.size());
        return resultado;
    }

    /**
     * Define o contexto ativo (lore/system prompt) usado pelo MistralPort quando
     * a correção via LLM está habilitada. Sem contextoId, a cura permanece
     * 100% estrutural/regex (sem chamadas ao LLM).
     */
    private boolean aplicarContextoLlm(String contextoId) {
        if (contextoId == null || contextoId.isBlank()) {
            return false;
        }
        if (!gerenciadorContexto.existeContexto(contextoId)) {
            System.out.println(AnsiCores.YELLOW + "Contexto desconhecido \"" + contextoId
                + "\" — cura seguirá apenas estrutural (sem LLM)." + AnsiCores.RESET);
            return false;
        }
        gerenciadorContexto.definirContextoAtivo(contextoId);
        return true;
    }

    private void curarArquivo(
        Path arqOriginal,
        Path pastaTraduzida,
        boolean llmHabilitado,
        int[] curados,
        int[] corrigidosLlm,
        int[] semAlteracao,
        int[] semPar,
        List<String> erros
    ) {
        String nomeOriginal = arqOriginal.getFileName().toString();
        String nomeBase = nomeOriginal.substring(0, nomeOriginal.lastIndexOf("."));
        Path arqTraduzido = pastaTraduzida.resolve(nomeBase + "_PT-BR.ass");

        if (!Files.exists(arqTraduzido)) {
            arqTraduzido = pastaTraduzida.resolve(nomeBase + "_PTBR.ass");
        }

        if (!Files.exists(arqTraduzido)) {
            semPar[0]++;
            return;
        }

        try {
            DocumentoLegenda docOriginal = leitor.ler(arqOriginal);
            DocumentoLegenda docTraduzido = leitor.ler(arqTraduzido);

            if (docOriginal.eventos().size() != docTraduzido.eventos().size()) {
                // As legendas não estão alinhadas 1:1 (ex.: original foi re-extraído
                // depois da tradução). Tentar curar por posição aqui arrisca cortar
                // ou embaralhar falas sem nenhum aviso — mais seguro recusar e avisar
                // do que gravar um arquivo truncado.
                String msg = arqTraduzido.getFileName() + ": contagem de eventos não corresponde ("
                    + docOriginal.eventos().size() + " no original vs " + docTraduzido.eventos().size()
                    + " na tradução) — arquivo pulado, nenhuma alteração feita.";
                log.warn(msg);
                System.out.println(AnsiCores.YELLOW + "  [Pulado] " + msg + AnsiCores.RESET);
                erros.add(msg);
                return;
            }

            boolean houveModificacao = false;
            int linhasCuradas = 0;
            int linhasCorrigidasLlm = 0;
            List<EventoLegenda> novosEventos = new ArrayList<>(docTraduzido.eventos().size());

            for (int i = 0; i < docOriginal.eventos().size(); i++) {
                EventoLegenda evtOriginal = docOriginal.eventos().get(i);
                EventoLegenda evtTraduzido = docTraduzido.eventos().get(i);

                if (evtOriginal.isDialogo() && evtTraduzido.isDialogo()
                    && evtOriginal.temTexto() && evtTraduzido.temTexto()) {
                    String textoOriginal = evtOriginal.texto();
                    String textoPtBrAntigo = evtTraduzido.texto();

                    String textoCurado = sanitizador.curarTags(textoOriginal, textoPtBrAntigo);
                    boolean corrigidoPorLlm = false;

                    if (llmHabilitado) {
                        Optional<String> corrigidoLlm = corretorLlm.corrigirSeNecessario(textoOriginal, textoCurado);
                        if (corrigidoLlm.isPresent()) {
                            // Passagem estrutural final: garante que a retradução do LLM
                            // não perdeu/alucinou as tags de formatação do original.
                            textoCurado = sanitizador.curarTags(textoOriginal, corrigidoLlm.get());
                            corrigidoPorLlm = true;
                        }
                    }

                    if (!textoPtBrAntigo.equals(textoCurado)) {
                        novosEventos.add(evtTraduzido.comTexto(textoCurado));
                        houveModificacao = true;
                        if (corrigidoPorLlm) {
                            linhasCorrigidasLlm++;
                        } else {
                            linhasCuradas++;
                        }
                    } else {
                        novosEventos.add(evtTraduzido);
                    }
                } else {
                    novosEventos.add(evtTraduzido);
                }
            }

            if (houveModificacao) {
                DocumentoLegenda documentoCurado = new DocumentoLegenda(
                    docTraduzido.cabecalho(),
                    novosEventos,
                    docTraduzido.quebraDeLinha(),
                    docTraduzido.comBom()
                );
                escritor.escrever(arqTraduzido, documentoCurado);
                curados[0]++;
                corrigidosLlm[0] += linhasCorrigidasLlm;
                System.out.println(AnsiCores.GREEN + "  [Curado] " + arqTraduzido.getFileName() + " ("
                    + linhasCuradas + " tags restauradas, " + linhasCorrigidasLlm + " corrigidas via LLM)" + AnsiCores.RESET);
            } else {
                semAlteracao[0]++;
                System.out.println(AnsiCores.DIM + "  [OK]     " + arqTraduzido.getFileName() + " (Tags perfeitas)" + AnsiCores.RESET);
            }

        } catch (Exception e) {
            String msg = "Falha ao curar " + arqTraduzido.getFileName() + ": " + e.getMessage();
            log.error(msg, e);
            System.out.println(AnsiCores.RED + "  [Erro] " + msg + AnsiCores.RESET);
            erros.add(msg);
        }
    }
}
