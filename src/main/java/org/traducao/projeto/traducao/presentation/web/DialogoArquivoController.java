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
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/dialogo")
public class DialogoArquivoController {

    private static final Logger log = LoggerFactory.getLogger(DialogoArquivoController.class);

    @GetMapping("/selecionar-pasta")
    public ResponseEntity<Map<String, String>> selecionarPasta() {
        log.info("Solicitado seletor nativo de pasta no Windows...");
        // Usa OpenFileDialog (UI moderna do Explorer) em vez de FolderBrowserDialog/Shell.Application,
        // cuja janela de seleção de pasta ainda usa a interface antiga (estilo Windows 95).
        String script = "Add-Type -AssemblyName System.Windows.Forms; " +
                        criarScriptOwnerTopMost() +
                        "try { " +
                        "  $f = New-Object System.Windows.Forms.OpenFileDialog; " +
                        "  $f.Title = 'Selecione a pasta desejada'; " +
                        "  $f.CheckFileExists = $false; " +
                        "  $f.CheckPathExists = $true; " +
                        "  $f.ValidateNames = $false; " +
                        "  $f.FileName = 'Selecione esta pasta'; " +
                        "  if ($f.ShowDialog($owner) -eq [System.Windows.Forms.DialogResult]::OK) { Write-Output (Split-Path $f.FileName -Parent) }; " +
                        "} finally { " +
                        "  if ($null -ne $f) { $f.Dispose(); } " +
                        "  if ($null -ne $owner) { $owner.Close(); $owner.Dispose(); } " +
                        "}";

        String caminho = executarScriptPowerShell(script);
        log.info("Caminho de pasta selecionado: {}", caminho);
        if (caminho != null && !caminho.isBlank()) {
            return ResponseEntity.ok(Map.of("caminho", caminho));
        }
        return ResponseEntity.ok(Map.of("caminho", ""));
    }

    @GetMapping("/selecionar-arquivo")
    public ResponseEntity<Map<String, String>> selecionarArquivo(@RequestParam(required = false, defaultValue = "*.*") String filtro) {
        log.info("Solicitado seletor nativo de arquivo no Windows...");
        String script = "Add-Type -AssemblyName System.Windows.Forms; " +
                        criarScriptOwnerTopMost() +
                        "try { " +
                        "  $f = New-Object System.Windows.Forms.OpenFileDialog; " +
                        "  $f.Title = 'Selecione o arquivo desejado'; " +
                        "  if ($f.ShowDialog($owner) -eq [System.Windows.Forms.DialogResult]::OK) { Write-Output $f.FileName }; " +
                        "} finally { " +
                        "  if ($null -ne $f) { $f.Dispose(); } " +
                        "  if ($null -ne $owner) { $owner.Close(); $owner.Dispose(); } " +
                        "}";

        String caminho = executarScriptPowerShell(script);
        log.info("Caminho de arquivo selecionado: {}", caminho);
        if (caminho != null && !caminho.isBlank()) {
            return ResponseEntity.ok(Map.of("caminho", caminho));
        }
        return ResponseEntity.ok(Map.of("caminho", ""));
    }

    /**
     * Cria um formulário invisível e TopMost para servir de "owner" do diálogo nativo.
     * Sem isso, o Windows costuma abrir o diálogo atrás da janela do navegador (sem foco),
     * dando a impressão de que nada aconteceu enquanto o backend fica esperando o usuário
     * interagir com uma janela que ele nunca vê.
     */
    private String criarScriptOwnerTopMost() {
        return "Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; " +
               "public class NativeWindowTools { " +
               "[DllImport(\"user32.dll\")] public static extern bool SetForegroundWindow(IntPtr hWnd); " +
               "[DllImport(\"user32.dll\")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow); " +
               "}'; " +
               "$owner = New-Object System.Windows.Forms.Form; " +
               "$owner.TopMost = $true; " +
               "$owner.ShowInTaskbar = $false; " +
               "$owner.StartPosition = 'CenterScreen'; " +
               "$owner.FormBorderStyle = 'FixedToolWindow'; " +
               "$owner.Opacity = 0.01; " +
               "$owner.Width = 1; $owner.Height = 1; " +
               "$owner.Show(); " +
               "$owner.WindowState = 'Normal'; " +
               "$owner.BringToFront(); " +
               "$owner.Activate(); " +
               "[System.Windows.Forms.SendKeys]::SendWait('%'); " +
               "Start-Sleep -Milliseconds 50; " +
               "[NativeWindowTools]::ShowWindow($owner.Handle, 5) | Out-Null; " +
               "[NativeWindowTools]::SetForegroundWindow($owner.Handle) | Out-Null; " +
               "[System.Windows.Forms.Application]::DoEvents(); " +
               "Start-Sleep -Milliseconds 150; ";
    }

    private String executarScriptPowerShell(String script) {
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-STA", "-Command", script);
            Process process = pb.start();

            StringBuilder erro = new StringBuilder();
            Thread leitorErro = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String linha;
                    while ((linha = reader.readLine()) != null) {
                        erro.append(linha).append(System.lineSeparator());
                    }
                } catch (Exception e) {
                    log.warn("Erro ao ler stderr do seletor nativo", e);
                }
            });
            leitorErro.setDaemon(true);
            leitorErro.start();

            String linha;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                linha = reader.readLine();
            }

            boolean finalizouATempo = process.waitFor(3, TimeUnit.MINUTES);
            if (!finalizouATempo) {
                log.warn("Seletor nativo do Windows nao respondeu a tempo, finalizando processo...");
                process.destroyForcibly();
                return null;
            }
            if (erro.length() > 0) {
                log.warn("Saida de erro do seletor nativo do Windows: {}", erro);
            }
            return linha != null ? linha.trim() : null;
        } catch (Exception e) {
            log.error("Erro ao executar seletor nativo do Windows via PowerShell", e);
            return null;
        }
    }
}
