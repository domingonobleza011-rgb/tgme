package com.tgme.timekeeping.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tgme.timekeeping.TgmeApp
import com.tgme.timekeeping.data.User
import com.tgme.timekeeping.logic.Security
import kotlinx.coroutines.launch

/** First-run admin creation (replaces install.php). */
@Composable
fun SetupScreen(onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("Administrator") }
    var user by remember { mutableStateOf("admin") }
    var pw by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("TGME Timekeeping", style = MaterialTheme.typography.headlineMedium)
        Text("First-time setup: create the administrator account.")
        Field("Full name", name, { name = it })
        Field("Username", user, { user = it })
        Field("Password (min 6)", pw, { pw = it }, password = true)
        Field("Recovery PIN (4-20 chars)", pin, { pin = it })
        if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error)
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            when {
                name.isBlank() || user.isBlank() || pw.isBlank() || pin.isBlank() -> err = "Please fill in all fields."
                pw.length < 6 -> err = "Password must be at least 6 characters."
                pin.length !in 4..20 -> err = "Recovery PIN must be 4 to 20 characters."
                else -> scope.launch {
                    TgmeApp.db.users().insert(
                        User(
                            username = user.trim(), passwordHash = Security.hash(pw), recoveryPin = pin.trim(),
                            fullName = name.trim(), role = "TGME Admin - Full Access", isAdmin = true
                        )
                    )
                    onDone()
                }
            }
        }) { Text("Create administrator") }
    }
}

@Composable
fun LoginScreen() {
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    var forgot by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("TGME Timekeeping", style = MaterialTheme.typography.headlineMedium)
        Text("Sign in to continue")
        Field("Username", user, { user = it })
        Field("Password", pw, { pw = it }, password = true)
        if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error)
        if (info.isNotEmpty()) Text(info)
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            if (user.isBlank() || pw.isEmpty()) { err = "Please enter your username and password."; return@Button }
            scope.launch {
                val u = TgmeApp.db.users().byUsername(user.trim())
                if (u != null && Security.verify(pw, u.passwordHash)) Session.user = u
                else err = "Invalid username or password."
            }
        }) { Text("Login") }
        TextButton(onClick = { forgot = true }) { Text("Forgot password?") }
    }

    if (forgot) ForgotPasswordDialog(
        onDismiss = { forgot = false },
        onSuccess = { forgot = false; err = ""; info = "Password reset successfully. You may now log in." }
    )
}

@Composable
private fun ForgotPasswordDialog(onDismiss: () -> Unit, onSuccess: () -> Unit) {
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset password") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Username", user, { user = it })
                Field("Recovery PIN", pin, { pin = it })
                Field("New password", pw, { pw = it }, password = true)
                Field("Confirm password", pw2, { pw2 = it }, password = true)
                if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    user.isBlank() || pin.isBlank() || pw.isEmpty() || pw2.isEmpty() -> err = "Please fill in all fields."
                    pw != pw2 -> err = "New password and confirm password do not match."
                    pw.length < 6 -> err = "New password must be at least 6 characters."
                    else -> scope.launch {
                        val u = TgmeApp.db.users().byUsername(user.trim())
                        if (u == null || u.recoveryPin != pin.trim()) err = "Username or Recovery PIN is incorrect."
                        else { TgmeApp.db.users().update(u.copy(passwordHash = Security.hash(pw))); onSuccess() }
                    }
                }
            }) { Text("Reset") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
