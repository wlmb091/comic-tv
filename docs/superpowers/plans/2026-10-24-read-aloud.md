# Read aloud implementation plan

> **For agentic workers:** Use superpowers:executing-plans inline; one independent final review.

**Goal:** Narrate local text EPUB/PDF with Android offline Portuguese voices and visible text following speech.
**Architecture:** Pure segmentation/cursor in core; PDF/session, persistence and orchestration behind testable interfaces in app; Android TTS adapter; dedicated narrated text screen reusing current reader navigation/progress.
**Tech stack:** Kotlin/Compose TV, coroutines, Android TTS, PDFBox-Android2.0.27.0.
**Spec:** docs/superpowers/specs/2026-10-24-read-aloud-design.md (user approved).

## Constraints

MinSdk26, Android TV11 target device; keep stable1.0/data/update certificate and USB. No OCR/cloud/voice cloning/background service or backup schema change. PDF64MiB/5,000pages/120,000chars/page/scratch128MiB/free64MiB; current+next2 text cache16MiB. No auto-start on restore/resume. Resume at segment start. Voices offline Portuguese only. Speeds0.75/1/1.25/1.5.

## Review focus

Late callbacks must not advance another page/book. Navigation/background/manual scroll/audio-focus loss must pause and release audio. PDF-only-image and extraction failure must remain distinct. Prefetch must not cross resource/lifecycle bounds. Persistent page/hash/segment must match text before resuming. Existing automatic reading must not compete.

## Tasks

- [x] **1 Core:** test then implement core/reader/NarrationText.kt: `SpeechSegment(start,end)`, `speechSegments(text,maxChars)`, digest, `SpeechCursor` with generation/id/cursor/range/pause/next/previous/done guards. Test Unicode/long paragraphs/boundaries/stale completions. Run core:test RED→GREEN.
- [x] **2 Sources/storage:** app/reader/narration/{NarrationContracts,PdfNarrationSource,NarrationStore}. Interfaces `NarrationSource(count,label,text(page),close)`, `NarrationSpeech(initialize,speak,stop,close,events)`, typed state/settings/position. Add pinned PDFBox dependency/resources+license. PDF session serialized worker/scratch finally, bounded writer, cleanup; file store atomic by hashed comicID. JVM store tests and native PDF text/image/limit tests (compile-only if no device).
- [x] **3 Controller:** app/reader/narration/NarrationController.kt with injected source/speech/store/scope; load/prepare, generation cancellation, explicit play, paragraph progression/autopage, pause/error/voice/rate/manual navigation, current+next2 LRU, serialized persistence and release. Fake-engine/source JVM tests prove pause/stale callbacks/no autostart/missing voice/errors/prefetch/close.
- [x] **4 Android/UI:** AndroidNarrationSpeech offline voice enumeration/TTS/audiofocus; manifest service query. NarratedReaderScreen highlighted AnnotatedString/autoscroll and D-pad controls. ReaderTools+EpubTextScreen entry, ReaderScreen host, ReaderViewModel text source/progress handoff with no unnecessary image rendering in narrated mode, root passes local PDF original only. Pause existing automatic reading and anticipation; lifecycle pause; return to visual reader. Native control tests compiled.
- [x] **5 Delivery:** full JVM/debug/AndroidTest compilation/lint, fresh review and fixes, release APK/signature/ABI/SQL,1.1.0/code39 notes/source archive/report/private main. No native voice performance claim without Box test.

Ruling: execute using the approved design and existing in-place feature/comic-tv checkout; no extra approval for routine implementation choices. Only local imported EPUB/PDF provide text, so the online image/extension repository interface remains unchanged. Native integration requires manual voice availability testing on Box.

## Approved translation addition

LAN-only LibreTranslate client/source/configuration in same release; private IPv4, no proxy/redirect/cloud, bounded and cancellable current+next2 translation, separate original/translated positions. Server Compose+guide delivered separately, Docker not installed automatically. See local-translation-addendum.md. Client, source and variant tests passed; native dialog/PDF tests compiled.
