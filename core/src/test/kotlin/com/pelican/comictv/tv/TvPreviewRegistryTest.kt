package com.pelican.comictv.tv

import org.junit.Assert.*
import org.junit.Test

class TvPreviewRegistryTest {
    @Test fun onlyTheDisplayedPreviewCanBeInstalledOnce(){
        val previews=TvPreviewRegistry<String>({10L});val a=previews.issue("a");val b=previews.issue("b")
        assertNull(previews.consume(a));assertEquals("b",previews.consume(b));assertNull(previews.consume(b))
    }
    @Test fun expiryAndSessionCloseInvalidatePreview(){
        var now=1L;val previews=TvPreviewRegistry<String>({now},100)
        val a=previews.issue("secret");now=102;assertNull(previews.peek());assertNull(previews.consume(a))
        val b=previews.issue("other");previews.clear();assertNull(previews.consume(b))
    }
}
