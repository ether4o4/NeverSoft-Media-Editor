package com.neversoft.editor.engine

import android.content.Context
import android.net.Uri
import com.neversoft.editor.media.MediaUtils
import com.neversoft.editor.model.AudioTrack
import com.neversoft.editor.model.Clip
import com.neversoft.editor.model.Filter
import com.neversoft.editor.model.MediaType
import com.neversoft.editor.model.Project
import com.neversoft.editor.model.TextClip
import com.neversoft.editor.model.TextPosition
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Autosaves the current edit to disk (JSON) so a crash or the app being killed
 * never loses work. Media is referenced by Uri — if a picked Uri's grant has
 * lapsed by the time you restore, that clip may need re-picking, but the edit
 * structure (trims, order, captions, filters…) is preserved.
 */
object ProjectStore {

    private fun file(context: Context) = File(context.filesDir, "autosave.json")

    fun save(context: Context, project: Project) {
        try {
            val f = file(context)
            if (project.isEmpty) { f.delete(); return }
            f.writeText(toJson(project).toString())
        } catch (e: Exception) {
            // Autosave is best-effort; never let it interfere with editing.
        }
    }

    fun load(context: Context): Project? {
        return try {
            val f = file(context)
            if (!f.exists()) return null
            fromJson(JSONObject(f.readText()))
        } catch (e: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    fun hasSaved(context: Context): Boolean = file(context).exists()

    // ---- serialisation ----------------------------------------------------

    private fun toJson(p: Project): JSONObject {
        val clips = JSONArray()
        p.clips.forEach { c ->
            val texts = JSONArray()
            c.texts.forEach { t ->
                texts.put(
                    JSONObject()
                        .put("id", t.id)
                        .put("text", t.text)
                        .put("position", t.position.name)
                        .put("sizeSp", t.sizeSp)
                        .put("color", t.color)
                )
            }
            clips.put(
                JSONObject()
                    .put("id", c.id)
                    .put("uri", c.uri.toString())
                    .put("type", c.type.name)
                    .put("sourceDurationMs", c.sourceDurationMs)
                    .put("sourceWidth", c.sourceWidth)
                    .put("sourceHeight", c.sourceHeight)
                    .put("trimStartMs", c.trimStartMs)
                    .put("trimEndMs", c.trimEndMs)
                    .put("imageDurationMs", c.imageDurationMs)
                    .put("speed", c.speed.toDouble())
                    .put("filter", c.filter.name)
                    .put("muted", c.muted)
                    .put("autoEnhance", c.autoEnhance)
                    .put("rotationDeg", c.rotationDeg)
                    .put("texts", texts)
            )
        }
        val obj = JSONObject()
            .put("outputWidth", p.outputWidth)
            .put("outputHeight", p.outputHeight)
            .put("clips", clips)
        p.music?.let { m ->
            obj.put(
                "music",
                JSONObject()
                    .put("uri", m.uri.toString())
                    .put("title", m.title)
                    .put("volume", m.volume.toDouble())
            )
        }
        return obj
    }

    private fun fromJson(obj: JSONObject): Project {
        val clipsArr = obj.optJSONArray("clips") ?: JSONArray()
        val clips = ArrayList<Clip>(clipsArr.length())
        var maxId = 0L
        for (i in 0 until clipsArr.length()) {
            val c = clipsArr.getJSONObject(i)
            val textsArr = c.optJSONArray("texts") ?: JSONArray()
            val texts = ArrayList<TextClip>(textsArr.length())
            for (j in 0 until textsArr.length()) {
                val t = textsArr.getJSONObject(j)
                val tid = t.getLong("id")
                maxId = maxOf(maxId, tid)
                texts.add(
                    TextClip(
                        id = tid,
                        text = t.getString("text"),
                        position = TextPosition.valueOf(t.optString("position", "BOTTOM")),
                        sizeSp = t.optInt("sizeSp", 28),
                        color = t.optLong("color", 0xFFFFFFFF),
                    )
                )
            }
            val id = c.getLong("id")
            maxId = maxOf(maxId, id)
            clips.add(
                Clip(
                    id = id,
                    uri = Uri.parse(c.getString("uri")),
                    type = MediaType.valueOf(c.optString("type", "VIDEO")),
                    sourceDurationMs = c.optLong("sourceDurationMs", 0),
                    sourceWidth = c.optInt("sourceWidth", 0),
                    sourceHeight = c.optInt("sourceHeight", 0),
                    trimStartMs = c.optLong("trimStartMs", 0),
                    trimEndMs = c.optLong("trimEndMs", 0),
                    imageDurationMs = c.optLong("imageDurationMs", 3000),
                    speed = c.optDouble("speed", 1.0).toFloat(),
                    filter = Filter.valueOf(c.optString("filter", "NONE")),
                    muted = c.optBoolean("muted", false),
                    autoEnhance = c.optBoolean("autoEnhance", false),
                    rotationDeg = c.optInt("rotationDeg", 0),
                    texts = texts,
                )
            )
        }
        val music = obj.optJSONObject("music")?.let { m ->
            AudioTrack(
                uri = Uri.parse(m.getString("uri")),
                title = m.optString("title", "Audio"),
                volume = m.optDouble("volume", 1.0).toFloat(),
            )
        }
        // Keep future ids from colliding with restored ones.
        MediaUtils.ensureIdAbove(maxId)
        return Project(
            clips = clips,
            music = music,
            outputWidth = obj.optInt("outputWidth", 720),
            outputHeight = obj.optInt("outputHeight", 1280),
        )
    }
}
