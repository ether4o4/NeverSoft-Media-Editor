package com.neversoft.editor.dsp

import kotlin.math.cos
import kotlin.math.sin

/**
 * In-place iterative radix-2 Cooley–Tukey FFT. Length must be a power of two
 * (we use 4096). Twiddles are accumulated in double precision for accuracy;
 * the caller supplies double re/im buffers.
 */
object Fft {

    fun transform(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        val n = re.size
        require(n and (n - 1) == 0) { "FFT length must be a power of two" }

        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }

        var len = 2
        while (len <= n) {
            val ang = (if (inverse) 2.0 else -2.0) * Math.PI / len
            val wLenR = cos(ang)
            val wLenI = sin(ang)
            var i = 0
            while (i < n) {
                var wr = 1.0
                var wi = 0.0
                val half = len / 2
                for (k in 0 until half) {
                    val aRe = re[i + k]
                    val aIm = im[i + k]
                    val bRe0 = re[i + k + half]
                    val bIm0 = im[i + k + half]
                    val bRe = bRe0 * wr - bIm0 * wi
                    val bIm = bRe0 * wi + bIm0 * wr
                    re[i + k] = aRe + bRe
                    im[i + k] = aIm + bIm
                    re[i + k + half] = aRe - bRe
                    im[i + k + half] = aIm - bIm
                    val nwr = wr * wLenR - wi * wLenI
                    wi = wr * wLenI + wi * wLenR
                    wr = nwr
                }
                i += len
            }
            len = len shl 1
        }

        if (inverse) {
            val inv = 1.0 / n
            for (i in 0 until n) {
                re[i] = re[i] * inv
                im[i] = im[i] * inv
            }
        }
    }
}
