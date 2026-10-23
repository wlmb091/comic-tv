package com.pelican.comictv.sources

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class MihonRepoClientTest {
    private val base = "https://example.org/repo/"
    private val descriptor = """{"meta":{"name":"Test","signingKeyFingerprint":"${"ab".repeat(32)}"},"index_v2":"index.json"}"""
    @Test fun normalizesExplicitGithubBranch() {
        assertEquals("https://raw.githubusercontent.com/a/b/repo/repo.json", MihonRepoClient().normalize("https://github.com/a/b/tree/repo"))
        assertThrows(IllegalArgumentException::class.java) { MihonRepoClient().normalize("http://example.org/repo") }
    }
    @Test fun readsV2JsonAndRelativeResources() {
        val index = """{"extensionList":{"extensions":[{"name":"Example","packageName":"eu.test.example","versionCode":"4","versionName":"1.4.4","extensionLib":"1.4","resources":{"apkUrl":"apk/test.apk"},"sources":[{"name":"Example","language":"pt-BR"}]}]}}"""
        val client = MihonRepoClient { url -> (if (url.endsWith("repo.json")) descriptor else index).toByteArray() }
        val result = client.fetch(base)
        assertEquals("Test", result.name)
        assertEquals(base + "apk/test.apk", result.extensions.single().apkUrl)
        assertEquals(listOf("pt-BR"), result.extensions.single().languages)
    }
    @Test fun readsGzippedProtobufInlineList() {
        fun field(n: Int, data: ByteArray): ByteArray {
            fun v(x: Int): ByteArray { var a=x; val out=ByteArrayOutputStream(); do {out.write((a and 127) or if(a ushr 7 != 0)128 else 0);a=a ushr 7}while(a!=0);return out.toByteArray() }
            return v(n*8+2)+v(data.size)+data
        }
        val extension = field(1,"Example".toByteArray())+field(2,"eu.test.example".toByteArray())+field(3,field(1,"apk/test.apk".toByteArray()))+byteArrayOf(40,4)+field(6,"1.4.4".toByteArray())
        val bytes = field(101,field(1,extension))
        val out=ByteArrayOutputStream(); GZIPOutputStream(out).use { it.write(bytes) }
        val client=MihonRepoClient { if(it.endsWith("repo.json")) descriptor.toByteArray() else out.toByteArray() }
        assertEquals(4L, client.fetch(base).extensions.single().versionCode)
    }
    @Test fun rejectsMalformedAndOversizedTransport() {
        assertThrows(IllegalArgumentException::class.java) { MihonRepoClient { ByteArray(8*1024*1024+1) }.fetch(base) }
        assertThrows(Exception::class.java) { MihonRepoClient { "{broken".toByteArray() }.fetch(base) }
    }
    @Test fun rejectsRecursiveExternalList() {
        assertThrows(IllegalArgumentException::class.java) {
            MihonRepoClient { """{"extensionListUrl":"loop.json"}""".toByteArray() }.fetch(base+"index.json")
        }
    }
    @Test fun readsExternalListAndPreservesDirectIndexPin() {
        val entry = """{"extensions":[{"name":"Example","packageName":"eu.test.example","versionCode":4,"resources":{"apkUrl":"test.apk"}}]}"""
        val index = """{"name":"Direct","signingKey":"${"cd".repeat(32)}","extensionListUrl":"list.json"}"""
        val client = MihonRepoClient { if (it.endsWith("list.json")) entry.toByteArray() else index.toByteArray() }
        val result = client.fetch(base + "index.json")
        assertEquals("cd".repeat(32), result.fingerprint)
        assertEquals(1, result.extensions.size)
    }
    @Test fun preservesDirectProtobufPin() {
        fun field(number: Int, bytes: ByteArray): ByteArray {
            val tag = number * 8 + 2
            val prefix = if (tag < 128) byteArrayOf(tag.toByte()) else byteArrayOf(((tag and 127) or 128).toByte(), (tag ushr 7).toByte())
            require(bytes.size < 128)
            return prefix + byteArrayOf(bytes.size.toByte()) + bytes
        }
        val entry = field(1, "Example".toByteArray()) + field(2, "eu.test.example".toByteArray()) +
            field(3, field(1, "test.apk".toByteArray())) + byteArrayOf(40, 4)
        val pin = "ab".repeat(32)
        val index = field(3, pin.toByteArray()) + field(101, field(1, entry))
        assertEquals(pin, MihonRepoClient { index }.fetch(base + "index.pb").fingerprint)
    }
    @Test fun rejectsInvalidFingerprintInsecureResourceAndQuery() {
        assertThrows(IllegalArgumentException::class.java) {
            MihonRepoClient { descriptor.replace("ab".repeat(32), "bad").toByteArray() }.fetch(base)
        }
        val index = """{"extensions":[{"name":"Example","packageName":"eu.test.example","versionCode":4,"resources":{"apkUrl":"http://example.org/test.apk"}}]}"""
        assertThrows(IllegalArgumentException::class.java) { MihonRepoClient { index.toByteArray() }.fetch(base + "index.json") }
        assertThrows(IllegalArgumentException::class.java) { MihonRepoClient().normalize(base + "?key=value") }
        assertThrows(IllegalArgumentException::class.java) { MihonRepoClient().normalize(base + "#fragment") }
    }
    @Test fun rejectsDecompressionBomb() {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { gzip -> repeat(2049) { gzip.write(ByteArray(8192)) } }
        assertThrows(IllegalArgumentException::class.java) { MihonRepoClient { output.toByteArray() }.fetch(base) }
    }
    @Test fun rejectsExcessiveExtensions() {
        val index = "[" + List(10001) { "{}" }.joinToString(",") + "]"
        assertThrows(IllegalArgumentException::class.java) { MihonRepoClient { index.toByteArray() }.fetch(base+"index.min.json") }
    }
    @Test fun readsLegacyAndRejectsObsoleteStub() {
        val legacy="""[{"name":"Example","pkg":"eu.test.example","apk":"test.apk","version":"1.4.4","code":4,"lang":"pt","sources":[{"name":"Example","lang":"pt"}]}]"""
        val client=MihonRepoClient { if(it.endsWith("repo.json")) descriptor.replace(",\"index_v2\":\"index.json\"", "").toByteArray() else legacy.toByteArray() }
        assertEquals(base+"apk/test.apk", client.fetch(base).extensions.single().apkUrl)
        assertThrows(IllegalArgumentException::class.java) { MihonRepoClient { """[{"name":"Outdated App","pkg":"eu.kanade.tachiyomi.extension.all.outdated","apk":"x.apk","code":1}]""".toByteArray() }.fetch(base+"index.min.json") }
    }
}
