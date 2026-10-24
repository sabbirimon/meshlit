package com.meshlit.workspace.richtext

import org.commonmark.node.*
import org.commonmark.ext.gfm.tables.*
import org.commonmark.parser.Parser
import kotlinx.serialization.json.*
import java.net.URI

data class ReplySpan(val text:String, val bold:Boolean=false, val italic:Boolean=false,
    val code:Boolean=false, val url:String?=null)
sealed interface ReplyBlock {
    data class Prose(val spans:List<ReplySpan>,val heading:Int=0,val indent:Int=0,
        val marker:String?=null,val quote:Boolean=false):ReplyBlock
    data class Code(val language:String,val text:String):ReplyBlock
    data class Table(val header:List<List<ReplySpan>>,val rows:List<List<List<ReplySpan>>>):ReplyBlock
    data class Chart(val title:String,val unit:String,val labels:List<String>,val values:List<Double>):ReplyBlock
    data object Rule:ReplyBlock
}
data class ReplyDocument(val blocks:List<ReplyBlock>,val plainFallback:Boolean=false)

/** Bound text items without cutting a UTF-16 surrogate pair (including emoji). */
fun String.chunkReplyText(limit:Int):List<String> {
    require(limit>=2)
    return buildList {
        var start=0
        while(start<length) {
            var end=minOf(start+limit,length)
            if(end<length && this@chunkReplyText[end-1].isHighSurrogate() && this@chunkReplyText[end].isLowSurrogate()) end--
            add(substring(start,end));start=end
        }
    }
}

private fun bareReplyUrl(value:String):String {
    var candidate=value.trimEnd('.',',',';','!','?')
    while(candidate.endsWith(')') && candidate.count{it==')'}>candidate.count{it=='('} ||
        candidate.endsWith(']') && candidate.count{it==']'}>candidate.count{it=='['}) {
        candidate=candidate.dropLast(1).trimEnd('.',',',';','!','?')
    }
    return candidate
}

/** Only human-clicked HTTP(S) links are active. Images/HTML never fetch or execute. */
fun safeReplyUrl(value:String):String? = runCatching {
    require(value.length in 1..2048 && value.none { it.isWhitespace() || it.isISOControl() })
    val uri=URI(value)
    require(uri.scheme?.lowercase() in setOf("https","http") && !uri.host.isNullOrBlank() && uri.rawUserInfo==null)
    value
}.getOrNull()

/** Proven CommonMark/GFM parser, bounded AST conversion; oversized structures retain raw text. */
fun formatReply(text:String):ReplyDocument {
    fun raw()=ReplyDocument(text.chunkReplyText(2048).map { ReplyBlock.Prose(listOf(ReplySpan(it))) },true)
    if(text.length>131072) return raw()
    return runCatching {
        val root=Parser.builder().extensions(listOf(TablesExtension.create())).build().parse(text)
        val blocks=mutableListOf<ReplyBlock>(); var visited=0
        fun budget(depth:Int) { require(depth<=32 && ++visited<=12000 && blocks.size<=800) }
        fun children(node:Node):List<Node> = buildList {var child=node.firstChild;while(child!=null){add(child);child=child.next}}
        fun spans(node:Node):List<ReplySpan> {
            val output=mutableListOf<ReplySpan>()
            fun walk(n:Node,style:ReplySpan,depth:Int) {
                budget(depth)
                when(n) {
                    is org.commonmark.node.Text -> {
                        if(style.url!=null || style.code) output+=style.copy(text=n.literal)
                        else {
                            var offset=0
                            Regex("https?://[^\\s<>]+").findAll(n.literal).forEach{match->
                                val candidate=bareReplyUrl(match.value)
                                val url=safeReplyUrl(candidate)
                                if(url!=null) {
                                    if(match.range.first>offset) output+=style.copy(text=n.literal.substring(offset,match.range.first))
                                    output+=style.copy(text=candidate,url=url);offset=match.range.first+candidate.length
                                }
                            }
                            if(offset<n.literal.length) output+=style.copy(text=n.literal.substring(offset))
                        }
                    }
                    is org.commonmark.node.Code -> output+=style.copy(text=n.literal,code=true)
                    is SoftLineBreak -> output+=style.copy(text=" ")
                    is HardLineBreak -> output+=style.copy(text="\n")
                    is HtmlInline -> output+=style.copy(text=n.literal)
                    is Emphasis -> children(n).forEach{walk(it,style.copy(italic=true),depth+1)}
                    is StrongEmphasis -> children(n).forEach{walk(it,style.copy(bold=true),depth+1)}
                    is Link -> children(n).forEach{walk(it,style.copy(url=safeReplyUrl(n.destination)),depth+1)}
                    is Image -> {output+=style.copy(text="Image: ");children(n).forEach{walk(it,style.copy(url=safeReplyUrl(n.destination)),depth+1)}}
                    else -> children(n).forEach{walk(it,style,depth+1)}
                }
            }
            children(node).forEach{walk(it,ReplySpan(""),0)}
            return output
        }
        fun prose(runs:List<ReplySpan>,heading:Int,indent:Int,marker:String?,quote:Boolean) {
            // Long paragraphs are lazy-list chunks, not a single viewport-sized item.
            val pieces=mutableListOf<ReplySpan>();var size=0;var first=true
            fun flush(){if(pieces.isNotEmpty()){blocks+=ReplyBlock.Prose(pieces.toList(),heading,indent,if(first) marker else null,quote);pieces.clear();size=0;first=false}}
            runs.forEach{span->var rest=span.text;while(rest.isNotEmpty()) {
                var end=minOf(2048-size,rest.length)
                if(end<rest.length && end>0 && rest[end-1].isHighSurrogate() && rest[end].isLowSurrogate()) end--
                if(end==0) {flush();continue}
                val part=rest.take(end);pieces+=span.copy(text=part);size+=part.length;rest=rest.drop(part.length)
                if(size==2048) flush()
            }};flush()
        }
        fun walk(n:Node,depth:Int=0,indent:Int=0,marker:String?=null,quote:Boolean=false) {
            budget(depth)
            when(n) {
                is Heading -> prose(spans(n),n.level,indent,marker,quote)
                is Paragraph -> prose(spans(n),0,indent,marker,quote)
                is FencedCodeBlock -> {
                    val language=n.info.substringBefore(' ').take(40)
                    val chart=if(language=="meshlit-chart") parseReplyChart(n.literal) else null
                    if(chart!=null) blocks+=chart else {
                        val parts=n.literal.chunkReplyText(4096).ifEmpty{listOf("")}
                        parts.forEachIndexed{i,part->blocks+=ReplyBlock.Code(if(parts.size>1) "$language · part ${i+1}/${parts.size}" else language,part)}
                    }
                }
                is IndentedCodeBlock -> n.literal.chunkReplyText(4096).forEach{blocks+=ReplyBlock.Code("text",it)}
                is HtmlBlock -> prose(listOf(ReplySpan(n.literal)),0,indent,marker,quote)
                is ThematicBreak -> blocks+=ReplyBlock.Rule
                is BlockQuote -> children(n).forEach{walk(it,depth+1,indent,null,true)}
                is BulletList, is OrderedList -> {
                    var number=if(n is OrderedList) n.markerStartNumber ?: 1 else 0
                    children(n).forEach {item -> var first=true
                        children(item).forEach {child->
                            val prefix=if(first) {if(n is OrderedList) "${number}." else "•"} else null
                            walk(child,depth+1,indent+1,prefix,quote);first=false
                        };number++
                    }
                }
                is TableBlock -> {
                    val header=mutableListOf<List<ReplySpan>>();val rows=mutableListOf<List<List<ReplySpan>>>()
                    children(n).forEach {section->children(section).forEach {row->
                        val cells=children(row).map{spans(it)}
                        require(cells.size<=32 && rows.size<=512)
                        if(section is TableHead) header+=cells else rows+=cells
                    }}
                    require(header.isNotEmpty())
                    if(rows.isEmpty()) blocks+=ReplyBlock.Table(header,emptyList())
                    else rows.chunked(12).forEach{blocks+=ReplyBlock.Table(header,it)}
                }
                else -> children(n).forEach{walk(it,depth+1,indent,marker,quote)}
            }
        }
        walk(root)
        require(blocks.size<=800)
        ReplyDocument(blocks)
    }.getOrElse { raw() }
}

/** An explicit native bar chart only; never infer/fabricate data from prose or tables. */
fun parseReplyChart(source:String):ReplyBlock.Chart? = runCatching {
    require(source.length<=16384)
    val obj=Json.parseToJsonElement(source).jsonObject
    require(obj.keys==setOf("type","title","unit","labels","values"))
    require(obj.getValue("type").jsonPrimitive.content=="bar")
    val title=obj.getValue("title").jsonPrimitive.also{require(it.isString)}.content
    val unit=obj.getValue("unit").jsonPrimitive.also{require(it.isString)}.content
    require(title.length in 1..160 && unit.length<=32)
    val labels=obj.getValue("labels").jsonArray.map {it.jsonPrimitive.also{p->require(p.isString)}.content}
    val values=obj.getValue("values").jsonArray.map{it.jsonPrimitive.also{p->require(!p.isString)}.double}
    require(labels.size in 1..24 && labels.size==values.size && labels.all{it.length in 1..80})
    require(values.all{it.isFinite() && kotlin.math.abs(it)<=1e12})
    ReplyBlock.Chart(title,unit,labels,values)
}.getOrNull()

fun replyBlockText(block:ReplyBlock):String=when(block) {
    is ReplyBlock.Prose -> block.spans.joinToString(""){it.text}
    is ReplyBlock.Code -> block.text
    is ReplyBlock.Table -> (listOf(block.header)+block.rows).joinToString("\n"){row->row.joinToString(" | "){cell->cell.joinToString(""){it.text}}}
    is ReplyBlock.Chart -> block.title+"\n"+block.labels.zip(block.values).joinToString("\n"){"${it.first}: ${it.second} ${block.unit}"}
    ReplyBlock.Rule -> ""
}
