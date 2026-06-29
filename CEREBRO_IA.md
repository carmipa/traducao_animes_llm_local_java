# CÉREBRO DE IA — Memória viva do projeto

> **Instrução para agentes (Cursor, Copilot, Claude, Gemini, etc.):** consulte o protocolo **AI-OS** em `.cursor/rules/ai-os.mdc` e leia **este arquivo** em toda sessão que tocar pipeline, tradução, revisão, telemetria ou UI web.  
> **Ao concluir mudanças relevantes, atualize a seção [Estado atual](#estado-atual-2026-06-27)** e, se criou pacotes/classes novos, regenere `mapa_projeto.md` (`POST /api/mapa` ou `--app.modo=MAPEAR`).

Documentos irmãos (consultar nesta ordem após decisões oficiais do Obsidian, se houver):

| Arquivo | Função |
|---------|--------|
| `.cursor/rules/ai-os.mdc` | Protocolo Antigravit — escopo fechado, ordem de consulta, qualidade |
| **CEREBRO_IA.md** (este) | Estado recente, decisões, checklist, onde salvar o quê |
| [ARQUITETURA.md](ARQUITETURA.md) | Decisões profundas, armadilhas JDK 25, interface web |
| [mapa_projeto.md](mapa_projeto.md) | Árvore de classes gerada automaticamente |
| [README.md](README.md) | Visão geral para humanos (pode ficar levemente desatualizado nos números de menu) |

---

## Protocolo obrigatório para IAs

1. **Antes de codar:** `.cursor/rules/ai-os.mdc` → este arquivo → trecho relevante de `ARQUITETURA.md`.
2. **Escopo fechado** antes de implementar; não ampliar escopo durante a execução (AI-OS).
3. **Depois de codar:** atualizar [Estado atual](#estado-atual-2026-06-27); rodar `./gradlew test` se mexeu em use cases.
4. **Persistência:** toda operação de pipeline deve gerar **logs + telemetria + relatório** (ver abaixo) — não só `System.out`.
5. **Beans Spring:** qualquer `@Service`/`@RestController` novo entra no `@Import` de `Application.java` (JDK 25 / ASM).
6. **Regenerar mapa** quando adicionar pacote Java novo (ex.: `raspagemRevisao`).

---

## Estado atual (2026-06-27)

### Menu web (ordem real na sidebar)

| # | Painel | API | Canal SSE |
|---|--------|-----|-----------|
| 1 | Análise de Mídia | `POST /api/analisar` | `analise` |
| 2 | Extração | `POST /api/extrair` | `extracao` |
| 3 | Tradução Local | `POST /api/traduzir` | `traducao` |
| 4 | Correção Cache | `POST /api/corrigir-cache`, `POST /api/corrigir-scraping` | `correcao` |
| 6 | **Revisão de Legendas** | `POST /api/revisar-legendas` (Google), `POST /api/revisar-legendas-concordancia` (LLM) | `revisao` |
| 7 | Remuxer | `POST /api/remuxar` | `remuxer` |
| 8 | Mapa do Projeto | `POST /api/mapa` | — |
| 9 | Telemetria | `GET /api/telemetria` | — |

*(Não há item 5 na UI — numeração histórica.)*

### Painel 6 — duas ações distintas

| Botão | API | Motor | O que corrige |
|-------|-----|-------|----------------|
| **Revisar Concordância (LLM)** | `POST /api/revisar-legendas-concordancia` | Mistral local + contexto de lore | Gênero, pronomes, artigos, tratamentos (senhor/senhora, ele/ela) nos `.ass` |
| **Corrigir via Scraping Google** | `POST /api/revisar-legendas` | Google Translate | Inglês residual e falas que o auditor flagrou |

Ambos exigem pasta com legendas **traduzidas** (`.ass`). Campo opcional: pasta EN. Select **Contexto de Lore** vale para o botão LLM.

**Legado (não é o fluxo principal da UI):** `POST /api/revisar-cache` + `RevisarCacheUseCase` — mesma lógica LLM, mas no **cache JSON** (menu 4 / CLI `RASPAGEM_REVISAO`). Preferir painel 6 para legendas `.ass` já geradas.

### Pipeline recomendado (DanMachi / obras com cache)

```
Extrair → Traduzir (LLM) → Limpar cache (4) → Google no cache (4 scraping)
→ Revisão de Legendas (6): Concordância LLM → opcional Google residual
→ Remuxer (7)
```

### Pacote `apiDadosAnime` (novo)

```
org.traducao.projeto.apiDadosAnime
├── domain/model/AnimeMetadata.java              # Record Java 25 (título, poster, nota, sinopse)
├── infrastructure/adapters/
│   ├── TmdbApiClientAdapter.java                # API TMDB (language=pt-BR, busca por TV/Movie)
│   └── JikanApiClientAdapter.java               # API Jikan MyAnimeList (fallback automático)
├── application/ObterMetadataAnimeUseCase.java   # Sanitização de pasta + cache em cache/metadata/
└── presentation/web/AnimeMetadataController.java # GET /api/metadata?caminho=...
```

### Pacote `raspagemRevisao` (atualizado)

```
org.traducao.projeto.raspagemRevisao
├── application/
│   ├── DetectorConcordanciaService.java    # heurísticas EN×PT (gênero, pronomes, tratamentos)
│   ├── AuditorProblemasLegendaService.java # inglês residual + concordância
│   ├── RevisarLegendasUseCase.java         # .ass PT; modos GOOGLE | LLM_CONCORDANCIA; cache EN fallback
│   └── RevisarCacheUseCase.java            # revisão LLM no cache JSON (legado API/CLI)
├── RevisorLegendasCLI.java                 # app.modo=RASPAGEM_REVISAO_LEGENDAS
└── RevisorRaspagemCLI.java                 # revisão LLM cache (modo separado)
```

**Frontend:** `static/revisao/revisao.js` — validação de caminhos, contextos, botões Google e LLM.

**Regras PT-BR gerais:** `traducao/contexto/RegrasConcordanciaPtBr.java` — injetado nos prompts via `ContextoPrompt`.

**Ajuste de gênero em fala ambígua (2026-06-28):** o inglês não marca gênero em 1ª/2ª pessoa (`I'm tired`, `Are you hurt?`, `Thank you`). O prompt agora proíbe masculino como fallback automático e recomenda formulações neutras quando o falante/interlocutor não puder ser inferido. O detector também marca como suspeitas traduções ambíguas no masculino (`Estou cansado`, `Você está pronto?`, `Obrigado`, `Estás ferido?`, `Não estou bêbado`) para revisão via lore/LLM.

**Pareamento legenda EN ↔ PT (Revisão de Legendas, menu 6):**

- Entrada: pasta com `*_PTBR_TrackN.ass` ou `*_PT-BR.ass`
- Original EN (ordem de prioridade):
  1. `.ass` pareado na mesma pasta (ex.: `_Track2.ass`) ou pasta EN opcional na UI
  2. **Fallback:** `cache/**/*_ENG.cache.json` (walk + pareamento por episódio/nome)
- Correções são salvas de volta no `.ass` traduzido (mesma pasta de entrada)

**Pareamento cache ↔ tradução LLM (menu 3):**

- Cache: `...Dual Audio]_ENG.cache.json` em `cache/` (walk recursivo)
- Normalização remove: `_PT-BR`, `_PTBR`, `_PTBR_TrackN`, `_TrackN`, `_ENG`

### Contextos de lore (tradução LLM e Web UI Select)

`GerenciadorContexto` + provedores em `traducao/contexto/**`. Expostos na Web UI via `GET /api/contextos`:
- **DanMachi**: Season 1 a 5 discriminados, Sword Oratoria e Arrow of the Orion (Filme).
- **Macross**: Macross Anime, Macross 2 (Lovers Again), Macross 7, Macross DYRL (Filme 1), Macross Frontier, Macross Delta.
- **Gundam**: Universal Century (0079, Zeta, ZZ, CCA, War in Pocket 0080, Stardust Memory 0083, 08th MS Team, Reconguista), SEED (SEED, SEED Destiny, SEED Freedom).
- **Outros**: Knights of Sidonia (TV e Filme Love Woven in the Stars), Guilty Crown, Neon Genesis Evangelion (Série TV), Evangelion: 1.11, Evangelion: 2.22, Evangelion: 3.33, Evangelion: 3.0+1.0, 86 Eighty-Six.

---

## Onde cada coisa é salva (padrão unificado de subpastas)

Por padrão, quando o campo de pasta de saída opcional é deixado em branco na Web UI, o sistema cria automaticamente a subpasta de destino **dentro da própria pasta da mídia indicada pelo usuário**:
- **Tradução Local (3)**: subpasta `traducao_ptbr/` na pasta do anime.
- **Extração de Legendas (2)**: subpasta `legendas_extraidas_[formato]/` na pasta dos vídeos.
- **Análise de Mídia (1)**: subpasta `relatorios/` na pasta do vídeo/anime.
- *(Se o usuário preencher o campo opcional de saída, o sistema salva no caminho customizado informado).*

**Padronização Visual de Sucesso no Console Web:**
Todos os 8 endpoints operacionais da API (`ApiController.java`) imprimem um banner ultra-destacado em verde ANSI (`\u001B[32m`) com moldura (`========================================================================`) e o ícone `🎉` ao finalizar cada tarefa em segundo plano, garantindo alta visibilidade na interface.

| Tipo | Caminho Padrão | Quem grava |
|------|----------------|------------|
| Log estruturado | `logs/tradutor.log` | SLF4J |
| Log console web | `logs/console-web.log` | `LogStreamService` (SSE) |
| Telemetria canônica | `logs/telemetria_compartilhada.json` | `TelemetriaService.persistirCanonico()` |
| Análise mídia | `{pasta_midia}/relatorios/*.txt`, `*.json` | `AnalisarMidiaUseCase` |
| Extração | `{pasta_midia}/legendas_extraidas_[formato]/*.ass` | `ExtrairLegendaUseCase` |
| Tradução | `{pasta_midia}/traducao_ptbr/*.ass` | `ProcessarArquivoUseCase` / `EscritorLegendaAss` |
| Cache tradução | `cache/**/**_ENG.cache.json` | `CacheTraducaoService` |

### JSON telemetria — estrutura

```json
{
  "midias": [ ... ],
  "traducoesLlm": [ ... ],
  "operacoes": [
    {
      "tipo": "Revisão Concordância (.ass LLM)",
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

Tipos em `operacoes[].tipo`: `Revisão Legendas (.ass)`, `Revisão Concordância (.ass LLM)`, `Revisão Gramatical (cache LLM)`, limpeza/correção cache.

**Operações que chamam `finalizarOperacao`:** Revisão Legendas (Google), Revisão Concordância (.ass LLM), Limpeza Cache, Correção Google, Revisão Gramatical (cache legado).

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
| Revisão de legendas lia cache | Confusão com menu 4 (revisão cache) | Documentado: cache é **fallback** EN no menu 6; LLM concordância também no painel 6 |
| `InvalidPathException` na revisão | UI enviava texto de logs no campo pasta | `revisao.js` valida caminho + `ApiController.parseCaminhoSeguro()` |
| Consoles de log pequenos / resize quebrado | `height: 220px` + flex anulava `resize` | `style.css`: `flex:none`, `45vh`, `resize: vertical` em `.console-body` |
| Concordância LLM só no cache/API legada | Botão removido do menu 4 | Integrado ao painel 6: `/api/revisar-legendas-concordancia` + select de contexto |

---

## Gaps conhecidos (backlog)

- README e `ARQUITETURA.md` ainda sem `/api/revisar-legendas-concordancia` e detalhe do modo LLM no painel 6.
- Falhas parciais de tradução LLM ainda não geram `LlmTelemetria`.
- Remuxer / extração não têm `OperacaoTelemetria` (só revisão/correção/cache).
- Detector heurístico pode não flaggar todos os erros de gênero (ex.: “seu rosto” para “his face” sem contexto feminino explícito).
- Consolidar ou deprecar formalmente `/api/revisar-cache` vs concordância no painel 6 (evitar duplicidade conceitual).
- AI-OS global (Obsidian `00_…99_`, kernel, scheduler) — fora do escopo deste repo; ver opção B do plano Antigravit.

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
| 2026-06-28 | Codex | Reforça regras contra masculino automático em falas ambíguas e documenta gêneros de personagens em Gundam 0080 |
| 2026-06-27 | Agent | AI-OS: rule `ai-os.mdc`; painel 6 com concordância LLM + Google; cache EN fallback; fix path UI; consoles redimensionáveis |

---

*Última verificação cruzada com o código: `RevisarLegendasUseCase` (modos GOOGLE/LLM), `/api/revisar-legendas-concordancia`, `static/revisao/revisao.js`, `style.css` consoles, `ApiController.parseCaminhoSeguro`, testes `./gradlew test` OK.*
