# Direct chapter shortcut correction plan

User request fully specifies presentation: previous/next should open neighbor directly, Chapters/editions keeps menu. Existing routes already resolve automatic neighbors but show picker while loading/on failure. Preserve selected sequence, trust, cancellation, progress and chapter identities.

- [x] Inspect ReaderScreen close→step route→OnlineEditionsScreen auto-open and existing VM neighbor tests.
- [x] Write native loading/no-list and boundary/no-list regressions before presentation change. ADB unavailable; no executable UI RED claim.
- [x] Render loading/message/back/retry only for nonzero initialStep; step0 keeps existing picker. No source order heuristic changes.
- [x] Full tests/builds/lint/native compilation, fresh review and final signing1.1.7/code46 with existing update cert. ActualBoxshortcut/latency still manual.
