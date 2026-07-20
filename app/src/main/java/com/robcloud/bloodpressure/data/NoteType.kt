package com.robcloud.bloodpressure.data

enum class NoteType(val abbreviation: String, val label: String) {
    MEDICATION_CHANGED("MC", "Medication Changed"),
    OTHER("OTH", "Other"),
    CHECK_UP("CHK", "Check Up"),
    SAMPLE_PROVIDED("SMP", "Sample Provided"),
    MEDICATION_TAKEN("MT", "Medication Taken")
}
