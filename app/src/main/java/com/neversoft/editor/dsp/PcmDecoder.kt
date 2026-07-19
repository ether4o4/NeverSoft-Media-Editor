package com.neversoft.editor.dsp

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder

/** Decoded stereo audio at a fixed 44.1 kHz (what Open-Unmix expects). */
class DecodedAudio(val left: FloatArray, val right: FloatArray) {
    val frames: Int get() = left.size
}

/**
 * Decodes any supported audio Uri to interleaved-then-deinterleaved 44.1 kHz
 * stereo float PCM via MediaCodec, resampling linearly when the source rate
 * differs. Mono sources are duplicated to both channels.
 */
object PcmDecoder {

    private const val TARGET_RATE = 44100

    fun decode(context: Context, uri: Uri): DecodedAudio {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        var trackIndex = -1
        var inFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                trackIndex = i; inFormat = f; break
            }
        }
        require(trackIndex >= 0 && inFormat != null) { "No audio track" }
        extractor.selectTrack(trackIndex)

        val mime = inFormat.getString(MediaFormat.KEY_MIME)!!
        var sampleRate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(inFormat, null, null, 0)
        codec.start()

        val pcm = ByteArrayOutputStream()
        val info = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false

        while (!sawOutputEos) {
            if (!sawInputEos) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val buf = codec.getInputBuffer(inIndex)!!
                    val size = extractor.readSampleData(buf, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEos = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(info, 10_000)
            if (outIndex >= 0) {
                if (info.size > 0) {
                    val buf = codec.getOutputBuffer(outIndex)!!
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    val bytes = ByteArray(info.size)
                    buf.get(bytes)
                    pcm.write(bytes)
                }
                codec.releaseOutputBuffer(outIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEos = true
            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val of = codec.outputFormat
                sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            }
        }

        codec.stop(); codec.release(); extractor.release()

        // 16-bit little-endian PCM -> float, deinterleaved to L/R.
        val raw = pcm.toByteArray()
        val bb = java.nio.ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val totalShorts = bb.remaining()
        val perChannel = if (channels > 0) totalShorts / channels else totalShorts
        var left = FloatArray(perChannel)
        var right = FloatArray(perChannel)
        var idx = 0
        for (n in 0 until perChannel) {
            val l = bb.get(idx).toInt(); idx++
            left[n] = l / 32768f
            if (channels >= 2) {
                val r = bb.get(idx).toInt(); idx += channels - 1
                right[n] = r / 32768f
            } else {
                right[n] = left[n]
            }
        }

        if (sampleRate != TARGET_RATE && perChannel > 1) {
            left = resample(left, sampleRate, TARGET_RATE)
            right = resample(right, sampleRate, TARGET_RATE)
        }
        return DecodedAudio(left, right)
    }

    private fun resample(input: FloatArray, from: Int, to: Int): FloatArray {
        val outLen = (input.size.toLong() * to / from).toInt().coerceAtLeast(1)
        val out = FloatArray(outLen)
        val step = from.toDouble() / to
        var pos = 0.0
        for (i in 0 until outLen) {
            val i0 = pos.toInt()
            val frac = pos - i0
            val a = input[i0.coerceIn(0, input.size - 1)]
            val b = input[(i0 + 1).coerceIn(0, input.size - 1)]
            out[i] = (a + (b - a) * frac).toFloat()
            pos += step
        }
        return out
    }
}
