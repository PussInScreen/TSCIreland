package com.tscireland.tscireland

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Find the WebView by its unique ID
        val webView = findViewById<WebView>(R.id.web)

        // loading url in the WebView.
        webView.loadUrl("https://tscireland.com/")

        // this will enable the javascript.
        webView.settings.javaScriptEnabled = true

        // WebViewClient allows you to handle
        // onPageFinished and override Url loading.
        webView.webViewClient = WebViewClient()
    }
}