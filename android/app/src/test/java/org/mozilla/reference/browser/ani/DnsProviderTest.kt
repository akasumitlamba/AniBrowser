/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DnsProviderTest {
    @Test fun invalidCustomEndpointsCannotEnableDns() {
        for (value in listOf("", "dns.google", "http://dns.google/dns-query", "https:///dns-query",
            "https://user:password@dns.google/dns-query", "https://dns.google/dns-query#fragment",
            "https://dns.google:0/dns-query", "https://dns.google:65536/dns-query",
            "https://dns.google/dns-query{?dns}", "https://bad host/dns-query")) {
            assertNull(value, DnsProvider.normalizeEndpoint(value))
            assertEquals("", DnsProvider.endpoint(DnsProvider.CUSTOM, value))
        }
    }

    @Test fun acceptsProviderPathsQueriesAndPortsWithoutRewritingThem() {
        for (url in listOf("https://dns.google/dns-query", "https://dns.example.com/profile-id",
            "https://dns.example.com:8443/dns-query?token=abc", "https://[2606:4700:4700::1111]/dns-query")) {
            assertEquals(url, DnsProvider.normalizeEndpoint("  $url  "))
        }
    }

    @Test fun missingOrObsoleteSelectionsUseSystemDns() {
        assertEquals(DnsProvider.SYSTEM, DnsProvider.find(null).id)
        assertEquals("", DnsProvider.endpoint("removed-provider", "https://dns.google/dns-query"))
        assertEquals("", DnsProvider.endpoint(DnsProvider.SYSTEM, "https://dns.google/dns-query"))
    }

    @Test fun presetsDoNotUseAnOldCustomEndpoint() {
        assertEquals("https://cloudflare-dns.com/dns-query", DnsProvider.endpoint("cloudflare", "https://old.example/dns-query"))
        assertEquals("https://dns.google/dns-query", DnsProvider.endpoint("google", ""))
        assertEquals("https://dns.quad9.net/dns-query", DnsProvider.endpoint("quad9", ""))
    }
}
