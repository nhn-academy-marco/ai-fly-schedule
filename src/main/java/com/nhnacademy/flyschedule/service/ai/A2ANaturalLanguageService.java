package com.nhnacademy.flyschedule.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.flyschedule.service.orchestration.NaturalLanguageOrchestrationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.HashMap;

/**
 * A2A 방식 자연어 검색 서비스
 *
 * Spring AI Function Calling을 사용하여 LLM이 직접 MCP Tools를 선택하고 호출합니다.
 * Ollama와 Gemini를 선택적으로 사용할 수 있습니다.
 */
@Slf4j
@Service
public class A2ANaturalLanguageService {

    private final ChatClient.Builder ollamaChatClientBuilder;
    private final ChatClient.Builder geminiChatClientBuilder;
    private final ChatModel ollamaChatModel;
    private final ChatModel geminiChatModel;
    private final NaturalLanguageOrchestrationService orchestrationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public A2ANaturalLanguageService(
            @Qualifier("ollamaChatClientBuilder") ChatClient.Builder ollamaChatClientBuilder,
            @Qualifier("geminiChatClientBuilder") ChatClient.Builder geminiChatClientBuilder,
            @Qualifier("ollamaChatModel") ChatModel ollamaChatModel,
            @Qualifier("googleGenAiChatModel") ChatModel geminiChatModel,
            NaturalLanguageOrchestrationService orchestrationService) {
        this.ollamaChatClientBuilder = ollamaChatClientBuilder;
        this.geminiChatClientBuilder = geminiChatClientBuilder;
        this.ollamaChatModel = ollamaChatModel;
        this.geminiChatModel = geminiChatModel;
        this.orchestrationService = orchestrationService;
    }

    /**
     * 자연어 메시지를 분석하여 LLM이 직접 Tools를 선택하고 호출
     *
     * @param message 사용어 자연어 메시지
     * @return 구조화된 응답 Map
     */
    public Map<String, Object> searchAndParse(String message) {
        // 기본: Ollama 사용
        return searchAndParse(message, "ollama");
    }

    /**
     * 자연어 메시지를 분석하여 LLM이 직접 Tools를 선택하고 호출
     *
     * @param message 사용어 자연어 메시지
     * @param model 사용할 모델 ("ollama" 또는 "gemini")
     * @return 구조화된 응답 Map
     */
    public Map<String, Object> searchAndParse(String message, String model) {
        log.info("A2A 자연어 검색 요청 (Function Calling, model={}): {}", model, message);

        try {
            // 모델 선택
            ChatClient.Builder selectedBuilder = "gemini".equalsIgnoreCase(model)
                    ? geminiChatClientBuilder
                    : ollamaChatClientBuilder;

            // 디버깅: 선택된 ChatModel 클래스 정보
            ChatModel selectedModel = "gemini".equalsIgnoreCase(model)
                    ? geminiChatModel
                    : ollamaChatModel;
            log.info("선택된 ChatModel: {}", selectedModel.getClass().getSimpleName());

            ChatClient chatClient = selectedBuilder.build();

            // 시스템 프롬프트: 매우 간단하게 Tool 호출만 지시
            String systemPrompt = """
                당신은 항공편 검색 도우미입니다.

                사용자 요청에 따라 적절한 검색 도구를 호출하세요.

                도구 호출 후에는 "검색 완료"라고만 답변하세요. 추가 설명은 불필요합니다.
                """;

            // ChatClient에 MCP Tools가 자동으로 등록됨
            String response = null;
            try {
                response = chatClient.prompt()
                        .system(systemPrompt)
                        .user(message)
                        .call()
                        .content();
                log.info("A2A LLM 응답 수신 (model={}): {} 글자", model, response.length());
            } catch (Exception e) {
                log.warn("LLM 응답 수신 실패 (Tool 호출은 성공할 수 있음): {}", e.getMessage());
                // Tool 호출은 성공했지만 응답 생성 실패 - 계속 진행
            }

            // LLM이 Tool을 호출했는지 확인 (응답이 있는지)
            boolean llmCalledTool = response != null && !response.trim().isEmpty();
            log.info("LLM Tool 호출 여부: {}", llmCalledTool);

            // 항상 OrchestrationService로 실제 데이터 조회
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);

            try {
                NaturalLanguageOrchestrationService.OrchestrationResult orchResult =
                        orchestrationService.orchestrateFlightSearch(message);

                if (orchResult.isSuccess() && orchResult.getData() != null) {
                    // 실제 데이터 설정
                    result.put("data", orchResult.getData());

                    // LLM 응답이 있으면 message로 사용, 없으면 기본 메시지
                    if (llmCalledTool) {
                        result.put("message", response);
                    } else {
                        result.put("message", "검색 완료");
                    }

                    // extractedParams 설정
                    if (orchResult.getExtractedParams() != null) {
                        result.put("extractedParams", orchResult.getExtractedParams());
                    }

                    log.info("OrchestrationService 데이터 조회 성공: {}개 항공사",
                            orchResult.getData().size());
                } else {
                    log.warn("OrchestrationService 데이터 조회 실패");
                    result.put("success", false);
                    result.put("message", orchResult.getMessage());
                    result.put("data", new HashMap<>());
                }
            } catch (Exception e) {
                log.error("OrchestrationService 호출 실패", e);
                result.put("success", false);
                result.put("message", "검색 실패: " + e.getMessage());
                result.put("data", new HashMap<>());
            }

            return result;

        } catch (Exception e) {
            log.error("A2A 검색 실패 (model={})", model, e);
            return Map.of(
                "success", false,
                "message", "검색 실패 (" + model + "): " + e.getMessage(),
                "data", Map.of()
            );
        }
    }

    /**
     * LLM 응답을 파싱하여 Map으로 변환
     */
    private Map<String, Object> parseResponse(String response) {
        try {
            String jsonContent = extractJson(response);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(jsonContent, Map.class);
            // 수정 가능한 Map으로 변환
            return new HashMap<>(result);
        } catch (Exception e) {
            log.error("JSON 파싱 실패: {}", response, e);
            // 파싱 실패 시 수정 가능한 Map 반환
            Map<String, Object> fallbackResult = new HashMap<>();
            fallbackResult.put("success", true);
            fallbackResult.put("message", response);
            fallbackResult.put("data", new HashMap<>());
            return fallbackResult;
        }
    }

    /**
     * LLM 응답에서 JSON 블록 추출
     */
    private String extractJson(String response) {
        String jsonContent = response;
        if (response.contains("```json")) {
            jsonContent = response.substring(response.indexOf("```json") + 7);
            int endIndex = jsonContent.indexOf("```");
            if (endIndex > 0) {
                jsonContent = jsonContent.substring(0, endIndex);
            }
        } else if (response.contains("```")) {
            jsonContent = response.substring(response.indexOf("```") + 3);
            int endIndex = jsonContent.indexOf("```");
            if (endIndex > 0) {
                jsonContent = jsonContent.substring(0, endIndex);
            }
        }
        return jsonContent.trim();
    }
}
