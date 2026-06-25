package org.traducao.animes.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CacheTraducaoServiceTest {

    private final CacheTraducaoService service = new CacheTraducaoService(new ObjectMapper());

    @Test
    void retornaMapaVazioQuandoArquivoNaoExiste(@TempDir Path tempDir) {
        Map<String, String> resultado = service.carregar(tempDir.resolve("nao-existe.json"));
        assertThat(resultado).isEmpty();
    }

    @Test
    void salvaEDepoisCarregaAsMesmasEntradas(@TempDir Path tempDir) {
        Path arquivo = tempDir.resolve("sub/cache.json");
        List<EntradaCache> entradas = List.of(
            new EntradaCache(0, "Dialogue", "Hello!", "Olá!", "en", "pt-br"),
            new EntradaCache(1, "Dialogue", "Goodbye.", "Adeus.", "en", "pt-br")
        );

        service.salvar(arquivo, entradas);
        assertThat(Files.exists(arquivo)).isTrue();

        Map<String, String> carregado = service.carregar(arquivo);
        assertThat(carregado).containsEntry("Hello!", "Olá!").containsEntry("Goodbye.", "Adeus.");
    }

    @Test
    void ignoraEntradasComTraducaoVaziaAoCarregar(@TempDir Path tempDir) {
        Path arquivo = tempDir.resolve("cache.json");
        List<EntradaCache> entradas = List.of(
            new EntradaCache(0, "Dialogue", "Hello!", "", "en", "pt-br"),
            new EntradaCache(1, "Dialogue", "Goodbye.", "Adeus.", "en", "pt-br")
        );
        service.salvar(arquivo, entradas);

        Map<String, String> carregado = service.carregar(arquivo);
        assertThat(carregado).doesNotContainKey("Hello!");
        assertThat(carregado).containsEntry("Goodbye.", "Adeus.");
    }

    @Test
    void retornaMapaVazioQuandoJsonCorrompido(@TempDir Path tempDir) throws IOException {
        Path arquivo = tempDir.resolve("corrompido.json");
        Files.writeString(arquivo, "{ isso nao e um json valido");

        Map<String, String> resultado = service.carregar(arquivo);
        assertThat(resultado).isEmpty();
    }
}
