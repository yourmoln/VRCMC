# Android ARM64 SenseVoice native runtime

The Android runtime is built from
[sherpa-onnx v2.0.0](https://github.com/k2-fsa/sherpa-onnx/tree/v2.0.0)
(`8abb7c8f87eae85b2e75f9a4115236c978980add`) with:

- Android NDK `27.2.12479018`
- CMake `3.22.1`
- `arm64-v8a`, API 26
- statically linked ONNX Runtime `1.28.2`
- `MinSizeRel` and `SHERPA_ONNX_LINK_LIBSTDCPP_STATICALLY=ON`
- `SHERPA_ONNX_ENABLE_TTS=OFF`
- `SHERPA_ONNX_ENABLE_SPEAKER_DIARIZATION=OFF`
- `SHERPA_ONNX_ENABLE_C_API=OFF`
- `SHERPA_ONNX_ENABLE_WEBSOCKET=OFF`
- `SHERPA_ONNX_ENABLE_BINARY=OFF`

Replace `offline-recognizer-impl.cc` with
[offline-recognizer-impl-sense-voice-only.cc](android-runtime/offline-recognizer-impl-sense-voice-only.cc),
and change its source entry in `sherpa-onnx/csrc/CMakeLists.txt` accordingly.
In `sherpa-onnx/jni/CMakeLists.txt`, keep only:

```text
common.cc
jni.cc
offline-recognizer.cc
offline-stream.cc
version.cc
wave-reader.cc
```

This removes every JNI binding unrelated to offline recognition and narrows
the C++ recognizer dispatcher to SenseVoice.

Build `sherpa-onnx-jni`, strip it with the NDK's `llvm-strip
--strip-unneeded`, then place it at:

```text
jni/arm64-v8a/libsherpa-onnx-jni.so
```

Use the `AndroidManifest.xml`, `classes.jar`, `R.txt`, and AAR metadata from
the official statically linked AAR. The resulting trimmed AAR is 7,484,372
bytes with SHA-256
`b281330fdd3f4e69b9bb03b837b6e6c6c432266c2fdb0e346902844734921bfb`.

Build the application with:

```powershell
.\gradlew.bat :composeApp:assembleRelease
```

The verified release APK for this revision is 9,694,107 bytes. JNI libraries
are packaged compressed and the app contains only `arm64-v8a`.
