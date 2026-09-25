/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import org.junit.Assert.*
import org.junit.Test

class ViewingModeTest {
    private val tablet = "Mozilla/5.0 (Android 10; Tablet; rv:157.0) Gecko/157.0 Firefox/157.0"

    @Test fun mobileSelectionRequestsAPhoneEvenOnATablet() {
        val agent = ViewingMode.userAgent(0, tablet, "10")!!
        assertTrue(agent.contains("Android 10; Mobile; rv:157.0"))
        assertFalse(agent.contains("Tablet"))
        assertTrue(agent.endsWith("Firefox/157.0"))
    }

    @Test fun modesDoNotLeakDesktopIdentityBackIntoMobile() {
        assertTrue(ViewingMode.userAgent(1, tablet, "10")!!.contains("X11; Linux x86_64"))
        assertNull(ViewingMode.userAgent(2, tablet, "10"))
        assertTrue(ViewingMode.userAgent(0, tablet, "10")!!.contains("Mobile"))
        assertFalse(ViewingMode.userAgent(0, tablet, "10")!!.contains("X11"))
    }

    @Test fun choiceSurvivesTheMobileEndpointRedirectWithoutMatchingUnrelatedSites() {
        assertEquals(ViewingMode.siteKey("www.youtube.com"), ViewingMode.siteKey("m.youtube.com"))
        assertNotEquals(ViewingMode.siteKey("youtube.com"), ViewingMode.siteKey("youtube.com.other.test"))
    }

    @Test fun engineUpdatesKeepTheirOwnVersionAndUnknownAgentsUseEngineDefault() {
        assertTrue(ViewingMode.userAgent(0, tablet.replace("157.0", "158.1"), "14")!!.contains("rv:158.1"))
        assertNull(ViewingMode.userAgent(0, "unknown", "10"))
    }
}
