# Branded Comic TV Home Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan inline.

**Goal:** Apply the approved native TV home and user-supplied identity, keeping configurable interface colors.

**Architecture:** Split selection logic, home header/hero, brand rendering and backdrop. Keep LibraryScreen's grid keys and restoration offsets stable. Preserve existing history/options and source/reader routes.

**Tech Stack:** Kotlin/Compose TV, existing Coil, Room and appearance preferences; Android API26+.

**Spec:** docs/design/DESIGN.md

## Constraints and review
- No new dependency, no database change, no fictional catalog data.
- Preserve collapsed startup and one-shot sidebar focus.
- Five portrait covers across typical 960dp TV width; full collection scrolls normally.
- Header/restoration must work with empty, hidden-history, long-title and enlarged-font states.
- Logo wordmark remains the supplied artwork; selectable accent colors remain semantic tokens.

## Tasks
- [x] Persist approved reference and official brand originals.
- [x] Implement actual featured-comic selection with JVM tests.
- [x] Add brand/native hero and default-versus-cover backdrop; apply icon/banner identity.
- [x] Integrate hero, compact resume cards and collection without changing restoration keys.
- [x] Verify tests, APK, lint, ABI and history SQL; document device limitations.

Review: hero actions now record focus before navigation. Hero uses content-driven height with decorative layers excluded from measurement; tabs grow with text, including system font scaling. High contrast disables decorative hero art.
