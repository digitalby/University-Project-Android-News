package me.digitalby.lr4

import java.io.Serializable
import java.lang.RuntimeException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.Checksum
import kotlin.collections.ArrayList

class RSSFeed(val items: ArrayList<RSSItem>): Serializable {
    var title: String? = null
    var pubDate: String? = null
    var lastRetrieved: String? = null
    val offlineAvailable: Boolean
    get() {
        for(item in items) {
            if(item.cachedContent.isNullOrEmpty())
                return false
            if(item.thumbnailURI.isNullOrEmpty())
                return false
        }
        return true
    }

    //private val simpleDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.getDefault())
}