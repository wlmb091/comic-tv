package com.pelican.comictv.ui

import org.junit.Assert.*
import org.junit.Test

class ContentEntryTargetTest {
    @Test fun loadingPageReplacementPreparesItsOwnFirstItem() {
        val target = ContentEntryTarget()
        val loading = Any()
        var preparations = 0
        target.register(loading, { false }, { preparations++ })
        target.requestFocus(resetToFirst = true)
        target.unregister(loading)
        target.register(Any(), { false }, { preparations++ })
        target.retry()
        target.retry()
        assertEquals(2, preparations)
    }

    @Test fun lateRegistrationCompletesOnlyAnExplicitPendingRequest() {
        val target = ContentEntryTarget()
        var focused = 0
        assertFalse(target.requestFocus())
        target.register(Any(), { focused++; true })
        target.retry()
        target.retry()
        assertEquals(1, focused)
    }

    @Test fun leavingContentCancelsLateFocusAndPreparation() {
        val target = ContentEntryTarget()
        var focused = 0
        var prepared = 0
        target.requestFocus(resetToFirst = true)
        target.cancel()
        target.register(Any(), { focused++; true }, { prepared++ })
        target.retry()
        assertEquals(0, focused)
        assertEquals(0, prepared)
    }

    @Test fun sectionEntryPreparesFirstItemOnceAndWaitsForLayout() {
        val target = ContentEntryTarget()
        var ready = false
        var prepared = 0
        var focused = 0
        target.requestFocus(resetToFirst = true)
        target.register(Any(), { if (ready) { focused++; true } else false }, { prepared++ })
        target.retry()
        assertEquals(1, prepared)
        assertEquals(0, focused)
        ready = true
        target.retry()
        target.retry()
        assertEquals(1, focused)
        assertEquals(1, prepared)
    }

    @Test fun outgoingOwnerCannotRemoveIncomingTarget() {
        val target = ContentEntryTarget()
        val outgoing = Any()
        var focused = ""
        target.register(outgoing, { focused = "old"; true })
        target.register(Any(), { focused = "new"; true })
        target.unregister(outgoing)
        assertTrue(target.requestFocus())
        assertEquals("new", focused)
    }

    @Test fun returningFromRailDoesNotPrepareOrScrollThePage() {
        val target = ContentEntryTarget()
        var prepared = 0
        target.register(Any(), { true }, { prepared++ })
        assertTrue(target.requestFocus())
        assertEquals(0, prepared)
    }
}
