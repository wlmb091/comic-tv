package com.pelican.comictv.mihon

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import rx.Observable
import rx.subscriptions.Subscriptions
import tachiyomi.core.common.util.lang.awaitSingle

class RxCoroutineCancellationTest {
    @Test(timeout = 5000) fun cancellationUnsubscribesWhileSubscribeIsStillBlocked() = runBlocking {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        val observable = Observable.unsafeCreate<Int> { subscriber ->
            subscriber.add(Subscriptions.create { released.countDown() })
            entered.countDown()
            released.await(3, TimeUnit.SECONDS)
            if (!subscriber.isUnsubscribed) {
                subscriber.onNext(1)
                subscriber.onCompleted()
            }
        }
        val job = launch(Dispatchers.IO) { observable.awaitSingle() }
        try {
            assertTrue("subscribe did not start", entered.await(1, TimeUnit.SECONDS))
            job.cancel()
            assertTrue("cancellation must unsubscribe before blocked subscribe returns", released.await(500, TimeUnit.MILLISECONDS))
        } finally {
            released.countDown()
            job.cancel()
            job.join()
        }
    }
}
