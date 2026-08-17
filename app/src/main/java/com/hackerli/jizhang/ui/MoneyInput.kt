package com.hackerli.jizhang.ui

object MoneyInput {
    private const val MAX_WHOLE_DIGITS = 6

    fun appendDigit(current: String, digit: Char): String {
        require(digit in '0'..'9')
        val whole = current.ifEmpty { "0" }
        if (whole.length >= MAX_WHOLE_DIGITS && !(whole == "0" && digit != '0')) return current
        return if (whole == "0") digit.toString() else whole + digit
    }

    fun backspace(current: String): String = current.dropLast(1)

    fun toYuan(current: String): Long = current.toLongOrNull() ?: 0L
}
