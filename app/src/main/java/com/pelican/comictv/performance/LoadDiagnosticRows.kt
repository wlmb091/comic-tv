package com.pelican.comictv.performance

internal data class LoadDiagnosticRow(val stage: LoadStage, val detail: String)

/** Stable rows keep unmeasured stages explicit and list positions intact after refresh/clear. */
internal fun loadDiagnosticRows(samples: Map<LoadStage, LoadSample>): List<LoadDiagnosticRow> {
    val priority = listOf(LoadStage.PageMaterialize, LoadStage.PageQueue, LoadStage.RemoteImage,
        LoadStage.PagePrepare, LoadStage.PageDisplay)
    return (priority + LoadStage.entries.filterNot { it in priority }).map { stage ->
        val sample = samples[stage]
        LoadDiagnosticRow(stage, if (sample == null || sample.count == 0L) "Sem medições nesta sessão."
            else "${sample.count} operação(ões) · média ${sample.averageMillis} ms · maior ${sample.maxMillis} ms · ${sample.failed} falha(s) · ${sample.cancelled} cancelada(s)")
    }
}
