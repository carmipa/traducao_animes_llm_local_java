package org.traducao.projeto.traducao.presentation.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/dialogo")
public class DialogoArquivoController {

    private static final Logger log = LoggerFactory.getLogger(DialogoArquivoController.class);

    @GetMapping("/selecionar-pasta")
    public ResponseEntity<Map<String, String>> selecionarPasta() {
        String script = "Add-Type -AssemblyName System.Windows.Forms; " +
                        "$f = New-Object System.Windows.Forms.FolderBrowserDialog; " +
                        "$f.Description = 'Selecione a pasta desejada'; " +
                        "if ($f.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) { Write-Output $f.SelectedPath }";

        String caminho = executarScriptPowerShell(script);
        if (caminho != null && !caminho.isBlank()) {
            return ResponseEntity.ok(Map.of("caminho", caminho));
        }
        return ResponseEntity.ok(Map.of("caminho", ""));
    }

    @GetMapping("/selecionar-arquivo")
    public ResponseEntity<Map<String, String>> selecionarArquivo(@RequestParam(required = false, defaultValue = "*.*") String filtro) {
        String script = "Add-Type -AssemblyName System.Windows.Forms; " +
                        "$f = New-Object System.Windows.Forms.OpenFileDialog; " +
                        "$f.Title = 'Selecione o arquivo desejado'; " +
                        "if ($f.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) { Write-Output $f.FileName }";

        String caminho = executarScriptPowerShell(script);
        if (caminho != null && !caminho.isBlank()) {
            return ResponseEntity.ok(Map.of("caminho", caminho));
        }
        return ResponseEntity.ok(Map.of("caminho", ""));
    }

    private String executarScriptPowerShell(String script) {
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", script);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String linha = reader.readLine();
                process.waitFor();
                return linha != null ? linha.trim() : null;
            }
        } catch (Exception e) {
            log.error("Erro ao executar seletor nativo do Windows via PowerShell", e);
            return null;
        }
    }
}
