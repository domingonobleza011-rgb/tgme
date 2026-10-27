package com.tgme.timekeeping.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert suspend fun insert(u: User): Long
    @Update suspend fun update(u: User)
    @Query("SELECT COUNT(*) FROM users") suspend fun count(): Int
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1") suspend fun byUsername(username: String): User?
    @Query("SELECT * FROM users WHERE id = :id") suspend fun byId(id: Long): User?
    @Query("SELECT * FROM users ORDER BY id") fun all(): Flow<List<User>>
    @Query("SELECT COUNT(*) FROM users WHERE sssNumber = :sss AND id != :exceptId") suspend fun sssInUse(sss: String, exceptId: Long): Int
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1") suspend fun get(): Settings?
    @Query("SELECT * FROM settings WHERE id = 1") fun flow(): Flow<Settings?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(s: Settings)
}

@Dao
interface EmployeeDao {
    @Insert suspend fun insert(e: Employee): Long
    @Update suspend fun update(e: Employee)
    @Query("DELETE FROM employees WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM employees WHERE id = :id") suspend fun byId(id: Long): Employee?
    @Query("SELECT * FROM employees WHERE qrToken = :token AND status = 'Active' LIMIT 1") suspend fun byActiveToken(token: String): Employee?
    @Query("SELECT * FROM employees ORDER BY lastName, firstName") fun all(): Flow<List<Employee>>
    @Query("SELECT * FROM employees WHERE status = 'Active' ORDER BY lastName, firstName") suspend fun activeList(): List<Employee>
    @Query("SELECT COUNT(*) FROM employees WHERE status = 'Active'") fun activeCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM employees WHERE sssNumber = :sss AND id != :exceptId") suspend fun sssInUse(sss: String, exceptId: Long): Int
    @Query("SELECT COUNT(*) FROM employees WHERE empNo = :empNo AND id != :exceptId") suspend fun empNoInUse(empNo: String, exceptId: Long): Int
}

@Dao
interface AttendanceDao {
    @Insert suspend fun insert(a: Attendance): Long
    @Update suspend fun update(a: Attendance)
    @Query("SELECT * FROM attendance WHERE empId = :empId AND attDate = :date") suspend fun byEmpDate(empId: Long, date: String): Attendance?
    @Query("SELECT * FROM attendance WHERE empId = :empId AND attDate BETWEEN :start AND :end") suspend fun range(empId: Long, start: String, end: String): List<Attendance>
    @Query("SELECT * FROM attendance WHERE empId = :empId AND attDate LIKE :monthPrefix || '%' ORDER BY attDate") suspend fun month(empId: Long, monthPrefix: String): List<Attendance>

    @Query("""
        SELECT e.id AS empId, e.empNo AS empNo, e.lastName AS lastName, e.firstName AS firstName,
               a.timeIn AS timeIn, a.timeOut AS timeOut,
               COALESCE(a.hoursWorked, 0) AS hoursWorked, COALESCE(a.status, 'Absent') AS status
        FROM employees e
        LEFT JOIN attendance a ON a.empId = e.id AND a.attDate = :date
        WHERE e.status = 'Active'
        ORDER BY e.lastName, e.firstName
    """)
    fun forDate(date: String): Flow<List<AttendanceRow>>

    @Query("""
        SELECT e.lastName AS lastName, e.firstName AS firstName, a.timeIn AS timeIn, a.timeOut AS timeOut
        FROM attendance a JOIN employees e ON e.id = a.empId
        WHERE a.attDate = :date AND (a.timeIn IS NOT NULL OR a.timeOut IS NOT NULL)
    """)
    fun scansForDate(date: String): Flow<List<ScanEvent>>

    @Query("SELECT COUNT(*) FROM attendance WHERE attDate = :date AND status = :status") fun countStatus(date: String, status: String): Flow<Int>
}

@Dao
interface PayrollDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(p: Payroll)
    @Update suspend fun update(p: Payroll)
    @Query("DELETE FROM payroll WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM payroll WHERE id = :id") suspend fun byId(id: Long): Payroll?
    @Query("SELECT COUNT(*) FROM payroll WHERE periodStart = :start AND periodEnd = :end AND daysPresent > 0")
    suspend fun workedCount(start: String, end: String): Int

    @Query("""
        SELECT p.*, e.lastName AS lastName, e.firstName AS firstName, e.empNo AS empNo
        FROM payroll p JOIN employees e ON e.id = p.empId
        ORDER BY p.periodStart DESC, e.lastName
    """)
    fun allWithEmployee(): Flow<List<PayrollRow>>
}
