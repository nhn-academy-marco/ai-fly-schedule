package com.nhnacademy.flyschedule.service.ai;

import com.nhnacademy.flyschedule.dto.response.A2ASearchResponse;
import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import com.nhnacademy.flyschedule.mcp.ToolResultCapture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A2A 방식 자연어 검색 서비스
 *
 * Spring AI Function Calling을 사용하여 LLM이 직접 MCP Tools를 선택하고 호출합니다.
 * Tool에서 캡처한 결과를 A2ASearchResponse DTO로 반환합니다.
 */
@Slf4j
@Service
public class A2ANaturalLanguageService {

    private final ChatClient ollamaClient;
    private final ChatClient geminiClient;

    public A2ANaturalLanguageService(
            @Qualifier("ollamaChatClientBuilder") ChatClient.Builder ollamaChatClientBuilder,
            @Qualifier("geminiChatClientBuilder") ChatClient.Builder geminiChatClientBuilder) {
        this.ollamaClient = ollamaChatClientBuilder.build();
        this.geminiClient = geminiChatClientBuilder.build();
    }

    public A2ASearchResponse searchAndParse(String message) {
        return searchAndParse(message, "ollama");
    }

    /**
     * A2A 방식: LLM이 Function Calling으로 자율적으로 MCP Tools를 선택·호출
     *
     * <p>동작 흐름:
     * <ol>
     *   <li>LLM이 사용자 요청을 분석</li>
     *   <li>LLM이 자율적으로 적절한 Tool 호출</li>
     *   <li>Tool 결과는 ToolResultCapture에 저장</li>
     *   <li>캡처된 데이터를 A2ASearchResponse DTO로 반환</li>
     * </ol>
     */
    public A2ASearchResponse searchAndParse(String message, String model) {
        log.info("A2A 자연어 검색 요청 (Function Calling, model={}): {}", model, message);

        try {
            boolean isGemini = "gemini".equalsIgnoreCase(model);
            log.info("선택된 ChatModel: {}", isGemini ? "Gemini" : "Ollama");

            ChatClient chatClient = isGemini ? geminiClient : ollamaClient;
            String systemPrompt = buildSystemPrompt();

            // 1. LLM 호출 (Function Calling으로 Tool 자율 호출)
            String content = chatClient.prompt()
                    .system(systemPrompt)
                    .user(message)
                    .call()
                    .content();

            log.info("A2A LLM 응답: {} 글자", StringUtils.hasText(content) ? content.length() : 0);

            // 2. Tool 캡처 결과 조회
            Map<String, Object> capturedData = ToolResultCapture.getAndClear();
            Map<String, List<FlightInfoResponse>> flightData = unwrapToolResult(capturedData);
            log.info("Tool 결과 캡처: {}개 도구, {}개 항공사 데이터", capturedData.size(), flightData.size());

            // 3. Tool이 호출되지 않은 경우
            if (capturedData.isEmpty()) {
                return A2ASearchResponse.fail("Tool 호출 결과가 없습니다.");
            }

            // 4. Tool은 호출됐으나 검색 결과가 없는 경우
            if (flightData.isEmpty()) {
                return A2ASearchResponse.fail("검색 결과가 없습니다.");
            }

            // 5. 성공: LLM 텍스트가 없으면 기본 메시지 사용
            String resultMessage = StringUtils.hasText(content) ? content : "검색 완료";
            return A2ASearchResponse.success(resultMessage, flightData);

        } catch (Exception e) {
            log.error("A2A 검색 실패 (model={})", model, e);
            ToolResultCapture.getAndClear(); // ThreadLocal 누수 방지
            return A2ASearchResponse.fail("검색 중 오류가 발생했습니다.");
        }
    }

    /**
     * 시스템 프롬프트 생성
     */
    private String buildSystemPrompt() {
        return """
            항공편 검색 도우미입니다. 반드시 한국어로만 답변하세요.
            사용자 요청에 맞는 도구를 호출하세요.
            오늘: %s
            규칙:
            - 날짜: "2026-06-25" 형식
            - 시간: 24시간 형식 ("10:00", "14:00")
            - 공항 이름은 그대로 전달 (예: "광주", "제주")
            """.formatted(LocalDate.now());
    }

    /**
     * Tool 캡처 결과에서 Tool 이름 래퍼를 제거하여 항공사→항공편 맵 반환
     */
    @SuppressWarnings("unchecked")
    private Map<String, List<FlightInfoResponse>> unwrapToolResult(Map<String, Object> capturedData) {
        if (capturedData.isEmpty()) {
            return Map.of();
        }
        Object lastResult = capturedData.values().stream().reduce((first, second) -> second).orElse(Map.of());
        if (lastResult instanceof Map) {
            return new HashMap<>((Map<String, List<FlightInfoResponse>>) lastResult);
        }
        return Map.of();
    }
}
