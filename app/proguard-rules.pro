# Keep kotlinx serialization metadata
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Hilt
-keep class dagger.hilt.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**