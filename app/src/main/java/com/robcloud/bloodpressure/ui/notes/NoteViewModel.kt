package com.robcloud.bloodpressure.ui.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.robcloud.bloodpressure.BloodPressureApp
import com.robcloud.bloodpressure.backup.BackupSyncWorker
import com.robcloud.bloodpressure.data.DEFAULT_NOTE_TIME
import com.robcloud.bloodpressure.data.NOTE_DETAILS_MAX_LENGTH
import com.robcloud.bloodpressure.data.Note
import com.robcloud.bloodpressure.data.NoteType
import com.robcloud.bloodpressure.ui.validateTakenAt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private fun nowMinute(): LocalTime = LocalTime.now().withSecond(0).withNano(0)

data class NoteUiState(
    val date: LocalDate = LocalDate.now(),
    /** True once the user picks a date; until then the form follows the calendar. */
    val dateEdited: Boolean = false,
    /** Only used by Medication Taken notes, which record when the dose was taken. */
    val time: LocalTime = nowMinute(),
    val timeEdited: Boolean = false,
    val noteType: NoteType = NoteType.entries.first(),
    val details: String = "",
    val errorMessage: String? = null,
    val justSaved: Boolean = false
)

/**
 * The clock time stored with a note: the actual time for Medication Taken (picked, or now), the
 * [DEFAULT_NOTE_TIME] sort key for every other type.
 */
internal fun NoteUiState.noteTime(now: LocalTime): LocalTime = when {
    noteType != NoteType.MEDICATION_TAKEN -> DEFAULT_NOTE_TIME
    timeEdited -> time
    else -> now
}

private const val KEY_DATE = "date_epoch_day"
private const val KEY_TIME = "time_second_of_day"
private const val KEY_TYPE = "note_type"
private const val KEY_DETAILS = "details"

/**
 * The half-written note, as plain values for [SavedStateHandle], so it survives Android killing
 * the app in the background. A date or time that wasn't picked isn't saved: it follows the clock.
 */
internal fun NoteUiState.toSavedEntry(): Map<String, Any?> = mapOf(
    KEY_DATE to if (dateEdited) date.toEpochDay() else null,
    KEY_TIME to if (timeEdited) time.toSecondOfDay() else null,
    KEY_TYPE to noteType.name,
    KEY_DETAILS to details
)

internal fun NoteUiState.withSavedEntry(saved: (String) -> Any?): NoteUiState {
    val pickedDate = (saved(KEY_DATE) as? Long)?.let(LocalDate::ofEpochDay)
    val pickedTime = (saved(KEY_TIME) as? Int)?.let { LocalTime.ofSecondOfDay(it.toLong()) }
    return copy(
        date = pickedDate ?: date,
        dateEdited = pickedDate != null || dateEdited,
        time = pickedTime ?: time,
        timeEdited = pickedTime != null || timeEdited,
        noteType = NoteType.entries.firstOrNull { it.name == saved(KEY_TYPE) } ?: noteType,
        details = saved(KEY_DETAILS) as? String ?: details
    )
}

class NoteViewModel(
    application: Application,
    private val savedState: SavedStateHandle
) : AndroidViewModel(application) {
    private val app = application as BloodPressureApp
    private val noteDao = app.database.noteDao()

    private val _uiState = MutableStateFlow(NoteUiState().withSavedEntry { savedState[it] })
    val uiState: StateFlow<NoteUiState> = _uiState.asStateFlow()

    private var saving = false

    fun updateDate(date: LocalDate) = update { it.copy(date = date, dateEdited = true, errorMessage = null) }
    fun setDateToday() = update { it.copy(date = LocalDate.now(), dateEdited = false, errorMessage = null) }
    fun updateTime(time: LocalTime) = update { it.copy(time = time, timeEdited = true, errorMessage = null) }
    fun updateNoteType(type: NoteType) = update { it.copy(noteType = type, errorMessage = null) }
    fun updateDetails(value: String) =
        update { it.copy(details = value.take(NOTE_DETAILS_MAX_LENGTH), errorMessage = null) }

    /** Moves an unpicked date/time on to now (called when the screen comes to the foreground). */
    fun refreshNow() = update {
        it.copy(
            date = if (it.dateEdited) it.date else LocalDate.now(),
            time = if (it.timeEdited) it.time else nowMinute()
        )
    }

    fun save() {
        if (saving) return
        val state = _uiState.value
        val details = state.details.trim()
        // Medication Taken notes stand on their own (type + time say it all); every other type is
        // free text that needs content.
        if (details.isEmpty() && state.noteType != NoteType.MEDICATION_TAKEN) {
            update { it.copy(errorMessage = "Enter some details for the note") }
            return
        }
        val date = if (state.dateEdited) state.date else LocalDate.now()
        val time = state.noteTime(nowMinute())
        if (state.noteType == NoteType.MEDICATION_TAKEN) {
            validateTakenAt(LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant())?.let { error ->
                update { it.copy(errorMessage = error) }
                return
            }
        }

        saving = true
        viewModelScope.launch {
            try {
                noteDao.insert(Note(date = date, noteType = state.noteType, details = details, time = time))
                BackupSyncWorker.enqueue(app)
                update { NoteUiState(justSaved = true) }
            } finally {
                saving = false
            }
        }
    }

    fun consumeSavedFlag() = update { it.copy(justSaved = false) }

    private fun update(transform: (NoteUiState) -> NoteUiState) {
        val next = transform(_uiState.value)
        _uiState.value = next
        next.toSavedEntry().forEach { (key, value) -> savedState[key] = value }
    }
}
