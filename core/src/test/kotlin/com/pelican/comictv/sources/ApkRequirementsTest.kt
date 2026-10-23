package com.pelican.comictv.sources

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkRequirementsTest {
    @Test fun readsUtf8AndUtf16Pools() {
        for (utf8 in listOf(true, false)) assertEquals(26, ApkRequirements.minimumSdkFromManifest(manifest(utf8 = utf8)))
    }
    @Test fun acceptsNumericStringAndImplicitDefault() {
        assertEquals(26, ApkRequirements.minimumSdkFromManifest(manifest(valueType = 3, value = 4)))
        assertEquals(1, ApkRequirements.minimumSdkFromManifest(manifest(includeSdk = false)))
    }
    @Test fun identifiesAndroidResourceIdAndRejectsDuplicateSdkElements() {
        assertEquals(26, ApkRequirements.minimumSdkFromManifest(manifest(resourceMap = true)))
        assertThrows(IllegalArgumentException::class.java) { ApkRequirements.minimumSdkFromManifest(manifest(duplicateSdk = true)) }
    }
    @Test fun rejectsCodenamesInvalidNumbersWrongNamespaceAndDuplicates() {
        for (bytes in listOf(manifest(text = "VanillaIceCream", valueType = 3, value = 4),
            manifest(value = 0), manifest(value = -1), manifest(namespace = -1), manifest(duplicate = true))) {
            assertThrows(IllegalArgumentException::class.java) { ApkRequirements.minimumSdkFromManifest(bytes) }
        }
    }
    @Test fun rejectsTruncationOverflowAndMissingRoot() {
        val good = manifest()
        for (n in 0 until good.size) assertThrows("length $n", IllegalArgumentException::class.java) {
            ApkRequirements.minimumSdkFromManifest(good.copyOf(n))
        }
        val bad = good.copyOf().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(12, Int.MAX_VALUE) }
        assertThrows(IllegalArgumentException::class.java) { ApkRequirements.minimumSdkFromManifest(bad) }
        assertThrows(IllegalArgumentException::class.java) { ApkRequirements.minimumSdkFromManifest(chunk(3, 8, byteArrayOf())) }
    }
    @Test fun readsZipAndBoundsExpandedManifest() {
        fun apk(bytes: ByteArray): File = File.createTempFile("requirements", ".apk").also { file ->
            ZipOutputStream(file.outputStream()).use { it.putNextEntry(ZipEntry("AndroidManifest.xml")); it.write(bytes); it.closeEntry() }
        }
        val good = apk(manifest())
        val huge = apk(ByteArray(1024 * 1024 + 1))
        try {
            assertEquals(26, ApkRequirements.minimumSdk(good))
            assertThrows(IllegalArgumentException::class.java) { ApkRequirements.minimumSdk(huge) }
        } finally { good.delete(); huge.delete() }
    }
    @Test fun incompatibleMessageExplainsBothApis() {
        ApkRequirements.requireSupported(26, 26)
        val error = assertThrows(IllegalArgumentException::class.java) { ApkRequirements.requireSupported(26, 23) }
        assertTrue(error.message!!.contains("26")); assertTrue(error.message!!.contains("23"))
        assertTrue(error.message!!.contains("Android"))
    }

    private fun manifest(utf8: Boolean = true, text: String = "26", valueType: Int = 0x10,
                         value: Int = 26, includeSdk: Boolean = true, namespace: Int = 2,
                         duplicate: Boolean = false, resourceMap: Boolean = false,
                         duplicateSdk: Boolean = false): ByteArray {
        val strings = listOf("manifest", "uses-sdk", "http://schemas.android.com/apk/res/android", if (resourceMap) "sdkAlias" else "minSdkVersion", text)
        val data = ByteArrayOutputStream()
        val offsets = strings.map { s ->
            val offset = data.size()
            if (utf8) { val bytes = s.toByteArray(); data.write(s.length); data.write(bytes.size); data.write(bytes); data.write(0) }
            else { data.write(le(s.length, 2)); data.write(s.toByteArray(Charsets.UTF_16LE)); data.write(byteArrayOf(0, 0)) }
            offset
        }
        while (data.size() % 4 != 0) data.write(0)
        val pool = chunk(1, 28, le(5) + le(0) + le(if (utf8) 0x100 else 0) + le(48) + le(0) + offsets.flatMap { le(it).toList() }.toByteArray() + data.toByteArray())
        fun start(name: Int, attrs: Boolean): ByteArray {
            val count = if (!attrs) 0 else if (duplicate) 2 else 1
            val attr = le(namespace) + le(3) + le(-1) + le(8, 2) + byteArrayOf(0, valueType.toByte()) + le(value)
            return chunk(0x102, 16, le(1) + le(-1) + le(-1) + le(name) + le(20, 2) + le(20, 2) + le(count, 2) + le(0, 2) + le(0, 2) + le(0, 2) + List(count) { attr }.fold(byteArrayOf()) { a, b -> a + b })
        }
        fun end(name: Int) = chunk(0x103, 16, le(1) + le(-1) + le(-1) + le(name))
        val map = if (resourceMap) chunk(0x180, 8, le(0) + le(0) + le(0) + le(0x0101020c)) else byteArrayOf()
        val sdk = if (includeSdk) start(1, true) + end(1) else byteArrayOf()
        val body = pool + map + start(0, false) + sdk + (if (duplicateSdk) sdk else byteArrayOf()) + end(0)
        return chunk(3, 8, body)
    }
    private fun le(value: Int, size: Int = 4): ByteArray = ByteArray(size) { (value ushr (it * 8)).toByte() }
    private fun chunk(type: Int, header: Int, body: ByteArray) = le(type, 2) + le(header, 2) + le(body.size + 8) + body
}
