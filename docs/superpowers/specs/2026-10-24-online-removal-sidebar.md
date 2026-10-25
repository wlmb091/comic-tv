# Online deletion and sidebar — approved

User explicitly requested deletion also for source editions and collapse sidebar after confirming a destination; expand only when returning to sidebar icons. Existing manual direction/source language/AllManga changes preserved.

Online deletion uses confirmation in library OK-long menu/details, default Cancelar. Explain removal of this edition's library data, downloaded offline package and queue entry, record and caches; title remains available in source catalog. Preserve other editions/source configuration/trust. Serialize with download worker, stop persistent queue entry before deleting row, clear live online session. If cleanup cannot finish report partial cleanup rather than claim data retained. No network/source calls to delete. Imported deletion unchanged.

Sidebar confirmation sends focus into destination content after composition and does not force selected sidebar icon. Same-section click also moves into content. Returning by D-pad left expands naturally. Preserve startup focus/accessibility/large font.

Test queue/disk removal and other-edition preservation; native Room/online/dialog/sidebar tests compile without physical TV. Release1.1.2/code41, update signature unchanged.
