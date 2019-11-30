package me.digitalby.lr4

import android.content.Context
import android.util.Log
import org.xml.sax.InputSource
import java.io.File
import java.io.IOException
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.Checksum
import javax.xml.parsers.SAXParserFactory

class FileIO(private val context: Context) {
    companion object {
        private const val URL_STRING = "https://www.nbcnewyork.com/news/politics/?rss=y&embedThumb=y&summary=y"
        private const val FILENAME = "news_feed.xml"
    }

    fun downloadFile(): Boolean {
        try {
            val url = URL(URL_STRING)
            val urlConnection = url.openConnection()
            val inputStream = urlConnection.getInputStream()

            val fileOutputStream = context.openFileOutput(FILENAME, Context.MODE_PRIVATE)

            val bytes = inputStream.readBytes()
            fileOutputStream.write(bytes)

            inputStream.close()
            fileOutputStream.close()
            return true
        } catch (e: IOException) {
            val fileOutputStream = context.openFileOutput(FILENAME, Context.MODE_PRIVATE)
            val bytes = byteArrayOf()
            fileOutputStream.write(bytes)
            return false
        }
    }

    fun readFile(): RSSFeed? {
        try {
            val factory = SAXParserFactory.newInstance()
            val parser = factory.newSAXParser()
            val xmlReader = parser.xmlReader

            val rssHandler = RSSFeedHandler()
            xmlReader.contentHandler = rssHandler

            val inputStream = context.openFileInput(FILENAME)
            val inputSource = InputSource(inputStream)
            xmlReader.parse(inputSource)


            return rssHandler.feed
        } catch (e: Exception) {
            Log.e("News reader", e.toString())
            return null
        }
    }
}