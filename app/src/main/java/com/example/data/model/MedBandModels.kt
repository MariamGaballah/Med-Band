package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val preferredLanguage: String = "ar",
    val hrMinThreshold: Long = 50L,
    val hrMaxThreshold: Long = 130L,
    val spo2MinThreshold: Long = 92L,
    val fallDetectionEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        val base = mutableMapOf<String, Any?>(
            "userId" to userId,
            "displayName" to displayName.take(100).ifBlank { "MedBand User" },
            "email" to email?.take(120),
            "preferredLanguage" to if (preferredLanguage == "en") "en" else "ar",
            "hrMinThreshold" to hrMinThreshold.coerceIn(30L, 120L),
            "hrMaxThreshold" to hrMaxThreshold.coerceIn(80L, 220L),
            "spo2MinThreshold" to spo2MinThreshold.coerceIn(70L, 98L),
            "fallDetectionEnabled" to fallDetectionEnabled,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return base.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class MedicalProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val emergencyContactName: String = "",
    val emergencyContactPhone: String = "",
    val bloodGroup: String = "UNKNOWN",
    val allergies: String = "",
    val chronicDiseases: String = "",
    val currentMedications: String = "",
    val medicalNotes: String = "",
    val nfcTokenId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "fullName" to fullName.trim().take(100),
        "emergencyContactName" to emergencyContactName.trim().take(100),
        "emergencyContactPhone" to emergencyContactPhone.trim().take(30),
        "bloodGroup" to (bloodGroup.takeIf { it in ALLOWED_BLOOD_GROUPS } ?: "UNKNOWN"),
        "allergies" to allergies.trim().take(500),
        "chronicDiseases" to chronicDiseases.trim().take(500),
        "currentMedications" to currentMedications.trim().take(500),
        "medicalNotes" to medicalNotes.trim().take(1000),
        "nfcTokenId" to nfcTokenId.trim().take(128),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "fullName" to fullName.trim().take(100),
        "emergencyContactName" to emergencyContactName.trim().take(100),
        "emergencyContactPhone" to emergencyContactPhone.trim().take(30),
        "bloodGroup" to (bloodGroup.takeIf { it in ALLOWED_BLOOD_GROUPS } ?: "UNKNOWN"),
        "allergies" to allergies.trim().take(500),
        "chronicDiseases" to chronicDiseases.trim().take(500),
        "currentMedications" to currentMedications.trim().take(500),
        "medicalNotes" to medicalNotes.trim().take(1000),
        "nfcTokenId" to nfcTokenId.trim().take(128),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        val ALLOWED_BLOOD_GROUPS = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-", "UNKNOWN")
    }
}

@Keep
@IgnoreExtraProperties
data class VitalMeasurement(
    @DocumentId val id: String = "",
    val userId: String = "",
    val heartRateBpm: Long = 0L,
    val spo2Percent: Long? = null,
    val spo2Valid: Boolean = false,
    val sensorQuality: String = "UNAVAILABLE",
    val movementStatus: String = "RESTING",
    val accelMagnitudeG: Double = 0.0,
    val gyroMagnitudeDps: Double = 0.0,
    val batteryPercent: Long? = null,
    val source: String = "BLE_ESP32C3",
    @ServerTimestamp val recordedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any?>(
            "userId" to userId,
            "heartRateBpm" to heartRateBpm.coerceIn(0L, 260L),
            "spo2Percent" to if (spo2Valid && spo2Percent != null) spo2Percent.coerceIn(50L, 100L) else null,
            "spo2Valid" to spo2Valid,
            "sensorQuality" to sensorQuality,
            "movementStatus" to movementStatus,
            "accelMagnitudeG" to accelMagnitudeG.coerceIn(0.0, 32.0),
            "gyroMagnitudeDps" to gyroMagnitudeDps.coerceIn(0.0, 2500.0),
            "batteryPercent" to batteryPercent?.coerceIn(0L, 100L),
            "source" to source.take(40),
            "recordedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return map.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class EmergencyEvent(
    @DocumentId val id: String = "",
    val userId: String = "",
    val triggerType: String = "SOS_MANUAL",
    val status: String = "PENDING_CONFIRMATION",
    val heartRateAtEvent: Long? = null,
    val spo2AtEvent: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationLabel: String = "",
    val notificationStatus: String = "NOT_SENT",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any?>(
            "userId" to userId,
            "triggerType" to triggerType,
            "status" to status,
            "heartRateAtEvent" to heartRateAtEvent?.coerceIn(0L, 260L),
            "spo2AtEvent" to spo2AtEvent?.coerceIn(50L, 100L),
            "latitude" to latitude?.coerceIn(-90.0, 90.0),
            "longitude" to longitude?.coerceIn(-180.0, 180.0),
            "locationLabel" to locationLabel.take(200),
            "notificationStatus" to notificationStatus,
            "notes" to notes.take(500),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return map.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class AlertRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = "SENSOR_QUALITY",
    val severity: String = "INFO",
    val title: String = "",
    val message: String = "",
    val acknowledged: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "category" to category,
        "severity" to severity,
        "title" to title.take(120).ifBlank { "Alert" },
        "message" to message.take(500).ifBlank { "Alert event" },
        "acknowledged" to acknowledged,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class BraceletDevice(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deviceName: String = "",
    val macAddress: String = "",
    val firmwareVersion: String = "1.0.0",
    val batteryPercent: Long? = null,
    val lastSyncAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any?>(
            "userId" to userId,
            "deviceName" to deviceName.take(80).ifBlank { "MedBand-ESP32C3" },
            "macAddress" to macAddress.take(64).ifBlank { "UNKNOWN" },
            "firmwareVersion" to firmwareVersion.take(32),
            "batteryPercent" to batteryPercent?.coerceIn(0L, 100L),
            "lastSyncAt" to FieldValue.serverTimestamp(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return map.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class NfcTokenRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val tokenId: String = "",
    val profileId: String = "primary",
    val tagUidHex: String = "",
    val label: String = "",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "tokenId" to tokenId.take(128),
        "profileId" to profileId.take(64),
        "tagUidHex" to tagUidHex.take(64),
        "label" to label.take(80).ifBlank { "MedBand Tag" },
        "active" to active,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
