package com.connecthub.media.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UploadSizeGuardFilterTest {

    private static final long MB = 1024L * 1024L;
    private final UploadSizeGuardFilter filter = new UploadSizeGuardFilter();
    private final FilterChain chain = mock(FilterChain.class);

    private MockHttpServletResponse post(String path, String tier, long contentLength) throws Exception {
        // declare the size without building a real body
        MockHttpServletRequest sized = new MockHttpServletRequest("POST", path) {
            @Override public long getContentLengthLong() { return contentLength; }
        };
        sized.setRequestURI(path);
        if (tier != null) sized.addHeader("X-Subscription-Tier", tier);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        if (filter.shouldNotFilter(sized)) chain.doFilter(sized, resp);
        else filter.doFilterInternal(sized, resp, chain);
        return resp;
    }

    @Test
    void freeUser_over10MB_getsImmediate413_withoutReachingTheController() throws Exception {
        MockHttpServletResponse resp = post("/api/v1/media/upload", "FREE", 50 * MB);
        assertEquals(413, resp.getStatus());
        assertTrue(resp.getContentAsString().contains("10 MB"));
        verifyNoInteractions(chain);
    }

    @Test
    void freeUser_underLimit_passes() throws Exception {
        assertEquals(200, post("/api/v1/media/upload", "FREE", 9 * MB).getStatus());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void multipartOverheadIsTolerated() throws Exception {
        // a file of exactly 10MB arrives as slightly more than 10MB on the wire
        assertEquals(200, post("/api/v1/media/upload", "FREE", 10 * MB + 500).getStatus());
    }

    @Test
    void paidUser_canUpload200MB_but_not300MB() throws Exception {
        assertEquals(200, post("/api/v1/media/upload", "PRO", 200 * MB).getStatus());
        assertEquals(413, post("/api/v1/media/upload", "PRO", 300 * MB).getStatus());
    }

    @Test
    void missingTier_isTreatedAsFree() throws Exception {
        assertEquals(413, post("/api/v1/media/upload", null, 50 * MB).getStatus());
    }

    @Test
    void profilePicture_capped_at2MB_forEveryone() throws Exception {
        assertEquals(413, post("/api/v1/media/profile-picture", "PRO", 20 * MB).getStatus());
        assertEquals(200, post("/api/v1/media/profile-picture", "FREE", 1 * MB).getStatus());
    }

    @Test
    void otherPaths_andNonPost_areIgnored() throws Exception {
        assertEquals(200, post("/api/v1/media/room/abc", "FREE", 50 * MB).getStatus());
        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/v1/media/upload");
        assertTrue(filter.shouldNotFilter(get));
    }
}
