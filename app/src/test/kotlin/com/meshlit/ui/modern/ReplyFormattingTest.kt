package com.meshlit.ui.modern

import com.meshlit.workspace.richtext.ReplyBlock

import org.junit.Assert.*
import org.junit.Test

class ReplyFormattingTest {
    @Test fun sectionsListsAndInlineStylesRemainSeparate() {
        val doc=formatReply("# Report\n\nA **strong** and *emphasized* reply with `code`.\n\n3. First\n4. Second\n   - Nested\n\n> Quoted text\n")
        assertFalse(doc.plainFallback)
        assertEquals(1,(doc.blocks.first() as ReplyBlock.Prose).heading)
        val spans=(doc.blocks[1] as ReplyBlock.Prose).spans
        assertTrue(spans.any{it.bold && it.text=="strong"});assertTrue(spans.any{it.italic && it.text=="emphasized"});assertTrue(spans.any{it.code && it.text=="code"})
        val lines=doc.blocks.filterIsInstance<ReplyBlock.Prose>()
        assertTrue(lines.any{it.marker=="3."});assertTrue(lines.any{it.marker=="4."})
        assertTrue(lines.any{it.indent==2 && it.marker=="•"});assertTrue(lines.any{it.quote})
    }
    @Test fun safeLinksActivateButHtmlAndImagesNeverFetch() {
        val doc=formatReply("[Source](https://example.org/a) [unsafe](javascript:alert(1))\n\n![Chart](https://example.org/chart.png)\n\n<script>evil()</script>")
        val spans=doc.blocks.filterIsInstance<ReplyBlock.Prose>().flatMap{it.spans}
        assertTrue(spans.any{it.url=="https://example.org/a"})
        assertTrue(spans.filter{it.text=="unsafe"}.all{it.url==null})
        assertTrue(spans.any{it.text=="Image: "})
        assertTrue(doc.blocks.any{replyBlockText(it).contains("<script>")})
        for(url in listOf("file:///etc/passwd","intent://x","data:text/html,x","https://user:pass@example.org","https://example.org/\n")) assertNull(safeReplyUrl(url))
    }
    @Test fun fencedAndStreamingCodeKeepsActualContent() {
        val text="```python\nprint('hello')\n```\n\nTrailing prose"
        val code=formatReply(text).blocks.filterIsInstance<ReplyBlock.Code>().single()
        assertEquals("python",code.language);assertEquals("print('hello')\n",code.text)
        assertEquals("partial\n",formatReply("```text\npartial").blocks.filterIsInstance<ReplyBlock.Code>().single().text)
        val large="x".repeat(20000)
        assertEquals("$large\n",formatReply("```text\n$large\n```").blocks.filterIsInstance<ReplyBlock.Code>().joinToString(""){it.text})
    }
    @Test fun plainSourceUrlsAreHumanClickableAndKeepTrailingPunctuation() {
        val doc=formatReply("Sources: https://example.org/source, followed by text.")
        val runs=doc.blocks.filterIsInstance<ReplyBlock.Prose>().flatMap{it.spans}
        assertEquals("https://example.org/source",runs.first{it.url!=null}.url)
        assertEquals("Sources: https://example.org/source, followed by text.",runs.joinToString(""){it.text})
        val balanced=formatReply("See https://example.org/Example_(topic).")
        assertEquals("https://example.org/Example_(topic)",balanced.blocks.filterIsInstance<ReplyBlock.Prose>().flatMap{it.spans}.first{it.url!=null}.url)
        assertEquals("https://example.org/topic",formatReply("(https://example.org/topic)").blocks.filterIsInstance<ReplyBlock.Prose>().flatMap{it.spans}.first{it.url!=null}.url)
    }
    @Test fun emojiSurrogatesRemainWholeAcrossLargeReplyChunks() {
        val text="x".repeat(2047)+"✨ 🧑‍💻 "+"x".repeat(2041)+"😀"
        val pieces=formatReply(text).blocks.filterIsInstance<ReplyBlock.Prose>().flatMap{it.spans}.map{it.text}
        assertEquals(text,pieces.joinToString(""))
        for(piece in pieces) {assertFalse(piece.first().isLowSurrogate());assertFalse(piece.last().isHighSurrogate())}
        val raw="x".repeat(131071)+"😀"
        val chunks=formatReply(raw).blocks.map{replyBlockText(it)}
        assertEquals(raw,chunks.joinToString(""));assertFalse(chunks.any{it.last().isHighSurrogate()})
        assertEquals("🧑‍💻", "🧑‍💻".chunkReplyText(2).joinToString(""))
    }
    @Test fun tableColumnsAndLongRowsAreNotDropped() {
        val source="| Name | Value |\n| --- | ---: |\n"+(1..25).joinToString("\n"){"| Row $it | $it |"}
        val tables=formatReply(source).blocks.filterIsInstance<ReplyBlock.Table>()
        assertEquals(3,tables.size);assertEquals(25,tables.sumOf{it.rows.size})
        assertEquals("Name",tables.first().header.first().single().text)
        assertEquals("25",tables.last().rows.last()[1].single().text)
    }
    @Test fun paragraphAndOversizeFallbackKeepAllCharacters() {
        val text="word ".repeat(1500)
        assertEquals(text.trimEnd(),formatReply(text).blocks.joinToString(""){replyBlockText(it)})
        val huge="x".repeat(131073)
        val doc=formatReply(huge);assertTrue(doc.plainFallback);assertEquals(huge,doc.blocks.joinToString(""){replyBlockText(it)})
    }
    @Test fun chartsNeedExplicitFiniteNumericDataAndKnownSchema() {
        val good="""{"type":"bar","title":"Results","unit":"ms","labels":["A","B"],"values":[0,-2]}"""
        assertEquals(listOf(0.0,-2.0),parseReplyChart(good)!!.values)
        assertTrue(formatReply("```meshlit-chart\n$good\n```").blocks.single() is ReplyBlock.Chart)
        for(bad in listOf(good.replace("[0,-2]","[\"0\",-2]"),good.replace("[0,-2]","[1e999,-2]"),good.replace("bar","line"),good.replace("[0,-2]","[1]"),good.replace("\"unit\":\"ms\",",""))) assertNull(parseReplyChart(bad))
        assertTrue(formatReply("```meshlit-chart\n{}\n```").blocks.single() is ReplyBlock.Code)
    }
}
