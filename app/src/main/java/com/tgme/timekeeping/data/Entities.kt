package com.tgme.timekeeping.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index("username", unique = true), Index("sssNumber", unique = true)]
)
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val passwordHash: String,
    val recoveryPin: String,
    val fullName: String,
    val role: String = "TGME Authorized Personnel",
    val isAdmin: Boolean = false,
    val sssNumber: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val id: Int = 1,
    val companyName: String = "TGME Construction and Supply",
    val branchLocation: String = "",
    val workStartTime: String = "08:00:00",
    val workEndTime: String = "17:00:00",
    val graceMinutes: Int = 10,
    val breakMinutes: Int = 60,
    val workDays: String = "Monday to Saturday",
    val lateDeductionPerMinute: Double = 0.0
)

@Entity(
    tableName = "employees",
    indices = [
        Index("empNo", unique = true),
        Index("qrToken", unique = true),
        Index("sssNumber", unique = true)
    ]
)
data class Employee(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val empNo: String,
    val lastName: String,
    val firstName: String,
    val department: String = "",
    val position: String = "",
    val contact: String = "",
    val sssNumber: String? = null,
    val dailyRate: Double = 610.0,
    val status: String = "Active", // Active | Inactive
    val qrToken: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "attendance",
    foreignKeys = [ForeignKey(
        entity = Employee::class, parentColumns = ["id"],
        childColumns = ["empId"], onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["empId", "attDate"], unique = true)]
)
data class Attendance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val empId: Long,
    val attDate: String,          // yyyy-MM-dd
    val timeIn: String? = null,   // HH:mm:ss
    val timeOut: String? = null,
    val hoursWorked: Double = 0.0,
    val status: String = "Present", // Present | Late | Absent | On Leave | Half Day
    val lateMinutes: Int = 0
)

@Entity(
    tableName = "payroll",
    foreignKeys = [ForeignKey(
        entity = Employee::class, parentColumns = ["id"],
        childColumns = ["empId"], onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["empId", "periodStart", "periodEnd"], unique = true)]
)
data class Payroll(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val empId: Long,
    val periodStart: String,
    val periodEnd: String,
    val daysPresent: Int = 0,
    val daysLate: Int = 0,
    val daysAbsent: Int = 0,
    val totalHours: Double = 0.0,
    val dailyRate: Double = 0.0,
    val grossPay: Double = 0.0,
    val lateDeduction: Double = 0.0,
    val sssDeduction: Double = 0.0,
    val philhealthDeduction: Double = 0.0,
    val pagibigDeduction: Double = 0.0,
    val otherDeduction: Double = 0.0,
    val adjustment: Double = 0.0,
    val adjustmentNote: String? = null,
    val netPay: Double = 0.0,
    val generatedAt: Long = System.currentTimeMillis()
)

/** Joined rows used by the UI. */
data class AttendanceRow(
    val empId: Long,
    val empNo: String,
    val lastName: String,
    val firstName: String,
    val timeIn: String?,
    val timeOut: String?,
    val hoursWorked: Double,
    val status: String
)

data class ScanEvent(val lastName: String, val firstName: String, val timeIn: String?, val timeOut: String?)

data class PayrollRow(
    @Embedded val p: Payroll,
    val lastName: String,
    val firstName: String,
    val empNo: String
)
