package com.habitsfirst.androidclone.ui.proofoflife

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.repository.ProofOfLifeRepository
import com.habitsfirst.androidclone.data.verification.ImageVerificationClient
import com.habitsfirst.androidclone.data.verification.ImageVerificationException
import com.habitsfirst.androidclone.data.verification.VerificationRequest
import com.habitsfirst.androidclone.data.verification.VerificationResult
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

data class ProofOfLifeUiState(
    val capturedImagePath: String? = null,
    val isVerifying: Boolean = false,
    val result: VerificationResult? = null,
    val errorMessage: String? = null,
    val missingApiKey: Boolean = false,
    val isDone: Boolean = false,
)

/**
 * A thin wrapper over the same capture/verify machinery a photo-verification habit uses
 * ([ImageVerificationClient], [ImageStore], [com.habitsfirst.androidclone.ui.components.PhotoVerificationCapture])
 * -- the only differences are a fixed prompt instead of a habit's own rules, throwaway
 * (not durably stored) photos since there's nothing to show again later, and
 * [ProofOfLifeRepository.confirmToday] instead of a habit completion on approval.
 */
@HiltViewModel
class ProofOfLifeViewModel @Inject constructor(
    private val proofOfLifeRepository: ProofOfLifeRepository,
    private val verificationClient: ImageVerificationClient,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProofOfLifeUiState())
    val uiState: StateFlow<ProofOfLifeUiState> = _uiState.asStateFlow()

    /** Called once the camera or gallery hands back a photo; scales it into a scratch file. */
    fun onImageCaptured(uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { ImageStore.saveToCache(appContext, uri) }
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
        val capturedPath = state.capturedImagePath ?: return
        if (state.isVerifying) return

        _uiState.value = state.copy(isVerifying = true, errorMessage = null, missingApiKey = false, result = null)
        viewModelScope.launch {
            try {
                val submittedBytes = withContext(Dispatchers.IO) { ImageStore.readBytes(capturedPath) }
                    ?: error("Couldn't read the photo")

                val result = verificationClient.verify(
                    VerificationRequest(
                        habitName = "Morning check-in",
                        description = PROOF_OF_LIFE_PROMPT,
                        exampleImage = null,
                        submittedImage = submittedBytes,
                    ),
                )

                if (result.approved) {
                    proofOfLifeRepository.confirmToday()
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
        // Nothing to keep around either way -- confirmation is just today's date on a flag.
        ImageStore.deleteQuietly(_uiState.value.capturedImagePath)
    }

    companion object {
        private const val PROOF_OF_LIFE_PROMPT =
            "A photo proving the user is awake and out of bed right now -- e.g. their kitchen, " +
                "bathroom, or the view outside. Reject a photo that could have been taken earlier: " +
                "someone still in bed, a photo of a screen or of another photo, a stock/generic image."
    }
}
