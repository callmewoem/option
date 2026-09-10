package com.locke.app.data.verification

/** What's being checked: the habit's own rules, plus the photo submitted as proof today. */
data class VerificationRequest(
    val habitName: String,
    /** The user-written description of what counts as done, if they gave one. */
    val description: String?,
    /** A saved example photo of what "done" looks like, if the user added one. */
    val exampleImage: ByteArray?,
    /** The photo the user just submitted as proof. */
    val submittedImage: ByteArray,
)

data class VerificationResult(val approved: Boolean, val reasoning: String)

/** Something that can decide, from a photo, whether a gated habit was actually done. */
interface ImageVerificationClient {
    suspend fun verify(request: VerificationRequest): VerificationResult
}

sealed class ImageVerificationException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** This month's free checks are used up and the caller isn't premium -- point the user at the paywall, not Settings. See [com.locke.app.data.repository.PreferencesRepository.FREE_VERIFICATIONS_PER_MONTH]. */
    object RequiresPremium : ImageVerificationException("You've used this month's free AI photo checks. Upgrade for unlimited.")
    class Network(message: String, cause: Throwable? = null) : ImageVerificationException(message, cause)
    class Api(message: String) : ImageVerificationException(message)
}
