package com.meshlit.ui.modern

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meshlit.core.inference.models.OnlineProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A reviewed attachment. Selection never uploads pixels; the composer Send does. */
@Composable
internal fun ChatPhotoAttachment(
    uri: Uri, profiles: List<OnlineProfile>, selectedId: String, enabled: Boolean,
    onProvider: (String) -> Unit, onRemove: () -> Unit, onSetup: () -> Unit, maxHeight: Dp,
) {
    val context = LocalContext.current
    val preview by produceState<Bitmap?>(null, uri) {
        value = null
        value = withContext(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytesLimited(8 * 1024 * 1024) }
                    ?: return@withContext null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
                var sample = 1
                while (bounds.outWidth / sample > 160 || bounds.outHeight / sample > 160) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        }
    }
    var choosing by remember { mutableStateOf(false) }
    val selected = profiles.firstOrNull { it.id == selectedId }
    Surface(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp).testTag("chat-photo-card"),
        shape=RoundedCornerShape(20.dp), color=MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.heightIn(max=maxHeight).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                val bitmap = preview
                if (bitmap != null) Image(bitmap.asImageBitmap(),"Selected photo preview",
                    Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),contentScale=ContentScale.Crop)
                else Icon(Icons.Default.Image,"Photo preview unavailable",Modifier.size(40.dp))
                Column(Modifier.weight(1f).padding(horizontal=10.dp)) {
                    Text("Photo attached",style=MaterialTheme.typography.titleSmall)
                    Text("Ask below, then send",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(enabled=enabled,onClick=onRemove) { Icon(Icons.Default.Close,"Remove photo") }
            }
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick={if(profiles.isEmpty()) onSetup() else choosing=true},enabled=enabled,
                    modifier=Modifier.fillMaxWidth().testTag("chat-photo-model"),shape=RoundedCornerShape(12.dp)) {
                    Text(selected?.let{"${it.name} · ${it.model}"} ?: "Set up a vision model",Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis)
                    Icon(Icons.Default.ExpandMore,null,Modifier.size(20.dp))
                }
                DropdownMenu(choosing,{choosing=false}) {
                    profiles.forEach { profile -> DropdownMenuItem(text={Text("${profile.name}\n${profile.model}",maxLines=3,overflow=TextOverflow.Ellipsis)},
                        onClick={onProvider(profile.id);choosing=false}) }
                    DropdownMenuItem(text={Text("Manage providers")},onClick={choosing=false;onSetup()})
                }
            }
            Text(if(selected==null) "The local chat model reads text only. Connect a vision provider to send this photo."
                else "Send shares this photo with ${Uri.parse(selected.endpoint).host ?: selected.name}. Choose a model that supports images.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun java.io.InputStream.readBytesLimited(limit:Int):ByteArray {
    val out=java.io.ByteArrayOutputStream()
    val buffer=ByteArray(8192)
    while(true) {
        val count=read(buffer)
        if(count<0) return out.toByteArray()
        require(out.size()+count<=limit){"Photo is too large"}
        out.write(buffer,0,count)
    }
}
