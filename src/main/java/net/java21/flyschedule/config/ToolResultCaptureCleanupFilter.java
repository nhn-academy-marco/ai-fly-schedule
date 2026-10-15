package net.java21.flyschedule.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.java21.flyschedule.mcp.ToolResultCapture;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 요청이 끝날 때마다 ToolResultCapture의 ThreadLocal 기록을 비웁니다.
 *
 * <p>McpController처럼 LLM 없이 Tool을 직접 호출하는 경로에서는 기록을 꺼내 가는 곳이 없습니다.
 * Tomcat은 스레드를 재사용하므로, 비우지 않으면 기록이 계속 쌓이고 다음 요청에 섞일 수 있습니다.
 */
@Component
public class ToolResultCaptureCleanupFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            ToolResultCapture.getAndClear();
        }
    }
}
