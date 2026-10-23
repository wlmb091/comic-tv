# Comic TV — plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Entregar o fluxo local de um leitor nativo de TV com importação CBZ/ZIP, biblioteca, leitura pelo controle e retomada.

**Architecture:** Um módulo Android com pacotes por responsabilidade e injeção manual por AppContainer. ViewModels expõem StateFlow; Room conserva biblioteca e progresso. ComicPageProvider desacopla o leitor das origens locais e futuras fontes Mihon.

**Tech Stack:** Kotlin, Compose Foundation, TV Material, Room, coroutines, Coil, JUnit. Versões serão fixadas após consultar documentação oficial e confirmar compatibilidade de JDK, Gradle, AGP e SDK.

**Spec:** comic-tv-especificacao.md. Referência visual: comic-tv-design-aprovado.png. Requisitos acumulados: comic-tv-referencias.md.

## Global Constraints
- Android TV e Google TV; interface em português; todas as funções acessíveis pelo D-pad básico.
- Direção visual aprovada: escuro, branco e âmbar; foco visível; margens de segurança; capas grandes.
- API 23 é a meta inicial; confirmar o piso pelas dependências resolvidas antes de fixar minSdk.
- Máximo de 1 GiB por importação, 5.000 entradas por ZIP, 32 MiB por página, 2 GiB declarados de imagens e 256 MiB de cache.
- Não executar extensões ou instalar APKs no computador durante o incremento local.
- Não anunciar compatibilidade universal com extensões. Múltiplos repositórios fazem parte do produto e terão incremento específico.
- Sem APK anunciado como pronto antes de compilação bem-sucedida; validação física é separada de build e testes JVM.

## Review Focus
- Seletor de arquivos ausente na TV: mostrar instrução útil sem encerrar o aplicativo.
- ZIP truncado, nomes inseguros e tamanho desconhecido: falhar sem promover importação parcial.
- Pressão de disco: preservar originais e página em uso; interromper extração com erro recuperável.
- Recriação de processo: restaurar edição e página válidas, sem depender de onDestroy.
- Repetição rápida de teclas: cancelar carregamentos antigos e impedir que uma página antiga substitua a atual.

## Estrutura de arquivos
Projeto em outputs/comic-tv/. Configuração na raiz e app/build.gradle.kts; código em app/src/main/java/com/pelican/comictv/.
- MainActivity.kt, AppContainer.kt: entrada e dependências.
- model/Comic.kt, model/ComicPageProvider.kt: identidades e contrato de páginas.
- archive/ZipComicArchive.kt, archive/NaturalOrder.kt, archive/PageCache.kt: índice, ordem e cache.
- library/LibraryRepository.kt, library/LibraryDatabase.kt: importação e Room.
- ui/ComicTvTheme.kt, ui/ComicTvApp.kt: tema e navegação.
- library/LibraryScreen.kt, library/LibraryViewModel.kt: coleção e foco.
- details/DetailsScreen.kt: capa, informações disponíveis e retomada.
- reader/ReaderState.kt, reader/ReaderViewModel.kt, reader/ReaderScreen.kt, reader/ReaderTools.kt: leitura, controles e ferramentas.
- README.md: execução, limites e estado real de validação.

## Task 1: Projeto Android e navegação TV
**Files:** configuração Gradle, manifesto, MainActivity, AppContainer, ui/* e recursos de launcher.
**Interfaces:** ComicTvApp(container: AppContainer); tema compartilhado por todas as telas.
- [ ] Consultar versões oficiais e registrar matriz JDK/Gradle/AGP/Kotlin/SDK; localizar ou preparar ferramentas necessárias.
- [ ] Criar configuração reproduzível, wrapper Gradle e manifesto exclusivo de TV com touchscreen opcional.
- [ ] Implementar tema e shell de navegação, biblioteca vazia e rota de importação; não simular fontes conectadas.
- [ ] Executar assembleDebug; verificar launcher, foco inicial e Back em emulador quando disponível.
- [ ] Registrar resultado e commit local se o projeto estiver sob Git.

## Task 2: Índice ZIP e cache seguro
**Files:** model/*, archive/*; testes em app/src/test/java/com/pelican/comictv/archive/.
**Interfaces:** ComicPageProvider.pages(comicId: String): List<PageRef>; materialize(comicId: String, page: PageRef): File; operações suspend. PageRef(index: Int, key: String). ZipComicArchive.index(file: File): List<PageRef>.
- [ ] Criar testes NaturalOrderTest e ZipComicArchiveTest: 1,2,10; ignorar __MACOSX; rejeitar ../ e caminhos absolutos; detectar ZIP inválido, sem imagens e acima de limites.
- [ ] Rodar testDebugUnitTest e confirmar falha por ausência das implementações.
- [ ] Implementar assinatura ZIP, filtragem JPEG/PNG/WebP, ordenação determinística e extração com contagem efetiva de bytes. Gerar nomes internos de cache em vez de reutilizar caminhos das entradas.
- [ ] Testar limite exato de 32 MiB e excesso de um byte; testar LRU com quota reduzida injetável, mantendo a página protegida. Injetar erro de escrita para representar disco cheio.
- [ ] Executar testDebugUnitTest até passar; registrar resultado e commit.

## Task 3: Importação, persistência e biblioteca
**Files:** library/*, details/DetailsScreen.kt e integração AppContainer; testes de repositório e testes instrumentados Room.
**Interfaces:** Comic(id: String, title: String, pageCount: Int, currentPage: Int); LibraryRepository.observeComics(): Flow<List<Comic>>; importComic(uri: Uri): Comic; saveProgress(id: String, page: Int); getComic(id: String): Comic.
- [ ] Testar importação interrompida sem registro persistido, IDs estáveis e página restaurada limitada ao intervalo válido. Confirmar falha inicial.
- [ ] Implementar cópia temporária limitada, validação e promoção com limpeza em caso de falha; reconciliar originais órfãos após interrupção do processo.
- [ ] Implementar Room e miniatura limitada da primeira página; capas não carregam páginas completas na grade.
- [ ] Criar biblioteca com Continuar lendo e coleção, tela de detalhes e importação SAF; mostrar mensagem se não houver Activity capaz de escolher arquivo.
- [ ] Restaurar foco por ID após retorno; testar retorno a item fora da primeira tela, biblioteca vazia e atualização de progresso sem roubar foco.
- [ ] Rodar testes JVM e instrumentados disponíveis; registrar separadamente os que não puderem executar.

## Task 4: Leitor, zoom e retomada
**Files:** reader/* e integração de navegação; testes ReaderStateTest e ReaderViewModelTest.
**Interfaces:** ReaderState(page: Int, pageCount: Int, zoom: Float, mode: ReaderMode, fit: FitMode); ReaderMode: Reading, Tools, Pan. ReaderViewModel.onAction(action: ReaderAction).
- [ ] Testar primeira/última página, RTL, zoom entre 1x e 3x em passos de 0,25, Back por contexto e redefinição do zoom ao trocar página. Confirmar falhas iniciais.
- [ ] Implementar estado, renderização e navegação; direita/esquerda para páginas, cima/baixo para zoom, OK para ferramentas. Pan explícito usa quatro setas e limita deslocamento à imagem.
- [ ] Implementar ferramentas de ajuste, direção e mover página. Salvar progresso ao mudar página e preservar edição em SavedStateHandle.
- [ ] Testar que requisições antigas canceladas não substituem a página mais recente; erro de extração permite tentar novamente.
- [ ] Testar leitor e biblioteca juntos com arquivo real em dispositivo disponível; rodar assembleDebug e testDebugUnitTest.

## Task 5: Entrega verificável do incremento
**Files:** README.md, relatório de validação e APK apenas se compilado.
- [ ] Verificar que telas seguem o conceito aprovado, com texto legível, foco visível e ferramentas acessíveis por controle.
- [ ] Exercitar importação, leitura, retomada e retorno de foco; registrar dispositivo/API e resultados.
- [ ] Documentar build, instalação, controles, limites e pendências. Sem SDK/emulador/TV, indicar precisamente quais verificações faltam.
- [ ] Entregar fontes e, quando houver build aprovado, APK debug identificado como tal.

## Incrementos seguintes — planos próprios
1. Ferramentas adicionais: marcadores, ir à página, rotação, fundo e galeria; confirmar escopo antes de construir.
2. Motor Mihon: revisar mihonsupport na tag nightly_2026_10_16, dependências e licenças; preservar cliente/cabeçalhos por fonte; testar assinatura e resolver páginas reais.
3. Gerenciador: múltiplos repositórios HTTPS, índices compatíveis, instalação via fluxo Android, atualização, confiança e fontes habilitadas. Validar dois repositórios disponíveis e fontes representativas; HQ Now quando operacional.
4. Importação Wi-Fi/QR, outros arquivos e zoom por tiles. Web/FTP/Night Mode precisam de definição de comportamento.

## Estado do ambiente
Em 22/10/2026, Git, Node e adb foram localizados. Java, Gradle e SDK Android não foram encontrados nas verificações iniciais. Nenhum código Android foi compilado ou executado neste projeto.

## Atualização: plano dos modos de transferência
Esta seção substitui a pendência anterior de definição de Web/FTP. Os três modos passam a integrar o escopo planejado do produto, após o fluxo local. Não são tratados como funções opcionais esquecidas; serão subsistemas com implementação e verificação próprias.

### Transferência A: HTTP na rede local
**Files planejados:** import/ImportScreen.kt, import/TransferSession.kt, import/LocalUploadServer.kt, import/LocalAddressResolver.kt, import/UploadPage.kt.
**Contrato:** o transporte entrega um arquivo temporário completo ao mesmo importador do incremento local; o importador valida e promove o arquivo.
- [ ] Implementar sessão temporária com iniciar/parar, URL real, QR e acesso por navegador no celular/computador.
- [ ] Mostrar status do servidor, clientes conectados e progresso; funcionar em Wi-Fi e Ethernet.
- [ ] Verificar upload real, cancelamento, arquivo inválido, limite de 1 GiB, falta de espaço e rede sem acesso entre clientes.
- [ ] Encerrar sessão e limpar arquivos incompletos ao parar; impedir novas requisições de sessão encerrada.

### Transferência B: servidor FTP na TV
**Files planejados:** import/FtpUploadServer.kt, import/FtpTransferScreen.kt e testes de isolamento de diretório.
- [ ] Definir autenticação e selecionar biblioteca Android mantida após verificar compatibilidade e licença. Login anônimo aparece na referência, mas ainda não é uma decisão do produto.
- [ ] Implementar iniciar/parar, endereço/porta, QR e dados de acesso; restringir operações à área temporária de importação.
- [ ] Promover arquivos apenas após upload concluído e validação pelo importador compartilhado.
- [ ] Testar cliente FTP real, modo passivo, interrupção, nomes inseguros, isolamento da biblioteca e encerramento da sessão.

### Transferência C: portal web com pareamento
**Files planejados:** import/WebTransferScreen.kt, import/WebTransferClient.kt; serviço e portal em projeto separado a especificar.
- [ ] Projetar backend próprio: criação/expiração de sessão, código de pareamento, renovação, transporte de arquivos, limpeza e limites. Definir hospedagem e custos antes de publicar serviço externo.
- [ ] Implementar portal para enviar arquivos e aplicativo de TV para parear por código/QR, acompanhar progresso e cancelar.
- [ ] Validar token expirado/renovado, isolamento entre TVs, transferência interrompida e envio fora da rede local conforme a topologia escolhida.
- [ ] Não usar domínio de terceiros das fotos nem prometer que o portal está disponível antes do backend estar implantado e testado.

### Design e verificação compartilhados
- [ ] Área Importar com Arquivo local, Rede local, FTP e Web, usando o tema escuro/âmbar aprovado.
- [ ] QR legível à distância, URL e instruções alternativas visíveis, foco inicial e retorno pelo controle.
- [ ] Todos os transportes compartilham limites, validação, mensagens e promoção para biblioteca; registrar quais foram executados em TV real.

## Estado da primeira entrega
Em 22/10/2026, foram implementados projeto Android, núcleo Kotlin, importação local, biblioteca, detalhes, leitor, zoom/deslocamento e persistência. Build debug e build dos testes instrumentados passaram. Os 20 testes JVM passaram, e Android Lint terminou com zero erros e 19 avisos documentados. A revisão independente teve seus problemas relevantes corrigidos e reavaliados.

A validação em TV/emulador e os testes instrumentados ainda estão pendentes. Transferências HTTP/FTP/Web e motor de extensões Mihon são incrementos futuros, sem implementação ativa nesta entrega. Consulte comic-tv/VALIDACAO.md para evidências e limitações.
