package com.pelican.comictv.tv

/**
 * Filters for a title's source list. Quality and audio are read from the source's name and description,
 * so they are a best guess: add-ons write them in many ways and some leave them out.
 */
object SourceFilter {
    const val ALL=""
    val QUALITIES=listOf("4K","1080p","720p","Outras")
    val AUDIOS=listOf("Dublado","Dual áudio","Inglês")
    private val k4=Regex("(?i)(^|[^a-z0-9])(2160p|4k|uhd)([^a-z0-9]|$)")
    private val p1080=Regex("(?i)(^|[^a-z0-9])(1080p|1080i|fhd)([^a-z0-9]|$)")
    private val p720=Regex("(?i)(^|[^a-z0-9])720p([^a-z0-9]|$)")
    private val dual=Regex("(?iu)(^|[^\\p{L}0-9])(dual([ ._-]?(audio|áudio))?|multi[ ._-]?(audio|áudio))([^\\p{L}0-9]|$)")
    private val dubbed=Regex("(?iu)(^|[^\\p{L}0-9])(dublado|dub|dubbed|pt[-_ ]?br|português|portugues|portuguese|nacional)([^\\p{L}0-9]|$)")
    // Upper-case BR alone (as in "1080p BR"), never the start of BRRip or BluRay.
    private val br=Regex("(^|[^\\p{L}0-9])BR([^\\p{L}0-9]|$)")
    private val flags=Regex("🇧🇷|🇵🇹")

    private fun text(s:TvStream)=s.name+"\n"+s.description
    fun quality(s:TvStream):String { val t=text(s);return when { k4.containsMatchIn(t)->"4K";p1080.containsMatchIn(t)->"1080p";p720.containsMatchIn(t)->"720p";else->"Outras" } }
    /** A source with no language in its name counts as English, the usual original audio. */
    fun audio(s:TvStream):String { val t=text(s);return when { dual.containsMatchIn(t)->"Dual áudio";dubbed.containsMatchIn(t)||br.containsMatchIn(t)||flags.containsMatchIn(t)->"Dublado";else->"Inglês" } }
    fun addon(s:TvStream)=s.addonName.ifBlank{"Fontes"}

    data class Choice(val addon:String=ALL,val quality:String=ALL,val audio:String=ALL)
    data class Option(val value:String,val label:String,val count:Int)
    data class Result(val shown:List<IndexedValue<TvStream>>,val addons:List<Option>,val qualities:List<Option>,val audios:List<Option>,
        /** What actually applies: a remembered add-on missing from this title counts as all. */
        val applied:Choice)

    /** Each row of buttons counts the sources that the other two filters leave, so a number never promises an empty list. */
    fun apply(streams:List<TvStream>,choice:Choice):Result {
        val all=streams.withIndex().toList()
        val addonNames=all.map{addon(it.value)}.distinct()
        val c=choice.copy(addon=choice.addon.takeIf{it in addonNames}?:ALL,quality=choice.quality.takeIf{it in QUALITIES}?:ALL,audio=choice.audio.takeIf{it in AUDIOS}?:ALL)
        fun keep(s:TvStream,skip:String)=(skip=="addon"||c.addon==ALL||addon(s)==c.addon)&&(skip=="quality"||c.quality==ALL||quality(s)==c.quality)&&(skip=="audio"||c.audio==ALL||audio(s)==c.audio)
        fun row(skip:String,values:List<String>,of:(TvStream)->String):List<Option> {
            val pool=all.filter{keep(it.value,skip)}
            return listOf(Option(ALL,"Todas",pool.size))+values.map{v->Option(v,v,pool.count{of(it.value)==v})}
        }
        return Result(all.filter{keep(it.value,"")},row("addon",addonNames,::addon),row("quality",QUALITIES,::quality),row("audio",AUDIOS,::audio),c)
    }
}
