package com.example.ui.viewmodel

import android.nfc.Tag
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.AiMapsRepository
import com.example.data.firebase.LocalAppPreferences
import com.example.data.firebase.MapsGroundedCareResult
import com.example.data.firebase.MedBandRepository
import com.example.data.firebase.PreferencesRepository
import com.example.data.model.AlertRecord
import com.example.data.model.BleConnectionState
import com.example.data.model.BraceletDevice
import com.example.data.model.EmergencyEvent
import com.example.data.model.LiveMotionPacket
import com.example.data.model.LiveVitalsPacket
import com.example.data.model.MedicalProfile
import com.example.data.model.MovementActivity
import com.example.data.model.NfcTokenRecord
import com.example.data.model.UserAccount
import com.example.data.model.VitalMeasurement
import com.example.hardware.AlertEvaluator
import com.example.hardware.BleProtocol
import com.example.hardware.LocationHelper
import com.example.hardware.MedBandBleManager
import com.example.hardware.NfcMedicalManager
import com.example.hardware.ParsedMedBandNfcToken
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class MedBandViewModel(
    private val currentUserId: String,
    private val currentDisplayName: String?,
    private val currentEmail: String?,
    private val repository: MedBandRepository,
    private val preferencesRepository: PreferencesRepository,
    private val aiMapsRepository: AiMapsRepository,
    val bleManager: MedBandBleManager,
    private val locationHelper: LocationHelper
) : ViewModel() {

    val preferences: StateFlow<LocalAppPreferences> = preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), LocalAppPreferences())

    val userAccountState: StateFlow<UiState<UserAccount?>> = repository.observeUserAccount(currentUserId)
        .toUiStateFlow("Failed to load user account")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val medicalProfileState: StateFlow<UiState<MedicalProfile?>> = repository.observePrimaryMedicalProfile(currentUserId)
        .toUiStateFlow("Failed to load medical profile")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val measurementsState: StateFlow<UiState<List<VitalMeasurement>>> = repository.observeMeasurements(currentUserId)
        .toUiStateFlow("Failed to load measurement history")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val emergenciesState: StateFlow<UiState<List<EmergencyEvent>>> = repository.observeEmergencies(currentUserId)
        .toUiStateFlow("Failed to load emergency events")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val alertsState: StateFlow<UiState<List<AlertRecord>>> = repository.observeAlerts(currentUserId)
        .toUiStateFlow("Failed to load alerts")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val devicesState: StateFlow<UiState<List<BraceletDevice>>> = repository.observeDevices(currentUserId)
        .toUiStateFlow("Failed to load registered bracelets")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val nfcTokensState: StateFlow<UiState<List<NfcTokenRecord>>> = repository.observeNfcTokens(currentUserId)
        .toUiStateFlow("Failed to load NFC tokens")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _mapsCareState = MutableStateFlow<UiState<MapsGroundedCareResult>>(UiState.Idle)
    val mapsCareState: StateFlow<UiState<MapsGroundedCareResult>> = _mapsCareState.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _nfcScanResult = MutableStateFlow<UiState<MedicalProfile?>>(UiState.Idle)
    val nfcScanResult: StateFlow<UiState<MedicalProfile?>> = _nfcScanResult.asStateFlow()

    private val _lastParsedNfcToken = MutableStateFlow<ParsedMedBandNfcToken?>(null)
    val lastParsedNfcToken: StateFlow<ParsedMedBandNfcToken?> = _lastParsedNfcToken.asStateFlow()

    private val _nfcWriteInProgress = MutableStateFlow(false)
    val nfcWriteInProgress: StateFlow<Boolean> = _nfcWriteInProgress.asStateFlow()

    private val _braceletAlertActive = MutableStateFlow(false)
    val braceletAlertActive: StateFlow<Boolean> = _braceletAlertActive.asStateFlow()

    private val _pendingFallCountdownSec = MutableStateFlow<Int?>(null)
    val pendingFallCountdownSec: StateFlow<Int?> = _pendingFallCountdownSec.asStateFlow()

    private var countdownJob: Job? = null
    private var lastPersistedMeasurementMs = 0L
    private val lastAlertCooldowns = mutableMapOf<String, Long>()
    private var previousBleState = BleConnectionState.DISCONNECTED

    init {
        viewModelScope.launch {
            repository.ensureUserAccount(currentUserId, currentDisplayName, currentEmail)
        }
        observeBleTelemetryForPersistenceAndAlerts()
    }

    private fun <T> Flow<T>.toUiStateFlow(fallbackMessage: String): Flow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, fallbackMessage, root)
                emit(UiState.Error(root.localizedMessage ?: fallbackMessage))
            }

    private fun observeBleTelemetryForPersistenceAndAlerts() {
        // Track BLE connection transitions + device registration
        viewModelScope.launch {
            combine(
                bleManager.connectionState,
                bleManager.connectedDevice,
                bleManager.deviceHealth
            ) { state, device, health -> Triple(state, device, health) }
                .collect { (state, device, health) ->
                    if (state == BleConnectionState.CONNECTED && device != null) {
                        preferencesRepository.saveLastDevice(device.address, device.name)
                        repository.upsertBraceletDevice(
                            currentUserId,
                            BraceletDevice(
                                userId = currentUserId,
                                deviceName = device.name,
                                macAddress = device.address,
                                firmwareVersion = health?.firmwareVersion?.ifBlank { "1.0.0" } ?: "1.0.0",
                                batteryPercent = health?.batteryPercent?.toLong()
                            )
                        )
                    } else if (previousBleState == BleConnectionState.CONNECTED &&
                        (state == BleConnectionState.DISCONNECTED || state == BleConnectionState.ERROR)
                    ) {
                        logAlertWithCooldown(
                            category = "BLE_DISCONNECTED",
                            severity = "WARNING",
                            titleEn = "MedBand Disconnected",
                            titleAr = "انقطع الاتصال بالإسورة",
                            messageEn = "BLE link to ESP32-C3 was interrupted.",
                            messageAr = "انقطع اتصال البلوتوث مع إسورة ESP32-C3."
                        )
                    }
                    previousBleState = state
                }
        }

        // Persist valid vitals & evaluate thresholds
        viewModelScope.launch {
            bleManager.liveVitals.collect { vitals ->
                if (vitals == null) return@collect
                val account = (userAccountState.value as? UiState.Success)?.data
                val hrMin = account?.hrMinThreshold ?: 50L
                val hrMax = account?.hrMaxThreshold ?: 130L
                val spo2Min = account?.spo2MinThreshold ?: 92L

                val alerts = AlertEvaluator.evaluateVitals(vitals, hrMin, hrMax, spo2Min)
                alerts.forEach { alert ->
                    logAlertWithCooldown(
                        category = alert.category,
                        severity = alert.severity,
                        titleEn = alert.titleEn,
                        titleAr = alert.titleAr,
                        messageEn = alert.messageEn,
                        messageAr = alert.messageAr
                    )
                    if (alert.triggersPendingEmergency) {
                        triggerEmergency(
                            triggerType = "CRITICAL_VITALS",
                            pendingOnly = true,
                            notes = alert.messageEn
                        )
                    }
                }

                val now = System.currentTimeMillis()
                if (vitals.fingerDetected && vitals.heartRateBpm != null && now - lastPersistedMeasurementMs >= 10_000L) {
                    lastPersistedMeasurementMs = now
                    val motion = bleManager.liveMotion.value
                    repository.recordMeasurement(
                        currentUserId,
                        VitalMeasurement(
                            userId = currentUserId,
                            heartRateBpm = vitals.heartRateBpm.toLong(),
                            spo2Percent = if (vitals.spo2Valid) vitals.spo2Percent?.toLong() else null,
                            spo2Valid = vitals.spo2Valid,
                            sensorQuality = vitals.sensorQuality.name,
                            movementStatus = (motion?.activity ?: MovementActivity.RESTING).name,
                            accelMagnitudeG = motion?.accelMagnitudeG ?: 0.0,
                            gyroMagnitudeDps = motion?.gyroMagnitudeDps ?: 0.0,
                            batteryPercent = vitals.batteryPercent?.toLong(),
                            source = "BLE_ESP32C3"
                        )
                    )
                }
            }
        }

        // Evaluate motion & multi-stage experimental fall detection
        viewModelScope.launch {
            bleManager.liveMotion.collect { motion ->
                if (motion == null) return@collect
                val account = (userAccountState.value as? UiState.Success)?.data
                val fallEnabled = account?.fallDetectionEnabled ?: true
                val alerts = AlertEvaluator.evaluateMotion(motion, fallEnabled)
                alerts.forEach { alert ->
                    logAlertWithCooldown(
                        category = alert.category,
                        severity = alert.severity,
                        titleEn = alert.titleEn,
                        titleAr = alert.titleAr,
                        messageEn = alert.messageEn,
                        messageAr = alert.messageAr
                    )
                    if (alert.triggersPendingEmergency && _pendingFallCountdownSec.value == null) {
                        startFallConfirmationCountdown(motion)
                    }
                }
            }
        }
    }

    private fun startFallConfirmationCountdown(motion: LiveMotionPacket) {
        viewModelScope.launch {
            triggerEmergency(
                triggerType = "POSSIBLE_FALL",
                pendingOnly = true,
                notes = "Experimental multi-stage fall pattern (accel=${String.format(Locale.US, "%.2f", motion.accelMagnitudeG)}g)"
            )
            countdownJob?.cancel()
            countdownJob = viewModelScope.launch {
                for (sec in 20 downTo 1) {
                    _pendingFallCountdownSec.value = sec
                    delay(1000L)
                }
                _pendingFallCountdownSec.value = null
                // Note: Auto-escalates to CONFIRMED emergency record locally & triggers bracelet buzzer,
                // while NEVER claiming SMS delivery without explicit OS dispatch!
                val pending = (emergenciesState.value as? UiState.Success)?.data
                    ?.firstOrNull { it.status == "PENDING_CONFIRMATION" }
                if (pending != null) {
                    confirmEmergency(pending.id)
                }
            }
        }
    }

    private suspend fun logAlertWithCooldown(
        category: String,
        severity: String,
        titleEn: String,
        titleAr: String,
        messageEn: String,
        messageAr: String
    ) {
        val now = System.currentTimeMillis()
        val last = lastAlertCooldowns[category] ?: 0L
        if (now - last < 45_000L) return
        lastAlertCooldowns[category] = now
        val isAr = preferences.value.languageCode == "ar"
        repository.logAlert(
            currentUserId,
            AlertRecord(
                userId = currentUserId,
                category = category,
                severity = severity,
                title = if (isAr) titleAr else titleEn,
                message = if (isAr) messageAr else messageEn,
                acknowledged = false
            )
        )
    }

    // --- Preferences & Language ---

    fun setLanguage(code: String) {
        viewModelScope.launch {
            preferencesRepository.setLanguage(code)
            val acc = (userAccountState.value as? UiState.Success)?.data
            if (acc != null) {
                repository.updateUserSettings(
                    currentUserId,
                    preferredLanguage = code,
                    hrMin = acc.hrMinThreshold,
                    hrMax = acc.hrMaxThreshold,
                    spo2Min = acc.spo2MinThreshold,
                    fallEnabled = acc.fallDetectionEnabled
                )
            }
        }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDarkTheme(enabled) }
    }

    fun setAutoReconnect(enabled: Boolean) {
        bleManager.setAutoReconnect(enabled)
        viewModelScope.launch { preferencesRepository.setAutoReconnect(enabled) }
    }

    fun updateThresholdSettings(hrMin: Long, hrMax: Long, spo2Min: Long, fallEnabled: Boolean, intervalSec: Int) {
        viewModelScope.launch {
            preferencesRepository.setMeasurementIntervalSec(intervalSec)
            if (bleManager.connectionState.value == BleConnectionState.CONNECTED) {
                bleManager.sendCommand(BleProtocol.buildSetIntervalCommand(intervalSec * 1000))
            }
            val result = repository.updateUserSettings(
                currentUserId,
                preferredLanguage = preferences.value.languageCode,
                hrMin = hrMin,
                hrMax = hrMax,
                spo2Min = spo2Min,
                fallEnabled = fallEnabled
            )
            _statusBanner.value = if (result.isSuccess) {
                if (preferences.value.languageCode == "ar") "تم حفظ الإعدادات" else "Settings saved"
            } else {
                result.exceptionOrNull()?.localizedMessage ?: "Failed to update settings"
            }
        }
    }

    fun setFallSensitivity(mode: String) {
        viewModelScope.launch {
            preferencesRepository.setFallSensitivity(mode)
            if (bleManager.connectionState.value == BleConnectionState.CONNECTED) {
                bleManager.sendCommand(BleProtocol.buildSetFallSensitivityCommand(mode))
            }
        }
    }

    // --- Medical Profile ---

    fun saveMedicalProfile(profile: MedicalProfile) {
        viewModelScope.launch {
            val res = repository.saveMedicalProfile(currentUserId, profile)
            _statusBanner.value = if (res.isSuccess) {
                if (preferences.value.languageCode == "ar") "تم حفظ الملف الطبي" else "Medical profile saved"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to save medical profile"
            }
        }
    }

    // --- Emergency Actions ---

    fun triggerEmergency(triggerType: String, pendingOnly: Boolean, notes: String = "") {
        viewModelScope.launch {
            val vitals = bleManager.liveVitals.value
            val loc = locationHelper.getCurrentLocation(preferences.value.languageCode)
            val status = if (pendingOnly) "PENDING_CONFIRMATION" else "CONFIRMED"
            if (!pendingOnly) {
                activateBraceletAlert(true)
            }
            val event = EmergencyEvent(
                userId = currentUserId,
                triggerType = triggerType,
                status = status,
                heartRateAtEvent = vitals?.heartRateBpm?.toLong(),
                spo2AtEvent = if (vitals?.spo2Valid == true) vitals.spo2Percent?.toLong() else null,
                latitude = loc?.latitude,
                longitude = loc?.longitude,
                locationLabel = loc?.label ?: "",
                notificationStatus = "NOT_SENT",
                notes = notes
            )
            val res = repository.createEmergencyEvent(currentUserId, event)
            if (res.isFailure) {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to record emergency"
            }
            if (!pendingOnly) {
                val isAr = preferences.value.languageCode == "ar"
                repository.logAlert(
                    currentUserId,
                    AlertRecord(
                        userId = currentUserId,
                        category = "EMERGENCY",
                        severity = "CRITICAL",
                        title = if (isAr) "تفعيل حالة الطوارئ" else "Emergency Activated",
                        message = if (isAr) "تم تفعيل نداء الطوارئ وتشغيل تنبيه الإسورة." else "SOS triggered and bracelet alert activated.",
                        acknowledged = false
                    )
                )
            }
        }
    }

    fun confirmEmergency(eventId: String) {
        countdownJob?.cancel()
        _pendingFallCountdownSec.value = null
        activateBraceletAlert(true)
        viewModelScope.launch {
            val loc = locationHelper.getCurrentLocation(preferences.value.languageCode)
            val res = repository.updateEmergencyStatus(
                userId = currentUserId,
                eventId = eventId,
                newStatus = "CONFIRMED",
                notificationStatus = "NOT_SENT",
                notes = loc?.label?.let { "Confirmed at $it" }
            )
            if (res.isFailure) {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to confirm emergency"
            }
        }
    }

    fun markEmergencyDispatchedViaOsIntent(eventId: String) {
        viewModelScope.launch {
            repository.updateEmergencyStatus(
                userId = currentUserId,
                eventId = eventId,
                newStatus = "CONFIRMED",
                notificationStatus = "DISPATCHED_INTENT"
            )
        }
    }

    fun cancelEmergency(eventId: String) {
        countdownJob?.cancel()
        _pendingFallCountdownSec.value = null
        activateBraceletAlert(false)
        viewModelScope.launch {
            val res = repository.updateEmergencyStatus(
                userId = currentUserId,
                eventId = eventId,
                newStatus = "CANCELLED",
                notificationStatus = "CANCELLED"
            )
            if (res.isFailure) {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to cancel emergency"
            }
        }
    }

    fun resolveEmergency(eventId: String) {
        countdownJob?.cancel()
        _pendingFallCountdownSec.value = null
        activateBraceletAlert(false)
        viewModelScope.launch {
            repository.updateEmergencyStatus(
                userId = currentUserId,
                eventId = eventId,
                newStatus = "RESOLVED",
                notificationStatus = "NOT_SENT"
            )
        }
    }

    fun activateBraceletAlert(active: Boolean) {
        _braceletAlertActive.value = active
        if (bleManager.connectionState.value == BleConnectionState.CONNECTED) {
            val cmd = if (active) BleProtocol.buildAlertOnCommand() else BleProtocol.buildAlertOffCommand()
            bleManager.sendCommand(cmd)
        }
    }

    // --- Google Maps Grounding Nearby Medical Care ---

    fun searchNearbyMedicalCare() {
        viewModelScope.launch {
            _mapsCareState.value = UiState.Loading
            val lang = preferences.value.languageCode
            val loc = locationHelper.getCurrentLocation(lang)
            val res = aiMapsRepository.findNearbyMedicalFacilities(
                latitude = loc?.latitude,
                longitude = loc?.longitude,
                languageCode = lang
            )
            _mapsCareState.value = res.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.localizedMessage ?: "Failed to query nearby medical centers.") }
            )
        }
    }

    // --- NFC Token Handling ---

    fun setNfcWriteMode(enabled: Boolean) {
        _nfcWriteInProgress.value = enabled
    }

    fun handleNfcTagDiscovered(context: Context, tag: Tag) {
        viewModelScope.launch {
            if (_nfcWriteInProgress.value) {
                val existingProfile = (medicalProfileState.value as? UiState.Success)?.data
                if (existingProfile == null) {
                    _nfcScanResult.value = UiState.Error("Save your medical profile first before writing an NFC token.")
                    _nfcWriteInProgress.value = false
                    return@launch
                }
                val tokenId = existingProfile.nfcTokenId.ifBlank {
                    "tok_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                }
                val writeRes = NfcMedicalManager.writeOpaqueTokenToTag(context, tag, currentUserId, tokenId)
                _nfcWriteInProgress.value = false
                writeRes.fold(
                    onSuccess = { tagUidHex ->
                        repository.registerNfcToken(
                            currentUserId,
                            NfcTokenRecord(
                                userId = currentUserId,
                                tokenId = tokenId,
                                profileId = "primary",
                                tagUidHex = tagUidHex,
                                label = "MedBand Tag ${tagUidHex.takeLast(4)}",
                                active = true
                            )
                        )
                        _nfcScanResult.value = UiState.Success(existingProfile.copy(nfcTokenId = tokenId))
                        _statusBanner.value = if (preferences.value.languageCode == "ar") {
                            "تم ربط البطاقة برمز آمن ($tokenId)"
                        } else {
                            "Tag linked with opaque token ($tokenId)"
                        }
                    },
                    onFailure = { err ->
                        _nfcScanResult.value = UiState.Error(err.localizedMessage ?: "NFC write failed")
                    }
                )
            } else {
                _nfcScanResult.value = UiState.Loading
                val readRes = NfcMedicalManager.readTokenFromTag(tag)
                readRes.fold(
                    onSuccess = { parsed ->
                        handleParsedNfcToken(parsed)
                    },
                    onFailure = { err ->
                        _nfcScanResult.value = UiState.Error(err.localizedMessage ?: "Invalid NFC NDEF format")
                    }
                )
            }
        }
    }

    fun handleNfcUriIntent(uriString: String) {
        val parsed = NfcMedicalManager.parseTokenUri(uriString) ?: run {
            _nfcScanResult.value = UiState.Error("Invalid MedBand NFC URI format.")
            return
        }
        viewModelScope.launch {
            handleParsedNfcToken(parsed)
        }
    }

    private suspend fun handleParsedNfcToken(parsed: ParsedMedBandNfcToken) {
        _lastParsedNfcToken.value = parsed
        val res = repository.getMedicalProfileForAuthorizedToken(parsed.ownerUid, parsed.tokenId)
        _nfcScanResult.value = res.fold(
            onSuccess = { profile ->
                if (profile != null) UiState.Success(profile)
                else UiState.Error("Token not linked or deactivated.")
            },
            onFailure = { err ->
                UiState.Error(err.localizedMessage ?: "Unauthorized NFC token access.")
            }
        )
    }

    // --- Alerts & History Export ---

    fun acknowledgeAlert(alertId: String) {
        viewModelScope.launch { repository.acknowledgeAlert(currentUserId, alertId) }
    }

    fun acknowledgeAllAlerts() {
        val unreadIds = (alertsState.value as? UiState.Success)?.data
            ?.filter { !it.acknowledged && it.id.isNotBlank() }
            ?.map { it.id }
            .orEmpty()
        if (unreadIds.isEmpty()) return
        viewModelScope.launch { repository.acknowledgeAllAlerts(currentUserId, unreadIds) }
    }

    fun clearMeasurementHistory() {
        viewModelScope.launch {
            val res = repository.clearMeasurements(currentUserId)
            _statusBanner.value = if (res.isSuccess) {
                if (preferences.value.languageCode == "ar") "تم مسح سجل القراءات" else "History cleared"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to clear history"
            }
        }
    }

    fun buildExportCsvReport(): String {
        val rows = (measurementsState.value as? UiState.Success)?.data.orEmpty()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder("timestamp,heart_rate_bpm,spo2_percent,spo2_valid,sensor_quality,movement_status,accel_g,gyro_dps,battery\n")
        rows.forEach { m ->
            val ts = m.recordedAt?.toDate()?.let { fmt.format(it) } ?: "pending"
            sb.append("$ts,${m.heartRateBpm},${m.spo2Percent ?: ""},${m.spo2Valid},${m.sensorQuality},${m.movementStatus},${String.format(Locale.US, "%.2f", m.accelMagnitudeG)},${String.format(Locale.US, "%.1f", m.gyroMagnitudeDps)},${m.batteryPercent ?: ""}\n")
        }
        return sb.toString()
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }

    companion object {
        private const val TAG = "MedBandVM"
    }
}
