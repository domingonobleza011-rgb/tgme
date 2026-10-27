package com.tgme.timekeeping.logic

import com.tgme.timekeeping.data.AppDatabase
import com.tgme.timekeeping.data.Employee
import com.tgme.timekeeping.data.Payroll
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class PayCalc(
    val present: Int, val late: Int, val absent: Int, val hours: Double,
    val gross: Double, val lateMinutesTotal: Int, val lateDeduction: Double
)

/** Monday-Saturday bounds. offset: 0 = this week, -1 = last week. */
fun weekBounds(offset: Int = 0, ref: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate> {
    val monday = ref.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong())
    return monday to monday.plusDays(5)
}

fun mondayOf(date: String): LocalDate =
    LocalDate.parse(date).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** Port of compute_pay(): Sundays skipped; a day with no record counts as Absent. */
suspend fun computePay(db: AppDatabase, emp: Employee, start: String, end: String, lateRate: Double): PayCalc {
    val byDate = db.attendance().range(emp.id, start, end).associateBy { it.attDate }
    var present = 0; var late = 0; var absent = 0; var hours = 0.0; var lateMin = 0

    var d = LocalDate.parse(start)
    val last = LocalDate.parse(end)
    while (!d.isAfter(last)) {
        if (d.dayOfWeek != DayOfWeek.SUNDAY) {
            val r = byDate[d.toString()]
            when {
                r == null -> absent++
                r.status == "Present" -> { present++; hours += r.hoursWorked }
                r.status == "Late" -> { present++; late++; hours += r.hoursWorked; lateMin += r.lateMinutes }
                r.status == "Absent" -> absent++
                else -> Unit // On Leave / Half Day: not counted against attendance
            }
        }
        d = d.plusDays(1)
    }
    val gross = present * emp.dailyRate
    return PayCalc(present, late, absent, hours, gross, lateMin, round2(lateMin * lateRate))
}

fun netPay(gross: Double, late: Double, sss: Double, phic: Double, pagibig: Double, other: Double, adj: Double) =
    round2(gross - late - sss - phic - pagibig - other + adj)

/** Port of store_payroll_for_period(): upsert on (emp, period). Returns rows stored. */
suspend fun storePayrollForPeriod(
    db: AppDatabase, employees: List<Employee>, start: String, end: String, lateRate: Double,
    sss: Double = 0.0, phic: Double = 0.0, pagibig: Double = 0.0, other: Double = 0.0,
    adjustment: Double = 0.0, note: String = "", onlyWithAttendance: Boolean = false
): Int {
    var count = 0
    for (emp in employees) {
        val c = computePay(db, emp, start, end, lateRate)
        if (onlyWithAttendance && c.present <= 0 && c.late <= 0) continue
        db.payroll().upsert(
            Payroll(
                empId = emp.id, periodStart = start, periodEnd = end,
                daysPresent = c.present, daysLate = c.late, daysAbsent = c.absent,
                totalHours = c.hours, dailyRate = emp.dailyRate, grossPay = c.gross,
                lateDeduction = c.lateDeduction, sssDeduction = sss, philhealthDeduction = phic,
                pagibigDeduction = pagibig, otherDeduction = other, adjustment = adjustment,
                adjustmentNote = note.ifBlank { null },
                netPay = netPay(c.gross, c.lateDeduction, sss, phic, pagibig, other, adjustment)
            )
        )
        count++
    }
    return count
}

/** Auto-store the last completed week for employees who actually worked (once). */
suspend fun autoStoreLastWeek(db: AppDatabase) {
    val (mon, sat) = weekBounds(-1)
    if (!LocalDate.now().isAfter(sat)) return
    val emps = db.employees().activeList()
    if (emps.isEmpty()) return
    if (db.payroll().workedCount(mon.toString(), sat.toString()) > 0) return
    val rate = ensureSettings(db).lateDeductionPerMinute
    storePayrollForPeriod(db, emps, mon.toString(), sat.toString(), rate, onlyWithAttendance = true)
}
