package com.nhnacademy.flyschedule.mcp;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP Tool 호출 결과를 캡처하는 유틸리티
 *
 * A2A 방식에서 LLM이 Tool을 호출하면, Tool의 반환값을 ThreadLocal에 저장합니다.
 * A2ANaturalLanguageService에서 Tool 호출 후 캡처된 결과를 읽어 응답의 data 필드에 사용합니다.
 */
public final class ToolResultCapture {

    private static final ThreadLocal<Map<String, Object>> RESULTS =
            ThreadLocal.withInitial(HashMap::new);

    private ToolResultCapture() {}

    public static void capture(String toolName, Object result) {
        RESULTS.get().put(toolName, result);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getAndClear() {
        Map<String, Object> results = RESULTS.get();
        RESULTS.remove();
        return results;
    }
}
