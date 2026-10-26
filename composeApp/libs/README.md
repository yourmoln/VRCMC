# Bundled JVM dependencies

The Qwen3-ASR local recognizer uses these prebuilt artifacts from the Apache-2.0
[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) `v2.0.0` release:

- `sherpa-onnx-jvm-2.0.0.jar`
  - Source: https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.0/sherpa-onnx-jvm-2.0.0.jar
  - SHA-256: `ae9dea81af11253c874bed3a7a35e610b583f0350e8380786a1900eff8a831f0`
- `sherpa-onnx-native-lib-win-x64-2.0.0.jar`
  - Source: https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.0/sherpa-onnx-native-lib-win-x64-2.0.0.jar
  - SHA-256: `2c5c1ce813b86a3cb9c3d0ce6a40c86efda00de7d4ee286b59e2fc3892186946`
- `sherpa-onnx-android-arm64-2.0.0.aar`
  - Source AAR: https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.0/sherpa-onnx-static-link-onnxruntime-2.0.0.aar
  - Source SHA-256: `59366e37cf2760b419e895d2872aa1664027fd721f6e4c827cdb0e805088fcbe`
  - Custom SenseVoice-only SHA-256: `b281330fdd3f4e69b9bb03b837b6e6c6c432266c2fdb0e346902844734921bfb`
  - The custom AAR is 7,484,372 bytes and contains only the Java classes plus
    a stripped `arm64-v8a` `libsherpa-onnx-jni.so` that supports SenseVoice.

The native artifact contains `sherpa-onnx-jni.dll` and its matching
`onnxruntime.dll` for Windows x64. Local recognition support remains limited to
Windows x64 and Android ARM64, matching the platform checks in the application.
The Android AAR uses the statically linked ONNX Runtime build, removes all other
ABIs, and keeps only offline-recognition JNI bindings. See
[BUILD_ANDROID_ARM64.md](BUILD_ANDROID_ARM64.md) and the
[SenseVoice-only dispatcher](android-runtime/offline-recognizer-impl-sense-voice-only.cc).
