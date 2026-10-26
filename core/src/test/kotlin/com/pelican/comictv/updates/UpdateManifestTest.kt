package com.pelican.comictv.updates

import org.junit.Assert.*
import org.junit.Test

class UpdateManifestTest {
    private val json = """{"schemaVersion":1,"applicationId":"com.pelican.comictv","versionCode":57,"versionName":"1.1.18","minSdk":26,"sizeBytes":1024,"sha256":"${"a".repeat(64)}","apkUrl":"https://github.com/wlmb091/comic-tv-updates/releases/download/v1.1.18/Comic-TV-1.1.18.apk","notes":"Atualização"}"""
    @Test fun validManifestRoundTripsAndOnlyHigherCompatibleVersionIsOffered() {
        val value = UpdateManifestCodec.decode(json.toByteArray())
        assertTrue(value.isNewerThan(56)); assertFalse(value.isNewerThan(57)); assertFalse(value.isNewerThan(58))
        assertEquals(26, value.minSdk); assertEquals(1024, value.sizeBytes)
    }
    @Test fun untrustedOrInconsistentMetadataIsRejected() {
        val invalid = listOf(
            json.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
            json.replace("\"versionCode\":57", "\"versionCode\":\"57\""),
            json.replace("\"versionCode\":57", "\"versionCode\":0"),
            json.replace("\"minSdk\":26", "\"minSdk\":-1"),
            json.replace("com.pelican.comictv", "com.other.app"),
            json.replace("https://github.com/", "http://github.com/"),
            json.replace("wlmb091/comic-tv-updates", "other/downloads"),
            json.replace("github.com/", "github.com.evil/"),
            json.replace("download/v1.1.18/", "download/v1.1.19/"),
            json.replace("\"sizeBytes\":1024", "\"sizeBytes\":67108865"),
            json.replace("\"sizeBytes\":1024", "\"sizeBytes\":1e3"),
            json.replace("a".repeat(64), "z".repeat(64)),
            json.replace(".apk\"", ".apk?token=secret\""),
            json.replace("\"notes\":", "\"unknown\":0,\"notes\":"))
        invalid.forEach { bytes ->
            try { UpdateManifestCodec.decode(bytes.toByteArray()); fail("Unexpectedly accepted invalid manifest") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun oversizedMetadataAndNotesAreRejected() {
        for (value in listOf(" ".repeat(65_537), json.replace("Atualização", "a".repeat(12_001)))) {
            try { UpdateManifestCodec.decode(value.toByteArray()); fail("Limit not enforced") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun overflowingIntegersNeverWrapIntoSupportedValues() {
        for ((field, small, large) in listOf(
            Triple("schemaVersion", "1", "18446744073709551617"),
            Triple("versionCode", "57", "18446744073709551673"),
            Triple("sizeBytes", "1024", "18446744073709552640"))) {
            val invalid = json.replace("\"$field\":$small", "\"$field\":$large")
            try { UpdateManifestCodec.decode(invalid.toByteArray()); fail("Overflow accepted for $field") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
