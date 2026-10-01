# ── REPLATE Production ProGuard / R8 Rules ───────────────────────────────────

# Keep source file & line numbers so crash reports (Crashlytics) stay readable
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Generic signatures + annotations are read by Gson (TypeToken) and Firebase at runtime
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# ── Firebase ─────────────────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ── Models filled in by reflection (Firebase getValue(), Gson cache) ──────────
# Field and method names must survive shrinking or data silently comes back empty.
-keep class com.replate.app.models.** { *; }
-keep class com.replate.app.map.model.** { *; }

# ── Glide ────────────────────────────────────────────────────────────────────
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# ── PayHere SDK ──────────────────────────────────────────────────────────────
-keep class lk.payhere.** { *; }
-dontwarn lk.payhere.**

# ── ZXing (QR codes) ─────────────────────────────────────────────────────────
-keep class com.google.zxing.** { *; }

# ── Gson ─────────────────────────────────────────────────────────────────────
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── CircleImageView ──────────────────────────────────────────────────────────
-keep class de.hdodenhof.circleimageview.** { *; }
