package com.pelican.comictv.sources

import org.junit.Assert.*
import org.junit.Test

class SourceEntryGateTest {
    @Test fun returningFromReaderDoesNotReenterSameSection() {
        val gate = SourceEntryGate()
        assertTrue(gate.enter("catalog-entry"))
        assertFalse(gate.enter("catalog-entry"))
    }
    @Test fun explicitNavigationWithNewEntryDispatchesAgain() {
        val gate = SourceEntryGate()
        assertTrue(gate.enter("catalog-1"))
        assertTrue(gate.enter("extensions-2"))
        assertTrue(gate.enter("catalog-3"))
    }
    @Test fun processRecreationDispatchesRestoredEntryInNewViewModel() {
        assertTrue(SourceEntryGate().enter("restored-entry"))
        assertTrue(SourceEntryGate().enter("restored-entry"))
    }
}
