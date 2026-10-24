# Backup/restauração Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Exportar/restaurar dados locais versionados sem excluir a biblioteca, por seletor Android e sessão LAN.
**Architecture:** Codec/validação/merge puros em core; repositório coordena transação Room e journal AtomicFile recuperável, descritores online sem URLs e pendências locais por hash. Compose configurações expõe prévia e confirmação.
**Tech Stack:** Kotlin, Gson, Room, coroutines, NanoHTTPD e Compose existentes.
**Spec:** docs/superpowers/specs/2026-10-24-backup-restore-design.md, aprovado.

## Global Constraints
- API26+, sem dependência nova/aplicativo externo; backups de dados sem originais/páginas/credenciais/confiança.
- JSON32MiB,20000edições,2000coleções,100000vínculos; nomes/tipos/IDs/referências/inteiros validados antes da transação.
- Mesclar por data sem apagar registros/vínculos; favoritos unidos; local ausente pendente até reimportar hash idêntico; cancelar prévia não grava.

## Review Focus
- JSON com tipos coercíveis, duplicatas, profundidade excessiva ou referências órfãs deve ser recusado.
- Progresso atual mais recente e coleção de mesmo nome devem sobreviver a restauros repetidos.
- Interrupção entre banco/preferências/pendências deve recuperar por journal idempotente.
- Original local ausente não deve virar entrada quebrada; reimportação correta vincula progresso.
- Sessão LAN valida Host/Origin/token/tamanho e nunca aplica restauração sem confirmar na TV.

### Task 1: Modelo e codec
**Files:** core/backup/BackupCodec.kt, BackupModels.kt, BackupMerge.kt e testes.
**Interfaces:** BackupDocument/BackupComic/BackupCollection/BackupLink/BackupAppearance; BackupCodec.encode/decode; mergeComic(current,incoming).
- [ ] Escrever testes roundtrip, limites/tipos/duplicatas e merge por data; rodar e observar RED.
- [ ] Implementar validação estrita/limitada, serialização e regras de conflito; executar GREEN.

### Task 2: Persistência/restauro
**Files:** app/backup/BackupRepository.kt, RestorePendingStore.kt; LibraryDatabase/CollectionDao/LibraryRepository, preferências, OnlineReaderRepository/AppContainer.
**Interfaces:** prepareBackup():File, preview(File):BackupDocument, restore(document,appearance):RestoreResult, recover(); pending lookup por SHA256.
- [ ] Testar mesclagem/pendências/rollback/journal e reimportação em memória/arquivos temporários antes das alterações.
- [ ] Implementar DAO de restore em transação, journal atômico, descritor online sem URLs/sem confiança automática e preferências verificadas.
- [ ] Integrar identidade pendente a importação e backup de pendências; rodar JVM e preparar Room nativo.

### Task 3: Interface e transferência
**Files:** BackupViewModel/BackupSettings/BackupTransferServer e testes; SettingsScreen/ComicTvApp.
- [ ] Testar preview não aplica, confirmação aplica, sessão LAN upload/download expira/valida token/Host/Origin/limite.
- [ ] Implementar seletor SAF, prévia/confirmação, opção aparência, sessão local temporária com QR e encerramento por ciclo de vida.
- [ ] Rodar testes/build/lint, registrar resultado e commit; continuar plano offline já aprovado.

## Execução
Inline na branch feature/comic-tv existente. Ledger em work/backup-offline-progress.md; aprovações de desenho já recebidas, continuar sem nova pergunta para decisões rotineiras. Helpers POSIX substituídos por PowerShell no Windows.
