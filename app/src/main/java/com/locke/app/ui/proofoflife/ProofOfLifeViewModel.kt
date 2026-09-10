package com.locke.app.ui.proofoflife

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locke.app.data.billing.EntitlementRepository
import com.locke.app.data.repository.PreferencesRepository
import com.locke.app.data.repository.ProofOfLifeRepository
import com.locke.app.data.verification.ImageVerificationClient
import com.locke.app.data.verification.ImageVerificationException
import com.locke.app.data.verification.VerificationRequest
import com.locke.app.data.verification.VerificationResult
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

data class ProofOfLifeUiState(
    val capturedImagePath: String? = null,
    val isVerifying: Boolean = false,
    val result: VerificationResult? = null,
    val errorMessage: String? = null,
    val requiresPremium: Boolean = false,
    val isDone: Boolean = false,
    /** The configured check-in time ("HH:mm") -- the window's countdown starts here. */
    val deadlineTime: String = "08:00",
    /** Minutes after [deadlineTime] before a missed check-in is penalized -- the countdown's actual zero. */
    val windowMinutes: Int = PreferencesRepository.DEFAULT_PROOF_OF_LIFE_WINDOW_MINUTES,
    /** Priced exactly, per design spec §6.3 -- shown on the "take the penalty now" control. */
    val penaltyMinutes: Int = ProofOfLifeRepository.PENALTY_MINUTES,
    /** Null once premium (unlimited checks). Otherwise how many of this month's free AI photo checks are left -- see [com.locke.app.ui.habit.ImageVerificationUiState.freeChecksRemaining]'s twin on the habit-verification side. */
    val freeChecksRemaining: Int? = null,
)

/**
 * A thin wrapper over the same capture/verify machinery a photo-verification habit uses
 * ([ImageVerificationClient], [ImageStore], [com.locke.app.ui.components.PhotoVerificationCapture])
 * -- the only differences are a fixed prompt instead of a habit's own rules, throwaway
 * (not durably stored) photos since there's nothing to show again later, and
 * [ProofOfLifeRepository.confirmToday] instead of a habit completion on approval.
 */
@HiltViewModel
class ProofOfLifeViewModel @Inject constructor(
    private val proofOfLifeRepository: ProofOfLifeRepository,
    private val verificationClient: ImageVerificationClient,
    private val entitlementRepository: EntitlementRepository,
    private val preferencesRepository: PreferencesRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProofOfLifeUiState())
    val uiState: StateFlow<ProofOfLifeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = proofOfLifeRepository.settings.first()
            _uiState.value = _uiState.value.copy(deadlineTime = settings.time, windowMinutes = settings.windowMinutes)
        }
        refreshFreeChecksRemaining()
    }

    /** Re-reads how many free checks are left this month -- called on init and again after every submit. Left null (hidden) once premium. */
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
        val capturedPath = state.capturedImagePath ?: return
        if (state.isVerifying) return

        _uiState.value = state.copy(isVerifying = true, errorMessage = null, requiresPremium = false, result = null)
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
     * Automated judgment is always overridable (design spec §6.2): confirms today's
     * check-in without the vision model's approval, after it rejected (or failed on)
     * the submitted photo.
     */
    fun onOverride() {
        viewModelScope.launch {
            proofOfLifeRepository.confirmToday()
            _uiState.value = _uiState.value.copy(isDone = true)
        }
    }

    /** Ends the check-in window early at a stated cost, instead of waiting out the countdown (design spec §7). */
    fun onTakePenaltyNow() {
        viewModelScope.launch {
            proofOfLifeRepository.takePenaltyNow()
            _uiState.value = _uiState.value.copy(isDone = true)
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
