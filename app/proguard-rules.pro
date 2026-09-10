# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class com.locke.app.data.local.entity.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
