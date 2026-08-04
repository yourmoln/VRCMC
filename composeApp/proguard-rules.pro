# JNA resolves native entry points, structures and platform APIs through reflection/JNI.
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.Structure { <fields>; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-dontwarn com.sun.jna.**

# Ktor discovers the CIO engine and several JVM I/O bridges through ServiceLoader/reflection.
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
