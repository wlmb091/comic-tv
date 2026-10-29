package com.pelican.comictv.sources

import java.net.URI

/** Source-provided title artwork can use an old HTTP URL; always request it through TLS.
 * Raw path/query bytes are retained, including escaped characters in signed URLs.
 */
fun normalizeTitleCoverUrl(value: String?): String? {
    if (value == null || value.length !in 1..4096 || value != value.trim()) return null
    val parsed = try { URI(value) } catch (_: Exception) { return null }
    val scheme = parsed.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https") return null
    if (!parsed.isAbsolute || parsed.isOpaque || parsed.host.isNullOrBlank() || parsed.rawUserInfo != null) return null
    if (parsed.port != -1 && parsed.port !in 1..65535) return null
    val authority = parsed.rawAuthority ?: return null
    val secureAuthority = if (scheme == "http" && parsed.port == 80) authority.substringBeforeLast(':') else authority
    return buildString {
        append("https://"); append(secureAuthority); append(parsed.rawPath.orEmpty())
        parsed.rawQuery?.let { append('?'); append(it) }
        parsed.rawFragment?.let { append('#'); append(it) }
    }
}
