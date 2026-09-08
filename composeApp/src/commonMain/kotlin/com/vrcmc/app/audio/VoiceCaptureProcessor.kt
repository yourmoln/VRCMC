package com.vrcmc.app

import kotlin.math.max
import kotlin.math.sqrt

internal data class VoiceAudioChunk(
    val wav: ByteArray?,
    val overlapSamples: Int,
    val isFinal: Boolean,
)

internal class VoiceCaptureProcessor(
    private val config: VoiceInputConfig,
    private val onSpeechState: (Boolean) -> Unit,
    private val onPartial: (ByteArray) -> Unit,
    private val onFinal: (ByteArray) -> Unit,
    private val onNoSpeech: () -> Unit,
    private val onAutoStop: () -> Unit,
    private val stopOnSilence: Boolean = true,
    private val continuous: Boolean = false,
    private val emitPartials: Boolean = true,
    private val speechDetector: ((ByteArray) -> Boolean)? = null,
    private val onChunk: ((VoiceAudioChunk) -> Unit)? = null,
) {
    private val sampleRate = config.sampleRate
    private val frameDurationMillis = if (speechDetector == null) 30 else 32
    private val frameBytes = (sampleRate * frameDurationMillis / 1_000) * 2
    private val activationFrameCount = max(1, config.vadActivationMillis / frameDurationMillis)
    private val silenceFrameCount = max(1, config.tailSilenceMillis / frameDurationMillis)
    // Keep a longer lead-in so the first syllable is retained while VAD activates.
    private val preRollFrameCount = max(1, 600 / frameDurationMillis)
    private val minimumSpeechSamples = sampleRate * config.partialMinSpeechMillis / 1_000
    private val partialIntervalFrames = max(1, config.partialIntervalMillis / frameDurationMillis)
    private val maxSegmentFrames = max(1, config.maxSegmentSeconds * 1_000 / frameDurationMillis)
    private val overlapFrames = minOf(640 / frameDurationMillis, maxSegmentFrames / 2)
    private var pending = ByteArray(0)
    private val activationWindow = ArrayDeque<Boolean>()
    private val preRoll = ArrayDeque<ByteArray>()
    private val segment = mutableListOf<ByteArray>()
    private var inSpeech = false
    private var finished = false
    private var trailingSilenceFrames = 0
    private var speechSamples = 0
    private var framesSincePartial = 0
    private var noiseFloor = 0.003
    private var hasEmittedChunks = false
    private var chunkOverlapSamples = 0

    fun accept(pcm: ByteArray) {
        if (finished || pcm.isEmpty()) return
        pending += pcm
        var offset = 0
        while (pending.size - offset >= frameBytes && !finished) {
            processFrame(pending.copyOfRange(offset, offset + frameBytes))
            offset += frameBytes
        }
        pending = if (offset == 0) pending else pending.copyOfRange(offset, pending.size)
    }

    fun finish() {
        if (finished) return
        if (inSpeech) finalizeSpeech(autoStop = false) else {
            finished = true
            onNoSpeech()
        }
        finished = true
    }

    private fun processFrame(frame: ByteArray) {
        val metrics = frameMetrics(frame)
        val dynamicThreshold = max(config.vadMinRms, noiseFloor * 2.5)
        // Run the recurrent detector on quiet frames too, so its context never retains old speech.
        val detectedSpeech = speechDetector?.invoke(frame)
        val speechLike = if (detectedSpeech != null) {
            detectedSpeech && metrics.rms >= config.vadMinRms
        } else {
            metrics.rms >= dynamicThreshold &&
                (metrics.zeroCrossingRate in 0.004..0.38 || metrics.rms >= dynamicThreshold * 2.2)
        }

        if (!inSpeech && !speechLike) {
            noiseFloor = noiseFloor * 0.95 + metrics.rms * 0.05
        }
        activationWindow.addLast(speechLike)
        while (activationWindow.size > activationFrameCount) activationWindow.removeFirst()

        if (!inSpeech) {
            preRoll.addLast(frame)
            while (preRoll.size > preRollFrameCount) preRoll.removeFirst()
            val ratio = activationWindow.count { it }.toDouble() / activationWindow.size
            if (activationWindow.size == activationFrameCount && ratio >= config.vadSpeechRatio) {
                inSpeech = true
                segment.addAll(preRoll)
                preRoll.clear()
                speechSamples = activationWindow.count { it } * (frameBytes / 2)
                trailingSilenceFrames = 0
                framesSincePartial = 0
                onSpeechState(true)
            }
            return
        }

        segment += frame
        framesSincePartial++
        if (speechLike) {
            speechSamples += frameBytes / 2
            trailingSilenceFrames = 0
        } else {
            trailingSilenceFrames++
        }

        if (
            emitPartials && speechSamples >= minimumSpeechSamples &&
                framesSincePartial >= partialIntervalFrames &&
                trailingSilenceFrames < silenceFrameCount
        ) {
            framesSincePartial = 0
            onPartial(currentWav())
        }

        if (stopOnSilence || continuous) {
            when {
                trailingSilenceFrames >= silenceFrameCount -> finalizeSpeech(autoStop = true)
                segment.size >= maxSegmentFrames -> {
                    if (onChunk != null && continuous) emitContinuationChunk()
                    else finalizeSpeech(autoStop = true)
                }
            }
        }
    }

    private fun emitContinuationChunk() {
        if (speechSamples >= minimumSpeechSamples || (hasEmittedChunks && speechSamples > 0)) {
            onChunk?.invoke(VoiceAudioChunk(currentWav(), chunkOverlapSamples, isFinal = false))
            hasEmittedChunks = true
        }
        // Keep speech state and enough audio context to recover a word crossing the boundary.
        val overlap = segment.takeLast(overlapFrames)
        segment.clear()
        segment.addAll(overlap)
        chunkOverlapSamples = if (hasEmittedChunks) overlap.size * frameBytes / 2 else 0
        speechSamples = 0
        framesSincePartial = 0
    }

    private fun finalizeSpeech(autoStop: Boolean) {
        if (finished) return
        finished = true
        inSpeech = false
        onSpeechState(false)
        if (onChunk != null) {
            val valid = speechSamples >= minimumSpeechSamples || (hasEmittedChunks && speechSamples > 0)
            if (valid || hasEmittedChunks) {
                // A pause immediately after a duration boundary closes the sentence without
                // resending only overlap/silence to ASR.
                onChunk.invoke(VoiceAudioChunk(if (valid) currentWav() else null, chunkOverlapSamples, isFinal = true))
            } else onNoSpeech()
        } else if (speechSamples >= minimumSpeechSamples && segment.isNotEmpty()) {
            onFinal(currentWav())
        } else {
            onNoSpeech()
        }
        if (continuous && autoStop) {
            finished = false
            segment.clear()
            activationWindow.clear()
            preRoll.clear()
            speechSamples = 0
            trailingSilenceFrames = 0
            framesSincePartial = 0
            hasEmittedChunks = false
            chunkOverlapSamples = 0
        } else if (autoStop) onAutoStop()
    }

    private fun currentWav(): ByteArray {
        val pcmSize = segment.sumOf(ByteArray::size)
        val pcm = ByteArray(pcmSize)
        var offset = 0
        segment.forEach { frame ->
            frame.copyInto(pcm, offset)
            offset += frame.size
        }
        return pcm16ToWav(pcm, sampleRate)
    }
}

private data class FrameMetrics(val rms: Double, val zeroCrossingRate: Double)

private fun frameMetrics(frame: ByteArray): FrameMetrics {
    val samples = frame.size / 2
    if (samples == 0) return FrameMetrics(0.0, 0.0)
    var squareSum = 0.0
    var sum = 0.0
    var crossings = 0
    var previous = 0
    repeat(samples) { index ->
        val offset = index * 2
        val value = ((frame[offset].toInt() and 0xff) or (frame[offset + 1].toInt() shl 8)).toShort().toInt()
        val normalized = value / 32768.0
        squareSum += normalized * normalized
        sum += normalized
        if (index > 0 && (value >= 0) != (previous >= 0)) crossings++
        previous = value
    }
    return FrameMetrics(
        // A DC offset can have a large RMS while being completely inaudible.
        rms = sqrt(max(0.0, squareSum / samples - (sum / samples) * (sum / samples))),
        zeroCrossingRate = crossings.toDouble() / max(1, samples - 1),
    )
}
