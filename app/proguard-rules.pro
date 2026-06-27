# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep @Serializable types used for type-safe Navigation (Screen.kt). Without this,
# nav-arg routing breaks at runtime in release builds.
-keep,allowobfuscation,allowshrinking @kotlinx.serialization.Serializable class **
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep Room entities
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }

# Keep data classes
-keep class com.pdfmaster.app.domain.model.** { *; }
-keep class com.pdfmaster.app.data.local.entity.** { *; }

# Compose ships its own consumer ProGuard rules. The previous over-broad
# `-keep class androidx.compose.** { *; }` defeated most of R8's Compose
# optimizations (lambda grouping, sourceInformation stripping, ComposerImpl
# devirtualization). Removed deliberately — do NOT re-add a blanket keep.

# --- Play Billing: uses Parcelable/reflection internally ---
-keep class com.android.billingclient.api.** { *; }

# --- PDFBox-Android + BouncyCastle (reflection for fonts/encoding + crypto) ---
-keep class com.tom_roush.pdfbox.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.bouncycastle.**

# --- MuPDF (JNI: native method names MUST be preserved) ---
-keep class com.artifex.mupdf.fitz.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn com.artifex.mupdf.fitz.**

# --- ML Kit (document scanner / text recognition) ---
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
