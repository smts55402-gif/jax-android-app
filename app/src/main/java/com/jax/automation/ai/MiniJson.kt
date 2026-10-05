package com.jax.automation.ai

// ---------------------------------------------------------------------------
// MiniJson: minimal dependency-free recursive-descent JSON parser.
// Pure Kotlin — no android.*, no org.json — safe for JVM unit tests.
// Returns Map<String, Any?> for objects, List<Any?> for arrays, String,
// Double, Boolean, or null. Throws IllegalArgumentException on malformed input.
// ---------------------------------------------------------------------------

object MiniJson {

    fun parse(text: String): Any? {
        val parser = Parser(text)
        val value = parser.parseValue()
        parser.skipWhitespace()
        if (parser.pos < parser.text.length) {
            parser.fail("unexpected trailing content")
        }
        return value
    }

    private class Parser(val text: String) {
        var pos: Int = 0

        fun fail(message: String): Nothing =
            throw IllegalArgumentException("Malformed JSON: $message (position $pos)")

        fun skipWhitespace() {
            while (pos < text.length) {
                when (text[pos]) {
                    ' ', '\t', '\n', '\r' -> pos++
                    else -> return
                }
            }
        }

        fun peek(): Char? = if (pos < text.length) text[pos] else null

        fun expect(c: Char) {
            if (peek() != c) fail("expected '$c'")
            pos++
        }

        fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= text.length) fail("unexpected end of input")
            return when (val c = text[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                '-', in '0'..'9' -> parseNumber()
                else -> fail("unexpected character '$c'")
            }
        }

        fun parseObject(): Map<String, Any?> {
            expect('{')
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return map
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') fail("expected string key")
                val key = parseString()
                skipWhitespace()
                expect(':')
                map[key] = parseValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> fail("expected ',' or '}'")
                }
            }
        }

        fun parseArray(): List<Any?> {
            expect('[')
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                pos++
                return list
            }
            while (true) {
                list.add(parseValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> fail("expected ',' or ']'")
                }
            }
        }

        fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (pos >= text.length) fail("unterminated string")
                val c = text[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= text.length) fail("unterminated escape sequence")
                        when (val e = text[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > text.length) fail("invalid unicode escape")
                                val hex = text.substring(pos, pos + 4)
                                val code = hex.toIntOrNull(16) ?: fail("invalid unicode escape '\\u$hex'")
                                pos += 4
                                sb.append(code.toChar())
                            }
                            else -> fail("invalid escape '\\$e'")
                        }
                    }
                    else -> {
                        if (c < ' ') fail("unescaped control character in string")
                        sb.append(c)
                    }
                }
            }
        }

        fun parseNumber(): Double {
            val start = pos
            if (peek() == '-') pos++
            if (pos >= text.length) fail("invalid number")
            if (text[pos] == '0') {
                pos++
            } else if (text[pos] in '1'..'9') {
                while (pos < text.length && text[pos] in '0'..'9') pos++
            } else {
                fail("invalid number")
            }
            if (pos < text.length && text[pos] == '.') {
                pos++
                if (pos >= text.length || text[pos] !in '0'..'9') fail("invalid number")
                while (pos < text.length && text[pos] in '0'..'9') pos++
            }
            if (pos < text.length && (text[pos] == 'e' || text[pos] == 'E')) {
                pos++
                if (pos < text.length && (text[pos] == '+' || text[pos] == '-')) pos++
                if (pos >= text.length || text[pos] !in '0'..'9') fail("invalid number")
                while (pos < text.length && text[pos] in '0'..'9') pos++
            }
            val raw = text.substring(start, pos)
            return raw.toDoubleOrNull() ?: fail("invalid number '$raw'")
        }

        fun parseLiteral(literal: String, value: Any?): Any? {
            if (!text.startsWith(literal, pos)) fail("invalid literal")
            pos += literal.length
            return value
        }
    }
}
