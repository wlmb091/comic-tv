package com.pelican.comictv.reader

fun imageMemoryMiB(memoryClassMiB: Int, lowRam: Boolean): Int =
    ((memoryClassMiB.takeIf {it>0} ?: 192)/6).coerceIn(8,if(lowRam) 24 else 48)

fun coverWarmIndices(count: Int, firstVisible: Int, lastVisible: Int, direction: Int, limit: Int): List<Int> {
    if(count<=0 || firstVisible !in 0 until count || lastVisible !in firstVisible until count) return emptyList()
    val step=if(direction<0) -1 else 1
    val start=if(step>0) lastVisible+1 else firstVisible-1
    return (0 until limit.coerceIn(0,3)).map {start+it*step}.filter {it in 0 until count}
}
