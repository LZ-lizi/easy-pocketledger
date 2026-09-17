package com.pocketledger.domain

/**
 * Amount-field input rules, kept out of the view model so they are unit-testable
 * without an Android runtime.
 *
 * The two guards that actually matter are a bounded integer part and at most two
 * decimals; anything else the keypad can produce is valid by construction.
 */
object KeypadInput {

    const val MAX_INTEGER_DIGITS = 8          // up to 99,999,999 yuan
    const val MAX_DECIMALS = 2

    /**
     * Appends [digit] to the raw amount text.
     *
     * A lone leading `0` is replaced rather than extended, so "0" then "5" gives
     * "5" instead of "05".
     */
    fun appendDigit(current: String, digit: String): String = when {
        current.contains('.') -> {
            val decimals = current.substringAfter('.')
            if (decimals.length >= MAX_DECIMALS) current else current + digit
        }

        current == "0" -> digit
        current.length >= MAX_INTEGER_DIGITS -> current
        else -> current + digit
    }

    /** A second decimal point is ignored; a leading one becomes `0.`. */
    fun appendDecimalPoint(current: String): String = when {
        current.contains('.') -> current
        current.isEmpty() -> "0."
        else -> "$current."
    }
}
