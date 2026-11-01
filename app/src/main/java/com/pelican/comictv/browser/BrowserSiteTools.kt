package com.pelican.comictv.browser

/**
 * A short list of our own with well-known ad and pop-under networks. Link shorteners stay out on purpose:
 * many comic sites only reach their downloads through them.
 */
internal object BrowserAdblock {
    private val domains = hashSetOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com", "googletagservices.com",
        "amazon-adsystem.com", "adnxs.com", "rubiconproject.com", "pubmatic.com", "openx.net", "casalemedia.com",
        "criteo.com", "criteo.net", "smartadserver.com", "adsrvr.org", "advertising.com", "adform.net", "bidswitch.net",
        "yieldmo.com", "sharethrough.com", "teads.tv", "33across.com", "media.net", "zedo.com", "moatads.com",
        "taboola.com", "outbrain.com", "revcontent.com", "mgid.com", "ads-twitter.com", "ads.yahoo.com",
        "popads.net", "popcash.net", "propellerads.com", "propellerclick.com", "onclickads.net", "onclkds.com",
        "exoclick.com", "exosrv.com", "juicyads.com", "trafficjunky.net", "trafficstars.com", "adsterra.com",
        "hilltopads.net", "clickadu.com", "adcash.com", "admaven.com", "ad-maven.com", "popmyads.com", "a-ads.com",
        "coinzilla.io", "bitmedia.io", "adskeeper.com", "adskeeper.co.uk", "richads.com", "pushground.com",
    )
    /** Walks the host's parent domains, so a request costs a few set lookups and no regex. */
    fun blocks(host: String?): Boolean {
        var current = host?.lowercase()?.trim('.') ?: return false
        while (current.contains('.')) {
            if (current in domains) return true
            current = current.substringAfter('.')
        }
        return false
    }
}

/**
 * Inverts the page, then inverts images and video back, so a comic keeps its exact colors and only the white background goes dark.
 * Plain invert on purpose: it undoes itself exactly, while hue-rotate clips saturated colors and washes the comic out. The site's own colors change hue.
 */
internal object BrowserDarkMode {
    private const val ID = "comic-tv-dark-mode"
    private const val UNDO = "filter:invert(1)!important"
    const val CSS = "html{$UNDO;background:#fff!important}" +
        "img,video,picture,canvas,iframe,embed,object,[style*=\\\"background-image\\\"]{$UNDO}" +
        "picture img,[style*=\\\"background-image\\\"] img,[style*=\\\"background-image\\\"] video{filter:none!important}"
    fun script(enabled: Boolean): String = "(function(){var e=document.getElementById('$ID');" +
        if (enabled) "if(e)return;e=document.createElement('style');e.id='$ID';e.textContent=\"$CSS\";(document.head||document.documentElement).appendChild(e)})()"
        else "if(e)e.remove()})()"
}
