package com.personalizeprolabs.odolog.core

import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Turns what a person typed into a number field into a Double.
 *
 * People type "12.5" and "12,5", and they type thousands separators ("45,210 mi",
 * "45.210 km", "1 234,50"). The device locale decides how an ambiguous text such as
 * "1,234" is read (1234 in the US/UK, 1.234 in Germany).
 */
object NumberInput {

    /**
     * @param maxPlausible upper limit for the field (for example 1000 for litres). When the
     *   normal reading of an ambiguous text is above the limit, the other reading is used:
     *   "45.123" kWh on a German phone is 45.123 and not 45123.
     * @return the number, or null when the text is empty, negative or not a number.
     */
    fun parse(text: String?, locale: Locale, maxPlausible: Double = Double.MAX_VALUE): Double? {
        if (text == null) return null
        val sb = StringBuilder()
        for (ch in text) {
            when (ch) {
                // whitespace and apostrophes are only ever used for grouping
                ' ', '\t', '\u00A0', '\u202F', '\u2009', '\'', '\u2019' -> Unit
                else -> sb.append(ch)
            }
        }
        val s = sb.toString()
        if (s.isEmpty()) return null
        for (ch in s) {
            if (!(ch in '0'..'9' || ch == '.' || ch == ',')) return null
        }

        val localeDecimal = try {
            DecimalFormatSymbols.getInstance(locale).decimalSeparator
        } catch (e: Exception) {
            '.'
        }
        val dec = if (localeDecimal == ',') ',' else '.'
        val grp = if (dec == '.') ',' else '.'
        val nDec = s.count { it == dec }
        val nGrp = s.count { it == grp }

        val normalized: String = when {
            nDec == 0 && nGrp == 0 -> s
            nDec == 1 && nGrp == 0 -> s.replace(dec, '.')
            nDec == 0 && nGrp == 1 -> {
                val asGrouping = s.replace(grp.toString(), "")
                val asDecimal = s.replace(grp, '.')
                if (looksLikeGrouping(s, grp)) {
                    val value = asGrouping.toDoubleOrNull()
                    if (value != null && value > maxPlausible) asDecimal else asGrouping
                } else {
                    asDecimal
                }
            }
            nDec == 0 -> s.replace(grp.toString(), "")          // 1,234,567
            nGrp == 0 -> s.replace(dec.toString(), "")          // decimal char used for grouping
            else -> {
                val lastDec = s.lastIndexOf(dec)
                val lastGrp = s.lastIndexOf(grp)
                if (nDec == 1 && lastGrp < lastDec) {
                    // normal for this locale: 1,234.56 (US) or 1.234,56 (DE)
                    s.replace(grp.toString(), "").replace(dec, '.')
                } else if (nGrp == 1 && lastDec < lastGrp) {
                    // written the other way round: 1.234,56 typed on a US phone
                    s.replace(dec.toString(), "").replace(grp, '.')
                } else {
                    return null
                }
            }
        }
        if (normalized.isEmpty() || normalized == ".") return null
        val value = normalized.toDoubleOrNull() ?: return null
        return if (value.isNaN() || value.isInfinite()) null else value
    }

    /** "45,210" or "1.234": one to three digits, the separator, exactly three digits. */
    private fun looksLikeGrouping(s: String, separator: Char): Boolean {
        val index = s.indexOf(separator)
        val before = index
        val after = s.length - index - 1
        return before in 1..3 && after == 3
    }
}
