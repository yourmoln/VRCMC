# JNA resolves native entry points, structures and platform APIs through reflection/JNI.
-keep class com.sun.jna.** { *; }
-keep interface com.vrcmc.app.DwmApi { *; }
-keepclassmembers class * extends com.sun.jna.Structure { <fields>; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-dontwarn com.sun.jna.**

# Ktor discovers the CIO engine and several JVM I/O bridges through ServiceLoader/reflection.
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# JLayer resolves serialized decoder tables relative to JavaLayerUtils's package.
-keep class javazoom.jl.decoder.JavaLayerUtils { *; }

# ONNX Runtime binds these Java classes to its bundled JNI library.
-keep class ai.onnxruntime.** { *; }
# MethodHandle.invokeExact has JVM signature-polymorphic overloads that ProGuard cannot resolve.
-dontwarn ai.onnxruntime.platform.Fp16Conversions

# Persisted enum names are restored with valueOf at runtime.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
