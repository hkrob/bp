package com.robcloud.bloodpressure.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import android.net.Uri
import com.robcloud.bloodpressure.data.BpCategory
import com.robcloud.bloodpressure.data.Note
import com.robcloud.bloodpressure.data.Reading
import com.robcloud.bloodpressure.data.bpCategory
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Renders a one-or-more-page "doctor's report" PDF for the readings and notes currently in
 * view: a summary (period, count, average with AHA category, range), a line chart with note
 * markers on the systolic line, the full readings table, and a notes list. Built with the
 * platform [PdfDocument]/[Canvas] — no third-party dependency, no network, consistent with the
 * app's offline-only design. Returns a shareable content Uri via [FileProvider].
 */
object ReportPdf {

    // A4 at 72 dpi, portrait.
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val CONTENT_RIGHT = PAGE_WIDTH - MARGIN
    private const val BOTTOM_LIMIT = PAGE_HEIGHT - MARGIN

    private val dateFmt: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault())
    private val timeFmt: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())
    private val fileStampFmt: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())
    private val chartAxisFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

    private const val COLOR_TEXT = 0xFF1A1A1A.toInt()
    private const val COLOR_MUTED = 0xFF6B6B6B.toInt()
    private const val COLOR_RULE = 0xFFCFCFCF.toInt()
    private const val COLOR_HEADER_BG = 0xFFEDEDED.toInt()
    private const val COLOR_SYS = 0xFF00695C.toInt()
    private const val COLOR_DIA = 0xFF3B6E8F.toInt()
    private const val COLOR_HR = 0xFFD98A8A.toInt()
    private const val COLOR_NOTE = 0xFF7E57C2.toInt()

    private fun categoryColor(category: BpCategory): Int = when (category) {
        BpCategory.NORMAL -> 0xFF2E7D32.toInt()
        BpCategory.ELEVATED, BpCategory.STAGE_1 -> 0xFFCC8800.toInt()
        BpCategory.STAGE_2, BpCategory.CRISIS -> 0xFFD32F2F.toInt()
    }

    /**
     * Writes the report PDF into the app cache and returns a FileProvider content Uri for sharing.
     * [periodLabel] is the human period name (e.g. "Last 30 days"); [readings]/[notes] are the
     * already period-filtered sets shown on the History tab.
     */
    fun generate(
        context: Context,
        periodLabel: String,
        readings: List<Reading>,
        notes: List<Note>
    ): Uri {
        val doc = PdfDocument()
        val ascending = readings.sortedBy { it.takenAt }
        val newestFirst = readings.sortedByDescending { it.takenAt }
        val notesNewestFirst = notes.sortedByDescending { it.date }

        val ctx = PageContext(doc)
        ctx.newPage()
        drawHeader(ctx, periodLabel, ascending)
        drawSummary(ctx, ascending)
        drawChart(ctx, ascending, notes)
        drawReadingsTable(ctx, newestFirst)
        drawNotes(ctx, notesNewestFirst)
        ctx.finish()

        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "BP-Report-${fileStampFmt.format(Instant.now())}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Holds the running PDF document, current page/canvas, and the y cursor across pages. */
    private class PageContext(val doc: PdfDocument) {
        var page: PdfDocument.Page? = null
        lateinit var canvas: Canvas
        var y = MARGIN
        private var pageNumber = 0

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = doc.startPage(info)
            canvas = page!!.canvas
            y = MARGIN
        }

        /** Ensures [needed] vertical points remain; starts a new page (returning true) if not. */
        fun ensure(needed: Float): Boolean {
            if (y + needed <= BOTTOM_LIMIT) return false
            newPage()
            return true
        }

        fun finish() {
            page?.let { doc.finishPage(it) }
            page = null
        }
    }

    private fun paint(color: Int, size: Float, bold: Boolean = false) = Paint().apply {
        this.color = color
        textSize = size
        isAntiAlias = true
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun drawHeader(ctx: PageContext, periodLabel: String, ascending: List<Reading>) {
        ctx.canvas.drawText("Blood Pressure Report", MARGIN, ctx.y + 18f, paint(COLOR_TEXT, 20f, bold = true))
        ctx.y += 30f
        val range = if (ascending.isEmpty()) periodLabel else {
            "${dateFmt.format(ascending.first().takenAt)} – ${dateFmt.format(ascending.last().takenAt)}"
        }
        ctx.canvas.drawText(
            "$periodLabel  ·  $range  ·  generated ${dateFmt.format(Instant.now())}",
            MARGIN, ctx.y + 12f, paint(COLOR_MUTED, 11f)
        )
        ctx.y += 24f
        rule(ctx)
        ctx.y += 12f
    }

    private fun drawSummary(ctx: PageContext, readings: List<Reading>) {
        if (readings.isEmpty()) {
            ctx.canvas.drawText("No readings in this period.", MARGIN, ctx.y + 12f, paint(COLOR_TEXT, 12f))
            ctx.y += 24f
            return
        }
        val avgSys = readings.map { it.systolicMmHg }.average().roundToInt()
        val avgDia = readings.map { it.diastolicMmHg }.average().roundToInt()
        val avgHr = readings.map { it.heartRateBpm }.average().roundToInt()
        val category = BpCategory.of(avgSys, avgDia)
        val minSys = readings.minOf { it.systolicMmHg }
        val maxSys = readings.maxOf { it.systolicMmHg }
        val minDia = readings.minOf { it.diastolicMmHg }
        val maxDia = readings.maxOf { it.diastolicMmHg }

        ctx.canvas.drawText("Summary", MARGIN, ctx.y + 14f, paint(COLOR_TEXT, 14f, bold = true))
        ctx.y += 26f

        val label = paint(COLOR_MUTED, 11f)
        val value = paint(COLOR_TEXT, 12f)
        ctx.canvas.drawText("Readings", MARGIN, ctx.y + 12f, label)
        ctx.canvas.drawText("${readings.size}", MARGIN + 130f, ctx.y + 12f, value)
        ctx.y += 18f
        ctx.canvas.drawText("Average", MARGIN, ctx.y + 12f, label)
        ctx.canvas.drawText("$avgSys/$avgDia mmHg   ·   $avgHr bpm", MARGIN + 130f, ctx.y + 12f, value)
        val catPaint = paint(categoryColor(category), 12f, bold = true)
        ctx.canvas.drawText(category.label, MARGIN + 320f, ctx.y + 12f, catPaint)
        ctx.y += 18f
        ctx.canvas.drawText("Systolic range", MARGIN, ctx.y + 12f, label)
        ctx.canvas.drawText("$minSys – $maxSys mmHg", MARGIN + 130f, ctx.y + 12f, value)
        ctx.y += 18f
        ctx.canvas.drawText("Diastolic range", MARGIN, ctx.y + 12f, label)
        ctx.canvas.drawText("$minDia – $maxDia mmHg", MARGIN + 130f, ctx.y + 12f, value)
        ctx.y += 26f
    }

    private fun drawChart(ctx: PageContext, ascending: List<Reading>, notes: List<Note>) {
        if (ascending.size < 2) return
        val chartH = 200f
        ctx.ensure(chartH + 60f)
        ctx.canvas.drawText("Trend", MARGIN, ctx.y + 14f, paint(COLOR_TEXT, 14f, bold = true))
        ctx.y += 24f

        val left = MARGIN + 34f
        val right = CONTENT_RIGHT
        val top = ctx.y
        val bottom = ctx.y + chartH

        val allValues = ascending.flatMap { listOf(it.systolicMmHg, it.diastolicMmHg, it.heartRateBpm) }
        var minV = allValues.min().toFloat()
        var maxV = allValues.max().toFloat()
        val pad = ((maxV - minV) * 0.1f).coerceAtLeast(5f)
        minV -= pad; maxV += pad
        val xs = ascending.map { it.takenAt.epochSecond.toDouble() }
        val minX = xs.first(); val maxX = xs.last()
        val spanX = (maxX - minX).coerceAtLeast(1.0)

        fun px(t: Double) = (left + (t - minX) / spanX * (right - left)).toFloat()
        fun py(v: Float) = (bottom - (v - minV) / (maxV - minV) * (bottom - top))

        // Axis frame + horizontal gridlines with value labels.
        val axisPaint = Paint().apply { color = COLOR_RULE; strokeWidth = 1f; isAntiAlias = true }
        val gridLabel = paint(COLOR_MUTED, 9f)
        val ticks = 4
        for (i in 0..ticks) {
            val v = minV + (maxV - minV) * i / ticks
            val yy = py(v)
            ctx.canvas.drawLine(left, yy, right, yy, axisPaint)
            ctx.canvas.drawText("${v.roundToInt()}", MARGIN - 6f, yy + 3f, gridLabel)
        }

        fun series(selector: (Reading) -> Int, color: Int) {
            val p = Paint().apply {
                this.color = color; strokeWidth = 2f; isAntiAlias = true; style = Paint.Style.STROKE
            }
            for (i in 0 until ascending.size - 1) {
                ctx.canvas.drawLine(
                    px(xs[i]), py(selector(ascending[i]).toFloat()),
                    px(xs[i + 1]), py(selector(ascending[i + 1]).toFloat()), p
                )
            }
        }
        series({ it.heartRateBpm }, COLOR_HR)
        series({ it.diastolicMmHg }, COLOR_DIA)
        series({ it.systolicMmHg }, COLOR_SYS)

        // Note markers on the systolic line (systolic interpolated at each note date).
        val sysY = ascending.map { it.systolicMmHg.toDouble() }
        val notePaint = Paint().apply { color = COLOR_NOTE; isAntiAlias = true; style = Paint.Style.FILL }
        val noteDates = notes.map { it.date }.distinct()
        for (d in noteDates) {
            val t = d.atStartOfDay(ZoneId.systemDefault()).toEpochSecond().toDouble()
            if (t < minX || t > maxX) continue
            val v = interpolate(t, xs, sysY)
            ctx.canvas.drawCircle(px(t), py(v.toFloat()), 4f, notePaint)
        }

        // X-axis end labels.
        ctx.canvas.drawText(chartAxisFmt.format(ascending.first().takenAt.atZone(ZoneId.systemDefault())),
            left, bottom + 14f, gridLabel)
        val endLabel = chartAxisFmt.format(ascending.last().takenAt.atZone(ZoneId.systemDefault()))
        ctx.canvas.drawText(endLabel, right - gridLabel.measureText(endLabel), bottom + 14f, gridLabel)

        ctx.y = bottom + 24f
        // Legend.
        val lx = MARGIN
        drawLegendDot(ctx, lx, "Systolic", COLOR_SYS, 0f)
        drawLegendDot(ctx, lx, "Diastolic", COLOR_DIA, 90f)
        drawLegendDot(ctx, lx, "Heart rate", COLOR_HR, 185f)
        if (noteDates.isNotEmpty()) drawLegendDot(ctx, lx, "Note", COLOR_NOTE, 290f)
        ctx.y += 24f
    }

    private fun drawLegendDot(ctx: PageContext, baseX: Float, label: String, color: Int, offset: Float) {
        val cy = ctx.y + 6f
        ctx.canvas.drawCircle(baseX + offset + 4f, cy, 4f, Paint().apply {
            this.color = color; isAntiAlias = true
        })
        ctx.canvas.drawText(label, baseX + offset + 12f, cy + 4f, paint(COLOR_TEXT, 10f))
    }

    private fun drawReadingsTable(ctx: PageContext, newestFirst: List<Reading>) {
        if (newestFirst.isEmpty()) return
        ctx.ensure(40f)
        ctx.canvas.drawText("Readings", MARGIN, ctx.y + 14f, paint(COLOR_TEXT, 14f, bold = true))
        ctx.y += 24f
        drawTableHeader(ctx)
        val rowH = 18f
        for (r in newestFirst) {
            if (ctx.ensure(rowH)) drawTableHeader(ctx)
            val cat = r.bpCategory()
            val baseline = ctx.y + 13f
            ctx.canvas.drawText(dateFmt.format(r.takenAt), COL_DATE, baseline, paint(COLOR_TEXT, 10f))
            ctx.canvas.drawText(timeFmt.format(r.takenAt), COL_TIME, baseline, paint(COLOR_TEXT, 10f))
            ctx.canvas.drawText("${r.systolicMmHg}/${r.diastolicMmHg}", COL_BP, baseline,
                paint(categoryColor(cat), 10f, bold = true))
            ctx.canvas.drawText("${r.heartRateBpm}", COL_HR, baseline, paint(COLOR_TEXT, 10f))
            ctx.canvas.drawText(if (r.arm.name == "LEFT") "L" else "R", COL_ARM, baseline, paint(COLOR_TEXT, 10f))
            ctx.canvas.drawText(cat.label, COL_CAT, baseline, paint(categoryColor(cat), 10f))
            ctx.y += rowH
            rule(ctx)
        }
        ctx.y += 10f
    }

    private const val COL_DATE = MARGIN
    private const val COL_TIME = 140f
    private const val COL_BP = 220f
    private const val COL_HR = 300f
    private const val COL_ARM = 350f
    private const val COL_CAT = 400f

    private fun drawTableHeader(ctx: PageContext) {
        ctx.canvas.drawRect(MARGIN, ctx.y, CONTENT_RIGHT, ctx.y + 18f, Paint().apply { color = COLOR_HEADER_BG })
        val h = paint(COLOR_TEXT, 10f, bold = true)
        val b = ctx.y + 13f
        ctx.canvas.drawText("Date", COL_DATE + 2f, b, h)
        ctx.canvas.drawText("Time", COL_TIME, b, h)
        ctx.canvas.drawText("BP", COL_BP, b, h)
        ctx.canvas.drawText("HR", COL_HR, b, h)
        ctx.canvas.drawText("Arm", COL_ARM, b, h)
        ctx.canvas.drawText("Category", COL_CAT, b, h)
        ctx.y += 18f
    }

    private fun drawNotes(ctx: PageContext, notes: List<Note>) {
        if (notes.isEmpty()) return
        ctx.ensure(40f)
        ctx.canvas.drawText("Notes", MARGIN, ctx.y + 14f, paint(COLOR_TEXT, 14f, bold = true))
        ctx.y += 24f
        for (n in notes) {
            val head = "${dateFmt.format(n.date.atStartOfDay(ZoneId.systemDefault()).toInstant())}   " +
                "[${n.noteType.abbreviation}] ${n.noteType.label}"
            ctx.ensure(16f)
            ctx.canvas.drawText(head, MARGIN, ctx.y + 12f, paint(COLOR_TEXT, 11f, bold = true))
            ctx.y += 16f
            for (segment in wrap(n.details, paint(COLOR_TEXT, 10f), CONTENT_RIGHT - MARGIN - 12f)) {
                ctx.ensure(13f)
                ctx.canvas.drawText(segment, MARGIN + 12f, ctx.y + 10f, paint(COLOR_MUTED, 10f))
                ctx.y += 13f
            }
            ctx.y += 6f
        }
    }

    private fun rule(ctx: PageContext) {
        ctx.canvas.drawLine(MARGIN, ctx.y, CONTENT_RIGHT, ctx.y,
            Paint().apply { color = COLOR_RULE; strokeWidth = 0.5f })
    }

    /** Greedy word-wrap to [maxWidth] using the paint's measured text width. */
    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return emptyList()
        val words = text.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (w in words) {
            val candidate = if (current.isEmpty()) w else "$current $w"
            if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                lines.add(current.toString())
                current = StringBuilder(w)
            } else {
                current = StringBuilder(candidate)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    private fun interpolate(x: Double, xs: List<Double>, ys: List<Double>): Double {
        if (x <= xs.first()) return ys.first()
        if (x >= xs.last()) return ys.last()
        val hi = xs.indexOfFirst { it >= x }
        val lo = hi - 1
        val span = xs[hi] - xs[lo]
        if (span == 0.0) return ys[hi]
        val t = (x - xs[lo]) / span
        return ys[lo] + t * (ys[hi] - ys[lo])
    }
}
