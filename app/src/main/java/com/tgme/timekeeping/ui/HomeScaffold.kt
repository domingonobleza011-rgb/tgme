package com.tgme.timekeeping.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Scan("Scan", Icons.Default.Search),
    Employees("Employees", Icons.Default.Person),
    Attendance("Attendance", Icons.Default.DateRange),
    Payroll("Payroll", Icons.Default.List)
}

private enum class Extra { Settings, Users }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScaffold() {
    var tab by remember { mutableStateOf(Tab.Home) }
    var extra by remember { mutableStateOf<Extra?>(null) }
    var menu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(extra?.name ?: tab.label) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyDark, titleContentColor = Color.White, actionIconContentColor = Color.White),
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Company settings") }, onClick = { menu = false; extra = Extra.Settings })
                        DropdownMenuItem(text = { Text("User accounts") }, onClick = { menu = false; extra = Extra.Users })
                        DropdownMenuItem(text = { Text("Log out") }, onClick = { menu = false; Session.user = null })
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.values().forEach { t ->
                    NavigationBarItem(
                        selected = extra == null && tab == t,
                        onClick = { extra = null; tab = t },
                        icon = { Icon(t.icon, t.label) }, label = { Text(t.label) }
                    )
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            when (extra) {
                Extra.Settings -> SettingsScreen()
                Extra.Users -> UsersScreen()
                null -> when (tab) {
                    Tab.Home -> DashboardScreen()
                    Tab.Scan -> ScannerScreen()
                    Tab.Employees -> EmployeesScreen()
                    Tab.Attendance -> AttendanceScreen()
                    Tab.Payroll -> PayrollScreen()
                }
            }
        }
    }
}
