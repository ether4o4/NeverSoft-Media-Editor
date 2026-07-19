package com.neversoft.editor.model

/**
 * Parses a free-form list of time ranges into millisecond spans.
 *
 * Accepts ranges separated by commas, semicolons or new lines, each written as
 * `start-end`. A timecode is `minutes.seconds` (the form people type), and also
 * accepts `mm:ss`, `hh:mm:ss`, `hh.mm.ss`, or a bare seconds count. Examples:
 *   `14.23-14.30, 15.50-16.00, 17.09-17.20`
 *   `1:02:30 - 1:03:00`
 */
object TimecodeCutList {

    data class Range(val startMs: Long, val endMs: Long)
    data class Result(val ranges: List<Range>, val invalid: Int)

    fun parse(text: String, maxMs: Long): Result {
        val tokens = text.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        val ranges = mutableListOf<Range>()
        var invalid = 0
        for (t in tokens) {
            val parts = t.split("-", "–", "—", " to ").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size != 2) { invalid++; continue }
            val s = toMs(parts[0])
            val e = toMs(parts[1])
            if (s == null || e == null) { invalid++; continue }
            val start = s.coerceIn(0, if (maxMs > 0) maxMs else Long.MAX_VALUE)
            val end = e.coerceIn(0, if (maxMs > 0) maxMs else Long.MAX_VALUE)
            if (end <= start) { invalid++; continue }
            ranges.add(Range(start, end))
        }
        return Result(ranges, invalid)
    }

    /** One timecode token -> milliseconds, or null if it can't be read. */
    private fun toMs(s: String): Long? {
        val parts = s.split('.', ':').map { it.trim() }
        if (parts.isEmpty() || parts.any { it.isEmpty() }) return null
        return try {
            val nums = parts.map { it.toDouble() }
            val seconds = when (nums.size) {
                1 -> nums[0]
                2 -> nums[0] * 60 + nums[1]
                3 -> nums[0] * 3600 + nums[1] * 60 + nums[2]
                else -> return null
            }
            (seconds * 1000).toLong()
        } catch (e: NumberFormatException) {
            null
        }
    }
}
