#include "ble_manager.h"
#include "config.h"
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

static BLEServer* pServer = nullptr;
static BLECharacteristic* pVitalsChar = nullptr;
static BLECharacteristic* pMotionChar = nullptr;
static BLECharacteristic* pStatusChar = nullptr;
static BLECharacteristic* pCommandChar = nullptr;
static bool* pConnectedFlag = nullptr;
static BleManager::CommandCallback* pCmdCb = nullptr;

class MedBandServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer* server) override {
        if (pConnectedFlag) *pConnectedFlag = true;
    }
    void onDisconnect(BLEServer* server) override {
        if (pConnectedFlag) *pConnectedFlag = false;
    }
};

class MedBandCommandCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* characteristic) override {
        String value = characteristic->getValue().c_str();
        if (value.length() > 0 && pCmdCb && *pCmdCb) {
            (*pCmdCb)(value);
        }
    }
};

void BleManager::begin(CommandCallback onCommand) {
    _onCommand = onCommand;
    pConnectedFlag = &_deviceConnected;
    pCmdCb = &_onCommand;

    uint64_t chipMac = ESP.getEfuseMac();
    char suffix[8];
    snprintf(suffix, sizeof(suffix), "%04X", (uint16_t)(chipMac & 0xFFFF));
    String advName = String(MEDBAND_DEVICE_NAME_PREFIX) + "-" + String(suffix);

    BLEDevice::init(advName.c_str());
    BLEDevice::setMTU(247);

    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new MedBandServerCallbacks());

    BLEService* pService = pServer->createService(MEDBAND_SERVICE_UUID);

    pVitalsChar = pService->createCharacteristic(
        MEDBAND_CHAR_VITALS_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
    );
    pVitalsChar->addDescriptor(new BLE2902());

    pMotionChar = pService->createCharacteristic(
        MEDBAND_CHAR_MOTION_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
    );
    pMotionChar->addDescriptor(new BLE2902());

    pStatusChar = pService->createCharacteristic(
        MEDBAND_CHAR_STATUS_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
    );
    pStatusChar->addDescriptor(new BLE2902());

    pCommandChar = pService->createCharacteristic(
        MEDBAND_CHAR_COMMAND_UUID,
        BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR
    );
    pCommandChar->setCallbacks(new MedBandCommandCallbacks());

    pService->start();

    BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(MEDBAND_SERVICE_UUID);
    pAdvertising->setScanResponse(true);
    pAdvertising->setMinPreferred(0x06);
    BLEDevice::startAdvertising();
}

void BleManager::loop() {
    if (!_deviceConnected && _oldDeviceConnected) {
        delay(300);
        pServer->startAdvertising();
        _oldDeviceConnected = _deviceConnected;
    }
    if (_deviceConnected && !_oldDeviceConnected) {
        _oldDeviceConnected = _deviceConnected;
    }
}

void BleManager::notifyVitals(const String& json) {
    if (!pVitalsChar) return;
    pVitalsChar->setValue(json.c_str());
    if (_deviceConnected) pVitalsChar->notify();
}

void BleManager::notifyMotion(const String& json) {
    if (!pMotionChar) return;
    pMotionChar->setValue(json.c_str());
    if (_deviceConnected) pMotionChar->notify();
}

void BleManager::notifyStatus(const String& json) {
    if (!pStatusChar) return;
    pStatusChar->setValue(json.c_str());
    if (_deviceConnected) pStatusChar->notify();
}
