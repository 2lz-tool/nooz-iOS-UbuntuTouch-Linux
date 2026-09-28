package xyz.mdhv.riverwip.model

/**
 * A small, strict XML reader for feeds, replacing the JDK DOM parser
 * (`javax.xml`) that `FeedParser` used before, so this module builds for
 * Kotlin/Native (iOS, Ubuntu Touch) as well as the JVM.
 *
 * It is safe against hostile input *by construction* rather than by hardening
 * flags: DOCTYPE declarations are rejected outright, and the only entities
 * recognised are the five predefined ones and numeric character references, so
 * there is nothing to expand (no XXE, no billion-laughs). Nesting depth is
 * capped.
 *
 * Like the JDK parser it replaces it is strict about structure (mismatched
 * tags, duplicate attributes, undeclared entities such as `&nbsp;`, content
 * after the root all throw [XmlParseException]) and `FeedParser` maps any
 * failure to "empty feed". Two deliberate differences, both toward accepting
 * more real-world feeds: XML-illegal control characters are passed through
 * rather than rejected, and namespace prefixes are not required to be declared
 * (element names are matched on their local part anyway).
 */
internal class XmlParseException(message: String) : Exception(message)

internal sealed interface XmlNode

internal class XmlText(val text: String) : XmlNode

internal class XmlElement(
    /** Qualified name as written, e.g. `dc:creator`. */
    val name: String,
    val attributes: Map<String, String>,
) : XmlNode {
    val children: MutableList<XmlNode> = ArrayList()

    /** Name without any namespace prefix. */
    val localName: String get() = name.substringAfter(':')

    val childElements: List<XmlElement> get() = children.filterIsInstance<XmlElement>()

    /** Concatenated text of every descendant text node, as DOM's `textContent`. */
    val textContent: String
        get() = buildString { appendText(this@XmlElement, this) }

    fun hasAttribute(name: String): Boolean = attributes.containsKey(name)

    private fun appendText(e: XmlElement, out: StringBuilder) {
        for (c in e.children) when (c) {
            is XmlText -> out.append(c.text)
            is XmlElement -> appendText(c, out)
        }
    }
}

internal object XmlLite {

    private const val MAX_DEPTH = 512

    /** Parse [input] and return its root element, or throw [XmlParseException]. */
    fun parse(input: String): XmlElement = Reader(input.replace("\r\n", "\n").replace('\r', '\n')).document()

    private class Reader(private val s: String) {
        private var i = 0

        fun document(): XmlElement {
            skipMisc(allowText = false)
            if (i >= s.length || s[i] != '<') fail("no root element")
            val root = element()
            skipMisc(allowText = false)
            if (i < s.length) fail("content after the root element")
            return root
        }

        /** Whitespace, comments and processing instructions. DOCTYPE and stray text are errors. */
        private fun skipMisc(allowText: Boolean) {
            while (i < s.length) {
                when {
                    isSpace(s[i]) -> i++
                    s.startsWith("<?", i) -> skipProcessingInstruction()
                    s.startsWith("<!--", i) -> skipPast("-->")
                    s.startsWith("<!", i) -> fail("DOCTYPE and other declarations are not allowed")
                    s[i] == '<' -> return
                    else -> if (allowText) return else fail("text outside the root element")
                }
            }
        }

        /** `<?target?>` or `<?target data?>`; the target is a name and data must be separated from it by whitespace. */
        private fun skipProcessingInstruction() {
            i += 2
            readName()
            if (s.startsWith("?>", i)) { i += 2; return }
            if (i >= s.length || !isSpace(s[i])) fail("white space is required between the processing instruction target and data")
            skipPast("?>")
        }

        private fun skipPast(end: String) {
            val j = s.indexOf(end, i)
            if (j < 0) fail("unterminated construct, expected $end")
            i = j + end.length
        }

        private fun element(): XmlElement {
            val stack = ArrayList<XmlElement>()
            var root: XmlElement? = null
            while (true) {
                if (i >= s.length) fail("unexpected end of input")
                if (s[i] == '<') {
                    when {
                        s.startsWith("</", i) -> {
                            i += 2
                            val name = readName()
                            skipSpaces()
                            expect('>')
                            val open = stack.removeLastOrNull() ?: fail("stray end tag </$name>")
                            if (open.name != name) fail("end tag </$name> does not match <${open.name}>")
                            if (stack.isEmpty()) return root!!
                        }
                        s.startsWith("<!--", i) -> skipPast("-->")
                        s.startsWith("<![CDATA[", i) -> {
                            val j = s.indexOf("]]>", i)
                            if (j < 0) fail("unterminated CDATA section")
                            if (stack.isEmpty()) fail("CDATA outside the root element")
                            stack.last().children.add(XmlText(s.substring(i + 9, j)))
                            i = j + 3
                        }
                        s.startsWith("<?", i) -> skipProcessingInstruction()
                        s.startsWith("<!", i) -> fail("DOCTYPE and other declarations are not allowed")
                        else -> {
                            i++
                            val name = readName()
                            val attrs = LinkedHashMap<String, String>()
                            while (true) {
                                val hadSpace = skipSpaces()
                                if (i >= s.length) fail("unexpected end of input in <$name>")
                                if (s[i] == '>' || s.startsWith("/>", i)) break
                                if (!hadSpace) fail("expected whitespace before attribute in <$name>")
                                val an = readName()
                                skipSpaces()
                                expect('=')
                                skipSpaces()
                                if (attrs.put(an, attributeValue()) != null) fail("duplicate attribute $an")
                            }
                            val el = XmlElement(name, attrs)
                            if (stack.isEmpty()) root = el else stack.last().children.add(el)
                            if (s[i] == '>') {
                                i++
                                stack.add(el)
                                if (stack.size > MAX_DEPTH) fail("nesting too deep")
                            } else {
                                i += 2
                                if (stack.isEmpty()) return el
                            }
                        }
                    }
                } else {
                    if (stack.isEmpty()) fail("text outside the root element")
                    val j = s.indexOf('<', i).let { if (it < 0) s.length else it }
                    val raw = s.substring(i, j)
                    if (raw.contains("]]>")) fail("\"]]>\" must not appear in character data")
                    stack.last().children.add(XmlText(decode(raw, inAttribute = false)))
                    i = j
                }
            }
        }

        private fun attributeValue(): String {
            val quote = s.getOrNull(i)
            if (quote != '"' && quote != '\'') fail("attribute value must be quoted")
            val j = s.indexOf(quote, i + 1)
            if (j < 0) fail("unterminated attribute value")
            val raw = s.substring(i + 1, j)
            if ('<' in raw) fail("'<' is not allowed in an attribute value")
            i = j + 1
            return decode(raw, inAttribute = true)
        }

        private fun readName(): String {
            val start = i
            while (i < s.length && isNameChar(s[i])) i++
            if (i == start || !isNameStart(s[start])) fail("expected a name")
            val name = s.substring(start, i)
            val colon = name.indexOf(':')
            if (colon == 0 || colon == name.length - 1 || (colon >= 0 && (name.indexOf(':', colon + 1) >= 0 || !isNameStart(name[colon + 1])))) {
                fail("\"$name\" is not a valid QName")
            }
            return name
        }

        private fun expect(c: Char) {
            if (i >= s.length || s[i] != c) fail("expected '$c'")
            i++
        }

        private fun skipSpaces(): Boolean {
            val start = i
            while (i < s.length && isSpace(s[i])) i++
            return i > start
        }

        private fun decode(raw: String, inAttribute: Boolean): String {
            if (raw.indexOf('&') < 0 && !(inAttribute && (raw.indexOf('\n') >= 0 || raw.indexOf('\t') >= 0))) return raw
            val out = StringBuilder(raw.length)
            var k = 0
            while (k < raw.length) {
                val c = raw[k]
                if (c == '&') {
                    val semi = raw.indexOf(';', k)
                    if (semi < 0) fail("unterminated entity reference")
                    out.append(entity(raw.substring(k + 1, semi)))
                    k = semi + 1
                } else {
                    // Attribute-value normalisation: literal newlines and tabs become spaces.
                    out.append(if (inAttribute && (c == '\n' || c == '\t')) ' ' else c)
                    k++
                }
            }
            return out.toString()
        }

        private fun entity(name: String): String = when {
            name == "lt" -> "<"
            name == "gt" -> ">"
            name == "amp" -> "&"
            name == "quot" -> "\""
            name == "apos" -> "'"
            name.startsWith("#x") || name.startsWith("#X") -> codePoint(name.substring(2), 16)
            name.startsWith("#") -> codePoint(name.substring(1), 10)
            else -> fail("undeclared entity &$name;")
        }

        private fun codePoint(digits: String, radix: Int): String {
            val cp = digits.toIntOrNull(radix) ?: fail("bad character reference")
            val legal = cp == 0x9 || cp == 0xA || cp == 0xD || cp in 0x20..0xD7FF || cp in 0xE000..0xFFFD || cp in 0x10000..0x10FFFF
            if (!legal) fail("&#$digits; is not a valid XML character")
            return CodePoints.toStringOrNull(cp) ?: fail("invalid character reference")
        }

        private fun isSpace(c: Char) = c == ' ' || c == '\t' || c == '\n' || c == '\r'
        private fun isNameStart(c: Char) = c.isLetter() || c == '_' || c == ':' || c.code >= 0x80
        private fun isNameChar(c: Char) = isNameStart(c) || c.isDigit() || c == '-' || c == '.'

        private fun fail(msg: String): Nothing = throw XmlParseException("$msg (at offset $i)")
    }
}
