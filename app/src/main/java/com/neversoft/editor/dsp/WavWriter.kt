package com.neversoft.editor.dsp

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Writes stereo float channels to a 16-bit PCM WAV file (44.1 kHz). */
object WavWriter {

    private const val SAMPLE_RATE = 44100
    private const val CHANNELS = 2
    private const val BITS = 16

    fun write(file: File, left: FloatArray, right: FloatArray) {
        val frames = minOf(left.size, right.size)
        val dataBytes = frames * CHANNELS * (BITS / 8)
        val byteRate = SAMPLE_RATE * CHANNELS * (BITS / 8)

        BufferedOutputStream(FileOutputStream(file)).use { out ->
            fun i32(v: Int) = out.write(
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
            )
            fun i16(v: Int) = out.write(
                ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array()
            )

            out.write("RIFF".toByteArray(Charsets.US_ASCII))
            i32(36 + dataBytes)
            out.write("WAVE".toByteArray(Charsets.US_ASCII))
            out.write("fmt ".toByteArray(Charsets.US_ASCII))
            i32(16)                       // PCM chunk size
            i16(1)                        // audio format = PCM
            i16(CHANNELS)
            i32(SAMPLE_RATE)
            i32(byteRate)
            i16(CHANNELS * (BITS / 8))    // block align
            i16(BITS)
            out.write("data".toByteArray(Charsets.US_ASCII))
            i32(dataBytes)

            val buf = ByteBuffer.allocate(frames * CHANNELS * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (n in 0 until frames) {
                buf.putShort(toPcm16(left[n]))
                buf.putShort(toPcm16(right[n]))
            }
            out.write(buf.array())
        }
    }

    private fun toPcm16(v: Float): Short {
        val c = when {
            v > 1f -> 1f
            v < -1f -> -1f
            else -> v
        }
        return (c * 32767f).toInt().toShort()
    }
}
