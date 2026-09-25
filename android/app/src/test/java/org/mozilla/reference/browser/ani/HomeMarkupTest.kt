package org.mozilla.reference.browser.ani

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeMarkupTest {
    @Test fun savedUrlsRemainDataAndCannotBecomeClickHandlerCode() {
        val url = "https://example.com/#&apos;);AniHomeBridge.toggleAdShield();//"
        val markup = AnimeHub.buildTilesHtml(listOf(AniHomeTile("safe", "Test", url)))
        assertTrue(markup.contains("onclick=\"launch(this.dataset.url)\""))
        assertTrue(markup.contains("&amp;apos;"))
        assertFalse(markup.contains("onclick=\"launch('"))
    }
}
