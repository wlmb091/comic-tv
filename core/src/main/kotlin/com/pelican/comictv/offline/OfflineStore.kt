package com.pelican.comictv.offline

import com.google.gson.Gson
import com.pelican.comictv.sources.OnlineIdentity
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.*
import java.security.MessageDigest

data class OfflinePage(val index: Int, val bytes: Long, val sha256: String, val width: Int, val height: Int)
data class OfflineManifest(val version: Int = 1, val id: String, val count: Int, val fingerprint: String, val pages: List<OfflinePage>)

/** Permanent validated page files. Never shares paths with the disposable reader cache. */
class OfflineStore(private val root: File, private val freeBytes: () -> Long = { root.usableSpace }) {
    private val gson = Gson()
    private val verified = linkedMapOf<String, Pair<String, OfflineManifest>>()
    private fun rootDir(): File {
        val absolute = root.absoluteFile.normalize()
        require(!Files.isSymbolicLink(absolute.toPath()))
        val expected = absolute.parentFile?.let { File(it.canonicalFile,absolute.name) } ?: absolute
        return absolute.canonicalFile.also { require(it == expected) { "Diretório offline redirecionado." } }
    }
    private fun dir(id: String, create: Boolean = false): File {
        require(OnlineIdentity.isOnline(id))
        val parent = rootDir()
        if (create) parent.mkdirs()
        val dir = File(parent, id)
        require(!Files.isSymbolicLink(dir.toPath()) && dir.canonicalFile == File(parent,id)) { "Diretório offline inválido." }
        if (create) dir.mkdirs(); return dir
    }
    private fun file(id: String, name: String): File = File(dir(id), name).also {
        require(!Files.isSymbolicLink(it.toPath()) && it.canonicalFile.parentFile == dir(id)) { "Arquivo offline inválido." }
    }
    private fun read(id: String, name: String): OfflineManifest {
        val source = file(id,name); require(source.isFile && source.length() in 1..2_000_000)
        val value = source.reader(Charsets.UTF_8).use { gson.fromJson(it, OfflineManifest::class.java) }
        require(value.version == 1 && value.id == id && value.count in 1..5000 && value.fingerprint.matches(Regex("[a-f0-9]{64}")))
        require(value.pages.size <= value.count && value.pages.map { it.index }.distinct().size == value.pages.size)
        require(value.pages.all { it.index in 0 until value.count && it.bytes in 1..MAX_PAGE && it.sha256.matches(Regex("[a-f0-9]{64}")) && it.width > 0 && it.height > 0 && it.width.toLong()*it.height <= 100_000_000 })
        require(value.pages.sumOf { it.bytes } <= MAX_EDITION)
        return value
    }
    private fun write(id: String, name: String, manifest: OfflineManifest) {
        dir(id,true)
        val target = file(id,name); val temp = file(id,"$name.tmp")
        try {
            temp.outputStream().use { out -> out.write(gson.toJson(manifest).toByteArray(Charsets.UTF_8)); out.fd.sync() }
            Files.move(temp.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { temp.delete() }
    }
    @Synchronized fun begin(id: String, count: Int, fingerprint: String): OfflineManifest {
        require(count in 1..5000 && fingerprint.matches(Regex("[a-f0-9]{64}")))
        require(!isComplete(id)) { "Esta edição já tem download completo. Remova-o antes de baixar novamente." }
        rootDir().mkdirs(); ensureSpace()
        dir(id,true)
        val published = file(id,"complete.json")
        if (published.exists()) {
            // A damaged published package is not complete. Retain only verified pages
            // when the refreshed source list still matches, then resume as partial.
            val previous = runCatching { read(id,"complete.json") }.getOrNull()
            if (previous != null && previous.count == count && previous.fingerprint == fingerprint)
                write(id,"pending.json",previous.copy(pages = previous.pages.filter { runCatching { verifyPage(id,it) }.isSuccess }))
            check(published.delete()) { "Não foi possível preparar a recuperação do download." }
            verified.remove(id)
        }
        val old = runCatching { read(id,"pending.json") }.getOrNull()
        if (old != null && old.count == count && old.fingerprint == fingerprint) {
            val valid = old.pages.filter { runCatching { verifyPage(id,it) }.isSuccess }
            return old.copy(pages = valid).also { write(id,"pending.json",it) }
        }
        // An incomplete attempt may restart; a published complete package is never replaced.
        require(!file(id,"complete.json").exists()) { "Remova o download alterado antes de baixar novamente." }
        dir(id).listFiles().orEmpty().forEach { child ->
            require(!Files.isSymbolicLink(child.toPath()) && child.isFile && child.canonicalFile.parentFile == dir(id))
            check(child.delete()) { "Não foi possível reiniciar o download parcial." }
        }
        return OfflineManifest(id = id,count = count,fingerprint = fingerprint,pages = emptyList()).also { write(id,"pending.json",it) }
    }
    fun ensureSpace() { require(freeBytes() >= RESERVE + MAX_PAGE) { "Espaço insuficiente: mantenha ao menos 96 MiB livres para continuar." } }
    @Synchronized fun incoming(id: String, index: Int): File {
        require(index in 0..4999); ensureSpace(); return file(id,"incoming-$index.part")
    }
    @Synchronized fun commitPage(id: String, index: Int, width: Int, height: Int): OfflineManifest {
        val manifest = read(id,"pending.json"); require(index in 0 until manifest.count)
        require(width > 0 && height > 0 && width.toLong()*height <= 100_000_000)
        val input = file(id,"incoming-$index.part"); val length = input.length()
        require(length in 1..MAX_PAGE && manifest.pages.filterNot { it.index == index }.sumOf { it.bytes } + length <= MAX_EDITION) { "Download acima do limite de tamanho." }
        require(freeBytes() >= RESERVE) { "Espaço livre abaixo da reserva." }
        val page = OfflinePage(index,length,hash(input),width,height)
        Files.move(input.toPath(),file(id,"$index.page").toPath(),ATOMIC_MOVE,REPLACE_EXISTING)
        return manifest.copy(pages = (manifest.pages.filterNot { it.index == index } + page).sortedBy { it.index })
            .also { write(id,"pending.json",it); verified.remove(id) }
    }
    @Synchronized fun finish(id: String) {
        val manifest = read(id,"pending.json"); require(manifest.pages.size == manifest.count)
        manifest.pages.forEach { verifyPage(id,it) }
        write(id,"complete.json",manifest); file(id,"pending.json").delete(); verified.remove(id)
    }
    @Synchronized fun isComplete(id: String): Boolean = OnlineIdentity.isOnline(id) && runCatching { verified(id); true }.getOrDefault(false)
    @Synchronized fun verified(id: String): OfflineManifest {
        val manifest = read(id,"complete.json"); require(manifest.pages.size == manifest.count) { "Download incompleto." }
        val stamp = manifest.pages.joinToString(";") { page -> file(id,"${page.index}.page").let { "${page.index}:${it.length()}:${Files.getLastModifiedTime(it.toPath())}" } } + manifest.hashCode() + Files.getLastModifiedTime(file(id,"complete.json").toPath())
        verified[id]?.takeIf { it.first == stamp }?.let { return it.second }
        manifest.pages.forEach { verifyPage(id,it) }
        verified[id] = stamp to manifest
        while (verified.size > 4) verified.remove(verified.keys.first())
        return manifest
    }
    @Synchronized fun page(id: String, index: Int): File { require(index in 0..4999); return file(id,"$index.page") }
    @Synchronized fun ids(): List<String> = rootDir().listFiles().orEmpty().filter { OnlineIdentity.isOnline(it.name) && !Files.isSymbolicLink(it.toPath()) && it.isDirectory && it.canonicalFile == dir(it.name) }.map { it.name }
    @Synchronized fun hasPackage(id: String) = OnlineIdentity.isOnline(id) && file(id,"complete.json").exists()
    @Synchronized fun progress(id: String): Int = runCatching { read(id,"pending.json").pages.size }.getOrDefault(0)
    @Synchronized fun remove(id: String) {
        val directory = dir(id)
        if (!directory.exists()) return
        file(id,"complete.json").let { if (it.exists()) check(it.delete()) }
        for (child in directory.listFiles().orEmpty()) {
            require(child.isFile && !Files.isSymbolicLink(child.toPath()) && child.canonicalFile.parentFile == directory)
            check(child.delete()) { "Não foi possível remover todos os arquivos do download." }
        }
        check(directory.delete()); verified.remove(id)
    }
    private fun verifyPage(id: String, page: OfflinePage) {
        val source = file(id,"${page.index}.page")
        require(source.isFile && source.length() == page.bytes && hash(source) == page.sha256) { "Download alterado ou incompleto. Remova e baixe novamente." }
    }
    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(16384); while (true) {
            if (Thread.currentThread().isInterrupted) throw IOException("Cancelado")
            val count = input.read(buffer); if (count < 0) break; digest.update(buffer,0,count)
        } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    companion object { const val MAX_PAGE = 32L*1024*1024; const val MAX_EDITION = 1024L*1024*1024; const val RESERVE = 64L*1024*1024 }
}
