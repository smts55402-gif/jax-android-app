package com.jax.automation.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniJsonTest {

    @Test
    fun parsesSimpleObject() {
        val result = MiniJson.parse("{\"name\":\"AncientYou\",\"n\":2}")
        assertTrue(result is Map<*, *>)
        @Suppress("UNCHECKED_CAST")
        val map = result as Map<String, Any?>
        assertEquals("AncientYou", map["name"])
        assertEquals(2.0, map["n"] as Double, 0.0)
    }

    @Test
    fun parsesSimpleArray() {
        val result = MiniJson.parse("[1,\"two\",true,null]")
        assertTrue(result is List<*>)
        @Suppress("UNCHECKED_CAST")
        val list = result as List<Any?>
        assertEquals(4, list.size)
        assertEquals(1.0, list[0] as Double, 0.0)
        assertEquals("two", list[1])
        assertEquals(true, list[2])
        assertNull(list[3])
    }

    @Test
    fun parsesNestedStructures() {
        val result = MiniJson.parse("{\"a\":{\"b\":[1,{\"c\":false}]}}")
        @Suppress("UNCHECKED_CAST")
        val map = result as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val a = map["a"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val b = a["b"] as List<Any?>
        assertEquals(1.0, b[0] as Double, 0.0)
        @Suppress("UNCHECKED_CAST")
        val inner = b[1] as Map<String, Any?>
        assertEquals(false, inner["c"])
    }

    @Test
    fun parsesStringEscapes() {
        assertEquals("a\nb", MiniJson.parse("\"a\\nb\""))
        assertEquals("a\tb", MiniJson.parse("\"a\\tb\""))
        assertEquals("say \"hi\"", MiniJson.parse("\"say \\\"hi\\\"\""))
        assertEquals("back\\slash", MiniJson.parse("\"back\\\\slash\""))
    }

    @Test
    fun parsesUnicodeEscape() {
        assertEquals("A", MiniJson.parse("\"\\u0041\""))
        assertEquals("A!", MiniJson.parse("\"\\u0041!\""))
    }

    @Test
    fun parsesNumbersAsDouble() {
        assertEquals(42.0, MiniJson.parse("42") as Double, 0.0)
        assertEquals(-7.0, MiniJson.parse("-7") as Double, 0.0)
        assertEquals(3.14, MiniJson.parse("3.14") as Double, 0.0)
        assertEquals(1000.0, MiniJson.parse("1e3") as Double, 0.0)
        assertEquals(0.025, MiniJson.parse("2.5e-2") as Double, 0.0)
        assertEquals(-250.0, MiniJson.parse("-2.5E2") as Double, 0.0)
    }

    @Test
    fun parsesBooleansAndNull() {
        assertEquals(true, MiniJson.parse("true"))
        assertEquals(false, MiniJson.parse("false"))
        assertNull(MiniJson.parse("null"))
    }

    @Test
    fun ignoresSurroundingWhitespace() {
        val result = MiniJson.parse("  \n\t { \"a\" : [ 1 , 2 ] } \r\n ")
        @Suppress("UNCHECKED_CAST")
        val map = result as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val list = map["a"] as List<Any?>
        assertEquals(2, list.size)
        assertEquals(2.0, list[1] as Double, 0.0)
    }

    @Test
    fun parsesEmptyObjectAndArray() {
        assertTrue((MiniJson.parse("{}") as Map<*, *>).isEmpty())
        assertTrue((MiniJson.parse("[]") as List<*>).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun unclosedBraceThrows() {
        MiniJson.parse("{\"a\":1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun trailingCommaThrows() {
        MiniJson.parse("{\"a\":1,}")
    }

    @Test(expected = IllegalArgumentException::class)
    fun singleQuotesThrow() {
        MiniJson.parse("{'a':1}")
    }

    @Test(expected = IllegalArgumentException::class)
    fun trailingContentThrows() {
        MiniJson.parse("{\"a\":1} garbage")
    }
}
