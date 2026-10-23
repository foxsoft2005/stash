# R8 rules for release builds (on top of proguard-android-optimize.txt and the
# rules every library ships for itself).

# Keep real class and method names. Stash is open source, so renaming protects
# nothing, and readable names make crash reports from users usable without a
# mapping file. Unused code is still removed, so the APK stays small.
-dontobfuscate
