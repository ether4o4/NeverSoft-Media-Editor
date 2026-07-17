package com.neversoft.editor.engine

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches the on-device stem-separation model once and caches it in app storage.
 * Only the model file crosses the network; all audio processing stays local.
 *
 * Model: Open-Unmix UMX-L vocals (ONNX), ~108 MB, MIT-licensed.
 */
object StemModelManager {

    private const val URL_VOCALS =
        "https://huggingface.co/chinedudave06/demucs-onnx/resolve/main/umxl_vocals.onnx"
    private const val FILE_NAME = "umxl_vocals.onnx"
    private const val MIN_BYTES = 100_000_000L // sanity floor (~108 MB expected)

    fun modelFile(context: Context): File =
        File(File(context.filesDir, "models").apply { mkdirs() }, FILE_NAME)

    fun isReady(context: Context): Boolean {
        val f = modelFile(context)
        return f.exists() && f.length() >= MIN_BYTES
    }

    /** Blocking download (call off the main thread). onProgress gets 0..100. */
    fun download(context: Context, onProgress: (Int) -> Unit): Boolean {
        if (isReady(context)) { onProgress(100); return true }
        val dest = modelFile(context)
        val part = File(dest.parentFile, "$FILE_NAME.part")
        return try {
            val conn = (URL(URL_VOCALS).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 30_000
                readTimeout = 30_000
            }
            conn.connect()
            if (conn.responseCode !in 200..299) return false
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(1 shl 16)
                    var read: Int
                    var done = 0L
                    var lastPct = -1
                    while (input.read(buf).also { read = it } >= 0) {
                        out.write(buf, 0, read)
                        done += read
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                        }
                    }
                }
            }
            if (part.length() < MIN_BYTES) { part.delete(); return false }
            if (dest.exists()) dest.delete()
            part.renameTo(dest)
            onProgress(100)
            true
        } catch (e: Exception) {
            part.delete()
            false
        }
    }
}
