package org.traducao.projeto.raspagemRevisao.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.traducao.projeto.raspagemRevisao.domain.ResultadoDeteccaoConcordancia;
import org.traducao.projeto.traducao.application.ValidadorTraducaoService;
import org.traducao.projeto.traducao.domain.exceptions.AlucinacaoDetectadaException;
import org.traducao.projeto.traducao.domain.ports.MistralPort;
import org.traducao.projeto.traducao.infrastructure.contexto.GerenciadorContexto;
import org.traducao.projeto.traducao.infrastructure.legenda.MascaradorTags;
import org.traducao.projeto.telemetria.OperacaoTelemetria;
import org.traducao.projeto.telemetria.TelemetriaService;
import org.traducao.projeto.traducao.presentation.ui.AnsiCores;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class RevisarCacheUseCase {

    private static final Logger log = LoggerFactory.getLogger(RevisarCacheUseCase.class);

    private final ObjectMapper mapper;
    private final DetectorConcordanciaService detector;
    private final MistralPort mistralPort;
    private final ValidadorTraducaoService validador;
    private final MascaradorTags mascaradorTags;
    private final GerenciadorContexto gerenciadorContexto;
    private final TelemetriaService telemetriaService;

    public RevisarCacheUseCase(
        ObjectMapper mapper,
        DetectorConcordanciaService detector,
        MistralPort mistralPort,
        ValidadorTraducaoService validador,
        MascaradorTags mascaradorTags,
        GerenciadorContexto gerenciadorContexto,
        TelemetriaService telemetriaService
    ) {
        this.mapper = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        this.detector = detector;
        this.mistralPort = mistralPort;
        this.validador = validador;
        this.mascaradorTags = mascaradorTags;
        this.gerenciadorContexto = gerenciadorContexto;
        this.telemetriaService = telemetriaService;
    }

    public int executar(Path diretorioCache) {
        return executar(diretorioCache, null);
    }

    public int executar(Path diretorioCache, String contextoId) {
        long inicioMs = System.currentTimeMillis();
        out("Iniciando revisão gramatical (concordância PT-BR) em: "
            + diretorioCache.toAbsolutePath());

        if (contextoId != null && !contextoId.isBlank()) {
            if (gerenciadorContexto.existeContexto(contextoId)) {
                gerenciadorContexto.definirContextoAtivo(contextoId);
                System.out.println(AnsiCores.CYAN + "Contexto ativo: "
                    + gerenciadorContexto.obterNomeContextoAtivo() + AnsiCores.RESET);
            } else {
                System.out.println(AnsiCores.YELLOW + "Contexto desconhecido \""
                    + contextoId + "\" — usando contexto padrão." + AnsiCores.RESET);
            }
        }

        if (!Files.exists(diretorioCache)) {
            System.out.println(AnsiCores.RED + "Erro: pasta de cache não encontrada." + AnsiCores.RESET);
            return 0;
        }

        int[] arquivosProcessados = {0};
        int[] falasRevisadas = {0};
        int[] falasSuspeitas = {0};

        try (Stream<Path> caminhos = Files.walk(diretorioCache)) {
            caminhos.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".cache.json"))
                .forEach(arquivo -> processarArquivoCache(
                    arquivo, arquivosProcessados, falasRevisadas, falasSuspeitas));
        } catch (IOException e) {
            System.out.println(AnsiCores.RED + "Erro ao varrer cache: " + e.getMessage() + AnsiCores.RESET);
            return 0;
        }

        out("Arquivos analisados: " + arquivosProcessados[0]);
        out("Falas com possível erro de concordância: " + falasSuspeitas[0]);
        out("Falas corrigidas e salvas no cache: " + falasRevisadas[0]);

        long duracaoMs = System.currentTimeMillis() - inicioMs;
        OperacaoTelemetria operacao = TelemetriaService.criarOperacao(
            "Revisão Gramatical (cache LLM)",
            diretorioCache.toAbsolutePath().toString(),
            duracaoMs,
            arquivosProcessados[0],
            falasSuspeitas[0],
            falasRevisadas[0]
        );
        String relatorio = """
            REVISÃO GRAMATICAL DO CACHE (LLM)
            =================================
            Pasta: %s
            Duração: %s
            Arquivos analisados: %d
            Falas com possível erro de concordância: %d
            Falas corrigidas e salvas: %d
            """.formatted(
            diretorioCache.toAbsolutePath(),
            formatarDuracaoMs(duracaoMs),
            arquivosProcessados[0],
            falasSuspeitas[0],
            falasRevisadas[0]
        );
        telemetriaService.finalizarOperacao(
            operacao, diretorioCache, "revisao_gramatical_cache", relatorio);
        out("Relatório salvo em: " + TelemetriaService.resolverPastaRelatorios(diretorioCache));

        return falasRevisadas[0];
    }

    private String formatarDuracaoMs(long ms) {
        long segundos = ms / 1000;
        return segundos >= 60 ? (segundos / 60) + "min " + (segundos % 60) + "s" : segundos + "s";
    }

    private void out(String mensagem) {
        System.out.println(mensagem);
        log.info(mensagem);
    }

    private void processarArquivoCache(
        Path arquivo,
        int[] totalArquivos,
        int[] totalRevisadas,
        int[] totalSuspeitas
    ) {
        totalArquivos[0]++;
        System.out.println("Analisando: " + arquivo.getFileName());

        try {
            List<Map<String, Object>> entradas = mapper.readValue(arquivo.toFile(),
                new TypeReference<List<Map<String, Object>>>() {});

            int revisadasNesteArquivo = 0;
            boolean modificado = false;

            for (Map<String, Object> entrada : entradas) {
                String original = (String) entrada.get("original");
                String traduzido = (String) entrada.get("traduzido");

                if (original == null || original.isBlank()
                    || traduzido == null || traduzido.isBlank()
                    || original.equals(traduzido)) {
                    continue;
                }

                ResultadoDeteccaoConcordancia deteccao = detector.analisar(original, traduzido);
                if (!deteccao.suspeito()) {
                    continue;
                }

                totalSuspeitas[0]++;
                System.out.println("  -> Revisando linha " + entrada.get("indice")
                    + " [" + entrada.get("estilo") + "]:");
                System.out.println("     Inglês: " + AnsiCores.YELLOW + original + AnsiCores.RESET);
                System.out.println("     PT atual: " + AnsiCores.YELLOW + traduzido + AnsiCores.RESET);
                deteccao.motivos().forEach(m ->
                    System.out.println("     " + AnsiCores.DIM + "• " + m + AnsiCores.RESET));

                Optional<String> revisadoOpt = tentarRevisar(original, traduzido, deteccao.motivos());
                if (revisadoOpt.isEmpty()) {
                    System.out.println("     " + AnsiCores.RED + "Revisão não aplicada (LLM indisponível ou resposta inválida)."
                        + AnsiCores.RESET);
                    continue;
                }

                String revisado = revisadoOpt.get();
                if (revisado.equals(traduzido)) {
                    System.out.println("     " + AnsiCores.DIM + "LLM manteve o texto original." + AnsiCores.RESET);
                    continue;
                }

                ResultadoDeteccaoConcordancia posRevisao = detector.analisar(original, revisado);
                if (posRevisao.suspeito() && posRevisao.motivos().size() >= deteccao.motivos().size()) {
                    System.out.println("     " + AnsiCores.YELLOW
                        + "Correção descartada: ainda há indícios de concordância incorreta." + AnsiCores.RESET);
                    continue;
                }

                System.out.println("     PT revisado: " + AnsiCores.GREEN + revisado + AnsiCores.RESET);
                entrada.put("traduzido", revisado);
                revisadasNesteArquivo++;
                modificado = true;
            }

            if (modificado) {
                mapper.writeValue(arquivo.toFile(), entradas);
                totalRevisadas[0] += revisadasNesteArquivo;
                System.out.println(AnsiCores.GREEN + "  [OK] " + revisadasNesteArquivo
                    + " fala(s) revisada(s) e salva(s)." + AnsiCores.RESET + "\n");
            } else {
                System.out.println("  -> Nenhuma correção de concordância aplicada neste arquivo.\n");
            }
        } catch (IOException e) {
            System.out.println(AnsiCores.RED + "  -> Erro ao ler/escrever cache: "
                + e.getMessage() + AnsiCores.RESET + "\n");
        }
    }

    private Optional<String> tentarRevisar(
        String original, String traduzido, List<String> motivos
    ) {
        MascaradorTags.Mascarado mascOriginal = mascaradorTags.mascarar(original);
        MascaradorTags.Mascarado mascTraduzido = mascaradorTags.mascarar(traduzido);

        Optional<String> resposta = mistralPort.revisarConcordancia(
            mascOriginal.texto(),
            mascTraduzido.texto(),
            motivos
        );

        if (resposta.isEmpty()) {
            return Optional.empty();
        }

        try {
            String desmascarado = mascaradorTags.desmascarar(resposta.get(), mascTraduzido.tags());
            validador.validarFala(desmascarado);
            return Optional.of(desmascarado);
        } catch (AlucinacaoDetectadaException e) {
            log.warn("Revisão descartada por validação: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
