package org.traducao.projeto.curatags;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.traducao.projeto.traducao.domain.legenda.DocumentoLegenda;
import org.traducao.projeto.traducao.domain.legenda.EventoLegenda;
import org.traducao.projeto.traducao.infrastructure.legenda.EscritorLegendaAss;
import org.traducao.projeto.traducao.infrastructure.legenda.LeitorLegendaAss;
import org.traducao.projeto.traducao.presentation.ui.AnsiCores;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
public class CuraTagsUseCase {

    private static final Logger log = LoggerFactory.getLogger(CuraTagsUseCase.class);

    private final LeitorLegendaAss leitor;
    private final EscritorLegendaAss escritor;
    private final SanitizadorTagsService sanitizador;

    public CuraTagsUseCase(LeitorLegendaAss leitor, EscritorLegendaAss escritor, SanitizadorTagsService sanitizador) {
        this.leitor = leitor;
        this.escritor = escritor;
        this.sanitizador = sanitizador;
    }

    public ResultadoCuraTags curarPasta(Path pastaOriginal) {
        Path pastaTraduzida = pastaOriginal.resolve("traducao_ptbr");

        if (!Files.exists(pastaOriginal) || !Files.exists(pastaTraduzida)) {
            String msg = "Pastas não encontradas — esperava " + pastaOriginal + " e " + pastaTraduzida;
            System.out.println(AnsiCores.YELLOW + msg + AnsiCores.RESET);
            return new ResultadoCuraTags(0, 0, 0, 1, List.of(msg));
        }

        System.out.println(AnsiCores.CYAN + "\n=== Iniciando Cura de Tags de Legendas ===" + AnsiCores.RESET);
        System.out.println("Diretório: " + pastaOriginal.getFileName());

        int[] curados = {0};
        int[] semAlteracao = {0};
        int[] semPar = {0};
        List<String> erros = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(pastaOriginal)) {
            List<Path> originais = stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".ass"))
                    .toList();

            for (Path arqOriginal : originais) {
                curarArquivo(arqOriginal, pastaTraduzida, curados, semAlteracao, semPar, erros);
            }
        } catch (IOException e) {
            log.error("Erro ao percorrer pasta original de legendas: {}", pastaOriginal, e);
            erros.add("Erro ao percorrer pasta original: " + e.getMessage());
        }

        ResultadoCuraTags resultado = new ResultadoCuraTags(curados[0], semAlteracao[0], semPar[0], erros.size(), erros);
        if (erros.isEmpty()) {
            System.out.println(AnsiCores.GREEN + "\n✓ Cura de legendas concluída: " + curados[0]
                + " curado(s), " + semAlteracao[0] + " já perfeito(s)." + AnsiCores.RESET);
        } else {
            System.out.println(AnsiCores.RED + "\n⚠ Cura de legendas concluída com " + erros.size()
                + " erro(s): " + curados[0] + " curado(s), " + semAlteracao[0] + " já perfeito(s)." + AnsiCores.RESET);
        }
        log.info("Cura de tags finalizada em {}: {} curados, {} sem alteração, {} sem par traduzido, {} erro(s)",
            pastaOriginal.getFileName(), curados[0], semAlteracao[0], semPar[0], erros.size());
        return resultado;
    }

    private void curarArquivo(
        Path arqOriginal,
        Path pastaTraduzida,
        int[] curados,
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
            List<EventoLegenda> novosEventos = new ArrayList<>(docTraduzido.eventos().size());

            for (int i = 0; i < docOriginal.eventos().size(); i++) {
                EventoLegenda evtOriginal = docOriginal.eventos().get(i);
                EventoLegenda evtTraduzido = docTraduzido.eventos().get(i);

                if (evtOriginal.isDialogo() && evtTraduzido.isDialogo()
                    && evtOriginal.temTexto() && evtTraduzido.temTexto()) {
                    String textoOriginal = evtOriginal.texto();
                    String textoPtBrAntigo = evtTraduzido.texto();

                    String textoCurado = sanitizador.curarTags(textoOriginal, textoPtBrAntigo);

                    if (!textoPtBrAntigo.equals(textoCurado)) {
                        novosEventos.add(evtTraduzido.comTexto(textoCurado));
                        houveModificacao = true;
                        linhasCuradas++;
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
                System.out.println(AnsiCores.GREEN + "  [Curado] " + arqTraduzido.getFileName() + " (" + linhasCuradas + " tags restauradas)" + AnsiCores.RESET);
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
