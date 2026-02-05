# Pixel Tracker Library

Android library for tracking 1x1 pixel visibility with detailed logging.

## Features
- 📊 Tracks when a 1x1 transparent pixel becomes visible on screen
- 🎯 Configurable visibility threshold and debounce time
- 📝 Customizable logging (console, file, network, etc.)
- 🆔 Unique pixel IDs for multiple trackers
- 📈 Detailed metadata and statistics
- 🎨 Easy integration with XML or programmatically

## Installation

Add JitPack repository to your root `build.gradle`:

```gradle
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```
# Add dependency:
```gradle
dependencies {
    implementation 'com.github.yourusername:pixel-tracker:1.0.0'
}
```
## Usage

# Programmatic creation:
```kotlin
val pixelTracker = PixelTracker.create(context, "my_pixel_id")
pixelTracker.startTracking()

// Get stats
val stats = pixelTracker.getStats()

// Stop tracking when done
pixelTracker.stopTracking()
XML layout:
xml
<com.example.pixeltracker.PixelTracker
    android:layout_width="1px"
    android:layout_height="1px"
    app:pixelId="my_pixel"
    app:logTag="MyTracker"
    app:debounceTime="100"
    app:visibilityThreshold="1"
    app:isDebugMode="true" />
```
Demo App

The project includes a demo app that shows:
* Pixel placed at the bottom of scrollable content
* Real-time visibility tracking
* Toast notifications when pixel appears
* Statistics display
* Reset functionality
Logging
By default, logs are sent to Logcat with tag "PixelTracker". You can customize logging:
kotlin
pixelTracker.setCustomLogger(object : PixelTracker.PixelLogger {
    override fun logAppearance(pixelId: String, timestamp: String, metadata: Map<String, Any>) {
        // Send to your analytics service
    }
    
    override fun logDisappearance(pixelId: String, timestamp: String, metadata: Map<String, Any>) {
        // Custom logging logic
    }
    
    override fun logError(pixelId: String, error: String, timestamp: String) {
        // Error handling
    }
})
Configuration


Parameter	Default	Description
pixelId	Random UUID	Unique identifier for the pixel
logTag	"PixelTracker"	Tag for logging
debounceTime	100ms	Time to wait before logging visibility changes
visibilityThreshold	1px	Minimum visible size to trigger logging
isDebugMode	true	Enable/disable debug logging