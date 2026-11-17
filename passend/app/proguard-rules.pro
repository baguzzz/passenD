-keep class com.few.passend.data.local.** { *; }
-keep class com.few.passend.domain.model.** { *; }
-keepclassmembers class * {
    @androidx.room.* <fields>;
}