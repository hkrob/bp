package com.robcloud.bloodpressure.backup

import com.robcloud.bloodpressure.data.Arm
import com.robcloud.bloodpressure.data.Note
import com.robcloud.bloodpressure.data.NoteType
import com.robcloud.bloodpressure.data.Reading
import java.time.Instant
import java.time.LocalDate

data class ParsedCsv(val readings: List<Reading>, val notes: List<Note>)

/**
 * Single CSV backs up both readings and notes, distinguished by a leading record_type
 * column. Reading/note-specific columns are left blank on rows that don't use them.
 * Note details is free text, so fields are RFC4180-quoted (quotes doubled, commas/
 * newlines wrapped) rather than relying on the old "no field can contain a comma" rule.
 */
object Csv {
    private val LEGACY_HEADER =
        listOf("id", "taken_at", "systolic_mmhg", "diastolic_mmhg", "heart_rate_bpm", "arm")
    private val HEADER = listOf(
        "record_type", "id", "date", "systolic_mmhg", "diastolic_mmhg", "heart_rate_bpm",
        "arm", "note_type", "note_details"
    )

    fun write(readings: List<Reading>, notes: List<Note>): String {
        val readingRows = readings.sortedByDescending { it.takenAt }.map { r ->
            listOf(
                "READING", r.id, r.takenAt.toString(),
                r.systolicMmHg.toString(), r.diastolicMmHg.toString(), r.heartRateBpm.toString(),
                r.arm.name, "", ""
            )
        }
        val noteRows = notes.sortedByDescending { it.date }.map { n ->
            listOf(
                "NOTE", n.id, n.date.toString(),
                "", "", "", "",
                n.noteType.name, guardFormulaInjection(n.details)
            )
        }
        val rows = readingRows + noteRows
        val lines = listOf(HEADER.joinToString(",")) + rows.map { row -> row.joinToString(",") { csvField(it) } }
        return lines.joinToString("\n") + "\n"
    }

    fun parse(csv: String): ParsedCsv {
        val rows = parseRows(csv).filter { row -> row.any { it.isNotBlank() } }
        if (rows.isEmpty()) return ParsedCsv(emptyList(), emptyList())

        val header = rows.first()
        val dataRows = rows.drop(1)

        if (header == LEGACY_HEADER) {
            val readings = dataRows.mapNotNull { parts -> parseLegacyReading(parts) }
            return ParsedCsv(readings, emptyList())
        }

        val readings = mutableListOf<Reading>()
        val notes = mutableListOf<Note>()
        for (parts in dataRows) {
            if (parts.size != HEADER.size) continue
            runCatching {
                when (parts[0]) {
                    "READING" -> readings.add(
                        Reading(
                            id = parts[1],
                            takenAt = Instant.parse(parts[2]),
                            systolicMmHg = parts[3].toInt(),
                            diastolicMmHg = parts[4].toInt(),
                            heartRateBpm = parts[5].toInt(),
                            arm = Arm.valueOf(parts[6])
                        )
                    )
                    "NOTE" -> notes.add(
                        Note(
                            id = parts[1],
                            date = LocalDate.parse(parts[2]),
                            noteType = NoteType.valueOf(parts[7]),
                            details = unguardFormulaInjection(parts[8])
                        )
                    )
                }
            }
        }
        return ParsedCsv(readings, notes)
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

    private val FORMULA_TRIGGER_CHARS = charArrayOf('=', '+', '-', '@', '\t')

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
