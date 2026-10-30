package com.pelican.comictv.browser

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BrowserBlobPageTest {
    @get:Rule val compose = createComposeRule()

    @Test fun webViewPreservesDownloadNameAndContentAfterProviderRevokesUrl() = runBlocking {
        val captured = CompletableDeferred<Pair<WebView, BrowserDownloadRequest>>()
        var fixture: WebView? = null
        compose.setContent {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context -> WebView(context).apply {
                fixture = this
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    private var started = false
                    override fun onPageFinished(view: WebView, url: String) {
                        if (started) return
                        started = true
                        view.evaluateJavascript("""(function(){
                            const blob=new Blob([new Uint8Array([82,97,114,33,26,7,0,0])],{type:'application/octet-stream'});
                            const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='Edição especial.cbr';
                            document.body.appendChild(a);window.fixtureUrl=a.href;return a.href;
                        })();""") { value ->
                            val blobUrl = Gson().fromJson(value, String::class.java)
                            BrowserBlobPage.prepare(view, BrowserDownloadRequest(blobUrl, "uuid.bin", "Fixture")) { request ->
                                if (request == null) captured.completeExceptionally(AssertionError("Preparation failed"))
                                else view.evaluateJavascript("URL.revokeObjectURL(window.fixtureUrl);") { captured.complete(view to request) }
                            }
                        }
                    }
                }
                loadDataWithBaseURL("https://blob-fixture.example/", "<html><body>Download fixture</body></html>", "text/html", "UTF-8", null)
            } })
        }
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "blob-fixture.download")
        try {
            val (view, request) = withTimeout(20_000) { captured.await() }
            assertEquals("Edição especial.cbr", request.suggestedName)
            val result = withTimeout(20_000) { BrowserBlobTransfer.transfer(view, request, file, { _, _ -> }, {}) }
            assertEquals("Edição especial.cbr", result.filename)
            assertEquals(8L, result.totalBytes)
            assertArrayEquals(byteArrayOf(82,97,114,33,26,7,0,0), file.readBytes())
            withContext(Dispatchers.Main) { BrowserBlobPage.release(view, request.blobKey) }
        } finally {
            file.delete()
            compose.runOnIdle { fixture?.destroy() }
        }
    }
}
