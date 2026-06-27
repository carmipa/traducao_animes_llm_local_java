package org.traducao.projeto.traducao.infrastructure.contexto;

import org.springframework.stereotype.Component;
import org.traducao.projeto.traducao.domain.ports.ProvedorContexto;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class GerenciadorContexto {

    private final List<ProvedorContexto> provedores;
    private ProvedorContexto provedorAtivo;

    public GerenciadorContexto(List<ProvedorContexto> provedores) {
        this.provedores = provedores.stream()
                .sorted(Comparator.comparing(ProvedorContexto::getNomeExibicao, String.CASE_INSENSITIVE_ORDER))
                .toList();
        validarIdsUnicos(this.provedores);
        this.provedorAtivo = provedorPadrao();
    }

    public List<ProvedorContexto> getProvedores() {
        return provedores;
    }

    public ProvedorContexto definirContextoAtivo(String id) {
        if (id == null || id.isBlank()) {
            return this.provedorAtivo;
        }
        this.provedorAtivo = provedores.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElse(provedorPadrao());
        return this.provedorAtivo;
    }

    public String obterPromptAtivo() {
        if (this.provedorAtivo == null) {
            return "Voce e um tradutor especialista. Traduza fielmente.";
        }
        return this.provedorAtivo.obterPromptSistema();
    }

    public String obterNomeContextoAtivo() {
        return this.provedorAtivo != null ? this.provedorAtivo.getNomeExibicao() : "Padrao";
    }

    private ProvedorContexto provedorPadrao() {
        return provedores.stream()
                .filter(p -> "danmachi".equals(p.getId()))
                .findFirst()
                .orElse(provedores.isEmpty() ? null : provedores.get(0));
    }

    private void validarIdsUnicos(List<ProvedorContexto> provedores) {
        Map<String, Long> contagemPorId = provedores.stream()
                .map(ProvedorContexto::getId)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        List<String> duplicados = contagemPorId.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

        if (!duplicados.isEmpty()) {
            throw new IllegalStateException("IDs de contexto duplicados: " + duplicados);
        }
    }
}
