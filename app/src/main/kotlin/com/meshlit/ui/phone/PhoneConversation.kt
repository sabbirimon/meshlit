package com.meshlit.ui.phone

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshlit.R

@Composable
internal fun PhoneGreeting(title:String, status:@Composable ()->Unit) {
    Column(horizontalAlignment=Alignment.CenterHorizontally,
        modifier=Modifier.fillMaxWidth().padding(horizontal=28.dp)) {
        Icon(painterResource(R.drawable.meshlit_mark),null,Modifier.size(38.dp),tint=androidx.compose.ui.graphics.Color.Unspecified)
        Spacer(Modifier.height(24.dp))
        Text(title,style=MaterialTheme.typography.headlineLarge.copy(fontWeight=FontWeight.Normal,lineHeight=40.sp),
            textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onBackground)
        status()
    }
}

@Composable
internal fun PhonePrompt(value:String,onChange:(String)->Unit,placeholder:String,enabled:Boolean,
                        focus:FocusRequester,onSend:()->Unit,attachments:@Composable ()->Unit,
                        tools:@Composable ()->Unit,action:@Composable ()->Unit) {
    val colors=MaterialTheme.colorScheme
    Surface(shape=RoundedCornerShape(10.dp),color=colors.surfaceContainerHigh,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=4.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
            attachments()
            BasicTextField(value=value,onValueChange=onChange,enabled=enabled,
                modifier=Modifier.weight(1f).heightIn(min=48.dp).focusRequester(focus)
                    .semantics{contentDescription=placeholder}.testTag("chat-composer"),
                maxLines=5,textStyle=MaterialTheme.typography.bodyLarge.copy(color=colors.onSurface,fontSize=18.sp,lineHeight=26.sp),
                cursorBrush=SolidColor(colors.primary),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Send),
                keyboardActions=KeyboardActions(onSend={onSend()}),decorationBox={field->
                    Box(Modifier.fillMaxWidth().padding(vertical=12.dp),contentAlignment=Alignment.CenterStart){
                        if(value.isEmpty()) Text(placeholder,style=MaterialTheme.typography.bodyLarge,color=colors.onSurfaceVariant)
                        field()
                    }
                })
            tools()
            action()
        }
    }
}
