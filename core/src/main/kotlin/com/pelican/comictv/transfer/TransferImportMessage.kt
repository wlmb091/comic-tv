package com.pelican.comictv.transfer

/** Import feedback overrides byte progress, but must not mask a new transfer or a server error. */
fun transferImportMessage(previous: TransferStatus, next: TransferStatus, current: String?): String? = when {
    next.activeUploads > previous.activeUploads -> null
    next.completed > previous.completed -> current
    next.message != previous.message -> null
    else -> current
}
