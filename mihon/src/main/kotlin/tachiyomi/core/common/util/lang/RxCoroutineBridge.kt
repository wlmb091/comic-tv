// Vendored from mihonapp/mihon@446f6df8 (Apache-2.0).
// COMIC TV: register cancellation before subscribe, which may execute blocking HTTP synchronously.
package tachiyomi.core.common.util.lang

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import rx.Observable
import rx.Subscriber

suspend fun <T> Observable<T>.awaitSingle(): T = single().awaitOne()

@OptIn(InternalCoroutinesApi::class)
private suspend fun <T> Observable<T>.awaitOne(): T = suspendCancellableCoroutine { cont ->
    val subscriber = object : Subscriber<T>() {
        override fun onStart() { request(1) }
        override fun onNext(t: T) {
            cont.tryResume(t)?.let(cont::completeResume)
        }
        override fun onCompleted() {
            cont.tryResumeWithException(IllegalStateException("Should have invoked onNext"))?.let(cont::completeResume)
        }
        override fun onError(e: Throwable) {
            cont.tryResumeWithException(e)?.let(cont::completeResume)
        }
    }
    // Adding the cancellation hook after subscribe returns is too late for legacy synchronous
    // Call.asObservable(): execute() blocks subscribe until the response or network timeout.
    cont.invokeOnCancellation { subscriber.unsubscribe() }
    if (cont.isActive) subscribe(subscriber)
}
