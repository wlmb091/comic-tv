# Biblioteca, coleções e armazenamento — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrar favoritos, coleções, busca local e limpeza segura de caches às telas atuais.

**Architecture:** Room 3 conserva favoritos e vínculos de coleções com migração aditiva. Regras puras de nomes/busca e armazenamento limitado ficam em core; ViewModels/Compose integram estado e D-pad, com o mutex existente do repositório protegendo limpeza.

**Tech Stack:** Kotlin, Compose TV, Room, coroutines/StateFlow, JUnit e SQLite do Node; nenhuma dependência nova.

**Spec:** docs/superpowers/specs/2026-10-24-library-organization-storage-design.md (aprovado pelo usuário).

## Global Constraints

- Preservar Android 8/API26, biblioteca, histórico, progresso, preferências e fontes existentes.
- OK longo de 1 segundo; todas as ações acessíveis por D-pad e temas/texto ampliado.
- Busca local por título sem diferenciar acentos/caixa; coleções de 1–40 caracteres, nomes normalizados únicos.
- Limpar somente páginas/miniaturas conhecidas; preservar originais, capas permanentes, banco e descritores online; não seguir links.
- Sem IA, backup, download offline, agrupamento automático ou alteração do motor do leitor.

## Review Focus

- Migração de uma biblioteca antiga com histórico oculto e progresso deve preservar todos os valores.
- Remover favorito/coleção do filtro ativo pode eliminar a capa focada; restaurar foco em um controle estável.
- Cancelar seleção múltipla não altera os vínculos; excluir coleção nunca exclui a edição.
- Cache com symlink, falha parcial ou materialização concorrente não pode apagar outros dados.
- Busca vazia/sem resultado e texto de 130% não devem impedir sair ou acessar controles.

### Task 1: Dados e regras de organização

**Files:** core/library/LibraryOrganization.kt e testes; app/library/LibraryDatabase.kt, CollectionDao.kt, LibraryRepository.kt, AppContainer.kt; tools/check-library-organization.mjs.

**Interfaces:** normalizeLibraryText(String): String; validatedCollectionName(String): Pair<String,String>; LibrarySearchEntry(id,title,favorite); filterLibrary(entries,query,filter,membership): List<String>. CollectionEntity(id,name,normalizedName), CollectionMembership(comicId,collectionId); repositório observeCollections/observeMemberships/setFavorite/createCollection/renameCollection/deleteCollection/setCollections.

- [ ] Testar normalização, nomes reservados/limites e interseção busca/favoritos/coleção; executar core:test e observar falha antes da implementação.
- [ ] Implementar regras e Room 2→3 (favorite, coleções e vínculos indexados/FK); registrar migração e adaptar fake DAO existente.
- [ ] Testar SQL real para cadeias 1→2→3/2→3, unicidade, cancelamento transacional e exclusão mantendo comics/progresso.
- [ ] Executar testes e compilar app; registrar resultado e commit.

### Task 2: Biblioteca e organização por controle

**Files:** LibraryViewModel.kt, LibraryScreen.kt, HistoryOptions.kt, novos LibraryControls.kt/CollectionDialogs.kt, DetailsScreen.kt, ComicTvApp.kt; testes de filtros/restauração e instrumentação.

**Interfaces:** consulta e chave de filtro em SavedStateHandle; dados persistentes observados do repositório; diálogos usam callbacks onDismiss e invalidam lista por Flow.

- [ ] Testar estado de consulta/filtro, coleção removida e resultado vazio antes de implementar estado.
- [ ] Acrescentar busca por ação explícita, faixa Todas/Favoritos/coleções, contador/mensagem vazia e restauração de estado/foco.
- [ ] Implementar favorito e seleção múltipla Salvar/Cancelar em menu de OK longo e detalhes, gerenciamento CRUD com confirmação e nomes validados.
- [ ] Testar cancelamento/seleção no estado puro e preparar testes nativos dos diálogos/foco; executar JVM e compilar UI; registrar commit.

### Task 3: Armazenamento limitado aos caches

**Files:** core/storage/LibraryStorage.kt e testes; LibraryRepository.kt; novos StorageViewModel.kt/StorageSettings.kt; SettingsScreen.kt/ComicTvApp.kt.

**Interfaces:** StorageReport com bytes por categoria e espaço livre; CacheSelection(Pages,Thumbnails,Both); CacheCleanup com bytes removidos/falhas. repository.storageReport()/clearReaderCaches(selection) suspend no mutex do repositório.

- [ ] Testar cálculo, cache ausente, links fora dos alvos, preservação de originais/capas e falha parcial antes da implementação.
- [ ] Implementar varredura sem links e limpeza somente em diretórios permitidos; capturar tamanho antes de excluir, confirmar resultado, invalidar memória Coil após limpeza.
- [ ] Integrar aba Armazenamento, atualização explícita, cálculo em IO, confirmação com Cancelar focado e bloqueio durante operação; testar estado/erros.
- [ ] Executar testes, build e revisão final independente; corrigir achados importantes com regressões.

### Task 4: Entrega verificável

**Files:** README.md, VALIDACAO.md, AGENTS.md, versão app, APK/ZIP somente em outputs.

- [ ] Executar core:test, mihon:testDebugUnitTest, app:testDebugUnitTest, assembleDebug, assembleDebugAndroidTest e lintDebug.
- [ ] Verificar assinatura/versionamento/ABI e scripts de migração/organização; conferir dispositivos ADB e declarar instrumentação não executada se ausente.
- [ ] Atualizar documentação, registrar decisões, normalizar LF, diff --check e commit; empacotar APK e fonte, publicar no repositório privado autorizado e verificar HEAD remoto.

## Execução e registro

Implementação nativa nesta sessão, na branch de desenvolvimento existente feature/comic-tv, sem nova escolha de abordagem após a aprovação do desenho. Ledger: work/library-organization-progress.md no workspace principal. Scripts auxiliares da skill dependentes de shell POSIX serão substituídos por registro PowerShell equivalente neste ambiente Windows.
