package com.terraceweather.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Injects the Umami analytics script (self-hosted, cookie-less visit counter) into
 * Swagger UI's page. Both properties empty (the default): nothing is injected.
 */
@Component
public class UmamiFilter extends OncePerRequestFilter {

    private final String src;
    private final String websiteId;

    public UmamiFilter(@Value("${umami.src:}") String src, @Value("${umami.id:}") String websiteId) {
        this.src = src;
        this.websiteId = websiteId;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return src.isBlank() || websiteId.isBlank() || !request.getRequestURI().contains("swagger-ui");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        chain.doFilter(request, wrapper);

        byte[] body = wrapper.getContentAsByteArray();
        String contentType = wrapper.getContentType();
        if (contentType == null || !contentType.contains("html") || body.length == 0) {
            wrapper.copyBodyToResponse();
            return;
        }

        String html = new String(body, StandardCharsets.UTF_8);
        String script = "<script defer src=\"" + src + "\" data-website-id=\"" + websiteId + "\"></script></head>";
        String patched = html.replace("</head>", script);
        byte[] out = patched.getBytes(StandardCharsets.UTF_8);
        response.setContentLength(out.length);
        response.getOutputStream().write(out);
    }
}
