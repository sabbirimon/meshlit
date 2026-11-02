package com.meshlit.search

data class TextOccurrence(val start:Int,val end:Int,val line:Int,val column:Int)
data class TextMatches(val total:Int,val positions:List<TextOccurrence>,val characters:Int) {
    fun location(index:Int=0):String=positions.getOrNull(index)?.let{"line ${it.line}, column ${it.column}"}.orEmpty()
}
data class SectionOccurrence(val section:Int,val occurrence:TextOccurrence)
data class SectionMatches(val total:Int=0,val positions:List<SectionOccurrence> = emptyList(),val characters:Int=0)
fun searchTextSections(sections:List<String>,query:String,maxPositions:Int=2000):SectionMatches {
    var total=0;var characters=0;val positions=ArrayList<SectionOccurrence>()
    sections.forEachIndexed { index,text ->
        val found=textSearchMatches(text,query,maxPositions=(maxPositions-positions.size).coerceAtLeast(0))
        total+=found.total;characters+=found.characters
        positions+=found.positions.map{SectionOccurrence(index,it)}
    }
    return SectionMatches(total,positions,characters)
}

/** Counts all occurrences; only navigation positions are bounded. Offsets address original text. */
fun textSearchMatches(text:String,query:String,words:Boolean=false,maxPositions:Int=1000):TextMatches {
    require(maxPositions in 0..5000)
    val terms=if(words)query.trim().split(Regex("\\s+")).filter{it.isNotBlank()}.distinctBy{it.lowercase()} else listOf(query).filter{it.isNotBlank()}
    if(terms.isEmpty())return TextMatches(0,emptyList(),text.codePointCount(0,text.length))
    val pattern=Regex(terms.joinToString("|"){Regex.escape(it)},RegexOption.IGNORE_CASE)
    val positions=ArrayList<TextOccurrence>();var total=0;var cursor=0;var line=1;var column=1
    for(match in pattern.findAll(text)) {
        total++
        if(positions.size<maxPositions) {
            while(cursor<match.range.first) {
                val cp=text.codePointAt(cursor)
                if(cp==10){line++;column=1}else column++
                cursor+=Character.charCount(cp)
            }
            positions+=TextOccurrence(match.range.first,match.range.last+1,line,column)
        }
    }
    return TextMatches(total,positions,text.codePointCount(0,text.length))
}
