# TV text input activation plan

User request is fully specified: D-pad focus only highlights, OK/click activates keyboard everywhere; preserve existing field values/styles/actions/password masking. Direct bugfix, no new design choices or approval gate. Pending cover/navigation performance proposal is outside this release.

- [x] Trace String BasicTextField auto-focus and two explicit show-on-open effects. Verify cached Compose APIs/official documentation before selecting state-based fields.
- [x] Add native tests before component, observe compile RED; implement shared TvTextInput state synchronization and explicit activation, connect all three input sites, remove auto-show effects.
- [x] Full JVM suites/builds/lint/native compilation, one fresh review, fixes if important; native focus/actual keyboard runtime remains manual.
- [x] Package1.1.6/code45 with same update signature, notes/validation and publish authorized private main.

Activation enables showKeyboardOnFocus only after OK, thereby starting the input session; delayed explicit show checks activation still holds after the frame. Native pointer handling starts its own tap session. Focus loss resets activation. Keep password hidden with BasicSecureTextField and retain Search/Done callbacks. No new dependencies.

Live search user addition completed per docs/superpowers/specs/2026-10-25-live-search-addendum.md; new-scope review and owner/mode fixes verified in final suite.
