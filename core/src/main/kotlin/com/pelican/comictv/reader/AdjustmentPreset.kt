package com.pelican.comictv.reader

import com.google.gson.Gson

/**
 * Image adjustments saved under a name, to use on other editions.
 * Without [paper] it leaves the edition's own temperature and tint: those usually belong to one scan's yellowed paper.
 */
data class AdjustmentPreset(val id: String, val name: String, val folder: String = "", val values: String, val paper: Boolean = true) {
    fun valid(): Boolean = runCatching {
        id.matches(ID) && name.trim().length in 1..40 && name.none { it.code < 32 } && folder.length <= 30 && folder.none { it.code < 32 } && ImageAdjustments.parse(values) != null
    }.getOrDefault(false)
    fun over(current: ImageAdjustments): ImageAdjustments {
        val own = ImageAdjustments.restore(values)
        return if (paper) own else ImageAdjustments(own.values.mapIndexed { i, value -> if (i < 2) current.values[i] else value })
    }
    companion object {
        const val MAX = 200
        val ID = Regex("[A-Za-z0-9-]{1,60}")
        fun parse(text: String): AdjustmentPreset? = runCatching { Gson().fromJson(text, AdjustmentPreset::class.java) }.getOrNull()?.takeIf { it.valid() }
        /** What this TV already has wins, as with drawings. */
        fun merge(current: List<AdjustmentPreset>, incoming: List<AdjustmentPreset>): List<AdjustmentPreset> {
            val ids = current.map { it.id }.toSet()
            return (current + incoming.filter { it.id !in ids }).take(MAX)
        }
    }
}
