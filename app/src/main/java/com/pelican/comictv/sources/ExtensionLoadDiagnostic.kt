package com.pelican.comictv.sources

/** Preserve the underlying constructor/linkage error instead of an empty reflection wrapper. */
internal fun extensionLoadDiagnostic(error: Throwable): String {
    val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
    var cause = error
    repeat(16) {
        seen.add(cause)
        val next = cause.cause
        if (next == null || next in seen) return@repeat
        cause = next
    }
    val type = cause.javaClass.simpleName.ifBlank { cause.javaClass.name }
    val message = cause.message?.replace(Regex("[\\p{Cntrl}\\s]+"), " ")?.trim().orEmpty()
    return (if (message.isBlank()) type else "$type: $message").take(350)
}
