package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.data.firebase.configureAppCheck
import com.example.ui.MedBandApp
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val incomingNfcUri = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before rendering UI or creating ViewModels
        configureAppCheck(this, intent)

        handleNfcIntent(intent)
        enableEdgeToEdge()

        setContent {
            MedBandApp(
                incomingNfcUri = incomingNfcUri,
                onConsumeNfcUri = { incomingNfcUri.value = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNfcIntent(intent)
    }

    private fun handleNfcIntent(intent: Intent?) {
        val dataString = intent?.dataString ?: return
        if (dataString.startsWith("medband://profile/")) {
            incomingNfcUri.value = dataString
        }
    }
}
