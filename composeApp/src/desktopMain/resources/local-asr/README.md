# Local speech recognition

Local models are downloaded only after the user clicks Download model; they are not bundled in the application. Downloads continue across page and provider changes and when voice input is disabled. Only Cancel download explicitly cancels the task. Local inference is available on Windows x64.

- Model: SenseVoice Small INT8, Chinese, Cantonese, English, Japanese, and Korean, subject to the [FunASR model license](https://github.com/modelscope/FunASR#license).
- Model source: https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/tree/2365baeacb507f821a0c8120fcee3d484dba7a07
- `model.int8.onnx`: 239233841 bytes, SHA-256 `c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51`.
- `tokens.txt`: 315894 bytes, SHA-256 `f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc`.
- Model: Fun-ASR-Nano 2512 INT8, with stronger dialect, accent, far-field, and noisy-speech recognition, subject to the FunASR model license.
- Model source: https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-funasr-nano-int8-2025-12-17/tree/ebb95fbda2a034d9a8f3b2d2cba32c104b60bed3
- `fun-asr-nano-2512/model.int8.onnx`: 263531902 bytes, SHA-256 `9dc6e72aa8bc6f5966cf2857a0ce3a425b1d72e91500e147d66329f407c017a1`.
- `fun-asr-nano-2512/tokens.txt`: 939815 bytes, SHA-256 `2db4bb25d046e8849e336c9465e248e1694914d996c58c71bdeca18cfb722992`.
- Runtime: `com.microsoft.onnxruntime:onnxruntime:1.20.0` on Desktop. Feature extraction, LFR, normalization, and CTC decoding run locally.

The application checks both file sizes and SHA-256 digests before loading the model, including files received from the HF Mirror fallback. No cloud recognition fallback is used.
