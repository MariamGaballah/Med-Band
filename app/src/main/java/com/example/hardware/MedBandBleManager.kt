package com.example.hardware

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.BleConnectionState
import com.example.data.model.DeviceHealthPacket
import com.example.data.model.DiscoveredBleDevice
import com.example.data.model.LiveMotionPacket
import com.example.data.model.LiveVitalsPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.ArrayDeque

class MedBandBleManager(private val context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private var activeGatt: BluetoothGatt? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private val descriptorQueue = ArrayDeque<BluetoothGattCharacteristic>()
    private var userInitiatedDisconnect = false
    private var autoReconnectEnabled = true
    private var reconnectAttempt = 0
    private var reconnectJob: Job? = null
    private var scanTimeoutJob: Job? = null
    private var lastDeviceAddress: String? = null

    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredBleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredBleDevice>> = _discoveredDevices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<DiscoveredBleDevice?>(null)
    val connectedDevice: StateFlow<DiscoveredBleDevice?> = _connectedDevice.asStateFlow()

    private val _rssi = MutableStateFlow<Int?>(null)
    val rssi: StateFlow<Int?> = _rssi.asStateFlow()

    private val _liveVitals = MutableStateFlow<LiveVitalsPacket?>(null)
    val liveVitals: StateFlow<LiveVitalsPacket?> = _liveVitals.asStateFlow()

    private val _liveMotion = MutableStateFlow<LiveMotionPacket?>(null)
    val liveMotion: StateFlow<LiveMotionPacket?> = _liveMotion.asStateFlow()

    private val _deviceHealth = MutableStateFlow<DeviceHealthPacket?>(null)
    val deviceHealth: StateFlow<DeviceHealthPacket?> = _deviceHealth.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _lastSyncMillis = MutableStateFlow<Long?>(null)
    val lastSyncMillis: StateFlow<Long?> = _lastSyncMillis.asStateFlow()

    fun hasBlePermissions(): Boolean {
        return requiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(appContext, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requiredPermissions(): List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun setAutoReconnect(enabled: Boolean) {
        autoReconnectEnabled = enabled
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val advName = result.scanRecord?.deviceName
            val rawName = advName ?: try { device.name } catch (_: SecurityException) { null } ?: "BLE Device"
            val entry = DiscoveredBleDevice(
                name = rawName,
                address = device.address,
                rssi = result.rssi
            )
            val current = _discoveredDevices.value.toMutableList()
            val existingIdx = current.indexOfFirst { it.address == entry.address }
            if (existingIdx >= 0) {
                current[existingIdx] = entry
            } else {
                current.add(entry)
            }
            _discoveredDevices.value = current.sortedByDescending {
                val isMedBand = it.name.contains(BleProtocol.DEVICE_NAME_PREFIX, ignoreCase = true)
                (if (isMedBand) 1000 else 0) + it.rssi
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with errorCode=$errorCode")
            _lastError.value = "BLE scan failed (code $errorCode)"
            if (_connectionState.value == BleConnectionState.SCANNING) {
                _connectionState.value = BleConnectionState.DISCONNECTED
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasBlePermissions()) {
            _lastError.value = "Bluetooth permissions missing"
            return
        }
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _lastError.value = "Bluetooth is disabled"
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _lastError.value = "BLE scanner unavailable on this hardware"
            return
        }
        _lastError.value = null
        _discoveredDevices.value = emptyList()
        _connectionState.value = BleConnectionState.SCANNING

        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(BleProtocol.SERVICE_UUID)).build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Scan broadly first so MedBand prototypes advertising name without UUID in primary packet also appear
        try {
            scanner.startScan(null, settings, scanCallback)
            scanTimeoutJob?.cancel()
            scanTimeoutJob = scope.launch {
                delay(12_000L)
                stopScan()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BLE scan", e)
            _lastError.value = e.localizedMessage ?: "Failed to start BLE scan"
            _connectionState.value = BleConnectionState.ERROR
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        if (!hasBlePermissions()) return
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "stopScan ignored: ${e.message}")
        }
        if (_connectionState.value == BleConnectionState.SCANNING) {
            _connectionState.value = if (_connectedDevice.value != null) {
                BleConnectionState.CONNECTED
            } else {
                BleConnectionState.DISCONNECTED
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(address: String, deviceNameHint: String? = null) {
        if (!hasBlePermissions()) {
            _lastError.value = "Bluetooth permissions required"
            return
        }
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _lastError.value = "Bluetooth is disabled"
            return
        }
        stopScan()
        reconnectJob?.cancel()
        userInitiatedDisconnect = false
        lastDeviceAddress = address
        _lastError.value = null
        _connectionState.value = BleConnectionState.CONNECTING

        try {
            activeGatt?.disconnect()
            activeGatt?.close()
            activeGatt = null

            val remoteDevice: BluetoothDevice = adapter.getRemoteDevice(address)
            val resolvedName = deviceNameHint ?: try { remoteDevice.name } catch (_: SecurityException) { null } ?: "MedBand ESP32-C3"
            _connectedDevice.value = DiscoveredBleDevice(resolvedName, address, _rssi.value ?: -60)

            activeGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                remoteDevice.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                remoteDevice.connectGatt(appContext, false, gattCallback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "connectToDevice failed", e)
            _lastError.value = e.localizedMessage ?: "Failed to connect to bracelet"
            _connectionState.value = BleConnectionState.ERROR
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        userInitiatedDisconnect = true
        reconnectJob?.cancel()
        reconnectAttempt = 0
        try {
            activeGatt?.disconnect()
            activeGatt?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing GATT: ${e.message}")
        }
        activeGatt = null
        commandCharacteristic = null
        descriptorQueue.clear()
        _connectedDevice.value = null
        _liveVitals.value = null
        _liveMotion.value = null
        _deviceHealth.value = null
        _rssi.value = null
        _connectionState.value = BleConnectionState.DISCONNECTED
    }

    @SuppressLint("MissingPermission")
    fun sendCommand(jsonCommand: String): Boolean {
        val gatt = activeGatt ?: return false
        val char = commandCharacteristic ?: return false
        if (!hasBlePermissions()) return false
        val payload = jsonCommand.toByteArray(Charsets.UTF_8)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val res = gatt.writeCharacteristic(char, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                res == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                char.value = payload
                @Suppress("DEPRECATION")
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(char)
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendCommand failed", e)
            _lastError.value = e.localizedMessage ?: "Failed to send command"
            false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                reconnectAttempt = 0
                _connectionState.value = BleConnectionState.DISCOVERING_SERVICES
                if (!gatt.requestMtu(247)) {
                    gatt.discoverServices()
                }
                gatt.readRemoteRssi()
            } else {
                Log.w(TAG, "BLE disconnected status=$status newState=$newState")
                commandCharacteristic = null
                descriptorQueue.clear()
                _liveVitals.value = null
                _liveMotion.value = null
                _deviceHealth.value = null
                try {
                    gatt.close()
                } catch (_: Exception) {
                }
                if (activeGatt == gatt) activeGatt = null

                if (!userInitiatedDisconnect && autoReconnectEnabled && !lastDeviceAddress.isNullOrBlank()) {
                    _connectionState.value = BleConnectionState.CONNECTING
                    scheduleReconnect()
                } else {
                    _connectionState.value = if (status != BluetoothGatt.GATT_SUCCESS && !userInitiatedDisconnect) {
                        _lastError.value = "BLE link error (GATT status $status)"
                        BleConnectionState.ERROR
                    } else {
                        BleConnectionState.DISCONNECTED
                    }
                    _connectedDevice.value = null
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "MTU negotiated=$mtu status=$status")
            gatt.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _lastError.value = "Service discovery failed ($status)"
                _connectionState.value = BleConnectionState.ERROR
                return
            }
            val service = gatt.getService(BleProtocol.SERVICE_UUID)
            if (service == null) {
                _lastError.value = "MedBand GATT service not found on device"
                _connectionState.value = BleConnectionState.ERROR
                return
            }
            commandCharacteristic = service.getCharacteristic(BleProtocol.CHAR_COMMAND_UUID)
            descriptorQueue.clear()
            listOfNotNull(
                service.getCharacteristic(BleProtocol.CHAR_VITALS_UUID),
                service.getCharacteristic(BleProtocol.CHAR_MOTION_UUID),
                service.getCharacteristic(BleProtocol.CHAR_STATUS_UUID)
            ).forEach { descriptorQueue.add(it) }

            _connectionState.value = BleConnectionState.CONNECTED
            _lastSyncMillis.value = System.currentTimeMillis()
            subscribeNextCharacteristic(gatt)
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            subscribeNextCharacteristic(gatt)
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _rssi.value = rssi
                _connectedDevice.value = _connectedDevice.value?.copy(rssi = rssi)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val bytes = characteristic.value ?: return
            handleIncomingCharacteristic(characteristic.uuid.toString(), bytes)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingCharacteristic(characteristic.uuid.toString(), value)
        }
    }

    @SuppressLint("MissingPermission")
    private fun subscribeNextCharacteristic(gatt: BluetoothGatt) {
        val next = descriptorQueue.poll() ?: return
        gatt.setCharacteristicNotification(next, true)
        val cccd = next.getDescriptor(BleProtocol.CCCD_UUID) ?: run {
            subscribeNextCharacteristic(gatt)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(cccd)
        }
    }

    private fun handleIncomingCharacteristic(uuidString: String, bytes: ByteArray) {
        val payload = bytes.toString(Charsets.UTF_8).trim()
        val now = System.currentTimeMillis()
        _lastSyncMillis.value = now
        when {
            uuidString.equals(BleProtocol.CHAR_VITALS_UUID.toString(), ignoreCase = true) -> {
                BleProtocol.parseVitalsJson(payload, now)?.let { _liveVitals.value = it }
            }
            uuidString.equals(BleProtocol.CHAR_MOTION_UUID.toString(), ignoreCase = true) -> {
                BleProtocol.parseMotionJson(payload, now)?.let { _liveMotion.value = it }
            }
            uuidString.equals(BleProtocol.CHAR_STATUS_UUID.toString(), ignoreCase = true) -> {
                BleProtocol.parseStatusJson(payload)?.let { _deviceHealth.value = it }
            }
        }
    }

    private fun scheduleReconnect() {
        val addr = lastDeviceAddress ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val backoffMs = (2000L * (1 shl reconnectAttempt.coerceAtMost(4))).coerceAtMost(30_000L)
            reconnectAttempt++
            delay(backoffMs)
            if (!userInitiatedDisconnect && autoReconnectEnabled) {
                connectToDevice(addr, _connectedDevice.value?.name)
            }
        }
    }

    companion object {
        private const val TAG = "MedBandBleManager"
    }
}
