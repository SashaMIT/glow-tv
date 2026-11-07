# R8 rules: keep Glow itself readable and whole; shrink only the libraries.
-dontobfuscate
-keep class com.tv98.glow.** { *; }
# WebView only exposes methods that still carry @JavascriptInterface at runtime (the live cam player).
-keepattributes RuntimeVisibleAnnotations

# Annotation-only and optional dependencies the libraries reference but never need at runtime.
-dontwarn androidx.annotation.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**
-dontwarn kotlin.**
-dontwarn com.google.common.util.concurrent.ListenableFuture
