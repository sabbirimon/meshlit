package com.meshlit.search

import org.junit.Assert.*
import org.junit.Test

class TextSearchMatchesTest {
    @Test fun everyOccurrenceHasAnOriginalLineAndUnicodeColumn() {
        val found=textSearchMatches("😀 Cat cat\nবাংলা cat","cat")
        assertEquals(3,found.total)
        assertEquals(listOf(1,1,2),found.positions.map{it.line})
        assertEquals(listOf(3,7,7),found.positions.map{it.column})
        assertEquals(listOf("Cat","cat","cat"),found.positions.map{"😀 Cat cat\nবাংলা cat".substring(it.start,it.end)})
    }
    @Test fun totalIsNotTruncatedWithNavigationPositionsAndPunctuationIsLiteral() {
        val found=textSearchMatches("cat ".repeat(2500),"cat",maxPositions=3)
        assertEquals(2500,found.total);assertEquals(3,found.positions.size)
        assertEquals(1,textSearchMatches("a+b aab","a+b").total)
        assertEquals(0,textSearchMatches("anything","").total)
        assertEquals(3,textSearchMatches("cat dog CAT","cat dog",words=true).total)
    }
}
