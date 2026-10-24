# Details chapter selection implementation plan

> **For agentic workers:** Execute inline with superpowers:executing-plans.

**Goal:** Choose chapters from an online edition's details without entering its reader first.
**Architecture:** Add optional details action and reuse OnlineEditionsScreen/ViewModel. Query origin distinguishes details versus reader; chapter loading/opening and trust checks remain unchanged.
**Spec:** User photo and request before final0.12.5 packaging.

## Task

- [x] Native test: returning from details-origin list, including source failure, calls Back without opening reader/chapter; compile RED absent backLabel.
- [x] Details action below Continue, only for online entries. Keep first focus on Continue and unrelated actions unchanged.
- [x] Route defaults to reader for existing links/step shortcuts. Details origin returns via popBackStack, uses cover not stale reader preview and labels Back/preview correctly. Chapter selection opens chosen reader through existing callback.
- [x] Run full build/JVM/lint and compile native tests, independent review, then include in authorized0.12.5 package.

Review focus: hardware Back and visible Back share exit callback; failed source load still permits return; local archives do not pretend to have a series; reader shortcuts preserve defaults; no page load/progress mutation merely for browsing chapters.
