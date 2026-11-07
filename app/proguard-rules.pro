# Lume release rules (R8). Most libraries ship their own consumer rules (OkHttp, Room, Coil,
# WorkManager, Compose, kotlinx-serialization); these cover what is specific to Lume.

# kotlinx-serialization: keep generated serializers and companion lookups for our models
# (backup payloads, source manifests, cached titles, engine DTOs).
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers @kotlinx.serialization.Serializable class com.lume.app.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.lume.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.lume.app.**$$serializer { *; }

# jsoup: optional regex engine it can use when present.
-dontwarn com.google.re2j.**

# OkHttp optional TLS providers (not bundled).
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# WebView JavaScript bridges (Cloudflare challenge / source login), if any are added.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Readable crash reports.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Archive readers (CBR through junrar, CB7 through commons-compress): optional dependencies that
# aren't bundled. slf4j has no logging backend on Android and stays silent.
-dontwarn org.slf4j.**
-dontwarn org.apache.commons.compress.archivers.**
-dontwarn org.apache.commons.compress.compressors.**
-dontwarn org.apache.commons.compress.harmony.**
-dontwarn org.brotli.dec.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.objectweb.asm.**
-dontwarn java.lang.module.**
-dontwarn javax.naming.**
-dontwarn org.osgi.**
