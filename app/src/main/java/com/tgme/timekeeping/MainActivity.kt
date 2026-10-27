package com.tgme.timekeeping

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.tgme.timekeeping.ui.HomeScaffold
import com.tgme.timekeeping.ui.LoginScreen
import com.tgme.timekeeping.ui.Session
import com.tgme.timekeeping.ui.SetupScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1A1F3A))) {
                Surface(Modifier.fillMaxSize()) { Root() }
            }
        }
    }
}

@Composable
private fun Root() {
    val db = TgmeApp.db
    var userCount by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Session.user) { userCount = db.users().count() }

    when {
        userCount == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        userCount == 0 -> SetupScreen(onDone = { userCount = 1 })   // first run = replaces install.php
        Session.user == null -> LoginScreen()
        else -> HomeScaffold()
    }
}
