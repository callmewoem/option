package com.locke.app.ui.legal

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Loads the bundled privacy policy text (`assets/privacy_policy.md` -- see that file's
 * own note on where its canonical source and other copies live) for
 * [PrivacyPolicyScreen]. Bundled rather than fetched, so this screen (and what it
 * shows) works with no connectivity -- appropriate for a document a user might check
 * specifically because they're wondering what the app does without a network
 * connection in the first place.
 */
@HiltViewModel
class PrivacyPolicyViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _markdown = MutableStateFlow<String?>(null)
    val markdown: StateFlow<String?> = _markdown.asStateFlow()

    init {
        viewModelScope.launch {
            _markdown.value = withContext(Dispatchers.IO) {
                try {
                    appContext.assets.open("privacy_policy.md").bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    "Couldn't load the privacy policy. Please check your app store listing or contact the developer."
                }
            }
        }
    }
}
