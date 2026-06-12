package com.nhnacademy.flyschedule.controller;

import com.nhnacademy.flyschedule.dto.response.A2ASearchResponse;
import com.nhnacademy.flyschedule.service.ai.A2ANaturalLanguageService;
import com.nhnacademy.flyschedule.service.orchestration.NaturalLanguageOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 자연어 항공편 검색 컨트롤러
 *
 * 두 가지 방식을 지원합니다:
 * 1. Coordinator 방식 (default) - NaturalLanguageOrchestrationService
 * 2. A2A 방식 (optional) - A2ANaturalLanguageService
 */
@Slf4j
@RestController
@RequestMapping("/api/nl-search")
@RequiredArgsConstructor
public class NaturalLanguageSearchController {

    private final NaturalLanguageOrchestrationService orchestrationService;
    private final A2ANaturalLanguageService a2aService;

    /**
     * 자연어 항공편 검색 (Coordinator 방식)
     *
     * @param request 검색 요청
     * @return 검색 결과
     */
    @PostMapping("/search")
    public Map<String, Object> searchByNaturalLanguage(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        if (message == null || message.isBlank()) {
            return error("메시지를 입력해주세요.");
        }

        log.info("자연어 검색 요청 (Coordinator 방식): {}", message);

        try {
            // Coordinator 방식: 오케스트레이션 서비스 사용
            NaturalLanguageOrchestrationService.OrchestrationResult result =
                orchestrationService.orchestrateFlightSearch(message);

            return result.toResponse();

        } catch (Exception e) {
            log.error("자연어 검색 실패", e);
            return error("검색 실패: " + e.getMessage());
        }
    }

    /**
     * 자연어 항공편 검색 (A2A 방식)
     *
     * LLM이 직접 MCP Tools를 선택하고 호출합니다.
     *
     * @param request 검색 요청 (message, model - optional, "ollama" or "gemini")
     * @return 검색 결과 DTO
     */
    @PostMapping("/search/a2a")
    public A2ASearchResponse searchByA2A(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        if (message == null || message.isBlank()) {
            return A2ASearchResponse.fail("메시지를 입력해주세요.");
        }

        String model = request.getOrDefault("model", "ollama");

        log.info("자연어 검색 요청 (A2A 방식, model={}): {}", model, message);

        return a2aService.searchAndParse(message, model);
    }

    /**
     * 에러 응답 생성
     */
    private Map<String, Object> error(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", message);
        return error;
    }
}
