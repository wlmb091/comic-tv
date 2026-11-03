package com.pelican.comictv.tv

import com.google.gson.*
import java.net.URI
import java.net.URLEncoder

data class AddonResource(val name:String,val types:List<String>?=null,val idPrefixes:List<String>?=null)
data class CatalogExtra(val name:String,val required:Boolean=false,val options:List<String> = emptyList())
data class TvCatalog(val id:String,val type:String,val name:String,val extra:List<CatalogExtra> = emptyList()) {
    fun canRequest(values:Map<String,String>)=extra.none { e ->
        val value=values[e.name]
        (e.required&&value.isNullOrBlank())||(!value.isNullOrBlank()&&e.options.isNotEmpty()&&value !in e.options)
    }
}
data class AddonManifest(val id:String,val name:String,val version:String,val description:String="",val logo:String="",
    val types:List<String> = emptyList(),val idPrefixes:List<String>?=null,val resources:List<AddonResource> = emptyList(),
    val catalogs:List<TvCatalog> = emptyList(),val addonCatalogs:List<TvCatalog> = emptyList(),val configurable:Boolean=false,val configurationRequired:Boolean=false) {
    fun supports(resource:String,type:String,id:String)=resources.any{it.name==resource&&type in (it.types?:types)&&
        (it.idPrefixes?:if(it.types==null)idPrefixes else null).let{prefixes->prefixes==null||prefixes.any(id::startsWith)}}
}
data class TvAddon(val url:String,val manifest:AddonManifest,val enabled:Boolean=true,val animeCatalogs:Set<String> = emptySet())
data class TvVideo(val id:String,val title:String,val season:Int=0,val episode:Int=0,val thumbnail:String="")
data class TvMeta(val id:String,val type:String,val name:String,val poster:String="",val background:String="",val description:String="",val genres:List<String> = emptyList(),val videos:List<TvVideo> = emptyList())
data class TvStream(val name:String="",val description:String="",val url:String="",val infoHash:String="",val fileIdx:Int?=null,
    val sources:List<String> = emptyList(),val externalUrl:String="",val headers:Map<String,String> = emptyMap(),val addonName:String="")

object StremioLinks {
    private val schemes=setOf("http","https")
    private fun uri(text:String):URI=try { URI(text) }catch(_:Exception){throw IllegalArgumentException("Endereço de complemento inválido.")}
    fun manifest(input:String):String {
        val text=input.trim().let{if(it.startsWith("stremio://",true))"https://"+it.substringAfter("://") else it}
        require(text.length in 1..8192&&text.none{it.code<32||it.code==127}){"Endereço de complemento inválido."}
        val uri=uri(text)
        require(uri.scheme?.lowercase() in schemes&&!uri.host.isNullOrBlank()&&uri.port in -1..65535&&uri.rawUserInfo==null&&uri.rawFragment==null){"Use um link HTTP(S) de complemento."}
        val path=uri.rawPath.orEmpty().ifEmpty{"/"}
        val suffix=if(path.substringAfterLast('/')=="manifest.json")path else path.trimEnd('/')+"/manifest.json"
        return uri.scheme.lowercase()+"://"+uri.rawAuthority+suffix+(uri.rawQuery?.let{"?$it"}?:"")
    }
    fun resource(manifest:String,resource:String,type:String,id:String,extra:Map<String,String> = emptyMap()):String {
        require(resource in setOf("catalog","meta","stream","subtitles","addon_catalog"))
        require(listOf(type,id).all{it.isNotBlank()&&it !in setOf(".","..")&&it.length<=4096&&it.none{c->c.code<32||c.code==127}}){"Identificador de conteúdo inválido."}
        require(extra.size<=30&&extra.all{it.key.isNotBlank()&&it.key.length<=100&&it.value.length<=4096}){"Filtros de catálogo inválidos."}
        val uri=URI(manifest(manifest));val path=uri.rawPath.removeSuffix("manifest.json")
        val params=if(extra.isEmpty())"" else "/"+extra.entries.sortedBy{it.key}.joinToString("&"){encode(it.key)+"="+encode(it.value)}
        return "${uri.scheme}://${uri.rawAuthority}$path$resource/${encode(type)}/${encode(id)}$params.json"+(uri.rawQuery?.let{"?$it"}?:"")
    }
    fun encode(value:String):String=URLEncoder.encode(value,"UTF-8").replace("+","%20")
    fun http(url:String):Boolean=runCatching{val u=URI(url);url.length<=32768&&url.none{it.code<32||it.code==127}&&u.scheme?.lowercase() in schemes&&!u.host.isNullOrBlank()&&u.port in -1..65535&&u.rawUserInfo==null}.getOrDefault(false)
    fun configure(url:String):String {val u=URI(manifest(url));return "${u.scheme}://${u.rawAuthority}${u.rawPath.removeSuffix("manifest.json")}configure"+(u.rawQuery?.let{"?$it"}?:"")}
}

object StremioJson {
    private fun JsonObject.s(key:String)=get(key)?.takeIf{it.isJsonPrimitive&&it.asJsonPrimitive.isString}?.asString.orEmpty()
    private fun JsonObject.integer(key:String):Int?=runCatching{get(key)?.takeIf{it.isJsonPrimitive}?.asString?.takeIf{it.matches(Regex("-?[0-9]{1,10}"))}?.toInt()}.getOrNull()
    private fun JsonObject.i(key:String)=integer(key)?:0
    private fun JsonObject.b(key:String)=runCatching{get(key)?.asBoolean}.getOrDefault(null)?:false
    private fun JsonObject.a(key:String)=get(key)?.takeIf{it.isJsonArray}?.asJsonArray?:JsonArray()
    private fun JsonObject.o(key:String)=get(key)?.takeIf{it.isJsonObject}?.asJsonObject?:JsonObject()
    private fun JsonArray.strings()=filter{it.isJsonPrimitive&&it.asJsonPrimitive.isString}.map{it.asString}.take(500)
    private fun root(text:String):JsonObject {
        require(text.length<=8*1024*1024){"Resposta grande demais."}
        return try{JsonParser.parseString(text).asJsonObject}catch(_:Exception){throw IllegalArgumentException("Resposta de complemento inválida.")}
    }
    private fun catalog(o:JsonObject):TvCatalog {
        val required=o.a("extraRequired").strings()
        val declared=o.a("extra").filter{it.isJsonObject}.map{val e=it.asJsonObject;CatalogExtra(e.s("name"),e.b("isRequired"),e.a("options").strings())}.filter{it.name.isNotBlank()}
        val legacy=(o.a("extraSupported").strings()+required).distinct().filter{n->declared.none{it.name==n}}.map{CatalogExtra(it,it in required)}
        return TvCatalog(o.s("id"),o.s("type"),o.s("name").ifBlank{o.s("id")},(declared+legacy).distinctBy{it.name}.take(30))
    }
    fun manifest(text:String):AddonManifest {
        val o=root(text);require(listOf("id","name","version").all{o.s(it).isNotBlank()}){"Manifesto incompleto."}
        require(listOf("resources","types","catalogs").all{o.get(it)?.isJsonArray==true}){"Manifesto incompleto."}
        val resources=o.a("resources").mapNotNull{when{it.isJsonPrimitive&&it.asJsonPrimitive.isString->AddonResource(it.asString);it.isJsonObject->{val r=it.asJsonObject;if(r.get("types")?.isJsonArray!=true||r.s("name").isBlank())null else AddonResource(r.s("name"),r.a("types").strings(),if(r.has("idPrefixes"))r.a("idPrefixes").strings()else null)};else->null}}.filter{it.name.isNotBlank()}.take(100)
        val hints=o.o("behaviorHints")
        return AddonManifest(o.s("id"),o.s("name"),o.s("version"),o.s("description"),o.s("logo"),o.a("types").strings(),if(o.has("idPrefixes"))o.a("idPrefixes").strings()else null,resources,
            o.a("catalogs").filter{it.isJsonObject}.take(100).map{catalog(it.asJsonObject)}.filter{it.id.isNotBlank()&&it.type.isNotBlank()}.distinctBy{it.type to it.id},o.a("addonCatalogs").filter{it.isJsonObject}.take(100).map{catalog(it.asJsonObject)}.filter{it.id.isNotBlank()&&it.type.isNotBlank()}.distinctBy{it.type to it.id},hints.b("configurable"),hints.b("configurationRequired"))
    }
    private fun item(o:JsonObject)=TvMeta(o.s("id"),o.s("type"),o.s("name"),o.s("poster"),o.s("background"),o.s("description"),o.a("genres").strings(),o.a("videos").filter{it.isJsonObject}.take(5000).map{val v=it.asJsonObject;val episode=v.i("episode");TvVideo(v.s("id"),v.s("title").ifBlank{v.s("name")}.ifBlank{if(episode>0)"Episódio $episode" else v.s("id")},v.i("season"),episode,v.s("thumbnail"))}.filter{it.id.isNotBlank()}.distinctBy{it.id})
    fun metas(text:String)=root(text).a("metas").filter{it.isJsonObject}.take(500).map{item(it.asJsonObject)}.filter{it.id.isNotBlank()&&it.name.isNotBlank()}.distinctBy{it.type to it.id}
    fun meta(text:String)=item(root(text).o("meta")).also{require(it.id.isNotBlank()&&it.name.isNotBlank()){"Detalhes de conteúdo inválidos."}}
    fun addonUrls(text:String)=root(text).a("addons").filter{it.isJsonObject}.take(100).mapNotNull{runCatching{StremioLinks.manifest(it.asJsonObject.s("transportUrl"))}.getOrNull()}.distinct()
    fun streams(text:String):List<TvStream> =root(text).a("streams").filter{it.isJsonObject}.take(500).mapNotNull{e->
        val o=e.asJsonObject
        val index=if(o.has("fileIdx"))o.integer("fileIdx")?.takeIf{it>=0}?:return@mapNotNull null else null
        val headers=o.o("behaviorHints").o("proxyHeaders").o("request").entrySet().filter{it.value.isJsonPrimitive&&it.value.asJsonPrimitive.isString&&it.key.length<=100&&it.value.asString.length<=8192&&it.key.none{c->c.code<32||c.code==127}&&it.value.asString.none{c->c=='\r'||c=='\n'}}.take(40).associate{it.key to it.value.asString}
        TvStream(o.s("name"),o.s("description").ifBlank{o.s("title")},o.s("url"),o.s("infoHash"),index,o.a("sources").strings(),o.s("externalUrl"),headers)
    }.distinct()
}

/** Byte windows are absolute offsets in torrent storage, never offsets in sparse on-disk files. */
object TorrentReadPlanner {
    fun pieces(position:Long,length:Int,pieceSize:Int):List<Int>{require(position>=0&&length>=0&&pieceSize>0);if(length==0)return emptyList();require(position<=Long.MAX_VALUE-length);val first=position/pieceSize;val last=(position+length-1)/pieceSize;require(last<=Int.MAX_VALUE&&last-first<65536);return (first.toInt()..last.toInt()).toList()}
    fun readable(position:Long,length:Int,pieceSize:Int,have:(Int)->Boolean):Int {
        var count=0
        for(piece in pieces(position,length,pieceSize)){if(!have(piece))break;count+=minOf(length-count,(pieceSize-(position+count)%pieceSize).toInt())}
        return count
    }
}
