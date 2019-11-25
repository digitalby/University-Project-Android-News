package com.example.lr4

import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.ref.WeakReference

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
            if(this.feed == null)
                return@setOnItemClickListener
            val feed = this.feed!!
            val item = feed.items[position]

            val uri = Uri.parse(item.link)
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }
        swipeRefreshLayout.setOnRefreshListener {
            DownloadFeed(this).execute()
        }

        io = FileIO(applicationContext)

        DownloadFeed(this).execute()
    }

    private fun updateDisplay() {
        if(this.feed == null) {
            titleTextView.text = getString(R.string.no_rss_feed)
            return
        }
        val feed = this.feed!!

        titleTextView.text = feed.title
        val items = feed.items

        val data = ArrayList<HashMap<String, String?>>()
        for(item in items) {
            val map = HashMap<String, String?>()
            map["date"] = item.pubDateWithFormat
            map["title"] = item.title
            map["description"] = item.description
            data.add(map)
        }

        val itemResource = R.layout.listview_item
        val from = arrayOf("date", "title", "description")
        val to = intArrayOf(R.id.textViewPubDate, R.id.textViewTitle, R.id.textViewDescription)

        val adapter = SimpleAdapter(this, data, itemResource, from, to)
        itemsListView.adapter = adapter
    }

    private class DownloadFeed(context: MainActivity) : AsyncTask<String, Void, String>() {
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

    private class ReadFeed(context: MainActivity) : AsyncTask<Void, Void, Void?>() {
        private val activityReference: WeakReference<MainActivity> = WeakReference(context)

        override fun doInBackground(vararg params: Void?): Void? {
            val activity = activityReference.get()
            if (activity == null || activity.isFinishing)
                return null
            activity.feed = activity.io.readFile()
            return null
        }

        override fun onPostExecute(result: Void?) {
            val activity = activityReference.get()
            if (activity == null || activity.isFinishing)
                return
            activity.updateDisplay()
            activity.pullToRefresh.isRefreshing = false
        }
    }
}
