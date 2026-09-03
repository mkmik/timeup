# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class pub.mkm.timeup.**$$serializer { *; }
-keepclassmembers class pub.mkm.timeup.** { *** Companion; }
-keepclasseswithmembers class pub.mkm.timeup.** { kotlinx.serialization.KSerializer serializer(...); }
