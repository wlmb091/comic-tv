package com.pelican.comictv.archive

import java.io.File

/** Only names explicitly selected by the user are considered. No neighbour discovery. */
object SelectedArchiveParts {
    fun ordered(files: List<File>): List<File> {
        require(files.isNotEmpty() && files.size <= 100) { "Escolha entre 1 e 100 partes." }
        require(files.map { it.name.lowercase() }.distinct().size == files.size) { "Há partes com nomes repetidos." }
        require(files.all { it.isFile && it.length() > 0 }) { "Uma parte está vazia ou indisponível." }
        var total = 0L
        files.forEach { file ->
            require(file.length() <= 1024L * 1024 * 1024 - total) { "O conjunto excede 1 GiB." }
            total += file.length()
        }
        if (files.size == 1 && !files[0].name.matches(Regex(".*\\.\\d{3}"))) return files
        val pattern = Regex("^(.*)\\.(\\d{3})$", RegexOption.IGNORE_CASE)
        val numbered = files.map { file ->
            val match = pattern.matchEntire(file.name) ?: error("Selecione todas as partes .001, .002… do mesmo arquivo. RAR multipart não é suportado.")
            Triple(match.groupValues[1].lowercase(), match.groupValues[2].toInt(), file)
        }.sortedBy { it.second }
        require(numbered.map { it.first }.distinct().size == 1 && numbered.map { it.second } == (1..files.size).toList()) {
            "Partes ausentes ou misturadas. Escolha a sequência completa começando em .001."
        }
        return numbered.map { it.third }
    }
    fun join(files: List<File>, destination: File): File {
        val ordered = ordered(files)
        require(ordered.none { it.canonicalFile == destination.canonicalFile })
        try {
            destination.outputStream().use { output ->
                for (part in ordered) part.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
                        val n = input.read(buffer); if (n < 0) break
                        check(destination.parentFile.usableSpace >= 64L * 1024 * 1024 + n) { "Espaço insuficiente." }
                        output.write(buffer, 0, n)
                    }
                }
            }
            return destination
        } catch (failure: Exception) { destination.delete(); throw failure }
    }
}
