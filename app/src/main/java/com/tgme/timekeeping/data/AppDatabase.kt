package com.tgme.timekeeping.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [User::class, Settings::class, Employee::class, Attendance::class, Payroll::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun users(): UserDao
    abstract fun settings(): SettingsDao
    abstract fun employees(): EmployeeDao
    abstract fun attendance(): AttendanceDao
    abstract fun payroll(): PayrollDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "tgme_timekeeping.db").build()
    }
}
