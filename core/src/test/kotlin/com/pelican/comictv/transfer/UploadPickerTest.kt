package com.pelican.comictv.transfer

import org.junit.Assert.*
import org.junit.Test

class UploadPickerTest {
    @Test fun `picker does not ask iOS to filter unregistered comic types`() {
        val picker = Regex("<input\\b[^>]*id=\"files\"[^>]*>").find(UploadPage.html)?.value
        assertNotNull(picker)
        assertFalse("iOS must be able to pick CBZ even without a registered file type", Regex("\\baccept\\s*=").containsMatchIn(picker!!))
        assertTrue(picker.contains("multiple"))
    }
}
