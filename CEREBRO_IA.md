# CÉREBRO DE IA — Memória viva do projeto

> **Instrução para agentes (Cursor, Copilot, etc.):** leia **este arquivo primeiro** em toda sessão que tocar pipeline, tradução, revisão, telemetria ou UI web.  
> **Ao concluir mudanças relevantes, atualize a seção [Estado atual](#estado-atual-2026-06-27)** e, se criou pacotes/classes novos, regenere `mapa_projeto.md` (`POST /api/mapa` ou `--app.modo=MAPEAR`).

Documentos irmãos (consultar junto):

| Arquivo | Função |
|---------|--------|
| **CEREBRO_IA.md** (este) | Estado recente, decisões, checklist, onde salvar o quê |
| [ARQUITETURA.md](ARQUITETURA.md) | Decisões profundas, armadilhas JDK 25, interface web |
| [mapa_projeto.md](mapa_projeto.md) | Árvore de classes gerada automaticamente |
| [README.md](README.md) | Visão geral para humanos (pode ficar levemente desatualizado nos números de menu) |

---

## Protocolo obrigatório para IAs

1. **Antes de codar:** ler este arquivo + trecho relevante de `ARQUITETURA.md`.
2. **Depois de codar:** atualizar [Estado atual](#estado-atual-2026-06-27); rodar `./gradlew test` se mexeu em use cases.
3. **Persistência:** toda operação de pipeline deve gerar **logs + telemetria + relatório** (ver abaixo) — não só `System.out`.
4. **Beans Spring:** qualquer `@Service`/`@RestController` novo entra no `@Import` de `Application.java` (JDK 25 / ASM).
5. **Regenerar mapa** quando adicionar pacote Java novo (ex.: `raspagemRevisao`).

---

## Estado atual (2026-06-27)

### Menu web (ordem real na sidebar)

| # | Painel | API | Canal SSE |
|---|--------|-----|-----------|
| 1 | Análise de Mídia | `POST /api/analisar` | `analise` |
| 2 | Extração | `POST /api/extrair` | `extracao` |
| 3 | Tradução Local | `POST /api/traduzir` | `traducao` |
| 4 | Correção Cache | `POST /api/corrigir-cache`, `POST /api/corrigir-scraping` | `correcao` |
| 6 | **Revisão de Legendas** | `POST /api/revisar-legendas` | `revisao` |
| 7 | Remuxer | `POST /api/remuxar` | `remuxer` |
| 8 | Mapa do Projeto | `POST /api/mapa` | — |
| 9 | Telemetria | `GET /api/telemetria` | — |

*(Não há item 5 na UI — numeração histórica.)*

### Pipeline recomendado (DanMachi / obras com cache)

```
Extrair → Traduzir (LLM) → Limpar cache (4) → Google no cache (4 scraping)
→ Revisão de Legendas (6) → Remuxer (7)
```

### Pacote `raspagemRevisao` (novo)

```
org.traducao.projeto.raspagemRevisao
├── application/
│   ├── DetectorConcordanciaService.java    # heurísticas EN×PT (gênero, pronomes)
│   ├── AuditorProblemasLegendaService.java # inglês residual + concordância
│   ├── RevisarLegendasUseCase.java         # .ass PT + .ass EN pareado + Google (sem cache)
│   └── RevisarCacheUseCase.java            # revisão LLM no cache (CLI /api/revisar-cache)
├── RevisorLegendasCLI.java                 # app.modo=RASPAGEM_REVISAO_LEGENDAS
└── RevisorRaspagemCLI.java                 # revisão LLM cache (modo separado)
```

**Regras PT-BR gerais:** `traducao/contexto/RegrasConcordanciaPtBr.java` — injetado nos prompts via `ContextoPrompt`.

**Pareamento legenda EN ↔ PT (Revisão de Legendas, menu 6):**

- Entrada: pasta com `*_PTBR_TrackN.ass` ou `*_PT-BR.ass`
- Original EN: `.ass` pareado na mesma pasta (ex.: `_Track2.ass`) ou pasta EN opcional na UI
- **Não usa** `cache/*.cache.json` — isso é o menu 4 (`RevisarCacheUseCase`)
- Correções são salvas de volta no `.ass` traduzido

**Pareamento cache ↔ tradução LLM (menu 3):**

- Cache: `...Dual Audio]_ENG.cache.json` em `cache/` (walk recursivo)
- Normalização remove: `_PT-BR`, `_PTBR`, `_PTBR_TrackN`, `_TrackN`, `_ENG`

### Contextos de lore (tradução LLM)

`GerenciadorContexto` + provedores em `traducao/contexto/**` (DanMachi, Gundam, Macross, 86, Sidonia…).  
Selecionável na UI tradução (`GET /api/contextos`).

---

## Onde cada coisa é salva (padrão unificado)

| Tipo | Caminho | Quem grava |
|------|---------|------------|
| Log estruturado | `logs/tradutor.log` | SLF4J (`out()` nos use cases de revisão/correção) |
| Log console web | `logs/console-web.log` | `LogStreamService` (SSE) |
| Telemetria canônica | `logs/telemetria_compartilhada.json` | `TelemetriaService.persistirCanonico()` |
| Cópia telemetria | `relatorios/{nomePasta}/telemetria_compartilhada.json` | `TelemetriaService.salvar(pastaRelatorios)` |
| Relatório operação | `relatorios/{nomePasta}/{prefixo}_yyyyMMdd_HHmmss.txt` + `.json` | `TelemetriaService.finalizarOperacao(...)` |
| Análise mídia | `relatorios/{anime}/*.txt`, `*.json` | `AnalisarMidiaUseCase` |
| Cache tradução | `cache/**/**_ENG.cache.json` (ou `{base}.cache.json`) | `CacheTraducaoService` |
| Legendas PT | pasta informada pelo usuário | `EscritorLegendaAss` / revisão sobrescreve na mesma pasta |

### JSON telemetria — estrutura

```json
{
  "midias": [ ... ],
  "traducoesLlm": [ ... ],
  "operacoes": [
    {
      "tipo": "Revisão Legendas (.ass)",
      "detalhe": "caminho da pasta",
      "tempoTotalMs": 45000,
      "arquivosProcessados": 15,
      "itensDetectados": 3,
      "itensCorrigidos": 2,
      "registradoEm": "2026-06-27T..."
    }
  ]
}
```

**Operações que chamam `finalizarOperacao`:** Revisão Legendas, Limpeza Cache, Correção Google, Revisão Gramatical (cache).

**Tradução LLM:** `registrarTraducao(LlmTelemetria)` por episódio bem-sucedido.

**Análise mídia:** `registrarMidia` + relatórios + `salvar(pastaRelatorios)`.

---

## Verificação de saúde do projeto (checklist IA)

Execute mentalmente ou via terminal após mudanças:

- [ ] `./gradlew test` — verde
- [ ] `@Import` em `Application.java` inclui beans novos
- [ ] Revisão legendas: log mostra `Legenda original EN:` e `N falas auditadas`
- [ ] `logs/telemetria_compartilhada.json` contém `"operacoes"` após revisão/correção
- [ ] `relatorios/<pasta>/revisao_legendas_*.txt` existe após menu 6
- [ ] SSE canal `revisao` → `#console-revisao` (`static/js/app.js` `consoleMap`)
- [ ] `[SUCESSO]` na UI dispara refresh telemetria (`verificarAlertaSSE` em `app.js`)
- [ ] Desconexão SSE não derruba servlet (`ConsoleRedirector` + `LogStreamService` engolem exceção)

---

## Bugs recentes corrigidos (não reintroduzir)

| Problema | Causa | Correção |
|----------|-------|----------|
| Revisão 0 problemas / 0 correções | Cache `_ENG` não pareado com `_PTBR_Track3` | `normalizarBaseLegenda` + walk + fallback por texto |
| Telemetria vazia pós-revisão | Só LLM/mídia registravam | `OperacaoTelemetria` + `finalizarOperacao` |
| Telemetria parcial em `relatorios/` | `salvar()` lia só pasta local | Fonte canônica sempre `logs/` + cópia |
| Erro servlet ao abrir Telemetria | SSE cliente desconectado propagava IO | try/catch em `ConsoleRedirector` / `LogStreamService` |
| Mensagem enganosa “Nenhum problema” | Falas puladas sem EN | Log distingue auditadas vs ignoradas |
| `[SUCESSO]` com pasta cache | Usuário apontou `cache\Season 05` em vez de legendas `.ass` | `validarPastaEntrada()` na API + aviso na UI |
| Revisão de legendas lia cache | Confusão com menu 4 (revisão cache) | `RevisarLegendasUseCase` usa só `.ass` EN+PT pareados |

---

## Gaps conhecidos (backlog)

- README tabela de painéis ainda lista numeração antiga (sem item 6 revisão).
- `ARQUITETURA.md` seção interface web não lista `/api/revisar-legendas` nem pacote `raspagemRevisao` em detalhe.
- Falhas parciais de tradução LLM ainda não geram `LlmTelemetria`.
- Remuxer / extração não têm `OperacaoTelemetria` (só revisão/correção/cache).
- Detector heurístico pode não flaggar todos os eres de gênero (ex.: “seu rosto” para “his face” sem contexto feminino explícito).

---

## Comandos úteis

```bash
# Interface web (padrão)
.\gradlew.bat bootRun --console=plain

# Testes
.\gradlew.bat test

# Regenerar mapa + relatório diretório
.\gradlew.bat bootRun --args="--app.modo=MAPEAR" --console=plain

# CLI revisão legendas
# app.modo=RASPAGEM_REVISAO_LEGENDAS (RevisorLegendasCLI)
```

---

## Histórico de atualizações deste cérebro

| Data | Autor | Resumo |
|------|-------|--------|
| 2026-06-27 | Agent | Criação; documenta raspagemRevisao, telemetria operacoes, relatórios, pareamento cache, protocolo IA |

---

*Última verificação cruzada com o código: pacotes `raspagemRevisao`, `TelemetriaService.finalizarOperacao`, `RevisarLegendasUseCase.resolverArquivoCache`, menu 6 em `index.html`, testes `./gradlew test` OK.*
