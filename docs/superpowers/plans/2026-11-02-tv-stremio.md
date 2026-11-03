# Aba TV com complementos Stremio — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrar filmes, séries e animes por complementos Stremio, reproduzindo links diretos e torrents na TV e controlando pelo celular; concluir legendas na fase seguinte.

**Architecture:** Cliente HTTP do protocolo em core, armazenamento e estado de sessão próprios no aplicativo, player Media3 e adaptador de torrent com leitura apenas de peças verificadas. Compose integra a nova seção aos cinco temas; o Companion existente fornece cadastro de URLs e controle da reprodução na mesma sessão LAN.

**Tech Stack:** Kotlin, coroutines/StateFlow, Gson, OkHttp, Compose TV, Coil, Media3; libtorrent4j candidato para torrents, sujeito à validação API/ABI.

**Spec:** docs/superpowers/specs/2026-11-02-tv-stremio-design.md

## Global Constraints

- minSdk 26, compileSdk/targetSdk 36, Java 17; preservar assinatura e ABIs atuais, dados e USB.
- Não alterar versão, gerar APK de distribuição, publicar ou executar dois builds Gradle simultâneos.
- Filmes/Séries/Animes agora; jogos/emulador e formato próprio de fontes futuros.
- Nenhuma cópia de binários, código, marca, credenciais ou artes dos APKs analisados.
- Layout congelado do Controle do celular; só nova área TV/player. Efeitos opcionais com chaves TV/celular, sem introduzir animações obrigatórias.
- Persistir progresso/listas deste módulo separado das HQs. Não vazar URLs configuradas ou cabeçalhos em logs/backup.
- Box/iPhone e velocidade/codec não confirmados sem validação física.

## Review Focus

- Complemento com credenciais no caminho/query: preservar URL sem expor nas mensagens, logs ou backups.
- IDs de anime com dois-pontos/barras e tipos próprios: codificação e seleção de recursos corretas.
- Seek em torrent incompleto: aguardar peças verificadas; nunca servir zeros/arquivo parcial como vídeo válido.
- Resposta tardia ao trocar obra ou sair: não iniciar outro vídeo nem sobrescrever a tela atual.
- Celular desconectado, background ou pacote torrent sem espaço: parar tráfego e liberar estado próprio sem apagar outros dados.

---

### Task 1: Validar dependências de reprodução mantendo Android 8

**Files:** app/build.gradle.kts; app/src/main/assets/licenses/; docs/validation/tv-stremio.md.

**Interfaces:** produz versões fixadas de Media3 e torrent disponíveis para as tarefas 4–5; nenhuma mudança na versão Android mínima.

- [ ] Verificar metadados Maven/AAR de Media3 para compileSdk 36/minSdk 26; fixar versão comum em exoplayer, hls, dash e ui. Não escolher latest dinamicamente.
- [ ] Examinar libtorrent4j 2.1.0-38 (Java + android-arm e android-arm64) e seus binários antes de adicionar. A 2.1.0-39 declara Android API >=28 e não é candidata para elevar silenciosamente o minSdk.
- [ ] Validar load JNI, símbolos de API, licenças, alinhamento nativo e ABIs contra o aplicativo. Se a candidata não suportar API26, escolher compilação própria de versão compatível ou alternativa mantida, documentando decisão; não declarar torrents funcionando com stub.
- [ ] Registrar versões/hashes/licenças escolhidos e conferir resolução de dependências e compilação focada. Gradle único.

### Task 2: Protocolo e transporte Stremio

**Files:** core/src/main/kotlin/com/pelican/comictv/tv/StremioModels.kt, StremioLinks.kt, StremioClient.kt; core/src/test/kotlin/com/pelican/comictv/tv/StremioClientTest.kt.

**Interfaces:** `StremioLinks.manifest(input: String): String`; `StremioClient.manifest(url: String): AddonManifest`; `catalog(addon: Addon, type: String, id: String, extras: Map<String,String>): List<TvMeta>`; `meta(addon: Addon, type: String, id: String): TvMeta`; `streams(addon: Addon, type: String, videoId: String): List<TvStream>`; `addonCatalog(...): List<AddonDescriptor>`; suspending network operations.

- [ ] Criar fixtures/testes para stremio://, manifest HTTP(S) configurado, query, recursos string/objeto, prefixos, extras obrigatórios e IDs com caracteres reservados; rejeitar protocolos inesperados e HTML.
- [ ] Implementar modelos tolerantes a campos extras e tipos próprios. Separar movie/series do rótulo visual Animes; preservar IDs opacos e dados de temporada/episódio.
- [ ] Implementar URLs e requisições: 15s conexão/45s chamada, até 4 chamadas simultâneas, corpo JSON máximo 8MiB e 500 resultados por resposta. Redirecionamentos HTTP(S) limitados; cancelar chamadas ao cancelar coroutine. Nenhum erro mostra URL completa.
- [ ] Testar HTTP falso com pesquisa, gênero, skip e extras declarados; addon_catalog retorna propostas de instalação individual, sem executar código recebido.

### Task 3: Registro de complementos, catálogo e progresso

**Files:** app/src/main/java/com/pelican/comictv/tv/TvStore.kt, TvRepository.kt, TvViewModel.kt; app/src/main/java/com/pelican/comictv/AppContainer.kt; app/src/test/java/com/pelican/comictv/tv/TvRepositoryTest.kt.

**Interfaces:** `TvRepository.state: StateFlow<TvState>`; suspend `previewAddon(url)`, `installAddon(addon)`, `setAddonEnabled(id, enabled)`, `removeAddon(id)`, `search(query)`, `openMeta(type,id)`, `loadStreams(video)`; `TvStore.saveProgress(key, positionMs, durationMs)`; IDs identificam tipo+obra+vídeo e instalação por URL, sem colidir configurações.

- [ ] Testar persistência atômica, reinício, remoção sem apagar progresso e duas configurações do mesmo complemento; não incluir URLs em backup genérico.
- [ ] Criar armazenamento privado separado, consulta somente de complementos habilitados e compatíveis e resultados progressivos por fonte. `SupervisorJob`, cancelamento e geração de requisição descartam respostas antigas.
- [ ] Implementar paginação por catálogo, extras obrigatórios e busca só nos catálogos que a declaram; não fingir busca global em fontes sem recurso.
- [ ] Oferecer Cinemeta como instalação opcional, sem conta Stremio. Catálogos de anime com tipo ambíguo recebem seleção manual para seção Animes.
- [ ] Testar fonte lenta/falha isolada, mudança rápida de obra/pesquisa, catálogo vazio, cancelamento e restauração de favoritos/progresso.

### Task 4: Torrents e leitura progressiva

**Files:** app/src/main/java/com/pelican/comictv/tv/TvTorrentSession.kt, TorrentDataSource.kt; app/src/test/java/com/pelican/comictv/tv/TorrentReadPlannerTest.kt.

**Interfaces:** suspend `TvTorrentSession.prepare(infoHash: String, fileIdx: Int?, sources: List<String>): TorrentMedia`; `TorrentMedia.read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int`; `close()` cancela esperas e remove sessão. `state: StateFlow<TorrentState>` informa metadados, buffering, bytes e falha.

- [ ] Testar seleção fileIdx e maior vídeo quando ausente, índices inválidos, caminhos externos, links e falta de espaço; vídeo selecionado deve caber no limite de cache configurado e espaço livre com reserva mínima de 256MiB.
- [ ] Implementar sessão de torrent nativa única no foreground e diretório próprio cacheDir/tv-torrents. Limite inicial 8GiB, ajustável entre 1/2/4/8/16GiB; informar tamanho insuficiente antes de transferência longa e permitir mudar limite. Não tocar em USB/importados nem pedir troca de depuração.
- [ ] Priorizar peças do vídeo selecionado e regiões de início/fim necessárias; seeks atualizam prioridades. DataSource bloqueia até a peça estar verificada, com cancelamento e timeout recuperável; nunca entregar bytes não baixados de arquivo esparso.
- [ ] Parar e liberar torrent ao trocar fonte, sair do player ou app ir ao background; remover somente cache próprio após fechar handles. Mostrar estado sem prometer velocidade.
- [ ] Validar transferência/seek/cancelamento com torrent de fixture criado localmente e seeder controlado; não usar obras comerciais como teste. Validar JNI em aparelho/emulador compatível quando disponível e registrar pendência quando não.

### Task 5: Player direto/torrent e sessão de controle

**Files:** app/src/main/java/com/pelican/comictv/tv/TvPlaybackController.kt, TvPlayerScreen.kt; app/src/test/java/com/pelican/comictv/tv/TvPlaybackControllerTest.kt.

**Interfaces:** `play(request: TvPlaybackRequest)`, `pause()`, `resume()`, `seekTo(positionMs: Long)`, `stop()`; `state: StateFlow<TvPlaybackState>` compartilhado com Companion. Controller pertence ao container e player é acessado no Main; lifecycle explícito.

- [ ] Implementar URL HTTP(S), cabeçalhos de request informados pela fonte e HLS/DASH/progressivo; torrent usa DataSource da tarefa 4. externalUrl oferece abertura explícita no navegador. Formato não implementado produz mensagem concreta, não crash.
- [ ] Implementar controles D-pad pausa/continuar, avançar/retroceder, proporção, seleção de áudio e fonte; salvar progresso a cada 5s e no stop. Retomada pergunta Continuar ou Começar do início quando há progresso.
- [ ] Testar callback antigo após stop, troca de fonte, seek fora da duração, duração desconhecida e background liberando sockets/decoders/torrent.
- [ ] Reservar seleção de legendas para fase 2. Player sem fonte inicial não abre tela preta nem inicia downloads sozinho.

### Task 6: Aba TV e layout nos temas

**Files:** app/src/main/java/com/pelican/comictv/tv/TvScreen.kt, TvDetailsScreen.kt, TvAddonsScreen.kt; app/src/main/java/com/pelican/comictv/ui/ComicTvApp.kt; app/src/main/res/drawable/nav_tv.xml; app/src/androidTest/java/com/pelican/comictv/tv/TvNavigationTest.kt.

**Interfaces:** `TvScreen(repository, playback, onBack)` gerencia destinos internos sem armazenar URLs em rotas; rota principal `tv` usa shell existente.

- [ ] Adicionar TV à lista de seções e ícones comuns aos temas. Início/Filmes/Séries/Animes/Minha lista/Busca/Complementos são navegação interna.
- [ ] Implementar fileiras/grades reais, estado vazio com Adicionar complemento, ficha com temporadas/episódios, fontes agrupadas por complemento e prévia de manifesto antes de instalar. Configurar abre página explicitamente e retorna para colar URL configurada.
- [ ] Reutilizar componentes de foco/texto/cores do projeto, identidade própria; alto contraste e texto 85–130%. Não mover Controle nem introduzir animação obrigatória.
- [ ] Testar entrada/Voltar/foco após remoção de complemento e retorno de detalhes nos cinco temas; orientação retrato e tela pequena são verificações de layout separadas, sem afirmar execução física.

### Task 7: Cadastro e controle de vídeo pelo celular

**Files:** core/src/main/kotlin/com/pelican/comictv/transfer/CompanionServer.kt; app/src/main/java/com/pelican/comictv/companion/CompanionController.kt; app/src/main/assets/companion/index.html; core/src/test/kotlin/com/pelican/comictv/transfer/CompanionServerTest.kt.

**Interfaces:** GET `/tv-state` retorna nome/estado/posição/duração/buffering sem URLs secretas; comandos `tv-pause`, `tv-resume`, `tv-seek`, `tv-stop`, `tv-addon-preview`, `tv-addon-install` com limite de payload e validação de sessão existentes. Prévia retorna nonce vinculado à sessão; instalar confirma esse nonce, não uma URL diferente sem prévia.

- [ ] Adicionar área TV própria no painel para colar URL/confirmar complemento e mostrar reprodução da TV. Controle oferece pausa/continuar, seek e parar. Não exigir navegação completa de catálogo no celular nesta etapa, pois usuário escolheu controle.
- [ ] Executar rede fora de Main e ações de player no Main, verificando sessão após requisições longas. Preservar token, Host/Origin, limites, expiração e encerramento em background.
- [ ] Testar autenticação, payload, nonce expirado, seek inválido e matriz comandos UI x allowlist. Conferir que área Controle congelada não muda dimensões nem ordem.

### Task 8: Legendas — fase seguinte autorizada

**Files:** core/src/main/kotlin/com/pelican/comictv/tv/StremioSubtitles.kt; app/src/main/java/com/pelican/comictv/tv/TvSubtitleController.kt, TvSubtitleSettings.kt; app/src/test/java/com/pelican/comictv/tv/TvSubtitleTest.kt.

**Interfaces:** suspend `StremioClient.subtitles(addon, video, hints): List<TvSubtitle>`; `TvSubtitleController.select(subtitle?)`, `setDelayMs(delay)`; preferências idioma/tamanho/fundo persistidas.

- [ ] Unir faixas embutidas, legendas de stream e complementos subtitles sem colidir IDs de provedores. Preservar label/lang e enviar filename/hash/size quando disponíveis.
- [ ] Implementar SRT/VTT e ASS/SSA com estilos/posicionamento; validar renderização e fontes com fixture própria de anime. Media3 básico não é prova de paridade libass; selecionar integração libass ou player alternativo compatível se necessário, verificando licenças/ABIs/API26 antes de incorporar.
- [ ] Oferecer PT-BR inicial alterável, desligar, seleção manual, tamanho/fundo e atraso; exportar os mesmos ajustes ao controle do celular sem alterar seu layout congelado.
- [ ] Testar codificação UTF8/legado, relógio/seek, estilos ASS, múltiplas legendas do mesmo idioma e localhost:11470 dependente de servidor ausente. Nunca substituir localhost pelo IP da TV arbitrariamente.

### Task 9: Integração e conclusão

**Files:** docs/validation/tv-stremio.md; docs/tv-stremio-scope-2026-11-02.md.

- [ ] Executar testes focados de protocolo/torrent/sessão e compilação Android; depois checks de integração justificados. Uma tarefa Gradle por vez, sem APK de distribuição.
- [ ] Registrar versões nativas, licenças, recursos realmente implementados, fase atual, lacunas e resultado físico separado de compilação.
- [ ] Revisão independente final das mudanças com foco nos cinco riscos acima; corrigir achados antes de concluir. Sem commit/push/publicação automáticos.

## Referências técnicas verificadas

- Protocolo: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md
- Media3: https://developer.android.com/media/media3/exoplayer/hello-world
- Torrent: https://github.com/aldenml/libtorrent4j ; changelog registra API>=28 em 2.1.0-39: https://raw.githubusercontent.com/aldenml/libtorrent4j/master/changelog.txt

## Auto-revisão

Protocolo/links/tipos: tarefa 2; persistência/pesquisa: 3; torrent: 4; vídeo: 5; layout/temas: 6; celular: 7; legendas fase 2: 8; integração: 9. Os cinco riscos de Review Focus possuem passos de teste específicos. Decisão técnica de versão JNI só é concluída após tarefa 1, sem alterar os requisitos do usuário.
