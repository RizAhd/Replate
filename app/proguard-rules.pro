# ── REPLATE Production ProGuard Rules ────────────────────────────────────────

# Keep source file & line numbers for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Firebase ─────────────────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ── Firebase Realtime Database model classes ──────────────────────────────────
-keepclassmembers class com.replate.app.models.** {
    *;
}

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

# ── ZXing (QR codes) ────────────────────────────────────────────────────────
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.** { *; }

# ── Gson ─────────────────────────────────────────────────────────────────────
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── MPAndroidChart ───────────────────────────────────────────────────────────
-keep class com.github.mikephil.charting.** { *; }

# ── CircleImageView ─────────────────────────────────────────────────────────
-keep class de.hdodenhof.circleimageview.** { *; }
