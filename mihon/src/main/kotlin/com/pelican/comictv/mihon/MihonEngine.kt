package com.pelican.comictv.mihon

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.system.Os
import com.pelican.comictv.sources.*
import dalvik.system.DelegateLastClassLoader
import dalvik.system.DexClassLoader
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Executes explicitly trusted, Android-installed extension code in the host process. */
class MihonEngine(context: Context) {
    private val context = context.applicationContext
    private data class Loaded(val source: CatalogueSource, val version: Long, val pins: Set<String>)
    private val loaded = ConcurrentHashMap<SourceHandle, Loaded>()
    private val originalPages = object : LinkedHashMap<Pair<SourceHandle, OnlinePage>, Page>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<SourceHandle, OnlinePage>, Page>?) = size > 5000
    }
    init { MihonRuntime.init(this.context as Application) }

    @Suppress("DEPRECATION", "QueryPermissionsNeeded")
    fun installed(): List<InstalledExtension> = context.packageManager.getInstalledPackages(flags()).mapNotNull { info ->
        if (!isExtension(info)) return@mapNotNull null
        runCatching {
            InstalledExtension(info.packageName, info.applicationInfo?.let { context.packageManager.getApplicationLabel(it).toString() }
                ?: info.packageName, version(info), info.versionName.orEmpty(), signatures(info), ExtensionPolicy.supported(lib(info)))
        }.getOrNull()
    }.sortedBy { it.name }

    @Suppress("DEPRECATION")
    @Synchronized fun load(packageName: String, expectedFingerprint: String): List<SourceHandle> {
        // Do not retain an older trusted instance if replacement/update fails verification.
        loaded.keys.filter { it.packageName == packageName }.forEach(loaded::remove)
        synchronized(originalPages) { originalPages.keys.removeAll { it.first.packageName == packageName } }
        val info = context.packageManager.getPackageInfo(packageName, flags())
        require(isExtension(info)) { "Package is not a Mihon extension" }
        val pins = signatures(info)
        ExtensionPolicy.requireTrusted(pins, expectedFingerprint)
        require(ExtensionPolicy.supported(lib(info))) { "Unsupported extension API: ${lib(info)}" }
        val app = requireNotNull(info.applicationInfo)
        val classes = ExtensionPolicy.classNames(packageName, app.metaData?.getString("tachiyomi.extension.class"))
        require(classes.isNotEmpty()) { "Missing extension source classes" }
        val path = (listOf(app.sourceDir) + app.splitSourceDirs.orEmpty()).joinToString(File.pathSeparator)
        val loader = if (Build.VERSION.SDK_INT >= 27) DelegateLastClassLoader(path, context.classLoader)
            else DexClassLoader(path, context.codeCacheDir.absolutePath, null, context.classLoader)
        val sources = classes.flatMap { name ->
            when (val instance = Class.forName(name, true, loader).getDeclaredConstructor().newInstance()) {
                is SourceFactory -> instance.createSources()
                is CatalogueSource -> listOf(instance)
                else -> error("Unsupported extension source class: $name")
            }
        }.filterIsInstance<CatalogueSource>()
        val entries = sources.map { source ->
            val handle = SourceHandle(packageName, source.id, source.name, source.lang, (source as? HttpSource)?.baseUrl.orEmpty())
            handle to Loaded(source, version(info), pins)
        }
        require(entries.isNotEmpty()) { "Extension has no catalogue sources" }
        entries.forEach { (handle, value) -> loaded[handle] = value }
        return entries.map { it.first }
    }

    @Suppress("DEPRECATION")
    private fun source(handle: SourceHandle): CatalogueSource {
        val item = loaded[handle] ?: error("Source must be loaded with a trusted certificate first")
        val current = context.packageManager.getPackageInfo(handle.packageName, flags())
        check(version(current) == item.version && signatures(current) == item.pins) { "Extension changed; reload and verify its certificate" }
        return item.source
    }

    suspend fun catalog(source: SourceHandle, query: String = "", latest: Boolean = false, page: Int = 1): CatalogResult = withContext(Dispatchers.IO) {
        require(page > 0)
        val api = source(source)
        val result = when {
            query.isNotBlank() -> api.getSearchManga(page, query, api.getFilterList())
            latest -> { check(api.supportsLatest) { "Source does not support latest updates" }; api.getLatestUpdates(page) }
            else -> api.getPopularManga(page)
        }
        CatalogResult(result.mangas.map(::mapTitle), result.hasNextPage)
    }

    suspend fun titlePreview(handle: SourceHandle, title: OnlineTitle): OnlineTitle = withContext(Dispatchers.IO) {
        val manga = SManga.create().apply { url = title.key; this.title = title.title; thumbnail_url = title.coverUrl; description = title.description }
        val result = source(handle).getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
        OnlineTitle(title.key, runCatching { result.title }.getOrDefault(title.title), result.thumbnail_url, result.description, result.author ?: title.author, result.artist ?: title.artist)
    }
    suspend fun details(source: SourceHandle, title: OnlineTitle): OnlineDetails = withContext(Dispatchers.IO) {
        val manga = SManga.create().apply { url = title.key; this.title = title.title; thumbnail_url = title.coverUrl; description = title.description }
        // 1.6 sources override getMangaUpdate; older sources use the vendored Rx bridge.
        val update = source(source).getMangaUpdate(manga, emptyList(), true, true)
        OnlineDetails(OnlineTitle(title.key, runCatching { update.manga.title }.getOrDefault(title.title), update.manga.thumbnail_url ?: title.coverUrl, update.manga.description ?: title.description, update.manga.author ?: title.author, update.manga.artist ?: title.artist), update.chapters.map { OnlineChapter(it.url, it.name, it.chapter_number) })
    }

    suspend fun pages(source: SourceHandle, chapter: OnlineChapter): List<OnlinePage> = withContext(Dispatchers.IO) {
        val original = source(source).getPageList(SChapter.create().apply { url = chapter.key; name = chapter.title; chapter_number = chapter.number })
        require(original.isNotEmpty() && original.size <= 5000) { "Chapter must contain between 1 and 5000 pages" }
        original.mapIndexed { index, page ->
            OnlinePage(index, page.url, page.imageUrl).also { mapped -> synchronized(originalPages) { originalPages[source to mapped] = page } }
        }
    }

    suspend fun download(source: SourceHandle, page: OnlinePage, target: File, maxBytes: Long = 32L * 1024 * 1024): File = withContext(Dispatchers.IO) {
        require(maxBytes in 1..(32L * 1024 * 1024)) { "Invalid page size limit" }
        val api = source(source) as? HttpSource ?: error("Image download requires an HTTP source")
        val original = synchronized(originalPages) { originalPages[source to page] } ?: Page(page.index, page.pageUrl, page.imageUrl)
        if (original.imageUrl.isNullOrBlank()) original.imageUrl = api.getImageUrl(original)
        target.absoluteFile.parentFile?.mkdirs()
        val temporary = File.createTempFile("mihon-page-", ".part", target.absoluteFile.parentFile)
        try {
            api.getImage(original).use { response ->
                val closeOnCancel = launch(start = CoroutineStart.UNDISPATCHED) { try { awaitCancellation() } finally { response.close() } }
                try {
                check(response.isSuccessful) { "HTTP ${response.code}" }
                val body = requireNotNull(response.body) { "Empty image response" }
                require(body.contentLength() <= maxBytes) { "Image exceeds page size limit" }
                body.byteStream().use { input -> temporary.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= maxBytes) { "Image exceeds page size limit" }
                        output.write(buffer, 0, read)
                    }
                    require(total > 0) { "Empty image response" }
                    output.fd.sync()
                } }
                } finally { closeOnCancel.cancel() }
            }
            currentCoroutineContext().ensureActive()
            Os.rename(temporary.absolutePath, target.absolutePath)
            target
        } finally { temporary.delete() }
    }

    private fun mapTitle(manga: SManga) = OnlineTitle(manga.url, manga.title, manga.thumbnail_url, manga.description, manga.author, manga.artist)
    private fun isExtension(info: PackageInfo) = info.reqFeatures.orEmpty().any { it.name == "tachiyomi.extension" }
    private fun lib(info: PackageInfo): String? = info.applicationInfo?.metaData?.get("tachiyomix.extensionLib")?.toString()
        ?: info.versionName?.split('.')?.take(2)?.joinToString(".")
    @Suppress("DEPRECATION") private fun flags() = PackageManager.GET_CONFIGURATIONS or PackageManager.GET_META_DATA or
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    @Suppress("DEPRECATION") private fun version(info: PackageInfo) = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    @Suppress("DEPRECATION") private fun signatures(info: PackageInfo): Set<String> =
        (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures).orEmpty()
            .map { ExtensionPolicy.fingerprint(it.toByteArray()) }.toSet()
}
