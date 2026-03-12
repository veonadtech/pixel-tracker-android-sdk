# Pixel Tracker Android SDK


A lightweight Android SDK for tracking pixel visibility events.  
Designed for ad impressions, analytics, and user behavior monitoring.

---

## Table of Contents

- [Requirements](#requirements)
- [Installation](#installation)
- [Initialize SDK](#initialize-sdk)
- [Usage](#usage)
- [Pixel Events](#pixel-events)
- [Statistics](#statistics)
- [Control Tracking](#control-tracking)
- [Troubleshooting](#troubleshooting)
- [Support](#support)

---

## Overview

Pixel Tracker SDK provides:

- 👁 Automatic visibility detection
- 🔄 Configurable refresh counting
- 🎯 Visibility threshold configuration
- 📊 Real-time statistics
- 🐛 Debug visualization mode
- 📦 Minimal footprint

---

## Requirements

- Android API 19+
- Kotlin 1.8+
- Gradle 8.3+

---

## Installation

### Use Maven

#### 1. Add JitPack repository

`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

#### 2. Add dependency

`build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.veonadtech:pixel-tracker-android-sdk:0.1.0")
}
```

> Replace `0.1.0` with the latest version.

---

## Initialize SDK

Initialize once at app startup

```kotlin
class Demo : Application() {

    companion object {
        private const val TAG = "PixelTracker"
        private const val BASEURL = "Enter your base URL here"
        private const val ISDEBUGMODE = true
    }

    override fun onCreate() {
        super.onCreate()

        PixelTracker.initialize(BASEURL, ISDEBUGMODE) { status ->
            when (status) {
                is InitStatus.Success ->
                    Log.d(TAG, "Pixel tracker SDK initialized with URL: $BASEURL, debugMode: $ISDEBUGMODE")
                is InitStatus.Failure -> {
                    Log.e(TAG, "${status.reason}: ${status.exception.message}", status.exception)
                }
            }
        }
    }
}
```

Add Internet permission:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

---

## Usage

### Attach Pixel

- Create pixelContainer programmatically or via XML 
with pixel size (visible in debug mode, 1x1 in production).
- Add container to your layout
- Attach pixel tracker
```kotlin
val pixelTracker = PixelTracker.attach(
    context = this,
    container = pixelContainer,
    config = PixelConfig(
        pixelId = "home_screen_banner",
        refreshTimeSeconds = 30,
        pixelSize = 1,
        visibilityThreshold = 1
    )
)

pixelTracker.start()

```

---

## Pixel Events

```kotlin
pixelTracker.setEventListener(object : PixelEventListener {

    override fun onAppearance(pixelId: String, timestamp: String) {}

    override fun onDisappearance(pixelId: String, timestamp: String) {}

    override fun onRefresh(pixelId: String, timestamp: String) {}

    override fun onError(pixelId: String, error: String, timestamp: String) {}
})
```

---

## Statistics

```kotlin
val stats = pixelTracker.getStats()

Log.d("PixelStats", """
Total Appearances: ${stats.totalAppearances}
Currently Visible: ${stats.isCurrentlyVisible}
Next Refresh In: ${stats.nextRefreshInMs}ms
""")
```

---

## Control Tracking

```kotlin
// Start tracking
pixelTracker.start()
// Stop tracking
pixelTracker.stop()
// Clean up when done
pixelTracker.destroy()
// Set the interval at which the pixel's visibility is checked
// The default value is 3 seconds
pixelTracker.setVisibilityCheckInterval(ts) //ts - time in sec
```
### Best Practices

```kotlin
override fun onPause() {
super.onPause()
pixelTracker.stop()
}

override fun onDestroy() {
super.onDestroy()
pixelTracker.destroy()
}
```

---

## Troubleshooting

### Events not sending

- Ensure INTERNET permission is added
- Verify `baseUrl` is correct

### No debug logs

- Set `isDebugMode = true`

---

## Support

[GitHub Issues](https://github.com/veonadtech/pixel-tracker-android-sdk/issues)
