# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# PixelTracker public API
-keep class com.veonadtech.pixeltracker.PixelTracker {
    *;
}

# Public data classes
-keep class com.veonadtech.pixeltracker.api.PixelConfig {
    *;
}

-keep class com.veonadtech.pixeltracker.api.PixelStats {
    *;
}

-keep class com.veonadtech.pixeltracker.api.PixelEventListener {
    *;
}

-keep interface com.veonadtech.pixeltracker.api.PixelHandle {
    *;
}

-keep interface com.veonadtech.pixeltracker.api.PixelLogger {
    *;
}

# GSON MODEL CLASSES

-keep class com.veonadtech.pixeltracker.internal.model.PixelEvent {
    *;
}

# GSON ANNOTATIONS

-keepattributes *Annotation*, Signature, Exception
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# for debug
# -keepattributes SourceFile,LineNumberTable
# -renamesourcefileattribute SourceFile