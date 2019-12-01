package me.digitalby.lr4

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Uri
import android.opengl.Visibility
import android.os.AsyncTask
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.nio.charset.Charset

class MainActivity : AppCompatActivity() {
    private var feed: RSSFeed? = null
    private var offlineMode = true
    private var itemsCached = 0
    private lateinit var io: FileIO

    private lateinit var titleTextView: TextView
    private lateinit var itemsListView: ListView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar

    private lateinit var adapter: SimpleAdapter
    private lateinit var sharedPreferences: SharedPreferences
    private val arrayList = ArrayList<HashMap<String, String?>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        titleTextView = findViewById(R.id.titleTextView)
        itemsListView = findViewById(R.id.list)
        swipeRefreshLayout = findViewById(R.id.pullToRefresh)
        progressBar = findViewById(R.id.progressBar)

        itemsListView.setOnItemClickListener { _, _, position, _ ->
            if(this.feed == null)
                return@setOnItemClickListener
            val feed = this.feed!!
            val item = feed.items[position]

            if(offlineMode) {
                val intent = Intent(this, WebViewActivity::class.java)
                intent.putExtra("item", item)
                startActivity(intent)
            } else {
                val uri = Uri.parse(item.link)
                val intent = Intent(Intent.ACTION_VIEW, uri)
                startActivity(intent)
            }
        }
        swipeRefreshLayout.setOnRefreshListener {
            refreshNow()
        }

        val itemResource = R.layout.listview_item
        val from = arrayOf("date", "title", "description", "image")
        val to = intArrayOf(
            R.id.textViewPubDate,
            R.id.textViewTitle,
            R.id.textViewDescription,
            R.id.imageDocumentIcon
        )

        adapter = SimpleAdapter(this, arrayList, itemResource, from, to)
        itemsListView.adapter = adapter

        adapter.setViewBinder { view, _, textRepresentation ->
            when{
                view.id == R.id.imageDocumentIcon -> {
                    val imageView = view as ImageView
                    if (textRepresentation.isNullOrEmpty()) {
                        imageView.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.newspaper))
                    } else {
                        val inputStream = FileInputStream(File(textRepresentation))
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        imageView.setImageBitmap(bitmap)
                        inputStream.close()
                    }
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

        sharedPreferences = getSharedPreferences("me.digitalby.lr4", Context.MODE_PRIVATE)

        val savedURL = sharedPreferences.getString("url", null)

        io = FileIO(applicationContext)

        if(savedURL.isNullOrEmpty()) {
            requestNewFeedURL(true)
        } else {
            val feed = loadFeed()
            if (feed == null) {
                refreshNow()
            } else {
                this.feed = feed
                updateDisplay()
                Snackbar.make(
                    itemsListView as View,
                    "Last retrieved: ${feed.lastRetrieved}", Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId == R.id.buttonChangeURL)
            requestNewFeedURL(false)
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    private fun requestNewFeedURL(mandatory: Boolean) {
        val builder = AlertDialog.Builder(this)
        val inputView = EditText(this)
        inputView.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        builder.setMessage("Enter URL for your desired RSS feed")
            .setTitle("New RSS feed")
            .setView(inputView)
            .setPositiveButton(getString(android.R.string.ok)) { _, _ ->
                val newURL = inputView.text.toString()
                if(newURL.isEmpty())
                    requestNewFeedURL(mandatory)
                else {
                    val edit = sharedPreferences.edit()
                    edit.putString("url", newURL)
                    edit.apply()
                    refreshNow()
                }
            }
        if(mandatory) {
            builder.setCancelable(false)
        } else {
            builder.setNegativeButton(getString(android.R.string.cancel)) { _, _ -> }
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun loadFeed(): RSSFeed? {
        return try {
            val fileInputStream = openFileInput("feed.lr4")
            val objectInputStream = ObjectInputStream(fileInputStream)
            val feed = objectInputStream.readObject() as RSSFeed?
            objectInputStream.close()
            fileInputStream.close()
            feed
        } catch (e: FileNotFoundException) {
            null
        }
    }

    private fun saveFeed() {
        try {
            val fileOutputStream = openFileOutput("feed.lr4", Context.MODE_PRIVATE)
            val objectOutputStream = ObjectOutputStream(fileOutputStream)
            objectOutputStream.writeObject(feed)
            objectOutputStream.close()
            fileOutputStream.close()
        } catch(e: InvalidClassException) {
            Log.e("News app save feed", "The class is not valid. $e")
        } catch (e: NotSerializableException) {
            Log.e("News app save feed", "The class is not serializable. $e")
        } catch (e: IOException) {
            Log.e("News app save feed", "$e")
        }
    }

    private fun refreshNow() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo

        if (networkInfo != null && networkInfo.isConnected) {
            DownloadFeed(this).execute()
        } else {
            tryLoadOffline()
        }
    }

    private fun tryLoadOffline() {
        val feed = this.feed?: loadFeed()
        if(feed != null && feed.offlineAvailable) {
            this.feed = feed
            updateDisplay()
            Snackbar.make(
                itemsListView as View,
                getString(R.string.cached_only), Snackbar.LENGTH_LONG
            ).show()
        } else {
            titleTextView.text = getString(R.string.no_connection)
            Snackbar.make(
                itemsListView as View,
                getString(R.string.no_connection_long), Snackbar.LENGTH_LONG
            ).show()
        }
        swipeRefreshLayout.isRefreshing = false
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

        updateArrayList()
        adapter.notifyDataSetChanged()
    }

    private fun precacheResources() {
        itemsCached = 0
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        for(i in 0 until feed!!.items.size) {
            val item = feed!!.items.elementAt(i)
            if(item.thumbnailURI.isNullOrEmpty())
                DownloadImage(this).execute(i)
            if (item.cachedContent.isNullOrEmpty()) {
                DownloadPage(this).execute(item)
            } else {
                itemsCached += 1
            }
        }
    }

    private fun tryFinishPrecaching() {
        if(!feed!!.offlineAvailable)
            return
        updateArrayList()
        adapter.notifyDataSetChanged()
        saveFeed()
        progressBar.visibility = View.GONE
        Snackbar.make(
            itemsListView as View,
            getString(R.string.precaching_complete),
            Snackbar.LENGTH_LONG
        ).show()
    }

    fun updateArrayList() {
        arrayList.clear()
        for (item in feed!!.items) {
            val map = HashMap<String, String?>()
            map["date"] = item.pubDateWithFormat
            map["title"] = item.title
            map["description"] = item.description
            map["image"] = item.thumbnailURI
            arrayList.add(map)
        }
    }

    private class DownloadFeed(context: MainActivity) : AsyncTask<String, Unit, Unit>() {

        private val activityReference: WeakReference<MainActivity> = WeakReference(context)
        override fun onPreExecute() {
            val activity = activityReference.get()
            if (activity == null || activity.isFinishing)
                return

            activity.pullToRefresh.isRefreshing = true
        }

        override fun doInBackground(vararg params: String?) {
            val activity = activityReference.get()
            if (activity == null || activity.isFinishing)
                return
            val url = activity.sharedPreferences.getString("url", null)
            val download = activity.io.downloadFile(url)
            if(!download)
                cancel(true)

        }

        override fun onPostExecute(result: Unit) {
            val activity = activityReference.get()
            if (activity == null || activity.isFinishing)
                return
            ReadFeed(activity).execute()
        }


        override fun onCancelled() {
            val activity = activityReference.get()
            if (activity == null || activity.isFinishing)
                return
            activity.tryLoadOffline()
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
            if(activity.feed != null) {
                activity.offlineMode = false
                activity.precacheResources()
            }
        }

    }

    private class DownloadImage(context: MainActivity) : AsyncTask<Int, Unit, Unit>() {
        private val activityReference: WeakReference<MainActivity> = WeakReference(context)

        override fun doInBackground(vararg params: Int?) {
            val activity = activityReference.get()
            if (activity == null || activity.isFinishing)
                return
            val index = params[0]!!
            val item = activity.feed?.items?.elementAt(index)
            val image = Picasso.get()
                .load(item?.thumbnailURL)
                .noFade()
                .resize(96, 96)
                .centerInside()
                .placeholder(R.drawable.newspaper)
                .get()
            if(image != null) {
                val fileOutputStream = activity.openFileOutput("$index.png", Context.MODE_PRIVATE)
                image.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
                val file = File("$index.png")
                item?.thumbnailURI = "${activity.filesDir.absolutePath}/${file.path}"
                fileOutputStream.close()
            }
        }

        override fun onPostExecute(result: Unit) {
            val activity = activityReference.get()
            if (activity == null || activity.isFinishing)
                return
            val adapter = activity.itemsListView.adapter as SimpleAdapter
            activity.updateArrayList()
            adapter.notifyDataSetChanged()
        }
    }

    private class DownloadPage(context: MainActivity) : AsyncTask<RSSItem, Int, Unit>() {
        private val activityReference: WeakReference<MainActivity> = WeakReference(context)
        override fun doInBackground(vararg params: RSSItem?) {
            val activity = activityReference.get()
            if (activity == null || activity.isFinishing)
                return

            val entity = params[0] ?: return
            val link = entity.link ?: return
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
            activity.itemsCached += 1
            publishProgress(activity.itemsCached * 100 / activity.feed!!.items.count())
        }

        override fun onPostExecute(result: Unit?) {
            val activity = activityReference.get()
            if (activity == null || activity.isFinishing)
                return
            activity.tryFinishPrecaching()
        }

        override fun onProgressUpdate(vararg values: Int?) {
            val activity = activityReference.get()
            if (activity == null || activity.isFinishing)
                return
            activity.progressBar.progress = values[0]!!
        }
    }

}

