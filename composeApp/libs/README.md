# Bundled JVM dependencies

The Qwen3-ASR local recognizer uses these prebuilt artifacts from the Apache-2.0
[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) `v2.0.0` release:

- `sherpa-onnx-jvm-2.0.0.jar`
  - Source: https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.0/sherpa-onnx-jvm-2.0.0.jar
  - SHA-256: `ae9dea81af11253c874bed3a7a35e610b583f0350e8380786a1900eff8a831f0`
- `sherpa-onnx-native-lib-win-x64-2.0.0.jar`
  - Source: https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.0/sherpa-onnx-native-lib-win-x64-2.0.0.jar
  - SHA-256: `2c5c1ce813b86a3cb9c3d0ce6a40c86efda00de7d4ee286b59e2fc3892186946`

The native artifact contains `sherpa-onnx-jni.dll` and its matching
`onnxruntime.dll` for Windows x64. Local recognition support remains limited to
Windows x64, matching the platform check in the application.
