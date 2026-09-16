# Room generates implementations reflectively-adjacent code; keep entities intact.
-keep class com.pocketledger.data.entity.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.pocketledger.**$$serializer { *; }
-keepclassmembers class com.pocketledger.** {
    *** Companion;
}
-keepclasseswithmembers class com.pocketledger.** {
    kotlinx.serialization.KSerializer serializer(...);
}
