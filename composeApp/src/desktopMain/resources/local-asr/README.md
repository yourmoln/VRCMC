# Local speech recognition

Local models are downloaded only after the user clicks Download model; they are not bundled in the application. Downloads continue across page and provider changes and when voice input is disabled. Only Cancel download explicitly cancels the task. Downloaded model files can be removed from the same settings section. Local inference is available on Windows x64.

- Model: SenseVoice Small INT8, Chinese, Cantonese, English, Japanese, and Korean, subject to the [FunASR model license](https://github.com/modelscope/FunASR#license).
- Model source: https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/tree/2365baeacb507f821a0c8120fcee3d484dba7a07
- `model.int8.onnx`: 239233841 bytes, SHA-256 `c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51`.
- `tokens.txt`: 315894 bytes, SHA-256 `f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc`.
- Model: Fun-ASR-Nano 2512 INT8, with stronger dialect, accent, far-field, and noisy-speech recognition, subject to the FunASR model license.
- Model source: https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-funasr-nano-int8-2025-12-17/tree/ebb95fbda2a034d9a8f3b2d2cba32c104b60bed3
- `fun-asr-nano-2512/model.int8.onnx`: 263531902 bytes, SHA-256 `9dc6e72aa8bc6f5966cf2857a0ce3a425b1d72e91500e147d66329f407c017a1`.
- `fun-asr-nano-2512/tokens.txt`: 939815 bytes, SHA-256 `2db4bb25d046e8849e336c9465e248e1694914d996c58c71bdeca18cfb722992`.
- Model: Qwen3-ASR 0.6B INT8, supporting multilingual speech and Chinese dialects, subject to the [Qwen3-ASR model license](https://huggingface.co/Qwen/Qwen3-ASR-0.6B).
- Model source: https://huggingface.co/csukuangfj2/sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25/tree/68818b2313fe77bd06f6a7c5068ff3ef59d02b8a
- `qwen3-asr-0.6b/encoder.int8.onnx`: 182491662 bytes, SHA-256 `60748d3e6744a57c9c91e1b17424a6c2990567e8adceb0783940c03ed98fa9d9`.
- `qwen3-asr-0.6b/decoder.int8.onnx`: 755914231 bytes, SHA-256 `4f6885be5959ae26af3089d38ee7972c5fafbeeb1cf8d5e76eab6d8b61ca5771`.
- `qwen3-asr-0.6b/conv_frontend.onnx`: 44148281 bytes, SHA-256 `d22dc4423e0940e49884e903d2ea2f7e5567c14fc1aed97e4e26d6b8f208ef9e`.
- `qwen3-asr-0.6b/tokenizer/merges.txt`: 1671853 bytes, SHA-256 `8831e4f1a044471340f7c0a83d7bd71306a5b867e95fd870f74d0c5308a904d5`.
- `qwen3-asr-0.6b/tokenizer/tokenizer_config.json`: 12487 bytes, SHA-256 `4942d005604266809309cabc9f4e9cb89ce855d59b14681fdc0e1cc62ea26c4c`.
- `qwen3-asr-0.6b/tokenizer/vocab.json`: 2776833 bytes, SHA-256 `ca10d7e9fb3ed18575dd1e277a2579c16d108e32f27439684afa0e10b1440910`.
- Runtime: `com.microsoft.onnxruntime:onnxruntime:1.28.0` runs SenseVoice/Fun-ASR feature extraction, LFR, normalization, and CTC decoding. Qwen3-ASR uses the bundled Apache-2.0 `sherpa-onnx` 2.0.0 JVM API and Windows x64 JNI runtime with ONNX Runtime 1.28.2. Both runtimes use the same ONNX Runtime API level so the models can be switched in either order without loading incompatible native libraries.
- Bundled JVM runtime: `sherpa-onnx-jvm-2.0.0.jar`, SHA-256 `ae9dea81af11253c874bed3a7a35e610b583f0350e8380786a1900eff8a831f0`.
- Bundled Windows x64 native runtime: `sherpa-onnx-native-lib-win-x64-2.0.0.jar`, SHA-256 `2c5c1ce813b86a3cb9c3d0ce6a40c86efda00de7d4ee286b59e2fc3892186946`.

The application checks every file size and SHA-256 digest before loading the model, including files received from the HF Mirror fallback. Deleting a model closes the active engine before removing all of its files. No cloud recognition fallback is used.
