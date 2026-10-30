package com.pelican.comictv.explorer

import org.junit.Assert.*
import org.junit.Test

class ExplorerLocationsTest {
    @Test fun parentPositionAndFilterSurviveVisitingChild() {
        val locations = ExplorerLocations()
        locations.save("parent", ExplorerPosition("batman", "Quadrinhos", 18, 31))
        locations.save("child", ExplorerPosition("", "Todos", 3, 7))
        assertEquals(ExplorerPosition("batman", "Quadrinhos", 18, 31), locations.restore("parent"))
        assertEquals(ExplorerPosition(), locations.restore("new"))
    }
    @Test fun filteringNeverRequestsFocusAwayFromEditor() {
        assertFalse(explorerShouldRestoreFocus(editingQuery = true, loading = false))
        assertFalse(explorerShouldRestoreFocus(editingQuery = false, loading = true))
        assertTrue(explorerShouldRestoreFocus(editingQuery = false, loading = false))
    }
}
