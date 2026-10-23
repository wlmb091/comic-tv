package com.pelican.comictv.sources

import java.io.IOException

/** Optional extension APIs must fail as reader errors, without terminating the host. */
internal inline fun <T> extensionOperation(block: () -> T): T = try {
    block()
} catch (failure: LinkageError) {
    throw IOException("Esta extensão exige uma API ainda não compatível (${failure.javaClass.simpleName}).", failure)
}
