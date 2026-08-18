# TVProxy R8/ProGuard rules.
#
# M0: no app-specific keeps needed yet — AndroidX/Kotlin/Media3 consumer rules
# ship with their libraries. Add entries here as features land (M2+ player,
# M1+ Room/Retrofit) and when R8 reports missing classes.

# Keep line numbers for readable crash logs in release builds.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
