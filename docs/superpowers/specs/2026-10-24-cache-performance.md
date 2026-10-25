# Cache performance — approved

User confirmed delays in reading/timeline and library/catalog; approved reducing repeated cache scans, earlier thumbnails, nearby cover anticipation and adaptive RAM, preserving originals/reading quality.

Cache: scan on first access/mutation/failed validation/explicit trim, reuse quota result on unchanged hits; recheck unavoidable protected-file overage when protection changes. Keep forced public trim for external maintenance. Library foreground uses conditional trim rather than redundant full scans.

Reader: warm current thumbnail before waiting on next2 full pages; warm each next thumbnail as soon as its page is ready. Keep current+next2 anticipation, cancellation, budgets and original files. No extra parallel source calls.

Covers: same Coil loader as visible images, offscreen next3 (or1 on small/low-RAM heaps), debounce120ms, sequential and lifecycle-resumed only; derive target size from measured grid width, cap predecode width640. Visible image requests unchanged (never force lower-quality rendering). RAM loader budget8–48MiB, heap/6, low-RAM cap24MiB; fallback192MiB heap yields existing32MiB. Do not claim absent disk cache or alter Coil default disk behavior without evidence.

Tests prove scan counts/quota/protection, thumbnail priority before delayed prefetch, memory/window bounds; native controls/build/lint/release and independent review. No physical speedup claim without Box timing. Release1.1.4/code43.
