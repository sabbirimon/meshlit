package com.meshlit.ui.modern

import com.meshlit.chat.ChatMessage
import com.meshlit.core.inference.ModelInfo
import com.meshlit.models.LibraryModel

/** Display cleanup only; exact model IDs and filenames remain available in details. */
internal fun modelDisplayName(value:String):String {
    val name=value.trim().substringAfterLast('/').substringAfterLast('\\')
        .replace(Regex("\\.(gguf|onnx|safetensors|bin)$",RegexOption.IGNORE_CASE),"")
        .replace(Regex("[-_. ]+(?:I?Q[2-8](?:_[A-Z0-9]+)*|F(?:16|32)|BF16)$",RegexOption.IGNORE_CASE),"")
        .replace(Regex("[-_]+")," ").replace(Regex("\\s+")," ").trim()
    return name.ifBlank{"Choose model"}
}

internal fun activeLibraryModel(entries:List<LibraryModel>,loaded:ModelInfo?,loadingPath:String?=null):LibraryModel? {
    val path=loadingPath ?: loaded?.modelPath ?: return null
    return entries.firstOrNull{it.path.isNotBlank() && it.path==path || it.id==path.removePrefix("runanywhere:")}
        ?: entries.firstOrNull{it.id==loaded?.modelName}
}

/** Quotes are editable draft text. Existing input is never truncated or submitted automatically. */
internal fun quotedReplyDraft(message:ChatMessage,draft:String):String? {
    val space=12000-draft.length-2
    if(space<48)return null
    val content=message.text.replace("\r","").lineSequence().take(12).joinToString("\n"){"> $it"}
    var quote=("> ${if(message.role=="user") "You" else "Assistant"}:\n$content").take(minOf(space,1200))
    if(quote.lastOrNull()?.isHighSurrogate()==true)quote=quote.dropLast(1)
    return "$quote\n\n$draft"
}
