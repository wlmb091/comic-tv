package com.pelican.comictv.tv

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import dev.jdtech.mpv.MPVLib
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Base64

/** One audio or subtitle track as mpv lists it in track-list. */
internal data class MpvTrack(val id: Int, val type: String, val lang: String, val title: String, val codec: String,
    val channels: Int, val selected: Boolean, val external: Boolean, val forced: Boolean)

/**
 * The mpv engine (libmpv, GPLv3 build) of the Comic TV player. One instance plays one video; [release] ends it.
 * Callbacks arrive on the main thread. mpv decodes in software when the device decoder gives no picture,
 * which is what the ExoPlayer path could not do on some Allwinner chips.
 */
internal class TvMpvEngine(context: Context, decoder: String, caFile: File?, private val listener: Listener) {
    interface Listener {
        fun onFileLoaded()
        /** Playback can start or continue: the start of the file, or the point after a seek, is buffered. */
        fun onReady()
        fun onFirstFrame()
        fun onPlaying(playing: Boolean)
        fun onBuffering(buffering: Boolean)
        fun onTracksChanged()
        /** The file ended before any picture or sound could play: the source or the format failed. */
        fun onFailed()
        fun onEnded()
    }

    private val main = Handler(Looper.getMainLooper())
    private val mpv: MPVLib = MPVLib.create(context.applicationContext) ?: throw IllegalStateException("mpv could not be created.")
    @Volatile private var released = false
    var loaded = false; private set
    private var frameSeen = false
    @Volatile var positionMs = 0L; private set
    @Volatile var durationMs = 0L; private set
    @Volatile var paused = false; private set
    @Volatile var buffering = true; private set
    var software = decoder == "software"; private set

    private val observer = object : MPVLib.EventObserver {
        override fun eventProperty(property: String) { if (property == "track-list") post { listener.onTracksChanged() } }
        override fun eventProperty(property: String, value: Long) {
            when (property) {
                "video-out-params/w" -> if (value > 0) post { if (!frameSeen) { frameSeen = true; Log.i(TAG, "firstFrame width=$value"); listener.onFirstFrame() } }
            }
        }
        override fun eventProperty(property: String, value: Double) {
            when (property) {
                "time-pos" -> positionMs = (value * 1000).toLong().coerceAtLeast(0)
                "duration" -> durationMs = (value * 1000).toLong().coerceAtLeast(0)
            }
        }
        override fun eventProperty(property: String, value: Boolean) {
            when (property) {
                "pause" -> { paused = value; post { listener.onPlaying(!value && !buffering) } }
                "paused-for-cache", "seeking" -> { buffering = value; post { if (loaded) { listener.onBuffering(value); listener.onPlaying(!paused && !value) } } }
            }
        }
        override fun eventProperty(property: String, value: String) {}
        override fun event(eventId: Int) {
            when (eventId) {
                MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> post { loaded = true; listener.onFileLoaded(); listener.onTracksChanged() }
                MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> post { buffering = false; listener.onReady(); listener.onBuffering(false); listener.onPlaying(!paused) }
                MPVLib.MpvEvent.MPV_EVENT_END_FILE -> post { if (!loaded) listener.onFailed() else listener.onEnded() }
            }
        }
    }
    private val log = object : MPVLib.LogObserver {
        override fun logMessage(prefix: String, level: Int, text: String) {
            if (level <= MPVLib.MpvLogLevel.MPV_LOG_LEVEL_WARN) Log.w(TAG, "[$prefix] ${text.trimEnd()}")
        }
    }

    init {
        mpv.setOptionString("config", "no")
        mpv.setOptionString("vo", "gpu")
        mpv.setOptionString("gpu-context", "android")
        mpv.setOptionString("opengl-es", "yes")
        // mediacodec-copy copies the device decoder output back to memory, so a decoder that outputs nothing is visible as
        // "no frame" and the player can turn to software decoding; "no" decodes with FFmpeg from the start.
        mpv.setOptionString("hwdec", hwdec(decoder))
        mpv.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        mpv.setOptionString("ao", "audiotrack,opensles")
        mpv.setOptionString("audio-set-media-role", "yes")
        // The same network cache budget as the ExoPlayer path: a few seconds to start, bounded memory.
        mpv.setOptionString("cache", "yes")
        mpv.setOptionString("demuxer-max-bytes", "48MiB")
        mpv.setOptionString("demuxer-max-back-bytes", "16MiB")
        mpv.setOptionString("cache-pause-initial", "no")
        mpv.setOptionString("network-timeout", "30")
        mpv.setOptionString("tls-verify", "yes")
        caFile?.let { mpv.setOptionString("tls-ca-file", it.absolutePath) }
        mpv.setOptionString("input-default-bindings", "no")
        mpv.setOptionString("input-vo-keyboard", "no")
        mpv.setOptionString("osd-level", "0")
        mpv.setOptionString("save-position-on-quit", "no")
        mpv.setOptionString("keep-open", "yes")
        mpv.setOptionString("idle", "yes")
        mpv.setOptionString("force-window", "no")
        mpv.setOptionString("sub-auto", "no")
        mpv.setOptionString("slang", "pob,pb,pt-BR,por,pt,eng,en")
        mpv.setOptionString("alang", "pob,pt-BR,por,pt")
        mpv.setOptionString("embeddedfonts", "yes")
        mpv.init()
        mpv.addObserver(observer)
        mpv.addLogObserver(log)
        mpv.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        mpv.observeProperty("paused-for-cache", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        mpv.observeProperty("seeking", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        mpv.observeProperty("track-list", MPVLib.MpvFormat.MPV_FORMAT_NONE)
        mpv.observeProperty("video-out-params/w", MPVLib.MpvFormat.MPV_FORMAT_INT64)
    }

    private fun post(block: () -> Unit) { main.post { if (!released) block() } }

    // ---- surface ----

    fun attach(surface: Surface) {
        if (released) return
        mpv.attachSurface(surface)
        mpv.setOptionString("force-window", "yes")
        mpv.setPropertyString("vo", "gpu")
    }
    fun resize(width: Int, height: Int) { if (!released) mpv.setPropertyString("android-surface-size", "${width}x$height") }
    fun detach() {
        if (released) return
        mpv.setPropertyString("vo", "null")
        mpv.setPropertyString("force-window", "no")
        mpv.detachSurface()
    }

    // ---- playback ----

    /**
     * [headers] are already validated; mpv takes them as one comma-separated list. A torrent read through the loopback
     * server may wait minutes for peers, so [patientSeconds] keeps mpv from giving up while the torrent searches.
     */
    fun load(url: String, headers: Map<String, String>, startMs: Long, patientSeconds: Int = 30) {
        mpv.setOptionString("network-timeout", patientSeconds.toString())
        if (headers.isNotEmpty()) mpv.setOptionString("http-header-fields", headers.entries.joinToString(",") { "${it.key}: ${it.value}" })
        if (startMs > 0) mpv.setOptionString("start", "%.3f".format(java.util.Locale.ROOT, startMs / 1000.0))
        mpv.command(arrayOf("loadfile", url, "replace"))
    }
    fun play() { if (!released) mpv.setPropertyBoolean("pause", false) }
    fun pause() { if (!released) mpv.setPropertyBoolean("pause", true) }
    fun seek(ms: Long) { if (!released) mpv.command(arrayOf("seek", "%.3f".format(java.util.Locale.ROOT, ms / 1000.0), "absolute")) }
    val playing get() = !paused && !buffering

    /** Turns to FFmpeg software decoding on the fly; mpv reinitializes the video decoder at the same position. */
    fun useSoftware() { if (released) return; software = true; mpv.setPropertyString("hwdec", "no") }

    /** 0 fit, 1 fill (crop), 2 stretch. */
    fun aspect(mode: Int) {
        if (released) return
        mpv.setPropertyString("keepaspect", if (mode == 2) "no" else "yes")
        mpv.setPropertyDouble("panscan", if (mode == 1) 1.0 else 0.0)
    }

    // ---- tracks ----

    fun tracks(): List<MpvTrack> {
        if (released) return emptyList()
        val count = mpv.getPropertyInt("track-list/count") ?: return emptyList()
        return (0 until count.coerceAtMost(200)).mapNotNull { i ->
            val p = "track-list/$i/"
            val type = mpv.getPropertyString(p + "type") ?: return@mapNotNull null
            val id = mpv.getPropertyInt(p + "id") ?: return@mapNotNull null
            MpvTrack(id, type, mpv.getPropertyString(p + "lang").orEmpty(), mpv.getPropertyString(p + "title").orEmpty(),
                mpv.getPropertyString(p + "codec").orEmpty(), mpv.getPropertyInt(p + "demux-channel-count") ?: 0,
                mpv.getPropertyBoolean(p + "selected") == true, mpv.getPropertyBoolean(p + "external") == true,
                mpv.getPropertyBoolean(p + "forced") == true)
        }
    }
    fun hasVideo() = tracks().any { it.type == "video" }
    fun selectSubtitle(id: Int?) { if (!released) mpv.setPropertyString("sid", id?.toString() ?: "no") }
    fun selectAudio(id: Int) { if (!released) mpv.setPropertyString("aid", id.toString()) }
    /** Replaces the external subtitle file: the previous one is removed so the list keeps only the current file. */
    fun addSubtitle(file: File, title: String, lang: String) {
        if (released) return
        tracks().filter { it.type == "sub" && it.external }.forEach { mpv.command(arrayOf("sub-remove", it.id.toString())) }
        mpv.command(arrayOf("sub-add", file.absolutePath, "select", title.ifBlank { "Legenda" }, lang.ifBlank { "und" }))
    }

    /** The user's style for SRT/VTT, and for ASS when the original style is off. */
    fun style(p: TvSubtitlePrefs) = runCatching {
        if (released) return@runCatching
        fun hex(argb: Int) = "#%08X".format(argb)
        mpv.setPropertyString("sub-ass-override", if (p.originalAssStyle) "scale" else "force")
        mpv.setPropertyDouble("sub-scale", p.sizePercent / 100.0)
        mpv.setPropertyString("sub-color", hex(TvSubtitlePrefs.COLORS[p.color]))
        mpv.setPropertyString("sub-back-color", when (p.background) { 0 -> "#00000000"; 1 -> "#99000000"; else -> "#FF000000" })
        mpv.setPropertyString("sub-border-style", if (p.background == 0) "outline-and-shadow" else "background-box")
        mpv.setPropertyDouble("sub-border-size", if (p.edge == 1) 2.5 else 0.0)
        mpv.setPropertyDouble("sub-shadow-offset", if (p.edge == 2) 2.0 else 0.0)
        // sub-margin-y counts pixels of a 720-line screen.
        mpv.setPropertyInt("sub-margin-y", (p.bottomPercent * 72 / 10).coerceIn(0, 300))
    }

    fun release() {
        if (released) return
        released = true
        main.removeCallbacksAndMessages(null)
        runCatching { mpv.removeObserver(observer) }
        runCatching { mpv.removeLogObserver(log) }
        runCatching { mpv.command(arrayOf("stop")) }
        runCatching { mpv.destroy() }
    }

    companion object {
        private const val TAG = "ComicTvMpv"
        fun hwdec(decoder: String) = if (decoder == "software") "no" else "mediacodec-copy"

        /**
         * mpv's TLS does not read Android's certificate store: the system authorities are written once to a PEM file,
         * so HTTPS sources stay verified. Rewritten when the system list changes size.
         */
        @Synchronized fun caFile(context: Context): File? = runCatching {
            val store = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
            val certs = store.aliases().toList().mapNotNull { store.getCertificate(it) as? X509Certificate }
            val file = File(context.filesDir, "mpv/cacert.pem")
            val marker = File(context.filesDir, "mpv/cacert.count")
            if (file.isFile && marker.isFile && marker.readText() == certs.size.toString()) return@runCatching file
            file.parentFile?.mkdirs()
            val encoder = Base64.getMimeEncoder(64, "\n".toByteArray())
            val tmp = File(file.parentFile, "cacert.pem.part")
            tmp.bufferedWriter().use { out ->
                for (c in certs) { out.write("-----BEGIN CERTIFICATE-----\n"); out.write(encoder.encodeToString(c.encoded)); out.write("\n-----END CERTIFICATE-----\n") }
            }
            if (!tmp.renameTo(file)) { file.delete(); check(tmp.renameTo(file)) }
            marker.writeText(certs.size.toString())
            file
        }.getOrNull()
    }
}
