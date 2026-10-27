package com.tgme.timekeeping.logic

import com.tgme.timekeeping.data.AppDatabase
import com.tgme.timekeeping.data.Attendance
import com.tgme.timekeeping.data.Settings
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

data class ScanResult(val ok: Boolean, val message: String, val action: String? = null)

val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
val LABEL_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm:ss a")

fun round2(v: Double) = Math.round(v * 100.0) / 100.0

suspend fun ensureSettings(db: AppDatabase): Settings =
    db.settings().get() ?: Settings().also { db.settings().save(it) }

/** Port of includes/attendance_logic.php record_scan(): first scan = time in, second = time out. */
suspend fun recordScan(db: AppDatabase, tokenRaw: String): ScanResult {
    val token = tokenRaw.trim()
    if (token.isEmpty()) return ScanResult(false, "No QR token received.")

    val emp = db.employees().byActiveToken(token)
        ?: return ScanResult(false, "QR code not recognized or employee is inactive.")

    val s = ensureSettings(db)
    val now = LocalTime.now().withNano(0)
    val today = LocalDate.now().toString()
    val nowStr = now.format(TIME_FMT)
    val nowLabel = now.format(LABEL_FMT)
    val workStart = LocalTime.parse(s.workStartTime)
    val name = "${emp.lastName}, ${emp.firstName}"

    val row = db.attendance().byEmpDate(emp.id, today)
    return when {
        row == null -> {
            val cutoff = workStart.plusMinutes(s.graceMinutes.toLong())
            val late = now.isAfter(cutoff)
            val status = if (late) "Late" else "Present"
            val lateMin = if (late) max(0L, Math.round(Duration.between(workStart, now).seconds / 60.0)).toInt() else 0
            db.attendance().insert(
                Attendance(empId = emp.id, attDate = today, timeIn = nowStr, status = status, lateMinutes = lateMin)
            )
            ScanResult(true, "$name timed IN at $nowLabel" + if (late) " (LATE, $lateMin min)" else "", "time_in")
        }
        row.timeOut == null -> {
            val seconds = Duration.between(LocalTime.parse(row.timeIn ?: nowStr), now).seconds
            val hours = round2(max(0.0, seconds / 3600.0 - s.breakMinutes / 60.0))
            db.attendance().update(row.copy(timeOut = nowStr, hoursWorked = hours))
            ScanResult(true, "$name timed OUT at $nowLabel ($hours hrs worked)", "time_out")
        }
        else -> ScanResult(false, "$name has already timed in and out for today.")
    }
}
