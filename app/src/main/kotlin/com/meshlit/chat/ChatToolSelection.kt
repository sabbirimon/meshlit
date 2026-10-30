package com.meshlit.chat

/** Editing a conversation grant never enables the corresponding device/service permission. */
enum class ChatToolGroup(val label:String,val count:Int,val restrictedInReview:Boolean=false) {
    MEMORY("Personal memory tools",1,true), LOCAL_SEARCH("Local search tools",1),
    WEB("Web search and page tools",3), PHONE("Phone tools",4,true),
    NODE("Node tools and security references",8,true), CRYPTO("Local cryptography tools",1),
    AUDIO("GibberLink audio tools",2), REMOTE("Multi-node command tools",3),
    PEER("P2P chat and command tools",4);

    fun selected(options:ChatOptions):Boolean=when(this) {
        MEMORY->options.memoryTools; LOCAL_SEARCH->options.localSearchTools; WEB->options.webTools
        PHONE->options.phoneTools; NODE->options.nodeTools; CRYPTO->options.cryptoTools
        AUDIO->options.gibberlinkTools; REMOTE->options.remoteTools; PEER->options.peerTools
    }
    private fun changed(options:ChatOptions,enabled:Boolean):ChatOptions=when(this) {
        MEMORY->options.copy(memoryTools=enabled); LOCAL_SEARCH->options.copy(localSearchTools=enabled)
        WEB->options.copy(webTools=enabled); PHONE->options.copy(phoneTools=enabled)
        NODE->options.copy(nodeTools=enabled); CRYPTO->options.copy(cryptoTools=enabled)
        AUDIO->options.copy(gibberlinkTools=enabled); REMOTE->options.copy(remoteTools=enabled)
        PEER->options.copy(peerTools=enabled)
    }
    fun unavailableReason(options:ChatOptions,review:Boolean):String? {
        // Revocation stays possible even for an older configuration that is no longer eligible.
        if(selected(options)) return null
        if(review && restrictedInReview) return "Unavailable in the Play review build."
        if(!options.supportsLocalToolSelection) return "Select Local model to enable chat tools."
        if(options.toolCount+count>10) return "Needs $count slots; ${maxOf(0,10-options.toolCount)} available. Turn off another group first."
        return null
    }
    fun update(options:ChatOptions,enabled:Boolean,review:Boolean=false):ChatOptions {
        if(enabled) require(unavailableReason(options,review)==null){unavailableReason(options,review).orEmpty()}
        return changed(options,enabled)
    }
}

val ChatOptions.supportsLocalToolSelection:Boolean
    get()=onlineProfileId==null && routeId==null && colibriMode=="OFF"
