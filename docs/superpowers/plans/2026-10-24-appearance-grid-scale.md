# Appearance grid and scale correction plan

> **For agentic workers:** Execute inline with superpowers:executing-plans.

**Goal:** Compact theme choices into an adaptive grid and adjust interface text using left/right on one horizontal control,85–130% in5% steps.
**Architecture:** Existing Settings screen and palette, unchanged routes. Add optional percentage alongside legacy size0/1/2; preserve old preferences/backups and validate new backup values. Sequential settings changes derive from the latest saved state so rapid arrows are not discarded by the busy flag.
**Tech stack:** Existing Kotlin/Compose/Room/Gson, no new dependency.
**Spec:** User photos and follow-ups; below100% allowed, max130%; current approved design remains binding.

## Tasks and acceptance

- [x] Test legacy100/115/130 mapping, all percentage steps/clamping and preservation through backup codec. Reject malformed/out-of-range backup percentages.
- [x] Add optional textPercent preference/backup field, derived percentage and immutable adjustment method. Keep legacy textSize for fallback and existing tests.
- [x] Add focusable horizontal percentage control with left/right steps, Up/Down focus escape, accessible range/setProgress semantics. Global theme updates after successful persisted change; no dropped repeated arrows while screen is active.
- [x] Convert settings content to adaptive LazyVerticalGrid: full-span section rows, one theme per cell with preview/name. Stable keys preserve slider focus during scale changes.
- [x] Compile native control tests, run core/app tests and lint/build. Review changes including prior reader icon correction.
- [x] Accumulate changes for next APK together with further photos; no version bump or publication until photo batch is complete.

## Review focus

Old preference keys/backups must load identically. Optional field must not weaken strict JSON checks. Backup appearance roundtrip must preserve85–130%. Focus/scroll remain usable when grid column count changes. No rapid-arrow loss from busy guard. Settings changes must preserve unrelated appearance fields.

Ruling:85% lower bound and5% step were stated before implementation; the user authorized proceeding. New optional percent is emitted only when used; restoring new percentages requires the updated application. Native UI tests are compile-only without ADB.
