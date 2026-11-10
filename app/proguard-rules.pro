# R8 / ProGuard rules for the release build.
#
# The app uses no reflection, no serialization library and no native code, so the
# default rules (proguard-android-optimize.txt + the rules shipped inside the
# AndroidX / Compose / coroutines libraries) are enough.
#
# Keep line numbers so that crash stack traces from Play Console stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
