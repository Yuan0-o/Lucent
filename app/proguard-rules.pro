
-keep class com.lucent.app.nativebridge.LucentNative { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-keep class com.lucent.app.local.LocalLlm { *; }
-keep interface com.lucent.app.local.LocalLlm$PieceCallback { *; }
-keepclassmembers class * implements com.lucent.app.local.LocalLlm$PieceCallback {
    public void onPiece(java.lang.String);
}

-keep class net.sqlcipher.** { *; }
-keep interface net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

-dontwarn kotlinx.coroutines.**

-dontwarn dev.chrisbanes.haze.**

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**
