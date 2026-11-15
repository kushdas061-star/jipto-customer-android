# JIPTO Customer Android App ProGuard / R8 Rules
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# Keep data models serialized/deserialized with Firebase Firestore
-keep class com.jipto.customer.data.model.** { *; }

# Google Play Services & Maps
-keep class com.google.android.gms.maps.** { *; }
-dontwarn com.google.android.gms.**

# Razorpay Checkout SDK
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
-keep class com.razorpay.** { *; }
-dontwarn com.razorpay.**
