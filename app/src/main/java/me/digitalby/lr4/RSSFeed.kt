package me.digitalby.lr4

import java.io.Serializable

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