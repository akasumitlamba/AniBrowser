/* SPDX-License-Identifier: MPL-2.0 */
package app.anibrowser.lite;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;

public final class SitePolicy {
    private SitePolicy() {}
    private static final Set<String> SEARCH = new HashSet<>(Arrays.asList("google.com", "google.co.in", "google.co.uk",
        "google.ca", "google.com.au", "google.de", "google.fr", "bing.com", "duckduckgo.com",
        "search.yahoo.com", "search.brave.com", "ecosia.org", "startpage.com", "search.yahoo.co.jp"));
    public static String host(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT).replaceFirst("\\.$", "");
        } catch (RuntimeException e) { return ""; }
    }
    public static String key(String url) {
        return host(url).replaceFirst("^(www\\.|m\\.|mobile\\.)", "");
    }
    public static boolean web(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        return (lower.startsWith("https://") || lower.startsWith("http://")) && !host(url).isEmpty();
    }
    public static boolean search(String url) { return SEARCH.contains(key(url)); }
    public static boolean needsPrompt(String source, String destination, boolean direct, boolean popup) {
        if (direct) return false;
        if (search(source)) return false;
        return popup || (web(source) && !key(source).equals(key(destination)));
    }
    public static String input(String text) {
        String value = text.trim();
        if (web(value)) return value;
        if (!value.contains(" ") && !value.contains("://") && (value.contains(".") || value.startsWith("localhost"))) {
            String candidate = "https://" + value;
            if (web(candidate)) return candidate;
        }
        try { return "https://www.google.com/search?q=" + java.net.URLEncoder.encode(value, "UTF-8"); }
        catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
    }
}
