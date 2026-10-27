package com.tgme.timekeeping.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tgme.timekeeping.TgmeApp
import java.time.LocalDate
import kotlin.math.max

@Composable
fun DashboardScreen() {
    val db = TgmeApp.db
    val today = LocalDate.now().toString()
    val total by db.employees().activeCount().collectAsState(0)
    val present by db.attendance().countStatus(today, "Present").collectAsState(0)
    val late by db.attendance().countStatus(today, "Late").collectAsState(0)
    val absent = max(0, total - present - late)

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Welcome back, ${Session.user?.fullName ?: ""}", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("$present", "Present Today", Modifier.weight(1f))
            StatCard("$late", "Late Arrivals", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("$absent", "Absent Today", Modifier.weight(1f))
            StatCard("$total", "Total Employees", Modifier.weight(1f))
        }
    }
}
