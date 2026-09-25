package com.robcloud.bloodpressure.backup

import com.robcloud.bloodpressure.data.Arm
import com.robcloud.bloodpressure.data.Note
import com.robcloud.bloodpressure.data.NoteType
import com.robcloud.bloodpressure.data.Reading
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * [skippedRows] counts non-blank data rows that produced neither a reading nor a note (wrong
 * column count, unparseable value, unknown record type). Sync must not overwrite a file whose
 * rows it couldn't read, or those rows are lost.
 */
data class ParsedCsv(val readings: List<Reading>, val notes: List<Note>, val skippedRows: Int = 0) {
    companion object {
        val EMPTY = ParsedCsv(emptyList(), emptyList())
    }
}

/**
 * Single CSV backs up both readings and notes, distinguished by a leading record_type
 * column. Reading/note-specific columns are left blank on rows that don't use them.
 * Note details is free text, so fields are RFC4180-quoted (quotes doubled, commas/
 * newlines wrapped) rather than relying on the old "no field can contain a comma" rule.
 */
object Csv {
    private val LEGACY_HEADER =
        listOf("id", "taken_at", "systolic_mmhg", "diastolic_mmhg", "heart_rate_bpm", "arm")
    /** Written by 2.4–2.6, before the irregular-heartbeat column. Still read, never written. */
    private val HEADER_V2 = listOf(
        "record_type", "id", "date", "systolic_mmhg", "diastolic_mmhg", "heart_rate_bpm",
        "arm", "note_type", "note_details"
    )
    private val HEADER = HEADER_V2 + "irregular_heartbeat"

    fun write(readings: List<Reading>, notes: List<Note>): String {
        val readingRows = readings.sortedByDescending { it.takenAt }.map { r ->
            listOf(
                "READING", r.id, r.takenAt.toString(),
                r.systolicMmHg.toString(), r.diastolicMmHg.toString(), r.heartRateBpm.toString(),
                r.arm.name, "", "", if (r.irregularHeartbeat) "true" else "false"
            )
        }
        val noteRows = notes.sortedByDescending { it.date.atTime(it.time) }.map { n ->
            listOf(
                "NOTE", n.id, n.date.atTime(n.time).toString(),
                "", "", "", "",
                n.noteType.name, guardFormulaInjection(n.details), ""
            )
        }
        val rows = readingRows + noteRows
        val lines = listOf(HEADER.joinToString(",")) + rows.map { row -> row.joinToString(",") { csvField(it) } }
        return lines.joinToString("\n") + "\n"
    }

    /**
     * True if [csv] is empty or starts with one of this app's headers — i.e. it is safe for sync
     * to adopt and rewrite. A same-named file written by some other app is left alone.
     */
    fun isBackupFile(csv: String): Boolean {
        val header = parseRows(csv.removePrefix(BOM)).firstOrNull { row -> row.any { it.isNotBlank() } }
            ?: return true
        return header == HEADER || header == HEADER_V2 || header == LEGACY_HEADER
    }

    fun parse(csv: String): ParsedCsv {
        // Spreadsheet apps often save UTF-8 with a byte-order mark, which would otherwise stick
        // to the first header cell and make a known header unrecognisable.
        val rows = parseRows(csv.removePrefix(BOM)).filter { row -> row.any { it.isNotBlank() } }
        if (rows.isEmpty()) return ParsedCsv.EMPTY

        val header = rows.first()
        val dataRows = rows.drop(1)

        if (header == LEGACY_HEADER) {
            val readings = dataRows.mapNotNull { parts -> parseLegacyReading(parts) }
            return ParsedCsv(readings, emptyList(), skippedRows = dataRows.size - readings.size)
        }

        // The row width follows the file's own header, or every row of a v2 backup would be
        // rejected as the wrong width. An unrecognised header is read as v2; its rows then fail
        // to parse and are counted as skipped.
        val width = if (header == HEADER) HEADER.size else HEADER_V2.size
        val readings = mutableListOf<Reading>()
        val notes = mutableListOf<Note>()
        var skipped = 0
        for (parts in dataRows) {
            if (parts.size != width) {
                skipped++
                continue
            }
            val parsed = runCatching {
                when (parts[0]) {
                    "READING" -> readings.add(
                        Reading(
                            id = parts[1],
                            takenAt = Instant.parse(parts[2]),
                            systolicMmHg = parts[3].toInt(),
                            diastolicMmHg = parts[4].toInt(),
                            heartRateBpm = parts[5].toInt(),
                            arm = Arm.valueOf(parts[6]),
                            irregularHeartbeat = parseFlag(parts.getOrElse(9) { "" })
                        )
                    )
                    "NOTE" -> {
                        val (noteDate, noteTime) = parseNoteDateTime(parts[2])
                        notes.add(
                            Note(
                                id = parts[1],
                                date = noteDate,
                                noteType = NoteType.valueOf(parts[7]),
                                details = unguardFormulaInjection(parts[8]),
                                time = noteTime
                            )
                        )
                    }
                    else -> false
                }
            }.getOrDefault(false)
            if (!parsed) skipped++
        }
        return ParsedCsv(readings, notes, skipped)
    }

    /** U+FEFF, the byte-order mark (built from its code point to keep the source plain ASCII). */
    private val BOM = Char(0xFEFF).toString()

    /**
     * Notes now store a full local date-time (e.g. 2026-07-20T16:24). Legacy backups wrote a
     * date only (2026-07-20) — parse those and default the time to just-after-midnight so old
     * files keep importing.
     */
    private fun parseNoteDateTime(value: String): Pair<LocalDate, LocalTime> =
        runCatching {
            val dt = LocalDateTime.parse(value)
            dt.toLocalDate() to dt.toLocalTime()
        }.getOrElse { LocalDate.parse(value) to LocalTime.of(0, 1) }

    /** Blank (a v2 file, or a note row) means not flagged; anything unexpected fails the row. */
    private fun parseFlag(value: String): Boolean = when (value) {
        "true" -> true
        "false", "" -> false
        else -> throw IllegalArgumentException("Bad irregular_heartbeat value: $value")
    }

    private fun parseLegacyReading(parts: List<String>): Reading? {
        if (parts.size != LEGACY_HEADER.size) return null
        return runCatching {
            Reading(
                id = parts[0],
                takenAt = Instant.parse(parts[1]),
                systolicMmHg = parts[2].toInt(),
                diastolicMmHg = parts[3].toInt(),
                heartRateBpm = parts[4].toInt(),
                arm = Arm.valueOf(parts[5])
            )
        }.getOrNull()
    }

    private fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
            "\"" + value.replace("\"", "\"\"") + "\""
        else value

    private val FORMULA_TRIGGER_CHARS = charArrayOf('=', '+', '-', '@', '\t', '\r')

    /**
     * Spreadsheet apps execute cells starting with = + - @ as formulas, so a note like
     * "=HYPERLINK(...)" pasted into the details field would run when the backup CSV is
     * opened in Excel/Sheets (CSV formula injection). Escape with a leading apostrophe —
     * spreadsheets treat that as a text marker. A literal leading apostrophe is doubled so
     * the app's own import ([unguardFormulaInjection] strips exactly one) stays lossless.
     */
    private fun guardFormulaInjection(value: String): String = when {
        value.isEmpty() -> value
        value[0] in FORMULA_TRIGGER_CHARS || value[0] == '\'' -> "'$value"
        else -> value
    }

    private fun unguardFormulaInjection(value: String): String =
        if (value.startsWith("'")) value.substring(1) else value

    /** Minimal RFC4180 tokenizer — needed because quoted fields may contain literal newlines. */
    private fun parseRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        var field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> {
                        row.add(field.toString())
                        field = StringBuilder()
                    }
                    '\r' -> {}
                    '\n' -> {
                        row.add(field.toString())
                        field = StringBuilder()
                        rows.add(row)
                        row = mutableListOf()
                    }
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows
    }
}
