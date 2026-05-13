package com.cameron.tganime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cameron.tganime.ui.nav.AppRoot
import com.cameron.tganime.ui.theme.TgAnimeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TgAnimeTheme {
                AppRoot()
            }
        }
    }
}
