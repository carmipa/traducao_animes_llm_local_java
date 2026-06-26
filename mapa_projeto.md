# MAPA ESTRUTURAL DO PROJETO - TRACKER ANIMES
Gerado em: traducao_animes_llm_local_java
Este documento serve como mapa de contexto para LLMs atualizarem a documentação oficial.
---

## 📁 Pasta: `.vscode/`
*(Nenhum script Python ou Java nesta pasta)*

## 📁 Pasta: `gradle/`
*(Nenhum script Python ou Java nesta pasta)*

## 📁 Pasta: `legendas-ptbr/`
*(Nenhum script Python ou Java nesta pasta)*

## 📁 Pasta: `logs/`
*(Nenhum script Python ou Java nesta pasta)*

## 📁 Pasta: `relatorios/`
*(Nenhum script Python ou Java nesta pasta)*

## 📁 Pasta: `src/`
### 📄 Arquivo: `src/main/java/org/traducao/projeto/analisadorMidia/application/AnalisarMidiaUseCase.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/analisadorMidia/domain/AnalisadorException.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/analisadorMidia/domain/AudioInfo.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/analisadorMidia/domain/AuditoriaResultado.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/analisadorMidia/domain/ContainerInfo.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/analisadorMidia/domain/LegendaInfo.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/analisadorMidia/domain/VideoInfo.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/analisadorMidia/infrastructure/adapters/FfprobeAdapter.java`
```text
Executa ffprobe no vídeo e obtém o JSON com as informações gerais e faixas.
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/analisadorMidia/presentation/AnalisadorMidiaCLI.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/analisadorMidia/presentation/ui/ConsoleAnalisadorLogger.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/legendasExtracao/application/ExtrairLegendaUseCase.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/legendasExtracao/application/strategy/ExtratorAssStrategy.java`
```text
1. Tentar por palavras-chave
2. Tentar a última candidata (geralmente a faixa completa em ASS, a primeira é signs)
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/legendasExtracao/application/strategy/ExtratorPgsStrategy.java`
```text
Para PGS, geralmente pega a primeira encontrada ou a marcada como default
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/legendasExtracao/application/strategy/ExtratorSrtStrategy.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/legendasExtracao/application/strategy/ExtratorStrategy.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/legendasExtracao/domain/ExtratorException.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/legendasExtracao/domain/FaixaLegenda.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/legendasExtracao/domain/FormatoLegenda.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/legendasExtracao/domain/RelatorioExtracao.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/legendasExtracao/infrastructure/adapters/MkvToolNixAdapter.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/legendasExtracao/infrastructure/config/ExtratorProperties.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/legendasExtracao/presentation/ExtratorCLI.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/legendasExtracao/presentation/ui/ConsoleExtratorLogger.java`
```text
Tag colorida em negrito (chama atenção), corpo da mensagem em peso normal
(mais fácil de ler em blocos de texto maiores) — INFO fica sem cor nenhuma.
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/mapaProjeto/application/GeradorMapaProjetoUseCase.java`
```text
Lista e ordena pastas imediatas na raiz
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/mapaProjeto/application/MapeadorDiretorioUseCase.java`
```text
Cabeçalho Técnico
PARTE 1: CAMINHO ABSOLUTO COMPLETO NO SISTEMA LOCAL
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/mapaProjeto/presentation/MapaProjetoCLI.java`
```text
Determina a raiz a ser mapeada
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/raspagemCorrecao/application/CorrigirComGoogleUseCase.java`
```text
Lista de termos e magias conhecidas de DanMachi que devem permanecer inalterados
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/raspagemCorrecao/CorretorRaspagemCLI.java`
```text
CommandLineRunner que realiza a tradução das falas residuais pendentes em inglês
utilizando raspagem na API gratuita e sem chaves do Google Translate.
Ativado quando a propriedade app.modo é configurada como "RASPAGEM_CORRECAO".
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/remuxer/application/MapeadorMidiaService.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/remuxer/application/RemuxarLoteUseCase.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/remuxer/domain/RelatorioRemux.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/remuxer/domain/RemuxerException.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/remuxer/domain/RemuxTarefa.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/remuxer/infrastructure/adapters/MkvmergeAdapter.java`
```text
Tentar os caminhos padrões do Windows
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/remuxer/infrastructure/config/RemuxerProperties.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/remuxer/presentation/RemuxerCLI.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/remuxer/presentation/ui/ConsoleRemuxerLogger.java`
```text
Tag colorida em negrito (chama atenção), corpo da mensagem em peso normal
(mais fácil de ler em blocos de texto maiores) — INFO/DEBUG ficam sem cor.
Exemplo: [10:20:30] [INFO   ] Mensagem...
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/telemetria/LlmTelemetria.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/telemetria/MidiaTelemetria.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/telemetria/OperacaoHistorico.java`
```text
Uma linha da tabela de histórico de operações exibida no painel de Telemetria.
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/telemetria/TelemetriaResumo.java`
```text
Resumo serializável da telemetria acumulada na sessão atual do servidor,
consumido pelo painel "Telemetria" da interface web.
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/telemetria/TelemetriaService.java`
```text
Local canônico dentro do próprio projeto onde a telemetria é sempre
mesclada e persistida a cada registro, para sobreviver a restarts do
servidor e não depender só do lote em memória (que é limpo a cada
análise via limparLote()). É o que o painel web lê em gerarResumo().
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducaoCorrige/application/LimparCacheUseCase.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducaoCorrige/CorretorCache.java`
```text
Programa Utilitário que realiza a limpeza seletiva do cache de tradução.
Remove traduções que falharam e foram salvas com o texto original em inglês (fallbacks),
permitindo que sejam reprocessadas com a nova lógica e prompts corrigidos.
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducaoCorrige/CorretorCacheCLI.java`
```text
CommandLineRunner que realiza a limpeza do cache de tradução integrado ao fluxo do Spring.
Ativado quando a propriedade app.modo é configurada como "CORRIGIR_CACHE".
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/Application.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/application/ProcessarArquivoUseCase.java`
```text
Orquestra a tradução de um único arquivo de legenda: le -> reaproveita o
cache existente -> traduz só o que falta (deduplicando falas repetidas) ->
valida -> escreve a legenda final em PT-BR -> grava/atualiza o cache.
<p>
Correções manuais feitas pelo usuário no JSON de cache são respeitadas na
próxima execução: uma fala cujo texto original já tem tradução não-vazia no
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/application/ProcessarEpisodioUseCase.java`
```text
Quantas tentativas extras (alem da primeira) sao feitas numa fala isolada
(lote de tamanho 1) antes de desistir e manter o texto original sem traducao.
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/application/ValidadorTraducaoService.java`
```text
Regras robustas importadas do pipeline Python, ampliadas após observar em
produção o Mistral Nemo deixar fragmentos como "exactly the same" sem
traduzir mesmo traduzindo o resto da fala corretamente.

UNICODE_CHARACTER_CLASS e necessario aqui: sem ela, \b no Java so reconhece
[a-zA-Z0-9_] como caractere de palavra, entao letras acentuadas (ç, ã, é...)
contam como "fronteira", e palavras em portugues como "força" ou "esforço"
batem com "\bfor\b" e disparam falso positivo de "resíduo em inglês".
Evita os falsos positivos de "Abaixo a tirania" bloqueando apenas preâmbulos óbvios
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/domain/exceptions/AlucinacaoDetectadaException.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/domain/exceptions/ArquivoLegendaException.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/domain/exceptions/DivergenciaLinhasException.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/domain/exceptions/LlmFalhaComunicacaoException.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/domain/exceptions/RespostaLlmVaziaException.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/domain/exceptions/TraducaoParcialException.java`
```text
Construtor usado pela camada do Episódio (nível de Lotes)
Construtor usado pela camada de Arquivo (nível de Falas Mascaradas)
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/domain/exceptions/TradutorException.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/domain/legenda/DocumentoLegenda.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/domain/legenda/EventoLegenda.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/domain/Lote.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/domain/ports/MistralPort.java`
```text
Verifica, antes de iniciar a tradução, se o servidor LLM local está
online e se o modelo configurado está efetivamente carregado em
memória — evita descobrir isso só depois de várias tentativas/timeouts
já no meio da tradução do primeiro episódio.
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/domain/StatusLlm.java`
```text
Resultado da checagem de disponibilidade do servidor LLM local (ex: LM Studio)
feita no início da execução, antes de começar a traduzir qualquer episódio.
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/domain/TraducaoLote.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/infrastructure/adapters/MistralClientAdapter.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/infrastructure/cache/CacheTraducaoService.java`
```text
Persiste, por arquivo de legenda, o par (texto original em ingles -> texto
traduzido) em JSON. Serve a dois propositos: (1) permitir que o usuario
revise/corrija falhas de traducao manualmente editando o JSON e (2) evitar
chamar o LLM de novo para falas ja traduzidas em uma execucao anterior -
uma correcao manual no cache e respeitada na proxima execucao.
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/infrastructure/cache/EntradaCache.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/infrastructure/config/LlmProperties.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/infrastructure/config/RestClientConfig.java`
```text
Aplica timeouts de conexao/leitura ao {@link org.springframework.web.client.RestClient.Builder}
autoconfigurado pelo Spring Boot, antes dele chegar ao {@code MistralClientAdapter}.
Sem isso, uma chamada ao LLM local que trava (ex: servidor LM Studio sem
resposta) deixaria a Virtual Thread bloqueada indefinidamente.
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/infrastructure/config/TradutorProperties.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/infrastructure/dtos/RecordsMistral.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/infrastructure/legenda/EscritorLegendaAss.java`
```text
Reconstroi o arquivo .ass a partir do {@link DocumentoLegenda}, repetindo o
cabecalho original e as linhas nao traduziveis byte a byte, e so trocando o
campo Text dos eventos Dialogue pela versao traduzida.
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/infrastructure/legenda/LeitorLegendaAss.java`
```text
Le arquivos .ass/.ssa preservando byte a byte tudo que nao for o campo Text
dos eventos Dialogue (estilos, timestamps, secoes de metadados). So o campo
Text e exposto para traducao; o resto e reconstruido identico pelo
{@link EscritorLegendaAss}.
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/infrastructure/legenda/MascaradorTags.java`
```text
Isola tags de formatação ASS/SSA (ex: {\i1}, {\pos(...)}) e códigos de quebra
(\N, \n, \h) do texto antes de enviar ao LLM, trocando-os por marcadores
[[TAGn]] que o modelo é instruído a preservar literalmente. Sem isso o LLM
tende a "traduzir" ou descartar as tags, corrompendo a legenda renderizada.
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/presentation/TradutorCLI.java`
```text
Ponto de entrada da CLI: varre a pasta de entrada por arquivos .ass/.ssa
e traduz cada um sequencialmente.
<p>
Se {@code tradutor.diretorio-entrada} estiver vazio, o {@link Application#main}
pede os caminhos via {@link ConsoleEntrada}
antes do Spring subir.
<p>
Arquivos são processados um por vez de propósito: todos compartilham o
mesmo LLM local (GPU única). Lotes dentro de cada episódio também são
sequenciais (ver {@code ProcessarEpisodioUseCase}).
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/presentation/ui/AnsiCores.java`
```text
Cores ANSI compartilhadas entre o prompt interativo e o {@link ConsoleUILogger}.
Usar apenas caracteres ASCII nos textos do prompt evita problemas de encoding
no console do Windows (cp1252 vs UTF-8).
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/presentation/ui/ConsoleEntrada.java`
```text
Numeração segue a ordem natural do pipeline: primeiro auditar a
mídia, depois extrair/traduzir/corrigir a legenda e por fim remuxar.
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/presentation/ui/ConsoleUILogger.java`
```text
Wrapper thread-safe em torno da barra de progresso (estilo tqdm). Todo
acesso a {@code pb} e sincronizado porque mensagens podem chegar
durante a tradução de um episódio.
<p>
O console e efêmero (a barra de progresso sobrescreve linhas antigas), por
isso toda mensagem também é espelhada no logger SLF4J, que persiste em
arquivo (ver {@code logging.file.name}) e sobrevive para análise posterior.
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/presentation/ui/PastasExecucao.java`
```text
Pastas efetivas da execução atual. Preenchidas pelo {@code TradutorCLI} a
partir do diálogo Swing ou das propriedades/linha de comando.
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/presentation/web/ApiController.java`
```text
REST Controller que expõe a API REST para a interface web.
Permite acionar todos os módulos do pipeline em segundo plano.
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/presentation/web/BrowserLauncher.java`
```text
Listener que aguarda a inicialização completa do Spring Boot
e abre automaticamente o navegador padrão na URL da aplicação web
caso a propriedade app.modo esteja configurada como "WEB".
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/presentation/web/ConsoleRedirector.java`
```text
Interceptador global de System.out.
Redireciona tudo que é impresso no console padrão para o LogStreamService (SSE)
sem deixar de imprimir no console físico (terminal do CMD/PowerShell original).
```

### 📄 Arquivo: `src/main/java/org/traducao/projeto/traducao/presentation/web/LogStreamService.java`
```text
Serviço responsável por gerenciar conexões Server-Sent Events (SSE)
e despachar mensagens de log em tempo real para os clientes web conectados.
Cada linha publicada também é persistida em {@code logs/console-web.log},
já que o console do navegador (diferente de {@code logs/tradutor.log}) não
sobrevive a um reload de página ou ao fechamento da aba.
```

### 📄 Arquivo: `src/test/java/org/traducao/projeto/legendasExtracao/application/ExtrairLegendaUseCaseTest.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/test/java/org/traducao/projeto/legendasExtracao/application/strategy/ExtratorAssStrategyTest.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/test/java/org/traducao/projeto/legendasExtracao/application/strategy/ExtratorPgsStrategyTest.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/test/java/org/traducao/projeto/legendasExtracao/application/strategy/ExtratorSrtStrategyTest.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/test/java/org/traducao/projeto/legendasExtracao/domain/FormatoLegendaTest.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/test/java/org/traducao/projeto/legendasExtracao/presentation/ExtratorCLITest.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/test/java/org/traducao/projeto/remuxer/application/MapeadorMidiaServiceTest.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/test/java/org/traducao/projeto/remuxer/application/RemuxarLoteUseCaseTest.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/test/java/org/traducao/projeto/remuxer/presentation/RemuxerCLITest.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/test/java/org/traducao/projeto/traducao/ApplicationTest.java`
```text
Sem stdin interativo nos testes: solicitarPastas retorna vazio
```

### 📄 Arquivo: `src/test/java/org/traducao/projeto/traducao/application/ProcessarArquivoUseCaseTest.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/test/java/org/traducao/projeto/traducao/application/ProcessarEpisodioUseCaseTest.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/test/java/org/traducao/projeto/traducao/application/ValidadorTraducaoServiceTest.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/test/java/org/traducao/projeto/traducao/infrastructure/adapters/MistralClientAdapterTest.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/test/java/org/traducao/projeto/traducao/infrastructure/cache/CacheTraducaoServiceTest.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/test/java/org/traducao/projeto/traducao/infrastructure/config/TradutorPropertiesTest.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/test/java/org/traducao/projeto/traducao/infrastructure/legenda/LeitorLegendaAssTest.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/test/java/org/traducao/projeto/traducao/infrastructure/legenda/MascaradorTagsTest.java`
*(Sem docstring ou cabeçalho explicativo)*

### 📄 Arquivo: `src/test/java/org/traducao/projeto/traducao/presentation/TradutorCLITest.java`
*(Sem docstring ou cabeçalho explicativo)*

---
