# Keep Moshi generated adapters, annotated JSON models, and Room entities intact under R8
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class * extends com.squareup.moshi.JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-keep class com.example.data.model.** { *; }
-keep class com.example.data.remote.RemoteStoreProfile { *; }
-keep class com.example.data.auth.UserAccount { *; }

# Keep Room database & entities
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }

# Keep Kotlin metadata & coroutines Main dispatcher
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class kotlin.Metadata { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# Strip verbose/debug/info logs from release builds so sensitive diagnostics aren't emitted
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
