# Keep Hilt generated classes
-keep class **_HiltModules { *; }
-keep class **_HiltComponents$* { *; }
-keep,allowobfuscation,allowshrinking @dagger.hilt.android.lifecycle.HiltViewModel class *

# JGit
-keep class org.eclipse.jgit.** { *; }
-keepclassmembers class org.eclipse.jgit.** { *; }
-dontwarn org.eclipse.jgit.**
-dontwarn org.slf4j.**

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking interface retrofit2.Callback
-keep,allowobfuscation,allowshrinking class * extends retrofit2.Retrofit

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin Serialization
-keepattributes *Annotation*
-keepclassmembers class **$$serializer { *; }
-keep,includedescriptorclasses class com.gitofy.**$$serializer { *; }
-keepclassmembers class com.gitofy.** {
    *** Companion;
}

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Data classes
-keep class com.gitofy.data.remote.dto.** { *; }

# Keep model classes
-keep class com.gitofy.domain.model.** { *; }
