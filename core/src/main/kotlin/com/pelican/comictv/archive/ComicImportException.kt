package com.pelican.comictv.archive

import java.io.IOException

/** A controlled, user-facing import error; never include internal paths or session tokens. */
open class ComicImportException(message: String, cause: Throwable? = null) : IOException(message, cause)
