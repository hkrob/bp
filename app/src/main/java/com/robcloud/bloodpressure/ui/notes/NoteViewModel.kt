package com.robcloud.bloodpressure.ui.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.robcloud.bloodpressure.BloodPressureApp
import com.robcloud.bloodpressure.backup.BackupSyncWorker
import com.robcloud.bloodpressure.data.NOTE_DETAILS_MAX_LENGTH
import com.robcloud.bloodpressure.data.Note
import com.robcloud.bloodpressure.data.NoteType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class NoteUiState(
    val date: LocalDate = LocalDate.now(),
    val noteType: NoteType = NoteType.entries.first(),
    val details: String = "",
    val errorMessage: String? = null,
    val justSaved: Boolean = false
)

class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BloodPressureApp
    private val noteDao = app.database.noteDao()

    private val _uiState = MutableStateFlow(NoteUiState())
    val uiState: StateFlow<NoteUiState> = _uiState.asStateFlow()

    fun updateDate(date: LocalDate) = update { it.copy(date = date) }
    fun updateNoteType(type: NoteType) = update { it.copy(noteType = type) }
    fun updateDetails(value: String) =
        update { it.copy(details = value.take(NOTE_DETAILS_MAX_LENGTH), errorMessage = null) }

    fun save() {
        val state = _uiState.value
        val details = state.details.trim()
        if (details.isEmpty()) {
            update { it.copy(errorMessage = "Enter some details for the note") }
            return
        }

        viewModelScope.launch {
            noteDao.insert(Note(date = state.date, noteType = state.noteType, details = details))
            BackupSyncWorker.enqueue(app)
            _uiState.value = NoteUiState(justSaved = true)
        }
    }

    fun consumeSavedFlag() = update { it.copy(justSaved = false) }

    private fun update(transform: (NoteUiState) -> NoteUiState) {
        _uiState.value = transform(_uiState.value)
    }
}
