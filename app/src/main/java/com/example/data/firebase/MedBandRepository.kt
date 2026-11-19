package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.AlertRecord
import com.example.data.model.BraceletDevice
import com.example.data.model.EmergencyEvent
import com.example.data.model.MedicalProfile
import com.example.data.model.NfcTokenRecord
import com.example.data.model.UserAccount
import com.example.data.model.VitalMeasurement
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class MedBandRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun <T> Flow<T>.wrapFirestoreStream(path: String, uid: String): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
        }
        throw error
    }

    // --- User Account & Threshold Settings ---

    fun observeUserAccount(userId: String): Flow<UserAccount?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .wrapFirestoreStream(path, userId)
        )
    }

    suspend fun ensureUserAccount(userId: String, displayName: String?, email: String?): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            val docRef = db.collection("users").document(userId)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val account = UserAccount(
                    userId = userId,
                    displayName = displayName?.take(100)?.ifBlank { "MedBand User" } ?: "MedBand User",
                    email = email?.take(120)
                )
                docRef.set(account.toCreateMap()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserSettings(
        userId: String,
        preferredLanguage: String,
        hrMin: Long,
        hrMax: Long,
        spo2Min: Long,
        fallEnabled: Boolean
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            val safeMin = hrMin.coerceIn(30L, 120L)
            val safeMax = hrMax.coerceIn((safeMin + 1L).coerceAtLeast(80L), 220L)
            val safeSpo2 = spo2Min.coerceIn(70L, 98L)
            db.collection("users").document(userId).update(
                mapOf(
                    "preferredLanguage" to if (preferredLanguage == "en") "en" else "ar",
                    "hrMinThreshold" to safeMin,
                    "hrMaxThreshold" to safeMax,
                    "spo2MinThreshold" to safeSpo2,
                    "fallDetectionEnabled" to fallEnabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Medical Profile ---

    fun observePrimaryMedicalProfile(userId: String): Flow<MedicalProfile?> = flow {
        val path = "users/$userId/medical_profiles/primary"
        emitAll(
            db.collection("users").document(userId)
                .collection("medical_profiles").document("primary")
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(MedicalProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .wrapFirestoreStream(path, userId)
        )
    }

    suspend fun saveMedicalProfile(userId: String, profile: MedicalProfile): Result<String> {
        val path = "users/$userId/medical_profiles/primary"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            require(profile.fullName.isNotBlank()) { "Full name is required." }
            require(profile.emergencyContactName.isNotBlank()) { "Emergency contact name is required." }
            require(profile.emergencyContactPhone.trim().length >= 3) { "Emergency contact phone is required." }

            val docRef = db.collection("users").document(userId)
                .collection("medical_profiles").document("primary")
            val existing = docRef.get().await()
            val token = profile.nfcTokenId.ifBlank {
                existing.getString("nfcTokenId")?.takeIf { it.isNotBlank() }
                    ?: "tok_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            }
            val clean = profile.copy(userId = userId, nfcTokenId = token)
            if (existing.exists()) {
                docRef.update(clean.toUpdateMap()).await()
            } else {
                docRef.set(clean.toCreateMap()).await()
            }
            Result.success(token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- Measurements History ---

    fun observeMeasurements(userId: String, limitCount: Long = 200L): Flow<List<VitalMeasurement>> = flow {
        val path = "users/$userId/measurements"
        emitAll(
            db.collection("users").document(userId)
                .collection("measurements")
                .orderBy("recordedAt", Query.Direction.DESCENDING)
                .limit(limitCount)
                .snapshots()
                .map { snap ->
                    snap.toObjects(VitalMeasurement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .wrapFirestoreStream(path, userId)
        )
    }

    suspend fun recordMeasurement(userId: String, measurement: VitalMeasurement): Result<Unit> {
        val path = "users/$userId/measurements"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            val docRef = db.collection("users").document(userId)
                .collection("measurements").document()
            docRef.set(measurement.copy(userId = userId).toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun clearMeasurements(userId: String): Result<Unit> {
        val path = "users/$userId/measurements"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            val docs = db.collection("users").document(userId)
                .collection("measurements").limit(200).get().await()
            val batch = db.batch()
            docs.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Emergency Events ---

    fun observeEmergencies(userId: String): Flow<List<EmergencyEvent>> = flow {
        val path = "users/$userId/emergencies"
        emitAll(
            db.collection("users").document(userId)
                .collection("emergencies")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .snapshots()
                .map { snap ->
                    snap.toObjects(EmergencyEvent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .wrapFirestoreStream(path, userId)
        )
    }

    suspend fun createEmergencyEvent(userId: String, event: EmergencyEvent): Result<String> {
        val path = "users/$userId/emergencies"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            val docRef = db.collection("users").document(userId)
                .collection("emergencies").document()
            docRef.set(event.copy(userId = userId).toCreateMap()).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateEmergencyStatus(
        userId: String,
        eventId: String,
        newStatus: String,
        notificationStatus: String,
        notes: String? = null
    ): Result<Unit> {
        val path = "users/$userId/emergencies/$eventId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            require(eventId.isNotBlank())
            val updates = mutableMapOf<String, Any>(
                "status" to newStatus,
                "notificationStatus" to notificationStatus,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (notes != null) updates["notes"] = notes.take(500)
            db.collection("users").document(userId)
                .collection("emergencies").document(eventId)
                .update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Alerts & Notifications ---

    fun observeAlerts(userId: String): Flow<List<AlertRecord>> = flow {
        val path = "users/$userId/alerts"
        emitAll(
            db.collection("users").document(userId)
                .collection("alerts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .snapshots()
                .map { snap ->
                    snap.toObjects(AlertRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .wrapFirestoreStream(path, userId)
        )
    }

    suspend fun logAlert(userId: String, alert: AlertRecord): Result<Unit> {
        val path = "users/$userId/alerts"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            val docRef = db.collection("users").document(userId)
                .collection("alerts").document()
            docRef.set(alert.copy(userId = userId).toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun acknowledgeAlert(userId: String, alertId: String): Result<Unit> {
        val path = "users/$userId/alerts/$alertId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            db.collection("users").document(userId)
                .collection("alerts").document(alertId)
                .update(
                    mapOf(
                        "acknowledged" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun acknowledgeAllAlerts(userId: String, unreadIds: List<String>): Result<Unit> {
        val path = "users/$userId/alerts"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            if (unreadIds.isEmpty()) return Result.success(Unit)
            val batch = db.batch()
            unreadIds.take(100).forEach { id ->
                val ref = db.collection("users").document(userId).collection("alerts").document(id)
                batch.update(ref, mapOf("acknowledged" to true, "updatedAt" to FieldValue.serverTimestamp()))
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Bracelet Devices ---

    fun observeDevices(userId: String): Flow<List<BraceletDevice>> = flow {
        val path = "users/$userId/devices"
        emitAll(
            db.collection("users").document(userId)
                .collection("devices")
                .snapshots()
                .map { snap ->
                    snap.toObjects(BraceletDevice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .wrapFirestoreStream(path, userId)
        )
    }

    suspend fun upsertBraceletDevice(userId: String, device: BraceletDevice): Result<Unit> {
        val safeDocId = device.macAddress.replace(":", "_").replace(Regex("[^a-zA-Z0-9_\\-]"), "").ifBlank { "medband_primary" }
        val path = "users/$userId/devices/$safeDocId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            val docRef = db.collection("users").document(userId).collection("devices").document(safeDocId)
            val snap = docRef.get().await()
            if (snap.exists()) {
                val updates = mutableMapOf<String, Any>(
                    "deviceName" to device.deviceName.take(80).ifBlank { "MedBand-ESP32C3" },
                    "firmwareVersion" to device.firmwareVersion.take(32),
                    "lastSyncAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                device.batteryPercent?.let { updates["batteryPercent"] = it.coerceIn(0L, 100L) }
                docRef.update(updates).await()
            } else {
                docRef.set(device.copy(userId = userId).toCreateMap()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- NFC Tokens ---

    fun observeNfcTokens(userId: String): Flow<List<NfcTokenRecord>> = flow {
        val path = "users/$userId/nfc_tokens"
        emitAll(
            db.collection("users").document(userId)
                .collection("nfc_tokens")
                .snapshots()
                .map { snap ->
                    snap.toObjects(NfcTokenRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .wrapFirestoreStream(path, userId)
        )
    }

    suspend fun registerNfcToken(userId: String, tokenRecord: NfcTokenRecord): Result<Unit> {
        val safeTokenId = tokenRecord.tokenId.replace(Regex("[^a-zA-Z0-9_\\-]"), "")
        val path = "users/$userId/nfc_tokens/$safeTokenId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            require(safeTokenId.isNotBlank()) { "NFC token ID cannot be blank" }
            val docRef = db.collection("users").document(userId)
                .collection("nfc_tokens").document(safeTokenId)
            val existing = docRef.get().await()
            val clean = tokenRecord.copy(userId = userId, tokenId = safeTokenId)
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "profileId" to clean.profileId,
                        "tagUidHex" to clean.tagUidHex,
                        "label" to clean.label,
                        "active" to clean.active,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(clean.toCreateMap()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun getMedicalProfileForAuthorizedToken(userId: String, tokenId: String): Result<MedicalProfile?> {
        val path = "users/$userId/nfc_tokens/$tokenId"
        return try {
            val uid = requireUserId()
            if (uid != userId) {
                return Result.failure(SecurityException("Token belongs to another account. Authorized sign-in required."))
            }
            val tokenDoc = db.collection("users").document(userId)
                .collection("nfc_tokens").document(tokenId).get().await()
            val tokenRecord = tokenDoc.toObject(NfcTokenRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val profileId = tokenRecord?.takeIf { it.active }?.profileId ?: "primary"
            val profileDoc = db.collection("users").document(userId)
                .collection("medical_profiles").document(profileId).get().await()
            val profile = profileDoc.toObject(MedicalProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            if (profile != null && (profile.nfcTokenId == tokenId || tokenRecord?.active == true)) {
                Result.success(profile)
            } else {
                Result.success(null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }
}
