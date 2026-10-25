# Section focus correction

User requested first visible focus for every sidebar section, especially first title in Catalog/Recent. Existing neutral parent prevents sidebar expansion but leaves no highlighted control. Use the existing per-shell target registry, with a pending handoff while content loads. Cancel when the user leaves the neutral entry. Never activate an action merely by focusing it.

1. Add regression tests for pending readiness, cancellation, ownership and one-shot preparation; run RED, then implement registry and focus-safe modifier.
2. Register Library→Todas, Catalog/Recent→first title (empty result→Trocar fonte), no selected source→Buscar fonte, Extensions→Instaladas, Import→Escolher arquivo, Settings→Acessibilidade e aparência. Explicit section change may prepare the top; moving right out of the rail preserves existing scroll. Preserve detail-back restoration and explicit keyboard activation.
3. Compile native integration regressions, run JVM/build/lint and package 1.1.11 with unchanged update certificate. Fresh final review, fix important findings, publish authorized artifacts and report physical testing gap.

Review focus: late network response after user moved back to sidebar, source route overlap, saved scroll/Back restoration, empty results, keyboard and OK-release leakage.
