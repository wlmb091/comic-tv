package com.pelican.comictv.archive

import java.io.File

/** Shared signature detection: filenames and HTTP MIME types never select a decoder. */
enum class ComicContainer { Zip, Rar, SevenZip, Pdf, Html, Executable, Empty, Incomplete, Unknown }
object ComicContainers {
    val extensions = setOf("cbz", "zip", "cbr", "rar", "cb7", "7z", "pdf", "epub")
    private val signatures = listOf(
        byteArrayOf(80,75,3,4), byteArrayOf(80,75,5,6), byteArrayOf(80,75,7,8),
        byteArrayOf(82,97,114,33,26,7,0), byteArrayOf(82,97,114,33,26,7,1,0),
        byteArrayOf(55,122,0xbc.toByte(),0xaf.toByte(),39,28), "%PDF-".toByteArray(Charsets.US_ASCII))
    fun detect(file: File): ComicContainer {
        val bytes = file.inputStream().use { input ->
            val buffer = ByteArray(512); var count=0
            while(count<buffer.size) { val n=input.read(buffer,count,buffer.size-count);if(n<0)break;count+=n }
            buffer.copyOf(count)
        }
        if(bytes.isEmpty()) return ComicContainer.Empty
        fun starts(sig: ByteArray)=bytes.size>=sig.size && sig.indices.all { bytes[it]==sig[it] }
        val at=signatures.indexOfFirst(::starts)
        if(at>=0)return when(at) { 0,1,2->ComicContainer.Zip;3,4->ComicContainer.Rar;5->ComicContainer.SevenZip;else->ComicContainer.Pdf }
        if(signatures.any { bytes.size<it.size && bytes.indices.all { i->bytes[i]==it[i] } })return ComicContainer.Incomplete
        val text=bytes.toString(Charsets.UTF_8).trimStart('﻿',' ','\t','\r','\n').lowercase()
        if(listOf("<!doctype html","<html","<head","<body","<error").any(text::startsWith) || text.startsWith("<?xml")&&text.contains("<error"))return ComicContainer.Html
        if(starts(byteArrayOf(77,90))||starts(byteArrayOf(127,69,76,70)))return ComicContainer.Executable
        return ComicContainer.Unknown
    }
    fun failure(format: ComicContainer): String = when(format) {
        ComicContainer.Empty -> "O arquivo recebido está vazio. Baixe novamente pelo site."
        ComicContainer.Incomplete -> "O arquivo está incompleto. Conclua ou refaça o download."
        ComicContainer.Html -> "O site forneceu uma página de erro ou login em vez do quadrinho. Abra o site e baixe o arquivo novamente."
        ComicContainer.Executable -> "O arquivo recebido é um aplicativo ou executável, não um quadrinho."
        else -> "Formato não reconhecido. Envie ZIP/CBZ, RAR/CBR, 7z/CB7, PDF ou EPUB."
    }
}
