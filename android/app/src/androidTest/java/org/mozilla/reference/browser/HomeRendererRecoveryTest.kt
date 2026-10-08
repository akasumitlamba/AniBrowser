/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser

import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.reference.browser.ani.AnimeHubView

class HomeRendererRecoveryTest {
    @Test fun homeRecoversAfterItsRendererIsTerminated() {
        ActivityScenario.launch(BrowserActivity::class.java).use { scenario ->
            lateinit var home: AnimeHubView
            scenario.onActivity { activity ->
                home = AnimeHubView(activity)
                activity.findViewById<ViewGroup>(android.R.id.content).addView(
                    home, ViewGroup.LayoutParams(-1, -1),
                )
                home.loadContent()
            }
            try {
                awaitMain { home.contentReady }
                lateinit var oldView: WebView
                scenario.onActivity {
                    oldView = home.getChildAt(0) as WebView
                    assertTrue("Renderer termination was accepted", oldView.webViewRenderProcess!!.terminate())
                }
                awaitMain { home.contentReady && home.getChildAt(0) !== oldView }
            } finally {
                scenario.onActivity { (home.parent as? ViewGroup)?.removeView(home) }
            }
        }
    }

    private fun awaitMain(condition: () -> Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = android.os.SystemClock.uptimeMillis() + 30_000
        do {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            Thread.sleep(100)
        } while (android.os.SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Home did not become ready within 30 seconds")
    }
}
