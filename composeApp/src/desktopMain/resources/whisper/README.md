# Local speech recognition

The model is downloaded only after the user clicks Download model; it is not bundled in the application. Downloads continue across page and provider changes and when voice input is disabled. Only Cancel download explicitly cancels the task.

- Model: OpenAI Whisper Small, multilingual, MIT license (`LICENSE.whisper`).
- GGML model source: https://huggingface.co/ggerganov/whisper.cpp/tree/5359861c739e955e79d9a303bcbc70fb988958b1
- File: `ggml-small-q5_1.bin`, 190085487 bytes.
- SHA-256: `ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb`.
- Runtime: `io.github.givimad:whisper-jni:1.7.1`, Apache-2.0 (`LICENSE.whisper-jni`), wrapping whisper.cpp (MIT, `LICENSE.whisper-cpp`).
- Windows native binaries require x64 and AVX2. Inference uses the CPU with up to six threads.

The application checks the pinned file size and digest before loading it, including files received from the HF Mirror fallback. No cloud recognition fallback is used.
