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
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep Stripe SDK classes
-keep class com.stripe.** { *; }
-dontwarn com.stripe.**

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.mllinman.languagetranslator.**$$serializer { *; }
-keepclassmembers class com.mllinman.languagetranslator.** {
    *** Companion;
}
-keepclasseswithmembers class com.mllinman.languagetranslator.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Keep translation models and data classes
-keep class com.mllinman.languagetranslator.models.** { *; }
-keep class com.mllinman.languagetranslator.api.** { *; }

# Keep subscription manager for reflection
-keep class com.mllinman.languagetranslator.SubscriptionManager { *; }
-keep class com.mllinman.languagetranslator.SubscriptionManager$* { *; }