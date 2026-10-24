package com.meshlit.browser

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual WebView DOM behavior with explicit test pages; not real-model autonomous task proof. */
@RunWith(AndroidJUnit4::class)
class BrowserDomAndroidTest {
    @Test fun autonomousNavigationDoesNotFireSiteClickHandlersAndRejectsStaleElements()=runBlocking {
        val scenario=ActivityScenario.launch(BrowserActivity::class.java)
        val loaded=CompletableDeferred<Unit>();lateinit var web:WebView
        try {
            scenario.onActivity{activity->web=WebView(activity).apply{
                settings.javaScriptEnabled=true;settings.blockNetworkLoads=true
                webViewClient=object:WebViewClient(){override fun onPageFinished(view:WebView,url:String?){loaded.complete(Unit)}}
            };activity.setContentView(web);web.loadDataWithBaseURL("https://site.test/","<a id='next' href='/next' onclick='window.clicked=true'>Read next</a>","text/html","UTF-8",null)}
            withTimeout(15000){loaded.await()};delay(500)
            suspend fun eval(script:String):String {
                val value=CompletableDeferred<String>();scenario.onActivity{web.evaluateJavascript(script){raw->value.complete(Json.parseToJsonElement(raw).jsonPrimitive.content)}}
                return withTimeout(5000){value.await()}
            }
            val snapshot=Json.parseToJsonElement(eval(BrowserActivity.SNAPSHOT_SCRIPT)).jsonObject
            assertEquals(1,snapshot["elements"]!!.jsonArray.size)
            val result=Json.parseToJsonElement(eval("(${BrowserActivity.APPLY_SCRIPT})({action:'click',index:0},true)")).jsonObject
            assertTrue(result["ok"]!!.jsonPrimitive.boolean);assertEquals("https://site.test/next",result["navigate"]!!.jsonPrimitive.content)
            assertEquals("false",eval("String(window.clicked===true)"))
            eval(BrowserActivity.SNAPSHOT_SCRIPT)
            eval("document.getElementById('next').textContent='Delete records';'changed'")
            assertFalse(Json.parseToJsonElement(eval("(${BrowserActivity.APPLY_SCRIPT})({action:'click',index:0},true)")).jsonObject["ok"]!!.jsonPrimitive.boolean)
            eval("document.body.innerHTML='<input type=\"password\"><a href=\"/next\">Next</a>'; 'changed'")
            assertTrue(Json.parseToJsonElement(eval(BrowserActivity.SNAPSHOT_SCRIPT)).jsonObject["sensitiveFlow"]!!.jsonPrimitive.boolean)
            eval("document.body.innerHTML='<a id=\"later\" style=\"position:absolute;top:300vh\" href=\"/later\">Later</a>';'changed'")
            assertEquals(0,Json.parseToJsonElement(eval(BrowserActivity.SNAPSHOT_SCRIPT)).jsonObject["elements"]!!.jsonArray.size)
            eval("window.scrollTo(0,innerHeight*2.5);'scrolled'");delay(200)
            assertEquals(1,Json.parseToJsonElement(eval(BrowserActivity.SNAPSHOT_SCRIPT)).jsonObject["elements"]!!.jsonArray.size)
            eval("window.scrollTo(0,0);document.body.innerHTML=Array.from({length:80},(_,i)=>'<a href=\"/'+('x'.repeat(3000))+i+'\">Read '+i+'</a>').join(' ');'changed'")
            val bounded=eval(BrowserActivity.SNAPSHOT_SCRIPT)
            assertTrue(bounded.length<=24000);assertTrue(Json.parseToJsonElement(bounded).jsonObject["truncated"]!!.jsonPrimitive.boolean)
            println("MESHLIT_BROWSER_DOM testPage=true noClickHandlers=true staleRejected=true passwordPause=true viewportScroll=true snapshotBounded=true realModelTaskProof=false")
        } finally{scenario.onActivity{web.destroy()};scenario.close()}
    }
}
