package com.pelican.comictv.library

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Two transfers, at most one admitted as background; active work is not interrupted for priority. */
internal class PageTransferScheduler(
    // Optional probe outside locks lets concurrency tests hold this exact cancellation boundary.
    private val beforeQueuedPriorityChange: suspend () -> Unit = {},
) {
    private class Ticket(var foreground: Boolean) {
        val ready = CompletableDeferred<Unit>()
        var active = false
        var finished = false
    }
    private class KeyGate {
        val mutex = Mutex()
        var users = 0
        var foregroundUsers = 0
        var ticket: Ticket? = null
    }
    private val keysLock = Mutex()
    private val keys = mutableMapOf<String, KeyGate>()
    private val queueLock = Mutex()
    private val waiting = mutableListOf<Ticket>()
    private var active = 0
    private var activeBackground = 0

    suspend fun <T : Any> load(key: String, foreground: Boolean, cached: suspend () -> T?, fetch: suspend () -> T): T {
        cached()?.let { return it }
        val gate = keysLock.withLock { keys.getOrPut(key) { KeyGate() }.also {
            it.users++; if (foreground) it.foregroundUsers++
        } }
        try {
            if (foreground) {
                val ticket = keysLock.withLock { gate.ticket }
                if (ticket != null) queueLock.withLock {
                    if (!ticket.active && !ticket.finished) { ticket.foreground = true; drain() }
                }
            }
            return gate.mutex.withLock {
                cached() ?: run {
                    val ticket = keysLock.withLock { Ticket(gate.foregroundUsers > 0).also { gate.ticket = it } }
                    try {
                        queueLock.withLock { waiting += ticket; drain() }
                        ticket.ready.await()
                        currentCoroutineContext().ensureActive()
                        fetch()
                    } finally { withContext(NonCancellable) {
                        queueLock.withLock {
                            if (ticket.active) { active--; if (!ticket.foreground) activeBackground--; ticket.active = false }
                            else waiting.remove(ticket)
                            ticket.finished = true
                            drain()
                        }
                        keysLock.withLock { if (gate.ticket === ticket) gate.ticket = null }
                    } }
                }
            }
        } finally { withContext(NonCancellable) {
            val demote = keysLock.withLock {
                gate.users--; if (foreground) gate.foregroundUsers--
                if (gate.users == 0 && keys[key] === gate) keys.remove(key)
                if (gate.foregroundUsers == 0) gate.ticket else null
            }
            if (demote != null) {
                beforeQueuedPriorityChange()
                queueLock.withLock {
                    val noForegroundDemand = keysLock.withLock { gate.foregroundUsers == 0 && gate.ticket === demote }
                    if (noForegroundDemand && !demote.active && !demote.finished) { demote.foreground = false; drain() }
                }
            }
        } }
    }
    private fun drain() {
        while (active < 2) {
            val next = waiting.firstOrNull { it.foreground }
                ?: if (activeBackground < 1) waiting.firstOrNull() else null
            if (next == null) return
            waiting.remove(next)
            next.active = true; active++
            if (!next.foreground) activeBackground++
            next.ready.complete(Unit)
        }
    }
}
