# Imported file removal

User approved “adicionar” after concrete proposal: Excluir da biblioteca in one-second OK menu, confirmation, remove only app-owned imported copy and preserve external source. Also expose same action in local details. Existing history hiding remains separate; online editions excluded.

Implementation: stage owned original by rename, Room row delete (cascade collection links), finalize owned file/cache/cover deletion. Restore staged original on DB failure; startup recovers interruption from row existence. Cancellation cannot split commit once confirmed. No recursive deletion or external URI deletion. Confirmation initially focuses Cancelar, errors remain visible. Completed operation returns library with valid focus. Existing backups remain snapshots and can explicitly restore metadata later.

Validation: core staging/recovery/isolation/invalid-ID tests, native Room/external-source/cascade tests and confirmation focus/cancel tests (compile-only without Box). Full unit/build/lint/signature/ABI/migrations. Release1.1.1/code40, same update certificate.
