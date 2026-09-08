# Silero VAD

Bundled local speech detection model from Silero VAD v6.2.1 (MIT).

- Source: https://github.com/snakers4/silero-vad/tree/v6.2.1
- Model: `src/silero_vad/data/silero_vad.onnx`
- SHA-256: `1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3`
- License: `LICENSE.silero-vad` alongside this file.

The app runs the model locally using the CPU ONNX Runtime. It requires no model download or additional credentials at runtime. The Windows listener feeds 512 mono PCM samples at 16 kHz with 64 samples of context and recurrent state shaped `[2, 1, 128]`, following the upstream ONNX wrapper.
