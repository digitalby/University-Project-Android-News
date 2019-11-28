package me.digitalby.lr4

import java.lang.RuntimeException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.Checksum
import kotlin.collections.ArrayList

class RSSFeed(val items: ArrayList<RSSItem>) {
    var title: String? = null
    var pubDate: String? = null
    val pubDateMilliseconds: Long?
    get() {
        try {
            if(pubDate == null)
                return null
            val date = simpleDateFormat.parse(pubDate!!.trim())
            return date?.time
        } catch(e: ParseException) {
            throw RuntimeException(e)
        }
    }
    val offlineAvailable: Boolean
    get() {
        for(item in items) {
            if(item.cachedContent.isNullOrEmpty())
                return false
        }
        return true
    }

    private val simpleDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.getDefault())
}