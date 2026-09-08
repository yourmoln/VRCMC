package com.vrcmc.app

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.nio.LongBuffer

/** Local Silero VAD v6.2.1. One instance owns the recurrent state of one listening session. */
internal class SileroSpeechDetector : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val session = OrtSession.SessionOptions().use { options ->
        options.setInterOpNumThreads(1)
        options.setIntraOpNumThreads(1)
        val model = checkNotNull(javaClass.getResourceAsStream("/vad/silero_vad.onnx")) {
            "Bundled speech detector is missing"
        }.use { it.readBytes() }
        environment.createSession(model, options)
    }
    private val state = FloatArray(256)
    private val context = FloatArray(64)

    @Suppress("UNCHECKED_CAST")
    fun isSpeech(pcm: ByteArray): Boolean {
        require(pcm.size == 1_024) { "Silero VAD requires 512 PCM16 samples at 16 kHz" }
        val samples = FloatArray(576)
        context.copyInto(samples)
        repeat(512) { index ->
            val offset = index * 2
            samples[index + 64] =
                ((pcm[offset].toInt() and 255) or (pcm[offset + 1].toInt() shl 8)).toShort() / 32768f
        }
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(samples), longArrayOf(1, 576)).use { input ->
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(state), longArrayOf(2, 1, 128)).use { history ->
                OnnxTensor.createTensor(environment, LongBuffer.wrap(longArrayOf(16_000)), longArrayOf()).use { rate ->
                    session.run(mapOf("input" to input, "state" to history, "sr" to rate)).use { result ->
                        val probability = (result[0].value as Array<FloatArray>)[0][0]
                        val nextState = result[1].value as Array<Array<FloatArray>>
                        nextState[0][0].copyInto(state, 0)
                        nextState[1][0].copyInto(state, 128)
                        samples.copyInto(context, 0, samples.size - context.size)
                        return probability >= 0.6f
                    }
                }
            }
        }
    }

    override fun close() = session.close()
}
