package com.pelican.comictv.tv

import java.io.File
import java.security.MessageDigest
import java.util.Properties

/**
 * Subtitles kept on the TV for each video, whatever source plays it: at most [PER_VIDEO] files per video and
 * [VIDEOS] videos, the oldest dropped first, plus the last choice ("off" or a kept subtitle) and its sync.
 */
class SubtitleMemory(private val root:File) {
    data class Entry(val id:String,val label:String,val lang:String,val kind:SubtitleText.Kind,val file:File,val time:Long)
    data class Choice(val id:String,val delayMs:Long)

    private fun dir(videoKey:String)=File(root,hash(videoKey).take(32))
    private fun index(dir:File)=Properties().apply { File(dir,INDEX).takeIf { it.isFile }?.inputStream()?.use { runCatching { load(it) } } }
    private fun write(dir:File,p:Properties) {
        val temp=File(dir,"$INDEX.tmp")
        temp.outputStream().use { p.store(it,null) }
        if(!temp.renameTo(File(dir,INDEX))) { File(dir,INDEX).delete();temp.renameTo(File(dir,INDEX)) }
    }

    /** Newest first; entries whose file is gone are skipped. */
    @Synchronized fun list(videoKey:String):List<Entry> {
        val dir=dir(videoKey)
        val p=index(dir)
        return p.stringPropertyNames().filter { it.endsWith(".kind") }.map { it.removeSuffix(".kind") }.mapNotNull { id ->
            val kind=SubtitleText.Kind.entries.firstOrNull { it.name==p.getProperty("$id.kind") }?:return@mapNotNull null
            val file=File(dir,"$id.${kind.extension}").takeIf { it.isFile }?:return@mapNotNull null
            Entry(id,p.getProperty("$id.label").orEmpty(),p.getProperty("$id.lang").orEmpty(),kind,file,p.getProperty("$id.time")?.toLongOrNull()?:0)
        }.sortedByDescending { it.time }
    }

    /** Keeps [text]; the same text kept again only becomes the newest. Returns the kept entry's id. */
    @Synchronized fun save(videoKey:String,label:String,lang:String,text:String,kind:SubtitleText.Kind):String {
        val dir=dir(videoKey)
        dir.mkdirs()
        val p=index(dir)
        val id=hash(text).take(16)
        val newest=p.stringPropertyNames().filter { it.endsWith(".time") }.maxOfOrNull { p.getProperty(it).toLongOrNull()?:0 }?:0
        File(dir,"$id.${kind.extension}").writeText(text,Charsets.UTF_8)
        p.setProperty("$id.kind",kind.name)
        p.setProperty("$id.label",label.take(160).filter { it.code>=32 })
        p.setProperty("$id.lang",lang.take(20).filter { it.code>=32 })
        p.setProperty("$id.time",maxOf(System.currentTimeMillis(),newest+1).toString())
        val kept=p.stringPropertyNames().filter { it.endsWith(".time") }.map { it.removeSuffix(".time") }
            .sortedByDescending { p.getProperty("$it.time").toLongOrNull()?:0 }
        for(old in kept.drop(PER_VIDEO)) {
            SubtitleText.Kind.entries.forEach { File(dir,"$old.${it.extension}").delete() }
            listOf("kind","label","lang","time").forEach { p.remove("$old.$it") }
            if(p.getProperty("choice")==old) { p.remove("choice");p.remove("delay") }
        }
        write(dir,p)
        dir.setLastModified(System.currentTimeMillis())
        trimVideos(dir)
        return id
    }

    @Synchronized fun choice(videoKey:String):Choice? {
        val p=index(dir(videoKey))
        val id=p.getProperty("choice")?:return null
        return Choice(id,(p.getProperty("delay")?.toLongOrNull()?:0).coerceIn(-60_000,60_000))
    }

    /** [id] is "off", a kept entry id, or null to forget the choice and let the automatic choice decide. */
    @Synchronized fun choose(videoKey:String,id:String?,delayMs:Long=0) {
        val dir=dir(videoKey)
        if(id==null&&!dir.isDirectory)return
        dir.mkdirs()
        val p=index(dir)
        if(id==null) { p.remove("choice");p.remove("delay") }
        else { p.setProperty("choice",id);p.setProperty("delay",delayMs.coerceIn(-60_000,60_000).toString()) }
        write(dir,p)
        dir.setLastModified(System.currentTimeMillis())
        trimVideos(dir)
    }

    @Synchronized fun clear() { root.deleteRecursively() }

    private fun trimVideos(current:File) {
        val dirs=root.listFiles { f -> f.isDirectory }?.sortedByDescending { if(it==current)Long.MAX_VALUE else it.lastModified() }?:return
        dirs.drop(VIDEOS).forEach { it.deleteRecursively() }
    }

    companion object {
        const val PER_VIDEO=5
        const val VIDEOS=200
        private const val INDEX="index.properties"
        private fun hash(text:String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
