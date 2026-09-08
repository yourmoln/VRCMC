package com.vrcmc.app

import kotlin.test.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

class ReadAloudTest {
    @Test
    fun newAndExistingSettingsDefaultToDisabledOriginal() {
        for (json in listOf("", "{}", "{\"translate\":true}", "{\"readAloud\":false}")) {
            val settings = storedTranslationSettingsFromJson(json)
            assertFalse(settings.readAloud.enabled)
            assertEquals(ReadAloudSource.ORIGINAL, settings.readAloud.source)
        }
    }

    @Test
    fun settingsRoundTripAndUnknownValuesPreserveOtherPreferences() {
        val config = ReadAloudConfig(true, ReadAloudSource.TRANSLATION, "ja-JP-NanamiNeural", "Headphones|USB")
        val settings = StoredTranslationSettings(readAloud = config, translate = true)
        assertEquals(config, storedTranslationSettingsFromJson(settings.toJson()).readAloud)
        val restored = storedTranslationSettingsFromJson("""{"translate":true,"readAloud":{"source":"future","voice":""}}""")
        assertTrue(restored.translate)
        assertEquals(ReadAloudConfig(), restored.readAloud)
    }

    @Test
    fun selectsOriginalTranslationsInDisplayOrderOrExactOutput() {
        val translated = mapOf("English" to "Hello", "日本語" to "こんにちは")
        val order = listOf("日本語", originalOutputKey, "English")
        val outgoing = "こんにちは(你好)Hello"
        assertEquals("你好", readAloudText(ReadAloudSource.ORIGINAL, "你好", translated, order, outgoing))
        assertEquals("こんにちは\nHello", readAloudText(ReadAloudSource.TRANSLATION, "你好", translated, order, outgoing))
        assertEquals(outgoing, readAloudText(ReadAloudSource.OUTGOING, "你好", translated, order, outgoing))
        assertEquals("", readAloudText(ReadAloudSource.TRANSLATION, "你好", emptyMap(), order, "你好"))
    }

    @Test
    fun disabledSpeechDoesNotContactService() = runBlocking {
        var requests = 0
        val controller = ReadAloudController(this, ReadAloudConfig(), {},
            synthesize = { _, _ -> requests++; byteArrayOf(1) }, player = FakePlayer())
        try {
            controller.enqueue("hello")
            yield()
            assertEquals(0, requests)
        } finally { controller.stop() }
    }

    @Test
    fun playsInOrderWithoutOverlappingRequests() = runBlocking {
        withTimeout(2_000) {
            val started = Channel<String>(Channel.UNLIMITED)
            val finishFirst = CompletableDeferred<Unit>()
            val synthesized = mutableListOf<String>()
            val player = FakePlayer { audio ->
                val text = audio.decodeToString()
                started.send(text)
                if (text == "one") finishFirst.await()
            }
            val controller = ReadAloudController(this, ReadAloudConfig(enabled = true), {},
                synthesize = { text, _ -> synthesized.add(text); text.encodeToByteArray() }, player = player)
            try {
                controller.enqueue("one")
                controller.enqueue("two")
                assertEquals("one", started.receive())
                assertEquals(listOf("one"), synthesized)
                finishFirst.complete(Unit)
                assertEquals("two", started.receive())
                assertEquals(listOf("one", "two"), synthesized)
            } finally { controller.stop() }
        }
    }

    @Test
    fun disablingCancelsSynthesisDropsQueueAndRejectsStaleSettings() = runBlocking {
        withTimeout(2_000) {
            val started = CompletableDeferred<Unit>()
            val finishSynthesis = CompletableDeferred<Unit>()
            val played = Channel<String>(Channel.UNLIMITED)
            val enabled = ReadAloudConfig(enabled = true)
            val controller = ReadAloudController(this, enabled, {},
                synthesize = { text, _ ->
                    if (text == "old") { started.complete(Unit); finishSynthesis.await() }
                    text.encodeToByteArray()
                }, player = FakePlayer { played.send(it.decodeToString()) })
            try {
                controller.enqueue("old")
                controller.enqueue("queued")
                started.await()
                controller.configure(enabled.copy(enabled = false))
                finishSynthesis.complete(Unit)
                yield()
                assertTrue(played.tryReceive().isFailure)
                val newConfig = enabled.copy(voice = "ja-JP-NanamiNeural")
                controller.configure(newConfig)
                controller.enqueue("stale", enabled)
                controller.enqueue("new", newConfig)
                assertEquals("new", played.receive())
                assertTrue(played.tryReceive().isFailure)
            } finally { controller.stop() }
        }
    }

    @Test
    fun failureDoesNotBlockFollowingSpeechOrLogMessageText() = runBlocking {
        withTimeout(2_000) {
            val errors = mutableListOf<String>()
            val played = CompletableDeferred<Unit>()
            val controller = ReadAloudController(this, ReadAloudConfig(enabled = true), errors::add,
                synthesize = { text, _ ->
                    if (text == "private text") error("private text")
                    byteArrayOf(1)
                }, player = FakePlayer { played.complete(Unit) })
            try {
                controller.enqueue("private text")
                controller.enqueue("next")
                played.await()
                assertEquals(1, errors.size)
                assertFalse(errors.single().contains("private text"))
                assertTrue(controller.failed)
            } finally { controller.stop() }
        }
    }

    @Test
    fun disablingDuringPlaybackReleasesPlayerAndDropsQueuedSpeech() = runBlocking {
        withTimeout(2_000) {
            val started = CompletableDeferred<Unit>()
            val released = CompletableDeferred<Unit>()
            val played = mutableListOf<String>()
            var stopped = false
            val enabled = ReadAloudConfig(enabled = true)
            val player = object : SpeechAudioPlayer {
                override suspend fun play(mp3: ByteArray, outputDeviceId: String) {
                    played.add(mp3.decodeToString())
                    started.complete(Unit)
                    try { awaitCancellation() } finally { released.complete(Unit) }
                }
                override fun stop() { stopped = true }
            }
            val controller = ReadAloudController(this, enabled, {},
                synthesize = { text, _ -> text.encodeToByteArray() }, player = player)
            try {
                controller.enqueue("first")
                controller.enqueue("second")
                started.await()
                controller.configure(enabled.copy(enabled = false))
                released.await()
                assertTrue(stopped)
                assertFalse(controller.busy)
                assertFalse(controller.failed)
                assertEquals(listOf("first"), played)
            } finally { controller.stop() }
        }
    }

    @Test
    fun timedOutSpeechDoesNotLeaveADeadQueue() = runBlocking {
        withTimeout(2_000) {
            val played = CompletableDeferred<Unit>()
            val errors = mutableListOf<String>()
            val controller = ReadAloudController(this, ReadAloudConfig(enabled = true), errors::add,
                synthesize = { text, _ ->
                    if (text == "timeout") withTimeout(1) { awaitCancellation() }
                    byteArrayOf(1)
                }, player = FakePlayer { played.complete(Unit) })
            try {
                controller.enqueue("timeout")
                controller.enqueue("next")
                played.await()
                assertEquals(listOf("Edge TTS: synthesis timed out"), errors)
            } finally { controller.stop() }
        }
    }

    @Test
    fun failuresIdentifySynthesisAndPlaybackWithoutLoggingPrivateDetails() = runBlocking {
        withTimeout(2_000) {
            val errors = mutableListOf<String>()
            val played = CompletableDeferred<Unit>()
            val privateDetails = "private speech https://example.invalid/?TrustedClientToken=secret"
            val controller = ReadAloudController(this, ReadAloudConfig(enabled = true), errors::add,
                synthesize = { text, _ ->
                    if (text == "synthesis failure") {
                        throw IllegalStateException(privateDetails, UnsupportedOperationException(privateDetails))
                    }
                    text.encodeToByteArray()
                }, player = FakePlayer { audio ->
                    if (audio.decodeToString() == "playback failure") throw SpeechAudioPlaybackException(1, -1004)
                    played.complete(Unit)
                })
            try {
                controller.enqueue("synthesis failure")
                controller.enqueue("playback failure")
                controller.enqueue("next")
                played.await()
                assertEquals(listOf(
                    "Edge TTS: synthesis failed: IllegalStateException <- UnsupportedOperationException",
                    "Edge TTS: playback failed: SpeechAudioPlaybackException (1/-1004)",
                ), errors)
                assertTrue(errors.none { it.contains("private speech") || it.contains("TrustedClientToken") || it.contains("secret") })
            } finally { controller.stop() }
        }
    }

    @Test
    fun playbackTimeoutIsReportedSeparatelyAndDoesNotBlockFollowingSpeech() = runBlocking {
        withTimeout(2_000) {
            val errors = mutableListOf<String>()
            val played = CompletableDeferred<Unit>()
            val controller = ReadAloudController(this, ReadAloudConfig(enabled = true), errors::add,
                synthesize = { text, _ -> text.encodeToByteArray() },
                player = FakePlayer { audio ->
                    if (audio.decodeToString() == "timeout") withTimeout(1) { awaitCancellation() }
                    played.complete(Unit)
                })
            try {
                controller.enqueue("timeout")
                controller.enqueue("next")
                played.await()
                assertEquals(listOf("Edge TTS: playback timed out"), errors)
            } finally { controller.stop() }
        }
    }

    private class FakePlayer(private val onPlay: suspend (ByteArray) -> Unit = {}) : SpeechAudioPlayer {
        override suspend fun play(mp3: ByteArray, outputDeviceId: String) = onPlay(mp3)
        override fun stop() = Unit
    }

    @Test
    fun previewUsesCandidateVoiceWithoutChangingSavedVoiceOrOutputDevice() = runBlocking {
        withTimeout(2_000) {
            val synthesized = mutableListOf<Pair<String, String>>()
            val outputs = Channel<String>(Channel.UNLIMITED)
            val config = ReadAloudConfig(enabled = true, outputDeviceId = "headphones")
            val controller = ReadAloudController(this, config, {},
                synthesize = { text, voice -> synthesized.add(text to voice); byteArrayOf(1) },
                player = object : SpeechAudioPlayer {
                    override suspend fun play(mp3: ByteArray, outputDeviceId: String) { outputs.send(outputDeviceId) }
                    override fun stop() = Unit
                })
            try {
                controller.preview("preview", "ja-JP-NanamiNeural")
                assertEquals("headphones", outputs.receive())
                controller.enqueue("message", config)
                assertEquals("headphones", outputs.receive())
                assertEquals(listOf("preview" to "ja-JP-NanamiNeural", "message" to config.voice), synthesized)
            } finally { controller.stop() }
        }
    }

    @Test
    fun closingVoicePickerAfterPreviewDoesNotCancelRegularSpeech() = runBlocking<Unit> {
        withTimeout(2_000) {
            val started = Channel<String>(Channel.UNLIMITED)
            val releaseMessage = CompletableDeferred<Unit>()
            val controller = ReadAloudController(this, ReadAloudConfig(enabled = true), {},
                synthesize = { text, _ -> text.encodeToByteArray() },
                player = FakePlayer {
                    val text = it.decodeToString()
                    started.send(text)
                    if (text == "message") releaseMessage.await()
                })
            try {
                controller.preview("preview", "ja-JP-NanamiNeural")
                assertEquals("preview", started.receive())
                controller.enqueue("message")
                assertEquals("message", started.receive())
                assertNull(controller.previewVoice)
                controller.stopPreview()
                assertTrue(controller.busy)
                releaseMessage.complete(Unit)
            } finally { controller.stop() }
        }
    }
}
