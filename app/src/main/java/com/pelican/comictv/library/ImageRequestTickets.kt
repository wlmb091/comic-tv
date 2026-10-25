package com.pelican.comictv.library

import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

/** Accessed under the storage mutex; tickets survive removal only to reject their pending commits. */
internal class ImageRequestTickets {
    class Ticket(val id: String, val operation: Job?) { var valid = true }
    private val active = mutableSetOf<Ticket>()
    fun begin(id: String, operation: Job? = null) = Ticket(id, operation).also { active += it }
    fun check(ticket: Ticket) {
        if (!ticket.valid || ticket !in active) throw IOException("O armazenamento mudou durante o carregamento. Tente novamente.")
    }
    private fun invalidate(ticket: Ticket) {
        ticket.valid = false
        ticket.operation?.cancel(CancellationException("Carregamento descartado após alteração do armazenamento."))
    }
    fun invalidate(id: String) { active.filter { it.id == id }.forEach(::invalidate) }
    fun invalidateAll() { active.toList().forEach(::invalidate) }
    fun finish(ticket: Ticket) { active.remove(ticket) }
}
