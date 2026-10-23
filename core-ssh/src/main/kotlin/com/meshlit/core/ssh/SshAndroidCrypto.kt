package com.meshlit.core.ssh

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/** Android's platform EC parameters omit P-384 on some devices. Apache SSHD
 * enumerates that curve while initializing even when Meshlit only accepts P-256.
 * Warm SSHD's curve registry with a complete provider, then restore the device's
 * original provider order before other application cryptography runs. */
internal object SshAndroidCrypto {
    @Volatile private var ready=false
    @Synchronized fun ensureCurveRegistry() {
        if(ready) return
        val oldPosition=Security.getProviders().indexOfFirst { it.name=="BC" }.let { if(it<0) -1 else it+1 }
        val completeProvider=BouncyCastleProvider()
        Security.removeProvider("BC")
        try {
            check(Security.insertProviderAt(completeProvider,1)==1)
            Class.forName("org.apache.sshd.common.cipher.ECCurves")
            ready=true
        } finally {
            Security.removeProvider("BC")
            if(oldPosition>0) Security.insertProviderAt(completeProvider,oldPosition)
            else Security.addProvider(completeProvider)
        }
    }
}
