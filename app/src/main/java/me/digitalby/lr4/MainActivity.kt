package me.digitalby.lr4

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import com.squareup.picasso.Picasso
import com.squareup.picasso.RequestCreator
import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.nio.charset.Charset

class MainActivity : AppCompatActivity() {
    private var feed: RSSFeed? = RSSFeed(ArrayList())
    private lateinit var io: FileIO
    private lateinit var titleTextView: TextView
    private lateinit var itemsListView: ListView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        titleTextView = findViewById(R.id.titleTextView)
        itemsListView = findViewById(R.id.list)
        swipeRefreshLayout = findViewById(R.id.pullToRefresh)

        itemsListView.setOnItemClickListener { _, _, position, _ ->
            //TODO webview support
            if(this.feed == null)
                return@setOnItemClickListener
            val feed = this.feed!!
            val item = feed.items[position]

            val uri = Uri.parse(item.link)
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }
        swipeRefreshLayout.setOnRefreshListener {
            refreshNow(this)
        }

        io = FileIO(applicationContext)
        refreshNow(this)
    }

    private fun refreshNow(context: Context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo

        if (networkInfo != null && networkInfo.isConnected) {
            DownloadFeed(this).execute()
        } else {
            //TODO: check if a cached version is available
            titleTextView.text = getString(R.string.no_connection)
            Snackbar.make(
                itemsListView as View,
                getString(R.string.no_connection_long), Snackbar.LENGTH_LONG
            ).show()
            swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun updateDisplay() {
        if(this.feed == null) {
            titleTextView.text = getString(R.string.no_rss_feed)
            Snackbar.make(
                itemsListView as View,
                getString(R.string.no_rss_feed_long), Snackbar.LENGTH_LONG
            ).show()
            return
        }
        val feed = this.feed!!

        titleTextView.text = feed.title
        val items = feed.items

        val arrayList = ArrayList<HashMap<String, String?>>()
        for(item in items) {
            val map = HashMap<String, String?>()
            map["date"] = item.pubDateWithFormat
            map["title"] = item.title
            map["description"] = item.description
            map["imageURL"] = item.thumbnailURL
            arrayList.add(map)
        }

        val itemResource = R.layout.listview_item
        val from = arrayOf("date", "title", "description", "imageURL")
        val to = intArrayOf(
            R.id.textViewPubDate,
            R.id.textViewTitle,
            R.id.textViewDescription,
            R.id.imageDocumentIcon
        )

        val adapter = SimpleAdapter(this, arrayList, itemResource, from, to)
        itemsListView.adapter = adapter

        adapter.setViewBinder { view, data, textRepresentation ->
            when{
                view.id == R.id.imageDocumentIcon -> {
                    val imageView = view as ImageView
                    DownloadImage(this)
                        .execute(Pair(imageView, textRepresentation))
                    true
                }
                view.id in arrayOf(
                    R.id.textViewPubDate,
                    R.id.textViewTitle,
                    R.id.textViewDescription
                ) -> {
                    val textView = view as TextView
                    textView.text = textRepresentation
                    true
                }
                else -> false
            }
        }
        if(!feed.offlineAvailable)
            DownloadPages(this).execute()
    }

    private class DownloadImage(context: MainActivity) : AsyncTask<Pair<ImageView, String>, Unit, Pair<RequestCreator, ImageView>>() {
        private val activityReference: WeakReference<MainActivity> = WeakReference(context)

        override fun doInBackground(vararg params: Pair<ImageView, String>?): Pair<RequestCreator, ImageView>? {
            val activity = activityReference.get()
            if (activity == null || activity.isFinishing)
                return null
            val pair = params[0]!!
            return Pair(Picasso.get()
                .load(Uri.parse(pair.second))
                .noFade()
                .resize(96, 96)
                .centerInside()
                .placeholder(R.drawable.newspaper), pair.first)
        }

        override fun onPostExecute(result: Pair<RequestCreator, ImageView>?) {
            if(result == null)
                return
            result.first.into(result.second)
        }
    }

    private class DownloadFeed(context: MainActivity) : AsyncTask<String, Unit, String>() {
        private val activityReference: WeakReference<MainActivity> = WeakReference(context)

        override fun onPreExecute() {
            val activity = activityReference.get()
            if (activity == null || activity.isFinishing)
                return

            activity.pullToRefresh.isRefreshing = true
        }

        override fun doInBackground(vararg params: String?): String {
            val activity = activityReference.get()
            if (activity == null || activity.isFinishing)
                return ""
            activity.io.downloadFile()
            return ""
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            val activity = activityReference.get()
            if (activity == null || activity.isFinishing)
                return
            ReadFeed(activity).execute()
        }
    }

    private class ReadFeed(context: MainActivity) : AsyncTask<Unit, Unit, Unit>() {
        private val activityReference: WeakReference<MainActivity> = WeakReference(context)

        override fun doInBackground(vararg params: Unit) {
            val activity = activityReference.get()
            if (activity == null || activity.isFinishing)
                return
            val newFeed = activity.io.readFile()
            activity.feed = newFeed
        }

        override fun onPostExecute(result: Unit) {
            val activity = activityReference.get()
            if (activity == null || activity.isFinishing)
                return
            activity.updateDisplay()
            activity.pullToRefresh.isRefreshing = false
        }
    }

    private class DownloadPages(context: MainActivity) : AsyncTask<Unit, Unit, Unit>() {
        private val activityReference: WeakReference<MainActivity> = WeakReference(context)

        override fun onPreExecute() {
            val activity = activityReference.get()
            if (activity == null || activity.isFinishing)
                return
            Snackbar.make(
                activity.itemsListView as View,
                activity.getString(R.string.precaching_started), Snackbar.LENGTH_LONG
            ).show()
        }

        override fun doInBackground(vararg params: Unit?) {
            val activity = activityReference.get()
            if (activity == null || activity.isFinishing)
                return
            for(entity in activity.feed!!.items) {
                val link = entity.link ?: continue
                try
                {
                    var url = URL(link)
                    var urlConnection = url.openConnection()
                    var httpURLConnection = urlConnection as HttpURLConnection
                    httpURLConnection.instanceFollowRedirects = true
                    var responseCode = httpURLConnection.responseCode
                    while (responseCode in 301 until 401) {
                        val redirectHeader = httpURLConnection.getHeaderField("Location")
                        if(redirectHeader.isNullOrEmpty()) {
                            Log.e("News app", "Can't redirect")
                            continue
                        }
                        httpURLConnection.disconnect()
                        url = URL(redirectHeader)
                        urlConnection = url.openConnection()
                        httpURLConnection = urlConnection as HttpURLConnection
                        responseCode = httpURLConnection.responseCode
                    }
                    val inputStream = httpURLConnection.inputStream
                    val bufferedInputStream = BufferedInputStream(inputStream)

                    val data = bufferedInputStream.readBytes()
                    val stringData = data.toString(Charset.defaultCharset())
                    entity.cachedContent = stringData
                } catch (e: MalformedURLException) {
                } catch (e: IOException) {
                }
            }
        }

        override fun onPostExecute(result: Unit?) {
            val activity = activityReference.get()
            if (activity == null || activity.isFinishing)
                return
            Snackbar.make(
                activity.itemsListView as View,
                activity.getString(R.string.precaching_complete), Snackbar.LENGTH_LONG
            ).show()
            //TODO handle image loading and precaching
            //TODO save the cached version
        }

    }
}
