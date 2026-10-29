package com.pelican.comictv.reader

/** Only the original file of the page actually on screen can become a manual cover. */
internal fun canUseSagaPageCover(image: PageImage?, loading: Boolean, hasError: Boolean): Boolean =
    image != null && image.displayed && image.text == null && !loading && !hasError
