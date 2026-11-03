package com.pelican.comictv.tv

/** A session owns one reviewed installation. Tokens cannot install a different, expired or replayed URL. */
class TvPreviewRegistry<T>(private val clock:()->Long,private val lifetimeMs:Long=5*60*1000L) {
    private var current:Triple<String,Long,T>?=null
    @Synchronized fun issue(value:T):String=java.util.UUID.randomUUID().toString().also{current=Triple(it,clock(),value)}
    @Synchronized fun peek():Pair<String,T>? {
        val p=current?:return null
        if(clock()-p.second !in 0 until lifetimeMs){current=null;return null}
        return p.first to p.third
    }
    @Synchronized fun consume(token:String):T? {
        val p=peek()?:return null
        if(p.first!=token)return null
        current=null;return p.second
    }
    @Synchronized fun clear(){current=null}
}
