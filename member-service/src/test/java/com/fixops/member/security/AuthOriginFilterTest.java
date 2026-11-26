package com.fixops.member.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class AuthOriginFilterTest {
    private final AuthOriginFilter filter = new AuthOriginFilter("http://localhost:8088");
    private MockHttpServletRequest request() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/members/refresh");
        req.setServletPath("/members/refresh");
        return req;
    }

    @Test
    void missingOriginIsRejectedWithContractFields() throws Exception {
        var req = request();
        req.addHeader("X-FixOps-CSRF", "1");
        var res = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(req, res, chain);
        assertEquals(403, res.getStatus());
        assertTrue(res.getContentAsString().contains("CSRF_REJECTED"));
        assertTrue(res.getContentAsString().contains("traceId"));
        assertTrue(res.getContentAsString().contains("timestamp"));
        assertNull(chain.getRequest());
    }

    @Test
    void allowedOriginAndHeaderReachControllerChain() throws Exception {
        var req = request();
        req.addHeader("Origin", "http://localhost:8088");
        req.addHeader("X-FixOps-CSRF", "1");
        var res = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(req, res, chain);
        assertSame(req, chain.getRequest());
        assertEquals(200, res.getStatus());
    }
}
