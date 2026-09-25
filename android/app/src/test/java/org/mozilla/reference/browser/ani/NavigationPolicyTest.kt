/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mozilla.reference.browser.ani.NavigationPolicy.Decision.*

class NavigationPolicyTest {
    @Test fun webRequestsAndRedirectsNeverRequireNavigationReplay() {
        for (url in listOf("https://login.example/", "http://example.com/", "blob:https://example.com/id", "HTTPS://example.com/")) {
            for (direct in listOf(false, true)) for (frame in listOf(false, true)) {
                assertEquals(url, ALLOW, NavigationPolicy.classify(url, direct, frame))
            }
        }
    }

    @Test fun webContentCannotNavigateToPrivilegedOrExecutableSchemes() {
        for (url in listOf("file:///secret", "content://provider/secret", "resource://test", "chrome://test", "javascript:alert(1)", "data:text/html,test", "about:config", "JAVASCRIPT:alert(1)")) {
            assertEquals(url, DENY, NavigationPolicy.classify(url, direct = false, subframe = false))
        }
    }

    @Test fun internalPagesAreAllowedOnlyForDirectTopLevelNavigation() {
        assertEquals(ALLOW, NavigationPolicy.classify("about:home", direct = true, subframe = false))
        assertEquals(DENY, NavigationPolicy.classify("about:home", direct = true, subframe = true))
        assertEquals(DENY, NavigationPolicy.classify("about:home", direct = false, subframe = false))
    }

    @Test fun blankFramesRemainAvailableToSites() {
        assertEquals(ALLOW, NavigationPolicy.classify("about:blank", direct = false, subframe = true))
    }

    @Test fun externalApplicationsRequireConfirmationAndCannotLaunchFromFrames() {
        for (url in listOf("mailto:person@example.com", "tel:1234", "intent://host/#Intent;scheme=test;end")) {
            assertEquals(CONFIRM_EXTERNAL, NavigationPolicy.classify(url, direct = false, subframe = false))
            assertEquals(DENY, NavigationPolicy.classify(url, direct = false, subframe = true))
        }
    }

    @Test fun malformedSchemlessRequestsAreDenied() {
        assertEquals(DENY, NavigationPolicy.classify("", direct = false, subframe = false))
        assertEquals(DENY, NavigationPolicy.classify("relative/path", direct = false, subframe = false))
    }
}
