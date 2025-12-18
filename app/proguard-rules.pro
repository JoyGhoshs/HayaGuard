-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-keepclassmembers class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.vision.** { *; }
-dontwarn com.google.mlkit.**

-keep class org.chromium.net.** { *; }
-keep class com.google.android.gms.net.** { *; }
-dontwarn org.chromium.net.**

-keepclassmembers class com.hayaguard.app.FeedScraperJS { public *; }
-keepclassmembers class com.hayaguard.app.QuickLensJS { public *; }
-keepclassmembers class com.hayaguard.app.FriendsOnlyJS { public *; }
-keepclassmembers class com.hayaguard.app.HideReelsJS { public *; }

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class com.hayaguard.app.** { *; }

-dontwarn kotlin.**
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

-keep class androidx.** { *; }
-dontwarn androidx.**

-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**