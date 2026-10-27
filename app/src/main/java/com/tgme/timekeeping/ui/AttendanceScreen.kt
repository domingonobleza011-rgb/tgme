package com.tgme.timekeeping.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tgme.timekeeping.TgmeApp
import java.time.LocalDate

@Composable
fun AttendanceScreen() {
    val ctx = LocalContext.current
    var date by remember { mutableStateOf(LocalDate.now()) }
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("All") }
    val rows by TgmeApp.db.attendance().forDate(date.toString()).collectAsState(emptyList())

    val shown = rows.filter {
        (status == "All" || it.status == status) &&
            (query.isBlank() || "${it.lastName} ${it.firstName} ${it.empNo}".contains(query, ignoreCase = true))
    }

    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { date = date.minusDays(1) }) { Text("◀ Prev") }
            Text(dateLabel(date.toString()), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { date = date.plusDays(1) }) { Text("Next ▶") }
        }
        Field("Search", query, { query = it })
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("All", "Present", "Late", "Absent").forEach { s ->
                FilterChip(selected = status == s, onClick = { status = s }, label = { Text(s) })
            }
        }
        TextButton(onClick = {
            val out = mutableListOf(listOf("Emp No", "Name", "Time In", "Time Out", "Hours", "Status"))
            shown.forEach { out += listOf(it.empNo, "${it.lastName}, ${it.firstName}", timeLabel(it.timeIn), timeLabel(it.timeOut), d2(it.hoursWorked), it.status) }
            shareCsv(ctx, "attendance_$date.csv", out)
        }) { Text("⇩ Export CSV") }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(shown, key = { it.empId }) { r ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text("${r.lastName}, ${r.firstName}  (${r.empNo})", style = MaterialTheme.typography.titleSmall)
                        Text("In ${timeLabel(r.timeIn)}  •  Out ${timeLabel(r.timeOut)}  •  ${d2(r.hoursWorked)} hrs")
                        Text(r.status, color = when (r.status) {
                            "Present" -> MaterialTheme.colorScheme.primary
                            "Late" -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.error
                        })
                    }
                }
            }
        }
    }
}
