package com.pocketledger.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class KeypadInputTest {

    @Test
    fun `appends digits normally`() {
        assertEquals("1", KeypadInput.appendDigit("", "1"))
        assertEquals("12", KeypadInput.appendDigit("1", "2"))
        assertEquals("123", KeypadInput.appendDigit("12", "3"))
    }

    @Test
    fun `replaces a lone leading zero`() {
        assertEquals("5", KeypadInput.appendDigit("0", "5"))
    }

    @Test
    fun `caps the integer part`() {
        val sevenDigits = "1234567"
        assertEquals("12345678", KeypadInput.appendDigit(sevenDigits, "8"))
        assertEquals("12345678", KeypadInput.appendDigit("12345678", "9"))
    }

    @Test
    fun `caps at two decimals`() {
        assertEquals("12.3", KeypadInput.appendDigit("12.", "3"))
        assertEquals("12.34", KeypadInput.appendDigit("12.3", "4"))
        assertEquals("12.34", KeypadInput.appendDigit("12.34", "5"))
    }

    @Test
    fun `decimal point is only accepted once`() {
        assertEquals("0.", KeypadInput.appendDecimalPoint(""))
        assertEquals("12.", KeypadInput.appendDecimalPoint("12"))
        assertEquals("12.3", KeypadInput.appendDecimalPoint("12.3"))
    }

    /** Whatever the keypad produces must be parseable by the money layer. */
    @Test
    fun `keypad output always parses`() {
        var text = ""
        for (key in listOf("1", "2", ".", "3", "4", "5")) {
            text = KeypadInput.appendDigit(text, key)
        }
        assertEquals("12.34", text)
        assertEquals(1234L, Money.parseYuanToCents(text))
    }
}
