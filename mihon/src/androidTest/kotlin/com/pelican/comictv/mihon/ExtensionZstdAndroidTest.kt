package com.pelican.comictv.mihon

import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.zstd.Zstd
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the Android JNI decoder on the device ABI; cannot run in Windows JVM tests. */
@RunWith(AndroidJUnit4::class)
class ExtensionZstdAndroidTest {
    @Test fun decodesZstandardFixtureUsingPackagedNativeLibrary() {
        val compressed = Buffer().write(requireNotNull("KLUv/SAgAQEAQ29taWMgVFY6IHJlc3Bvc3RhIGRlIHF1YWRyaW5ob3M=".decodeBase64()))
        Zstd.decompress(compressed).buffer().use { decoded ->
            assertEquals("Comic TV: resposta de quadrinhos", decoded.readUtf8())
        }
    }
}
