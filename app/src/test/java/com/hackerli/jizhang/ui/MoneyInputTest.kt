package com.hackerli.jizhang.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyInputTest {
    @Test
    fun digitsRepresentWholeYuan() {
        var input = ""
        input = MoneyInput.appendDigit(input, '1')
        input = MoneyInput.appendDigit(input, '2')
        assertEquals("12", input)
        assertEquals(12L, MoneyInput.toYuan(input))
    }

    @Test
    fun inputIsLimitedToSixDigits() {
        assertEquals("123456", MoneyInput.appendDigit("123456", '7'))
    }

    @Test
    fun leadingZeroIsReplaced() {
        assertEquals("8", MoneyInput.appendDigit("0", '8'))
        assertEquals("0", MoneyInput.appendDigit("", '0'))
    }

    @Test
    fun backspaceCanClearInput() {
        assertEquals("", MoneyInput.backspace("7"))
    }
}
