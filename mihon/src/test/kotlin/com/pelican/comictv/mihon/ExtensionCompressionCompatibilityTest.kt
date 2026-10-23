package com.pelican.comictv.mihon

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtensionCompressionCompatibilityTest {
    @Test fun linksCompressionApisUsedByKeiyoushiExtensions() {
        val algorithm = Class.forName("okhttp3.CompressionInterceptor\$DecompressionAlgorithm")
        val algorithms = java.lang.reflect.Array.newInstance(algorithm, 3)
        listOf("okhttp3.brotli.Brotli", "okhttp3.Gzip", "okhttp3.zstd.Zstd").forEachIndexed { index, name ->
            java.lang.reflect.Array.set(algorithms, index, Class.forName(name).getField("INSTANCE").get(null))
        }
        val interceptor = Class.forName("okhttp3.CompressionInterceptor")
            .getConstructor(algorithms.javaClass).newInstance(algorithms) as Interceptor
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        assertEquals(interceptor, client.interceptors.single())
        Class.forName("okhttp3.brotli.BrotliInterceptor")
    }
}
