package com.pelican.comictv.tv

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.annotation.MainThread
import androidx.media3.common.*
import androidx.media3.datasource.*
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.AssHandlerConfig
import io.github.peerless2012.ass.media.kt.withAssMkvSupport
import io.github.peerless2012.ass.media.kt.withAssSupport
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.media.type.AssRenderType
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.concurrent.TimeUnit

/** One subtitle or audio choice as the TV and the phone list it. */
data class TvTrackInfo(val id:String,val label:String,val selected:Boolean)

/** Public snapshot contains no source URL, headers, tracker address or addon credentials. */
data class TvPlaybackState(
    val active: Boolean = false,
    @Transient val poster: String = "",
    @Transient val background: String = "",
    val firstFrame: Boolean = false,
    val noVideo: Boolean = false,
    val storageWarning: String = "",
    val title: String = "",
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val message: String = "",
    val torrent: Boolean = false,
    val playerReady: Boolean = false,
    /** True after the first frame can play: until then pause/continue stay disabled. */
    val canPlay: Boolean = false,
    /** What is happening now: connecting, filling the start, loading. Empty while playing normally. */
    val stage: String = "",
    val subtitles: List<TvTrackInfo> = emptyList(),
    val audio: List<TvTrackInfo> = emptyList(),
    val subtitleDelayMs: Long = 0,
    val subtitleBusy: Boolean = false,
    /** The subtitle add-ons are being asked again. */
    val subtitleSearching: Boolean = false,
    /** Result of the last subtitle search or import, shown in the subtitle panel. */
    val subtitleNote: String = "",
    /** Name of the external player in use, empty for the Comic TV player. */
    val external: String = "",
    /** Another source of the same video can be tried. */
    val hasNext: Boolean = false,
    /** The torrent found no connection in time: the player offers the next source, while this one keeps trying. */
    val suggestNext: Boolean = false,
    /** Engine of the Comic TV player: "mpv" or "exo"; empty before it starts and for external players. */
    val engine: String = "",
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class TvPlaybackController(private val context: Context, private val repository: TvRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(TvPlaybackState())
    val state = mutable.asStateFlow()
    var player: ExoPlayer? = null
        private set
    /** mpv engine of the current video, when the Comic TV player runs with mpv. */
    internal var mpv: TvMpvEngine? = null
        private set
    /** Loopback server that hands the torrent bytes to mpv. */
    private var mpvServer: TvLocalStreamServer? = null
    /** Release of the previous mpv instance, which can wait for its network threads; the next one starts after it. */
    private var mpvRelease: Job? = null
    /** The mpv start failed before playing: the same video was handed to ExoPlayer once. */
    private var exoFallback = false
    private var surface: android.view.Surface? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var aspectMode = 0
    /** libass renderer of the current player, attached by the screen to its subtitle view. */
    var assHandler: AssHandler? = null
        private set
    private val launchRequests = MutableSharedFlow<TvExternalLaunch>(extraBufferCapacity = 1)
    val launches = launchRequests.asSharedFlow()
    private var job: Job? = null
    private var ticker: Job? = null
    private var subtitleJob: Job? = null
    private var delayJob: Job? = null
    private var generation = 0L
    private var torrent: TvTorrentSession? = null
    private var server: TvLocalStreamServer? = null
    private var meta: TvMeta? = null
    private var video: TvVideo? = null
    private var stream: TvStream? = null
    /** The source list the video was started from, for "Tentar próxima fonte". */
    private var alternatives: List<TvStream> = emptyList()
    private var startedAt = 0L
    private var playingWithoutFrameMs = 0L
    private var mediaUri: String = ""
    private var dataFactory: DataSource.Factory? = null
    private var external: List<TvSubtitle> = emptyList()
    private var subtitlesLoaded = false
    private val texts = HashMap<String, Pair<String, SubtitleText.Kind>>()
    private var loaded: TvSubtitle? = null
    private var pendingSideload = false
    private var autoPending = false
    private var phoneCount = 0
    /** The video decoder list puts software decoders first: the hardware one produced no frame. */
    private var softwareVideo = false
    private var softwareTried = false
    private val codecSelector = MediaCodecSelector { mime, secure, tunneling ->
        val all = MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling)
        if (softwareVideo && MimeTypes.isVideo(mime)) all.sortedBy { if (it.softwareOnly) 0 else 1 } else all
    }
    private val memory = SubtitleMemory(File(context.filesDir, "tv-subtitles-saved"))
    /** Choice kept for this video, applied once instead of the automatic choice. */
    private var savedChoice: SubtitleMemory.Choice? = null
    /** The loaded external subtitle was picked by the user, so it is kept for this video. */
    private var userPicked = false
    /** The TV file picker is open: leaving the app for it must not stop the video. */
    private var picking = false
    private var resumeAfterPick = false
    private val videoKey get() = meta?.let { m -> video?.let { "${m.id}|${it.id}" } }
    /** Fixed part of the external-player status; the ticker appends connections and speed for torrents. */
    private var externalBase = ""
    private val progressWrites = Channel<TvProgress>(Channel.UNLIMITED)
    private val http = okhttp3.OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val subtitleDir = File(context.cacheDir, "tv-subtitles")
    /** One writer at a time, so a later choice never lands before an earlier one. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val memoryIo = Dispatchers.IO.limitedParallelism(1)

    init {
        // Keep writes in playback order: an older periodic save must not overtake the final stop save.
        scope.launch(Dispatchers.IO) {
            for (p in progressWrites) runCatching { repository.progress(p.meta, p.video, p.positionMs, p.durationMs) }
        }
    }

    private val prefs get() = repository.state.value.video

    /** Starts [stream] in the Comic TV player, or in the external player chosen in the settings. */
    @MainThread fun play(meta: TvMeta, video: TvVideo, stream: TvStream, position: Long = 0, externalPlayer: String? = null,
        sources: List<TvStream> = emptyList()) {
        stop()
        this.meta = meta
        this.video = video
        this.stream = stream
        alternatives = sources.filter { it.externalUrl.isBlank() }.take(500)
        startedAt = System.nanoTime()
        playingWithoutFrameMs = 0L
        softwareVideo = prefs.decoder == "software"
        softwareTried = false
        val token = ++generation
        val isTorrent = StreamRank.torrent(stream)
        val player = externalPlayer ?: prefs.player
        val outside = player !in TvVideoPrefs.INTERNAL
        exoFallback = false
        external = stream.subtitles
        subtitlesLoaded = false
        autoPending = true
        mutable.value = TvPlaybackState(
            active = true,
            poster = meta.poster,
            background = meta.background,
            title = meta.name + if (video.id != meta.id) " · ${video.title}" else "",
            buffering = true,
            positionMs = position.coerceAtLeast(0),
            torrent = isTorrent,
            stage = if (isTorrent) "Iniciando torrent…" else "Abrindo vídeo…",
            external = if (outside) TvVideoPrefs.PLAYER_NAMES[player].orEmpty() else "",
            hasNext = nextSource(stream) != null,
        )
        savedChoice = null
        userPicked = false
        subtitleJob = scope.launch {
            val key = "${meta.id}|${video.id}"
            val kept = if (prefs.subtitles.remember) withContext(Dispatchers.IO) { runCatching { keptSubtitles(key) }.getOrDefault(emptyList()) } else emptyList()
            if (token != generation) return@launch
            if (prefs.subtitles.remember) savedChoice = withContext(Dispatchers.IO) { runCatching { memory.choice(key) }.getOrNull() }
            for ((sub, text) in kept) texts[sub.id] = text
            val found = try { withContext(Dispatchers.IO) { repository.subtitles(meta, video) } } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
            if (token != generation) return@launch
            external = (kept.map { it.first } + external + found).distinctBy { it.url.ifBlank { it.id } }
            subtitlesLoaded = true
            publishTracks()
            tryAutoSubtitle()
        }
        ticker = scope.launch { tick(token) }
        job = scope.launch {
            try {
                val factory: DataSource.Factory
                val uri: String
                if (isTorrent) {
                    val session = TvTorrentSession(context, repository.state.value.cacheGiB, prefs.publicTrackers)
                    torrent = session
                    session.prepare(stream)
                    ensureActive()
                    if (token != generation) return@launch
                    if(session.size>context.cacheDir.usableSpace)mutable.update {
                        it.copy(storageWarning="O vídeo é maior que o espaço livre. Será usado um buffer pequeno; a reprodução pode parar se faltar espaço.")
                    }
                    factory = RoutingFactory(DataSource.Factory { TorrentDataSource(session) })
                    uri = "torrent://video"
                } else {
                    require(StremioLinks.http(stream.url)) { "Fonte de vídeo incompatível." }
                    val headers = stream.headers.filter { (k, v) ->
                        k.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,100}")) && v.length <= 8192 &&
                            v.none { it == '\r' || it == '\n' || it == '\u0000' } &&
                            k.lowercase() !in setOf("host", "content-length", "connection")
                    }
                    if (outside && headers.isNotEmpty()) throw IllegalStateException("Esta fonte só toca no player do Comic TV, porque exige cabeçalhos próprios.")
                    factory = DefaultDataSource.Factory(context, OkHttpDataSource.Factory(http).setDefaultRequestProperties(headers))
                    uri = stream.url
                }
                ensureActive()
                if (token != generation) return@launch
                if (outside) { launchExternal(token, player, uri, isTorrent, position); return@launch }
                dataFactory = factory
                mediaUri = uri
                val headers = if (isTorrent) emptyMap() else stream.headers.filter { (k, v) ->
                    k.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,100}")) && v.length <= 8192 &&
                        v.none { it == '\r' || it == '\n' || it == '\u0000' } && k.lowercase() !in setOf("host", "content-length", "connection")
                }
                // mpv takes headers as one comma-separated list, so a value with a comma goes to ExoPlayer instead.
                if (player == "internal" && headers.values.none { ',' in it } && startMpv(token, uri, isTorrent, headers, position)) return@launch
                mutable.update { it.copy(engine = "exo") }
                startPlayer(token, position, playWhenReady = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                fail(token, e.message ?: "Não foi possível iniciar esta fonte.")
            } catch (e: Exception) {
                fail(token, if (isTorrent && e is java.io.IOException) e.message ?: "Torrent indisponível." else "Não foi possível iniciar esta fonte. Volte e escolha outra.")
            } catch (_: LinkageError) {
                fail(token, "Reprodução indisponível nesta arquitetura do aparelho.")
            }
        }
    }

    /** Starts [uri] in mpv; false when the engine cannot load on this device, so the caller uses ExoPlayer. */
    private suspend fun startMpv(token: Long, uri: String, isTorrent: Boolean, headers: Map<String, String>, position: Long): Boolean {
        mpvRelease?.join()
        if (token != generation) return true
        val url = if (isTorrent) {
            val session = torrent ?: return false
            val source = object : TvByteSource {
                override val size get() = session.size
                override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int) = session.read(position, buffer, offset, length)
            }
            val local = withContext(Dispatchers.IO) { TvLocalStreamServer(source, mimeOf(session.fileName)).start() }
            if (token != generation) { runCatching { local.close() }; return true }
            mpvServer = local
            local.videoUrl
        } else uri
        // Reading the system certificate store takes a moment: done off the main thread before the engine starts.
        val authorities = withContext(Dispatchers.IO) { TvMpvEngine.caFile(context) }
        if (token != generation) { mpvServer?.let { runCatching { it.close() } }; mpvServer = null; return true }
        val engine = try { TvMpvEngine(context, prefs.decoder, authorities, mpvListener(token)) }
        catch (e: LinkageError) { Log.e("ComicTvMpv", "engine unavailable", e); null }
        catch (e: Exception) { Log.e("ComicTvMpv", "engine failed", e); null }
        if (engine == null) { mpvServer?.let { runCatching { it.close() } }; mpvServer = null; return false }
        mpv = engine
        softwareVideo = engine.software
        surface?.let { engine.attach(it); engine.resize(surfaceWidth, surfaceHeight) }
        engine.aspect(aspectMode)
        engine.style(prefs.subtitles)
        mutable.update { it.copy(playerReady = true, engine = "mpv") }
        engine.load(url, headers, position, patientSeconds = if (isTorrent) 900 else 30)
        return true
    }

    private fun mpvListener(token: Long) = object : TvMpvEngine.Listener {
        override fun onFileLoaded() { if (token == generation) Log.i("ComicTvMpv", "fileLoaded") }
        override fun onReady() {
            if (token == generation) mutable.update { it.copy(canPlay = true, buffering = false, message = "") }
        }
        override fun onFirstFrame() { if (token == generation) mutable.update { it.copy(firstFrame = true, noVideo = false) } }
        override fun onPlaying(playing: Boolean) { if (token == generation) mutable.update { it.copy(playing = playing) } }
        override fun onBuffering(buffering: Boolean) { if (token == generation) mutable.update { it.copy(buffering = buffering) } }
        override fun onTracksChanged() {
            if (token != generation) return
            publishTracks()
            tryAutoSubtitle()
        }
        override fun onFailed() {
            if (token != generation) return
            Log.w("ComicTvMpv", "file did not open")
            // The same bytes once more with ExoPlayer, which opens a few formats mpv refuses, before giving up.
            if (!exoFallback && dataFactory != null) {
                exoFallback = true
                releaseMpv()
                mutable.update { it.copy(engine = "exo", playerReady = false, canPlay = false, stage = "Tentando com o ExoPlayer…") }
                startPlayer(token, state.value.positionMs, playWhenReady = true)
                loaded?.let { loadExternal(it) }
            } else fail(token, "Não foi possível reproduzir esta fonte. Volte e escolha outra.")
        }
        override fun onEnded() {
            if (token != generation) return
            saveProgress()
            mutable.update { it.copy(playing = false) }
        }
    }

    /** The surface of the player screen; mpv draws there while it exists. */
    @MainThread fun surfaceReady(value: android.view.Surface, width: Int, height: Int) {
        surface = value; surfaceWidth = width; surfaceHeight = height
        mpv?.let { it.attach(value); it.resize(width, height) }
    }
    @MainThread fun surfaceChanged(width: Int, height: Int) { surfaceWidth = width; surfaceHeight = height; mpv?.resize(width, height) }
    @MainThread fun surfaceGone() { mpv?.detach(); surface = null }
    /** 0 fit, 1 fill, 2 stretch; ExoPlayer applies it in its own view. */
    @MainThread fun setAspect(mode: Int) { aspectMode = mode; mpv?.aspect(mode) }
    /** The subtitle style changed in the settings during the video. */
    @MainThread fun subtitleStyle(p: TvSubtitlePrefs) { mpv?.style(p) }

    private fun releaseMpv() {
        // The server can exist without an engine when the start was cancelled halfway.
        mpvServer?.let { s -> mpvServer = null; runCatching { s.close() } }
        val old = mpv ?: return
        mpv = null
        old.detach()
        val previous = mpvRelease
        mpvRelease = scope.launch(Dispatchers.IO) { previous?.join(); runCatching { old.release() } }
    }

    private fun buildPlayer(factory: DataSource.Factory): ExoPlayer {
        val renderers = DefaultRenderersFactory(context)
            // Device decoders first; FFmpeg (audio) and libgav1 (AV1) only for tracks the device cannot decode.
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(codecSelector)
        val extractors = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES)
        // A shorter start buffer lets torrents begin as soon as a few seconds are on disk.
        val load = DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 50_000, 1_500, 3_000).build()
        val builder = ExoPlayer.Builder(context).setLoadControl(load)
        if (!prefs.subtitles.originalAssStyle) {
            assHandler = null
            return builder.setMediaSourceFactory(DefaultMediaSourceFactory(factory, extractors)).setRenderersFactory(renderers).build()
        }
        val handler = AssHandler(AssRenderType.OVERLAY_CANVAS, AssHandlerConfig())
        val parser = AssSubtitleParserFactory(handler)
        val sources = DefaultMediaSourceFactory(factory, extractors.withAssMkvSupport(parser, handler)).setSubtitleParserFactory(parser)
        return builder.setMediaSourceFactory(sources).setRenderersFactory(renderers.withAssSupport(handler)).build().also {
            handler.init(it)
            assHandler = handler
        }
    }

    private fun mediaItem(subtitle: File?, sub: TvSubtitle?, kind: SubtitleText.Kind?): MediaItem {
        val item = MediaItem.Builder().setUri(mediaUri)
        if (subtitle != null && sub != null && kind != null) item.setSubtitleConfigurations(listOf(
            MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(subtitle)).setMimeType(kind.mime)
                .setLanguage(sub.lang.ifBlank { null }).setLabel(subtitleLabel(sub)).setId(SIDELOAD_ID)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()))
        return item.build()
    }

    private fun startPlayer(token: Long, position: Long, playWhenReady: Boolean) {
        val factory = dataFactory ?: return
        val exo = buildPlayer(factory)
        player = exo
        exo.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
                if(token==generation)Log.i("ComicTvVideo", "decoder=$decoderName initMs=$initializationDurationMs")
            }
            override fun onVideoInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
                if(token==generation)Log.i("ComicTvVideo", "mime=${format.sampleMimeType} codecs=${format.codecs} size=${format.width}x${format.height} fps=${format.frameRate} color=${format.colorInfo}")
            }
            override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                if(token==generation)Log.i("ComicTvVideo", "dropped=$droppedFrames elapsedMs=$elapsedMs")
            }
            override fun onVideoDisabled(eventTime: AnalyticsListener.EventTime, decoderCounters: DecoderCounters) {
                if(token==generation)Log.i("ComicTvVideo", "videoDisabled rendered=${decoderCounters.renderedOutputBufferCount} dropped=${decoderCounters.droppedBufferCount}")
            }
        })
        exo.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                if(token==generation) {
                    Log.i("ComicTvVideo", "firstFrame positionMs=${exo.currentPosition}")
                    mutable.update { it.copy(firstFrame=true,noVideo=false) }
                }
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if(token==generation)Log.i("ComicTvVideo", "videoSize=${videoSize.width}x${videoSize.height} ratio=${videoSize.pixelWidthHeightRatio}")
            }
            override fun onPlayerError(error: PlaybackException) {
                if (token == generation) { Log.e("ComicTvVideo", "playerError code=${error.errorCodeName}");scope.launch { fail(token, explain(error)) } }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (token == generation) mutable.update { it.copy(playing = isPlaying) }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (token == generation) mutable.update {
                    val ready = playbackState == Player.STATE_READY
                    it.copy(buffering = playbackState == Player.STATE_BUFFERING, canPlay = it.canPlay || ready,
                        message = if (ready || playbackState == Player.STATE_ENDED) "" else it.message)
                }
            }
            override fun onTracksChanged(tracks: Tracks) {
                if (token != generation) return
                for(group in tracks.groups.filter { it.type==C.TRACK_TYPE_VIDEO })for(t in 0 until group.length) {
                    val f=group.getTrackFormat(t)
                    Log.i("ComicTvVideo", "track supported=${group.isTrackSupported(t)} selected=${group.isTrackSelected(t)} mime=${f.sampleMimeType} codecs=${f.codecs} size=${f.width}x${f.height} color=${f.colorInfo}")
                }
                if (pendingSideload) selectSideloaded(tracks)
                publishTracks()
                tryAutoSubtitle()
            }
        })
        // Force a new snapshot when the view must attach a newly created player after metadata loading.
        mutable.update { it.copy(playerReady = true) }
        exo.setMediaItem(mediaItem(null, null, null))
        exo.prepare()
        if (position > 0) exo.seekTo(position)
        exo.playWhenReady = playWhenReady
    }

    /** The hardware decoder started but showed nothing: restart the same video here with software decoders first. */
    @MainThread private fun switchToSoftwareDecoder(token: Long) {
        mpv?.let { m ->
            softwareTried = true
            softwareVideo = true
            Log.i("ComicTvMpv", "switching to the software video decoder at positionMs=${m.positionMs}")
            m.useSoftware()
            mutable.update { it.copy(stage = "Trocando para o decodificador por software…") }
            return
        }
        val exo = player ?: return
        softwareTried = true
        softwareVideo = true
        val position = exo.currentPosition.coerceAtLeast(0)
        val sub = loaded
        Log.i("ComicTvVideo", "switching to the software video decoder at positionMs=$position")
        // Torrent reads blocked in the loader are interrupted by release(), which ends them cleanly.
        val oldAss = assHandler
        player = null
        assHandler = null
        runCatching { exo.release() }
        runCatching { oldAss?.release() }
        pendingSideload = false
        mutable.update { it.copy(playerReady = false, canPlay = false, playing = false, buffering = true, firstFrame = false,
            noVideo = false, stage = "Trocando para o decodificador por software…") }
        startPlayer(token, position, playWhenReady = true)
        if (sub != null) loadExternal(sub)
    }

    private fun explain(error: PlaybackException): String {
        var cause: Throwable? = error
        while (cause != null) {
            val text = cause.message.orEmpty()
            if (cause is java.io.IOException && (text.startsWith("O torrent") || text.startsWith("Espaço livre"))) return text
            cause = cause.cause
        }
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
                "O aparelho não conseguiu decodificar este vídeo. Tente outra fonte ou um player externo."
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED, PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ->
                "Formato de arquivo não reconhecido. Tente outra fonte ou um player externo."
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                "A fonte parou de responder. Volte e escolha outra."
            else -> "Não foi possível reproduzir esta fonte. Volte e escolha outra."
        }
    }

    private suspend fun tick(token: Long) {
        var ticks = 0
        var lastTick = android.os.SystemClock.elapsedRealtime()
        while (currentCoroutineContext().isActive && token == generation) {
            val clockNow = android.os.SystemClock.elapsedRealtime()
            val elapsed = (clockNow-lastTick).coerceIn(0,2000)
            lastTick = clockNow
            val exo = player
            val m = mpv
            if (exo != null || m != null) {
                val duration = if (exo != null) exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0 else m!!.durationMs
                mutable.update { it.copy(positionMs = currentPosition(), durationMs = duration) }
                if((exo?.isPlaying ?: m!!.playing)&&!state.value.firstFrame)playingWithoutFrameMs+=elapsed
                // Audio-only files never show a frame: mpv lists the tracks, so it only waits when there is a video track.
                if(playingWithoutFrameMs>=10_000&&!state.value.firstFrame&&!state.value.noVideo&&(m==null||m.hasVideo())) {
                    Log.w("ComicTvVideo", "noFirstFrame playingMs=$playingWithoutFrameMs engine=${if (m != null) "mpv" else "exo"} software=$softwareVideo")
                    if (prefs.decoder == "auto" && !softwareTried && !softwareVideo) {
                        switchToSoftwareDecoder(token)
                        playingWithoutFrameMs = 0L
                    } else mutable.update { it.copy(noVideo=true) }
                }
                if (++ticks % 10 == 0) saveProgress()
            }
            val now = mutable.value
            val stats = torrent?.let { withContext(Dispatchers.IO) { it.stats() } }
            val seconds = (System.nanoTime() - startedAt) / 1_000_000_000L
            val stage = when {
                now.external.isNotBlank() && stats != null && !stats.metadata -> searching(stats, seconds)
                now.external.isNotBlank() -> if (stats != null && stats.metadata && externalBase.isNotBlank()) "$externalBase · ${connections(stats.peers)} · ${speed(stats.downloadRate)}" else externalBase.ifBlank { now.stage }
                stats != null && !stats.metadata -> searching(stats, seconds)
                stats != null && !now.canPlay -> {
                    val percent = if (stats.target > 0) (stats.done * 100 / stats.target).coerceIn(0, 99) else 0
                    "Preparando o início: $percent% · ${connections(stats.peers)} · ${speed(stats.downloadRate)}"
                }
                stats != null && now.buffering -> "Carregando · ${connections(stats.peers)} · ${speed(stats.downloadRate)}"
                !now.canPlay || now.buffering -> if (now.message.isBlank()) "Carregando vídeo…" else ""
                else -> ""
            }
            if (stage != now.stage) mutable.update { it.copy(stage = stage) }
            // No connection after the wait: offer the next source once, without stopping this one.
            val stuck = stats != null && stats.peers == 0 && !now.canPlay && seconds >= SUGGEST_AFTER_S
            if (stuck && !now.suggestNext && now.hasNext) mutable.update { it.copy(suggestNext = true) }
            if (stats != null && stats.peers > 0 && now.suggestNext) mutable.update { it.copy(suggestNext = false) }
            server?.let { s -> if (System.currentTimeMillis() - s.lastUse > EXTERNAL_IDLE_MS) { stopExternal(); return } }
            delay(500)
        }
    }

    private fun searching(stats: TorrentStats, seconds: Long): String {
        if (stats.candidates == 0 && stats.peers == 0) return "Procurando conexões · $seconds s"
        val candidates = if (stats.candidates == 1) "1 candidato" else "${stats.candidates} candidatos"
        val connected = if (stats.peers == 1) "1 conectada" else "${stats.peers} conectadas"
        return "Procurando conexões: $candidates, $connected · $seconds s"
    }

    /** The next source after [current] in the list the video was started from. */
    private fun nextSource(current: TvStream): TvStream? {
        val index = alternatives.indexOf(current)
        return if (index < 0) alternatives.firstOrNull { it != current } else alternatives.getOrNull(index + 1)
    }

    /** Plays the next source of the same video, from where the current one was. */
    @MainThread fun playNext() {
        val m = meta ?: return
        val v = video ?: return
        val current = stream ?: return
        val next = nextSource(current) ?: return
        val list = alternatives
        val position = currentPosition().takeIf { state.value.canPlay && it > 0 } ?: 0
        play(m, v, next, position, sources = list)
    }

    /** A per-video override: never writes the default preference. */
    @MainThread fun switchPlayer(id: String) {
        if(id !in TvVideoPrefs.PLAYERS)return
        val m=meta?:return
        val v=video?:return
        val source=stream?:return
        val position=currentPosition()
        val sources=alternatives
        play(m,v,source,position,externalPlayer=id,sources=sources)
    }

    private fun currentPosition(): Long = player?.currentPosition?.coerceAtLeast(0) ?: mpv?.positionMs ?: state.value.positionMs

    private fun connections(n: Int) = if (n == 1) "1 conexão" else "$n conexões"
    private fun speed(bytes: Long) = when {
        bytes >= 1024 * 1024 -> "%.1f MB/s".format(bytes / 1048576.0).replace('.', ',')
        else -> "${bytes / 1024} KB/s"
    }

    // ---- subtitles and audio ----

    private fun subtitleLabel(s: TvSubtitle): String {
        if (s.id.startsWith("saved-")) return (s.label.ifBlank { SubtitleLanguages.name(s.lang, "") } + " · Guardada na TV").take(160)
        val language = SubtitleLanguages.name(s.lang, s.label)
        val origin = when {
            s.id.startsWith("phone-") -> "Enviada pelo celular"
            s.id.startsWith("file-") -> "Importada na TV"
            s.id.startsWith("saved-") -> "Guardada na TV"
            else -> s.addonName.ifBlank { "Complemento" }
        }
        return listOf(language, s.label.takeIf { it.isNotBlank() && it != s.lang && it != language }, origin).filterNotNull().joinToString(" · ").take(160)
    }

    private fun formatLabel(f: Format, audio: Boolean): String {
        val language = f.language?.let { SubtitleLanguages.name(it, f.label.orEmpty()) }
        val codec = when (f.sampleMimeType) {
            MimeTypes.TEXT_SSA -> "ASS"; MimeTypes.APPLICATION_SUBRIP -> "SRT"; MimeTypes.TEXT_VTT -> "VTT"
            MimeTypes.APPLICATION_PGS -> "PGS"; MimeTypes.APPLICATION_DVBSUBS -> "DVB"
            MimeTypes.AUDIO_AC3 -> "AC-3"; MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "E-AC-3"
            MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_EXPRESS -> "DTS"; MimeTypes.AUDIO_TRUEHD -> "TrueHD"
            MimeTypes.AUDIO_AAC -> "AAC"; MimeTypes.AUDIO_OPUS -> "Opus"; MimeTypes.AUDIO_FLAC -> "FLAC"; MimeTypes.AUDIO_MPEG -> "MP3"
            else -> null
        }
        val channels = if (audio && f.channelCount > 0) "${f.channelCount} canais" else null
        return listOfNotNull(f.label?.takeIf { it.isNotBlank() }, language, codec, channels, if (f.selectionFlags and C.SELECTION_FLAG_FORCED != 0) "forçada" else null)
            .distinct().joinToString(" · ").ifBlank { if (audio) "Áudio" else "Legenda do vídeo" }.take(160)
    }

    private fun groups(type: Int) = player?.currentTracks?.groups.orEmpty().withIndex().filter { it.value.type == type }

    private fun embeddedKey(g: Tracks.Group, groupIndex: Int, track: Int) = (g.getTrackFormat(track).id ?: "$groupIndex:$track").take(80)

    private fun isSideloaded(g: Tracks.Group) = (0 until g.length).any { g.getTrackFormat(it).id?.contains(SIDELOAD_ID) == true }

    private fun mpvLabel(t: MpvTrack, audio: Boolean): String {
        val language = t.lang.takeIf { it.isNotBlank() }?.let { SubtitleLanguages.name(it, t.title) }
        val codec = when (t.codec.lowercase()) {
            "ass", "ssa" -> "ASS"; "subrip", "srt" -> "SRT"; "webvtt" -> "VTT"; "hdmv_pgs_subtitle" -> "PGS"; "dvb_subtitle" -> "DVB"
            "ac3" -> "AC-3"; "eac3" -> "E-AC-3"; "dts" -> "DTS"; "truehd" -> "TrueHD"; "aac" -> "AAC"; "opus" -> "Opus"; "flac" -> "FLAC"; "mp3" -> "MP3"
            else -> t.codec.uppercase().takeIf { it.isNotBlank() }
        }
        val channels = if (audio && t.channels > 0) "${t.channels} canais" else null
        return listOfNotNull(t.title.takeIf { it.isNotBlank() }, language, codec, channels, if (t.forced) "forçada" else null)
            .distinct().joinToString(" · ").ifBlank { if (audio) "Áudio" else "Legenda do vídeo" }.take(160)
    }

    private fun publishTracks() {
        mpv?.let { m ->
            val list = m.tracks()
            val anySub = list.any { it.type == "sub" && it.selected }
            val subs = mutableListOf(TvTrackInfo("off", "Desligadas", !anySub))
            for (t in list) if (t.type == "sub" && !t.external)
                subs += TvTrackInfo("e:m${t.id}", "No vídeo · " + mpvLabel(t, false), t.selected && loaded == null)
            val externalOn = list.any { it.type == "sub" && it.external && it.selected }
            val prefer = prefs.subtitles.preferBrazil
            external.withIndex().sortedBy { SubtitleLanguages.rank(it.value.lang, it.value.label, prefer) }.forEach { (i, s) ->
                subs += TvTrackInfo("x:$i", subtitleLabel(s), externalOn && loaded == s)
            }
            val audio = list.filter { it.type == "audio" }.map { TvTrackInfo("a:m${it.id}", mpvLabel(it, true), it.selected) }
            mutable.update { it.copy(subtitles = subs.take(400), audio = audio.take(50)) }
            return
        }
        val textOff = player?.trackSelectionParameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_TEXT) ?: true
        val subs = mutableListOf(TvTrackInfo("off", "Desligadas", textOff || groups(C.TRACK_TYPE_TEXT).none { it.value.isSelected }))
        for ((index, g) in groups(C.TRACK_TYPE_TEXT)) {
            if (isSideloaded(g)) continue
            for (t in 0 until g.length) if (g.isTrackSupported(t))
                subs += TvTrackInfo("e:" + embeddedKey(g, index, t), "No vídeo · " + formatLabel(g.getTrackFormat(t), false), !textOff && g.isTrackSelected(t) && loaded == null)
        }
        val prefer = prefs.subtitles.preferBrazil
        external.withIndex().sortedBy { SubtitleLanguages.rank(it.value.lang, it.value.label, prefer) }.forEach { (i, s) ->
            subs += TvTrackInfo("x:$i", subtitleLabel(s), !textOff && loaded == s)
        }
        val audio = groups(C.TRACK_TYPE_AUDIO).flatMap { (index, g) ->
            (0 until g.length).filter { g.isTrackSupported(it) }.map { t -> TvTrackInfo("a:" + embeddedKey(g, index, t), formatLabel(g.getTrackFormat(t), true), g.isTrackSelected(t)) }
        }
        mutable.update { it.copy(subtitles = subs.take(400), audio = audio.take(50)) }
    }

    private fun tryAutoSubtitle() {
        val exo = player
        val m = mpv
        if (!autoPending) return
        if (m != null) { if (!m.loaded) return } else if (exo == null || exo.currentTracks.groups.isEmpty()) return
        val waited = !subtitlesLoaded
        if (waited) return
        autoPending = false
        val p = prefs.subtitles
        // The last choice kept for this video comes before the automatic choice, even with it turned off.
        val kept = savedChoice?.takeIf { p.remember }
        savedChoice = null
        if (kept != null) {
            if (kept.id == "off") { setTextDisabled(true); publishTracks(); return }
            val index = external.indexOfFirst { it.id == "saved-${kept.id}" }
            if (index >= 0) {
                userPicked = true
                mutable.update { it.copy(subtitleDelayMs = kept.delayMs) }
                loadExternal(external[index])
                publishTracks()
                return
            }
        }
        if (!p.auto) { setTextDisabled(true); publishTracks(); return }
        var best: Pair<Int, String>? = null
        if (m != null) for (t in m.tracks()) if (t.type == "sub" && !t.external) {
            val rank = SubtitleLanguages.rank(t.lang, t.title, p.preferBrazil)
            if (rank != Int.MAX_VALUE && (best == null || rank < best.first)) best = rank to "e:m${t.id}"
        }
        for ((index, g) in groups(C.TRACK_TYPE_TEXT)) for (t in 0 until g.length) {
            if (!g.isTrackSupported(t)) continue
            val f = g.getTrackFormat(t)
            val rank = SubtitleLanguages.rank(f.language.orEmpty(), f.label.orEmpty(), p.preferBrazil)
            if (rank != Int.MAX_VALUE && (best == null || rank < best.first)) best = rank to "e:" + embeddedKey(g, index, t)
        }
        external.withIndex().forEach { (i, s) ->
            val rank = SubtitleLanguages.rank(s.lang, s.label, p.preferBrazil)
            if (rank != Int.MAX_VALUE && (best == null || rank < best!!.first)) best = rank to "x:$i"
        }
        selectSubtitle(best?.second ?: "off", user = false)
    }

    private fun setTextDisabled(disabled: Boolean) {
        mpv?.let { if (disabled) it.selectSubtitle(null); return }
        val exo = player ?: return
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, disabled).build()
    }

    /**
     * "off", "e:<embedded key>" for a track inside the video, or "x:<n>" for an external subtitle. A [user] choice is
     * kept for this video: an external subtitle with its sync, "off" as off, a track of the video by forgetting the choice.
     */
    @MainThread fun selectSubtitle(id: String, user: Boolean = true) {
        val exo = player
        val m = mpv
        if (exo == null && m == null) return
        autoPending = false
        if (user) savedChoice = null
        when {
            id == "off" -> {
                setTextDisabled(true)
                if (user) { userPicked = false; remember("off") }
            }
            id.startsWith("e:") -> {
                if (user) { userPicked = false; remember(null) }
                val key = id.removePrefix("e:")
                if (m != null) {
                    val track = key.removePrefix("m").toIntOrNull() ?: return
                    if (loaded != null) { loaded = null; mutable.update { it.copy(subtitleDelayMs = 0) } }
                    m.selectSubtitle(track)
                    publishTracks()
                    return
                }
                exo ?: return
                val match = groups(C.TRACK_TYPE_TEXT).firstNotNullOfOrNull { (index, g) ->
                    (0 until g.length).firstOrNull { embeddedKey(g, index, it) == key }?.let { g to it }
                } ?: return
                if (loaded != null) { loaded = null; mutable.update { it.copy(subtitleDelayMs = 0) } }
                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(match.first.mediaTrackGroup, match.second)).build()
            }
            id.startsWith("x:") -> {
                val sub = external.getOrNull(id.removePrefix("x:").toIntOrNull() ?: -1) ?: return
                userPicked = user
                mutable.update { it.copy(subtitleDelayMs = 0) }
                loadExternal(sub)
            }
        }
        publishTracks()
    }

    @MainThread fun selectAudio(id: String) {
        val key = id.removePrefix("a:")
        mpv?.let { m -> key.removePrefix("m").toIntOrNull()?.let(m::selectAudio); publishTracks(); return }
        val exo = player ?: return
        val match = groups(C.TRACK_TYPE_AUDIO).firstNotNullOfOrNull { (index, g) ->
            (0 until g.length).firstOrNull { embeddedKey(g, index, it) == key }?.let { g to it }
        } ?: return
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(match.first.mediaTrackGroup, match.second)).build()
        publishTracks()
    }

    private suspend fun subtitleText(sub: TvSubtitle): Pair<String, SubtitleText.Kind> {
        texts[sub.id]?.let { return it }
        val bytes = withContext(Dispatchers.IO) { repository.client.download(sub.url, SubtitleText.MAX_BYTES) }
        val text = SubtitleText.decode(bytes)
        val kind = SubtitleText.validate(text)
        return (text to kind).also { texts[sub.id] = it }
    }

    private suspend fun subtitleFile(sub: TvSubtitle, delayMs: Long): Pair<File, SubtitleText.Kind> {
        val (text, kind) = subtitleText(sub)
        return withContext(Dispatchers.IO) {
            subtitleDir.mkdirs()
            subtitleDir.listFiles()?.forEach { it.delete() }
            val file = File(subtitleDir, "${java.util.UUID.randomUUID()}.${kind.extension}")
            file.writeText(SubtitleText.shift(text, kind, delayMs), Charsets.UTF_8)
            Pair(file, kind)
        }
    }

    private fun loadExternal(sub: TvSubtitle) {
        val token = generation
        mutable.update { it.copy(subtitleBusy = true, message = "") }
        scope.launch {
            try {
                val (file, kind) = subtitleFile(sub, state.value.subtitleDelayMs)
                if (token != generation) return@launch
                val m = mpv
                if (m != null) {
                    loaded = sub
                    m.addSubtitle(file, subtitleLabel(sub), sub.lang)
                    if (userPicked) keep(sub)
                    return@launch
                }
                val exo = player ?: return@launch
                loaded = sub
                pendingSideload = true
                val position = exo.currentPosition
                val play = exo.playWhenReady
                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).clearOverridesOfType(C.TRACK_TYPE_TEXT).build()
                exo.setMediaItem(mediaItem(file, sub, kind), position)
                exo.prepare()
                exo.playWhenReady = play
                if (userPicked) keep(sub)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (token == generation) mutable.update { it.copy(message = e.message?.takeIf { m -> m.startsWith("Arquivo") || m.startsWith("Legenda") || m.startsWith("A legenda") } ?: "Não foi possível abrir esta legenda. Escolha outra.") }
            } finally {
                if (token == generation) { mutable.update { it.copy(subtitleBusy = false) }; publishTracks() }
            }
        }
    }

    private fun selectSideloaded(tracks: Tracks) {
        val exo = player ?: return
        val group = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_TEXT && isSideloaded(it) } ?: return
        pendingSideload = false
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0)).build()
    }

    /** Positive values show the subtitle later. Applies to external and phone subtitles, which the TV rewrites. */
    @MainThread fun setSubtitleDelay(ms: Long) {
        val value = ms.coerceIn(-60_000, 60_000)
        val sub = loaded
        if (sub == null) { mutable.update { it.copy(message = "A sincronia vale para legendas de complementos, importadas e enviadas pelo celular.") }; return }
        // Adjusting the sync is a choice: the subtitle, even an automatic one, is kept with it for this video.
        userPicked = true
        mutable.update { it.copy(subtitleDelayMs = value, message = "") }
        delayJob?.cancel()
        delayJob = scope.launch { delay(700); if (loaded == sub) loadExternal(sub) }
    }

    /** A subtitle file sent from the phone panel; it becomes the current subtitle when a video is playing. */
    fun addPhoneSubtitle(name: String, bytes: ByteArray) = addFileSubtitle("phone", name, bytes)

    /**
     * A subtitle file from the phone ([origin] "phone") or opened with the TV file picker ("file"). Invalid files throw
     * before anything changes; a valid one becomes the current subtitle when a video is playing.
     */
    fun addFileSubtitle(origin: String, name: String, bytes: ByteArray) {
        val text = SubtitleText.decode(bytes)
        val kind = SubtitleText.validate(text)
        val lower = name.lowercase()
        val lang = when { Regex("pt[-_ .]?br|pob|brazil|brasil").containsMatchIn(lower) -> "pob"; Regex("(^|[^a-z])(pt|por)([^a-z]|$)").containsMatchIn(lower) -> "por"; Regex("(^|[^a-z])(en|eng)([^a-z]|$)").containsMatchIn(lower) -> "eng"; else -> "" }
        scope.launch {
            val sub = TvSubtitle("$origin-${++phoneCount}", "", lang, name.take(120))
            texts[sub.id] = text to kind
            external = listOf(sub) + external
            if (player != null || mpv != null) selectSubtitle("x:0") else publishTracks()
        }
    }

    /** Asks the subtitle add-ons again, keeping the subtitles already listed and the current choice. */
    @MainThread fun searchSubtitles() {
        val m = meta ?: return
        val v = video ?: return
        if (state.value.subtitleSearching) return
        val token = generation
        mutable.update { it.copy(subtitleSearching = true, message = "") }
        scope.launch {
            val found = try { withContext(Dispatchers.IO) { repository.subtitles(m, v) } } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
            if (token != generation) return@launch
            val before = external.size
            // New subtitles go to the end, so the "x:<n>" ids of the listed ones stay the same.
            external = (external + found).distinctBy { it.url.ifBlank { it.id } }.take(400)
            subtitlesLoaded = true
            val added = external.size - before
            mutable.update { it.copy(subtitleSearching = false, subtitleNote = when {
                added > 1 -> "$added legendas novas encontradas."
                added == 1 -> "1 legenda nova encontrada."
                found.isEmpty() -> "Nenhum complemento de legendas respondeu para este vídeo."
                else -> "Nenhuma legenda nova."
            }) }
            publishTracks()
        }
    }

    /** The TV file picker opens over the app: pause, and do not stop the video when the app goes to the background. */
    @MainThread fun pickStarted() {
        picking = true
        resumeAfterPick = player?.playWhenReady == true || mpv?.paused == false
        player?.pause()
        mpv?.pause()
    }

    @MainThread fun pickFinished() {
        picking = false
        if (resumeAfterPick) { player?.play(); mpv?.play() }
        resumeAfterPick = false
    }

    @MainThread fun subtitleNote(text: String) { mutable.update { it.copy(subtitleNote = text) } }

    private fun keptSubtitles(key: String): List<Pair<TvSubtitle, Pair<String, SubtitleText.Kind>>> =
        memory.list(key).mapNotNull { e ->
            val text = runCatching { e.file.readText(Charsets.UTF_8) }.getOrNull() ?: return@mapNotNull null
            TvSubtitle("saved-${e.id}", "", e.lang, e.label) to (text to e.kind)
        }

    /** Keeps the subtitle picked by the user for this video, with the current sync. */
    private fun keep(sub: TvSubtitle) {
        val key = videoKey ?: return
        if (!prefs.subtitles.remember) return
        val (text, kind) = texts[sub.id] ?: return
        val delay = state.value.subtitleDelayMs
        val label = if (sub.id.startsWith("saved-")) sub.label else subtitleLabel(sub)
        scope.launch(memoryIo) { runCatching { memory.choose(key, memory.save(key, label, sub.lang, text, kind), delay) } }
    }

    private fun remember(choice: String?) {
        val key = videoKey ?: return
        if (!prefs.subtitles.remember) return
        scope.launch(memoryIo) { runCatching { memory.choose(key, choice) } }
    }

    /** Erases every subtitle kept per video. */
    fun forgetSubtitles() { scope.launch(memoryIo) { runCatching { memory.clear() } } }

    // ---- external players ----

    private suspend fun launchExternal(token: Long, player: String, uri: String, isTorrent: Boolean, position: Long) {
        val name = TvVideoPrefs.PLAYER_NAMES[player].orEmpty()
        val session = torrent
        val source = if (isTorrent && session != null) object : TvByteSource {
            override val size get() = session.size
            override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int) = session.read(position, buffer, offset, length)
        } else object : TvByteSource {
            override val size = 0L
            override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int) = -1
        }
        val mime = if (isTorrent) mimeOf(session?.fileName.orEmpty()) else "video/*"
        val local = withContext(Dispatchers.IO) { TvLocalStreamServer(source, mime).start() }
        server = local
        if (isTorrent && !TvStreamService.start(context)) throw IllegalStateException("O Android não permitiu manter a transmissão. Use o player do Comic TV.")
        // Best subtitle by language, converted to UTF-8 so external players show the accents correctly.
        var subtitleUrl: String? = null
        var subtitleName: String? = null
        if (prefs.subtitles.auto) {
            withTimeoutOrNull(10_000) { while (!subtitlesLoaded) delay(100) }
            val best = external.withIndex().filter { SubtitleLanguages.rank(it.value.lang, it.value.label, prefs.subtitles.preferBrazil) != Int.MAX_VALUE }
                .minByOrNull { SubtitleLanguages.rank(it.value.lang, it.value.label, prefs.subtitles.preferBrazil) }?.value
            if (best != null) runCatching {
                val (file, kind) = subtitleFile(best, 0)
                subtitleUrl = local.file("legenda.${kind.extension}", kind.mime, file)
                subtitleName = subtitleLabel(best)
            }
        }
        if (token != generation) return
        val video = if (isTorrent) local.videoUrl else uri
        val intent = TvExternalPlayers.intent(context, player, video, mime, state.value.title, position, subtitleUrl, subtitleName)
            ?: throw IllegalStateException("$name não está instalado na TV. Instale pela loja ou escolha outro player em Configurações.")
        mutable.update { it.copy(buffering = false, stage = "Abrindo no $name…") }
        launchRequests.emit(TvExternalLaunch(intent, name))
    }

    private fun mimeOf(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "mkv" -> "video/x-matroska"; "mp4", "m4v" -> "video/mp4"; "webm" -> "video/webm"; "avi" -> "video/x-msvideo"
        "ts" -> "video/mp2t"; "mov" -> "video/quicktime"; else -> "video/*"
    }

    /** The screen started the external app; the transfer continues until it returns. */
    @MainThread fun externalStarted() {
        val name = state.value.external
        externalBase = "Reproduzindo no $name. Ao sair do $name, a transmissão termina"
        mutable.update { it.copy(stage = externalBase) }
    }

    @MainThread fun externalFailed() {
        fail(generation, "Não foi possível abrir o ${state.value.external}. Confira se ele está instalado.")
    }

    /** The external player closed: save where it stopped and end the transfer. */
    @MainThread fun externalFinished(positionMs: Long?) {
        val m = meta; val v = video
        if (m != null && v != null && positionMs != null && positionMs > 0) progressWrites.trySend(TvProgress(m, v, positionMs, 0))
        stopExternal()
    }

    @MainThread fun stopExternal() { if (state.value.external.isNotBlank()) stop() }

    /** Leaving the app stops the Comic TV player; an external player keeps the transfer alive. */
    @MainThread fun stopIfInternal() { if (!picking && state.value.active && state.value.external.isBlank()) stop() }

    // ---- lifecycle ----

    private fun saveProgress() {
        val m = meta ?: return
        val v = video ?: return
        val exo = player
        val current = currentPosition()
        val duration = exo?.duration?.takeIf { it != C.TIME_UNSET && it > 0 } ?: mpv?.durationMs?.takeIf { it > 0 } ?: state.value.durationMs
        if (current > 0) progressWrites.trySend(TvProgress(m, v, current, duration.coerceAtLeast(0)))
    }

    private fun releaseResources() {
        // release() can synchronously wait for the loader: unblock torrent reads before invoking it.
        val oldTorrent = torrent
        oldTorrent?.cancelPendingReads()
        torrent = null
        val oldPlayer = player
        player = null
        val oldAss = assHandler
        assHandler = null
        runCatching { oldPlayer?.release() }
        runCatching { oldAss?.release() }
        releaseMpv()
        server?.let { s -> server = null; runCatching { s.close() }; TvStreamService.stop(context) }
        if (oldTorrent != null) scope.launch(Dispatchers.IO) { runCatching { oldTorrent.close() } }
    }

    private fun fail(token: Long, message: String) {
        if (token != generation) return
        saveProgress()
        generation++
        ticker?.cancel()
        ticker = null
        subtitleJob?.cancel()
        releaseResources()
        mutable.update { it.copy(buffering = false, playing = false, playerReady = false, canPlay = false, stage = "", message = message) }
    }

    @MainThread fun pause() { player?.pause(); mpv?.pause() }
    @MainThread fun resume() { player?.play(); mpv?.play() }
    @MainThread fun seekTo(ms: Long) {
        val duration = state.value.durationMs
        val target = ms.coerceIn(0, if (duration > 0) duration else Long.MAX_VALUE)
        player?.seekTo(target)
        mpv?.seek(target)
    }
    @MainThread fun stop() {
        saveProgress()
        generation++
        torrent?.cancelPendingReads()
        job?.cancel()
        job = null
        ticker?.cancel()
        ticker = null
        subtitleJob?.cancel()
        delayJob?.cancel()
        releaseResources()
        meta = null
        video = null
        stream = null
        alternatives = emptyList()
        external = emptyList()
        texts.clear()
        loaded = null
        pendingSideload = false
        savedChoice = null
        userPicked = false
        picking = false
        resumeAfterPick = false
        externalBase = ""
        mutable.value = TvPlaybackState()
    }

    companion object {
        private const val SIDELOAD_ID = "1280"
        private const val EXTERNAL_IDLE_MS = 15 * 60 * 1000L
        private const val SUGGEST_AFTER_S = 45L
    }
}

/** Torrent video bytes for the player, local files (subtitles) from disk. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class RoutingFactory(private val main: DataSource.Factory) : DataSource.Factory {
    override fun createDataSource(): DataSource = object : DataSource {
        private val video = main.createDataSource()
        private val file = FileDataSource()
        private var current: DataSource? = null
        override fun addTransferListener(listener: TransferListener) { video.addTransferListener(listener); file.addTransferListener(listener) }
        override fun open(dataSpec: DataSpec): Long = (if (dataSpec.uri.scheme == "file") file else video).also { current = it }.open(dataSpec)
        override fun read(buffer: ByteArray, offset: Int, length: Int) = current?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
        override fun getUri() = current?.uri
        override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()
        override fun close() { current?.close(); current = null }
    }
}
