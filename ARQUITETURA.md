# Arquitetura do Tradutor Local (Java 25 + Spring Boot)

> 🏠 [Voltar ao README](README.md) · 📜 [Licença](LICENSE)

## Para as IAs que lerem este repositório no futuro

**Leia primeiro [CEREBRO_IA.md](CEREBRO_IA.md)** — memória viva com estado atual do pipeline, telemetria, revisão de legendas e checklist.  
Atualize o CÉREBRO ao final de cada tarefa significativa; use este `ARQUITETURA.md` para decisões profundas e armadilhas.

Este projeto foi refatorado do Python (pipeline do Gundam Origin usando Mistral/Nemo) para Java 25 visando **baixo overhead de I/O** com Virtual Threads onde faz sentido.

**Leia este arquivo antes de alterar o fluxo de pastas, cache ou chamadas ao LLM** — várias decisões aqui existem por limitações reais do LM Studio (GPU única, fila serial de inferência).

---

## Como executar (humano ou IA orientando o usuário)

```bash
.\gradlew.bat bootRun --console=plain
```

O flag `--console=plain` mantém o terminal interativo (necessário para digitar o caminho da pasta).
O `build.gradle` define `standardInput = System.in` no `bootRun` para o Gradle repassar o teclado ao Java.

Ou com pasta fixa (pula o prompt interativo):

```bash
.\gradlew.bat bootRun --args="--tradutor.diretorio-entrada=D:\caminho\legendas_eng"
```

Ou use o atalho `run.bat` na raiz do projeto.

`tradutor.diretorio-entrada` vem **vazio** no `application.yml` por padrão. Nesse caso, o **`Application.main`** pede a pasta no console **antes** do Spring Boot subir (sem banner do Boot no meio do prompt).

### Prompt interativo de pastas (console only)

- Classe: `ConsoleEntrada` (`presentation/ui/`) — estatica, sem Spring.
- Chamada em `Application.main` → `prepararArgumentosComPastasDoConsole()` → vira `--tradutor.diretorio-entrada=...`.
- Leitura: `BufferedReader(System.in)` + `System.out.print` + `flush()` — igual `input()` no Python.
- Cores via `AnsiCores` (verde no prompt, azul/amarelo no banner).
- Textos em ASCII simples (evita caracteres quebrados no console Windows).
- **Gradle:** `standardInput = System.in` no `build.gradle` + `--console=plain` no bootRun.
- Depois do input, o Spring sobe e `TradutorCLI` so traduz (pastas ja em `TradutorProperties` / `PastasExecucao`).

| Campo no prompt | Obrigatório | Se vazio |
|-----------------|-------------|----------|
| Pasta de ENTRADA | Sim | Programa encerra |
| Pasta de SAÍDA | Não | `TradutorProperties.resolverDiretorioSaida()` — troca `eng` por `pt-br` no nome da pasta de entrada, ou acrescenta `_pt-br` |
| Pasta de CACHE | Não | `<pasta de saída>/cache` |

Se o prompt nao esperar digitacao: use `run.bat` ou `bootRun --console=plain` no mesmo terminal PowerShell.

---

## Estrutura de pacotes (hexagonal light)

```
org.traducao.projeto.traducao
├── Application.java          # @Import explícito (ver JDK 25 abaixo)
├── presentation/
│   ├── TradutorCLI.java      # CommandLineRunner — entrada do programa
│   └── ui/
│       ├── ConsoleUILogger.java
│       ├── ConsoleEntrada.java      # input() estilo Python — chamado no main
│       ├── AnsiCores.java
│       └── PastasExecucao.java
├── application/
│   ├── ProcessarArquivoUseCase.java   # orquestra 1 arquivo .ass
│   ├── ProcessarEpisodioUseCase.java  # traduz lotes de 1 episódio
│   └── ValidadorTraducaoService.java
├── domain/                   # Lote, TraducaoLote, exceções, ports
└── infrastructure/
    ├── adapters/MistralClientAdapter.java  # RestClient → LM Studio
    ├── cache/CacheTraducaoService.java
    ├── config/TradutorProperties.java, LlmProperties.java, RestClientConfig.java
    └── legenda/ LeitorLegendaAss, EscritorLegendaAss, MascaradorTags
```

Beans **não** são descobertos por `@ComponentScan` automático — ver seção JDK 25.

---

## Modos de execução (`app.modo`) e os pacotes `legendasExtracao` / `remuxer`

Além de `org.traducao.projeto.traducao` (tradução), o projeto tem dois módulos irmãos, cada um com seu próprio `CommandLineRunner` ativado por `@ConditionalOnProperty(name = "app.modo", havingValue = "...")`:

| Modo | CLI/Bean | Pacote | Função |
|---|---|---|---|
| `WEB` (padrão **desde jun/2026** quando nenhum argumento de CLI é passado) | `ApiController` + SPA estática | `traducao/presentation/web` | Sobe a interface web (ver seção [Interface Web](#interface-web) abaixo) — substitui os prompts de console por formulários e dispara cada pipeline em background. |
| `TRADUZIR` (`matchIfMissing = true`, só roda se `app.modo` não for definido) | `TradutorCLI` | `animes` | Traduz `.ass`/`.ssa` via LLM local (fluxo já documentado acima) |
| `EXTRAIR` | `ExtratorCLI` | `legendasExtracao` | Extrai faixas de legenda embutidas em `.mkv` (ASS/PGS/SRT) via MKVToolNix (`mkvmerge --identify` + `mkvextract`) |
| `REMUXAR` | `RemuxerCLI` | `remuxer` | Remultiplexa um `.mkv` original com uma legenda `.ass`/`.srt` traduzida via `mkvmerge` |
| `CORRIGIR_CACHE` | `CorretorCacheCLI` | `traducaoCorrige` | Limpa falas do cache cujo `traduzido` ficou igual ao `original` (fallback de falha do LLM) |
| `RASPAGEM_CORRECAO` | `CorretorRaspagemCLI` | `raspagemCorrecao` | Preenche as falas limpas acima raspando `translate.googleapis.com` (sem chave de API) |
| `MAPEAR` | `MapaProjetoCLI` | `mapaProjeto` | Gera `relatorio_diretorio_vps.txt` e `mapa_projeto.md` (documentação/taxonomia automática do repositório) |

> [!IMPORTANT]
> `WEB` não é "mais um modo" no mesmo pé que os outros — é o **caminho de inicialização padrão** (`Application.prepararArgumentosComPastasDoConsole`): quando o programa é iniciado **sem** `--tradutor.diretorio-entrada=...`, o `main()` injeta `--app.modo=WEB --server.port=8080 --spring.main.web-application-type=servlet` antes de subir o Spring. Passar `--tradutor.diretorio-entrada=<pasta>` continua pulando isso e caindo no modo CLI puro (`spring.main.web-application-type: none`, do `application.yml`) — nenhum Tomcat é levantado nesse caso.

`ConsoleEntrada.solicitarPastas()` pergunta o modo (`[1]/[2]/[3]`) antes do Spring subir e grava `--app.modo=...` nos args, igual ao fluxo de pastas. Os três modos reaproveitam as mesmas chaves `tradutor.diretorio-entrada` / `tradutor.diretorio-saida` (ver `Application.ARG_ENTRADA`/`ARG_SAIDA`) — não existem propriedades `extrator.diretorio-*` ou `remuxer.diretorio-*` separadas.

### `legendasExtracao` (extração)

```
org.traducao.projeto.legendasExtracao
├── domain/        FormatoLegenda (ASS/PGS/SRT), FaixaLegenda, RelatorioExtracao, ExtratorException
├── application/
│   ├── ExtrairLegendaUseCase.java     # varre .mkv, identifica faixas, extrai a melhor
│   └── strategy/  ExtratorStrategy + Ass/Pgs/Srt — Strategy Pattern para "qual faixa é a certa"
├── infrastructure/
│   ├── adapters/MkvToolNixAdapter.java   # ProcessBuilder → mkvmerge --identify / mkvextract
│   └── config/ExtratorProperties.java
└── presentation/  ExtratorCLI.java, ui/ConsoleExtratorLogger.java
```

Cada `ExtratorStrategy` decide a "melhor faixa" dentro das candidatas que batem com o formato (por palavra-chave no nome da faixa, idioma, ou flag `default_track` — ver implementações). Saída: `<pasta-de-entrada>/legendas_extraidas_<formato>/<nome>_Track<id>.<ext>`.

### `remuxer` (remultiplexação)

```
org.traducao.projeto.remuxer
├── domain/        RemuxTarefa, RelatorioRemux, RemuxerException
├── application/
│   ├── MapeadorMidiaService.java   # pareia cada .mkv com a legenda PT-BR pelo nome do arquivo
│   └── RemuxarLoteUseCase.java     # orquestra validação + remux + relatório
├── infrastructure/adapters/MkvmergeAdapter.java
└── presentation/  RemuxerCLI.java, ui/ConsoleRemuxerLogger.java
```

`MapeadorMidiaService` tenta várias convenções de nome (`_PTBR.ass`, `_PTBR_ENG.ass`, `_ENG.srt`, etc. — ver `tentativas` em `construirFilaProcessamento`) para achar a legenda de cada vídeo; o vídeo é **ignorado** (não é erro) se nenhuma bater. Saída: `<pasta-de-vídeos>/mkv_final_ptbr/<nome>_PTBR.mkv`.

### Bug crítico corrigido (jun/2026): `PastasExecucao` não configurada fora do modo `TRADUZIR`

`PastasExecucao` (bean singleton com os `Path` efetivos de entrada/saída) só era preenchida em `TradutorCLI.resolverPastas()` — método que só executa quando `app.modo=TRADUZIR`. Em `EXTRAIR`/`REMUXAR`, `ExtratorCLI`/`RemuxerCLI` liam `pastasExecucao.diretorioEntrada()` **antes de qualquer `configurar()` ter rodado**, recebendo `null` e estourando `NullPointerException` assim que o use case tentava usar o caminho.

**Correção:** `ExtratorCLI` e `RemuxerCLI` agora chamam `pastasExecucao.configurar(...)` no próprio `run()`, com a `TradutorProperties` injetada (mesmas chaves `tradutor.diretorio-*`, reaproveitadas pelos três modos). Cada CLI também valida com `Files.isDirectory(...)` antes de prosseguir, em vez de deixar o use case criar silenciosamente a pasta de saída (e, por consequência, a própria pasta de entrada via `Files.createDirectories`) quando o caminho informado não existe.

**Para IAs:** se um novo modo/CLI for adicionado, **não assuma** que `PastasExecucao` já está populada — cada `CommandLineRunner` condicional ao seu `app.modo` é responsável por chamar `configurar()` antes de ler `diretorioEntrada()`/`diretorioSaida()`.

---

<a id="interface-web"></a>
## Interface Web (modo `WEB`, padrão desde jun/2026)

Toda a interação por console (prompts, banners, barra de progresso) foi substituída por uma SPA estática (`src/main/resources/static/`) servida pelo próprio Spring Boot, com um `RestController` único acionando cada pipeline em background e transmitindo o log ao vivo via Server-Sent Events (SSE).

### Estrutura de pacotes

```
org.traducao.projeto.traducao.presentation.web
├── ApiController.java       # @RestController — POST /api/{analisar,extrair,traduzir,corrigir-cache,
│                             #   corrigir-scraping,remuxar,mapa}, GET /api/{status,telemetria,logs/stream}
├── LogStreamService.java    # Registro de SseEmitters + canal "atual" da tarefa em execução +
│                             #   persistência de cada linha em logs/console-web.log
├── ConsoleRedirector.java   # Substitui System.out por um OutputStream que espelha no console
│                             #   físico E publica cada linha completa no LogStreamService
└── BrowserLauncher.java     # @EventListener(ApplicationReadyEvent) — abre o navegador padrão
                              #   automaticamente quando app.modo=WEB

src/main/resources/static/   # SPA sem framework (HTML + CSS + JS módulos ES, sem build step)
├── index.html               # Sidebar com 7 painéis (análise, extração, tradução, correção,
│                             #   remuxer, mapa, telemetria)
├── js/app.js                 # Orquestrador: navegação entre painéis, conexão SSE, escaping de HTML
└── {analise,extracao,traducao,correcao,remuxer,mapa,telemetria}/*.js   # 1 módulo por painel
```

### Endpoints da API

Cada `POST` (exceto `/api/mapa`) só valida a entrada e devolve `200` imediatamente — o trabalho real roda em segundo plano num `ExecutorService` **single-thread** compartilhado por todos os módulos (mesma justificativa dos lotes sequenciais: uma GPU só, fila serial).

| Endpoint | Pipeline acionado |
|---|---|
| `POST /api/analisar` | `AnalisarMidiaUseCase` (auditoria de mídia/sincronia de legenda) |
| `POST /api/extrair` | `ExtrairLegendaUseCase` |
| `POST /api/traduzir` | `ProcessarArquivoUseCase` por arquivo `.ass`/`.ssa` encontrado |
| `POST /api/corrigir-cache` | `LimparCacheUseCase` |
| `POST /api/corrigir-scraping` | `CorrigirComGoogleUseCase` |
| `POST /api/remuxar` | `RemuxarLoteUseCase` |
| `POST /api/mapa` | `GeradorMapaProjetoUseCase` (síncrono — devolve o markdown direto na resposta) |
| `GET /api/status` | Heartbeat simples (`{"mensagem":"online"}`) |
| `GET /api/telemetria` | `TelemetriaService.gerarResumo(...)` — ver seção Telemetria abaixo |
| `GET /api/telemetria/exportar` | Download direto de `logs/telemetria_compartilhada.json` (`Content-Disposition: attachment`) |
| `GET /api/logs/stream` | Conexão SSE consumida pelo `EventSource` do `app.js` |

### Logs em tempo real (SSE) com canal por operação

`LogStreamService` mantém um campo `canalAtual` (ex.: `"analise"`, `"traducao"`, `"remuxer"`). Cada handler do `ApiController` define esse canal como **primeira linha** dentro do `executor.submit(...)`, antes de chamar o use case — como o executor é single-thread, isso garante que toda linha impressa por aquela tarefa seja publicada sob o canal certo, mesmo que o usuário troque de aba no navegador no meio da execução. O `app.js` registra um listener `EventSource` por canal e escreve direto no painel correspondente (sem depender de "qual aba está aberta agora").

> [!CAUTION]
> **Bug corrigido (jun/2026): logs cruzando entre painéis.** Antes de existir esse canal por operação, todas as mensagens iam para um único evento SSE `"console"`, e o `app.js` decidia o painel de destino **pela aba ativa no momento em que a linha chegava** — trocar de aba durante uma tradução longa fazia logs da tradução aparecerem no painel errado.

`ConsoleRedirector` substitui `System.out` (e por consequência também captura tudo que o Logback/SLF4J escreve no console, já que o `ConsoleAppender` do Spring Boot resolve `System.out` dinamicamente) por um `OutputStream` que: (1) espelha cada byte no console físico original; (2) acumula até `\n` e publica a linha completa via `LogStreamService.publicarLog(...)`.

### Persistência (logs e telemetria sobrevivem ao navegador/restart)

| O que | Onde fica | Quem grava |
|---|---|---|
| Console da web (texto puro, sem ANSI) | `logs/console-web.log` | `LogStreamService.publicarLog(...)`, a cada linha publicada |
| Log estruturado completo (igual ao modo CLI) | `logs/tradutor.log` | Logback (já existia antes da interface web) |
| Telemetria agregada do projeto | `logs/telemetria_compartilhada.json` | `TelemetriaService.registrarMidia(...)` / `registrarTraducao(...)`, a cada registro |

> [!CAUTION]
> **Bug corrigido (jun/2026): `/api/telemetria` sempre devolvia `{}`.** `TelemetriaService` não tem getters (não é um DTO) — sem eles, o Jackson não tinha nenhuma propriedade acessível para serializar. Corrigido criando `TelemetriaResumo`/`OperacaoHistorico` (records dedicados) e um método `TelemetriaService.gerarResumo(Path diretorioCache)` que monta o DTO a partir do banco persistido em `logs/telemetria_compartilhada.json` (não do lote em memória, que `limparLote()` zera a cada nova análise).

> [!NOTE]
> Antes dessa correção, **só** `AnalisarMidiaUseCase` registrava telemetria; o pipeline de tradução nunca chamava `telemetriaService.registrarTraducao(...)`. Isso foi corrigido em `ProcessarArquivoUseCase.processar(...)`, que agora mede o tempo total do arquivo e registra `LlmTelemetria` (modelo, linhas, falas do cache, tempo) ao final de cada tradução **bem-sucedida**. Falhas parciais/abortadas ainda não geram registro de telemetria — é um gap conhecido, não um requisito coberto ainda.

### Segurança

> [!CAUTION]
> **Risco corrigido (jun/2026): servidor web sem autenticação escutando em todas as interfaces de rede.** Por padrão o Spring Boot escuta em `0.0.0.0`, não só `127.0.0.1`. Como a interface web não tem login nem CSRF, qualquer dispositivo na mesma LAN/Wi-Fi podia disparar tradução, remux, limpeza de cache e até ler a árvore do código-fonte (`/api/mapa`) sem credencial nenhuma. **Solução aplicada:** `server.address: 127.0.0.1` no `application.yml` — o servidor só aceita conexões da própria máquina. Se algum dia for necessário acessar de outro dispositivo na rede, **não basta voltar para `0.0.0.0`** sem antes adicionar autenticação (nem que seja um token compartilhado simples).

> [!CAUTION]
> **Risco corrigido (jun/2026): XSS via `innerHTML` no console e na tabela de telemetria.** `app.js` (linhas de log) e `telemetria.js` (tabela de histórico) inseriam texto vindo do backend — nomes de arquivo, mensagens de erro, texto raspado de `translate.googleapis.com` — direto em `innerHTML`, sem escapar. Um nome de arquivo ou resposta de tradução contendo `<script>`/`<img onerror=...>` seria executado no navegador. **Solução:** `app.js` escapa entidades HTML antes de aplicar as cores ANSI (`escapeHtml()`); `telemetria.js` monta as células da tabela via `textContent` em vez de template strings em `innerHTML`.

### Bug crítico corrigido (jun/2026): beans da camada web nunca eram registrados

Igual ao restante do projeto (ver seção ASM/component scan mais abaixo), `ApiController`, `LogStreamService`, `ConsoleRedirector` e `BrowserLauncher` dependem de `@Import({...})` explícito em `Application.java` — `@ComponentScan` automático não funciona neste projeto sob Java 25. Esses quatro beans **não estavam** na lista, então todo `/api/*` devolvia `404 "No static resource"` (o `DispatcherServlet` caía no resolvedor de recursos estáticos por não achar nenhum `@RequestMapping`), e o navegador nunca abria automaticamente. A interface web inteira ficava com a aparência pronta mas **nenhum botão funcionava de fato**.

**Correção:** os quatro beans web + `LimparCacheUseCase` + `CorrigirComGoogleUseCase` (dependências do `ApiController` que também faltavam) foram adicionados ao `@Import` de `Application.java`.

**Para IAs:** ao criar qualquer novo `@Component`/`@Service`/`@RestController`, **sempre** adicione a classe na lista `@Import` de `Application.java` — mesmo estando no mesmo pacote do `Application`, mesmo sem nenhum outro bean a importar diretamente. Esqueceu disso é exatamente o que causou este bug: a classe compila, o Spring sobe sem erro nenhum, e a única pista é a ausência silenciosa do comportamento esperado.

---

## Pipeline de tradução (.ass / .ssa)

Testado com 47 legendas em inglês de Mobile Suit Gundam ZZ (`.ass` Aegisub).

### 1. `TradutorCLI`

- Varre a pasta de entrada por `.ass` / `.ssa`.
- Processa arquivos **sequencialmente** entre si.
- Falha em um episódio não aborta os demais (`TraducaoParcialException` / `TradutorException` capturadas por arquivo).
- Arquivos de saída: mesmo nome com sufixo `_PT-BR` (remove `_ENG` se existir).

### 2. `LeitorLegendaAss` / `EscritorLegendaAss`

- Parsing preservando BOM, CRLF, metadados, timestamps.
- Índice do campo `Style` lido da linha `Format:` de `[Events]` (não hard-coded).

### 3. `MascaradorTags`

- Tags ASS (`{\i1}`, `{\pos(...)}`) e `\N`/`\n`/`\h` viram `[[TAG0]]`, `[[TAG1]]`…
- LLM deve devolver os mesmos marcadores; perda/duplicação/invenção → `AlucinacaoDetectadaException`.

### 4. `ProcessarArquivoUseCase`

- Deduplica falas idênticas antes de chamar o LLM.
- Ignora estilos em `tradutor.estilos-ignorados` (padrão: `Song JP`).
- Monta lotes de `tradutor.tamanho-lote` falas (padrão 20).
- Delega tradução a `ProcessarEpisodioUseCase`.
- Em falha parcial, salva traduções já obtidas no cache JSON antes de propagar `TraducaoParcialException`.

### 5. Cache JSON (`CacheTraducaoService`)

- Arquivo: `<nome>.cache.json` na pasta de cache.
- Cada entrada (`EntradaCache`): `indice`, `estilo`, `original`, `traduzido`, **`idiomaOriginal`**, **`idiomaTraduzido`**.
- Idiomas vêm de `tradutor.idioma-original` / `tradutor.idioma-traduzido` no `application.yml` (padrão `en` / `pt-br`) — **não** hard-coded no use case.
- Tradução não vazia no cache nunca é reenviada ao LLM; edição manual do JSON é respeitada na próxima execução.

### 6. `ProcessarEpisodioUseCase` — lotes **sequenciais**

**Importante para IAs:** a versão antiga disparava dezenas de lotes em paralelo com `StructuredTaskScope` + Virtual Threads. Isso **sobrecarregava o LM Studio** (GPU única): erros `application/octet-stream`, `Closed by interrupt`, programa “parando” após ~10 minutos nos logs.

**Estado atual (jun/2026):** lotes de um mesmo episódio são processados em **loop sequencial** (`for` simples). Virtual Threads ainda existem no projeto (Spring `spring.threads.virtual.enabled: true`) mas **não** são usadas para paralelizar chamadas ao LLM dentro do episódio.

Em falha de um lote:
- Lança `TraducaoParcialException` com lotes já traduzidos (`getLotesSalvos()`).
- `ProcessarArquivoUseCase` converte isso em entradas de cache parciais.

Validações por lote:
- `DivergenciaLinhasException` se o LLM devolver contagem de linhas diferente da enviada.
- `ValidadorTraducaoService` (inglês/francês residual, preâmbulos do modelo).

### 7. `MistralClientAdapter` (LM Studio / OpenAI-compatible API)

- URL: `tradutor.llm.base-url` (padrão `http://127.0.0.1:1234/v1`).
- Modelo: deve bater com `GET /v1/models` do LM Studio.
- **Retry:** até 3 tentativas com pausa de 2 s em falha HTTP, timeout ou erro de parse.
- Headers: `Content-Type` e `Accept: application/json` (evita resposta como `application/octet-stream` sem parse).
- Timeouts via `RestClientConfig`: connect 5 s, **read 180 s** (lot grandes podem demorar).

---

## Configuração (`application.yml` + `TradutorProperties` / `LlmProperties`)

| Propriedade | Padrão | Notas |
|-------------|--------|-------|
| `tradutor.diretorio-entrada` | `""` | Vazio → prompt no console |
| `tradutor.diretorio-saida` | `""` | Auto a partir da entrada |
| `tradutor.diretorio-cache` | `""` | Auto: `<saída>/cache` |
| `tradutor.tamanho-lote` | `20` | Falas por requisição LLM |
| `tradutor.estilos-ignorados` | `Song JP` | Não traduzidos |
| `tradutor.idioma-original` | `en` | Gravado no cache JSON |
| `tradutor.idioma-traduzido` | `pt-br` | Gravado no cache JSON |
| `tradutor.llm.read-timeout` | `180s` | Aumentado após travamentos |

Sobrescrever via CLI: `--tradutor.diretorio-entrada=...`, `--tradutor.idioma-original=ja`, etc.

---

## Logging, exceções e segurança

- SLF4J em todas as camadas; `ConsoleUILogger` espelha no log e coloriza o terminal.
- Log em arquivo: `logs/tradutor.log` (com `loteId` no MDC por lote).
- `spring.main.web-application-type: none` — sem Tomcat (CLI local, não serviço HTTP).
- Prompt de pastas usa `System.out` direto (não `ConsoleUILogger`) para não misturar com barra de progresso nem depender do SLF4J.

### Hierarquia de exceções relevantes

- `TradutorException` — base.
- `TraducaoParcialException` — falha no meio do episódio; carrega cache parcial.
- `DivergenciaLinhasException`, `AlucinacaoDetectadaException`, `LlmFalhaComunicacaoException`, etc.

---

## Console UI (`ConsoleUILogger`)

- Barra estilo `tqdm` via `me.tongfei:progressbar`.
- Thread-safe (`synchronized`) — usado durante tradução sequencial de lotes.
- Mensagens de prompt de pastas **ficam fora** deste logger de propósito.

---

## JDK 25 + Spring Boot 3.3 — workarounds obrigatórios

### ASM / component scan

O ASM do Spring 6.1.x não lê class file version 69 (Java 25). `@SpringBootApplication` sozinho **não registra beans** deste projeto.

**Solução:** `@Import({ TradutorCLI.class, ... })` explícito em `Application.java` + `@EnableConfigurationProperties`.

`build.gradle` → `JavaExec` (inclui `bootRun`):

```
--enable-preview
-Dspring.classformat.ignore=true
```

### Mockito / testes

`build.gradle` → `test`:

```
-Dnet.bytebuddy.experimental=true
```

Remover essas flags quando Spring/Mockito suportarem JDK 25 oficialmente.

---

## Testes (`src/test`)

JUnit 5 + Mockito + AssertJ. Cobertura principal:

- `ValidadorTraducaoService` — anti-alucinação.
- `MascaradorTags` — mascarar/desmascarar/falhas.
- `LeitorLegendaAss` — parsing real.
- `CacheTraducaoService` — resume + idiomas no JSON.
- `MistralClientAdapter` — MockRestServiceServer, retry em erro HTTP.
- `ProcessarEpisodioUseCase` — falha aborta com parcial.
- `TradutorCLITest` — falha isolada por arquivo.
- `ApplicationTest` — args com pasta ja informada.

---

## Histórico de decisões (não reverter sem motivo)

| Decisão | Motivo |
|---------|--------|
| Lotes sequenciais (não paralelos) | LM Studio trava com 20+ requisições simultâneas |
| Sem JOptionPane | Headless / IDE não exibe diálogo; usuário não via prompt |
| Prompt antes do `ConsoleUILogger` | Mensagem não se perdia atrás de logs Spring |
| `PastasExecucao` separado de `TradutorProperties` | Properties é imutável do Spring; paths vêm do console em runtime |
| Retry 3× no adapter | Falhas transitórias de rede/parse no LM Studio |
| `idioma-original` / `idioma-traduzido` no yml | Cache JSON documenta par de idiomas; suporta futuros pares além de en→pt-br |
| Episódios sequenciais entre arquivos | Uma falha não deve cancelar série inteira; GPU já é gargalo |
| Modo `WEB` como padrão sem argumentos (jun/2026) | A interface web substituiu os prompts de console como forma principal de uso; quem ainda quiser CLI pura passa `--tradutor.diretorio-entrada=...` explicitamente |
| `server.address: 127.0.0.1` fixo no `application.yml` (jun/2026) | Interface web não tem autenticação; expor em `0.0.0.0` deixaria qualquer dispositivo na LAN acionar tradução/remux/limpeza de cache sem credencial |
| Um canal SSE por operação (`analise`, `traducao`, ...) em vez de um único `"console"` (jun/2026) | Evitar log de uma operação aparecer no painel errado quando o usuário troca de aba no navegador |
| `LogStreamService`/`TelemetriaService` persistem em `logs/` a cada evento, não só ao final do lote (jun/2026) | Sobreviver a fechamento de aba e a restart do servidor; antes só existiam em memória/SSE |

---

## O que NÃO fazer (armadilhas comuns)

1. **Reativar `StructuredTaskScope` paralelo nos lotes** sem medir carga no LM Studio — causa os erros vistos em `logs/tradutor.log` (jun/2026).
2. **Hard-coded de pasta** em `application.yml` — usuário quer escolher no console.
3. **Usar só `ConsoleUILogger` para o prompt de pastas** — pode não aparecer a tempo ou competir com a progress bar.
4. **Confiar em `@ComponentScan`** sem `@Import` — beans não sobem no JDK 25. Isso vale **igualmente** para a camada web (`ApiController`, `LogStreamService`, `ConsoleRedirector`, `BrowserLauncher`) — foram esquecidos do `@Import` e deixaram a API inteira em 404 silencioso até jun/2026 (ver seção [Interface Web](#interface-web)).
5. **Assumir que Virtual Threads = paralelismo de GPU** — o gargalo é inferência local serial na prática.
6. **Assumir que `PastasExecucao` já está configurada** em `ExtratorCLI`/`RemuxerCLI` (ou em qualquer novo `CommandLineRunner` condicional a `app.modo`) — cada um precisa chamar `pastasExecucao.configurar(...)` no próprio `run()`; ela não é populada automaticamente fora do modo `TRADUZIR` (bug real corrigido em jun/2026, ver seção "Modos de execução" acima).
7. **Deixar `Files.createDirectories(pastaSaida)` rodar antes de validar que a pasta de entrada existe** — como `pastaSaida` é uma subpasta de `pastaVideos`, isso cria silenciosamente a pasta de entrada (vazia) em vez de avisar o usuário sobre um caminho digitado errado.
8. **Voltar `server.address` para `0.0.0.0`** (ou removê-lo do `application.yml`) sem antes adicionar autenticação à interface web — sem isso, qualquer dispositivo na mesma rede pode acionar qualquer pipeline (incluindo apagar/reescrever cache) sem nenhuma credencial.
9. **Inserir texto vindo do backend em `innerHTML`** no JS da interface web (nomes de arquivo, mensagens de erro, texto raspado do Google Translate) sem escapar — use `textContent`/`escapeHtml()` como em `app.js`/`telemetria.js`. Esses valores não são confiáveis: podem conter nomes de arquivo arbitrários do usuário ou texto de uma resposta HTTP externa.
