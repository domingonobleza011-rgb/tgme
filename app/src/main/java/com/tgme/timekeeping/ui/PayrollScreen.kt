package com.tgme.timekeeping.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tgme.timekeeping.TgmeApp
import com.tgme.timekeeping.data.PayrollRow
import com.tgme.timekeeping.logic.autoStoreLastWeek
import com.tgme.timekeeping.logic.ensureSettings
import com.tgme.timekeeping.logic.mondayOf
import com.tgme.timekeeping.logic.netPay
import com.tgme.timekeeping.logic.storePayrollForPeriod
import com.tgme.timekeeping.logic.weekBounds
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun PayrollScreen() {
    val db = TgmeApp.db
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by db.payroll().allWithEmployee().collectAsState(emptyList())

    var offset by remember { mutableStateOf(0) }
    var generate by remember { mutableStateOf(false) }
    var slip by remember { mutableStateOf<PayrollRow?>(null) }

    LaunchedEffect(Unit) { autoStoreLastWeek(db) }

    val (mon, sat) = weekBounds(offset)
    val weekRows = all.filter { mondayOf(it.p.periodStart) == mon }
    val weekly = all.groupBy { mondayOf(it.p.periodStart) }.toList().sortedByDescending { it.first }.take(12)

    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { offset-- }) { Text("◀ Prev") }
            Text("${dateLabel(mon.toString())} – ${dateLabel(sat.toString())}", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { offset++ }) { Text("Next ▶") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { generate = true }) { Text("Generate for all") }
            TextButton(onClick = {
                val out = mutableListOf(listOf("Emp No", "Name", "Present", "Late", "Absent", "Hours", "Gross", "Late Ded", "SSS", "PhilHealth", "Pag-IBIG", "Other", "Adjustment", "Net"))
                weekRows.forEach { r ->
                    val p = r.p
                    out += listOf(r.empNo, "${r.lastName}, ${r.firstName}", "${p.daysPresent}", "${p.daysLate}", "${p.daysAbsent}", d2(p.totalHours),
                        d2(p.grossPay), d2(p.lateDeduction), d2(p.sssDeduction), d2(p.philhealthDeduction), d2(p.pagibigDeduction), d2(p.otherDeduction), d2(p.adjustment), d2(p.netPay))
                }
                shareCsv(ctx, "payroll_$mon.csv", out)
            }) { Text("⇩ Export CSV") }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Text("Payroll for selected week", style = MaterialTheme.typography.titleSmall) }
            if (weekRows.isEmpty()) item { Text("No payroll generated for this week yet.") }
            items(weekRows, key = { it.p.id }) { r ->
                Card(Modifier.fillMaxWidth().clickable { slip = r }) {
                    Column(Modifier.padding(10.dp)) {
                        Text("${r.lastName}, ${r.firstName}", style = MaterialTheme.typography.titleSmall)
                        Text("${r.p.daysPresent} present • ${r.p.daysLate} late • ${r.p.daysAbsent} absent")
                        Text("Net ${money(r.p.netPay)}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            item { Text("Weekly summary", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall) }
            items(weekly, key = { it.first.toString() }) { (week, rows) ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text("${dateLabel(week.toString())} – ${dateLabel(week.plusDays(5).toString())}", style = MaterialTheme.typography.titleSmall)
                        LabelRow("Employees", "${rows.map { it.p.empId }.distinct().size}")
                        LabelRow("Gross", money(rows.sumOf { it.p.grossPay }))
                        LabelRow("Late deductions", money(rows.sumOf { it.p.lateDeduction }))
                        LabelRow("Other deductions", money(rows.sumOf { it.p.sssDeduction + it.p.philhealthDeduction + it.p.pagibigDeduction + it.p.otherDeduction }))
                        LabelRow("Adjustments", money(rows.sumOf { it.p.adjustment }))
                        LabelRow("Net", money(rows.sumOf { it.p.netPay }))
                    }
                }
            }
        }
    }

    if (generate) GenerateDialog(mon, sat) { generate = false }
    slip?.let { PayslipDialog(it) { slip = null } }
}

@Composable
private fun GenerateDialog(mon: LocalDate, sat: LocalDate, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var start by remember { mutableStateOf(mon.toString()) }
    var end by remember { mutableStateOf(sat.toString()) }
    var sss by remember { mutableStateOf("0") }
    var phic by remember { mutableStateOf("0") }
    var pagibig by remember { mutableStateOf("0") }
    var other by remember { mutableStateOf("0") }
    var adj by remember { mutableStateOf("0") }
    var note by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Generate payroll (all active employees)") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Field("Period start (yyyy-MM-dd)", start, { start = it })
                Field("Period end (yyyy-MM-dd)", end, { end = it })
                Field("SSS deduction", sss, { sss = it }, keyboard = KeyboardType.Decimal)
                Field("PhilHealth deduction", phic, { phic = it }, keyboard = KeyboardType.Decimal)
                Field("Pag-IBIG deduction", pagibig, { pagibig = it }, keyboard = KeyboardType.Decimal)
                Field("Other deduction", other, { other = it }, keyboard = KeyboardType.Decimal)
                Field("Adjustment (+/-)", adj, { adj = it }, keyboard = KeyboardType.Decimal)
                Field("Adjustment note", note, { note = it })
                if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = runCatching { LocalDate.parse(start.trim()) }.getOrNull()
                val e = runCatching { LocalDate.parse(end.trim()) }.getOrNull()
                if (s == null || e == null || e.isBefore(s)) { err = "Enter a valid period."; return@TextButton }
                scope.launch {
                    val db = TgmeApp.db
                    val rate = ensureSettings(db).lateDeductionPerMinute
                    storePayrollForPeriod(
                        db, db.employees().activeList(), s.toString(), e.toString(), rate,
                        sss.toDoubleOr0(), phic.toDoubleOr0(), pagibig.toDoubleOr0(), other.toDoubleOr0(),
                        adj.toDoubleOr0(), note.trim(), onlyWithAttendance = false
                    )
                    onClose()
                }
            }) { Text("Generate") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}

@Composable
private fun PayslipDialog(r: PayrollRow, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val p = r.p

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Payslip – ${r.lastName}, ${r.firstName}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${dateLabel(p.periodStart)} – ${dateLabel(p.periodEnd)}")
                LabelRow("Days present / late / absent", "${p.daysPresent} / ${p.daysLate} / ${p.daysAbsent}")
                LabelRow("Total hours", d2(p.totalHours))
                LabelRow("Daily rate", money(p.dailyRate))
                LabelRow("Gross pay", money(p.grossPay))
                LabelRow("Late deduction", money(p.lateDeduction))
                LabelRow("SSS", money(p.sssDeduction))
                LabelRow("PhilHealth", money(p.philhealthDeduction))
                LabelRow("Pag-IBIG", money(p.pagibigDeduction))
                LabelRow("Other", money(p.otherDeduction))
                LabelRow("Adjustment", money(p.adjustment) + (p.adjustmentNote?.let { " ($it)" } ?: ""))
                LabelRow("NET PAY", money(p.netPay))
            }
        },
        confirmButton = { TextButton(onClick = { editing = true }) { Text("Edit deductions") } },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
                TextButton(onClick = onClose) { Text("Close") }
            }
        }
    )

    if (editing) {
        var late by remember { mutableStateOf(p.lateDeduction.toString()) }
        var sss by remember { mutableStateOf(p.sssDeduction.toString()) }
        var phic by remember { mutableStateOf(p.philhealthDeduction.toString()) }
        var pagibig by remember { mutableStateOf(p.pagibigDeduction.toString()) }
        var other by remember { mutableStateOf(p.otherDeduction.toString()) }
        var adj by remember { mutableStateOf(p.adjustment.toString()) }
        var note by remember { mutableStateOf(p.adjustmentNote ?: "") }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Edit deductions") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Field("Late deduction", late, { late = it }, keyboard = KeyboardType.Decimal)
                    Field("SSS", sss, { sss = it }, keyboard = KeyboardType.Decimal)
                    Field("PhilHealth", phic, { phic = it }, keyboard = KeyboardType.Decimal)
                    Field("Pag-IBIG", pagibig, { pagibig = it }, keyboard = KeyboardType.Decimal)
                    Field("Other", other, { other = it }, keyboard = KeyboardType.Decimal)
                    Field("Adjustment (+/-)", adj, { adj = it }, keyboard = KeyboardType.Decimal)
                    Field("Adjustment note", note, { note = it })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val l = late.toDoubleOr0(); val s = sss.toDoubleOr0(); val h = phic.toDoubleOr0()
                        val g = pagibig.toDoubleOr0(); val o = other.toDoubleOr0(); val a = adj.toDoubleOr0()
                        TgmeApp.db.payroll().update(
                            p.copy(
                                lateDeduction = l, sssDeduction = s, philhealthDeduction = h, pagibigDeduction = g,
                                otherDeduction = o, adjustment = a, adjustmentNote = note.trim().ifBlank { null },
                                netPay = netPay(p.grossPay, l, s, h, g, o, a)
                            )
                        )
                        editing = false; onClose()
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } }
        )
    }
    if (confirmDelete) ConfirmDialog(
        "Delete payroll record", "Delete this payroll record?",
        onConfirm = { scope.launch { TgmeApp.db.payroll().delete(p.id); onClose() } },
        onDismiss = { confirmDelete = false }
    )
}
