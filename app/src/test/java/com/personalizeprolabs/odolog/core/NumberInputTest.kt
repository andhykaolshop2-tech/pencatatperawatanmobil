package com.personalizeprolabs.odolog.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberInputTest {
    private val us = Locale.US
    private val de = Locale.GERMANY

    private fun assertParses(expected: Double, text: String, locale: Locale, max: Double = Double.MAX_VALUE) {
        val actual = NumberInput.parse(text, locale, max)
        assertEquals("\"$text\" in $locale", expected, actual ?: Double.NaN, 1e-9)
    }

    @Test
    fun plainNumbers() {
        assertParses(42.0, "42", us)
        assertParses(42.0, " 42 ", de)
        assertParses(0.0, "0", us)
    }

    @Test
    fun decimalPointAndComma() {
        assertParses(12.5, "12.5", us)
        assertParses(12.5, "12,5", de)
        // the "wrong" separator still works when it cannot be a thousands separator
        assertParses(12.5, "12,5", us)
        assertParses(12.5, "12.5", de)
        assertParses(45.12, "45.12", de)
        assertParses(0.5, ",5", de)
        assertParses(0.5, ".5", us)
    }

    @Test
    fun thousandsSeparatorFollowsTheLocale() {
        assertParses(45210.0, "45,210", us)     // odometer typed in the US
        assertParses(45210.0, "45.210", de)     // odometer typed in Germany
        assertParses(1.789, "1,789", de)        // decimal comma in Germany
        assertParses(10.234, "10.234", us)      // gallons with three decimals in the US
        assertParses(1234567.0, "1,234,567", us)
        assertParses(1234567.0, "1.234.567", de)
        assertParses(1234567.0, "1,234,567", de)
    }

    @Test
    fun bothSeparators() {
        assertParses(1234.56, "1,234.56", us)
        assertParses(1234.56, "1.234,56", de)
        // written the other way round
        assertParses(1234.56, "1.234,56", us)
        assertParses(1234.56, "1,234.56", de)
    }

    @Test
    fun spacesAndApostrophesAreGrouping() {
        assertParses(1234.5, "1 234,5", de)
        assertParses(1234.5, "1\u00A0234.5", us)
        assertParses(1234.5, "1'234.5", us)
    }

    @Test
    fun implausibleGroupingFallsBackToDecimal() {
        // 45.123 kWh on a German phone whose keyboard only offers a dot
        assertParses(45.123, "45.123", de, 1000.0)
        // without a limit the locale decides
        assertParses(45123.0, "45.123", de)
        // a plausible value is not touched
        assertParses(45210.0, "45,210", us, 10_000_000.0)
    }

    @Test
    fun rejectsEverythingElse() {
        assertNull(NumberInput.parse(null, us))
        assertNull(NumberInput.parse("", us))
        assertNull(NumberInput.parse("   ", us))
        assertNull(NumberInput.parse("abc", us))
        assertNull(NumberInput.parse("-5", us))
        assertNull(NumberInput.parse("1e5", us))
        assertNull(NumberInput.parse(".", us))
        assertNull(NumberInput.parse(",", de))
        assertNull(NumberInput.parse("1.2.3,4,5", us))
    }
}
