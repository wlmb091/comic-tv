package com.pelican.comictv.reader
import android.content.Context
import coil3.request.ImageRequest
import java.io.File

internal fun readerThumbnailRequest(context: Context, file: File): ImageRequest = ImageRequest.Builder(context)
    .data(file).size(240, 360)
    .memoryCacheKey("reader-thumbnail:" + file.path + ":" + file.length() + ":" + file.lastModified()).build()
