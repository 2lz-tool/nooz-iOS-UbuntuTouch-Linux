package xyz.mdhv.riverwip.model

/**
 * A small URI reader/resolver, replacing `java.net.URI` so this module builds for
 * Kotlin/Native (iOS, Ubuntu Touch) as well as the JVM.
 *
 * It deliberately mirrors the parts of `java.net.URI` this app depends on:
 *  - [parse] rejects the same malformed input (illegal characters, bad `%` escapes,
 *    bad scheme names), because [CanonicalUrl] treats a parse failure as "fall back
 *    to dropping the fragment";
 *  - [host] is `null` for authorities that are not a valid server-based
 *    `host[:port]` (underscores, IDN hosts, …), exactly as `URI.getHost()` is;
 *  - [port] is -1 when absent.
 *
 * [resolve] follows RFC 3986 §5.2. It differs from `java.net.URI.resolve`
 * (RFC 2396) only in corner cases: a query-only reference (`?feed=rss`) keeps the
 * base path instead of collapsing to the base directory, and `..` segments that
 * climb above the root are dropped instead of left in place. Both are the
 * RFC-correct results.
 */
internal class Uri private constructor(
    val scheme: String?,
    /** Raw authority (`user@host:port`) or null. */
    val rawAuthority: String?,
    /** Server-based host, or null (no authority, or a registry-style one). Lowercasing is the caller's job. */
    val host: String?,
    /** -1 when the URI has no port. */
    val port: Int,
    val rawPath: String,
    val rawQuery: String?,
    val rawFragment: String?,
    /** `scheme:opaque-part` (e.g. `mailto:x@y`): no authority/path/query structure. */
    val isOpaque: Boolean,
    private val original: String,
) {

    /** The original text when parsed, or the recomposed text when produced by [resolve]. */
    override fun toString(): String = original

    /** Resolve [reference] against this URI. Throws [IllegalArgumentException] if [reference] is malformed. */
    fun resolve(reference: String): String {
        val r = parse(reference)
        if (r.isOpaque || isOpaque || r.scheme != null) return r.toString()

        val path: String
        val query: String?
        val authority: String?
        if (r.rawAuthority != null) {
            authority = r.rawAuthority
            path = r.rawPath
            query = r.rawQuery
        } else {
            authority = rawAuthority
            if (r.rawPath.isEmpty()) {
                path = rawPath
                query = r.rawQuery ?: rawQuery
            } else {
                path = if (r.rawPath.startsWith("/")) removeDotSegments(r.rawPath)
                else removeDotSegments(merge(r.rawPath))
                query = r.rawQuery
            }
        }
        return compose(scheme, authority, path, query, r.rawFragment)
    }

    private fun merge(refPath: String): String {
        if (rawAuthority != null && rawPath.isEmpty()) return "/$refPath"
        val slash = rawPath.lastIndexOf('/')
        return if (slash >= 0) rawPath.substring(0, slash + 1) + refPath else refPath
    }

    companion object {
        fun parse(input: String): Uri {
            requireLegalCharacters(input)

            var rest = input
            val hash = rest.indexOf('#')
            val fragment = if (hash >= 0) rest.substring(hash + 1).also { rest = rest.substring(0, hash) } else null

            // Scheme: a colon before any '/', '?' with something in front of it.
            var scheme: String? = null
            val colon = rest.indexOf(':')
            val slash = rest.indexOf('/')
            val qmark = rest.indexOf('?')
            val firstDelim = listOf(slash, qmark).filter { it >= 0 }.minOrNull() ?: Int.MAX_VALUE
            if (colon in 0 until firstDelim) {
                require(colon > 0) { "Expected scheme name at index 0: $input" }
                scheme = rest.substring(0, colon)
                require(isValidScheme(scheme)) { "Illegal character in scheme name: $input" }
                rest = rest.substring(colon + 1)
                if (!rest.startsWith("/")) {
                    require(rest.isNotEmpty()) { "Expected scheme-specific part: $input" }
                    requireLegalQueryOrFragment(rest, input)
                    if (fragment != null) requireLegalQueryOrFragment(fragment, input)
                    return Uri(scheme, null, null, -1, "", null, fragment, true, input)
                }
            }

            var query: String? = null
            val q = rest.indexOf('?')
            if (q >= 0) {
                query = rest.substring(q + 1)
                rest = rest.substring(0, q)
            }

            var authority: String? = null
            var host: String? = null
            var port = -1
            var path = rest
            if (rest.startsWith("//")) {
                val end = rest.indexOf('/', 2).let { if (it < 0) rest.length else it }
                val auth = rest.substring(2, end)
                path = rest.substring(end)
                if (auth.isEmpty()) {
                    require(path.isNotEmpty() || query != null || fragment != null) { "Expected authority: $input" }
                } else {
                    authority = auth
                    val parsed = parseAuthority(auth)
                    host = parsed.first
                    port = parsed.second
                }
            }

            requireLegalPath(path, input)
            if (query != null) requireLegalQueryOrFragment(query, input)
            if (fragment != null) requireLegalQueryOrFragment(fragment, input)
            return Uri(scheme, authority, host, port, path, query, fragment, false, input)
        }

        private fun compose(scheme: String?, authority: String?, path: String, query: String?, fragment: String?): String =
            buildString {
                if (scheme != null) append(scheme).append(':')
                if (authority != null) append("//").append(authority)
                append(path)
                if (query != null) append('?').append(query)
                if (fragment != null) append('#').append(fragment)
            }

        // ---- validation ---------------------------------------------------

        private const val UNRESERVED_PUNCT = "-_.!~*'()"
        private const val PATH_PUNCT = ":@&=+$,;/"

        private fun isAsciiAlnum(c: Char) = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9'
        private fun isAsciiAlpha(c: Char) = c in 'a'..'z' || c in 'A'..'Z'
        private fun isHex(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

        /** Unicode "other" characters java.net.URI tolerates: non-ASCII, not a control, not a space separator. */
        private fun isOther(c: Char): Boolean {
            if (c.code <= 0x7F) return false
            if (c.code in 0x80..0x9F) return false
            return when (c.code) {
                0x00A0, 0x1680, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000 -> false
                in 0x2000..0x200A -> false
                else -> true
            }
        }

        private fun requireLegalCharacters(s: String) {
            for ((i, c) in s.withIndex()) {
                val bad = c.code <= 0x20 || c.code == 0x7F || c in "\"<>\\^`{|}" ||
                    (c.code > 0x7F && !isOther(c))
                require(!bad) { "Illegal character at index $i: $s" }
            }
        }

        private fun requireEscapes(s: String, whole: String) {
            var i = 0
            while (i < s.length) {
                if (s[i] == '%') {
                    require(i + 2 < s.length && isHex(s[i + 1]) && isHex(s[i + 2])) { "Malformed escape pair: $whole" }
                    i += 3
                } else i++
            }
        }

        private fun requireLegalPath(path: String, whole: String) {
            requireEscapes(path, whole)
            for (c in path) {
                val ok = isAsciiAlnum(c) || c in UNRESERVED_PUNCT || c in PATH_PUNCT || c == '%' || isOther(c)
                require(ok) { "Illegal character in path: $whole" }
            }
        }

        private fun requireLegalQueryOrFragment(s: String, whole: String) {
            requireEscapes(s, whole)
            for (c in s) {
                val ok = isAsciiAlnum(c) || c in UNRESERVED_PUNCT || c in PATH_PUNCT || c == '%' ||
                    c == '?' || c == '[' || c == ']' || isOther(c)
                require(ok) { "Illegal character in query/fragment: $whole" }
            }
        }

        private fun isValidScheme(s: String): Boolean =
            s.isNotEmpty() && isAsciiAlpha(s[0]) && s.all { isAsciiAlnum(it) || it == '+' || it == '-' || it == '.' }

        // ---- authority ----------------------------------------------------

        /** Returns (host, port); host is null when the authority is registry-style. Throws on illegal characters. */
        private fun parseAuthority(auth: String): Pair<String?, Int> {
            requireEscapes(auth, auth)
            val at = auth.indexOf('@')
            val hostPort = if (at >= 0) auth.substring(at + 1) else auth
            val bracketed = hostPort.startsWith("[")
            // '[' and ']' are legal only around an IPv6 literal; java.net.URI throws for them anywhere else.
            val plain = if (bracketed) auth.substring(0, at + 1) + hostPort.substring(hostPort.indexOf(']').coerceAtLeast(0) + 1) else auth
            for (c in plain) {
                val ok = isAsciiAlnum(c) || c in UNRESERVED_PUNCT || c in "$,;:@&=+%" || isOther(c)
                require(ok) { "Illegal character in authority: $auth" }
            }
            if (at >= 0 && !isValidUserInfo(auth.substring(0, at))) return null to -1

            val hostStr: String
            val portStr: String?
            if (bracketed) {
                val close = hostPort.indexOf(']')
                require(close > 0) { "Expected closing bracket for IPv6 address: $auth" }
                hostStr = hostPort.substring(0, close + 1)
                require(isValidIpv6Literal(hostStr)) { "Malformed IPv6 address: $auth" }
                val tail = hostPort.substring(close + 1)
                portStr = when {
                    tail.isEmpty() -> null
                    tail.startsWith(":") -> tail.substring(1)
                    else -> throw IllegalArgumentException("Illegal character in hostname: $auth")
                }
            } else {
                val c = hostPort.indexOf(':')
                hostStr = if (c >= 0) hostPort.substring(0, c) else hostPort
                portStr = if (c >= 0) hostPort.substring(c + 1) else null
                if (!isValidHostname(hostStr) && !isValidIpv4(hostStr)) return null to -1
            }

            var port = -1
            if (!portStr.isNullOrEmpty()) {
                if (!portStr.all { it in '0'..'9' }) return null to -1
                port = portStr.toIntOrNull() ?: return null to -1
            }
            return hostStr to port
        }

        private fun isValidUserInfo(s: String): Boolean =
            s.all { isAsciiAlnum(it) || it in UNRESERVED_PUNCT || it in ";:&=+$," || it == '%' || isOther(it) }

        private fun isValidHostname(h: String): Boolean {
            if (h.isEmpty()) return false
            val labels = (if (h.endsWith(".")) h.dropLast(1) else h).split('.')
            if (labels.any { it.isEmpty() }) return false
            for (l in labels) {
                if (!l.all { isAsciiAlnum(it) || it == '-' }) return false
                if (l.first() == '-' || l.last() == '-') return false
            }
            return labels.size == 1 || isAsciiAlpha(labels.last().first())
        }

        private fun isValidIpv4(h: String): Boolean {
            val parts = h.split('.')
            return parts.size == 4 && parts.all { p -> p.isNotEmpty() && p.all { it in '0'..'9' } && (p.toIntOrNull() ?: 256) <= 255 }
        }

        /** `[h16:h16:…]` with at most one `::` and an optional trailing IPv4 quad, 8 groups in all. */
        private fun isValidIpv6Literal(h: String): Boolean {
            val inner = h.substring(1, h.length - 1)
            if (inner.isEmpty()) return false
            val dbl = inner.indexOf("::")
            if (dbl >= 0 && inner.indexOf("::", dbl + 1) >= 0) return false

            fun groups(part: String, allowV4Last: Boolean): Int? {
                if (part.isEmpty()) return 0
                val ps = part.split(':')
                var n = 0
                for ((i, g) in ps.withIndex()) {
                    if (g.isEmpty()) return null
                    if (g.contains('.')) {
                        if (!(allowV4Last && i == ps.lastIndex && isValidIpv4(g))) return null
                        n += 2
                    } else {
                        if (g.length !in 1..4 || !g.all(::isHex)) return null
                        n++
                    }
                }
                return n
            }
            return if (dbl >= 0) {
                val left = groups(inner.substring(0, dbl), false) ?: return false
                val right = groups(inner.substring(dbl + 2), true) ?: return false
                left + right <= 7
            } else groups(inner, true) == 8
        }

        // ---- RFC 3986 §5.2.4 ----------------------------------------------

        private fun removeDotSegments(path: String): String {
            val out = ArrayList<String>()
            val segments = path.split('/')
            for ((i, seg) in segments.withIndex()) {
                val last = i == segments.lastIndex
                when (seg) {
                    "." -> if (last) out.add("")
                    ".." -> {
                        if (out.size > 1 || (out.size == 1 && out[0].isNotEmpty())) out.removeAt(out.lastIndex)
                        if (last) out.add("")
                    }
                    else -> out.add(seg)
                }
            }
            val joined = out.joinToString("/")
            return if (path.startsWith("/") && !joined.startsWith("/")) "/$joined" else joined
        }
    }
}

/** `application/x-www-form-urlencoded`-style percent-encoding of UTF-8, as `URLEncoder.encode(s, "UTF-8")` produces it, with a space as `%20`. */
internal object UrlEncoding {
    private const val SAFE = ".-*_"
    private const val HEX = "0123456789ABCDEF"

    fun encode(s: String): String = buildString {
        for (b in s.encodeToByteArray()) {
            val v = b.toInt() and 0xFF
            val c = v.toChar()
            when {
                c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || (v < 0x80 && c in SAFE) -> append(c)
                v == 0x20 -> append("%20")
                else -> append('%').append(HEX[v shr 4]).append(HEX[v and 0x0F])
            }
        }
    }
}
