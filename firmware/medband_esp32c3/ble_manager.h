#pragma once
#include <Arduino.h>
#include <functional>

class BleManager {
public:
    using CommandCallback = std::function<void(const String& jsonCommand)>;

    void begin(CommandCallback onCommand);
    void loop();
    bool isConnected() const { return _deviceConnected; }

    void notifyVitals(const String& json);
    void notifyMotion(const String& json);
    void notifyStatus(const String& json);

private:
    bool _deviceConnected = false;
    bool _oldDeviceConnected = false;
    CommandCallback _onCommand;
};
