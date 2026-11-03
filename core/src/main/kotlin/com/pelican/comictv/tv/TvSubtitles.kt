package com.pelican.comictv.tv

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** An external subtitle offered by an add-on, a stream or the phone. [url] stays private to the TV. */
data class TvSubtitle(val id:String,val url:String,val lang:String,val label:String="",val addonName:String="")

/**
 * Player and subtitle choices shared by the TV settings, the player menu and the phone panel. [remember] keeps the
 * subtitles chosen or imported for each video, with their sync, and reopens the last one.
 */
data class TvSubtitlePrefs(val auto:Boolean=true,val preferBrazil:Boolean=true,val originalAssStyle:Boolean=true,
    val sizePercent:Int=100,val color:Int=0,val background:Int=1,val edge:Int=1,val bottomPercent:Int=8,val remember:Boolean=true) {
    fun normalized()=copy(sizePercent=(sizePercent/10*10).coerceIn(50,200),color=color.coerceIn(0,COLORS.lastIndex),
        background=background.coerceIn(0,2),edge=edge.coerceIn(0,2),bottomPercent=bottomPercent.coerceIn(0,30))
    companion object {
        /** ARGB text colors, in the order the settings show them. */
        val COLORS=listOf(0xFFFFFFFF.toInt(),0xFFFFEB3B.toInt(),0xFF80DEEA.toInt(),0xFFA5D6A7.toInt())
        val COLOR_NAMES=listOf("Branco","Amarelo","Ciano","Verde")
        val BACKGROUND_NAMES=listOf("Sem fundo","Fundo translúcido","Fundo sólido")
        val EDGE_NAMES=listOf("Sem contorno","Contorno","Sombra")
    }
}
/**
 * [publicTrackers] adds the public tracker list to every torrent. The source filter ([sourceAddon], [sourceQuality],
 * [sourceAudio]) is remembered for every title; empty means all.
 */
data class TvVideoPrefs(val player:String="internal",val subtitles:TvSubtitlePrefs=TvSubtitlePrefs(),val publicTrackers:Boolean=true,
    val sourceAddon:String="",val sourceQuality:String="",val sourceAudio:String="",val preparationArtwork:Boolean=true,
    val decoder:String="auto") {
    fun normalized()=copy(player=player.takeIf{it in PLAYERS}?:"internal",decoder=decoder?.takeIf{it in DECODERS}?:"auto",subtitles=(subtitles?:TvSubtitlePrefs()).normalized(),
        sourceAddon=(sourceAddon?:"").take(200).filter{it.code>=32},sourceQuality=sourceQuality?.takeIf{it in SourceFilter.QUALITIES}.orEmpty(),
        sourceAudio=sourceAudio?.takeIf{it in SourceFilter.AUDIOS}.orEmpty())
    companion object {
        /** internal: Comic TV with mpv; exo: Comic TV with ExoPlayer; ask: Android chooser; the others open that app directly. */
        val PLAYERS=listOf("internal","exo","ask","vlc","mx","just","nova")
        /** Players that run inside the Comic TV app. */
        val INTERNAL=setOf("internal","exo")
        /** Video decoder of the Comic TV players: auto tries the hardware one and switches to software when no frame appears. */
        val DECODERS=listOf("auto","hardware","software")
        val DECODER_NAMES=listOf("Automático","Hardware","Software")
        val PLAYER_NAMES=mapOf("internal" to "Player do Comic TV (mpv)","exo" to "Player do Comic TV (ExoPlayer)","ask" to "Perguntar sempre","vlc" to "VLC","mx" to "MX Player","just" to "Just Player","nova" to "Nova Video Player")
    }
}

object SubtitleLanguages {
    private val brazil=setOf("pob","pb","pt-br","pt_br","ptbr","pt-brazil","bra")
    private val portuguese=setOf("por","pt","pt-pt","pt_pt","ptpt","portuguese","português","portugues")
    private val english=setOf("eng","en","en-us","en-gb","english","inglês","ingles")
    private fun key(code:String)=code.trim().lowercase()
    fun isBrazil(code:String,label:String="")=key(code) in brazil||label.contains("brasil",true)||label.contains("brazil",true)
    fun isPortuguese(code:String,label:String="")=isBrazil(code,label)||key(code) in portuguese||key(code).startsWith("pt")||label.contains("portug",true)
    fun isEnglish(code:String,label:String="")=key(code) in english||key(code).startsWith("en-")||label.contains("english",true)||label.contains("inglês",true)
    /** 0 is the best match; [Int.MAX_VALUE] means the language is not one the user asked for. */
    fun rank(code:String,label:String,preferBrazil:Boolean):Int {
        val br=isBrazil(code,label);val pt=!br&&isPortuguese(code,label)
        return when {
            br->if(preferBrazil)0 else 1
            pt->if(preferBrazil)1 else 0
            isEnglish(code,label)->2
            else->Int.MAX_VALUE
        }
    }
    fun name(code:String,label:String=""):String = when {
        isBrazil(code,label)->"Português (Brasil)"
        isPortuguese(code,label)->"Português"
        isEnglish(code,label)->"Inglês"
        code.isBlank()->label.ifBlank{"Idioma não informado"}
        else->code
    }
}

/** Text subtitle files: charset repair, format detection and timing shifts done on the TV before playback. */
object SubtitleText {
    enum class Kind(val extension:String,val mime:String) { Srt("srt","application/x-subrip"),Vtt("vtt","text/vtt"),Ass("ass","text/x-ssa") }
    const val MAX_BYTES=5*1024*1024
    private val cp1252:Charset=Charset.forName("windows-1252")

    /** UTF-8 when valid (with or without BOM), UTF-16 by BOM, otherwise Windows-1252 as most Portuguese SRT files use. */
    fun decode(bytes:ByteArray):String {
        require(bytes.size in 1..MAX_BYTES){"Arquivo de legenda vazio ou grande demais."}
        if(bytes.size>=2&&bytes[0]==0xFF.toByte()&&bytes[1]==0xFE.toByte())return String(bytes,2,bytes.size-2,Charsets.UTF_16LE)
        if(bytes.size>=2&&bytes[0]==0xFE.toByte()&&bytes[1]==0xFF.toByte())return String(bytes,2,bytes.size-2,Charsets.UTF_16BE)
        val start=if(bytes.size>=3&&bytes[0]==0xEF.toByte()&&bytes[1]==0xBB.toByte()&&bytes[2]==0xBF.toByte())3 else 0
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes,start,bytes.size-start)).toString()
        } catch(_:CharacterCodingException) { String(bytes,cp1252) }
    }

    fun kind(text:String):Kind {
        val head=text.trimStart('﻿',' ','\t','\r','\n').take(4096)
        return when {
            head.startsWith("WEBVTT")->Kind.Vtt
            head.contains("[Script Info]",true)||head.contains("[V4+ Styles]",true)||head.contains("[V4 Styles]",true)||Regex("(?m)^Dialogue:").containsMatchIn(text.take(65536))->Kind.Ass
            else->Kind.Srt
        }
    }

    /** Rejects files with no cue at all, so a web page or an archive never reaches the player as a subtitle. */
    fun validate(text:String,kind:Kind=kind(text)):Kind {
        val ok=when(kind) {
            Kind.Ass->Regex("(?m)^Dialogue:\\s*[^,]*,\\s*\\d+:\\d{2}:\\d{2}\\.\\d{2}").containsMatchIn(text)
            Kind.Vtt,Kind.Srt->Regex("\\d{2}:\\d{2}[.,]\\d{3}\\s*-->").containsMatchIn(text)
        }
        require(ok){"Arquivo de legenda inválido ou em formato não compatível."}
        return kind
    }

    private val srtTime=Regex("(\\d{1,3}):(\\d{2}):(\\d{2})[,.](\\d{3})")
    private val vttTime=Regex("(?:(\\d{1,3}):)?(\\d{2}):(\\d{2})\\.(\\d{3})")
    private val assLine=Regex("^(\\s*Dialogue:\\s*[^,]*,)\\s*(\\d+):(\\d{2}):(\\d{2})\\.(\\d{2})\\s*,\\s*(\\d+):(\\d{2}):(\\d{2})\\.(\\d{2})\\s*,")

    /** Moves every cue by [deltaMs]; positive shows the subtitles later. Times before zero become zero. */
    fun shift(text:String,kind:Kind,deltaMs:Long):String {
        if(deltaMs==0L)return text
        fun ms(h:String?,m:String,s:String,frac:String,unit:Long)=((h?.toLongOrNull()?:0)*3600+m.toLong()*60+s.toLong())*1000+frac.toLong()*unit
        fun moved(value:Long)=(value+deltaMs).coerceAtLeast(0)
        fun hms(value:Long)=Triple(value/3_600_000,value/60_000%60,value/1000%60)
        return text.lineSequence().joinToString("\n") { line ->
            when(kind) {
                Kind.Srt->if(!line.contains("-->"))line else srtTime.replace(line){m->
                    val v=moved(ms(m.groupValues[1],m.groupValues[2],m.groupValues[3],m.groupValues[4],1));val(h,mi,s)=hms(v)
                    "%02d:%02d:%02d,%03d".format(h,mi,s,v%1000)
                }
                Kind.Vtt->if(!line.contains("-->"))line else vttTime.replace(line){m->
                    val v=moved(ms(m.groups[1]?.value,m.groupValues[2],m.groupValues[3],m.groupValues[4],1));val(h,mi,s)=hms(v)
                    "%02d:%02d:%02d.%03d".format(h,mi,s,v%1000)
                }
                Kind.Ass->assLine.find(line)?.let { m ->
                    val g=m.groupValues
                    fun ass(value:Long):String{val(h,mi,s)=hms(value);return "%d:%02d:%02d.%02d".format(h,mi,s,value%1000/10)}
                    val start=moved(ms(g[2],g[3],g[4],g[5],10));val end=moved(ms(g[6],g[7],g[8],g[9],10))
                    g[1]+ass(start)+","+ass(end)+","+line.substring(m.range.last+1)
                }?:line
            }
        }
    }
}

/** Orders a source list the way Stremio users expect: direct links first, then torrents by seeders. */
object StreamRank {
    private val patterns=listOf(Regex("👤\\s*(\\d{1,7})"),Regex("(?i)\\bseed(?:er)?s?\\b\\W{0,3}(\\d{1,7})"),Regex("(?i)\\bS:\\s*(\\d{1,7})"))
    fun torrent(stream:TvStream)=stream.infoHash.isNotBlank()||stream.url.startsWith("magnet:",true)
    fun seeders(stream:TvStream):Int? {
        val text=stream.name+"\n"+stream.description
        return patterns.firstNotNullOfOrNull{it.find(text)?.groupValues?.get(1)?.toIntOrNull()}
    }
    /** Torrents with fewer than this many seeders usually take long to start. */
    const val SLOW=10
    fun slow(stream:TvStream)=torrent(stream)&&(seeders(stream)?.let{it<SLOW}?:false)
    fun sorted(streams:List<TvStream>):List<TvStream> =streams.withIndex().sortedWith(compareBy<IndexedValue<TvStream>>(
        {if(torrent(it.value))1 else 0},{-(seeders(it.value)?:-1)},{it.index})).map{it.value}
}
