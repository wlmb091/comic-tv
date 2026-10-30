package com.pelican.comictv.explorer

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.pelican.comictv.archive.ApkManifestSplit
import com.pelican.comictv.archive.ApkSetEntry
import com.pelican.comictv.archive.ApkSetPolicy
import com.pelican.comictv.archive.openComicZip
import com.pelican.comictv.archive.guardZipMetadata
import java.io.File
import java.security.MessageDigest

internal object ExplorerSplitApk {
    /** Container is staged privately by ExplorerApk; never extract paths from an untrusted ZIP. */
    fun prepare(context: Context, container: File, directory: File): List<File> {
        val files = ArrayList<File>(); val metadata = ArrayList<ApkSetEntry>()
        var total = 0L
        guardZipMetadata(container, 500)
        openComicZip(container, 500).use { zip ->
            val entries = zip.entries().toList()
            require(entries.none { it.name.endsWith(".obb",true) || it.name.startsWith("Android/obb/",true) }) {
                "Este XAPK inclui OBB. A instalação de dados OBB não é suportada; escolha uma distribuição sem OBB."
            }
            val apks = entries.filter { !it.isDirectory && it.name.endsWith(".apk",true) }
            require(apks.size in 1..100) { "O pacote não contém entre 1 e 100 APKs." }
            apks.forEachIndexed { index, entry ->
                require(entry.size in 1..1024L*1024*1024 && entry.size<=1024L*1024*1024-total) { "APKs excedem 1 GiB." }
                total+=entry.size
                val file=File(directory,"split-$index.apk")
                val crc = java.util.zip.CRC32()
                java.util.zip.CheckedInputStream(zip.getInputStream(entry),crc).use { input -> file.outputStream().use { output ->
                    val buffer=ByteArray(64*1024);var count=0L
                    while(true) {
                        if(Thread.currentThread().isInterrupted)throw java.io.InterruptedIOException()
                        val n=input.read(buffer);if(n<0)break
                        count+=n;require(count<=entry.size) { "APK excede tamanho declarado." }
                        check(directory.usableSpace>=64L*1024*1024+n) { "Espaço insuficiente." }
                        output.write(buffer,0,n)
                    }
                    require(count==entry.size) { "APK incompleto." }
                } }
                require(entry.crc < 0 || crc.value == entry.crc) { "APK corrompido no pacote." }
                files+=file
                @Suppress("DEPRECATION")
                val flags=if(Build.VERSION.SDK_INT>=28)PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
                @Suppress("DEPRECATION")
                val info=context.packageManager.getPackageArchiveInfo(file.path,flags) ?: error("APK inválido no pacote.")
                @Suppress("DEPRECATION")
                val signatures=if(Build.VERSION.SDK_INT>=28)info.signingInfo?.apkContentsSigners else info.signatures
                val signers=signatures.orEmpty().map { signature -> MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
                var split:String?=null;val abis=HashSet<String>()
                guardZipMetadata(file, 5_000)
                openComicZip(file).use { apk ->
                    val manifest=apk.getEntry("AndroidManifest.xml") ?: error("APK sem manifesto.")
                    require(manifest.size in 8..2L*1024*1024)
                    apk.getInputStream(manifest).use { source ->
                        val bytes=ByteArray(manifest.size.toInt())
                        java.io.DataInputStream(source).readFully(bytes)
                        require(source.read()==-1);split=ApkManifestSplit.read(bytes)
                    }
                    apk.entries().toList().forEach { resource ->
                        val path=resource.name.split('/')
                        if(path.size>=3 && path[0]=="lib" && path.last().endsWith(".so"))abis+=path[1]
                    }
                }
                val abiNames = mapOf("arm64_v8a" to "arm64-v8a", "armeabi_v7a" to "armeabi-v7a",
                    "armeabi" to "armeabi", "x86" to "x86", "x86_64" to "x86_64", "mips" to "mips", "mips64" to "mips64", "riscv64" to "riscv64")
                abiNames[split?.substringAfterLast('.') ?: ""]?.let { abis += it }
                @Suppress("DEPRECATION")
                val version=if(Build.VERSION.SDK_INT>=28)info.longVersionCode else info.versionCode.toLong()
                metadata+=ApkSetEntry(file.name,info.packageName,version,signers,split,abis)
            }
        }
        val selected=ApkSetPolicy.select(metadata,Build.SUPPORTED_ABIS.toList()).map { it.key }.toSet()
        files.filter { it.name !in selected }.forEach { it.delete() }
        return files.filter { it.name in selected }
    }
}
