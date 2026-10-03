package com.lucent.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class TokenEstimatorTest {

    @Test
    fun blankTextIsZero() {
        assertEquals(0, TokenEstimator.estimate(null))
        assertEquals(0, TokenEstimator.estimate(""))
        assertEquals(0, TokenEstimator.estimate("   \n "))
    }

    @Test
    fun latinCountsFourCharactersPerToken() {
        assertEquals(1, TokenEstimator.estimate("abcd"))
        assertEquals(2, TokenEstimator.estimate("abcde"))
        assertEquals(0, TokenEstimator.estimate("    "))
        assertEquals(1, TokenEstimator.estimate("a"))
    }

    @Test
    fun cjkGlyphsAreOneTokenEach() {
        assertEquals(4, TokenEstimator.estimate("你好世界"))
        assertEquals(3, TokenEstimator.estimate("日本語"))
        assertEquals(3, TokenEstimator.estimate("한국어"))
    }

    @Test
    fun mixedScriptsSumCorrectly() {
        assertEquals(5, TokenEstimator.estimate("你好世界 abcd"))
    }

    @Test
    fun estimateAllSumsEstimates() {
        assertEquals(0, TokenEstimator.estimateAll(emptyList()))
        assertEquals(3, TokenEstimator.estimateAll(listOf("ab", "cd", "ef")))
        assertEquals(2, TokenEstimator.estimateAll(listOf("你好")))
    }

    @Test
    fun labelStaysReadable() {
        assertEquals("~0 tokens", TokenEstimator.label(0))
        assertEquals("~312 tokens", TokenEstimator.label(312))
        assertEquals("~1.2k tokens", TokenEstimator.label(1200))
        assertEquals("~12.3k tokens", TokenEstimator.label(12345))
    }
}
