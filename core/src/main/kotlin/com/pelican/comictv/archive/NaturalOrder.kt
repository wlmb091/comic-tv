package com.pelican.comictv.archive

object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            if (a[i] in '0'..'9' && b[j] in '0'..'9') {
                val startA = i; val startB = j
                while (i < a.length && a[i] in '0'..'9') i++
                while (j < b.length && b[j] in '0'..'9') j++
                val na = a.substring(startA, i).trimStart('0').ifEmpty { "0" }
                val nb = b.substring(startB, j).trimStart('0').ifEmpty { "0" }
                val result = na.length.compareTo(nb.length).takeIf { it != 0 } ?: na.compareTo(nb)
                if (result != 0) return result
            } else {
                val result = a[i].lowercaseChar().compareTo(b[j].lowercaseChar())
                if (result != 0) return result
                i++; j++
            }
        }
        return (a.length - i).compareTo(b.length - j).takeIf { it != 0 } ?: a.compareTo(b)
    }
}
