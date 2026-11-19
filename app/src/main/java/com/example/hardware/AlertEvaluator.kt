package com.example.hardware

import com.example.data.model.FallStage
import com.example.data.model.LiveMotionPacket
import com.example.data.model.LiveVitalsPacket
import com.example.data.model.MovementActivity
import com.example.data.model.SensorQuality

data class EvaluatedAlert(
    val category: String,
    val severity: String,
    val titleEn: String,
    val titleAr: String,
    val messageEn: String,
    val messageAr: String,
    val triggersPendingEmergency: Boolean = false
)

object AlertEvaluator {
    fun evaluateVitals(
        packet: LiveVitalsPacket,
        hrMin: Long,
        hrMax: Long,
        spo2Min: Long
    ): List<EvaluatedAlert> {
        val alerts = mutableListOf<EvaluatedAlert>()

        if (!packet.fingerDetected || packet.sensorQuality == SensorQuality.POOR) {
            alerts.add(
                EvaluatedAlert(
                    category = "SENSOR_QUALITY",
                    severity = "INFO",
                    titleEn = "Poor MAX30102 Signal Quality",
                    titleAr = "جودة إشارة مستشعر النبض ضعيفة",
                    messageEn = "Adjust bracelet contact on wrist for reliable readings.",
                    messageAr = "يرجى تعديل تثبيت الإسورة على المعصم للحصول على قراءة دقيقة."
                )
            )
            return alerts
        }

        packet.heartRateBpm?.let { hr ->
            if (hr > hrMax) {
                alerts.add(
                    EvaluatedAlert(
                        category = "HIGH_HR",
                        severity = if (hr >= hrMax + 25) "CRITICAL" else "WARNING",
                        titleEn = "Elevated Heart Rate ($hr BPM)",
                        titleAr = "ارتفاع معدل ضربات القلب ($hr نبضة/د)",
                        messageEn = "Heart rate exceeded configured limit of $hrMax BPM.",
                        messageAr = "تجاوز معدل النبض الحد المحدد ($hrMax نبضة/د).",
                        triggersPendingEmergency = hr >= hrMax + 35
                    )
                )
            } else if (hr < hrMin) {
                alerts.add(
                    EvaluatedAlert(
                        category = "LOW_HR",
                        severity = if (hr <= hrMin - 12) "CRITICAL" else "WARNING",
                        titleEn = "Low Heart Rate ($hr BPM)",
                        titleAr = "انخفاض معدل ضربات القلب ($hr نبضة/د)",
                        messageEn = "Heart rate dropped below configured limit of $hrMin BPM.",
                        messageAr = "انخفض معدل النبض عن الحد الأدنى ($hrMin نبضة/د).",
                        triggersPendingEmergency = hr <= hrMin - 15
                    )
                )
            }
        }

        // Only alert on SpO2 when algorithm validated the reading and quality is GOOD
        if (packet.spo2Valid && packet.sensorQuality == SensorQuality.GOOD) {
            packet.spo2Percent?.let { spo2 ->
                if (spo2 < spo2Min) {
                    alerts.add(
                        EvaluatedAlert(
                            category = "LOW_SPO2",
                            severity = if (spo2 <= 88) "CRITICAL" else "WARNING",
                            titleEn = "Low SpO2 Reading ($spo2%)",
                            titleAr = "انخفاض نسبة الأكسجين ($spo2%)",
                            messageEn = "Validated SpO2 fell below configured limit ($spo2Min%).",
                            messageAr = "انخفضت نسبة الأكسجين المعتمدة عن الحد المحدد ($spo2Min%).",
                            triggersPendingEmergency = spo2 <= 86
                        )
                    )
                }
            }
        }

        packet.batteryPercent?.let { bat ->
            if (bat <= 15) {
                alerts.add(
                    EvaluatedAlert(
                        category = "LOW_BATTERY",
                        severity = "WARNING",
                        titleEn = "MedBand Low Battery ($bat%)",
                        titleAr = "انخفاض بطارية الإسورة ($bat%)",
                        messageEn = "Recharge your MedBand ESP32-C3 soon.",
                        messageAr = "يرجى شحن إسورة MedBand قريباً."
                    )
                )
            }
        }

        return alerts
    }

    fun evaluateMotion(
        packet: LiveMotionPacket,
        fallDetectionEnabled: Boolean
    ): List<EvaluatedAlert> {
        val alerts = mutableListOf<EvaluatedAlert>()
        if (packet.activity == MovementActivity.UNUSUAL_MOVEMENT) {
            alerts.add(
                EvaluatedAlert(
                    category = "UNUSUAL_MOVEMENT",
                    severity = "WARNING",
                    titleEn = "Unusual Movement Detected",
                    titleAr = "تم رصد حركة غير معتادة",
                    messageEn = "MPU6050 recorded sudden acceleration (${String.format("%.2f", packet.accelMagnitudeG)}g).",
                    messageAr = "رصد مستشعر الحركة تسارعاً مفاجئاً (${String.format("%.2f", packet.accelMagnitudeG)}g)."
                )
            )
        }
        if (fallDetectionEnabled &&
            (packet.fallStage == FallStage.POSSIBLE_FALL || packet.activity == MovementActivity.POSSIBLE_FALL)
        ) {
            alerts.add(
                EvaluatedAlert(
                    category = "POSSIBLE_FALL",
                    severity = "CRITICAL",
                    titleEn = "Possible Fall Pattern Detected (Experimental)",
                    titleAr = "احتمال سقوط مرصود (تجريبي)",
                    messageEn = "Multi-stage impact, rotation, and post-impact stillness detected. Please confirm status.",
                    messageAr = "تم رصد صدمة متبوعة بتغير اتجاه وسكون. يرجى تأكيد حالتك.",
                    triggersPendingEmergency = true
                )
            )
        }
        return alerts
    }
}
