# Arquitetura do Tradutor Local (Java 25 + Spring Boot)

## Para as IAs que lerem este repositório no futuro

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
org.traducao.animes
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

---

## O que NÃO fazer (armadilhas comuns)

1. **Reativar `StructuredTaskScope` paralelo nos lotes** sem medir carga no LM Studio — causa os erros vistos em `logs/tradutor.log` (jun/2026).
2. **Hard-coded de pasta** em `application.yml` — usuário quer escolher no console.
3. **Usar só `ConsoleUILogger` para o prompt de pastas** — pode não aparecer a tempo ou competir com a progress bar.
4. **Confiar em `@ComponentScan`** sem `@Import` — beans não sobem no JDK 25.
5. **Assumir que Virtual Threads = paralelismo de GPU** — o gargalo é inferência local serial na prática.
