package com.example.data.firebase

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.LatLng
import com.google.firebase.ai.type.RetrievalConfig
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.ToolConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

data class GroundedPlaceSource(
    val title: String,
    val uri: String,
    val placeId: String?
)

data class MapsGroundedCareResult(
    val summaryText: String,
    val sources: List<GroundedPlaceSource>
)

class AiMapsRepository {
    private val aiBackend by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
    }

    suspend fun findNearbyMedicalFacilities(
        latitude: Double?,
        longitude: Double?,
        languageCode: String
    ): Result<MapsGroundedCareResult> {
        return try {
            val retrievalConfig = RetrievalConfig.builder()
                .setLanguageCode(if (languageCode == "ar") "ar" else "en-US")
                .apply {
                    if (latitude != null && longitude != null) {
                        setLatLng(LatLng(latitude = latitude, longitude = longitude))
                    }
                }
                .build()

            val model = aiBackend.generativeModel(
                modelName = "gemini-3.8-flash",
                tools = listOf(Tool.googleMaps()),
                toolConfig = ToolConfig(retrievalConfig = retrievalConfig)
            )

            val prompt = if (languageCode == "ar") {
                "اذكر أقرب المستشفيات والمراكز الطبية والصيدليات المناوبة القريبة من هذا الموقع مع ذكر اسم المنشأة والعنوان وساعات العمل إن توفرت، باختصار ووضوح."
            } else {
                "List nearby hospitals, urgent care clinics, and pharmacies near this location with facility name, address, and opening hours if available, concisely."
            }

            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }

            val text = response.text?.trim().orEmpty()
            val chunks = response.candidates.firstOrNull()?.groundingMetadata?.groundingChunks.orEmpty()
            val sources = chunks.mapNotNull { chunk ->
                val maps = chunk.maps ?: return@mapNotNull null
                val title = maps.title?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val uri = maps.uri?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                GroundedPlaceSource(
                    title = title,
                    uri = uri,
                    placeId = maps.placeId
                )
            }.distinctBy { it.uri }

            if (text.isBlank() && sources.isEmpty()) {
                Result.failure(IllegalStateException("No nearby medical facilities returned."))
            } else {
                Result.success(MapsGroundedCareResult(summaryText = text, sources = sources))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Google Maps grounding request timed out", e)
            Result.failure(IllegalStateException("Location search timed out. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google Maps grounding failed", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "AiMapsRepository"
    }
}
