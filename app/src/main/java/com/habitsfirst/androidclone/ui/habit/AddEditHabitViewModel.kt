package com.habitsfirst.androidclone.ui.habit

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.location.DeviceLocationProvider
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import com.habitsfirst.androidclone.domain.model.Habit
import com.habitsfirst.androidclone.domain.model.HabitKind
import com.habitsfirst.androidclone.domain.model.HabitType
import com.habitsfirst.androidclone.domain.model.InstalledApp
import com.habitsfirst.androidclone.service.WorkScheduler
import com.habitsfirst.androidclone.ui.navigation.Screen
import com.habitsfirst.androidclone.util.ImageStore
import com.habitsfirst.androidclone.util.InstalledAppsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.util.UUID
import javax.inject.Inject

data class AddEditHabitUiState(
    val habitId: Long = 0L,
    val name: String = "",
    val kind: HabitKind = HabitKind.GATING,
    val type: HabitType = HabitType.TALLY,
    val targetValue: Int = HabitType.TALLY.defaultTarget(),
    val targetPackageName: String? = null,
    val targetAppLabel: String? = null,
    /** [HabitType.PHOTO] only: what a proof photo must show. */
    val verificationPrompt: String = "",
    /** [HabitType.PHOTO] only: path to a saved example photo, if any. */
    val verificationExampleImagePath: String? = null,
    /** Empty means every day -- see [Habit.scheduledDays]. */
    val scheduledDays: Set<DayOfWeek> = emptySet(),
    /** [HabitType.VISIT_LOCATION] only. */
    val targetLatitude: Double? = null,
    /** [HabitType.VISIT_LOCATION] only. */
    val targetLongitude: Double? = null,
    /** [HabitType.VISIT_LOCATION] only. */
    val targetRadiusMeters: Int = Habit.DEFAULT_VISIT_LOCATION_RADIUS_METERS,
    /** [HabitType.VISIT_LOCATION] only. */
    val targetLocationLabel: String = "",
    /** True while [AddEditHabitViewModel.onCaptureCurrentLocation] is waiting on a fix. */
    val isCapturingLocation: Boolean = false,
    /** Set once a location capture attempt finds nothing -- cleared on the next attempt or once one succeeds. */
    val locationCaptureFailed: Boolean = false,
    /** [HabitType.GITHUB_CONTRIBUTION] only. */
    val targetGithubUsername: String = "",
    /** [HabitType.TAG_SCAN] only: generated once, the first time this type is picked -- see [AddEditHabitViewModel.onTypeChanged]. */
    val tagPayload: String? = null,
    val isSaving: Boolean = false,
    val isNew: Boolean = true,
    val canDelete: Boolean = false,
    /**
     * Hard mode: this is an existing gating habit, so it can't be deleted, downgraded, retyped,
     * re-verified, or otherwise made easier -- see the per-field guards in the view model that
     * key off this (target value can only go up, scheduled days can only be added, never removed).
     */
    val isKindLocked: Boolean = false,
    /** The target value as loaded from storage -- while [isKindLocked], [onTargetValueChanged] won't go below this. */
    val originalTargetValue: Int = 0,
    val installedApps: List<InstalledApp> = emptyList(),
    val savedSuccessfully: Boolean = false,
) {
    val isValid: Boolean
        get() = name.isNotBlank() &&
            (type != HabitType.APP_USAGE_MINUTES || targetPackageName != null) &&
            (!type.isMeasurable || targetValue > 0) &&
            (type != HabitType.PHOTO || verificationPrompt.isNotBlank() || verificationExampleImagePath != null) &&
            (type != HabitType.VISIT_LOCATION || (targetLatitude != null && targetLongitude != null)) &&
            (type != HabitType.GITHUB_CONTRIBUTION || targetGithubUsername.isNotBlank())
}

fun HabitType.defaultTarget(): Int = when (this) {
    HabitType.STEPS -> 10_000
    HabitType.TIMED_MINUTES -> 20
    HabitType.APP_USAGE_MINUTES -> 15
    HabitType.PHOTO -> 1
    HabitType.TALLY -> 1
    HabitType.WORKOUT_MINUTES -> 30
    HabitType.SLEEP_HOURS -> 8
    HabitType.VISIT_LOCATION -> 1
    HabitType.GITHUB_CONTRIBUTION -> 1
    HabitType.WAKATIME_CODING_MINUTES -> 30
    HabitType.TAG_SCAN -> 1
}

@HiltViewModel
class AddEditHabitViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val installedAppsProvider: InstalledAppsProvider,
    @ApplicationContext private val appContext: Context,
    private val preferencesRepository: PreferencesRepository,
    private val deviceLocationProvider: DeviceLocationProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val habitId: Long = savedStateHandle.get<String>(Screen.ARG_HABIT_ID)?.toLongOrNull() ?: 0L
    private val initialKind: HabitKind = savedStateHandle.get<String>(Screen.ARG_KIND)
        ?.let { runCatching { HabitKind.valueOf(it) }.getOrNull() }
        ?: HabitKind.GATING

    /** e.g. a "set up photo verification" deep link from Home -- absent (empty string) leaves the form at its own default type. */
    private val initialType: HabitType? = savedStateHandle.get<String>(Screen.ARG_TYPE)
        ?.let { runCatching { HabitType.valueOf(it) }.getOrNull() }

    private val _uiState = MutableStateFlow(
        AddEditHabitUiState(
            habitId = habitId,
            kind = initialKind,
            type = initialType ?: HabitType.TALLY,
            targetValue = (initialType ?: HabitType.TALLY).defaultTarget(),
            isNew = habitId == 0L,
            canDelete = habitId != 0L,
        ),
    )
    val uiState: StateFlow<AddEditHabitUiState> = _uiState.asStateFlow()

    init {
        if (habitId != 0L) {
            viewModelScope.launch {
                val habit = habitRepository.getHabit(habitId)
                if (habit != null) {
                    _uiState.value = _uiState.value.copy(
                        name = habit.name,
                        kind = habit.kind,
                        type = habit.type,
                        targetValue = habit.targetValue,
                        targetPackageName = habit.targetPackageName,
                        targetAppLabel = habit.targetAppLabel,
                        verificationPrompt = habit.verificationPrompt.orEmpty(),
                        verificationExampleImagePath = habit.verificationExampleImagePath,
                        scheduledDays = habit.scheduledDays,
                        originalTargetValue = habit.targetValue,
                        targetLatitude = habit.targetLatitude,
                        targetLongitude = habit.targetLongitude,
                        targetRadiusMeters = habit.targetRadiusMeters ?: Habit.DEFAULT_VISIT_LOCATION_RADIUS_METERS,
                        targetLocationLabel = habit.targetLocationLabel.orEmpty(),
                        targetGithubUsername = habit.targetGithubUsername.orEmpty(),
                        tagPayload = habit.tagPayload,
                    )
                }
                // Hard mode only ever locks an *existing* gate -- a habit already
                // created as GATING before or during hard mode. New habits and other
                // kinds are never restricted.
                if (habit?.kind == HabitKind.GATING) {
                    preferencesRepository.isHardModeEnabled.collect { hardMode ->
                        _uiState.value = _uiState.value.copy(canDelete = !hardMode, isKindLocked = hardMode)
                    }
                }
            }
        }
        viewModelScope.launch {
            val apps = installedAppsProvider.getLaunchableApps()
            _uiState.value = _uiState.value.copy(installedApps = apps)
        }
    }

    fun onNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun onKindChanged(kind: HabitKind) {
        if (_uiState.value.isKindLocked) return
        _uiState.value = _uiState.value.copy(kind = kind)
    }

    fun onTypeChanged(type: HabitType) {
        // Hard mode: swapping a locked gate's type (e.g. photo verification down to a
        // one-tap tally) would loosen it just as much as downgrading its kind would.
        if (_uiState.value.isKindLocked) return
        val current = _uiState.value
        _uiState.value = current.copy(
            type = type,
            targetValue = type.defaultTarget(),
            targetPackageName = if (type == HabitType.APP_USAGE_MINUTES) current.targetPackageName else null,
            targetAppLabel = if (type == HabitType.APP_USAGE_MINUTES) current.targetAppLabel else null,
            targetLatitude = if (type == HabitType.VISIT_LOCATION) current.targetLatitude else null,
            targetLongitude = if (type == HabitType.VISIT_LOCATION) current.targetLongitude else null,
            targetLocationLabel = if (type == HabitType.VISIT_LOCATION) current.targetLocationLabel else "",
            targetGithubUsername = if (type == HabitType.GITHUB_CONTRIBUTION) current.targetGithubUsername else "",
            // Generated once and kept even if the user picks a different type and comes
            // back -- see the field's own doc -- so a tag already written or printed
            // doesn't silently stop matching.
            tagPayload = if (type == HabitType.TAG_SCAN) (current.tagPayload ?: UUID.randomUUID().toString()) else current.tagPayload,
        )
    }

    /** Hard mode: a locked gate's target can be raised but never lowered below what it was when loaded. */
    fun onTargetValueChanged(value: Int) {
        val state = _uiState.value
        val floor = if (state.isKindLocked) state.originalTargetValue else 1
        _uiState.value = state.copy(targetValue = value.coerceAtLeast(floor))
    }

    fun onTargetAppSelected(app: InstalledApp) {
        // Hard mode: retargeting a locked APP_USAGE_MINUTES gate at an app the user
        // barely opens would defeat it as surely as unblocking the app would.
        if (_uiState.value.isKindLocked) return
        _uiState.value = _uiState.value.copy(targetPackageName = app.packageName, targetAppLabel = app.label)
    }

    fun onVerificationPromptChanged(prompt: String) {
        // Hard mode: a locked PHOTO gate's verification criteria can't be softened either.
        if (_uiState.value.isKindLocked) return
        _uiState.value = _uiState.value.copy(verificationPrompt = prompt)
    }

    /**
     * Toggling a day out of an empty (every-day) selection narrows it to just that day.
     * Hard mode: for a locked gate this can only ever add days, never remove one -- fewer
     * required days means it gates less often, the same loosening a kind downgrade would be.
     */
    fun onScheduledDayToggled(day: DayOfWeek) {
        val state = _uiState.value
        val current = state.scheduledDays
        if (state.isKindLocked && (day in current || current.isEmpty())) return
        _uiState.value = state.copy(
            scheduledDays = if (day in current) current - day else current + day,
        )
    }

    /** Resetting to every day only ever adds required days, so this is safe even for a locked gate. */
    fun onEveryDaySelected() {
        _uiState.value = _uiState.value.copy(scheduledDays = emptySet())
    }

    /** Copies a picked example photo into app storage and stores its path. */
    fun onExampleImageSelected(uri: Uri) {
        // Hard mode: swapping out a locked gate's example photo is a verification-softening move too.
        if (_uiState.value.isKindLocked) return
        val idForFile = if (habitId != 0L) habitId else System.currentTimeMillis()
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { ImageStore.saveExampleImage(appContext, uri, idForFile) }
            if (path != null) {
                ImageStore.deleteQuietly(_uiState.value.verificationExampleImagePath)
                _uiState.value = _uiState.value.copy(verificationExampleImagePath = path)
            }
        }
    }

    fun onExampleImageCleared() {
        if (_uiState.value.isKindLocked) return
        ImageStore.deleteQuietly(_uiState.value.verificationExampleImagePath)
        _uiState.value = _uiState.value.copy(verificationExampleImagePath = null)
    }

    /**
     * Captures the device's current location as a [HabitType.VISIT_LOCATION] habit's
     * target -- called while the user is physically standing at the place they want to
     * gate on (e.g. the gym). Requesting the runtime permission itself is
     * [AddEditHabitScreen]'s job; this assumes it's already granted.
     */
    fun onCaptureCurrentLocation() {
        if (_uiState.value.isKindLocked) return
        _uiState.value = _uiState.value.copy(isCapturingLocation = true, locationCaptureFailed = false)
        viewModelScope.launch {
            val location = deviceLocationProvider.requestCurrentLocation()
            _uiState.value = if (location != null) {
                _uiState.value.copy(
                    isCapturingLocation = false,
                    locationCaptureFailed = false,
                    targetLatitude = location.latitude,
                    targetLongitude = location.longitude,
                )
            } else {
                _uiState.value.copy(isCapturingLocation = false, locationCaptureFailed = true)
            }
        }
    }

    fun onLocationLabelChanged(label: String) {
        if (_uiState.value.isKindLocked) return
        _uiState.value = _uiState.value.copy(targetLocationLabel = label)
    }

    fun onRadiusMetersChanged(meters: Int) {
        if (_uiState.value.isKindLocked) return
        _uiState.value = _uiState.value.copy(targetRadiusMeters = meters)
    }

    fun onGithubUsernameChanged(username: String) {
        if (_uiState.value.isKindLocked) return
        _uiState.value = _uiState.value.copy(targetGithubUsername = username)
    }

    fun onSave() {
        val state = _uiState.value
        if (!state.isValid || state.isSaving) return
        _uiState.value = state.copy(isSaving = true)
        viewModelScope.launch {
            habitRepository.saveHabit(
                Habit(
                    id = state.habitId,
                    name = state.name.trim(),
                    kind = state.kind,
                    type = state.type,
                    targetValue = if (!state.type.isMeasurable) 1 else state.targetValue,
                    targetPackageName = state.targetPackageName,
                    targetAppLabel = state.targetAppLabel,
                    verificationPrompt = state.verificationPrompt.trim().takeIf {
                        state.type == HabitType.PHOTO && it.isNotBlank()
                    },
                    verificationExampleImagePath = state.verificationExampleImagePath.takeIf {
                        state.type == HabitType.PHOTO
                    },
                    scheduledDays = state.scheduledDays,
                    targetLatitude = state.targetLatitude.takeIf { state.type == HabitType.VISIT_LOCATION },
                    targetLongitude = state.targetLongitude.takeIf { state.type == HabitType.VISIT_LOCATION },
                    targetRadiusMeters = state.targetRadiusMeters.takeIf { state.type == HabitType.VISIT_LOCATION },
                    targetLocationLabel = state.targetLocationLabel.trim().takeIf {
                        state.type == HabitType.VISIT_LOCATION && it.isNotBlank()
                    },
                    targetGithubUsername = state.targetGithubUsername.trim().takeIf {
                        state.type == HabitType.GITHUB_CONTRIBUTION && it.isNotBlank()
                    },
                    tagPayload = state.tagPayload.takeIf { state.type == HabitType.TAG_SCAN },
                ),
            )
            // The periodic worker behind each of these types is otherwise only ever
            // enqueued once, at the end of onboarding (APP_USAGE_MINUTES) or never at all
            // (the other three, new since) -- a habit of that type added later (the
            // common case) would silently never get progress without this.
            // enqueueUniquePeriodicWork's KEEP policy makes this a no-op if it's already
            // running.
            when (state.type) {
                HabitType.APP_USAGE_MINUTES -> WorkScheduler.scheduleUsageTracking(appContext)
                HabitType.VISIT_LOCATION -> WorkScheduler.scheduleLocationSync(appContext)
                HabitType.GITHUB_CONTRIBUTION -> WorkScheduler.scheduleGithubSync(appContext)
                HabitType.WAKATIME_CODING_MINUTES -> WorkScheduler.scheduleWakaTimeSync(appContext)
                else -> {}
            }
            _uiState.value = _uiState.value.copy(isSaving = false, savedSuccessfully = true)
        }
    }

    fun onDelete() {
        if (habitId == 0L || !_uiState.value.canDelete) return
        viewModelScope.launch {
            habitRepository.deleteHabit(habitId)
            _uiState.value = _uiState.value.copy(savedSuccessfully = true)
        }
    }
}
