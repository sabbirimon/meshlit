package com.meshlit.observability

/** Export sanitization is also used by the preview, so displayed and exported
 * entries match. This is best-effort: users still choose the export destination. */
object LogRedaction {
    private val secrets=Regex("(?i)(authorization|password|passwd|secret|token|api[-_]?key|cookie|prompt|content|body)")
    private val bearer=Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+")
    private val hf=Regex("hf_[A-Za-z0-9]{8,}")
    private val assignment=Regex("(?i)((?:token|api[-_]?key|password|secret|signature)=)[^\\s&\"']+")
    fun text(value:String):String=assignment.replace(hf.replace(bearer.replace(value,"Bearer [redacted]"),"[redacted]")) {
        "${it.groupValues[1]}[redacted]"
    }
    fun entry(entry:LogBuffer.Entry):LogBuffer.Entry=entry.copy(
        message=text(entry.message),errorMessage=entry.errorMessage?.let(::text),
        context=entry.context.mapValues { (key,value) -> if(secrets.containsMatchIn(key)) "[redacted]" else text(value.toString()) })
}
