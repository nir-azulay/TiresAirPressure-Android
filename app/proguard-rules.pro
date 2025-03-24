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

# Keep permission-related classes and methods
-keep class android.content.pm.PackageInfo { *; }
-keep class android.content.pm.PackageManager { *; }
-keep class android.content.Context { *; }
-keep class androidx.core.content.ContextCompat { *; }
-keep class androidx.core.app.ActivityCompat { *; }
-keep class androidx.core.app.ComponentActivity { *; }
-keep class android.app.Activity { *; }

# Keep permission request related classes
-keep class androidx.activity.result.contract.ActivityResultContracts$RequestMultiplePermissions { *; }
-keep class androidx.activity.result.contract.ActivityResultContracts$RequestPermission { *; }

# Keep BLE-related classes
-keep class android.bluetooth.** { *; }
-keep class com.nazulay.tiresairpressuremanagement.data.ble.** { *; }
-keep class com.nazulay.tiresairpressuremanagement.domain.models.** { *; }

# Keep Activity Result API classes
-keep class androidx.activity.result.** { *; }
-keep class androidx.activity.result.contract.** { *; }

# Keep Compose-related classes
-keep class androidx.compose.** { *; }
-keepclassmembers class * extends androidx.compose.runtime.State { *; }
-keepclassmembers class * extends androidx.compose.runtime.MutableState { *; }

# Keep ViewModel classes
-keep class * extends androidx.lifecycle.ViewModel {
    <init>();
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep R8 full mode optimizations for your app's package
-keep class com.nazulay.tiresairpressuremanagement.** { *; }

# Keep permission strings
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Keep all permission-related strings
-keepclassmembers class **.R$string {
    public static <fields>;
}

# Keep manifest permissions
-keepattributes RuntimeVisibleAnnotations
-keep class android.Manifest { *; }
-keep class android.Manifest$permission { *; }
-keep class android.Manifest$permission_group { *; }

# Keep source file names and line numbers for better crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep Android Auto related classes if you're using it
-keep class androidx.car.app.** { *; }

# Keep Nordic Semiconductor BLE library classes if you're using them
-keep class no.nordicsemi.android.ble.** { *; }

# Keep Moshi classes (if you're using Moshi for JSON)
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keepclassmembers class ** {
    @com.squareup.moshi.FromJson *;
    @com.squareup.moshi.ToJson *;
}

# Keep any classes with @Keep annotation
-keep class androidx.annotation.Keep
-keep @androidx.annotation.Keep class * {*;}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <methods>;
}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <fields>;
}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <init>(...);
}

# Keep MainActivity and its methods
-keep class com.nazulay.tiresairpressuremanagement.MainActivity { *; }