package com.habitsfirst.androidclone.ui.habit

import android.content.Context
import android.net.Uri
import android.nfc.Tag
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.domain.model.Habit
import com.habitsfirst.androidclone.ui.navigation.Screen
import com.habitsfirst.androidclone.util.ImageStore
import com.habitsfirst.androidclone.util.NfcTagIo
import com.habitsfirst.androidclone.util.QrCode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ScanTagUiState(
    val habit: Habit? = null,
    val isDone: Boolean = false,
    /** True right after a scan (NFC or QR) that didn't match this habit's tag -- cleared once shown. */
    val mismatch: Boolean = false,
)

/** Drives the "tap your tag, or scan its QR code" completion flow for one [com.habitsfirst.androidclone.domain.model.HabitType.TAG_SCAN] habit. */
@HiltViewModel
class ScanTagViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val habitId: Long = requireNotNull(savedStateHandle.get<String>(Screen.ARG_HABIT_ID)).toLong()

    private val _uiState = MutableStateFlow(ScanTagUiState())
    val uiState: StateFlow<ScanTagUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(habit = habitRepository.getHabit(habitId))
        }
    }

    /** Called by [com.habitsfirst.androidclone.ui.components.NfcReaderModeEffect] off the main thread -- reads the tag's own thread-safety note before adding more work here. */
    fun onNfcTagDiscovered(tag: Tag) {
        handleScannedPayload(NfcTagIo.readText(tag))
    }

    /** Called once the QR capture camera hands back a photo; decodes it off the main thread, then discards it -- it's not proof worth keeping, just a means to read the code. */
    fun onQrImageCaptured(uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { ImageStore.saveToCache(appContext, uri) } ?: return@launch
            val decoded = withContext(Dispatchers.Default) { QrCode.decodeFromFile(path) }
            ImageStore.deleteQuietly(path)
            handleScannedPayload(decoded)
        }
    }

    private fun handleScannedPayload(scanned: String?) {
        val expected = _uiState.value.habit?.tagPayload
        if (scanned != null && expected != null && scanned == expected) {
            viewModelScope.launch {
                habitRepository.setTallyHabitDone(habitId, true)
                _uiState.value = _uiState.value.copy(isDone = true, mismatch = false)
            }
        } else {
            _uiState.value = _uiState.value.copy(mismatch = true)
        }
    }

    fun onMismatchShown() {
        _uiState.value = _uiState.value.copy(mismatch = false)
    }

    /** "Mark done anyway" (design spec §6.2), for a lost, damaged, or hard-to-read tag. */
    fun onOverride() {
        viewModelScope.launch {
            habitRepository.setTallyHabitDone(habitId, true)
            _uiState.value = _uiState.value.copy(isDone = true)
        }
    }
}
