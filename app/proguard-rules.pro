-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.kativa.reader.**$$serializer { *; }
-keepclassmembers class com.kativa.reader.** {
    *** Companion;
}
-keepclasseswithmembers class com.kativa.reader.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.kativa.reader.data.remote.dto.** { *; }

# Retrofit
-keepattributes Signature, Exceptions
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
