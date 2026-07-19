package com.neversoft.editor.dsp

import kotlin.math.cos
import kotlin.math.sqrt

/** One channel's complex spectrogram, frame-major: idx = frame * BINS + bin. */
class Spectro(val frames: Int, val re: FloatArray, val im: FloatArray)

/**
 * STFT / iSTFT tuned to match Open-Unmix's expectations (and torch.stft defaults):
 * n_fft = 4096, hop = 1024, periodic Hann window, center = true with reflect
 * padding, one-sided (2049 bins), no normalisation. iSTFT uses the same window
 * with squared-window overlap normalisation (torch.istft).
 */
object Stft {
    const val N = 4096
    const val HOP = 1024
    const val BINS = N / 2 + 1 // 2049
    private const val PAD = N / 2

    private val window = DoubleArray(N) { 0.5 - 0.5 * cos(2.0 * Math.PI * it / N) }

    private fun reflect(k: Int, len: Int): Int {
        if (len == 1) return 0
        var i = k
        val period = 2 * (len - 1)
        i %= period
        if (i < 0) i += period
        return if (i >= len) period - i else i
    }

    fun frameCount(signalLen: Int): Int {
        val padded = signalLen + 2 * PAD
        return if (padded < N) 0 else 1 + (padded - N) / HOP
    }

    /** Forward STFT of one channel. */
    fun forward(signal: FloatArray): Spectro {
        val len = signal.size
        val frames = frameCount(len)
        val re = FloatArray(frames * BINS)
        val im = FloatArray(frames * BINS)
        val fr = DoubleArray(N)
        val fi = DoubleArray(N)
        for (f in 0 until frames) {
            val start = f * HOP
            for (i in 0 until N) {
                val idx = start + i - PAD
                val s = signal[reflect(idx, len)].toDouble()
                fr[i] = s * window[i]
                fi[i] = 0.0
            }
            Fft.transform(fr, fi, inverse = false)
            val base = f * BINS
            for (b in 0 until BINS) {
                re[base + b] = fr[b].toFloat()
                im[base + b] = fi[b].toFloat()
            }
        }
        return Spectro(frames, re, im)
    }

    fun magnitude(spec: Spectro): FloatArray {
        val out = FloatArray(spec.frames * BINS)
        for (i in out.indices) {
            val r = spec.re[i]
            val m = spec.im[i]
            out[i] = sqrt(r * r + m * m)
        }
        return out
    }

    /** Inverse STFT back to a waveform of [targetLen] samples. */
    fun inverse(re: FloatArray, im: FloatArray, frames: Int, targetLen: Int): FloatArray {
        val paddedLen = (frames - 1) * HOP + N
        val acc = DoubleArray(paddedLen)
        val wsum = DoubleArray(paddedLen)
        val fr = DoubleArray(N)
        val fi = DoubleArray(N)
        for (f in 0 until frames) {
            val base = f * BINS
            // Rebuild the full hermitian spectrum.
            for (b in 0 until BINS) {
                fr[b] = re[base + b].toDouble()
                fi[b] = im[base + b].toDouble()
            }
            for (b in BINS until N) {
                val mirror = N - b
                fr[b] = re[base + mirror].toDouble()
                fi[b] = -im[base + mirror].toDouble()
            }
            Fft.transform(fr, fi, inverse = true)
            val start = f * HOP
            for (i in 0 until N) {
                val w = window[i]
                acc[start + i] += fr[i] * w
                wsum[start + i] += w * w
            }
        }
        val out = FloatArray(targetLen)
        for (t in 0 until targetLen) {
            val p = t + PAD
            if (p < paddedLen) {
                val w = wsum[p]
                out[t] = if (w > 1e-8) (acc[p] / w).toFloat() else 0f
            }
        }
        return out
    }
}
