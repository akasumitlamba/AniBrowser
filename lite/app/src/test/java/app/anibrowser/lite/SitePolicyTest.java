package app.anibrowser.lite;
import org.junit.Test;
import static org.junit.Assert.*;
public class SitePolicyTest {
    @Test public void selectedSearchEngineIsUsedWithoutChangingUrls() {
        assertEquals("https://duckduckgo.com/?q=video+speed",SitePolicy.input("video speed",1));
        assertEquals("https://www.bing.com/search?q=cats",SitePolicy.input("cats",2));
        assertEquals("https://example.com",SitePolicy.input("example.com",2));
        assertEquals("https://www.google.com/search?q=cats",SitePolicy.input("cats",0));
    }
    @Test public void promptEveryCrossSiteAttempt() {
        for(int i=0;i<3;i++) assertTrue(SitePolicy.needsPrompt("https://video.example/a","https://other.example/b",false,false));
    }
    @Test public void searchResultsAndDirectAddressAreExempt() {
        assertFalse(SitePolicy.needsPrompt("https://www.google.co.in/search?q=a","https://video.example",false,false));
        assertFalse(SitePolicy.needsPrompt("https://duckduckgo.com/?q=a","https://video.example",false,true));
        assertFalse(SitePolicy.needsPrompt("https://one.example","https://two.example",true,false));
    }
    @Test public void searchLookalikesAreNotExempt() {
        assertTrue(SitePolicy.needsPrompt("https://google.com.evil.example","https://video.example",false,false));
        assertTrue(SitePolicy.needsPrompt("https://google.com@evil.example","https://video.example",false,false));
    }
    @Test public void sameSiteModesAndSpeedsShareMobileAliases() {
        assertEquals(SitePolicy.key("https://www.example.com/a"),SitePolicy.key("https://m.example.com/b"));
        assertFalse(SitePolicy.needsPrompt("https://example.com","https://www.example.com/next",false,false));
        assertTrue(SitePolicy.needsPrompt("https://example.com","https://example.com/popup",false,true));
    }
    @Test public void unsafeAddressesBecomeSearches() {
        assertFalse(SitePolicy.web("javascript:alert(1)"));
        assertTrue(SitePolicy.input("javascript:alert(1)").startsWith("https://www.google.com/search?"));
        assertEquals("https://example.com",SitePolicy.input("example.com"));
        assertEquals("http://localhost:8080/a",SitePolicy.input("http://localhost:8080/a"));
    }
}
