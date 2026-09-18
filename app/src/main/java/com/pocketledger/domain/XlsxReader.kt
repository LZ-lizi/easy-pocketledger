package com.pocketledger.domain

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * A deliberately small .xlsx reader.
 *
 * An .xlsx is a zip of XML, and a bill only ever needs two of those parts: the shared
 * string table and one sheet. Every general-purpose spreadsheet library on Android is
 * either enormous or a decade stale, and the whole job here is "produce the same
 * `List<List<String>>` the CSV reader produces", so the CSV column matching can then be
 * reused unchanged -- one set of rules for both file types means the two formats cannot
 * drift apart.
 *
 * What it does **not** do: styles, formulas (their cached values are read instead),
 * multiple sheets, or dates-as-serials on its own. A cell is turned into the text a
 * human would have typed where that is possible; anything else is left exactly as the
 * file wrote it, and [CsvImport] decides whether it means anything.
 */
object XlsxReader {

    private const val SHARED_STRINGS = "xl/sharedStrings.xml"
    private const val SHEET_PREFIX = "xl/worksheets/sheet"

    /** Guards against a zip that expands into something the phone cannot hold. */
    private const val MAX_ENTRY_BYTES = 24 * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 48 * 1024 * 1024

    /**
     * True when the bytes are a zip container.
     *
     * Checked by signature rather than by file name: Android's document picker happily
     * hands back `application/octet-stream` and a name with no extension, and sniffing
     * four bytes is cheaper than explaining to the user why their file was not read.
     */
    fun looksLikeZip(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val p = bytes[0].toInt() and 0xFF
        val k = bytes[1].toInt() and 0xFF
        return p == 0x50 && k == 0x4B
    }

    /**
     * Reads the first worksheet into a table of strings.
     *
     * Returns an empty table when the bytes are not a usable workbook, which the caller
     * reports as "no records found" rather than as a crash.
     */
    fun read(bytes: ByteArray): List<List<String>> {
        val parts = unzip(bytes)
        if (parts.isEmpty()) return emptyList()

        val shared = parts[SHARED_STRINGS]?.let(::parseSharedStrings).orEmpty()
        val sheet = parts.entries
            .filter { it.key.startsWith(SHEET_PREFIX) && it.key.endsWith(".xml") }
            .minByOrNull { it.key }
            ?.value
            ?: return emptyList()

        return parseSheet(sheet, shared)
    }

    // ------------------------------------------------------------------ internals

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val parts = mutableMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (entry.isDirectory || !WANTED.matches(name)) {
                    zip.closeEntry()
                    continue
                }
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                var size = 0
                while (true) {
                    val read = zip.read(chunk)
                    if (read <= 0) break
                    size += read
                    if (size > MAX_ENTRY_BYTES) break
                    buffer.write(chunk, 0, read)
                }
                zip.closeEntry()
                if (size > MAX_ENTRY_BYTES) continue
                total += size
                if (total > MAX_TOTAL_BYTES) break
                parts[name] = buffer.toByteArray()
            }
        }
        return parts
    }

    private val WANTED = Regex("""xl/(sharedStrings\.xml|worksheets/sheet[^/]*\.xml)""")

    /** Every `<si>` in document order; a string may be split across several runs. */
    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val doc = parse(xml) ?: return emptyList()
        val items = doc.getElementsByTagName("si")
        return (0 until items.length).map { index ->
            items.item(index).textOfDescendants("t")
        }
    }

    private fun parseSheet(xml: ByteArray, shared: List<String>): List<List<String>> {
        val doc = parse(xml) ?: return emptyList()
        val rows = doc.getElementsByTagName("row")
        val table = ArrayList<List<String>>(rows.length)

        for (index in 0 until rows.length) {
            val row = rows.item(index) as? Element ?: continue
            val cells = sortedMapOf<Int, String>()
            val nodes = row.getElementsByTagName("c")
            for (cellIndex in 0 until nodes.length) {
                val cell = nodes.item(cellIndex) as? Element ?: continue
                val reference = cell.getAttribute("r")
                // Cells the user never touched are simply absent from the XML, so a
                // row's columns can only be placed by the reference ("C7" -> 2).
                val column = if (reference.isNotEmpty()) {
                    columnIndex(reference)
                } else {
                    (cells.keys.maxOrNull() ?: -1) + 1
                }
                if (column < 0) continue
                cells[column] = cellText(cell, shared)
            }
            val width = (cells.keys.maxOrNull() ?: -1) + 1
            table.add(List(width) { cells[it] ?: "" })
        }
        return table
    }

    private fun cellText(cell: Element, shared: List<String>): String = when (cell.getAttribute("t")) {
        // A shared string is an index into sharedStrings.xml.
        "s" -> shared.getOrNull(cell.childText("v")?.trim()?.toIntOrNull() ?: -1).orEmpty()
        // Rich text written inline, possibly across several runs.
        "inlineStr" -> cell.textOfDescendants("t")
        // A formula's cached string result.
        "str" -> cell.childText("v").orEmpty()
        "b" -> if (cell.childText("v") == "1") "是" else "否"
        // Numbers, including the serial dates and amounts.
        else -> cell.childText("v").orEmpty()
    }

    /** `C7` -> 2; `AA1` -> 26. */
    private fun columnIndex(reference: String): Int {
        var index = 0
        var letters = 0
        for (char in reference) {
            if (!char.isLetter()) break
            index = index * 26 + (char.uppercaseChar() - 'A' + 1)
            letters++
        }
        return if (letters == 0) -1 else index - 1
    }

    /**
     * Parses XML with external entity resolution switched off.
     *
     * These files come from outside the app, so an XXE payload is a real (if unlikely)
     * concern; every hardening flag is set best-effort because not all of them exist on
     * every platform's parser.
     */
    private fun parse(xml: ByteArray): org.w3c.dom.Document? = runCatching {
        val factory = DocumentBuilderFactory.newInstance()
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        runCatching { factory.isExpandEntityReferences = false }
        runCatching { factory.isNamespaceAware = false }
        factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
    }.getOrNull()

    /** Concatenates the text of every descendant element with the given tag. */
    private fun Node.textOfDescendants(tag: String): String {
        val elements = (this as? org.w3c.dom.Document)?.getElementsByTagName(tag)
            ?: (this as? Element)?.getElementsByTagName(tag)
            ?: return ""
        return buildString {
            for (index in 0 until elements.length) {
                append(elements.item(index).textContent.orEmpty())
            }
        }
    }

    /** The text of the first direct child element with the given tag. */
    private fun Element.childText(tag: String): String? {
        val children = childNodes
        for (index in 0 until children.length) {
            val node = children.item(index)
            if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == tag) {
                return node.textContent
            }
        }
        return null
    }
}
