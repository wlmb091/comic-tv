# Browser sites and release 1.4.3

> Execution: native, user explicitly authorized completing the existing package without intermediate approvals.

Goal: add the spreadsheet links and six supplied sites, compact browser shortcuts with Ver mais, then publish all pending fixes.
Architecture: existing BrowserSites catalog, preserve original IDs and search providers; new links are manual browser shortcuts, not dozens of new automatic network queries. Home initially 12 sites; expanded responsive grid, compact icons, focus retained on toggle. No new dependencies.

- [x] Extract/deduplicate spreadsheet URLs; normalize Blogger country domains, retain actual href rather than potentially mistyped label; prefer HTTPS, report failures without replacing domains. Store provenance.
- [x] BrowserSites.kt + BrowserSiteUi.kt: add links, initial12/Ver mais, compact adaptive cards and icons; preserve existing search functionality and bounded network. Test catalog, URL encoding and provider limits.
- [x] Run focused JVM/Android compile/lint and JS tests; independent final review; fix blocking findings.
- [ ] Bump1.4.3/code77, release notes including MEGA, Downloads focus, reader loading/zoom. Commit/push authorized source once. Full checks once in existing CI and verify public APK digest/signature/version before reporting publication.

Review focus: D-pad on expanding grid; no hidden-site selection; HTTP-only legacy links; duplicate/country-domain links; network fanout; preserve previous pending reader/browser work. Physical Box acceptance remains manual.
