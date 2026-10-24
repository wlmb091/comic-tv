package com.pelican.comictv.sources

/** An existing navigation entry resumes from a reader without resetting its catalog/details. */
internal class SourceEntryGate {
    private var lastEntry: String? = null
    fun enter(entryId: String): Boolean {
        if (entryId == lastEntry) return false
        lastEntry = entryId
        return true
    }
}
