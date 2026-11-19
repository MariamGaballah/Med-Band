package com.example.hardware

import android.app.Activity
import android.content.Context
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.util.Log
import java.nio.charset.StandardCharsets

data class ParsedMedBandNfcToken(
    val ownerUid: String,
    val tokenId: String,
    val tagUidHex: String,
    val rawUri: String
)

enum class NfcAvailability {
    READY,
    DISABLED,
    UNSUPPORTED
}

object NfcMedicalManager {
    private const val TAG = "MedBandNfc"
    private const val SCHEME = "medband"
    private const val HOST = "profile"

    fun checkAvailability(context: Context): NfcAvailability {
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return NfcAvailability.UNSUPPORTED
        return if (adapter.isEnabled) NfcAvailability.READY else NfcAvailability.DISABLED
    }

    fun buildTokenUri(ownerUid: String, tokenId: String): String =
        "$SCHEME://$HOST/$ownerUid/$tokenId"

    fun parseTokenUri(uri: String, tagUidHex: String = ""): ParsedMedBandNfcToken? {
        val trimmed = uri.trim()
        val prefix = "$SCHEME://$HOST/"
        if (!trimmed.startsWith(prefix)) return null
        val remainder = trimmed.removePrefix(prefix)
        val parts = remainder.split("/").filter { it.isNotBlank() }
        if (parts.size != 2) return null
        val uid = parts[0]
        val token = parts[1]
        if (!uid.matches(Regex("^[a-zA-Z0-9_\\-]{3,128}$")) ||
            !token.matches(Regex("^[a-zA-Z0-9_\\-]{3,128}$"))
        ) {
            return null
        }
        return ParsedMedBandNfcToken(
            ownerUid = uid,
            tokenId = token,
            tagUidHex = tagUidHex,
            rawUri = trimmed
        )
    }

    fun tagUidToHex(tag: Tag): String =
        tag.id?.joinToString("") { byte -> "%02X".format(byte) } ?: ""

    fun enableReaderMode(
        activity: Activity,
        onTagDiscovered: (Tag) -> Unit
    ) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V
        adapter.enableReaderMode(
            activity,
            { tag -> onTagDiscovered(tag) },
            flags,
            Bundle()
        )
    }

    fun disableReaderMode(activity: Activity) {
        try {
            NfcAdapter.getDefaultAdapter(activity)?.disableReaderMode(activity)
        } catch (e: Exception) {
            Log.w(TAG, "disableReaderMode ignored: ${e.message}")
        }
    }

    fun readTokenFromTag(tag: Tag): Result<ParsedMedBandNfcToken> {
        val tagUid = tagUidToHex(tag)
        val ndef = Ndef.get(tag)
            ?: return Result.failure(IllegalArgumentException("Tag does not contain an NDEF structure."))
        return try {
            ndef.connect()
            val message = ndef.ndefMessage ?: ndef.cachedNdefMessage
                ?: return Result.failure(IllegalArgumentException("NFC tag is empty."))
            val parsed = extractTokenFromNdefMessage(message, tagUid)
            if (parsed != null) {
                Result.success(parsed)
            } else {
                Result.failure(IllegalArgumentException("No valid MedBand NDEF token record found."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read NFC tag", e)
            Result.failure(e)
        } finally {
            try {
                ndef.close()
            } catch (_: Exception) {
            }
        }
    }

    fun extractTokenFromNdefMessage(message: NdefMessage, tagUidHex: String = ""): ParsedMedBandNfcToken? {
        for (record in message.records) {
            val uri = record.toUri()?.toString()
            if (!uri.isNullOrBlank()) {
                parseTokenUri(uri, tagUidHex)?.let { return it }
            }
            if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT)) {
                val payload = record.payload
                if (payload.isNotEmpty()) {
                    val langLen = payload[0].toInt() and 0x3F
                    if (1 + langLen < payload.size) {
                        val text = String(payload, 1 + langLen, payload.size - 1 - langLen, StandardCharsets.UTF_8)
                        parseTokenUri(text, tagUidHex)?.let { return it }
                    }
                }
            }
        }
        return null
    }

    fun writeOpaqueTokenToTag(
        context: Context,
        tag: Tag,
        ownerUid: String,
        tokenId: String
    ): Result<String> {
        val uri = buildTokenUri(ownerUid, tokenId)
        val uriRecord = NdefRecord.createUri(uri)
        val aarRecord = NdefRecord.createApplicationRecord(context.packageName)
        val message = NdefMessage(arrayOf(uriRecord, aarRecord))
        val tagUid = tagUidToHex(tag)

        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return try {
                ndef.connect()
                if (!ndef.isWritable) {
                    return Result.failure(IllegalStateException("NFC tag is read-only."))
                }
                val bytesNeeded = message.toByteArray().size
                if (ndef.maxSize < bytesNeeded) {
                    return Result.failure(IllegalStateException("NFC tag capacity (${ndef.maxSize}B) is smaller than token ($bytesNeeded B)."))
                }
                ndef.writeNdefMessage(message)
                Result.success(tagUid)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write NDEF tag", e)
                Result.failure(e)
            } finally {
                try {
                    ndef.close()
                } catch (_: Exception) {
                }
            }
        }

        val formatable = NdefFormatable.get(tag)
        if (formatable != null) {
            return try {
                formatable.connect()
                formatable.format(message)
                Result.success(tagUid)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to format NDEF tag", e)
                Result.failure(e)
            } finally {
                try {
                    formatable.close()
                } catch (_: Exception) {
                }
            }
        }

        return Result.failure(IllegalArgumentException("Tag is neither NDEF nor NDEF-formatable."))
    }
}
