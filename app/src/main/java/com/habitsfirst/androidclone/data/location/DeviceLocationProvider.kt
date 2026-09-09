package com.habitsfirst.androidclone.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Thin wrapper around [LocationManager] for [com.habitsfirst.androidclone.domain.model.HabitType.VISIT_LOCATION]
 * habits (see [com.habitsfirst.androidclone.service.LocationSyncWorker]).
 *
 * Deliberately reads only the OS's already-cached last-known location for every
 * provider, never [LocationManager.requestLocationUpdates] or a fresh GPS fix: Android's
 * background-location restrictions (Android 10+) gate an app *requesting new location
 * updates* while backgrounded behind the separate, Play-Store-scrutinized "Allow all the
 * time" permission -- but reading a location the OS already has cached only ever needs
 * the plain foreground [Manifest.permission.ACCESS_FINE_LOCATION]/`ACCESS_COARSE_LOCATION`
 * permission, the same one a periodic background worker can freely call with. That
 * mirrors [com.habitsfirst.androidclone.data.healthconnect.HealthConnectManager]'s own
 * "read once per periodic tick, never a continuous stream" shape.
 */
@Singleton
class DeviceLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasPermission(): Boolean = PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** The freshest cached fix across every registered provider, or null if permission isn't granted or none has one yet. */
    fun lastKnownLocation(): Location? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return runCatching {
            manager.allProviders.mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }.maxByOrNull { it.time }
        }.getOrNull()
    }

    /**
     * A live, best-effort fix for interactive setup (the add/edit habit form's "Use my
     * current location" button) -- always triggered from the foreground by a direct tap,
     * so unlike [lastKnownLocation] it never touches Android's background-location
     * restrictions either. [LocationManager.getCurrentLocation] is Android 11+ (API 30)
     * only, so this falls back to [lastKnownLocation] on older devices, and also if no
     * provider is enabled or nothing responds within [timeoutMillis].
     */
    suspend fun requestCurrentLocation(timeoutMillis: Long = 15_000): Location? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return lastKnownLocation()

        val provider = when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return lastKnownLocation()
        }

        val liveFix = withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val cancellationSignal = CancellationSignal()
                continuation.invokeOnCancellation { cancellationSignal.cancel() }
                try {
                    manager.getCurrentLocation(provider, cancellationSignal, context.mainExecutor) { location ->
                        if (continuation.isActive) continuation.resume(location)
                    }
                } catch (e: SecurityException) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
        return liveFix ?: lastKnownLocation()
    }

    companion object {
        val PERMISSIONS: Array<String> = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
}
