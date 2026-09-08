package com.lucent.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Characterisation tests for [TokenEstimator] (P0-5 domain group): the token estimate that sizes
 * every assistant request — CJK glyphs count one each, other scripts four characters per token
 * with ceiling division, blank text costs nothing, and the human label stays readable at every
 * magnitude.
 */
class TokenEstimatorTest {

    @Test
    fun blankTextIsZero() {
        assertEquals(0, TokenEstimator.estimate(null))
        assertEquals(0, TokenEstimator.estimate(""))
        assertEquals(0, TokenEstimator.estimate("   \n "))
    }

    @Test
    fun latinCountsFourCharactersPerToken() {
        assertEquals(1, TokenEstimator.estimate("abcd"))      // exactly one token
        assertEquals(2, TokenEstimator.estimate("abcde"))     // ceiling of 5/4
        assertEquals(0, TokenEstimator.estimate("    "))      // whitespace is free
        assertEquals(1, TokenEstimator.estimate("a"))         // never rounds to zero
    }

    @Test
    fun cjkGlyphsAreOneTokenEach() {
        assertEquals(4, TokenEstimator.estimate("你好世界"))
        assertEquals(3, TokenEstimator.estimate("日本語"))
        assertEquals(3, TokenEstimator.estimate("한국어"))
    }

    @Test
    fun mixedScriptsSumCorrectly() {
        // 4 CJK glyphs + "abcd" (1 token) = 5
        assertEquals(5, TokenEstimator.estimate("你好世界 abcd"))
    }

    @Test
    fun estimateAllSumsEstimates() {
        assertEquals(0, TokenEstimator.estimateAll(emptyList()))
        assertEquals(3, TokenEstimator.estimateAll(listOf("ab", "cd", "ef"))) // three separate tokens
        assertEquals(2, TokenEstimator.estimateAll(listOf("你好")))           // two CJK glyphs
    }

    @Test
    fun labelStaysReadable() {
        assertEquals("~0 tokens", TokenEstimator.label(0))
        assertEquals("~312 tokens", TokenEstimator.label(312))
        assertEquals("~1.2k tokens", TokenEstimator.label(1200))
        assertEquals("~12.3k tokens", TokenEstimator.label(12345))
    }
}
