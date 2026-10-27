package com.tgme.timekeeping.logic

/** Port of includes/sss.php normalize_sss(). Blank is allowed (null). */
object Sss {
    data class Result(val ok: Boolean, val value: String?, val error: String = "")

    fun normalize(raw: String): Result {
        val t = raw.trim()
        if (t.isEmpty()) return Result(true, null)
        val digits = t.filter { it.isDigit() }
        if (t.any { !(it.isDigit() || it == '-' || it.isWhitespace()) } || digits.length != 10) {
            return Result(false, null, "SSS number must be 10 digits, e.g. 12-3456789-0.")
        }
        return Result(true, "${digits.substring(0, 2)}-${digits.substring(2, 9)}-${digits[9]}")
    }
}
