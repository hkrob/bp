package com.robcloud.bloodpressure.ui.capture

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.robcloud.bloodpressure.BloodPressureApp
import com.robcloud.bloodpressure.data.Arm
import com.robcloud.bloodpressure.data.BpCategory
import com.robcloud.bloodpressure.data.Note
import com.robcloud.bloodpressure.data.NoteType
import com.robcloud.bloodpressure.data.Reading
import com.robcloud.bloodpressure.backup.BackupSyncWorker
import com.robcloud.bloodpressure.ui.validateReading
import com.robcloud.bloodpressure.widget.LastReadingWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

data class CaptureUiState(
    val systolic: String = "",
    val diastolic: String = "",
    val heartRate: String = "",
    val arm: Arm = Arm.LEFT,
    val takenAt: Instant = Instant.now(),
    val errorMessage: String? = null,
    val justSaved: Boolean = false,
    val medicationSaved: Boolean = false,
    /** "sys/dia" of a just-saved reading in the hypertensive-crisis range, for the advisory dialog. */
    val crisisBp: String? = null
)

class CaptureViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BloodPressureApp
    private val dao = app.database.readingDao()
    private val noteDao = app.database.noteDao()

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    val lastReading: StateFlow<Reading?> = dao.observeLatest()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The reading before the latest one, if any — used to show a trend vs. last time. */
    val previousReading: StateFlow<Reading?> = dao.observeLatestTwo()
        .map { it.getOrNull(1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun updateSystolic(value: String) = update { it.copy(systolic = value.filterDigits(3), errorMessage = null) }
    fun updateDiastolic(value: String) = update { it.copy(diastolic = value.filterDigits(3), errorMessage = null) }
    fun updateHeartRate(value: String) = update { it.copy(heartRate = value.filterDigits(3), errorMessage = null) }
    fun updateArm(arm: Arm) = update { it.copy(arm = arm) }
    fun updateTakenAt(instant: Instant) = update { it.copy(takenAt = instant) }
    fun setTakenAtNow() = update { it.copy(takenAt = Instant.now()) }

    fun save() {
        val state = _uiState.value
        val systolic = state.systolic.toIntOrNull()
        val diastolic = state.diastolic.toIntOrNull()
        val heartRate = state.heartRate.toIntOrNull()

        val error = validateReading(systolic, diastolic, heartRate)
        if (error != null) {
            update { it.copy(errorMessage = error) }
            return
        }

        viewModelScope.launch {
            dao.insert(
                Reading(
                    systolicMmHg = systolic!!,
                    diastolicMmHg = diastolic!!,
                    heartRateBpm = heartRate!!,
                    arm = state.arm,
                    takenAt = state.takenAt
                )
            )
            BackupSyncWorker.enqueue(app)
            LastReadingWidgetProvider.refresh(app)
            val isCrisis = BpCategory.of(systolic, diastolic!!) == BpCategory.CRISIS
            _uiState.value = CaptureUiState(
                justSaved = true,
                crisisBp = if (isCrisis) "$systolic/$diastolic" else null
            )
        }
    }

    /**
     * Quick-logs a Medication Taken note using the date & time currently selected in the form,
     * recording the real clock time so it sorts among readings in the Log. Leaves any in-progress
     * reading input untouched.
     */
    fun saveMedicationTaken() {
        val zoned = _uiState.value.takenAt.atZone(ZoneId.systemDefault())
        viewModelScope.launch {
            noteDao.insert(
                Note(
                    date = zoned.toLocalDate(),
                    noteType = NoteType.MEDICATION_TAKEN,
                    details = "",
                    time = zoned.toLocalTime()
                )
            )
            BackupSyncWorker.enqueue(app)
            update { it.copy(medicationSaved = true) }
        }
    }

    fun consumeSavedFlag() = update { it.copy(justSaved = false) }

    fun consumeMedicationSavedFlag() = update { it.copy(medicationSaved = false) }

    fun consumeCrisisWarning() = update { it.copy(crisisBp = null) }

    private fun update(transform: (CaptureUiState) -> CaptureUiState) {
        _uiState.value = transform(_uiState.value)
    }
}

private fun String.filterDigits(maxLen: Int): String = filter { it.isDigit() }.take(maxLen)
