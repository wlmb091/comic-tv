# Chapter order controls — photo request

User requested buttons to reorder the source-details chapter list, currently newest/final first. Include in pending1.1.3 before finalizing extension queue.

Controls above list: Do início (ascending number), Do final (descending number), Ordem da fonte (original). Source default preserved until chosen; remember per title while navigating. Stable numeric ordering with fractional chapters, source-provided number preferred; explicit chapter-number title fallback when absent/default; unknown extras last and stable. Dedupe chapter URL, never change metadata/identity or mutate source list. Reset list scroll to top on order change, keep focus on control. Existing reader order controls remain independent. Tests core sorting and native control semantics compile, full verification and APK regenerated.
