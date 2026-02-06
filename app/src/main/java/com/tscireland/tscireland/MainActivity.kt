package com.tscireland.tscireland
import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.net.toUri
import android.os.Bundle
import androidx.activity.addCallback
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.File
import java.io.FileInputStream
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams

class MainActivity : AppCompatActivity() {
    private val cacheFile by lazy { File(cacheDir, "offline_page.html") }
    private val mainUrl = "https://tscireland.com/"
    // Suppressing as the site is rendered through Squarespace code.
    // TODO: Review their SDLC
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        // Find the WebView by its unique ID
        val webView = findViewById<WebView>(R.id.web)

        // Handle system insets using margins
        ViewCompat.setOnApplyWindowInsetsListener(webView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> {
                topMargin = insets.top
                bottomMargin = insets.bottom
            }
            windowInsets
        }

        // loading url in the WebView
        webView.loadUrl(mainUrl)

        // this will enable the javascript.
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.cacheMode = if (isNetworkAvailable(applicationContext)) {
            WebSettings.LOAD_DEFAULT
        } else {
            WebSettings.LOAD_CACHE_ELSE_NETWORK
        }

        // Prevent content from going off screen
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.setSupportZoom(true)
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false

        // WebViewClient allows you to handle
        // onPageFinished and override Url loading.
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString()
                if (url?.endsWith(".pdf", ignoreCase = true) == true) {
                    CustomTabsIntent.Builder().build().launchUrl(this@MainActivity, url.toUri())
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Save page content for offline use
                if (isNetworkAvailable(applicationContext) && url?.startsWith(mainUrl) == true) {
                    view?.evaluateJavascript("document.documentElement.outerHTML") { html ->
                        val decoded = html?.replace("\\u003C", "<")?.replace("\\\"", "\"")
                        cacheFile.writeText(decoded ?: "")
                    }
                }
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                // Serve cached HTML for main page when offline
                if (!isNetworkAvailable(applicationContext) && 
                    (url == mainUrl || url == "${mainUrl}/") && 
                    cacheFile.exists()) {
                    return WebResourceResponse(
                        "text/html",
                        "UTF-8",
                        FileInputStream(cacheFile)
                    )
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
        // Handle back button to navigate in WebView history
        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        supportActionBar?.hide()
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val nw = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(nw) ?: return false
        return when {
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            //for other device how are able to connect with Ethernet
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            //for check internet over Bluetooth
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> true
            else -> false
        }
    }
}
