package app.anibrave;

import java.net.URI;
import java.util.Locale;

final class SiteKey {
    static String of(String url) {
        if(url==null)return "";
        try {
            URI uri = URI.create(url);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) return "";
            String host = uri.getHost(); if (host == null) return "";
            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) host = host.substring(4);
            else if (host.startsWith("m.")) host = host.substring(2);
            if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
            return host;
        } catch (IllegalArgumentException e) { return ""; }
    }
    static String blankNewTab(String url) {
        if (url == null) return "about:blank";
        String value = url.toLowerCase(Locale.ROOT);
        if (value.equals("chrome://newtab") || value.startsWith("chrome://newtab/") || value.equals("chrome-native://newtab") || value.startsWith("chrome-native://newtab/")) return "about:blank";
        return url;
    }
}
