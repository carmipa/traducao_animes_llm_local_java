package org.traducao.projeto.curatags;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.traducao.projeto.traducao.infrastructure.contexto.GerenciadorContexto;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CuraTagsController {

    private final CuraTagsUseCase curaTagsUseCase;
    private final GerenciadorContexto gerenciadorContexto;

    public CuraTagsController(CuraTagsUseCase curaTagsUseCase, GerenciadorContexto gerenciadorContexto) {
        this.curaTagsUseCase = curaTagsUseCase;
        this.gerenciadorContexto = gerenciadorContexto;
    }

    @PostMapping("/cura-tags")
    public ResponseEntity<Map<String, Object>> iniciarCuraTags(@RequestBody Map<String, String> payload) {
        String diretorio = payload.get("diretorio");
        if (diretorio == null || diretorio.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("erro", "Diretório do anime não informado."));
        }

        String contextoId = payload.get("contextoId");
        if (contextoId != null && !contextoId.isBlank() && !gerenciadorContexto.existeContexto(contextoId)) {
            return ResponseEntity.badRequest().body(Map.of(
                "erro", "Contexto de tradução desconhecido: \"" + contextoId + "\". Recarregue a página e selecione um contexto válido."));
        }

        try {
            Path pastaBase = Paths.get(diretorio);
            ResultadoCuraTags resultado = curaTagsUseCase.curarPasta(pastaBase, contextoId);

            String mensagem = String.format(
                "Cura finalizada: %d curado(s), %d corrigido(s) via LLM, %d já perfeito(s), %d sem tradução pareada, %d erro(s) de %d arquivo(s).",
                resultado.curados(), resultado.corrigidosLlm(), resultado.semAlteracao(), resultado.semPar(),
                resultado.totalErros(), resultado.totalArquivos() + resultado.semPar());

            if (resultado.teveErros()) {
                return ResponseEntity.internalServerError().body(Map.of(
                    "erro", mensagem,
                    "detalhesErros", resultado.erros()
                ));
            }
            return ResponseEntity.ok(Map.of("mensagem", mensagem));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("erro", "Falha ao curar tags: " + e.getMessage()));
        }
    }
}
