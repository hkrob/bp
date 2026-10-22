package com.robcloud.bloodpressure.ocr

import com.robcloud.bloodpressure.ui.DIASTOLIC_MAX
import com.robcloud.bloodpressure.ui.SYSTOLIC_MAX

/**
 * One piece of recognised text, reduced to what [parseReadingTokens] needs — deliberately not
 * ML Kit's own `Text` type, so the parsing logic here has no Android dependency and is plain-JVM
 * testable. [top]/[height] are pixel values from the recognizer's bounding box, in the same
 * (arbitrary but consistent) coordinate space for every token in one call.
 */
data class OcrToken(val text: String, val top: Int, val height: Int)

/** Scan result. A field is null when nothing plausible was found for it — never a guess. */
data class ReadingOcrResult(val systolic: Int?, val diastolic: Int?, val heartRate: Int?) {
    val isEmpty: Boolean get() = systolic == null && diastolic == null && heartRate == null
}

private val NUMBER = Regex("\\d+")

// How much smaller than the tallest candidate a token can be and still count as "the same big
// LCD digits" rather than printed text nearby — the scale markers and model number on the Omron
// sample this was built against ("135—", "85—", "HEM-7200") are all noticeably smaller than the
// reading itself.
private const val BIG_DIGIT_HEIGHT_FRACTION = 0.6

/**
 * Best-effort reading from a monitor photo's recognised text. Built for upper-arm monitors that
 * show systolic above diastolic above pulse, in large digits, which covers most home monitors
 * (including the Omron this was tested against) — not a guarantee for every model's layout.
 * Never auto-fills a value outside [the same ranges][com.robcloud.bloodpressure.ui.validateReading]
 * accepts, and this is a guess either way: the caller must let the user review every field before
 * saving, never save directly from it.
 */
fun parseReadingTokens(tokens: List<OcrToken>): ReadingOcrResult {
    data class Candidate(val value: Int, val top: Int, val height: Int)

    val candidates = tokens
        .flatMap { token -> NUMBER.findAll(token.text).map { Candidate(it.value.toInt(), token.top, token.height) } }
        // A broad net — field-specific ranges are checked once the three guesses are picked below,
        // after the systolic/diastolic order correction, which needs both numbers first.
        .filter { it.value in 10..300 }
    if (candidates.isEmpty()) return ReadingOcrResult(null, null, null)

    val tallest = candidates.maxOf { it.height }
    val bigOnes = candidates.filter { it.height >= tallest * BIG_DIGIT_HEIGHT_FRACTION }.sortedBy { it.top }

    val topGuess = bigOnes.getOrNull(0)?.value
    val middleGuess = bigOnes.getOrNull(1)?.value
    val heartRateGuess = bigOnes.getOrNull(2)?.value

    // Systolic is always the higher of the two blood-pressure numbers — corrects a monitor whose
    // layout puts diastolic on top, without needing to know every model's screen arrangement.
    val (systolicGuess, diastolicGuess) = if (topGuess != null && middleGuess != null && topGuess < middleGuess) {
        middleGuess to topGuess
    } else {
        topGuess to middleGuess
    }

    return ReadingOcrResult(
        systolic = systolicGuess?.takeIf { it in 60..SYSTOLIC_MAX },
        diastolic = diastolicGuess?.takeIf { it in 40..DIASTOLIC_MAX },
        heartRate = heartRateGuess?.takeIf { it in 30..220 }
    )
}
