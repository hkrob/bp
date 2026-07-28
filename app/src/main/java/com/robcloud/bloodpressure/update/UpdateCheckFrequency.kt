package com.robcloud.bloodpressure.update

enum class UpdateCheckFrequency(val label: String, val days: Long) {
    NEVER("Never", 0),
    DAILY("Daily", 1),
    WEEKLY("Weekly", 7),
    MONTHLY("Monthly", 30);

    companion object {
        val DEFAULT = DAILY
        fun fromName(name: String) = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
