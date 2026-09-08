package com.vrcmc.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.text.Normalizer
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue

/** Opt-in quality check. Synthetic fixtures are generated once and reused from build/.
 * Recognition is entirely local; only these fixed, public test sentences are sent to Edge TTS.
 */
class LocalWhisperQualityTest {
    private data class Utterance(val id: String, val language: String, val voice: String, val text: String)
    private val utterances = listOf(
        Utterance("zh-room", "zh", "zh-CN-XiaoxiaoNeural", "你好，我刚刚加入这个房间，可以听到我的声音吗？"),
        Utterance("zh-controller", "zh", "zh-CN-YunxiNeural", "请等我一下，我的手柄没电了，需要重新连接。"),
        Utterance("zh-photo", "zh", "zh-CN-XiaoxiaoNeural", "这个世界很漂亮，我们一起去那边拍张照片吧。"),
        Utterance("ja-voice", "ja", "ja-JP-NanamiNeural", "こんにちは、私の声は聞こえますか。少しゆっくり話してください。"),
        Utterance("ja-controller", "ja", "ja-JP-KeitaNeural", "ちょっと待ってください。コントローラーの電池が切れました。"),
        Utterance("ja-photo", "ja", "ja-JP-NanamiNeural", "このワールドはとてもきれいですね。一緒に写真を撮りましょう。"),
        Utterance("en-room", "en", "en-US-AriaNeural", "Hi, I just joined this room. Can you hear my microphone?"),
        Utterance("en-controller", "en", "en-US-GuyNeural", "Please wait a moment. My controller ran out of battery and I need to reconnect."),
        Utterance("en-photo", "en", "en-US-AriaNeural", "This world looks beautiful. Let's take a picture together over there."),
    )

    @Test
    fun chineseJapaneseAndEnglishConversationAccuracyAndSpeed() = runBlocking {
        assumeTrue(System.getenv("VRCMC_LOCAL_ASR_QUALITY_TEST") == "1")
        assumeTrue(localWhisperSupported())
        val root = Path.of("build", "local-asr-test").toAbsolutePath()
        Files.createDirectories(root)
        val recognizer = DesktopLocalSpeechRecognizer(LocalWhisperModelStore(root.resolve("ggml-small-q5_1.bin")))
        val report = StringBuilder("# 本地 Whisper 中日英识别验证\n\n")
        report.append("模型：Whisper Small Q5_1；CPU 推理，最多 6 线程。样本为固定测试句的 Edge TTS 合成语音，每种语言包含男女声。\n\n")
        report.append("中日文计算去标点后的字错误率（CER），英文计算词错误率（WER）。噪声样本叠加固定种子的约 20 dB 信噪比白噪声；自动检测另取每种语言一条。耗时作为硬件相关的测量结果记录，不作为准确率测试的通过条件。\n\n")
        val errors = mutableListOf<Pair<String, Double>>()
        val failures = mutableListOf<String>()
        try {
            assertTrue(recognizer.downloadModel(), recognizer.status.value.toString())
            assertTrue(recognizer.prepare(), recognizer.status.value.toString())
            for (utterance in utterances) {
                val mp3 = root.resolve("${utterance.id}.mp3")
                if (!Files.isRegularFile(mp3)) Files.write(mp3, synthesizeFixture(utterance))
                val samples = whisperPcmSamples(decodeMp3(Files.readAllBytes(mp3)))
                val variants = buildList {
                    add("clean" to samples)
                    add("noise20db" to withNoise(samples))
                    if (utterance.id in setOf("zh-room", "ja-voice", "en-room")) add("auto" to samples)
                }
                for ((variant, audio) in variants) {
                    val language = if (variant == "auto") "auto" else utterance.language
                    val started = System.nanoTime()
                    val result = assertIs<VoiceTranscriptionResult.Success>(recognizer.transcribe(toWav(audio), language))
                    val elapsed = (System.nanoTime() - started) / 1_000_000_000.0
                    val duration = audio.size / 16_000.0
                    val rate = errorRate(utterance.text, result.text, utterance.language)
                    errors += "${utterance.language}/$variant" to rate
                    val info = "${utterance.id}/$variant: error=${percent(rate)}, audio=${seconds(duration)}s, inference=${seconds(elapsed)}s, RTF=${seconds(elapsed / duration)}"
                    println("$info\nExpected: ${utterance.text}\nActual: ${result.text}")
                    report.append("## ${utterance.id} / $variant\n\n- 原文：${utterance.text}\n- 识别：${result.text}\n- 错误率：${percent(rate)}；音频 ${seconds(duration)} 秒，识别 ${seconds(elapsed)} 秒，耗时/音频时长 ${seconds(elapsed / duration)}。\n\n")
                    Files.writeString(root.resolve("quality-report.md"), report.toString())
                    if (rate > 0.20) failures += "$info: ${result.text}"
                }
            }
            report.append("## 汇总\n\n| 语言 / 场景 | 平均错误率 |\n| --- | --- |\n")
            for ((key, entries) in errors.groupBy { it.first }) {
                val average = entries.map { it.second }.average()
                report.append("| $key | ${percent(average)} |\n")
                if (average > 0.10) failures += "$key average error rate ${percent(average)}"
            }
            assertTrue(failures.isEmpty(), failures.joinToString("\n"))
        } finally {
            recognizer.release()
            Files.writeString(root.resolve("quality-report.md"), report.toString())
        }
    }

    @Test
    fun humanSpeechInThreeLanguagesIncludingProductionAudioChunks() = runBlocking {
        assumeTrue(System.getenv("VRCMC_LOCAL_ASR_HUMAN_TEST") == "1")
        assumeTrue(localWhisperSupported())
        val root = Path.of("build", "local-asr-test").toAbsolutePath()
        val recordings = root.resolve("fleurs")
        check(Files.isRegularFile(recordings.resolve("manifest.json"))) { "Run scripts/download-local-asr-fixtures.py first" }
        val manifest = Json.parseToJsonElement(Files.readString(recordings.resolve("manifest.json"))).jsonArray
        val recognizer = DesktopLocalSpeechRecognizer(LocalWhisperModelStore(root.resolve("ggml-small-q5_1.bin")))
        val report = StringBuilder("# 中日英真人语音验证\n\nFLEURS（google/fleurs，CC-BY-4.0）固定版本的测试集，每种语言按归档顺序取前 3 段；详细来源见 fleurs/manifest.json。完整音频保持原始音量。额外对每种语言第一段运行应用的 VAD、6 秒重叠分块和字幕句子合并；分块测试先统一峰值至 0.8，避免低音量语音低于配置的采集门限。\n\n")
        val rates = mutableListOf<Pair<String, Double>>()
        val failures = mutableListOf<String>()
        val chunkedLanguages = mutableSetOf<String>()
        try {
            assertTrue(recognizer.downloadModel(), recognizer.status.value.toString())
            assertTrue(recognizer.prepare(), recognizer.status.value.toString())
            for (item in manifest) {
                val obj = item.jsonObject
                val language = obj.getValue("language").jsonPrimitive.content
                val file = obj.getValue("file").jsonPrimitive.content
                val expected = obj.getValue("text").jsonPrimitive.content
                val wav = Files.readAllBytes(recordings.resolve(file))
                val modes = if (chunkedLanguages.add(language)) listOf("whole", "chunks") else listOf("whole")
                for (mode in modes) {
                    val started = System.nanoTime()
                    val actual = if (mode == "whole") assertIs<VoiceTranscriptionResult.Success>(recognizer.transcribe(wav, language)).text
                        else recognizeChunks(recognizer, wav, language)
                    val elapsed = (System.nanoTime() - started) / 1_000_000_000.0
                    val rate = errorRate(expected, actual, language)
                    rates += "$language/$mode" to rate
                    report.append("## $file / $mode\n\n- 原文：$expected\n- 识别：$actual\n- 错误率：${percent(rate)}；处理耗时 ${seconds(elapsed)} 秒。\n\n")
                    Files.writeString(root.resolve("human-quality-report.md"), report.toString())
                    println("FLEURS $file/$mode error=${percent(rate)} seconds=${seconds(elapsed)}\n$actual")
                    if (rate > 0.30) failures += "$file/$mode: ${percent(rate)}"
                }
            }
            report.append("## 汇总\n\n| 语言 / 模式 | 平均错误率 |\n| --- | --- |\n")
            for ((key, values) in rates.groupBy { it.first }) {
                val average = values.map { it.second }.average()
                report.append("| $key | ${percent(average)} |\n")
                if (average > 0.15) failures += "$key average ${percent(average)}"
            }
            assertTrue(failures.isEmpty(), failures.joinToString("\n"))
        } finally {
            recognizer.release()
            Files.writeString(root.resolve("human-quality-report.md"), report.toString())
        }
    }

    private suspend fun recognizeChunks(recognizer: LocalSpeechRecognizer, wav: ByteArray, language: String): String {
        val samples = whisperPcmSamples(wav)
        val peak = samples.maxOf { kotlin.math.abs(it) }.coerceAtLeast(0.0001f)
        val converted = toWav(FloatArray(samples.size) { samples[it] * (0.8f / peak) })
        val pcm = converted.copyOfRange(44, converted.size)
        val chunks = mutableListOf<VoiceAudioChunk>()
        SileroSpeechDetector().use { detector ->
            val processor = VoiceCaptureProcessor(
                config = VoiceInputConfig(sampleRate = 16_000), onSpeechState = {}, onPartial = {},
                onFinal = {}, onNoSpeech = {}, onAutoStop = {}, continuous = true, emitPartials = false,
                speechDetector = detector::isSpeech, onChunk = chunks::add,
            )
            processor.accept(pcm)
            processor.accept(ByteArray(32_000))
            processor.finish()
        }
        val assembler = SentenceTextAssembler()
        val completed = mutableListOf<String>()
        for (chunk in chunks) {
            chunk.wav?.let {
                val text = assertIs<VoiceTranscriptionResult.Success>(recognizer.transcribe(it, language)).text
                completed += assembler.append(text, chunk.overlapSamples > 0)
            }
            if (chunk.isFinal) completed += assembler.finish()
        }
        return completed.joinToString(" ")
    }

    private suspend fun synthesizeFixture(utterance: Utterance): ByteArray {
        var lastFailure: Exception? = null
        repeat(3) {
            try { return EdgeTtsService.synthesize(utterance.text, utterance.voice) }
            catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                lastFailure = error
            }
        }
        throw checkNotNull(lastFailure)
    }

    private fun decodeMp3(mp3: ByteArray): ByteArray {
        val stream = Bitstream(ByteArrayInputStream(mp3))
        val decoder = Decoder()
        val pcm = ByteArrayOutputStream()
        var sampleRate = 24_000
        try {
            while (true) {
                val header = stream.readFrame() ?: break
                val samples = decoder.decodeFrame(header, stream) as SampleBuffer
                check(samples.channelCount == 1)
                sampleRate = samples.sampleFrequency
                for (index in 0 until samples.bufferLength) {
                    val value = samples.buffer[index].toInt()
                    pcm.write(value and 255)
                    pcm.write((value shr 8) and 255)
                }
                stream.closeFrame()
            }
        } finally { stream.close() }
        return pcm16ToWav(pcm.toByteArray(), sampleRate)
    }

    private fun withNoise(samples: FloatArray): FloatArray {
        val rms = kotlin.math.sqrt(samples.map { it.toDouble() * it }.average())
        val amplitude = (rms / 10 * kotlin.math.sqrt(3.0)).toFloat()
        val random = Random(42)
        return FloatArray(samples.size) { (samples[it] + (random.nextFloat() * 2 - 1) * amplitude).coerceIn(-1f, 1f) }
    }

    private fun toWav(samples: FloatArray): ByteArray {
        val pcm = ByteArray(samples.size * 2)
        samples.forEachIndexed { index, value ->
            val signed = (value * 32768).roundToInt().coerceIn(-32768, 32767)
            pcm[index * 2] = signed.toByte()
            pcm[index * 2 + 1] = (signed shr 8).toByte()
        }
        return pcm16ToWav(pcm, 16_000)
    }

    private fun errorRate(expected: String, actual: String, language: String): Double {
        fun units(text: String): List<String> {
            val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase()
            return if (language == "en") normalized.replace("’", "'").replace("'", "")
                .split(Regex("[^a-z0-9]+" )).filter(String::isNotBlank)
            else normalized.filter(Char::isLetterOrDigit).map(Char::toString)
        }
        val a = units(expected)
        val b = units(actual)
        var previous = IntArray(b.size + 1) { it }
        a.forEachIndexed { i, value ->
            val current = IntArray(b.size + 1)
            current[0] = i + 1
            b.forEachIndexed { j, other -> current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + if (value == other) 0 else 1) }
            previous = current
        }
        return previous.last().toDouble() / a.size.coerceAtLeast(1)
    }

    private fun percent(value: Double) = "${(value * 10_000).roundToInt() / 100.0}%"
    private fun seconds(value: Double) = ((value * 100).roundToInt() / 100.0).toString()
}
