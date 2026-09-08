package com.lucent.app.assistant.text

import com.lucent.app.network.ToolExecResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Characterisation tests for [ReplyPolish], extracted verbatim from AssistantController (v2.7.6).
 * They pin down the markdown scrubber, the refusal/terse detectors and the honest-reply fallback
 * logic so later refactors cannot silently change what reaches the chat bubbles.
 */
class ReplyPolishTest {

    // ---- deRobotify ----

    @Test
    fun stripsBoldItalicAndCodeSpans() {
        assertEquals("hello world code", ReplyPolish.deRobotify("**hello** *world* `code`"))
        assertEquals("plain", ReplyPolish.deRobotify("plain"))
        assertEquals("a_b", ReplyPolish.deRobotify("a_b")) // lone underscore untouched
    }

    @Test
    fun stripsLineLeadingMarkdown() {
        assertEquals("heading\nbullet\n2. keeps", ReplyPolish.deRobotify("# heading\n- bullet\n2. keeps"))
        // A bullet not at the line start is not a markdown prefix.
        assertEquals("keeps • mid-line", ReplyPolish.deRobotify("keeps • mid-line"))
    }

    @Test
    fun leavesNormalProseAlone() {
        val prose = "I'm fine — 2 * 3 = 6 and it's 100% done, ok?"
        assertEquals(prose, ReplyPolish.deRobotify(prose))
    }

    @Test
    fun stripsStageDirectionAsterisks() {
        assertEquals("smiles warmly", ReplyPolish.deRobotify("*smiles* warmly"))
    }

    // ---- isBareRefusal ----

    @Test
    fun detectsShortBareRefusals() {
        assertTrue(ReplyPolish.isBareRefusal("I can't"))
        assertTrue(ReplyPolish.isBareRefusal("Sorry, I cannot do that"))
        assertTrue(ReplyPolish.isBareRefusal("我不能"))
        assertTrue(ReplyPolish.isBareRefusal("できません"))
    }

    @Test
    fun ignoresLongOrSubstantiveText() {
        assertFalse(ReplyPolish.isBareRefusal(""))
        // Over 64 characters passes through untouched.
        assertFalse(ReplyPolish.isBareRefusal("I can't do that because the note is archived, but here is what I found instead."))
        // Not an "i can't…" opener, so it is not a bare refusal.
        assertFalse(ReplyPolish.isBareRefusal("Thanks for the help"))
        // Characterisation: the opener check only looks at the start, so a short "i can't" with
        // trailing words still counts as a bare refusal (this is the v2.7.6 behaviour).
        assertTrue(ReplyPolish.isBareRefusal("i can't believe you"))
    }

    // ---- isTerseNonAnswer ----

    @Test
    fun detectsTersePlaceholders() {
        assertTrue(ReplyPolish.isTerseNonAnswer("done"))
        assertTrue(ReplyPolish.isTerseNonAnswer("Done."))
        assertTrue(ReplyPolish.isTerseNonAnswer("ok"))
        assertTrue(ReplyPolish.isTerseNonAnswer("no reply"))
        assertTrue(ReplyPolish.isTerseNonAnswer(""))
    }

    @Test
    fun ignoresRealReplies() {
        assertFalse(ReplyPolish.isTerseNonAnswer("Done — created note \"Groceries\"."))
    }

    // ---- imageFileName ----

    @Test
    fun mapsMimeToFileName() {
        assertEquals("image.jpg", ReplyPolish.imageFileName("image/jpeg"))
        assertEquals("image.jpg", ReplyPolish.imageFileName("image/jpg"))
        assertEquals("image.webp", ReplyPolish.imageFileName("image/webp"))
        assertEquals("image.gif", ReplyPolish.imageFileName("image/gif"))
        assertEquals("image.png", ReplyPolish.imageFileName(null))
        assertEquals("image.png", ReplyPolish.imageFileName("application/pdf"))
    }

    // ---- replyContent ----

    @Test
    fun keepsRealReply() {
        val out = ReplyPolish.replyContent(
            "Created note \"Groceries\".", hasImage = false,
            toolResults = listOf(ToolExecResult(summary = "Created note \"Groceries\".", success = true))
        )
        assertEquals("Created note \"Groceries\".", out)
    }

    @Test
    fun replacesBareRefusalDenyingSuccess() {
        // Reported bug: model created the task, then replied "i can't".
        val out = ReplyPolish.replyContent(
            "I can't", hasImage = false,
            toolResults = listOf(ToolExecResult(summary = "Created task \"Buy milk\".", success = true))
        )
        assertEquals("Created task \"Buy milk\".", out)
    }

    @Test
    fun replacesTersePlaceholderWithToolSummary() {
        val out = ReplyPolish.replyContent(
            "done", hasImage = false,
            toolResults = listOf(ToolExecResult(summary = "Deleted note \"Old\".", success = true))
        )
        assertEquals("Deleted note \"Old\".", out)
    }

    @Test
    fun fallsBackToFailureSummaryFirst() {
        val out = ReplyPolish.replyContent(
            null, hasImage = false,
            toolResults = listOf(
                ToolExecResult(summary = "Created note \"A\".", success = true),
                ToolExecResult(summary = "Could not find task \"B\".", success = false)
            )
        )
        assertEquals("Could not find task \"B\".", out)
    }

    @Test
    fun emptyResultFallsBackToScriptInUserLanguage() {
        val en = ReplyPolish.replyContent(null, hasImage = false, toolResults = emptyList(), userText = "hello")
        assertTrue(en.contains("Sorry"))
        val zh = ReplyPolish.replyContent(null, hasImage = false, toolResults = emptyList(), userText = "你好")
        assertTrue(zh.contains("抱歉"))
        val ja = ReplyPolish.replyContent(null, hasImage = false, toolResults = emptyList(), userText = "こんにちは")
        assertTrue(ja.contains("ごめんなさい"))
        val ko = ReplyPolish.replyContent(null, hasImage = false, toolResults = emptyList(), userText = "안녕하세요")
        assertTrue(ko.contains("죄송해요"))
    }
}
