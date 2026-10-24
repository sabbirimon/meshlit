package com.meshlit.browser
import com.meshlit.core.inference.browser.BrowserAction
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
class BrowserPolicyRulesTest {
    private val policy=BrowserPolicy(enabled=true,agentAllowed=true,navigationAllowed=true,typingAllowed=true)
    private fun state(tag:String="A",href:String="https://site.test/next",label:String="Read next",type:String="text",sensitive:Boolean=false)=buildJsonObject{
        put("url","https://site.test/start");put("sensitiveFlow",sensitive);put("elements",buildJsonArray{add(buildJsonObject{
            put("tag",tag);put("href",href);put("label",label);put("type",type);put("formOrigin","https://site.test")
        })})
    }
    @Test fun exactOriginIncludesPortAndRejectsCredentials(){assertEquals("https://site.test:8443",BrowserPolicyRules.origin("https://SITE.test:8443/p"));listOf("http://site.test","https://u:p@site.test","file:///tmp").forEach{url->assertThrows(IllegalArgumentException::class.java){BrowserPolicyRules.origin(url)}}}
    @Test fun autonomousLinksNeedIndependentGrantsAndOrigin(){val action=BrowserAction("click",0);BrowserPolicyRules.check(policy,"https://site.test",action,state(),true)
        listOf(policy.copy(enabled=false),policy.copy(agentAllowed=false),policy.copy(navigationAllowed=false)).forEach{p->assertThrows(IllegalArgumentException::class.java){BrowserPolicyRules.check(p,"https://site.test",action,state(),true)}}
        listOf("https://site.test:8443/","https://other.test/","javascript:alert(1)").forEach{href->assertThrows(IllegalArgumentException::class.java){BrowserPolicyRules.check(policy,"https://site.test",action,state(href=href),true)}}
    }
    @Test fun buttonsLoginsAndSensitiveActionsPause(){listOf(state(tag="BUTTON"),state(label="Delete data"),state(sensitive=true)).forEach{s->assertThrows(IllegalArgumentException::class.java){BrowserPolicyRules.check(policy,"https://site.test",BrowserAction("click",0),s,true)}}}
    @Test fun ordinaryInputCanBeEnabledButPasswordCannot(){BrowserPolicyRules.check(policy,"https://site.test",BrowserAction("type",0,"find"),state(tag="INPUT"),false)
        assertThrows(IllegalArgumentException::class.java){BrowserPolicyRules.check(policy,"https://site.test",BrowserAction("type",0,"secret"),state(tag="INPUT",type="password"),false)}
        assertThrows(IllegalArgumentException::class.java){BrowserPolicyRules.check(policy.copy(typingAllowed=false),"https://site.test",BrowserAction("type",0,"find"),state(tag="INPUT"),false)}
    }
    @Test fun scrollDoneStillRequireEnabledNonSensitiveOrigin(){BrowserPolicyRules.check(policy,"https://site.test",BrowserAction("scroll",delta=300),state(),true)
        assertThrows(IllegalArgumentException::class.java){BrowserPolicyRules.check(policy,"https://site.test",BrowserAction("done"),state(sensitive=true),true)}
    }
}
