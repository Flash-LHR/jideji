package com.hackerli.jizhang.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

class ForegroundLocationProvider(private val context: Context) {
    private val locationManager = context.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission")
    suspend fun locate(): Result<LocationSnapshot> = runCatching {
        val cached = newestRecentLocation()
        val location = cached ?: requestFromAvailableProviders()
            ?: error("暂时无法获取位置，请靠近窗边后重试")
        LocationSnapshot(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy,
            label = withTimeoutOrNull(3_000L) { reverseGeocode(location) } ?: "位置已记录",
        )
    }.onFailure { if (it is CancellationException) throw it }

    private suspend fun requestFromAvailableProviders(): Location? {
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { locationManager.isProviderEnabled(it) }
        if (providers.isEmpty()) error("请打开手机定位服务")
        providers.forEach { provider ->
            val location = withTimeoutOrNull(8_000L) { requestCurrentLocation(provider) }
            if (location != null) return location
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun newestRecentLocation(): Location? {
        val cutoff = System.currentTimeMillis() - 2 * 60_000L
        return locationManager.getProviders(true)
            .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
            .filter { it.time >= cutoff }
            .maxWithOrNull(compareBy<Location> { it.time }.thenBy { -it.accuracy })
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private suspend fun requestCurrentLocation(provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val cancellationSignal = CancellationSignal()
                continuation.invokeOnCancellation { cancellationSignal.cancel() }
                locationManager.getCurrentLocation(
                    provider,
                    cancellationSignal,
                    context.mainExecutor,
                ) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        locationManager.removeUpdates(this)
                        if (continuation.isActive) continuation.resume(location)
                    }

                    override fun onProviderDisabled(provider: String) {
                        locationManager.removeUpdates(this)
                        if (continuation.isActive) continuation.resume(null)
                    }

                    @Deprecated("Deprecated in Android")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                }
                continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }
                locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocode(location: Location): String = withContext(Dispatchers.IO) {
        val fallback = "位置已记录"
        if (!Geocoder.isPresent()) return@withContext fallback
        val address = runCatching {
            Geocoder(context, Locale.SIMPLIFIED_CHINESE)
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
        }.getOrNull() ?: return@withContext fallback
        address.readableLabel().ifBlank { fallback }
    }
}

private fun Address.readableLabel(): String {
    val area = subLocality ?: subAdminArea ?: locality ?: adminArea
    return listOfNotNull(area, thoroughfare).distinct().joinToString(" · ")
}
