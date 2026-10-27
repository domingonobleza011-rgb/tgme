# TGME Timekeeping (Android: Kotlin + Room)

Offline port of the PHP/MySQL `tgme_system`. All data lives in an on-device SQLite DB (Room).

Open in Android Studio (Koala+), let Gradle sync, run on a device (camera needed for QR scan).

| PHP | Android |
|---|---|
| database.sql + alter_*.sql | data/Entities.kt, Daos.kt, AppDatabase.kt |
| install.php | SetupScreen (first run creates admin) |
| login_process / forgot_password | LoginScreen + recovery-PIN dialog (PBKDF2 hashing) |
| includes/attendance_logic.php | logic/AttendanceLogic.kt |
| payroll.php compute/store/auto-week | logic/PayrollLogic.kt + PayrollScreen |
| includes/sss.php | logic/Sss.kt |
| employee*.php, qr_codes.php | EmployeesScreen (QR shown per employee) |
| qr_scanner.php / kiosk.php | ScannerScreen (CameraX + ML Kit, offline) |
| attendance_log.php, export_csv, dtr_csv | AttendanceScreen / EmployeesScreen CSV share |
| settings.php, users.php | SettingsScreen, UsersScreen (admin only) |

Not ported: kiosk token/URL, PWA files (manifest.json, sw.js), avatar upload, web CSS.
Data is stored in the app's private storage; add a backup/export if you need to move it between devices.
