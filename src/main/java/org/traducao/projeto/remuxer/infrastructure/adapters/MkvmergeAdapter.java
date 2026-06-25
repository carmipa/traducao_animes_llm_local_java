package org.traducao.projeto.remuxer.infrastructure.adapters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.traducao.projeto.remuxer.domain.RemuxTarefa;
import org.traducao.projeto.remuxer.domain.RemuxerException;
import org.traducao.projeto.remuxer.infrastructure.config.RemuxerProperties;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class MkvmergeAdapter {

    private static final Logger log = LoggerFactory.getLogger(MkvmergeAdapter.class);
    private final String mkvmergePath;

    public MkvmergeAdapter(RemuxerProperties properties) {
        this.mkvmergePath = localizarMkvmerge(properties.resolverMkvmergePath());
    }

    private String localizarMkvmerge(String caminhoConfigurado) {
        if (!"mkvmerge".equals(caminhoConfigurado) && Files.exists(Path.of(caminhoConfigurado))) {
            return caminhoConfigurado;
        }

        // Tentar os caminhos padrões do Windows
        List<String> caminhosPadrao = List.of(
            "C:\\Program Files\\MKVToolNix\\mkvmerge.exe",
            "C:\\Program Files (x86)\\MKVToolNix\\mkvmerge.exe"
        );

        for (String caminho : caminhosPadrao) {
            if (Files.exists(Path.of(caminho))) {
                log.info("mkvmerge detectado no caminho padrão do Windows: {}", caminho);
                return caminho;
            }
        }
        
        log.info("mkvmerge.exe não encontrado em caminhos padrões. Assumindo que está no PATH do sistema.");
        return "mkvmerge";
    }

    public void validarInfraestrutura() {
        try {
            Process process = new ProcessBuilder(mkvmergePath, "--version")
                    .redirectErrorStream(true)
                    .start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RemuxerException("mkvmerge executou com erro (exitCode=" + exitCode + ")");
            }
            String output = new String(process.getInputStream().readAllBytes()).trim();
            log.info("mkvmerge validado com sucesso: {}", output);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RemuxerException("Falha ao validar mkvmerge no caminho: " + mkvmergePath + ". Verifique se está instalado.", e);
        }
    }

    public void executarRemux(RemuxTarefa tarefa) {
        List<String> command = List.of(
            mkvmergePath,
            "-o", tarefa.caminhoSaida().toString(),
            "--no-subtitles",
            tarefa.caminhoVideo().toString(),
            "--language", "0:por",
            "--track-name", "0:Português (Mistral)",
            "--default-track", "0:yes",
            tarefa.caminhoLegenda().toString()
        );

        log.debug("Executando comando: {}", String.join(" ", command));

        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new RemuxerException("mkvmerge falhou com exitCode " + exitCode + ": " + output);
            }
            
            if (!Files.exists(tarefa.caminhoSaida())) {
                throw new RemuxerException("mkvmerge finalizou, mas arquivo de saída não foi encontrado: " + tarefa.caminhoSaida());
            }
        } catch (IOException e) {
            throw new RemuxerException("Erro de I/O ao tentar invocar mkvmerge: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RemuxerException("Thread interrompida durante a execução do mkvmerge.", e);
        }
    }
}
