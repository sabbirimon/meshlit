package com.meshlit.qualification

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.meshlit.MainActivity
import com.meshlit.legal.LegalAgreementStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/** Opt-in device qualification against a disposable, credential-free real Harness host.
 * Provision qualification/harness-launch privately through run-as; never accepts app notices. */
@RunWith(AndroidJUnit4::class)
class HarnessDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun findWeb(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findWeb(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun dom(script: String? = null): JSONObject {
        val latch = CountDownLatch(1)
        var result = "{}"
        compose.activity.runOnUiThread {
            val web = findWeb(compose.activity.window.decorView)
            if (web == null) latch.countDown() else web.evaluateJavascript(
                script ?: "JSON.stringify({ready:document.readyState,text:document.body.innerText.slice(0,3000),buttons:document.querySelectorAll('button').length,clean:!location.search.includes('token=')})"
            ) { value -> result = org.json.JSONTokener(value).nextValue() as? String ?: "{}"; latch.countDown() }
        }
        check(latch.await(10, TimeUnit.SECONDS)) { "WebView DOM observation timed out" }
        return JSONObject(result)
    }
    @Test fun realHostLoginDisconnectAndNavigation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("Owner must accept notices manually", LegalAgreementStore(context).accepted())
        val fixture = File(context.filesDir, "qualification/harness-launch")
        check(fixture.isFile) { "Private disposable Harness launch fixture missing" }
        val login = fixture.readText().trim()
        val endpoint = com.meshlit.harness.HarnessEndpoint.parse(login).endpoint
        val prefs = context.getSharedPreferences("deepseek-harness-host", 0)
        val previous = prefs.getString("endpoint", null)
        val checks = mutableListOf<String>()
        try {
            compose.waitUntil(180_000) { runCatching { compose.onAllNodesWithContentDescription("Menu").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithTag("boot-open-app").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
            if (compose.onAllNodesWithTag("boot-open-app").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("boot-open-app").performClick()
            if (compose.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Later").performClick()
            compose.onNodeWithContentDescription("Menu").performClick()
            compose.onNodeWithTag("workspace-search").performTextReplacement("Settings")
            closeSoftKeyboard()
            compose.onNodeWithTag("app-drawer").performScrollToNode(hasTestTag("workspace-settings"))
            compose.onNodeWithTag("workspace-settings").performClick()
            compose.onNodeWithTag("settings-search").performTextReplacement("DeepSeek")
            closeSoftKeyboard()
            compose.onNodeWithText("DeepSeek Harness").performClick()
            compose.onNodeWithText("Use your Harness host").assertExists()
            checks += "settings-to-harness"
            compose.onNode(hasSetTextAction() and hasText("HTTPS launch URL")).performTextReplacement("http://192.168.1.1/")
            closeSoftKeyboard()
            compose.onNodeWithText("Connect").performScrollTo().performClick()
            compose.onNodeWithText("Use HTTPS, a root ending in /, and an optional ?token=… login field.").assertExists()
            checks += "plaintext-lan-rejected"
            compose.onNode(hasSetTextAction() and hasText("HTTPS launch URL")).performTextReplacement(login)
            closeSoftKeyboard()
            compose.onNodeWithText("Connect").performScrollTo().performClick()
            compose.onNodeWithText("Connect to this host?").assertExists()
            compose.onNodeWithText("Cancel").performClick()
            checks += "connection-confirmation-cancel"
            compose.onNodeWithText("Connect").performScrollTo().performClick()
            compose.onNodeWithText("Open Harness").performClick()
            compose.waitUntil(60_000) { val d = dom(); d.optBoolean("clean") && d.optInt("buttons") > 0 && d.optString("text").length > 30 }
            if (dom().optString("text").contains("Preview Notice")) {
                dom("(() => {const b=[...document.querySelectorAll('button')].find(b=>b.textContent.trim()==='Continue'); if(b)b.click();return JSON.stringify({});})()")
                compose.waitUntil(15_000) { !dom().optString("text").contains("Preview Notice") }
            }
            if (dom().optString("text").contains("Get started")) {
                dom("(() => {const b=[...document.querySelectorAll('button')].find(b=>b.getAttribute('aria-label')==='Close'); if(b)b.click();return JSON.stringify({});})()")
                compose.waitUntil(15_000) { !dom().optString("text").contains("Get started") }
            }
            compose.waitUntil(15_000) { dom().optString("text").contains("Into the Unknown") }
            val observed = dom()
            assertEquals(endpoint, prefs.getString("endpoint", null))
            assertFalse(prefs.all.values.any { it.toString().contains("token=") })
            checks += "real-host-rendered-with-interactive-controls"
            checks += "bootstrap-token-removed-and-clean-address-saved"
            val painted = CountDownLatch(1)
            compose.activity.runOnUiThread {
                findWeb(compose.activity.window.decorView)!!.postVisualStateCallback(1, object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) { painted.countDown() }
                })
            }
            check(painted.await(10, TimeUnit.SECONDS)) { "Harness paint timed out" }
            Thread.sleep(1500) // Allow the native compositor to present the completed WebView frame.
            val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val out = File(context.filesDir, "qualification/harness-rendered.png")
            out.outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; screenshot.recycle()
            compose.onNodeWithText("Disconnect").performClick()
            compose.onNodeWithText("Use your Harness host").assertExists()
            checks += "disconnect-returns-to-setup"
            compose.onNodeWithText("Connect").performScrollTo().performClick()
            compose.onNodeWithText("Open Harness").performClick()
            compose.waitUntil(30_000) { dom().optInt("buttons") > 0 }
            checks += "cookie-reconnect-without-token"
            compose.onNodeWithText("Back").performClick()
            compose.onNodeWithText("Use your Harness host").assertExists()
            compose.onNodeWithText("Back").performClick()
            compose.onNodeWithTag("settings-search").assertExists()
            checks += "back-to-settings"
            File(context.filesDir, "qualification/harness-device.json").writeText(JSONObject()
                .put("checks", org.json.JSONArray(checks)).put("modelRequests", 0)
                .put("renderedText", observed.optString("text"))
                .put("webView", WebView.getCurrentWebViewPackage()?.versionName)
                .put("device", android.os.Build.MODEL).put("android", android.os.Build.VERSION.RELEASE).toString(2))
        } finally {
            if (previous == null) prefs.edit().remove("endpoint").commit() else prefs.edit().putString("endpoint", previous).commit()
            fixture.delete()
        }
    }
}
