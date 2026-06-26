package org.traducao.projeto.traducao.infrastructure.dtos;

import java.util.List;

public class RecordsMistral {
    public record Mensagem(String role, String content) {}
    public record ChatRequest(String model, List<Mensagem> messages, double temperature, int max_tokens) {}
    public record Choice(Mensagem message) {}
    public record RespostaLlm(List<Choice> choices) {}
    public record ModeloDisponivel(String id) {}
    public record ListaModelos(List<ModeloDisponivel> data) {}
}
