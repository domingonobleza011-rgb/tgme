package com.tgme.timekeeping.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tgme.timekeeping.TgmeApp
import com.tgme.timekeeping.data.Settings
import com.tgme.timekeeping.data.User
import com.tgme.timekeeping.logic.Security
import com.tgme.timekeeping.logic.Sss
import com.tgme.timekeeping.logic.ensureSettings
import kotlinx.coroutines.launch
import java.time.LocalTime

@Composable
fun SettingsScreen() {
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }
    var company by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var grace by remember { mutableStateOf("") }
    var brk by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("") }
    var lateRate by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val s = ensureSettings(TgmeApp.db)
        company = s.companyName; branch = s.branchLocation; start = s.workStartTime.take(5); end = s.workEndTime.take(5)
        grace = s.graceMinutes.toString(); brk = s.breakMinutes.toString(); days = s.workDays
        lateRate = s.lateDeductionPerMinute.toString(); loaded = true
    }
    if (!loaded) return

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Field("Company name", company, { company = it })
        Field("Branch / location", branch, { branch = it })
        Field("Work start (HH:mm)", start, { start = it })
        Field("Work end (HH:mm)", end, { end = it })
        Field("Grace minutes", grace, { grace = it }, keyboard = KeyboardType.Number)
        Field("Break minutes", brk, { brk = it }, keyboard = KeyboardType.Number)
        Field("Work days", days, { days = it })
        Field("Late deduction per minute (₱)", lateRate, { lateRate = it }, keyboard = KeyboardType.Decimal)
        if (msg.isNotEmpty()) Text(msg, color = MaterialTheme.colorScheme.primary)
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            val st = runCatching { LocalTime.parse(start.trim()) }.getOrNull()
            val en = runCatching { LocalTime.parse(end.trim()) }.getOrNull()
            if (st == null || en == null) { msg = "Use HH:mm for times, e.g. 08:00."; return@Button }
            scope.launch {
                TgmeApp.db.settings().save(
                    Settings(
                        companyName = company.trim(), branchLocation = branch.trim(),
                        workStartTime = st.toString().let { if (it.length == 5) "$it:00" else it },
                        workEndTime = en.toString().let { if (it.length == 5) "$it:00" else it },
                        graceMinutes = grace.toIntOrNull() ?: 10, breakMinutes = brk.toIntOrNull() ?: 60,
                        workDays = days.trim(), lateDeductionPerMinute = lateRate.toDoubleOr0()
                    )
                )
                msg = "Settings saved."
            }
        }) { Text("Save settings") }
    }
}

@Composable
fun UsersScreen() {
    val users by TgmeApp.db.users().all().collectAsState(emptyList())
    var creating by remember { mutableStateOf(false) }
    val isAdmin = Session.user?.isAdmin == true

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isAdmin) Button(onClick = { creating = true }) { Text("Create account") }
        else Text("Only the administrator can create accounts.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(users, key = { it.id }) { u ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text(u.fullName, style = MaterialTheme.typography.titleSmall)
                        Text("@${u.username} • ${u.role}${if (u.isAdmin) " • Admin" else ""}")
                        Text("SSS: ${u.sssNumber ?: "—"}")
                    }
                }
            }
        }
    }
    if (creating && isAdmin) CreateUserDialog { creating = false }
}

@Composable
private fun CreateUserDialog(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var sss by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var adminPw by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Create account") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Field("Full name", name, { name = it })
                Field("Username", user, { user = it })
                Field("Role / title", role, { role = it })
                Field("SSS number (optional)", sss, { sss = it })
                Field("Password", pw, { pw = it }, password = true)
                Field("Confirm password", pw2, { pw2 = it }, password = true)
                Field("Recovery PIN", pin, { pin = it })
                Field("Your (admin) password", adminPw, { adminPw = it }, password = true)
                if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = Sss.normalize(sss)
                when {
                    Session.user?.let { Security.verify(adminPw, it.passwordHash) } != true ->
                        err = "Your password is incorrect. The account was not created."
                    name.isBlank() || user.isBlank() || pw.isEmpty() || pin.isBlank() -> err = "Please fill in all required fields."
                    name.length > 100 -> err = "Full name is too long (max 100 characters)."
                    !Regex("^[A-Za-z0-9._-]{3,50}$").matches(user.trim()) ->
                        err = "Username must be 3-50 characters: letters, numbers, dot, dash or underscore only."
                    pw.length < 6 -> err = "Password must be at least 6 characters."
                    pw != pw2 -> err = "Password and confirm password do not match."
                    pin.length !in 4..20 -> err = "Recovery PIN must be 4 to 20 characters."
                    !s.ok -> err = s.error
                    else -> scope.launch {
                        val dao = TgmeApp.db.users()
                        when {
                            dao.byUsername(user.trim()) != null -> err = "That username is already taken."
                            s.value != null && dao.sssInUse(s.value, 0) > 0 -> err = "That SSS number is already registered to another account."
                            else -> {
                                dao.insert(
                                    User(
                                        username = user.trim(), passwordHash = Security.hash(pw), recoveryPin = pin.trim(),
                                        fullName = name.trim(), role = role.trim().ifBlank { "TGME Authorized Personnel" }.take(50),
                                        isAdmin = false, sssNumber = s.value
                                    )
                                )
                                onClose()
                            }
                        }
                    }
                }
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}
