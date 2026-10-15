package net.java21.flyschedule.config;

import net.java21.flyschedule.mcp.ToolResultCapture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ToolResultCaptureCleanupFilter 테스트")
class ToolResultCaptureCleanupFilterTest {

    @Test
    @DisplayName("요청 처리 중 쌓인 Tool 기록을 요청이 끝나면 비운다 (Tool을 직접 호출하는 McpController 경로)")
    void clearsAfterRequest() throws Exception {
        ToolResultCaptureCleanupFilter filter = new ToolResultCaptureCleanupFilter();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/mcp/airports"), new MockHttpServletResponse(),
                (request, response) -> ToolResultCapture.capture("getAirportList", "전체", List::of));

        assertThat(ToolResultCapture.getAndClear()).isEmpty();
    }
}
