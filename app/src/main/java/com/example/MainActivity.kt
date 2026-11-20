package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.notifications.LocalNotificationHelper
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.FfCreatorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LocalNotificationHelper.ensureChannel(this)
        setContent {
            FfCreatorTheme {
                AppNavigation()
            }
        }
    }
}
