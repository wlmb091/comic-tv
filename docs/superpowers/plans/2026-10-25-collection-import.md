# Collection Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Import all editions inside ZIP/RAR collections through local, HTTP and FTP flows.
**Architecture:** Core bounded collection detector/extractor; suspend sequential runner in app; repository serializes received batches and deduplicates by SHA256 against local originals. Existing single-edition import/restore remains the publishing primitive.
**Tech Stack:** Kotlin, Junrar already present, java.util.zip, coroutines, Compose TV.
**Spec:** docs/superpowers/specs/2026-10-25-collection-import-design.md (approved).

## Global Constraints
1 GiB input; 100 editions; 2 GiB extracted metadata total including ignored entries; 5000 entries; no password/multivolume/links/traversal. Android11/USB preserved. No new dependencies or cloud.

## Review Focus
- Mixed page images and nested archives preserve existing single-HQ detection.
- Partial failure/cancellation must preserve committed entries and clean owned scratch only.
- Repeated/concurrent collection uploads must not duplicate original/progress.
- RAR solid replay must be bounded and cancellable.
- Network callback/session expiration must not expose stale import state.

### Task 1: bounded archive collection reader
Files core/archive/CollectionArchive.kt, RarComicArchive.kt and CollectionArchiveTest.kt.
Interface inspect(File): List<CollectionEntry>? and extract(File, CollectionEntry, File): File; entry(name,size).
- [x] Write failing tests for detection, natural sort/subfolders, mixed pages, malicious paths, limits, real RAR and interruption.
- [x] Run core:test RED, implement using existing RAR validation/extraction with collection-specific limits; GREEN.

### Task 2: sequential import and integration
Files app/library/CollectionImportRunner.kt, LibraryRepository.kt, LibraryViewModel.kt, transfer VMs/screens, ui/ComicTvApp.kt; app tests CollectionImportRunnerTest.kt.
Runner takes suspend importOne and cancellation-aware extraction/check-space callbacks; progress and summary model shared by UI.
- [x] Runner tests RED for per-entry failures, duplicates, cleanup and cancellation.
- [x] Implement and connect local/HTTP/FTP; repository dedupe checks local hashes under batch mutex, preserving restore logic. Add cancel button for local and summary/progress in network state.
- [x] GREEN core/app/mihon tests, compile Android tests, debug/release and lint.

### Task 3: review and package
- [x] Fresh whole-change review, one fix pass with regression tests for important findings.
- [x] Version1.1.5/code44 notes/validation, existing certificate signing/alignment/ABI/SQL verification.
- [x] Commit/publish authorized private main, sourceZIP and APK/report; physical Box performance explicitly unverified.
