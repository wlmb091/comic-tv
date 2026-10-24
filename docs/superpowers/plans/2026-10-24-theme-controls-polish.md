# Theme controls polish implementation plan

> **For agentic workers:** Execute inline with superpowers:executing-plans.

**Goal:** Distinguish selected tabs/options from remote focus consistently.
**Architecture:** Shared Compose TvChoice, consuming existing palette tokens. Keep action TvButton unchanged. No navigation or persistence changes.
**Tech Stack:** Kotlin and existing Compose TV Material.
**Spec:** docs/design/DESIGN.md, approved selected/focused distinction and user authorization to continue the visual consistency phase.

## Constraints and review focus

Preserve Android 8 minimum, theme presets, focus requester modifiers, font scaling, selection callbacks and existing stored preferences. Selected content must remain readable in every preset/high contrast. Never infer selected semantics for ordinary primary action buttons.

## Task

- [x] Prepare Compose tests proving choice selection changes only on activation and is independent of remote focus; compile before implementation to confirm missing symbol.
- [x] Add TvChoice(text, selected, onClick, modifier, compact) with selected semantics, accent outline/text, neutral panel, strong focused fill and no scale growth. Compact variant retains header dimensions.
- [x] Adopt in library tabs/filters and Settings section/theme/text choices. Preserve all callbacks/keys. Leave ordinary actions untouched.
- [x] Run app JVM tests, assembleDebug/assembleDebugAndroidTest/lintDebug; tests on native device remain pending.
- [x] Independent read-only review, update docs, package 0.12.3 using existing signing key and authorized publishing workflow.

Ruling: Incremental refinement of approved visual language, not a new layout. Existing palette contrast tests cover accent/panel and action/onAction pairs; no new blended selected background is introduced.
