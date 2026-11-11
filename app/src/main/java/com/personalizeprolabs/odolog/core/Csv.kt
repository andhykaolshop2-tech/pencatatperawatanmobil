package com.personalizeprolabs.odolog.core

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Minimal CSV reader / writer (RFC 4180 style quoting). */
object Csv {

    fun write(rows: List<List<String>>, delimiter: Char = ','): String {
        val sb = StringBuilder()
        for (row in rows) {
            for (i in row.indices) {
                if (i > 0) sb.append(delimiter)
                sb.append(escape(row[i], delimiter))
            }
            sb.append("\r\n")
        }
        return sb.toString()
    }

    private fun escape(cell: String, delimiter: Char): String {
        var needsQuotes = cell.startsWith(" ") || cell.endsWith(" ")
        if (!needsQuotes) {
            for (ch in cell) {
                if (ch == delimiter || ch == '"' || ch == '\n' || ch == '\r') {
                    needsQuotes = true
                    break
                }
            }
        }
        return if (needsQuotes) "\"" + cell.replace("\"", "\"\"") + "\"" else cell
    }

    /**
     * Parses CSV text into rows of cells. Understands quoted cells (with line breaks and
     * doubled quotes inside), a leading byte order mark, an Excel "sep=;" first line and
     * detects the delimiter (comma, semicolon or tab) from the header line. Blank lines
     * are skipped.
     */
    fun parse(text: String): List<List<String>> {
        var s = text
        if (s.startsWith("\uFEFF")) s = s.substring(1)

        var delimiter: Char? = null
        if (s.length >= 5 && s.substring(0, 4).equals("sep=", ignoreCase = true)) {
            var lineEnd = s.length
            for (i in s.indices) {
                if (s[i] == '\n' || s[i] == '\r') {
                    lineEnd = i
                    break
                }
            }
            if (lineEnd == 5) {
                delimiter = s[4]
                s = s.substring(lineEnd)
            }
        }
        val d = delimiter ?: detectDelimiter(s)

        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var rowHasContent = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    cell.append(c)
                }
            } else if (c == '"' && cell.isEmpty()) {
                inQuotes = true
                rowHasContent = true
            } else if (c == d) {
                row.add(cell.toString())
                cell.setLength(0)
                rowHasContent = true
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < s.length && s[i + 1] == '\n') i++
                if (rowHasContent || cell.isNotEmpty()) {
                    row.add(cell.toString())
                    rows.add(row)
                }
                row = ArrayList()
                cell.setLength(0)
                rowHasContent = false
            } else {
                cell.append(c)
            }
            i++
        }
        if (rowHasContent || cell.isNotEmpty()) {
            row.add(cell.toString())
            rows.add(row)
        }
        return rows
    }

    /** Counts candidate delimiters outside quotes in the first non-empty line. */
    private fun detectDelimiter(s: String): Char {
        var commas = 0
        var semicolons = 0
        var tabs = 0
        var inQuotes = false
        var seenContent = false
        for (c in s) {
            if (c == '"') {
                inQuotes = !inQuotes
                seenContent = true
            } else if (!inQuotes) {
                if (c == '\n' || c == '\r') {
                    if (seenContent) break else continue
                }
                seenContent = true
                when (c) {
                    ',' -> commas++
                    ';' -> semicolons++
                    '\t' -> tabs++
                }
            }
        }
        return when {
            semicolons > commas && semicolons >= tabs -> ';'
            tabs > commas && tabs > semicolons -> '\t'
            else -> ','
        }
    }

    /**
     * Decodes the bytes of a text file. UTF-8 is tried first (strictly); files that are
     * not valid UTF-8 are read as UTF-16 when they carry that byte order mark (Excel
     * "Unicode text") and otherwise as Windows-1252 (Excel "CSV" on Western systems).
     */
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if ((b0 == 0xFF && b1 == 0xFE) || (b0 == 0xFE && b1 == 0xFF)) {
                return String(bytes, StandardCharsets.UTF_16)
            }
        }
        return try {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            val fallback = try {
                Charset.forName("windows-1252")
            } catch (e2: Exception) {
                StandardCharsets.ISO_8859_1
            }
            String(bytes, fallback)
        }
    }

    /** UTF-8 with byte order mark, so that spreadsheet programs show umlauts correctly. */
    fun encode(text: String): ByteArray {
        val body = text.toByteArray(StandardCharsets.UTF_8)
        val out = ByteArray(body.size + 3)
        out[0] = 0xEF.toByte()
        out[1] = 0xBB.toByte()
        out[2] = 0xBF.toByte()
        System.arraycopy(body, 0, out, 3, body.size)
        return out
    }
}
