package com.example.lr4

import android.content.Context
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import java.lang.ref.WeakReference

class MainActivity : AppCompatActivity() {

    companion object {
        private const val URL_STRING = "http://rss.cnn.com/rss/cnn_tech.rss"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        DownloadFeed(WeakReference(this)).execute()
    }

    class DownloadFeed(private val context: WeakReference<Context>) : AsyncTask<String, Void, String>() {
        override fun doInBackground(vararg params: String?): String {
            val urlString = params[0]

            //TODO download and write

            return "Feed downloaded"
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            Toast.makeText(context.get(), result, Toast.LENGTH_LONG).show()
        }
    }
}
