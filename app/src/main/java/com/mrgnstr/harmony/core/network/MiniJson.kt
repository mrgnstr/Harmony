package com.mrgnstr.harmony.core.network

/**
 * Lightweight, zero-dependency RFC 8259 JSON parser and builder.
 * Works identically on Android API 24+ and in pure JVM unit tests (where android.jar stubs org.json).
 */
sealed interface JsonValue {
    val asObjectOrNull: JsonObj? get() = this as? JsonObj
    val asArrayOrNull: JsonArr? get() = this as? JsonArr
    val asStringOrNull: String? get() = (this as? JsonStr)?.value ?: (this as? JsonNum)?.raw ?: (this as? JsonBool)?.value?.toString()
    val asIntOrNull: Int? get() = (this as? JsonNum)?.raw?.toIntOrNull() ?: (this as? JsonNum)?.raw?.toDoubleOrNull()?.toInt() ?: (this as? JsonStr)?.value?.toIntOrNull()
    val asLongOrNull: Long? get() = (this as? JsonNum)?.raw?.toLongOrNull() ?: (this as? JsonNum)?.raw?.toDoubleOrNull()?.toLong() ?: (this as? JsonStr)?.value?.toLongOrNull()
    val asDoubleOrNull: Double? get() = (this as? JsonNum)?.raw?.toDoubleOrNull() ?: (this as? JsonStr)?.value?.toDoubleOrNull()
    val asBooleanOrNull: Boolean? get() = (this as? JsonBool)?.value ?: (this as? JsonStr)?.value?.toBooleanStrictOrNull()

    fun toJsonString(): String
}

data object JsonNull : JsonValue {
    override fun toJsonString(): String = "null"
}

data class JsonBool(val value: Boolean) : JsonValue {
    override fun toJsonString(): String = if (value) "true" else "false"
}

data class JsonNum(val raw: String) : JsonValue {
    constructor(n: Number) : this(n.toString())
    override fun toJsonString(): String = raw
}

data class JsonStr(val value: String) : JsonValue {
    override fun toJsonString(): String = MiniJson.quote(value)
}

data class JsonArr(val items: List<JsonValue> = emptyList()) : JsonValue, List<JsonValue> by items {
    fun objects(): List<JsonObj> = items.mapNotNull { it.asObjectOrNull }
    fun strings(): List<String> = items.mapNotNull { it.asStringOrNull }
    fun ints(): List<Int> = items.mapNotNull { it.asIntOrNull }
    override fun toJsonString(): String = items.joinToString(",", prefix = "[", postfix = "]") { it.toJsonString() }
}

data class JsonObj(val fields: Map<String, JsonValue> = emptyMap()) : JsonValue {
    operator fun get(key: String): JsonValue? = fields[key]
    fun obj(key: String): JsonObj? = fields[key]?.asObjectOrNull
    fun arr(key: String): JsonArr = when (val v = fields[key]) {
        is JsonArr -> v
        is JsonObj -> JsonArr(listOf(v)) // Subsonic XML-to-JSON quirks sometimes emit a single object instead of 1-element array
        else -> JsonArr(emptyList())
    }
    fun str(key: String, default: String = ""): String = fields[key]?.asStringOrNull ?: default
    fun strOrNull(key: String): String? = fields[key]?.asStringOrNull?.takeIf { it.isNotEmpty() }
    fun int(key: String, default: Int = 0): Int = fields[key]?.asIntOrNull ?: default
    fun long(key: String, default: Long = 0L): Long = fields[key]?.asLongOrNull ?: default
    fun double(key: String, default: Double = 0.0): Double = fields[key]?.asDoubleOrNull ?: default
    fun doubleOrNull(key: String): Double? = fields[key]?.asDoubleOrNull
    fun bool(key: String, default: Boolean = false): Boolean = fields[key]?.asBooleanOrNull ?: default

    override fun toJsonString(): String =
        fields.entries.joinToString(",", prefix = "{", postfix = "}") { (k, v) -> "${MiniJson.quote(k)}:${v.toJsonString()}" }
}

class JsonObjBuilder {
    private val map = LinkedHashMap<String, JsonValue>()
    fun put(key: String, value: String?) { map[key] = if (value == null) JsonNull else JsonStr(value) }
    fun put(key: String, value: Number?) { map[key] = if (value == null) JsonNull else JsonNum(value) }
    fun put(key: String, value: Boolean?) { map[key] = if (value == null) JsonNull else JsonBool(value) }
    fun put(key: String, value: JsonValue?) { map[key] = value ?: JsonNull }
    fun putStrings(key: String, values: Iterable<String>) { map[key] = JsonArr(values.map { JsonStr(it) }) }
    fun putObjects(key: String, values: Iterable<JsonObj>) { map[key] = JsonArr(values.toList()) }
    fun build(): JsonObj = JsonObj(map)
}

fun buildJsonObj(block: JsonObjBuilder.() -> Unit): JsonObj = JsonObjBuilder().apply(block).build()

object MiniJson {
    fun parse(source: String): JsonValue = Parser(source).parseRoot()
    fun parseObject(source: String): JsonObj = parse(source).asObjectOrNull ?: JsonObj()
    fun parseArray(source: String): JsonArr = parse(source).asArrayOrNull ?: JsonArr()

    fun quote(s: String): String = buildString(s.length + 2) {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

    private class Parser(private val src: String) {
        private var pos = 0
        private val len = src.length

        fun parseRoot(): JsonValue {
            skipWhitespace()
            if (pos >= len) return JsonNull
            return parseValue()
        }

        private fun skipWhitespace() {
            while (pos < len) {
                when (src[pos]) {
                    ' ', '\t', '\r', '\n', '\uFEFF' -> pos++
                    else -> return
                }
            }
        }

        private fun parseValue(): JsonValue {
            skipWhitespace()
            if (pos >= len) throw IllegalArgumentException("Unexpected end of JSON at $pos")
            return when (val c = src[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> JsonStr(parseString())
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                '-', in '0'..'9' -> parseNumber()
                else -> throw IllegalArgumentException("Unexpected char '$c' at $pos")
            }
        }

        private fun parseObject(): JsonObj {
            pos++ // '{'
            skipWhitespace()
            if (pos < len && src[pos] == '}') {
                pos++
                return JsonObj()
            }
            val map = LinkedHashMap<String, JsonValue>()
            while (pos < len) {
                skipWhitespace()
                if (src[pos] != '"') throw IllegalArgumentException("Expected string key at $pos")
                val key = parseString()
                skipWhitespace()
                if (pos >= len || src[pos] != ':') throw IllegalArgumentException("Expected ':' at $pos")
                pos++
                val value = parseValue()
                map[key] = value
                skipWhitespace()
                if (pos >= len) break
                when (src[pos]) {
                    ',' -> pos++
                    '}' -> { pos++; return JsonObj(map) }
                    else -> throw IllegalArgumentException("Expected ',' or '}' at $pos")
                }
            }
            return JsonObj(map)
        }

        private fun parseArray(): JsonArr {
            pos++ // '['
            skipWhitespace()
            if (pos < len && src[pos] == ']') {
                pos++
                return JsonArr()
            }
            val list = ArrayList<JsonValue>()
            while (pos < len) {
                list += parseValue()
                skipWhitespace()
                if (pos >= len) break
                when (src[pos]) {
                    ',' -> pos++
                    ']' -> { pos++; return JsonArr(list) }
                    else -> throw IllegalArgumentException("Expected ',' or ']' at $pos")
                }
            }
            return JsonArr(list)
        }

        private fun parseString(): String {
            pos++ // '"'
            val sb = StringBuilder()
            while (pos < len) {
                val c = src[pos++]
                if (c == '"') return sb.toString()
                if (c == '\\') {
                    if (pos >= len) break
                    when (val esc = src[pos++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 <= len) {
                                val hex = src.substring(pos, pos + 4)
                                sb.append(hex.toInt(16).toChar())
                                pos += 4
                            }
                        }
                        else -> sb.append(esc)
                    }
                } else {
                    sb.append(c)
                }
            }
            return sb.toString()
        }

        private fun parseBoolean(): JsonBool =
            if (src.startsWith("true", pos)) { pos += 4; JsonBool(true) }
            else if (src.startsWith("false", pos)) { pos += 5; JsonBool(false) }
            else throw IllegalArgumentException("Invalid boolean at $pos")

        private fun parseNull(): JsonNull =
            if (src.startsWith("null", pos)) { pos += 4; JsonNull }
            else throw IllegalArgumentException("Invalid null at $pos")

        private fun parseNumber(): JsonNum {
            val start = pos
            while (pos < len && src[pos] in "-+0123456789.eE") pos++
            return JsonNum(src.substring(start, pos))
        }
    }
}
