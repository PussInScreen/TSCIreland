package com.tscireland.tscireland
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.net.toUri
import android.os.Build
import android.os.Bundle
import androidx.activity.addCallback
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
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
    private val mainUrl = "https://tscireland.org/"
    // Suppressing as the site is rendered through Squarespace code.
    // TODO: Review their SDLC
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            enableEdgeToEdge()
        }
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        // Find the WebView by its unique ID
        val webView = findViewById<WebView>(R.id.web)

        // Handle system insets using margins (only needed when edge-to-edge is enforced)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ViewCompat.setOnApplyWindowInsetsListener(webView) { view, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> {
                    topMargin = insets.top
                    bottomMargin = insets.bottom
                }
                windowInsets
            }
        }

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
                val url = request?.url?.toString() ?: return false
                val host = request.url?.host ?: return false

                if (url.endsWith(".pdf", ignoreCase = true)) {
                    CustomTabsIntent.Builder().build().launchUrl(this@MainActivity, url.toUri())
                    return true
                }

                if (!host.contains("tscireland.org", ignoreCase = true)) {
                    val socialDomains = listOf(
                        "facebook.com", "fb.com",
                        "instagram.com",
                        "twitter.com", "x.com",
                        "linkedin.com",
                        "youtube.com", "youtu.be",
                        "tiktok.com",
                        "snapchat.com",
                        "threads.net",
                        "pinterest.com"
                    )
                    val isSocial = socialDomains.any { host.endsWith(it, ignoreCase = true) }

                    if (isSocial) {
                        // Open in native app only — fall back to WebView if not installed
                        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                            addCategory(Intent.CATEGORY_BROWSABLE)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER
                        }
                        return try {
                            startActivity(intent)
                            true
                        } catch (_: ActivityNotFoundException) {
                            false
                        }
                    } else {
                        // All other external links — open in a browser tab
                        CustomTabsIntent.Builder().build().launchUrl(this@MainActivity, url.toUri())
                        return true
                    }
                }

                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Rewrite target="_blank" links so they go through shouldOverrideUrlLoading
                view?.evaluateJavascript("""
                    (function() {
                        document.querySelectorAll('a[target="_blank"]').forEach(function(a) {
                            a.setAttribute('target', '_self');
                        });
                        new MutationObserver(function(mutations) {
                            mutations.forEach(function(m) {
                                m.addedNodes.forEach(function(node) {
                                    if (node.querySelectorAll) {
                                        node.querySelectorAll('a[target="_blank"]').forEach(function(a) {
                                            a.setAttribute('target', '_self');
                                        });
                                    }
                                });
                            });
                        }).observe(document.body, {childList: true, subtree: true});
                    })();
                """.trimIndent(), null)
                // Save page content for offline use
                if (isNetworkAvailable(applicationContext) && url?.startsWith(mainUrl) == true) {
                    view?.evaluateJavascript("document.documentElement.outerHTML") { html ->
                        val decoded = html?.replace("\\u003C", "<")?.replace("\\\"", "\"")
                        cacheFile.writeText(decoded ?: "")
                    }
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                // Only handle main frame errors to avoid showing error page for subresource failures
                if (request?.isForMainFrame == true) {
                    val errorDescription = error?.description?.toString() ?: "Unknown error"
                    view?.loadDataWithBaseURL(
                        null,
                        buildErrorPage(
                            "Connection Error",
                            "We couldn't load the page. Please check your internet connection and try again.",
                            errorDescription
                        ),
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                if (request?.isForMainFrame == true) {
                    val statusCode = errorResponse?.statusCode ?: 0
                    view?.loadDataWithBaseURL(
                        null,
                        buildErrorPage(
                            "Error $statusCode",
                            "Something went wrong while loading the page.",
                            "HTTP $statusCode"
                        ),
                        "text/html",
                        "UTF-8",
                        null
                    )
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
        // loading url in the WebView
        webView.loadUrl(mainUrl)

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

    private fun buildErrorPage(title: String, message: String, detail: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        min-height: 100vh;
                        margin: 0;
                        background-color: #f5f5f5;
                        color: #333;
                    }
                    .container {
                        text-align: center;
                        padding: 32px;
                        max-width: 400px;
                    }
                    .icon {
                        font-size: 64px;
                        margin-bottom: 16px;
                    }
                    h1 {
                        font-size: 22px;
                        margin-bottom: 8px;
                        color: #222;
                    }
                    p {
                        font-size: 16px;
                        line-height: 1.5;
                        color: #666;
                        margin-bottom: 24px;
                    }
                    .detail {
                        font-size: 12px;
                        color: #999;
                        margin-bottom: 24px;
                    }
                    button {
                        background-color: #6200EE;
                        color: white;
                        border: none;
                        border-radius: 24px;
                        padding: 12px 32px;
                        font-size: 16px;
                        cursor: pointer;
                        font-weight: 500;
                    }
                    button:active {
                        background-color: #3700B3;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="icon">⚠️</div>
                    <h1>$title</h1>
                    <p>$message</p>
                    <p class="detail">$detail</p>
                    <button onclick="window.location.href='$mainUrl'">Try Again</button>
                </div>
            </body>
            </html>
        """.trimIndent()
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
