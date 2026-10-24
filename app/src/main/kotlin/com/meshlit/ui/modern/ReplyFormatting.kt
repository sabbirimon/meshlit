package com.meshlit.ui.modern

import com.meshlit.workspace.richtext.chunkReplyText as sharedChunkReplyText

internal typealias ReplySpan = com.meshlit.workspace.richtext.ReplySpan
internal typealias ReplyDocument = com.meshlit.workspace.richtext.ReplyDocument
internal fun String.chunkReplyText(limit: Int) = sharedChunkReplyText(limit)
internal fun safeReplyUrl(value: String) = com.meshlit.workspace.richtext.safeReplyUrl(value)
internal fun formatReply(text: String) = com.meshlit.workspace.richtext.formatReply(text)
internal fun parseReplyChart(text: String) = com.meshlit.workspace.richtext.parseReplyChart(text)

internal fun replyBlockText(block: com.meshlit.workspace.richtext.ReplyBlock) = com.meshlit.workspace.richtext.replyBlockText(block)
