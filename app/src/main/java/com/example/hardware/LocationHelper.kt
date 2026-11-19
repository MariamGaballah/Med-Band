package com.example.hardware

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

data class ResolvedLocation(
    val latitude: Double,
    val longitude: Double,
    val label: String
)

class LocationHelper(private val context: Context) {
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(languageCode: String): ResolvedLocation? {
        if (!hasLocationPermission()) return null
        return try {
            val cts = CancellationTokenSource()
            val location = fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token).await()
                ?: fusedClient.lastLocation.await()
                ?: return null
            val label = reverseGeocodeLabel(location.latitude, location.longitude, languageCode)
            ResolvedLocation(location.latitude, location.longitude, label)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun reverseGeocodeLabel(lat: Double, lng: Double, languageCode: String): String =
        withContext(Dispatchers.IO) {
            val fallback = "%.4f, %.4f".format(Locale.US, lat, lng)
            try {
                if (!Geocoder.isPresent()) return@withContext fallback
                val locale = if (languageCode == "ar") Locale("ar") else Locale.ENGLISH
                val geocoder = Geocoder(context, locale)
                @Suppress("DEPRECATION")
                val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(lat, lng, 1)
                } else {
                    geocoder.getFromLocation(lat, lng, 1)
                }
                val addr = addresses?.firstOrNull() ?: return@withContext fallback
                listOfNotNull(addr.subLocality, addr.locality, addr.countryName)
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                    .ifBlank { fallback }
            } catch (_: Exception) {
                fallback
            }
        }
}
