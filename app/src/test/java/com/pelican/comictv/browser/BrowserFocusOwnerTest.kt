package com.pelican.comictv.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserFocusOwnerTest {
    @Test fun homeShortcutsOwnInputInsteadOfTheEmptyWebView() {
        assertEquals(BrowserFocusOwner.Home, browserFocusOwner(true, "", false, false))
        assertEquals(BrowserFocusOwner.Page, browserFocusOwner(true, "https://example.org", false, false))
    }
    @Test fun controlsAndDialogsAlwaysTakePriorityOverHomeAndWebPages() {
        for (url in listOf("", "https://example.org")) {
            assertEquals(BrowserFocusOwner.Controls, browserFocusOwner(true,url,true,false))
            assertEquals(BrowserFocusOwner.None, browserFocusOwner(true,url,true,true))
            assertEquals(BrowserFocusOwner.None, browserFocusOwner(true,url,false,true))
            assertEquals(BrowserFocusOwner.None, browserFocusOwner(false,url,false,false))
        }
    }
}
