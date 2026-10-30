package com.pelican.comictv.archive

/** Pure policy: the platform installer remains responsible for Android compatibility and confirmation. */
data class ApkSetEntry(val key: String, val packageName: String, val version: Long, val signers: Set<String>,
    val split: String?, val nativeAbis: Set<String>)
object ApkSetPolicy {
    fun select(entries: List<ApkSetEntry>, supportedAbis: List<String>): List<ApkSetEntry> {
        require(entries.isNotEmpty() && entries.size <= 100) { "Pacote vazio ou com APKs demais." }
        val base = entries.singleOrNull { it.split.isNullOrEmpty() } ?: error("O pacote precisa conter exatamente um APK base.")
        require(base.packageName.isNotEmpty() && base.signers.isNotEmpty()) { "Identidade ou assinatura ausente." }
        require(entries.all { it.packageName == base.packageName && it.version == base.version && it.signers == base.signers }) { "Os APKs têm pacote, versão ou assinatura diferentes." }
        require(entries.map { it.split.orEmpty() }.distinct().size == entries.size) { "Há splits duplicados." }
        val available = entries.flatMap { it.nativeAbis }.toSet()
        val abi = supportedAbis.firstOrNull { it in available }
        require(available.isEmpty() || abi != null) { "Nenhuma ABI deste pacote é compatível com a TV." }
        require(base.nativeAbis.isEmpty() || abi in base.nativeAbis) { "O APK base não é compatível com a ABI desta TV." }
        return entries.filter { it.nativeAbis.isEmpty() || abi in it.nativeAbis }
    }
}

/** Reads only the root split attribute from bounded Android binary XML. No resource resolution or code loading. */
object ApkManifestSplit {
    fun read(bytes: ByteArray): String? {
        require(bytes.size in 8..2*1024*1024) { "Manifesto APK inválido." }
        fun u16(at: Int): Int { require(at >= 0 && at + 2 <= bytes.size); return (bytes[at].toInt() and 255) or ((bytes[at+1].toInt() and 255) shl 8) }
        fun u32(at: Int): Int { require(at >= 0 && at + 4 <= bytes.size); return (0..3).fold(0) { n,i -> n or ((bytes[at+i].toInt() and 255) shl (8*i)) } }
        require(u16(0)==3 && u32(4)==bytes.size)
        var strings = emptyList<String>(); var offset = u16(2)
        while (offset < bytes.size) {
            val type=u16(offset); val header=u16(offset+2); val size=u32(offset+4)
            require(header>=8 && size>=header && size<=bytes.size-offset)
            if (type==1) {
                require(header>=28); val count=u32(offset+8); val flags=u32(offset+16); val start=u32(offset+20)
                require(count in 0..50_000 && 4L*count<=size-header && start in header..size)
                fun length(at: Int, utf8: Boolean): Pair<Int,Int> {
                    require(at in offset until offset+size)
                    if (utf8) { val a=bytes[at].toInt() and 255; return if(a and 128==0) a to (at+1) else { require(at+1<offset+size); (((a and 127) shl 8) or (bytes[at+1].toInt() and 255)) to (at+2) } }
                    val a=u16(at); return if(a and 32768==0) a to (at+2) else (((a and 32767) shl 16) or u16(at+2)) to (at+4)
                }
                var decodedBytes = 0L
                val decodedOffsets = HashMap<Int, String>()
                strings=(0 until count).map { i ->
                    val relative=u32(offset+header+4*i); require(relative>=0 && relative<size-start)
                    decodedOffsets[relative]?.let { return@map it }
                    var at=offset+start+relative; val utf8=flags and 256!=0
                    if(utf8) at=length(at,true).second
                    val (n,body)=length(at,utf8); val nBytes=n.toLong()*(if(utf8)1 else 2)
                    require(nBytes<=offset+size-body && nBytes<=1_048_576)
                    decodedBytes += nBytes
                    require(decodedBytes <= 4L*1024*1024) { "String pool do manifesto excede o limite." }
                    String(bytes,body,nBytes.toInt(),if(utf8)Charsets.UTF_8 else Charsets.UTF_16LE).also { decodedOffsets[relative] = it }
                }
            } else if(type==0x102) {
                require(header>=16 && size>=36)
                fun text(index: Int): String { require(index in strings.indices); return strings[index] }
                require(text(u32(offset+20))=="manifest") { "Racine de manifeste invalide." }
                val attributeStart=u16(offset+24); val attributeSize=u16(offset+26); val count=u16(offset+28)
                require(attributeStart>=20 && attributeSize>=20 && 16L+attributeStart+count.toLong()*attributeSize<=size)
                for(i in 0 until count) {
                    val at=offset+16+attributeStart+i*attributeSize
                    if(text(u32(at+4))=="split") {
                        val raw=u32(at+8)
                        return if(raw!=-1) text(raw).takeIf { it.isNotEmpty() } else {
                            require(bytes[at+15].toInt() and 255==3); text(u32(at+16)).takeIf { it.isNotEmpty() }
                        }
                    }
                }
                return null
            }
            offset+=size
        }
        error("Manifesto Android ausente.")
    }
}
