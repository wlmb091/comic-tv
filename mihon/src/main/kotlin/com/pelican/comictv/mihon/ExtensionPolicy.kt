package com.pelican.comictv.mihon

import java.security.MessageDigest

/** Pure policy functions, kept separate so trust is checked before any APK constructor runs. */
object ExtensionPolicy {
    fun classNames(packageName: String, metadata: String?): List<String> = metadata.orEmpty()
        .split(';').map(String::trim).filter(String::isNotEmpty)
        .map { if (it.startsWith('.')) packageName + it else it }
    fun fingerprint(certificate: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(certificate).joinToString("") { "%02x".format(it) }
    fun normalizeFingerprint(value: String): String = value.replace(":", "").lowercase().also {
        require(it.matches(Regex("[0-9a-f]{64}"))) { "Invalid SHA-256 certificate fingerprint" }
    }
    fun requireTrusted(actual: Set<String>, expected: String) {
        val pin = normalizeFingerprint(expected)
        require(actual.isNotEmpty() && actual.all { normalizeFingerprint(it) == pin }) { "Extension certificate does not match the trusted pin" }
    }
    fun supported(version: String?): Boolean = version?.toDoubleOrNull()?.let { it >= 1.2 && it <= 1.6 } == true
}
