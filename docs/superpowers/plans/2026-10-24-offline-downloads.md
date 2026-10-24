# Downloads offline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Baixar edições completas e ler sem rede/fonte, separado do cache descartável.
**Architecture:** Store/manifestos verificados em core, fila persistente de um worker por tela, repositório híbrido local-first; estado Compose coordena pause/join antes de navegar.
**Tech Stack:** Kotlin, coroutines, AtomicFile/Gson, Compose e motor Mihon existentes.
**Spec:** docs/superpowers/specs/2026-10-24-offline-downloads-design.md, aprovado.

## Global Constraints
- 100itens únicos na fila;1edição/página por vez;5000páginas,32MiB/página,100MP,1GiB/edição,reserva64MiB.
- Pausar/aguardar ao sair/segundo plano; não retomar automaticamente. Downloads privados permanentes fora de cacheDir.
- Offline completo não consulta fonte/extensão/rede. Limpar cache não remove download; remoção confirmada não perde dados de leitura.

## Review Focus
- Manifesto completo com arquivo ausente/corrompido deve ser invalidado sem acessar rede silenciosamente.
- Mudança da lista na retomada não mistura sequências; falha parcial não substitui completo.
- Pause/remove/retomar e navegação não deixam engine trabalhando após recarga da extensão.
- Hash/caminhos/links/limites e bytes efetivos protegem diretórios externos e espaço livre.
- Backup restaurado não fabrica selo offline sem arquivos.

### Task 1: Store e fila
**Files:** core/offline/OfflineStore.kt e testes; app/offline/DownloadRepository/DownloadsViewModel.
**Interfaces:** manifest/página/queue; stage, verify, complete, remove; enqueue/pauseAndJoin/resume.
- [x] Escrever RED para manifesto parcial/corrompido, cancelamento/retomada, mudança de sequência, limites e exclusão seletiva.
- [x] Implementar store atômico, hashes, fila persistente e fonte falsa nos testes; rodar GREEN.

### Task 2: Fonte e leitor
**Files:** OnlineReaderRepository/OfflineReaderRepository, AppContainer/ComicTvApp, LibraryRepository.
- [x] Testar que leitura completa funciona com fonte que falha em qualquer chamada, miniaturas regeneram offline e progresso permanece.
- [x] Integrar exportação da lista/fetch da extensão, validação de imagens e adaptador offline-first, sem promover parciais.

### Task 3: UI e entrega
**Files:** DownloadsScreen, DetailsScreen/HistoryOptions/LibraryScreen/StorageSettings, docs/versionamento.
- [x] Integrar fila/progresso/pausa/remover/retomar, selos e armazenamento offline, confirmações e foco.
- [x] Executar JVM/build/instrumentação compilada/lint, revisão independente dos dois planos, correções com regressões, assinatura/ABI/migrações e empacotar APK/fonte.
- [x] Documentar limites reais e TV não executada se ADB ausente; commits/push no remoto privado já autorizado e verificação de HEAD.
