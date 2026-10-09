package app.anibrave;
public final class SiteKeyTest {
    private static void equal(String actual,String expected){if(!actual.equals(expected))throw new AssertionError(actual+" != "+expected);}
    public static void main(String[] args){
        equal(SiteKey.of("https://WWW.Example.com/watch?q=1"),"example.com");
        equal(SiteKey.of("https://m.example.com:8443/watch"),"example.com");
        equal(SiteKey.of("https://player.example.com/watch"),"player.example.com");
        equal(SiteKey.of("file:///private/video"),"");
        equal(SiteKey.of("javascript:alert(1)"),"");
        equal(SiteKey.of("not a URL"),"");
        equal(SiteKey.of(null),"");
        equal(SiteKey.blankNewTab("chrome://newtab/"),"about:blank");
        equal(SiteKey.blankNewTab("chrome-native://newtab/"),"about:blank");
        equal(SiteKey.blankNewTab("https://example.com/newtab/"),"https://example.com/newtab/");
        System.out.println("10 site identity and blank-tab checks passed");
    }
}
