package me.digitalby.lr4

import java.lang.RuntimeException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
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

    private val simpleDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.getDefault())
}