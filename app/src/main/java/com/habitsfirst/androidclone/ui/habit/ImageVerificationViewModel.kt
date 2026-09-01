package com.habitsfirst.androidclone.ui.habit

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.data.verification.ImageVerificationClient
import com.habitsfirst.androidclone.data.verification.ImageVerificationException
import com.habitsfirst.androidclone.data.verification.VerificationRequest
import com.habitsfirst.androidclone.data.verification.VerificationResult
import com.habitsfirst.androidclone.domain.model.Habit
import com.habitsfirst.androidclone.ui.navigation.Screen
import com.habitsfirst.androidclone.util.ImageStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ImageVerificationUiState(
    val habit: Habit? = null,
    val capturedImagePath: String? = null,
    val isVerifying: Boolean = false,
    val result: VerificationResult? = null,
    val errorMessage: String? = null,
    val missingApiKey: Boolean = false,
    val isDone: Boolean = false,
)

/** Drives the "submit today's proof photo" flow for one [com.habitsfirst.androidclone.domain.model.HabitType.PHOTO] habit. */
@HiltViewModel
class ImageVerificationViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val verificationClient: ImageVerificationClient,
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
                    missingApiKey = false,
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

        _uiState.value = state.copy(isVerifying = true, errorMessage = null, missingApiKey = false, result = null)
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
            } catch (e: ImageVerificationException.MissingApiKey) {
                _uiState.value = _uiState.value.copy(isVerifying = false, missingApiKey = true, errorMessage = e.message)
            } catch (e: ImageVerificationException) {
                _uiState.value = _uiState.value.copy(isVerifying = false, errorMessage = e.message)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isVerifying = false, errorMessage = "Something went wrong. Try again.")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // A captured photo that was never approved isn't proof of anything -- don't keep it around.
        if (_uiState.value.result?.approved != true) {
            ImageStore.deleteQuietly(_uiState.value.capturedImagePath)
        }
    }
}
