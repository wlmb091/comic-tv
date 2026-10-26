package com.pelican.comictv.sources

import eu.kanade.tachiyomi.network.HttpException
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InterruptedIOException
import javax.net.ssl.SSLException

/** Total cooperative budgets per admitted source operation, not a new retry policy. */
internal enum class SourceRequest(val timeoutMillis: Long) {
    Preview(20_000), Catalog(90_000), Details(90_000), PageList(90_000), Image(120_000)
}

internal suspend fun <T> sourceRequest(request: SourceRequest, timeoutMillis: Long = request.timeoutMillis,
    block: suspend () -> T): T {
    require(timeoutMillis > 0)
    try {
        val result = withTimeout(timeoutMillis) { block() }
        currentCoroutineContext().ensureActive()
        return result
    } catch (failure: CancellationException) {
        // An inner source timeout is an error; leaving/changing the screen is still cancellation.
        currentCoroutineContext().ensureActive()
        if (failure is TimeoutCancellationException) throw IOException(timeoutMessage(), failure)
        throw failure
    } catch (failure: LinkageError) {
        currentCoroutineContext().ensureActive()
        throw IOException("Esta extensão exige uma API ainda não compatível (${failure.javaClass.simpleName}).", failure)
    } catch (failure: Exception) {
        // A cancelled HTTP call can throw IOException instead of CancellationException.
        currentCoroutineContext().ensureActive()
        val causes = failureCauses(failure)
        val http = causes.filterIsInstance<HttpException>().firstOrNull()
        val message = when {
            http != null -> when (http.code) {
                401, 403 -> "A fonte recusou o acesso (HTTP ${http.code}). Confira a fonte antes de tentar novamente."
                404, 410 -> "Conteúdo não encontrado na fonte (HTTP ${http.code}). Reabra o capítulo ou atualize a lista da fonte."
                429 -> "A fonte está limitando os pedidos (HTTP 429). Aguarde antes de tentar novamente."
                in 500..599 -> "A fonte está temporariamente indisponível (HTTP ${http.code}). Tente novamente mais tarde."
                else -> "A fonte respondeu com HTTP ${http.code}. Tente novamente ou escolha outra fonte."
            }
            causes.any { it is InterruptedIOException } -> timeoutMessage()
            causes.any { it is SSLException } -> "Não foi possível estabelecer uma conexão segura com a fonte. Confira data, hora e rede do aparelho."
            failure is IOException -> if (request == SourceRequest.Image)
                "Não foi possível obter a imagem. Confira conexão e espaço disponível e tente novamente."
                else "Não foi possível acessar a fonte. Confira a conexão e tente novamente."
            else -> throw failure
        }
        throw IOException(message, failure)
    }
}

private fun timeoutMessage() = "A fonte demorou demais para responder. Tente novamente ou escolha outra fonte."

private fun failureCauses(failure: Throwable): List<Throwable> {
    val result = mutableListOf<Throwable>()
    var next: Throwable? = failure
    while (next != null && result.size < 12 && result.none { it === next }) {
        result += next
        next = next.cause
    }
    return result
}
