package com.meshlit.ui.modern

import com.meshlit.workspace.richtext.ReplyBlock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meshlit.ui.theme.MeshlitMapleMono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable internal fun rememberReply(text:String):ReplyDocument {
    val doc by produceState(ReplyDocument(emptyList()),text) {
        value=withContext(Dispatchers.Default){formatReply(text)}
    }
    return doc
}

@Composable private fun richReply(spans:List<ReplySpan>):AnnotatedString {
    val colors=MaterialTheme.colorScheme
    return remember(spans,colors) {buildAnnotatedString {spans.forEach{run->
        val style=SpanStyle(fontWeight=if(run.bold) FontWeight.SemiBold else null,
            fontStyle=if(run.italic) FontStyle.Italic else null,
            fontFamily=if(run.code) MeshlitMapleMono else null,
            color=when {run.code->colors.onSecondaryContainer;run.bold->colors.primary;else->androidx.compose.ui.graphics.Color.Unspecified},
            background=if(run.code) colors.secondaryContainer else androidx.compose.ui.graphics.Color.Unspecified)
        withStyle(style) {
            val url=run.url
            if(url!=null) withLink(LinkAnnotation.Url(url,TextLinkStyles(style=SpanStyle(color=colors.primary,textDecoration=TextDecoration.Underline)))){append(run.text)}
            else append(run.text)
        }
    }}}
}

@Composable internal fun ReplyContent(block:ReplyBlock,modifier:Modifier=Modifier) {
    val colors=MaterialTheme.colorScheme
    when(block) {
        is ReplyBlock.Prose -> Row(modifier.fillMaxWidth().padding(start=(minOf(block.indent,4)*12).dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(block.quote) Box(Modifier.width(3.dp).heightIn(min=24.dp).background(colors.primary))
            block.marker?.let{Text(it,Modifier.widthIn(min=22.dp),style=MaterialTheme.typography.bodyLarge,color=colors.onSurfaceVariant)}
            val type=when(block.heading){1->MaterialTheme.typography.headlineSmall;2->MaterialTheme.typography.titleLarge;in 3..6->MaterialTheme.typography.titleMedium;else->MaterialTheme.typography.bodyLarge}
            SelectionContainer(Modifier.weight(1f)) {
                Text(richReply(block.spans),style=if(block.heading>0) type.copy(fontWeight=FontWeight.SemiBold) else type,
                    color=if(block.heading>0) colors.primary else colors.onSurface,
                    modifier=if(block.heading>0) Modifier.padding(top=10.dp,bottom=2.dp).semantics{heading()} else Modifier)
            }
        }
        is ReplyBlock.Code -> {
            val clipboard=LocalClipboardManager.current
            var wrap by remember {mutableStateOf(false)}
            Surface(modifier.fillMaxWidth(),shape=MaterialTheme.shapes.medium,color=colors.surfaceContainer) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(block.language.ifBlank{"code"},Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=colors.onSurfaceVariant)
                        TextButton(onClick={wrap=!wrap}){Text(if(wrap) "Scroll" else "Wrap")}
                        IconButton(onClick={clipboard.setText(AnnotatedString(block.text))}){Icon(Icons.Default.ContentCopy,"Copy code")}
                    }
                    // Vertical viewport is bounded; long code remains selectable and scrollable.
                    val horizontal=rememberScrollState();val vertical=rememberScrollState()
                    Box(Modifier.fillMaxWidth().heightIn(max=360.dp).then(if(wrap) Modifier else Modifier.horizontalScroll(horizontal)).then(Modifier.verticalScroll(vertical))) {
                        SelectionContainer {Text(block.text,fontFamily=MeshlitMapleMono,style=MaterialTheme.typography.bodyMedium)}
                    }
                }
            }
        }
        is ReplyBlock.Table -> BoxWithConstraints(modifier.fillMaxWidth()) {
            val columns=block.header.size
            val width=if(columns<=2) maxWidth/columns else 164.dp
            Column {
                if(columns>2) Text("Swipe table horizontally",style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
                Column(Modifier.horizontalScroll(rememberScrollState()).testTag("reply-table")) {
                    (listOf(block.header)+block.rows).forEachIndexed {index,row->
                        Row(Modifier.background(if(index==0) colors.surfaceContainer else androidx.compose.ui.graphics.Color.Transparent)) {
                            for(column in 0 until columns) SelectionContainer {
                                Text(richReply(row.getOrNull(column).orEmpty()),Modifier.width(width).padding(10.dp),
                                    style=MaterialTheme.typography.bodyMedium.copy(fontWeight=if(index==0) FontWeight.SemiBold else FontWeight.Normal))
                            }
                        }
                        HorizontalDivider(Modifier.width(width*columns),color=colors.outlineVariant.copy(alpha=0.5f))
                    }
                }
            }
        }
        is ReplyBlock.Chart -> Surface(modifier.fillMaxWidth(),shape=MaterialTheme.shapes.medium,color=colors.surfaceContainer) {
            val primary=colors.primary;val line=colors.outlineVariant
            Column(Modifier.padding(12.dp).testTag("reply-chart")) {
                Text(block.title,style=MaterialTheme.typography.titleMedium)
                Text(block.unit.ifBlank{"Value"},style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
                val high=maxOf(0.0,block.values.max());val low=minOf(0.0,block.values.min())
                val range=(high-low).takeIf{it>0} ?: 1.0
                Canvas(Modifier.fillMaxWidth().height(160.dp).semantics{contentDescription=replyBlockText(block)}) {
                    val zero=(high/range*size.height).toFloat()
                    drawLine(line,Offset(0f,zero),Offset(size.width,zero))
                    val slot=size.width/block.values.size
                    block.values.forEachIndexed {i,v->val y=((high-v)/range*size.height).toFloat()
                        drawRect(primary,Offset(slot*i+slot*0.18f,minOf(y,zero)),Size(slot*0.64f,kotlin.math.abs(y-zero)))
                    }
                }
                // Exact values/labels remain readable rather than squeezing labels into pixels.
                block.labels.zip(block.values).forEach{(label,value)->Row(Modifier.fillMaxWidth().padding(top=4.dp)){
                    Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                    Text("${java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()} ${block.unit}",style=MaterialTheme.typography.bodyMedium)
                }}
            }
        }
        ReplyBlock.Rule -> HorizontalDivider(modifier.padding(vertical=6.dp),color=colors.outlineVariant)
    }
}

/** Full-screen reading/search/outline uses the exact response, without a composer or model changes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ReplyReader(text:String,onClose:()->Unit) {
    val doc=rememberReply(text);var original by remember{mutableStateOf(false)}
    var query by remember{mutableStateOf("")};var outline by remember{mutableStateOf(false)}
    var match by remember(query){mutableIntStateOf(0)}
    val blocks=if(original) remember(text){text.chunkReplyText(2048).map{ReplyBlock.Code("original",it)}} else doc.blocks
    val results=remember(blocks,query){if(query.isBlank()) emptyList() else blocks.indices.filter{replyBlockText(blocks[it]).contains(query,true)}}
    val headings=doc.blocks.mapIndexedNotNull{i,b->(b as? ReplyBlock.Prose)?.takeIf{it.heading>0}?.let{i to replyBlockText(b)}}
    val list=rememberLazyListState();val scope=rememberCoroutineScope()
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Scaffold(modifier=Modifier.fillMaxSize().testTag("reply-reader"),topBar={TopAppBar(title={Text("Response")},navigationIcon={
            IconButton(onClick=onClose){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Close response reader")}
        },actions={
            IconButton(enabled=headings.isNotEmpty() && !original,onClick={outline=true}){Icon(Icons.Default.List,"Response outline")}
            DropdownMenu(outline,{outline=false}){headings.forEach{(index,title)->DropdownMenuItem(text={Text(title,maxLines=2)},onClick={outline=false;scope.launch{list.scrollToItem(index)}})}}
            IconToggleButton(original,{original=it}){Icon(Icons.Default.Code,"Show original Markdown")}
        })}){padding->Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                OutlinedTextField(query,{query=it.take(160)},Modifier.weight(1f).testTag("reply-search"),singleLine=true,label={Text("Find in response")})
                TextButton(enabled=results.isNotEmpty(),onClick={scope.launch{list.scrollToItem(results[match%results.size]);match++}}){Text(if(query.isBlank()) "Find" else "${results.size} blocks · Next")}
            }
            LazyColumn(state=list,modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                itemsIndexed(blocks){_,block->ReplyContent(block)}
            }
        }}
    }
}
