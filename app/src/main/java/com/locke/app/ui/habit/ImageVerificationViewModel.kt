package com.locke.app.ui.habit

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locke.app.data.billing.EntitlementRepository
import com.locke.app.data.repository.HabitRepository
import com.locke.app.data.repository.PreferencesRepository
import com.locke.app.data.verification.ImageVerificationClient
import com.locke.app.data.verification.ImageVerificationException
import com.locke.app.data.verification.VerificationRequest
import com.locke.app.data.verification.VerificationResult
import com.locke.app.domain.model.Habit
import com.locke.app.ui.navigation.Screen
import com.locke.app.util.DateProvider
import com.locke.app.util.ImageStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ImageVerificationUiState(
    val habit: Habit? = null,
    val capturedImagePath: String? = null,
    val isVerifying: Boolean = false,
    val result: VerificationResult? = null,
    val errorMessage: String? = null,
    val requiresPremium: Boolean = false,
    val isDone: Boolean = false,
    /** True once [ImageVerificationViewModel.onOverride] has been used -- see [ImageVerificationViewModel.onCleared]. */
    val overridden: Boolean = false,
    /**
     * Null once premium (unlimited checks, nothing to count down). Otherwise how many
     * of this calendar month's free checks are left, refreshed after every submit --
     * see [PreferencesRepository.freeVerificationsRemaining].
     */
    val freeChecksRemaining: Int? = null,
)

/** Drives the "submit today's proof photo" flow for one [com.locke.app.domain.model.HabitType.PHOTO] habit. */
@HiltViewModel
class ImageVerificationViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val verificationClient: ImageVerificationClient,
    private val entitlementRepository: EntitlementRepository,
    private val preferencesRepository: PreferencesRepository,
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val habitId: Long = requireNotNull(savedStateHandle.get<String>(Screen.ARG_HABIT_ID)).toLong()

    private val _uiState = MutableStateFlow(ImageVerificationUiState())
    val uiState: StateFlow<ImageVerificationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(habit = habitRepository.getHabit(habitId))
        }
        refreshFreeChecksRemaining()
    }

    /** Re-reads how many free checks are left this month -- called on init and again after every submit, since a completed check spends one. Left null (hidden) once premium. */
    private fun refreshFreeChecksRemaining() {
        viewModelScope.launch {
            val remaining = if (entitlementRepository.isPremium()) {
                null
            } else {
                preferencesRepository.freeVerificationsRemaining(DateProvider.currentMonthString()).first()
            }
            _uiState.value = _uiState.value.copy(freeChecksRemaining = remaining)
        }
    }

    /** Called once the camera or gallery hands back a photo; copies it into app storage. */
    fun onImageCaptured(uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { ImageStore.saveVerificationImage(appContext, uri, habitId) }
            if (path != null) {
                ImageStore.deleteQuietly(_uiState.value.capturedImagePath)
                _uiState.value = _uiState.value.copy(
                    capturedImagePath = path,
                    result = null,
                    errorMessage = null,
                    requiresPremium = false,
                )
            }
        }
    }

    fun onRetake() {
        ImageStore.deleteQuietly(_uiState.value.capturedImagePath)
        _uiState.value = _uiState.value.copy(capturedImagePath = null, result = null, errorMessage = null)
    }

    fun onSubmit() {
        val state = _uiState.value
        val habit = state.habit ?: return
        val capturedPath = state.capturedImagePath ?: return
        if (state.isVerifying) return

        _uiState.value = state.copy(isVerifying = true, errorMessage = null, requiresPremium = false, result = null)
        viewModelScope.launch {
            try {
                val submittedBytes = withContext(Dispatchers.IO) { ImageStore.readBytes(capturedPath) }
                    ?: error("Couldn't read the photo")
                val exampleBytes = habit.verificationExampleImagePath?.let { path ->
                    withContext(Dispatchers.IO) { ImageStore.readBytes(path) }
                }

                val result = verificationClient.verify(
                    VerificationRequest(
                        habitName = habit.name,
                        description = habit.verificationPrompt,
                        exampleImage = exampleBytes,
                        submittedImage = submittedBytes,
                    ),
                )

                if (result.approved) {
                    habitRepository.setImageVerificationResult(
                        habitId = habit.id,
                        approved = true,
                        reasoning = result.reasoning,
                        imagePath = capturedPath,
                    )
                    _uiState.value = _uiState.value.copy(isVerifying = false, result = result, isDone = true)
                } else {
                    _uiState.value = _uiState.value.copy(isVerifying = false, result = result)
                }
                refreshFreeChecksRemaining()
            } catch (e: ImageVerificationException.RequiresPremium) {
                _uiState.value = _uiState.value.copy(isVerifying = false, requiresPremium = true, errorMessage = e.message)
            } catch (e: ImageVerificationException) {
                _uiState.value = _uiState.value.copy(isVerifying = false, errorMessage = e.message)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isVerifying = false, errorMessage = "Something went wrong. Try again.")
            }
        }
    }

    /**
     * Automated judgment is always overridable (design spec §6.2): marks today's photo
     * done without the vision model's approval, after it rejected (or failed on) the
     * submitted photo. The override itself is recorded in the completion's reasoning
     * text so it's visible later, distinct from a genuine model approval.
     */
    fun onOverride() {
        val state = _uiState.value
        val habit = state.habit ?: return
        val capturedPath = state.capturedImagePath ?: return
        viewModelScope.launch {
            habitRepository.setImageVerificationResult(
                habitId = habit.id,
                approved = true,
                reasoning = "Marked done manually -- overriding an automated rejection.",
                imagePath = capturedPath,
            )
            _uiState.value = _uiState.value.copy(isDone = true, overridden = true)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // A captured photo that was never approved (and never overridden) isn't proof
        // of anything -- don't keep it around. An overridden photo's path is already
        // saved as this completion's verificationImagePath, so it must survive.
        if (_uiState.value.result?.approved != true && !_uiState.value.overridden) {
            ImageStore.deleteQuietly(_uiState.value.capturedImagePath)
        }
    }
}
