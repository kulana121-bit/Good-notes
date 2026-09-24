# Proguard and R8 optimization rules for production release
# Aggressive R8 Optimization & Dead-Code Elimination
-optimizationpasses 5
-allowaccessmodification
-mergeinterfacesaggressively
-repackageclasses ''

# Strip verbose/debug logging overhead in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# --- Base rules ---
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod
-keep class kotlin.Metadata { *; }

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
-keep class * extends android.app.Application
-keep class * extends android.app.Activity
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.content.ContentProvider

# --- Kotlin Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.** {
    volatile <fields>;
}

# --- Jetpack Compose ---
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }

# --- Material Components ---
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# --- Retrofit ---
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**

# --- OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Room ---
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# --- Moshi Codegen ---
-keep class com.squareup.moshi.** { *; }
-keep class *JsonAdapter { *; }
-keepclassmembers class * {
    @com.squareup.moshi.JsonClass *;
}
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}

# --- Coil ---
-dontwarn coil.**

# --- Firebase & Google Services & Credential Manager ---
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.firebase.**
-dontwarn androidx.credentials.**
-dontwarn com.google.android.libraries.identity.googleid.**
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}

# --- WorkManager ---
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# --- App-specific data models and components (Namespace: com.example) ---
-keep class com.example.data.** { *; }
-keep class com.example.**$Companion { *; }
-keepclassmembers class com.example.data.** { *; }
-keep class com.example.data.local.NotesDatabase { *; }
-keep class com.example.MainActivity { *; }
