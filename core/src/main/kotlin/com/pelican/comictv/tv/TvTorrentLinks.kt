package com.pelican.comictv.tv

/** Magnet links for the torrent engine: the add-on's trackers first, then a fixed list of public trackers. */
object TorrentLinks {
    /** Well-known public trackers. Without them a torrent depends only on a cold DHT and often stays at zero connections. */
    val PUBLIC_TRACKERS=listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://explodie.org:6969/announce",
        "udp://tracker.moeking.me:6969/announce",
        "udp://tracker.tiny-vps.com:6969/announce",
        "udp://tracker.theoks.net:6969/announce",
        "udp://open.tracker.cl:1337/announce",
        "udp://tracker.dler.org:6969/announce",
        "udp://opentracker.io:6969/announce",
        "udp://tracker.qu.ax:6969/announce",
        "udp://tracker1.bt.moack.co.kr:80/announce",
        "udp://tracker-udp.gbitt.info:80/announce",
        "udp://p4p.arenabg.com:1337/announce",
        "udp://isk.richardsw.club:6969/announce",
        "udp://tracker.internetwarriors.net:1337/announce",
        "udp://9.rarbg.com:2810/announce",
        "http://tracker.openbittorrent.com:80/announce",
    )
    private val tracker=Regex("(?i)(udp|https?|wss?)://[^\\s&#]{1,500}")

    /** [url] is used when it is already a magnet; otherwise the link is built from [infoHash]. Returns null without a usable identifier. */
    fun magnet(url:String,infoHash:String,sources:List<String>,publicTrackers:Boolean):String? {
        val fromAddon=sources.filter{it.startsWith("tracker:")}.map{it.removePrefix("tracker:")}.filter{tracker.matches(it)}.take(30)
        val extra=(fromAddon+if(publicTrackers)PUBLIC_TRACKERS else emptyList()).distinct()
        if(url.startsWith("magnet:?",true)) {
            if(url.length>16384||url.any{it.code<32||it.code==127})return null
            val present=Regex("(?i)[?&]tr=([^&]*)").findAll(url).map{java.net.URLDecoder.decode(it.groupValues[1],"UTF-8")}.toSet()
            return url+extra.filter{it !in present}.take(50).joinToString(""){"&tr="+StremioLinks.encode(it)}
        }
        if(!infoHash.matches(Regex("[0-9a-fA-F]{40}")))return null
        return "magnet:?xt=urn:btih:"+infoHash.lowercase()+extra.take(50).joinToString(""){"&tr="+StremioLinks.encode(it)}
    }
}
