package com.meshlit.ui.phone

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhoneTurnAnchors(
    prompts:List<String>, current:Int, modifier:Modifier=Modifier, jump:(Int)->Unit
) {
    if(prompts.size<2)return
    var open by remember { mutableStateOf(false) }
    val selected = current.coerceIn(prompts.indices)
    val colors=MaterialTheme.colorScheme
    Box(modifier.width(48.dp).heightIn(min=48.dp,max=168.dp)
        .clickable(onClickLabel="Browse conversation turns"){open=true}
        .semantics{contentDescription="Conversation anchors, turn ${selected+1} of ${prompts.size}"},
        contentAlignment=Alignment.CenterStart) {
        val marks=minOf(prompts.size,20)
        Canvas(Modifier.padding(start=5.dp).size(10.dp,(marks*6).dp)){
            val currentMark=(selected.toLong()*(marks-1)/prompts.lastIndex).toInt()
            repeat(marks){i->
                val y=size.height*(i+.5f)/marks
                drawLine(if(i==currentMark)colors.primary else colors.outlineVariant,Offset(0f,y),
                    Offset(if(i==currentMark)size.width else size.width*.55f,y),2.dp.toPx())
            }
        }
    }
    if(open)ModalBottomSheet(onDismissRequest={open=false}){
        Text("Conversation turns",Modifier.padding(horizontal=20.dp),style=MaterialTheme.typography.titleLarge)
        val list=androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex=selected)
        LazyColumn(state=list,contentPadding=PaddingValues(12.dp)){
            itemsIndexed(prompts){index,prompt->
                TextButton(onClick={open=false;jump(index)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                    Text(if(index==selected)"●" else (index+1).toString(),Modifier.width(32.dp))
                    Text(prompt.ifBlank{"Attachment"},Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis)
                }
            }
        }
    }
}
