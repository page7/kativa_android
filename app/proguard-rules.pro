-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.kavita.reader.**$$serializer { *; }
-keepclassmembers class com.kavita.reader.** {
    *** Companion;
}
-keepclasseswithmembers class com.kavita.reader.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.kavita.reader.data.remote.dto.** { *; }

# Retrofit
-keepattributes Signature, Exceptions
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
