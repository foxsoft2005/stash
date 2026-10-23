# R8 rules for release builds (on top of proguard-android-optimize.txt and the
# rules every library ships for itself).

# Keep real class and method names. Stash is open source, so renaming protects
# nothing, and readable names make crash reports from users usable without a
# mapping file. Unused code is still removed, so the APK stays small.
-dontobfuscate

# ML Kit (GenAI Prompt API for Gemini Nano, and image labeling) wires up its
# internal components by class name at runtime. Its bundled rules don't hold up
# under R8 full mode (AGP 9's default): v0.1.0-beta.1 crashed at startup with an
# NPE inside com.google.android.gms.internal.mlkit_genai_prompt, because R8 had
# removed/merged classes ML Kit looks up reflectively. Keep ML Kit as shipped.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_**
