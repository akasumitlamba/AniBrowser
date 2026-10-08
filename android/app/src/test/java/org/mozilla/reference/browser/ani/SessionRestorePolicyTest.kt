package org.mozilla.reference.browser.ani

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRestorePolicyTest {
    @Test fun websiteWithPlaceholderSnapshotLoadsItsSavedUrl() {
        listOf(null, "", "about:blank", "about:home").forEach {
            assertTrue(SessionRestorePolicy.needsUrlReload("https://example.com/watch", it))
        }
    }
    @Test fun validHistoryIncludingRedirectsIsRetained() {
        assertFalse(SessionRestorePolicy.needsUrlReload("https://example.com", "https://example.com/watch"))
    }
    @Test fun homeAndLocalTabsAreNotRewritten() {
        listOf("about:home", "about:blank", "file:///document.html").forEach {
            assertFalse(SessionRestorePolicy.needsUrlReload(it, "about:blank"))
        }
    }
}
