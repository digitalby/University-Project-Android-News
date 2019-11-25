package com.example.lr4

import java.lang.RuntimeException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class RSSItem(
    var title: String? = null,
    var description: String? = null,
    var link: String? = null,
    var pubDate: String? = null
) {

    val pubDateWithFormat: String?
    get() {
        try {
            if(pubDate == null)
                return null
            val date = inputFormat.parse(pubDate!!.trim()) ?: return null
            return outputFormat.format(date)
        } catch(e: ParseException) {
            throw RuntimeException(e)
        }
    }

    private val outputFormat = SimpleDateFormat("EEEE h:mm a (MMM d)", Locale.getDefault())
    private val inputFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.getDefault())

}
