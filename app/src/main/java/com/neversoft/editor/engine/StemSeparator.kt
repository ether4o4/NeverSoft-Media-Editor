package com.neversoft.editor.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.neversoft.editor.dsp.PcmDecoder
import com.neversoft.editor.dsp.Spectro
import com.neversoft.editor.dsp.Stft
import com.neversoft.editor.dsp.WavWriter
import java.io.File
import java.nio.FloatBuffer

/**
 * On-device 2-stem separation (vocals + instrumental) with Open-Unmix UMX-L.
 *
 * Pipeline, all local: decode → STFT → run the vocals model on the magnitude
 * spectrogram → soft-mask → the complement is the instrumental → iSTFT → WAV.
 * Processed in ~20 s chunks to bound memory. EXPERIMENTAL: the DSP is tuned to
 * torch.stft but not yet device-verified, so quality may need adjustment.
 */
object StemSeparator {

    private const val CHUNK_SAMPLES = 20 * 44100
    private const val BINS = Stft.BINS

    fun interface Progress {
        fun update(percent: Int, label: String)
    }

    data class Output(val vocals: Uri?, val instrumental: Uri?)

    fun separate(
        context: Context,
        uri: Uri,
        baseTitle: String,
        modelPath: String,
        progress: Progress,
    ): Output {
        progress.update(3, "Decoding audio")
        val audio = PcmDecoder.decode(context, uri)
        val total = audio.frames
        require(total > 0) { "Empty audio" }

        val vocalsL = FloatArray(total)
        val vocalsR = FloatArray(total)
        val instL = FloatArray(total)
        val instR = FloatArray(total)

        val env = OrtEnvironment.getEnvironment()
        val session = env.createSession(modelPath, OrtSession.SessionOptions())
        val inputName = session.inputNames.iterator().next()

        val chunks = (total + CHUNK_SAMPLES - 1) / CHUNK_SAMPLES
        var chunkIndex = 0
        var start = 0
        while (start < total) {
            val end = minOf(start + CHUNK_SAMPLES, total)
            val len = end - start
            val l = audio.left.copyOfRange(start, end)
            val r = audio.right.copyOfRange(start, end)

            val specL = Stft.forward(l)
            val specR = Stft.forward(r)
            val frames = specL.frames
            val magL = Stft.magnitude(specL)
            val magR = Stft.magnitude(specR)

            // Model input: (1, 2, BINS, frames) magnitude, row-major.
            val input = FloatArray(2 * BINS * frames)
            packChannel(input, 0, magL, frames)
            packChannel(input, 1, magR, frames)

            val tensor = OnnxTensor.createTensor(
                env, FloatBuffer.wrap(input), longArrayOf(1L, 2L, BINS.toLong(), frames.toLong())
            )
            val outArr = session.run(mapOf(inputName to tensor)).use { res ->
                val fb = (res.get(0) as OnnxTensor).floatBuffer
                FloatArray(2 * BINS * frames).also { fb.get(it) }
            }
            tensor.close()

            reconstruct(specL, magL, outArr, 0, frames, len).let { (voc, inst) ->
                voc.copyInto(vocalsL, start); inst.copyInto(instL, start)
            }
            reconstruct(specR, magR, outArr, 1, frames, len).let { (voc, inst) ->
                voc.copyInto(vocalsR, start); inst.copyInto(instR, start)
            }

            chunkIndex++
            progress.update(10 + 82 * chunkIndex / chunks, "Separating stems")
            start = end
        }
        session.close()

        progress.update(94, "Saving stems")
        val cache = context.cacheDir
        val vFile = File(cache, "nsme_vocals_${System.currentTimeMillis()}.wav")
        val iFile = File(cache, "nsme_inst_${System.currentTimeMillis()}.wav")
        WavWriter.write(vFile, vocalsL, vocalsR)
        WavWriter.write(iFile, instL, instR)

        val vUri = publish(context, vFile, "$baseTitle (Vocals)")
        val iUri = publish(context, iFile, "$baseTitle (Instrumental)")
        vFile.delete(); iFile.delete()
        progress.update(100, "Done")
        return Output(vUri, iUri)
    }

    private fun packChannel(dst: FloatArray, ch: Int, mag: FloatArray, frames: Int) {
        // dst layout: ((ch * BINS) + bin) * frames + frame ; mag: frame * BINS + bin
        for (f in 0 until frames) {
            for (b in 0 until BINS) {
                dst[(ch * BINS + b) * frames + f] = mag[f * BINS + b]
            }
        }
    }

    /** Returns (vocals, instrumental) time-domain signals for one channel. */
    private fun reconstruct(
        spec: Spectro,
        mag: FloatArray,
        outArr: FloatArray,
        ch: Int,
        frames: Int,
        targetLen: Int,
    ): Pair<FloatArray, FloatArray> {
        val vRe = FloatArray(frames * BINS)
        val vIm = FloatArray(frames * BINS)
        val iRe = FloatArray(frames * BINS)
        val iIm = FloatArray(frames * BINS)
        for (f in 0 until frames) {
            for (b in 0 until BINS) {
                val si = f * BINS + b
                val oi = (ch * BINS + b) * frames + f
                val mixR = spec.re[si]
                val mixI = spec.im[si]
                val mixMag = mag[si]
                val estMag = outArr[oi]
                val mask = if (mixMag > 1e-6f) (estMag / mixMag).coerceIn(0f, 1f) else 0f
                vRe[si] = mixR * mask
                vIm[si] = mixI * mask
                iRe[si] = mixR * (1f - mask)
                iIm[si] = mixI * (1f - mask)
            }
        }
        val voc = Stft.inverse(vRe, vIm, frames, targetLen)
        val inst = Stft.inverse(iRe, iIm, frames, targetLen)
        return voc to inst
    }

    private fun publish(context: Context, source: File, title: String): Uri? {
        val name = title.replace(Regex("[^A-Za-z0-9 _()-]"), "").trim().ifBlank { "stem" } + ".wav"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/x-wav")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/NeverSoft")
                    put(MediaStore.Audio.Media.TITLE, title)
                    put(MediaStore.Audio.Media.IS_MUSIC, 1)
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val collection =
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = resolver.insert(collection, values) ?: return null
                resolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { it.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                val dir = File(
                    context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC),
                    "NeverSoft"
                ).apply { mkdirs() }
                val dest = File(dir, name)
                source.inputStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
                Uri.fromFile(dest)
            }
        } catch (e: Exception) {
            null
        }
    }
}
