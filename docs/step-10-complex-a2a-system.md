# Step 10: 복합 A2A 시스템과 자연어 검색

## 학습 목표

## 데모 사이트

실제 동작하는 데모 사이트에서 체험해보세요!

**[https://fly.java21.net](https://fly.java21.net)**

---
이 단계에서는 **LLM과 A2A를 통합**한 완전한 자연어 검색 시스템을 구현합니다.

**학습 완료 후 할 수 있는 것:**
- LLM을 통한 파라미터 추출
- A2A 오케스트레이션과 LLM 통합
- 자연어 검색 시스템 구현
- 복잡한 필터링 처리

---

## 시스템 아키텍처

### LLM + A2A 통합 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│  사용자                                                     │
│  "내일 오후 2시 이후로 광주에서 제주로 가는 항공편 알려줘"    │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  LLM (Gemini/Ollama)                                        │
│  - 자연어 파싱                                             │
│  - 파라미터 추출                                           │
│  - {departure:"광주", arrival:"제주",                       │
│    date:"내일", afterTime:"14:00"}                          │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  MultiAgentOrchestrator (Orchestrator)                      │
│  - LLM 결과 수신                                            │
│  - 에이전트 조율                                           │
└─────────────────────────────────────────────────────────────┘
         ↓           ↓           ↓           ↓
    ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐
    │ Date   │  │Airport │  │Flight  │  │ Time   │
    │ Parser │  │ Code   │  │ Search│  │ Filter │
    └────────┘  └────────┘  └────────┘  └────────┘
         ↓           ↓           ↓           ↓
    ┌──────────────────────────────────────────────────────┐
    │  GroupingAgent (항공사별 그룹핑)                      │
    └──────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  결과 통합                                                  │
│  {아시아나항공: [...], 제주항공: [...]}                    │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  LLM (자연어 응답 생성)                                    │
│  "네, 오후 2시 이후 광주-제주 항공편은..."                 │
└─────────────────────────────────────────────────────────────┘
```

---

## Step 10-1: LLM 파라미터 추출 서비스

### LlmAnalysisService.java

```java
package com.nhnacademy.flyschedule.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * LLM을 통한 자연어 파라미터 추출 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmAnalysisService {

    private final ChatClient.Builder geminiChatClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 자연어 메시지에서 항공편 검색 파라미터를 추출합니다.
     *
     * @param message 자연어 메시지
     * @return 추출된 파라미터 Map
     */
    public Map<String, Object> extractFlightSearchParams(String message) {
        log.info("LLM 파라미터 추출 시작: {}", message);

        ChatClient chatClient = geminiChatClientBuilder.build();

        String systemPrompt = """
            너는 항공편 검색 파라미터 추출 전문가야.

            사용자 메시지에서 다음 파라미터를 추출해서 JSON 형식으로 반환해줘:
            - departure: 출발 공항 이름 (예: "광주", "김포", "제주")
            - arrival: 도착 공항 이름 (예: "제주", "김포", "부산")
            - date: 날짜 (예: "내일", "모레", "2026-03-10")
            - afterTime: "이후" 시간 조건 (예: "14:00", "오후 2시")
            - beforeTime: "이전" 시간 조건
            - minPrice: 최소 가격 (숫자만)
            - maxPrice: 최대 가격 (숫자만)

            파라미터가 없으면 null로 설정해줘.
            반드시 유효한 JSON만 반환해줘.
            """;

        String userPrompt = String.format("""
            다음 메시지에서 파라미터를 추출해서 JSON으로 반환해줘:
            "%s"
            """, message);

        try {
            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            log.info("LLM 응답: {}", response);

            // JSON 파싱
            Map<String, Object> params = objectMapper.readValue(
                    response,
                    new TypeReference<Map<String, Object>>() {}
            );

            log.info("추출된 파라미터: {}", params);
            return params;

        } catch (Exception e) {
            log.error("LLM 파라미터 추출 실패", e);
            return Map.of();  // 빈 맵 반환
        }
    }
}
```

---

## Step 10-2: 자연어 오케스트레이션 서비스

### NaturalLanguageOrchestrationService.java

```java
package com.nhnacademy.flyschedule.service.orchestration;

import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import com.nhnacademy.flyschedule.service.agent.*;
import com.nhnacademy.flyschedule.service.ai.LlmAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 자연어 항공편 검색 오케스트레이션 서비스
 *
 * LLM 파라미터 추출 + A2A 에이전트 조율
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NaturalLanguageOrchestrationService {

    private final LlmAnalysisService llmAnalysisService;
    private final DateParserAgent dateParserAgent;
    private final AirportCodeAgent airportCodeAgent;
    private final FlightSearchAgent flightSearchAgent;
    private final TimeFilterAgent timeFilterAgent;
    private final PriceFilterAgent priceFilterAgent;
    private final GroupingAgent groupingAgent;

    /**
     * 자연어 메시지를 분석하여 항공편을 검색하고 결과를 반환합니다.
     */
    public OrchestrationResult orchestrateFlightSearch(String message) {
        log.info("========================================");
        log.info("자연어 항공편 검색 오케스트레이션 시작");
        log.info("메시지: {}", message);
        log.info("========================================");

        // 1단계: LLM 파라미터 추출
        log.info("단계 1: LLM 파라미터 추출");
        Map<String, Object> params = llmAnalysisService.extractFlightSearchParams(message);

        // 2단계: 파라미터 검증
        log.info("단계 2: 파라미터 검증");
        if (!params.containsKey("departure") || !params.containsKey("arrival")) {
            return OrchestrationResult.error("출발 공항과 도착 공항을 명확히 입력해주세요.");
        }

        // 3단계: 날짜 처리
        log.info("단계 3: 날짜 처리");
        String dateStr = (String) params.getOrDefault("date", "내일");
        String parsedDate = dateParserAgent.parseDate(dateStr);
        log.info("날짜: {} → {}", dateStr, parsedDate);

        // 4단계: 공항 코드 변환
        log.info("단계 4: 공항 코드 변환");
        String departure = (String) params.get("departure");
        String arrival = (String) params.get("arrival");
        String depCode = airportCodeAgent.getAirportCode(departure);
        String arrCode = airportCodeAgent.getAirportCode(arrival);
        log.info("공항: {} → {}, {} → {}", departure, depCode, arrival, arrCode);

        // 5단계: 항공편 검색
        log.info("단계 5: 항공편 검색");
        Map<String, List<FlightInfoResponse>> flightsMap =
                flightSearchAgent.searchAndGroupByAirline(depCode, arrCode, parsedDate);
        List<FlightInfoResponse> flights = flightsMap.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
        log.info("검색된 항공편: {}편", flights.size());

        // 6단계: 시간 필터링 (이후)
        if (params.containsKey("afterTime")) {
            log.info("단계 6a: 시간 필터링 (이후)");
            String afterTime = (String) params.get("afterTime");
            LocalTime time = LocalTime.parse(afterTime);
            flights = timeFilterAgent.filterAfterTime(flights, time);
            log.info("{} 이후 필터링: {}편", afterTime, flights.size());
        }

        // 7단계: 시간 필터링 (이전)
        if (params.containsKey("beforeTime")) {
            log.info("단계 6b: 시간 필터링 (이전)");
            String beforeTime = (String) params.get("beforeTime");
            LocalTime time = LocalTime.parse(beforeTime);
            flights = timeFilterAgent.filterBeforeTime(flights, time);
            log.info("{} 이전 필터링: {}편", beforeTime, flights.size());
        }

        // 8단계: 가격 필터링
        if (params.containsKey("minPrice") || params.containsKey("maxPrice")) {
            log.info("단계 7: 가격 필터링");
            Integer minPrice = params.containsKey("minPrice") ?
                    Integer.parseInt((String) params.get("minPrice")) : null;
            Integer maxPrice = params.containsKey("maxPrice") ?
                    Integer.parseInt((String) params.get("maxPrice")) : null;
            flights = priceFilterAgent.filterByPriceRange(flights, minPrice, maxPrice);
            log.info("가격 필터링: {}편", flights.size());
        }

        // 9단계: 항공사별 그룹핑
        log.info("단계 8: 항공사별 그룹핑");
        Map<String, List<FlightInfoResponse>> groupedFlights = groupingAgent.groupByAirline(flights);

        // 10단계: 결과 변환
        log.info("단계 9: 결과 변환");
        Map<String, List<Map<String, Object>>> resultData = convertToResultMap(groupedFlights);

        log.info("========================================");
        log.info("오케스트레이션 완료");
        log.info("========================================");

        return OrchestrationResult.success(params, resultData);
    }

    /**
     * FlightInfoResponse를 Map으로 변환
     */
    private Map<String, List<Map<String, Object>>> convertToResultMap(
            Map<String, List<FlightInfoResponse>> groupedFlights) {

        return groupedFlights.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .map(this::convertFlightToMap)
                                .collect(Collectors.toList())
                ));
    }

    private Map<String, Object> convertFlightToMap(FlightInfoResponse flight) {
        return Map.of(
                "vihicleId", flight.getFlightId(),
                "airlineNm", flight.getAirlineName(),
                "depTime", flight.getDepartureTime(),
                "arrTime", flight.getArrivalTime(),
                "economyCharge", flight.getEconomyCharge()
        );
    }

    /**
     * 오케스트레이션 결과
     */
    public static class OrchestrationResult {
        private final boolean success;
        private final String message;
        private final Map<String, Object> extractedParams;
        private final Map<String, List<Map<String, Object>>> data;

        private OrchestrationResult(boolean success, String message,
                                  Map<String, Object> extractedParams,
                                  Map<String, List<Map<String, Object>>> data) {
            this.success = success;
            this.message = message;
            this.extractedParams = extractedParams;
            this.data = data;
        }

        public static OrchestrationResult success(Map<String, Object> params,
                                                   Map<String, List<Map<String, Object>>> data) {
            return new OrchestrationResult(true, "검색 완료", params, data);
        }

        public static OrchestrationResult error(String message) {
            return new OrchestrationResult(false, message, null, Map.of());
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public Map<String, Object> getExtractedParams() {
            return extractedParams;
        }

        public Map<String, List<Map<String, Object>>> getData() {
            return data;
        }

        public Map<String, Object> toResponse() {
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("success", success);
            response.put("message", message);
            if (extractedParams != null) {
                response.put("extractedParams", extractedParams);
            }
            if (data != null && !data.isEmpty()) {
                response.put("data", data);
            }
            return response;
        }
    }
}
```

---

## Step 10-3: 자연어 검색 컨트롤러

### NaturalLanguageSearchController.java

```java
package com.nhnacademy.flyschedule.controller;

import com.nhnacademy.flyschedule.service.orchestration.NaturalLanguageOrchestrationService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/nl-search")
public class NaturalLanguageSearchController {

    private final NaturalLanguageOrchestrationService orchestrationService;

    public NaturalLanguageSearchController(NaturalLanguageOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    /**
     * 자연어 항공편 검색
     * POST /api/nl-search/search
     *
     * Body: {"message": "내일 오후 2시 이후로 광주에서 제주로 가는 항공편 알려줘"}
     */
    @PostMapping("/search")
    public Map<String, Object> search(@RequestBody Map<String, String> request) {
        String message = request.get("message");

        if (message == null || message.isBlank()) {
            return Map.of(
                    "success", false,
                    "message", "메시지를 입력해주세요."
            );
        }

        NaturalLanguageOrchestrationService.OrchestrationResult result =
                orchestrationService.orchestrateFlightSearch(message);

        return result.toResponse();
    }
}
```

---

## Step 10-4: 테스트

### 테스트 시나리오

```bash
# 시나리오 1: 기본 검색
curl -X POST http://localhost:8080/api/nl-search/search \
  -H "Content-Type: application/json" \
  -d '{"message": "내일 광주에서 제주로 가는 항공편 알려줘"}'

# 시나리오 2: 시간 필터
curl -X POST http://localhost:8080/api/nl-search/search \
  -H "Content-Type: application/json" \
  -d '{"message": "오후 2시 이후로 광주에서 제주로 가는 항공편 검색해줘"}'

# 시나리오 3: 가격 필터
curl -X POST http://localhost:8080/api/nl-search/search \
  -H "Content-Type: application/json" \
  -d '{"message": "5만원에서 7만원 사이의 광주-제주 항공편 알려줘"}'

# 시나리오 4: 복합 조건
curl -X POST http://localhost:8080/api/nl-search/search \
  -H "Content-Type: application/json" \
  -d '{"message": "내일 오후 2시 이후 6만원 이하인 광주-제주 항공편 추천해줘"}'
```

---

## Step 10-5: LLM과 A2A의 역할 분담

### LLM의 역할

| 역할 | 설명 | 예시 |
|------|------|------|
| **파라미터 추출**| 자연어에서 정보 추출 | "광주" → departure: "광주" |
| **의도 파악**| 사용자의 의도 이해 | "알려줘" → 검색 요청 |
| **응답 생성**| 결과를 자연어로 변환 | JSON → "네, 항공편은..." |

### A2A의 역할

| 역할 | 설명 | 예시 |
|------|------|------|
| **데이터 변환**| 파라미터 포맷 변환 | "내일" → "20260310" |
| **API 호출**| 외부 시스템 연동 | 공공데이터 API 호출 |
| **필터링**| 조건에 맞는 데이터 추출 | 시간/가격 필터링 |
| **그룹핑**| 데이터 정리 | 항공사별 그룹핑 |

---

## 체크리스트

- [ ] `LlmAnalysisService`가 구현됨
- [ ] `NaturalLanguageOrchestrationService`가 구현됨
- [ ] LLM 파라미터 추출이 동작함
- [ ] A2A 에이전트 조율이 동작함
- [ ] 자연어 검색이 완전히 동작함
- [ ] 복합 조건 검색이 동작함

---

## 다음 단계

**Step 11: 테스트와 배포**에서 Spring AI 애플리케이션을 테스트하고 배포하는 방법을 학습합니다.

---

## 참고 자료

- [Spring AI Testing Guide](https://docs.spring.io/spring-ai/reference/testing.html)
- [LLM Integration Patterns](https://martinfowler.com/articles/llm-Integration-patterns.html)
- [Prompt Engineering Guide](https://www.promptingguide.ai/)
