# Speech detection fixture

`speech.wav` was synthesized locally with Windows SAPI and Microsoft Zira Desktop at 16 kHz, mono PCM16 for this project's tests. It contains no captured user audio.

Text: “Testing speech detection. Only spoken words should be translated.”

`speech-zh.wav` was synthesized the same way using Microsoft Huihui Desktop. Text: “这是语音识别测试，静音时不识别。”

The service smoke test is explicitly enabled with `VRCMC_LISTENING_API_SMOKE=1`; it uses the locally configured ASR/translation services and sends only this known fixture. Hardware loopback tests require `VRCMC_LOOPBACK_SMOKE=1` and play the English fixture locally. Normal tests use the actual VAD model with fake service functions and never access cloud services.

`VRCMC_SENSE_VOICE_INTEGRATION_TEST=1` enables the cached SenseVoice INT8 integration test. It verifies the Chinese fixture and does not read API credentials or upload audio.

`VRCMC_FUN_ASR_INTEGRATION_TEST=1` enables the cached Fun-ASR-Nano INT8 integration test against its official Chinese sample.
