# TSC Ireland — Android App

A lightweight Android app that wraps [tscireland.org](https://tscireland.org/) in a WebView with offline caching, edge-to-edge display, and external-link handling.

## Features

- **WebView wrapper** — loads tscireland.org as a single-activity app
- **Offline caching** — saves the homepage HTML for offline viewing
- **External link routing** — social media links open in native apps; PDFs and other external links open in Chrome Custom Tabs
- **Edge-to-edge support** — respects system bar insets on API 35+
- **Back navigation** — hardware back button navigates WebView history
- **Error pages** — user-friendly error screens for connectivity and HTTP failures

## Requirements

- **Android Studio** (Ladybug or later recommended)
- **JDK 17** (for Gradle builds)
- **Min SDK 33** (Android 13)

## Getting Started

1. Clone the repository
2. Open the project in Android Studio
3. Sync Gradle and build
4. Run on a device or emulator (API 33+)

## Project Structure

```text
app/
  src/main/
    java/com/tscireland/tscireland/
      MainActivity.kt          # Single activity with WebView setup
    res/
      layout/activity_main.xml  # WebView layout
      values/themes.xml         # Light theme
      values-night/themes.xml   # Dark theme
gradle/
  libs.versions.toml            # Version catalog
.github/workflows/
  android.yml                   # CI build workflow
```

## Building a Release

Release signing requires a `keystore.properties` file in the project root:

```properties
storeFile=path/to/keystore.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

## Security & Privacy

- All traffic uses HTTPS
- Only tscireland.org homepage content is cached for offline use
- JavaScript is enabled to support the Squarespace-hosted site

## Contributing

Fork, branch, PR. Include a description of changes and testing steps.

## License

GPLv3

## Contact

TSC Ireland — <info@tscireland.org>
