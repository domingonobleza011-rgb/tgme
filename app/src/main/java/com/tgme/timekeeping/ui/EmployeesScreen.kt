package com.tgme.timekeeping.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tgme.timekeeping.TgmeApp
import com.tgme.timekeeping.data.Employee
import com.tgme.timekeeping.logic.Security
import com.tgme.timekeeping.logic.Sss
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun EmployeesScreen() {
    val all by TgmeApp.db.employees().all().collectAsState(emptyList())
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Employee?>(null) }
    var editing by remember { mutableStateOf<Employee?>(null) }
    var adding by remember { mutableStateOf(false) }

    val list = all.filter {
        query.isBlank() || "${it.lastName} ${it.firstName} ${it.empNo} ${it.department}".contains(query, ignoreCase = true)
    }

    androidx.compose.material3.Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Default.Add, "Add employee") }
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(12.dp)) {
            Field("Search", query, { query = it })
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { e ->
                    Card(Modifier.fillMaxWidth().clickable { selected = e }) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${e.lastName}, ${e.firstName}", style = MaterialTheme.typography.titleMedium)
                            Text("${e.empNo} • ${e.department.ifBlank { "—" }} • ${e.position.ifBlank { "—" }}")
                            Text("${money(e.dailyRate)}/day • ${e.status}")
                        }
                    }
                }
            }
        }
    }

    selected?.let { e ->
        EmployeeDetailDialog(e, onEdit = { editing = e; selected = null }, onClose = { selected = null })
    }
    if (adding) EmployeeFormDialog(null) { adding = false }
    editing?.let { EmployeeFormDialog(it) { editing = null } }
}

@Composable
private fun EmployeeDetailDialog(e: Employee, onEdit: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val qr = remember(e.qrToken) { qrBitmap(e.qrToken).asImageBitmap() }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("${e.lastName}, ${e.firstName}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(qr, "QR code", Modifier.size(220.dp))
                LabelRow("Employee No.", e.empNo)
                LabelRow("Department", e.department.ifBlank { "—" })
                LabelRow("Position", e.position.ifBlank { "—" })
                LabelRow("Contact", e.contact.ifBlank { "—" })
                LabelRow("SSS No.", e.sssNumber ?: "—")
                LabelRow("Daily rate", money(e.dailyRate))
                LabelRow("Status", e.status)
                TextButton(onClick = {
                    scope.launch {
                        val month = YearMonth.now()
                        val recs = TgmeApp.db.attendance().month(e.id, month.toString()).associateBy { it.attDate }
                        val rows = mutableListOf(
                            listOf("Daily Time Record"), listOf("Employee", "${e.lastName}, ${e.firstName}"),
                            listOf("Employee No.", e.empNo), listOf("Department", e.department),
                            listOf("Position", e.position), listOf("Period", month.toString()), listOf(),
                            listOf("Day", "Time In", "Time Out", "Hours", "Status")
                        )
                        for (d in 1..month.lengthOfMonth()) {
                            val date = month.atDay(d).toString()
                            val r = recs[date]
                            rows += listOf(
                                dateLabel(date), if (r?.timeIn != null) timeLabel(r.timeIn) else "",
                                if (r?.timeOut != null) timeLabel(r.timeOut) else "",
                                d2(r?.hoursWorked ?: 0.0), r?.status ?: ""
                            )
                        }
                        shareCsv(ctx, "DTR_${e.lastName}_${e.firstName}_$month.csv".replace(" ", "_"), rows)
                    }
                }) { Text("Export this month's DTR (CSV)") }
            }
        },
        confirmButton = { TextButton(onClick = onEdit) { Text("Edit") } },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
                TextButton(onClick = onClose) { Text("Close") }
            }
        }
    )
    if (confirmDelete) ConfirmDialog(
        "Delete employee", "This also deletes their attendance and payroll records.",
        onConfirm = { scope.launch { TgmeApp.db.employees().delete(e.id); onClose() } },
        onDismiss = { confirmDelete = false }
    )
}

@Composable
private fun EmployeeFormDialog(existing: Employee?, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var empNo by remember { mutableStateOf(existing?.empNo ?: "") }
    var last by remember { mutableStateOf(existing?.lastName ?: "") }
    var first by remember { mutableStateOf(existing?.firstName ?: "") }
    var dept by remember { mutableStateOf(existing?.department ?: "") }
    var pos by remember { mutableStateOf(existing?.position ?: "") }
    var contact by remember { mutableStateOf(existing?.contact ?: "") }
    var sss by remember { mutableStateOf(existing?.sssNumber ?: "") }
    var rate by remember { mutableStateOf((existing?.dailyRate ?: 610.0).toString()) }
    var active by remember { mutableStateOf((existing?.status ?: "Active") == "Active") }
    var err by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (existing == null) "Register employee" else "Edit employee") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Field("Employee No.", empNo, { empNo = it })
                Field("Last name", last, { last = it })
                Field("First name", first, { first = it })
                Field("Department", dept, { dept = it })
                Field("Position", pos, { pos = it })
                Field("Contact", contact, { contact = it }, keyboard = KeyboardType.Phone)
                Field("SSS number (optional)", sss, { sss = it })
                Field("Daily rate", rate, { rate = it }, keyboard = KeyboardType.Decimal)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Switch(active, { active = it })
                    Text(if (active) "  Active" else "  Inactive")
                }
                if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (empNo.isBlank() || last.isBlank() || first.isBlank()) { err = "Employee no., last and first name are required."; return@TextButton }
                val s = Sss.normalize(sss)
                if (!s.ok) { err = s.error; return@TextButton }
                scope.launch {
                    val dao = TgmeApp.db.employees()
                    val id = existing?.id ?: 0L
                    when {
                        dao.empNoInUse(empNo.trim(), id) > 0 -> err = "That employee number is already used."
                        s.value != null && dao.sssInUse(s.value, id) > 0 -> err = "That SSS number is already registered to another employee."
                        else -> {
                            val e = Employee(
                                id = id, empNo = empNo.trim(), lastName = last.trim(), firstName = first.trim(),
                                department = dept.trim(), position = pos.trim(), contact = contact.trim(),
                                sssNumber = s.value, dailyRate = rate.toDoubleOrNull() ?: 610.0,
                                status = if (active) "Active" else "Inactive",
                                qrToken = existing?.qrToken ?: Security.newQrToken(),
                                createdAt = existing?.createdAt ?: System.currentTimeMillis()
                            )
                            if (existing == null) dao.insert(e) else dao.update(e)
                            onClose()
                        }
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}
