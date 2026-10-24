# TV large text implementation plan

> **For agentic workers:** Execute inline with superpowers:executing-plans.

**Goal:** Preserve access to library header and sidebar controls with enlarged text.
**Architecture:** Existing Compose layout, adaptive library header and scrollable sidebar. Navigation and storage unchanged.
**Tech Stack:** Existing Kotlin/Compose TV.
**Spec:** docs/design/DESIGN.md: text scaling, legibility, D-pad navigation, sidebar collapsed on entry.

## Constraints and review focus

Keep focus requesters and grid keys unchanged. All header choices stay composed (no lazy-tab focus race). Use actual LocalDensity fontScale, including OS settings. Sidebar grows only while expanded, wraps labels and scrolls vertically on short screens. No new dependencies or inference of physical device success.

## Task

- [x] Prepare native tests for header separation/reachable choices at 130% and narrow width, and all sidebar entries at enlarged text/short height.
- [x] Extract LibraryHeader with existing callbacks and focus modifiers. Stack brand above actions below 760 dp × fontScale; use FlowRow in either case. Scale reserved wordmark width.
- [x] Expanded sidebar width scales from 228 dp up to 320 dp. Use scrollable contents, minimum instead of fixed button height, wrapping labels, unchanged collapsed width64 dp and navigation handlers.
- [x] Build/test/lint/sign/check metadata, independent review and package 0.12.4. Native UI execution remains pending.

Ruling: Target code-visible fixed dimensions identified in the accessibility review; do not change details summary scrolling without evidence of a new failure.
