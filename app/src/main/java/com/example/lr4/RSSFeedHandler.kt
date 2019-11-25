package com.example.lr4

import android.util.Log
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

class RSSFeedHandler: DefaultHandler() {
    var feed: RSSFeed? = null; private set
    private var item: RSSItem? = null

    private var didReadFeedTitle = false
    private var didReadFeedPubDate = false

    private var isTitle = false
    private var isDescription = false
    private var isLink = false
    private var isPubDate = false

    override fun startDocument() {
        feed = RSSFeed(ArrayList())
        item = RSSItem()
    }

    override fun endDocument() {

    }

    override fun startElement(
        uri: String?,
        localName: String?,
        qName: String?,
        attributes: Attributes?
    ) {
        when(qName) {
            "item" -> RSSItem()
            "title" -> isTitle = true
            "description" -> isDescription = true
            "link" -> isLink = true
            "pubDate" -> isPubDate = true
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        when(qName) {
            "item" -> {
                if(item != null)
                    feed?.items?.add(item!!)
            }
        }
    }

    override fun characters(ch: CharArray?, start: Int, length: Int) {
        if(ch == null) {
            Log.e("News app", "Got empty CharArray")
            return
        }
        val string = String(ch, start, length)
        when {
            isTitle -> {
                if(didReadFeedTitle) {
                    item?.title = string
                } else {
                    feed?.title = string
                    didReadFeedTitle = true
                }
                isTitle = false
            }
            isLink -> {
                item?.link = string
                isLink = false
            }
            isDescription -> {
                if(string.startsWith("<"))
                    item?.description = ("No description available.")
                else
                    item?.description = string
                isDescription = false
            }
            isPubDate -> {
                if(didReadFeedPubDate)
                    item?.pubDate = string
                else {
                    feed?.pubDate = string
                    didReadFeedPubDate = true
                }
                isPubDate = false
            }
        }
    }

}