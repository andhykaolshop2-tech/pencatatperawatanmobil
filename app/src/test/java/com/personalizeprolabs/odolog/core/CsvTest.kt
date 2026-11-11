package com.personalizeprolabs.odolog.core

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvTest {

    @Test
    fun writeQuotesOnlyWhenNeeded() {
        val text = Csv.write(listOf(listOf("a", "b c", "d,e", "say \"hi\"", "line1\nline2", " padded ", "")))
        assertEquals("a,b c,\"d,e\",\"say \"\"hi\"\"\",\"line1\nline2\",\" padded \",\r\n", text)
    }

    @Test
    fun roundTrip() {
        val rows = listOf(
            listOf("record", "title", "note"),
            listOf("expense", "Ölwechsel, Filter", "Werkstatt \"Müller\"\nzweite Zeile"),
            listOf("fuel", "", ""),
            listOf("reminder", " leading and trailing ", "semi;colon"),
        )
        assertEquals(rows, Csv.parse(Csv.write(rows)))
    }

    @Test
    fun parseHandlesLineEndingsAndBlankLines() {
        val rows = Csv.parse("a,b\r\n\r\nc,d\n\ne,f\rg,h")
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d"), listOf("e", "f"), listOf("g", "h")), rows)
    }

    @Test
    fun parseKeepsEmptyCells() {
        assertEquals(listOf(listOf("a", "", "c", "")), Csv.parse("a,,c,\n"))
        assertEquals(listOf(listOf("", "")), Csv.parse(",\n"))
        assertEquals(listOf(listOf("")), Csv.parse("\"\"\n"))
        assertTrue(Csv.parse("").isEmpty())
        assertTrue(Csv.parse("\r\n\r\n").isEmpty())
    }

    @Test
    fun parseDetectsSemicolonAndTab() {
        assertEquals(listOf(listOf("a", "b,c", "d"), listOf("1,5", "2", "3")), Csv.parse("a;\"b,c\";d\n1,5;2;3\n"))
        assertEquals(listOf(listOf("a", "b"), listOf("1,5", "2")), Csv.parse("a\tb\n1,5\t2\n"))
    }

    @Test
    fun parseSkipsByteOrderMarkAndSeparatorHint() {
        assertEquals(listOf(listOf("a", "b")), Csv.parse("\uFEFFa,b\n"))
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), Csv.parse("sep=;\r\na;b\r\n1;2\r\n"))
    }

    @Test
    fun encodeAddsByteOrderMarkAndDecodeReadsItBack() {
        val bytes = Csv.encode("Ölwechsel €")
        assertEquals(0xEF, bytes[0].toInt() and 0xFF)
        assertEquals(0xBB, bytes[1].toInt() and 0xFF)
        assertEquals(0xBF, bytes[2].toInt() and 0xFF)
        assertEquals(listOf(listOf("Ölwechsel €")), Csv.parse(Csv.decode(bytes)))
    }

    @Test
    fun decodeFallsBackForOtherEncodings() {
        val text = "Ölwechsel;Größe\r\n"
        assertEquals(text, Csv.decode(text.toByteArray(StandardCharsets.UTF_8)))
        assertEquals(text, Csv.decode(text.toByteArray(Charset.forName("windows-1252"))))
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(StandardCharsets.UTF_16LE)
        assertEquals(text, Csv.decode(utf16))
    }
}
