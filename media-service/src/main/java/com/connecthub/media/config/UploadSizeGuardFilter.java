package com.connecthub.media.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects an upload with 413 as soon as its declared Content-Length exceeds the caller's plan limit,
 * before the server spools the (possibly 250MB) body to disk. The multipart ceiling in application.yml
 * has to sit above the largest plan so paid users can upload; this filter is what keeps a FREE user
 * from being able to push that much data through. Uploads without a Content-Length (chunked) are
 * still capped by the multipart ceiling and re-checked exactly by MediaService.
 */
@Component
public class UploadSizeGuardFilter extends OncePerRequestFilter {

    /** Multipart framing/boundaries add a little on top of the file bytes. */
    static final long OVERHEAD_BYTES = 1024L * 1024L;
    static final long PROFILE_PICTURE_MAX_BYTES = 2L * 1024L * 1024L;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || limitFor(request) < 0;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long declared = request.getContentLengthLong();
        long limit = limitFor(request);
        if (declared > limit + OVERHEAD_BYTES) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":413,\"error\":\"File exceeds the " + (limit / (1024 * 1024))
                    + " MB upload limit for your plan\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Allowed file bytes for this request, or -1 if the path is not an upload endpoint. */
    private static long limitFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.endsWith("/api/v1/media/profile-picture")) return PROFILE_PICTURE_MAX_BYTES;
        if (path.endsWith("/api/v1/media/upload")) {
            String tier = MediaTierLimits.normalizeTier(request.getHeader("X-Subscription-Tier"));
            return MediaTierLimits.maxFileSizeKb(tier) * 1024L;
        }
        return -1;
    }
}
