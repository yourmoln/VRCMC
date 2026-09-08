# Speech detection fixture

`speech.wav` was synthesized locally with Windows SAPI and Microsoft Zira Desktop at 16 kHz, mono PCM16 for this project's tests. It contains no captured user audio.

Text: “Testing speech detection. Only spoken words should be translated.”

`speech-zh.wav` was synthesized the same way using Microsoft Huihui Desktop. Text: “这是语音识别测试，静音时不识别。”

The service smoke test is explicitly enabled with `VRCMC_LISTENING_API_SMOKE=1`; it uses the locally configured ASR/translation services and sends only this known fixture. Hardware loopback tests require `VRCMC_LOOPBACK_SMOKE=1` and play the English fixture locally. Normal tests use the actual VAD model with fake service functions and never access cloud services.

`VRCMC_LOCAL_ASR_INTEGRATION_TEST=1` enables `LocalWhisperIntegrationTest`: it downloads and verifies Whisper Small into `composeApp/build/local-asr-test`, then recognizes both fixtures locally and checks automatic language detection. It does not read API credentials or upload audio.

`VRCMC_LOCAL_ASR_QUALITY_TEST=1` enables the three-language conversation benchmark in `LocalWhisperQualityTest`. Nine fixed Chinese/Japanese/English sentences use six Edge TTS voices (the text is sent only to Edge TTS to generate test fixtures). Clean, 20 dB SNR noise and automatic-language variants are recognized locally. Cached audio and per-case CER/WER/latency results stay in `composeApp/build/local-asr-test/quality-report.md`. Accuracy gates are at most 20% error per case and 10% mean per language/scenario; latency is recorded separately because it depends on the host CPU and concurrent workload.

For human speech, first run `python scripts/download-local-asr-fixtures.py` from the repository root, then set `VRCMC_LOCAL_ASR_HUMAN_TEST=1`. The script streams the first three test recordings per language from the pinned [Google FLEURS dataset](https://huggingface.co/datasets/google/fleurs), licensed under [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0/). Audio, reference text and attribution stay in the ignored build directory. The test recognizes all nine original recordings and additionally runs VAD, six-second overlapping chunks and sentence assembly for one recording per language. The chunked variants normalize peak volume to 0.8 to meet the application's recording threshold. Results go to `human-quality-report.md`; accuracy gates are 30% per case and 15% per language/mode. These small deterministic samples are regression checks, not a claim about all speakers, accents or environments.
