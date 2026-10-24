# Home polish implementation plan

> **For agentic workers:** Execute inline with superpowers:executing-plans.

**Goal:** Refine the approved home composition for television viewing.
**Architecture:** Target existing Compose layout only. Preserve grid keys, focus restoration, theme tokens and data flows.
**Tech Stack:** Kotlin, Compose for TV, existing Coil.
**Spec:** docs/design/DESIGN.md and approved-home.png, with subsequent user refinements taking precedence.

## Constraints

Android 8 minimum; physical target Android TV 11. No added dependencies, no cover backdrop on home, neutral unfocused actions, original cover proportions, existing filters retained.

## Review focus

Long titles and enlarged text must expand vertically. Hero actions must wrap instead of overflowing. High contrast suppresses decorative artwork. Focus keys and grid item count must remain unchanged. Empty library retains import action.

## Task: refine existing home

- [x] Give grid edges 12 dp horizontal/16 dp vertical safe space; header separation 18 dp.
- [x] Hero minimum height 204 dp, content padding 24 dp horizontal/22 dp vertical. Add existing ProgressTrack for in-progress editions, width bounded to 240 dp. Use FlowRow for wrapping actions.
- [x] Recent cards 288 dp, 10 dp padding, 44x64 dp full cover, two title lines. Bold section headings. Keep collection cover dimensions unchanged to avoid pushing content unnecessarily.
- [x] Build debug and AndroidTest APK, run app JVM tests and lint. No new tests for low-impact spacing-only changes; do not claim native screenshot verification without a device.
- [x] Review diff, sign/check version, package 0.12.2 and update validation/manual ledger.

Ruling: This is a small refinement of the already approved design, authorized by the user's instruction to proceed, rather than a new design proposal. Preserve installed data and USB storage.
