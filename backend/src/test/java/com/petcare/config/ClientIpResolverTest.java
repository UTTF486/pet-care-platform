package com.petcare.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {

    @Test
    void ignoresForwardedHeaderFromUntrustedPeer() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.8");
        request.addHeader("X-Forwarded-For", "198.51.100.7");

        assertEquals("203.0.113.8", resolver.resolve(request));
    }

    @Test
    void usesForwardedClientBehindTrustedProxy() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.7");

        assertEquals("198.51.100.7", resolver.resolve(request));
    }

    @Test
    void skipsTrustedProxyChainFromRight() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1,10.0.0.2");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "spoofed-value, 198.51.100.7, 10.0.0.2");

        assertEquals("198.51.100.7", resolver.resolve(request));
    }

    @Test
    void normalizesMappedIpv4AndPort() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.7:4321");

        assertEquals("198.51.100.7", resolver.resolve(request));
    }

    @Test
    void supportsTrustedProxyCidr() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.12.3.4");
        request.addHeader("X-Forwarded-For", "198.51.100.9");

        assertEquals("198.51.100.9", resolver.resolve(request));
    }
}
