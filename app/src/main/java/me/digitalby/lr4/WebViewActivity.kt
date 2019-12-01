package me.digitalby.lr4

import android.app.Activity
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Xml
import android.webkit.WebView

class WebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_view)

        webView = findViewById(R.id.mainWebView)

        val intent = this.intent
        val data: RSSItem = intent.getSerializableExtra("item") as RSSItem? ?: return
        webView.loadData(data.cachedContent, "text/html", "UTF-8")
    }
}
