# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep WebView JS interface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep WebViewClient and WebView subclasses
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String);
    public boolean *(android.webkit.WebView, java.lang.String);
}

# Preserve line numbers for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile