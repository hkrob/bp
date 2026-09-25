package com.robcloud.bloodpressure.ui.capture

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.robcloud.bloodpressure.BloodPressureApp
import com.robcloud.bloodpressure.backup.BackupSyncWorker
import com.robcloud.bloodpressure.backup.backupBannerMessage
import com.robcloud.bloodpressure.backup.status
import com.robcloud.bloodpressure.data.Arm
import com.robcloud.bloodpressure.data.BpCategory
import com.robcloud.bloodpressure.data.Note
import com.robcloud.bloodpressure.data.NoteType
import com.robcloud.bloodpressure.data.Reading
import com.robcloud.bloodpressure.ui.validateReading
import com.robcloud.bloodpressure.ui.validateTakenAt
import com.robcloud.bloodpressure.widget.LastReadingWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** The crisis advisory says "act now", which only fits a reading taken about now, not a back-dated one. */
private val CRISIS_ADVICE_WINDOW: Duration = Duration.ofHours(1)

data class CaptureUiState(
    val systolic: String = "",
    val diastolic: String = "",
    val heartRate: String = "",
    val arm: Arm = Arm.LEFT,
    val takenAt: Instant = Instant.now(),
    /** True once the user picks a date or time; until then the form follows the clock. */
    val takenAtEdited: Boolean = false,
    val errorMessage: String? = null,
    val justSaved: Boolean = false,
    val medicationSaved: Boolean = false,
    /** "sys/dia" of a just-saved reading in the hypertensive-crisis range, for the advisory dialog. */
    val crisisBp: String? = null,
    /** Backup problem to warn about, if any (see [backupBannerMessage]). */
    val backupBanner: String? = null
)

/** Clears the entry for the next reading; keeps the arm and backup status. */
internal fun CaptureUiState.afterSave(crisisBp: String?, now: Instant): CaptureUiState = copy(
    systolic = "",
    diastolic = "",
    heartRate = "",
    takenAt = now,
    takenAtEdited = false,
    errorMessage = null,
    justSaved = true,
    crisisBp = crisisBp
)

/** The time to record: the picked one, or the moment of saving when nothing was picked. */
internal fun CaptureUiState.effectiveTakenAt(now: Instant): Instant = if (takenAtEdited) takenAt else now

class CaptureViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BloodPressureApp
    private val dao = app.database.readingDao()
    private val noteDao = app.database.noteDao()
    private val prefs = CapturePrefsStore(application)

    private val _uiState = MutableStateFlow(CaptureUiState(arm = prefs.lastArm()))
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    // Guards against a double tap inserting the same reading twice while the first insert runs.
    private var savingReading = false
    private var savingMedication = false

    init {
        refreshBackupStatus()
    }

    val lastReading: StateFlow<Reading?> = dao.observeLatest()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The reading before the latest one, if any — used to show a trend vs. last time. */
    val previousReading: StateFlow<Reading?> = dao.observeLatestTwo()
        .map { it.getOrNull(1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun updateSystolic(value: String) = update { it.copy(systolic = value.filterDigits(3), errorMessage = null) }
    fun updateDiastolic(value: String) = update { it.copy(diastolic = value.filterDigits(3), errorMessage = null) }
    fun updateHeartRate(value: String) = update { it.copy(heartRate = value.filterDigits(3), errorMessage = null) }

    fun updateArm(arm: Arm) {
        prefs.setLastArm(arm)
        update { it.copy(arm = arm) }
    }

    fun updateTakenAt(instant: Instant) = update { it.copy(takenAt = instant, takenAtEdited = true, errorMessage = null) }
    fun setTakenAtNow() = update { it.copy(takenAt = Instant.now(), takenAtEdited = false, errorMessage = null) }

    /** Moves the displayed time on to now — unless the user picked one, which is never overwritten. */
    fun refreshTakenAt() = update { if (it.takenAtEdited) it else it.copy(takenAt = Instant.now()) }

    fun refreshBackupStatus() {
        viewModelScope.launch {
            val banner = withContext(Dispatchers.IO) {
                backupBannerMessage(app.backupFolderStore.status(), Instant.now())
            }
            update { it.copy(backupBanner = banner) }
        }
    }

    fun save() {
        if (savingReading) return
        val state = _uiState.value
        val systolic = state.systolic.toIntOrNull()
        val diastolic = state.diastolic.toIntOrNull()
        val heartRate = state.heartRate.toIntOrNull()
        val now = Instant.now()
        val takenAt = state.effectiveTakenAt(now)

        val error = validateReading(systolic, diastolic, heartRate) ?: validateTakenAt(takenAt, now)
        if (error != null) {
            update { it.copy(errorMessage = error) }
            return
        }

        savingReading = true
        viewModelScope.launch {
            try {
                dao.insert(
                    Reading(
                        systolicMmHg = systolic!!,
                        diastolicMmHg = diastolic!!,
                        heartRateBpm = heartRate!!,
                        arm = state.arm,
                        takenAt = takenAt
                    )
                )
                BackupSyncWorker.enqueue(app)
                LastReadingWidgetProvider.refresh(app)
                val isCrisis = BpCategory.of(systolic, diastolic) == BpCategory.CRISIS &&
                    !takenAt.isBefore(now.minus(CRISIS_ADVICE_WINDOW))
                update { it.afterSave(crisisBp = if (isCrisis) "$systolic/$diastolic" else null, now = Instant.now()) }
            } finally {
                savingReading = false
            }
        }
    }

    /**
     * Quick-logs a Medication Taken note at the date & time shown in the form (now, unless one was
     * picked), recording the real clock time so it sorts among readings in the Log. Leaves any
     * in-progress reading input untouched.
     */
    fun saveMedicationTaken() {
        if (savingMedication) return
        val now = Instant.now()
        val takenAt = _uiState.value.effectiveTakenAt(now)
        validateTakenAt(takenAt, now)?.let { error ->
            update { it.copy(errorMessage = error) }
            return
        }
        val zoned = takenAt.atZone(ZoneId.systemDefault())
        savingMedication = true
        viewModelScope.launch {
            try {
                noteDao.insert(
                    Note(
                        date = zoned.toLocalDate(),
                        noteType = NoteType.MEDICATION_TAKEN,
                        details = "",
                        time = zoned.toLocalTime().withSecond(0).withNano(0)
                    )
                )
                BackupSyncWorker.enqueue(app)
                update { it.copy(medicationSaved = true) }
            } finally {
                savingMedication = false
            }
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
