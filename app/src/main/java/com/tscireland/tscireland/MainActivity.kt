package com.tscireland.tscireland

import java.io.File
import java.io.FileInputStream

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams

/**
 * Single-activity wrapper that loads [tscireland.org](https://tscireland.org/) inside a
 * [WebView] with offline caching, edge-to-edge support, and external-link handling.
 */
class MainActivity : AppCompatActivity() {

    private val cacheFile by lazy { File(cacheDir, "offline_page.html") }
    private val mainUrl = "https://tscireland.org/"

    /** JavaScript interface for handling email links from WebView */
    inner class EmailJavaScriptInterface {
        @JavascriptInterface
        fun openEmail(emailUrl: String) {
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = emailUrl.toUri()
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Domains that should be opened in their native app when available. */
    private val socialDomains = listOf(
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

    // Suppressing as the site is rendered through Squarespace code.
    // TODO: Review their SDLC
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            enableEdgeToEdge()
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val webView = findViewById<WebView>(R.id.web)
        applyEdgeToEdgeInsets(webView)
        configureWebSettings(webView)
        webView.webViewClient = createWebViewClient()
        webView.loadUrl(mainUrl)
        registerBackNavigation(webView)
        supportActionBar?.hide()
    }

    // ── WebView configuration ────────────────────────────────────────────

    /** Applies system-bar insets as margins when edge-to-edge is enforced (API 35+). */
    private fun applyEdgeToEdgeInsets(webView: WebView) {
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
    }

    /** Configures JavaScript, DOM storage, caching, viewport, and zoom settings. */
    private fun configureWebSettings(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = if (isNetworkAvailable()) {
                WebSettings.LOAD_DEFAULT
            } else {
                WebSettings.LOAD_CACHE_ELSE_NETWORK
            }
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }
        // Add JavaScript interface for email handling
        webView.addJavascriptInterface(EmailJavaScriptInterface(), "AndroidInterface")
    }

    /** Registers the back-press callback so the hardware back button navigates WebView history. */
    private fun registerBackNavigation(webView: WebView) {
        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    // ── WebViewClient ────────────────────────────────────────────────────

    /** Creates a [WebViewClient] that handles link routing, offline caching, and error pages. */
    private fun createWebViewClient(): WebViewClient = object : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url?.toString() ?: return false
            val host = request.url?.host ?: return false

            // Open email links in external email app
            if (url.startsWith("mailto:", ignoreCase = true)) {
                return try {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = url.toUri()
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                    true
                } catch (e: Exception) {
                    // Log the error for debugging
                    e.printStackTrace()
                    false
                }
            }

            // Open PDFs in a Chrome Custom Tab
            if (url.endsWith(".pdf", ignoreCase = true)) {
                CustomTabsIntent.Builder().build().launchUrl(this@MainActivity, url.toUri())
                return true
            }

            if (!host.contains("tscireland.org", ignoreCase = true)) {
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
                    // All other external links — open in a Chrome Custom Tab
                    CustomTabsIntent.Builder().build().launchUrl(this@MainActivity, url.toUri())
                    return true
                }
            }

            return false
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            // Rewrite target="_blank" links so they go through shouldOverrideUrlLoading
            // Also handle mailto links that might not trigger shouldOverrideUrlLoading
            view?.evaluateJavascript("""
                (function() {
                    document.querySelectorAll('a[target="_blank"]').forEach(function(a) {
                        a.setAttribute('target', '_self');
                    });
                    // Handle mailto links
                    document.querySelectorAll('a[href^="mailto:"]').forEach(function(a) {
                        a.addEventListener('click', function(e) {
                            e.preventDefault();
                            window.AndroidInterface.openEmail(this.href);
                        });
                    });
                    new MutationObserver(function(mutations) {
                        mutations.forEach(function(m) {
                            m.addedNodes.forEach(function(node) {
                                if (node.querySelectorAll) {
                                    node.querySelectorAll('a[target="_blank"]').forEach(function(a) {
                                        a.setAttribute('target', '_self');
                                    });
                                    node.querySelectorAll('a[href^="mailto:"]').forEach(function(a) {
                                        a.addEventListener('click', function(e) {
                                            e.preventDefault();
                                            window.AndroidInterface.openEmail(this.href);
                                        });
                                    });
                                }
                            });
                        });
                    }).observe(document.body, {childList: true, subtree: true});
                })();
            """.trimIndent(), null)

            // Cache the page HTML for offline use
            if (isNetworkAvailable() && url?.startsWith(mainUrl) == true) {
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
            // Only handle main-frame errors to avoid replacing content for sub-resource failures
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
            // Serve cached HTML for the main page when offline
            if (!isNetworkAvailable() &&
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

    /**
     * Checks whether the device currently has an active network connection
     * over Wi-Fi, cellular, Ethernet, or Bluetooth.
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)
    }
}
