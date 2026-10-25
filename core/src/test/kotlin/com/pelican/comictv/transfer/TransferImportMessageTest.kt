package com.pelican.comictv.transfer

import org.junit.Assert.*
import org.junit.Test

class TransferImportMessageTest {
    @Test fun `receiver refusal clears stale import progress`() {
        val previous = TransferStatus(activeUploads = 1, message = "Validando coleção.zip")
        val refused = previous.copy(message = "Arquivo recusado: limite de HQs excedido")
        assertNull(transferImportMessage(previous, refused, "Conferindo arquivos já importados…"))
    }
    @Test fun `new upload clears prior summary but normal progress keeps override`() {
        val previous = TransferStatus(completed = 1, message = "Importação concluída: coleção.zip")
        val receiving = previous.copy(activeUploads = 1, message = "Recebendo coleção.zip")
        assertNull(transferImportMessage(previous, receiving, "10 importadas · 0 erros"))
        assertEquals("Importando 2/10", transferImportMessage(receiving, receiving.copy(receivedBytes = 100), "Importando 2/10"))
    }
    @Test fun `completion and final cleanup preserve batch summary`() {
        val validating = TransferStatus(activeUploads = 1, message = "Validando coleção.zip")
        val completed = validating.copy(completed = 1, message = "Importação concluída: coleção.zip")
        assertEquals("Resumo", transferImportMessage(validating, completed, "Resumo"))
        assertEquals("Resumo", transferImportMessage(completed, completed.copy(activeUploads = 0), "Resumo"))
    }
}
