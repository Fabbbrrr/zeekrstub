# The app is small; minify only exists to keep the APK under the 5 MB ceiling
# that WorkManager's Kotlin/Room tail otherwise breaks.

# JobScheduler binds the service by the ComponentName we hand it; keep the name.
-keep class com.fab.zeekrstub.update.UpdateJobService { *; }

# PendingIntent targets this receiver by class; R8 cannot see that reference.
-keep class com.fab.zeekrstub.update.InstallResultReceiver { *; }

# Keep annotations so WorkManager's generated metadata stays readable.
-keepattributes *Annotation*

# Kotlin stdlib ships annotations that are compile-time only.
-dontwarn org.jetbrains.annotations.**
