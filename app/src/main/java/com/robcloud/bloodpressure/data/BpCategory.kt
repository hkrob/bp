package com.robcloud.bloodpressure.data

/**
 * American Heart Association blood-pressure categories. A reading falls into the highest
 * category either number qualifies for (e.g. 125/95 is Stage 2 because of the diastolic).
 */
enum class BpCategory(val label: String) {
    NORMAL("Normal"),
    ELEVATED("Elevated"),
    STAGE_1("Stage 1 hypertension"),
    STAGE_2("Stage 2 hypertension"),
    CRISIS("Hypertensive crisis");

    companion object {
        fun of(systolicMmHg: Int, diastolicMmHg: Int): BpCategory = when {
            systolicMmHg >= 180 || diastolicMmHg >= 120 -> CRISIS
            systolicMmHg >= 140 || diastolicMmHg >= 90 -> STAGE_2
            systolicMmHg >= 130 || diastolicMmHg >= 80 -> STAGE_1
            systolicMmHg >= 120 -> ELEVATED
            else -> NORMAL
        }
    }
}

fun Reading.bpCategory(): BpCategory = BpCategory.of(systolicMmHg, diastolicMmHg)
