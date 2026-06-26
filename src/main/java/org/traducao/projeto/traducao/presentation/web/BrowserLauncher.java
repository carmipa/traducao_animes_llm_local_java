package org.traducao.projeto.traducao.presentation.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Listener que aguarda a inicialização completa do Spring Boot
 * e abre automaticamente o navegador padrão na URL da aplicação web
 * caso a propriedade app.modo esteja configurada como "WEB".
 */
@Component
public class BrowserLauncher {

    private static final Logger log = LoggerFactory.getLogger(BrowserLauncher.class);

    @Value("${app.modo:}")
    private String modoExecucao;

    @EventListener(ApplicationReadyEvent.class)
    public void launchBrowser() {
        if (!"WEB".equals(modoExecucao)) {
            return;
        }

        String url = "http://localhost:8080";
        
        System.out.println("\n==============================================================");
        System.out.println("   SERVIDOR WEB INICIADO COM SUCESSO!");
        System.out.println("   Acesse a interface visual em: \u001B[36m" + url + "\u001B[0m");
        System.out.println("==============================================================\n");

        abrirNavegador(url);
    }

    private void abrirNavegador(String url) {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                // Windows
                Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", "start", url});
            } else if (os.contains("mac")) {
                // Mac OS
                Runtime.getRuntime().exec(new String[]{"open", url});
            } else {
                // Linux / Unix
                Runtime.getRuntime().exec(new String[]{"xdg-open", url});
            }
            log.info("Navegador aberto automaticamente na URL: {}", url);
        } catch (IOException e) {
            log.warn("Não foi possível abrir o navegador automaticamente: {}", e.getMessage());
        }
    }
}
