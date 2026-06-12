# Step 10: 복합 A2A 시스템과 자연어 검색

## 학습 목표

이 단계를 마치면 다음을 할 수 있습니다:

1. **Coordinator 방식**과 **A2A(Function Calling) 방식**의 차이를 설명할 수 있다
2. **LlmAnalysisService**가 자연어에서 검색 파라미터를 추출하는 원리를 이해한다
3. **ToolResultCapture**로 LLM이 자율적으로 호출한 Tool의 결과를 수집할 수 있다
4. 두 방식을 비교하고 **상황에 맞는 방식을 선택**할 수 있다

---

> 💡 **핵심 개념**: 이 단계에서는 **두 가지 방식**을 비교합니다.
>
> **Coordinator 방식**: 개발자가 코드로 "① LLM 호출 → ② 날짜 파싱 → ③ 공항 코드 변환 → ..."
> 순서를 **직접 지정**하는 방식입니다. (Step 9에서 배운 방식)
>
> **A2A(Function Calling) 방식**: LLM이 사용자의 질문을 보고
> "지금은 날짜 Tool을 호출해야겠다", "이제 공항 코드 Tool을 호출해야겠다"라고
> **스스로 판단**하는 방식입니다.
>
> 일상생활에 비유하면:
> - **Coordinator** = 식당에서 주문서를 정해진 순서대로 처리하는 주방장
> - **A2A** = 경험이 많은 요리사가 상황에 맞춰 자율적으로 요리하는 것

---

## 데모 사이트

실제 동작하는 데모 사이트에서 체험해보세요!

**[https://fly.java21.net](https://fly.java21.net)**

---
이 단계에서는 **LLM과 A2A를 통합**한 완전한 자연어 검색 시스템을 구현합니다.

---

## Coordinator vs A2A: 두 방식 비교

> ### 왜 두 가지 방식이 필요할까요?
> **Coordinator 방식**은 흐름이 명확해서 디버깅이 쉽지만, 새로운 조건이 추가될 때마다
> 코드를 수정해야 합니다.
> **A2A 방식**은 LLM이 상황에 맞게 자율적으로 대응하므로 유연하지만,
> LLM이 잘못된 Tool을 호출할 가능성도 있습니다.

```
Coordinator 방식 (오케스트레이터):
[코드] → [Orchestrator] → Agent1 → Agent2 → Agent3 → [결과]
         ↑ 순서를 코드로 직접 지정

A2A 방식 (에이전트 자율):
[코드] → [LLM이 판단] → Tool1 → [LLM이 판단] → Tool2 → [결과]
         ↑ AI 모델이 스스로 어떤 도구를 호출할지 결정
```

---

## 시스템 아키텍처

### 전체 시스템 아키텍처 (두 가지 방식)

이 시스템은 **Coordinator 방식**과 **A2A 방식** 두 가지를 지원합니다.

```
┌─────────────────────────────────────────────────────────────┐
│  사용자                                                     │
│  "내일 오후 2시 이후로 광주에서 제주로 가는 항공편 알려줘"    │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  NaturalLanguageSearchController                            │
│  POST /api/nl-search/search  → Coordinator 방식             │
│  POST /api/nl-search/search/a2a → A2A 방식                 │
└─────────────────────────────────────────────────────────────┘
           ↓                              ↓
┌──────────────────────┐   ┌──────────────────────────────────┐
│  Coordinator 방식     │   │  A2A 방식                        │
│  (OrchestrationService)│   │  (A2ANaturalLanguageService)     │
│                      │   │                                  │
│  1. LLM 파라미터 추출│   │  LLM Function Calling으로         │
│  2. 순차적 에이전트  │   │  MCP Tools를 자율적으로 선택·호출 │
│     호출 및 조율     │   │                                  │
└──────────────────────┘   └──────────────────────────────────┘
```

### Coordinator 방식 상세 흐름

```
┌─────────────────────────────────────────────────────────────┐
│  LLM (Ollama)                                               │
│  - 자연어 파싱                                             │
│  - 파라미터 추출                                           │
│  - {departure:"광주", arrival:"제주",                       │
│    date:"내일", afterTime:"14:00"}                          │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  NaturalLanguageOrchestrationService (Orchestrator)          │
│  - LLM 결과 수신                                            │
│  - 10단계로 에이전트 조율                                   │
└─────────────────────────────────────────────────────────────┘
         ↓           ↓           ↓           ↓
    ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐
    │ Date   │  │Airport │  │Flight  │  │ Time   │
    │ Parser │  │ Code   │  │ Search │  │ Filter │
    └────────┘  └────────┘  └────────┘  └────────┘
         ↓                                 ↓
    ┌────────┐  ┌──────────────────────────────────────────┐
    │ Price  │  │  GroupingAgent (항공사별 그룹핑)           │
    │ Filter │  └──────────────────────────────────────────┘
    └────────┘                        ↓
                          ┌─────────────────────────────────┐
                          │  결과 통합 (Map 변환)            │
                          │  {아시아나항공: [...], 제주항공: [...]}│
                          └─────────────────────────────────┘
```

### A2A 방식 상세 흐름

> ### 왜 LLM이 직접 Tool을 호출하게 할까요?
> 사용자가 "광주에서 제주 내일"이라고 하면 날짜 Tool과 공항 Tool이 모두 필요하지만,
> "광주에서 제주 5만원 이하"라고 하면 가격 Tool이 추가로 필요합니다.
> **LLM이 상황에 맞게 필요한 Tool만 선택**하면, 개발자가 모든 조합을 코드로 미리 작성할 필요가 없습니다.

```
┌─────────────────────────────────────────────────────────────┐
│  LLM (Ollama/Gemini)                                        │
│  - Function Calling으로 MCP Tools 직접 호출                 │
│  - LLM이 자율적으로 적절한 Tool 선택                        │
└─────────────────────────────────────────────────────────────┘
         ↓ (Tool 호출)
    ┌──────────────────────────────────────┐
    │  MCP Tools                           │
    │  - ToolResultCapture에 결과 저장     │
    └──────────────────────────────────────┘
         ↓
    ┌──────────────────────────────────────┐
    │  A2ASearchResponse DTO              │
    │  - success, message, data            │
    └──────────────────────────────────────┘
```

---

## 비교해보기: Coordinator vs A2A

> 📖 **Spring Boot 복습**: Spring Boot에서 Controller가 Service를 직접 호출하는 것은
> **Coordinator 방식**과 비슷합니다. 반면, **이벤트 기반 아키텍처** (예: `ApplicationEventPublisher`)는
> 이벤트를 발행하고, 각 리스너가 자율적으로 반응하는 점에서 **A2A 방식**과 비슷합니다.

### 코드 레벨 비교

| 항목 | Coordinator 방식 | A2A 방식 |
|------|-----------------|----------|
| **제어권**| Orchestrator가 흐름 제어 | LLM이 Tool 선택 |
| **LLM 모델**| Ollama | Ollama / Gemini 선택 가능 |
| **예측 가능성**| 높음 (순차적 흐름) | 낮음 (LLM 자율 판단) |
| **유연성**| 낮음 (고정된 단계) | 높음 (LLM이 동적 대응) |
| **엔드포인트**| POST /api/nl-search/search | POST /api/nl-search/search/a2a |
| **응답 형식**| Map<String, Object> | A2ASearchResponse DTO |
| **디버깅**| 쉬움 (단계별 로그 확인) | 어려움 (LLM 판단 과정 추적 필요) |
| **새 조건 추가**| 코드 수정 필요 | 프롬프트 수정으로 대응 가능 |

### 언제 어떤 방식을 쓸까?

| 상황 | 추천 방식 | 이유 |
|------|-----------|------|
| **처음 Spring AI를 배울 때**| Coordinator | 흐름을 코드로 볼 수 있어 이해하기 쉬움 |
| **고정된 작업 흐름**| Coordinator | 순서가 항상 같으면 코드로 지정하는 게 확실 |
| **사용자 요청이 다양할 때**| A2A | LLM이 상황에 맞게 Tool을 선택 |
| **새로운 필터/조건이 자주 추가될 때**| A2A | 프롬프트만 수정하면 새 Tool 활용 |
| **정확한 결과가 중요할 때**| Coordinator | 예측 가능성이 높아 신뢰도가 높음 |

---

## Step 10-1: LLM 파라미터 추출 서비스

### LlmAnalysisService.java

**Ollama ChatClient**를 사용하여 자연어에서 항공편 검색 파라미터를 추출합니다. LLM 호출이 실패하면 정규식 기반 파싱으로 fallback합니다.

> ### 왜 LLM으로 파라미터를 추출할까요?
> 사용자가 "내일 오후 2시 이후로 광주에서 제주로 가는 항공편"이라고 말하면,
> 이 문장에서 **출발지, 도착지, 날짜, 시간**을 분리해서 추출해야 합니다.
> 정규식으로도 가능하지만, 표현이 다양하면 (예: "글피", "다음 주 월요일") 처리하기 어렵습니다.
> LLM은 자연어를 이해하므로 다양한 표현을 처리할 수 있습니다.

```java
package com.nhnacademy.flyschedule.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM을 활용한 자연어 분석 서비스
 *
 * 사용자의 자연어 메시지에서 항공편 검색 파라미터를 추출합니다.
 * Spring AI ChatClient를 사용하여 구조화된 데이터를 추출합니다.
 */
@Slf4j
@Service
public class LlmAnalysisService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatClient chatClient;

    // ① @Qualifier로 특정 ChatClient.Builder를 지정
    //    "ollamaChatClientBuilder"라는 이름의 Bean을 주입받습니다.
    public LlmAnalysisService(
            @Qualifier("ollamaChatClientBuilder") ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 자연어 메시지에서 항공편 검색 파라미터 추출
     *
     * 1. 먼저 정규식 기반 파싱 시도 (빠름)
     * 2. 실패 시 LLM 호출
     * 3. LLM도 실패하면 다시 정규식 fallback
     *
     * @param message 사용자 자연어 메시지
     * @return 추출된 파라미터 Map
     */
    public Map<String, Object> extractFlightSearchParams(String message) {
        log.info("LLM 분석 요청: {}", message);

        // ② 먼저 정규식 기반 파싱 시도 (LLM 호출보다 훨씬 빠름)
        try {
            Map<String, Object> parsed = parseWithRegex(message);
            if (parsed != null && !parsed.isEmpty()) {
                log.info("정규식 파싱 성공: {}", parsed);
                return parsed;
            }
        } catch (Exception e) {
            log.warn("정규식 파싱 실패, LLM 시도: {}", e.getMessage());
        }

        // ③ 정규식으로 추출하지 못한 경우 LLM에게 요청
        String prompt = buildAnalysisPrompt(message);

        try {
            // ④ Spring AI의 ChatClient로 LLM 호출
            //    .prompt() → .user(프롬프트) → .call() → .content()
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (response != null && !response.isBlank()) {
                log.info("LLM 분석 결과: {}", response);
                return parseResponse(response);
            } else {
                throw new RuntimeException("LLM 응답이 비어있습니다.");
            }

        } catch (Exception e) {
            // ⑤ LLM도 실패하면 정규식 fallback (최후의 보루)
            log.error("LLM 호출 실패, 정규식 fallback 사용", e);
            Map<String, Object> fallbackResult = parseWithRegex(message);
            if (fallbackResult != null && !fallbackResult.isEmpty()) {
                return fallbackResult;
            }
            throw new RuntimeException("자연어 분석에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * LLM 분석 프롬프트 구성
     */
    private String buildAnalysisPrompt(String message) {
        // ⑥ LLM에게 "JSON 형식으로 추출하라"고 명확히 지시
        return String.format("""
            당신은 항공편 검색 시스템의 파라미터 추출 전문가입니다.

            사용자의 자연어 메시지를 분석하여 항공편 검색에 필요한 파라미터를 JSON 형식으로 추출하세요.

            ## 추출할 파라미터
            - departure: 출발 공항 **이름** (예: 광주, 김포, 제주, 인천, 부산)
            - arrival: 도착 공항 **이름** (예: 광주, 김포, 제주, 인천, 부산)
            - date: 날짜 (YYYY-MM-DD 형식)
            - afterTime: 기준 시간 (HH:MM 형식) - "이후" 조건만
            - beforeTime: 기준 시간 (HH:MM 형식) - "이전" 조건만
            - minPrice: 최소 가격 (숫자만)
            - maxPrice: 최대 가격 (숫자만)

            ## 출력 형식
            반드시 JSON 형식만 출력:
            {"departure":"광주","arrival":"제주","date":"2026-03-25","afterTime":"10:00"}

            ## 사용자 메시지
            %s
            """, message);
    }

    /**
     * 정규식 기반 파싱 (LLM 실패 시 fallback)
     *
     * 날짜, 공항 이름, 시간, 가격을 정규식으로 추출합니다.
     */
    private Map<String, Object> parseWithRegex(String message) {
        Map<String, Object> params = new HashMap<>();

        // ⑦ 날짜 파싱 (2026년3월25일, 2026-03-25 등)
        Pattern datePattern = Pattern.compile("(\\d{4})[년\\-](\\d{1,2})[월\\-](\\d{1,2})일?");
        Matcher dateMatcher = datePattern.matcher(message);
        if (dateMatcher.find()) {
            String year = dateMatcher.group(1);
            String month = String.format("%02d", Integer.parseInt(dateMatcher.group(2)));
            String day = String.format("%02d", Integer.parseInt(dateMatcher.group(3)));
            params.put("date", year + "-" + month + "-" + day);
        } else if (message.contains("내일")) {
            LocalDate tomorrow = LocalDate.now().plusDays(1);
            params.put("date", tomorrow.format(DateTimeFormatter.ISO_LOCAL_DATE));
        } else if (message.contains("모레")) {
            LocalDate dayAfterTomorrow = LocalDate.now().plusDays(2);
            params.put("date", dayAfterTomorrow.format(DateTimeFormatter.ISO_LOCAL_DATE));
        }

        // ⑧ 공항 이름 파싱
        String[] airports = {"광주", "김포", "제주", "인천", "부산", "대구", "울산", "청주", "양양"};
        for (String airport : airports) {
            if (message.contains(airport)) {
                if (!params.containsKey("departure")) {
                    params.put("departure", airport);
                } else if (!params.containsKey("arrival")) {
                    params.put("arrival", airport);
                    break;
                }
            }
        }

        // 시간 파싱, 가격 파싱 ...

        return params;
    }
}
```

> 📖 **Spring Boot 복습**: `@Qualifier("ollamaChatClientBuilder")`는
> 같은 타입의 Bean이 여러 개일 때, **어떤 Bean을 주입할지** 지정하는 어노테이션입니다.
> 이 프로젝트에는 Ollama용, Gemini용 ChatClient.Builder가 각각 있으므로
> `@Qualifier`로 명시적으로 선택합니다.

---

## Step 10-2: 자연어 오케스트레이션 서비스

### NaturalLanguageOrchestrationService.java

Coordinator 패턴으로 LLM 파라미터 추출 + A2A 에이전트 조율을 수행합니다. 전체 10단계 흐름은 Step 9에서 설명한 것과 동일합니다.

> 자세한 코드는 Step 9의 `NaturalLanguageOrchestrationService`를 참고하세요.

### convertToMap() 필드 매핑

> ### 왜 필드명이 다를까요?
> 공공데이터 포털 API의 응답 필드명이 `depPlandTime`, `vihicleId` 처럼 약간 이상합니다.
> (오타도 있습니다: `vihicleId` → `vehicleId`가 맞지만 원본 그대로 사용합니다.)
> 원본 API의 필드명을 그대로 유지해야 프론트엔드와의 호환성이 깨지지 않습니다.

`FlightInfoResponse`를 API 응답용 Map으로 변환할 때, 공공데이터 포털의 필드명을 그대로 사용합니다:

```java
private Map<String, Object> convertToMap(FlightInfoResponse flight) {
    return Map.of(
        "vihicleId", flight.getFlightId(),         // 항공편명
        "airlineNm", flight.getAirlineName(),      // 항공사명
        "depPlandTime", flight.getDepartureTime(), // 출발시간
        "arrPlandTime", flight.getArrivalTime(),   // 도착시간
        "economyCharge", flight.getEconomyCharge(), // 일반석 운임
        "prestigeCharge", flight.getPrestigeCharge(), // 비즈니스석 운임
        "depAirportNm", flight.getDepartureAirport(), // 출발공항명
        "arrAirportNm", flight.getArrivalAirport()   // 도착공항명
    );
}
```

> **참고:** `depPlandTime`, `arrPlandTime`은 공공데이터 포털 API의 원래 필드명입니다. `depTime`/`arrTime`이 아님에 주의하세요.

---

## Step 10-3: A2A 방식 서비스

### A2ANaturalLanguageService.java

> ### 왜 A2A 방식에서는 Orchestrator가 없을까요?
> Coordinator 방식에서는 `NaturalLanguageOrchestrationService`가 10단계를 직접 제어했습니다.
> A2A 방식에서는 **LLM이 Orchestrator 역할**을 합니다.
> LLM이 "이 Tool을 호출해야겠다"라고 스스로 판단하므로, 개발자가 순서를 지정할 필요가 없습니다.

LLM Function Calling을 사용하여 LLM이 직접 MCP Tools를 선택하고 호출하는 A2A 방식의 서비스입니다.

```java
package com.nhnacademy.flyschedule.service.ai;

import com.nhnacademy.flyschedule.dto.response.A2ASearchResponse;
import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import com.nhnacademy.flyschedule.mcp.ToolResultCapture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

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

    // ① 두 개의 LLM 클라이언트를 주입받습니다 (Ollama, Gemini)
    public A2ANaturalLanguageService(
            @Qualifier("ollamaChatClientBuilder") ChatClient.Builder ollamaChatClientBuilder,
            @Qualifier("geminiChatClientBuilder") ChatClient.Builder geminiChatClientBuilder,
            @Qualifier("ollamaChatModel") ChatModel ollamaChatModel,
            @Qualifier("googleGenAiChatModel") ChatModel geminiChatModel) {
        this.ollamaClient = ollamaChatClientBuilder.build();
        this.geminiClient = geminiChatClientBuilder.build();
    }

    /**
     * A2A 방식: LLM이 Function Calling으로 자율적으로 MCP Tools를 선택·호출
     *
     * 동작 흐름:
     * 1. LLM이 사용자 요청을 분석
     * 2. LLM이 자율적으로 적절한 Tool 호출
     * 3. Tool 결과는 ToolResultCapture에 저장
     * 4. 캡처된 데이터를 A2ASearchResponse DTO로 반환
     */
    public A2ASearchResponse searchAndParse(String message, String model) {
        log.info("A2A 자연어 검색 요청 (Function Calling, model={}): {}", model, message);

        try {
            // ② model 파라미터로 Ollama 또는 Gemini 선택
            boolean isGemini = "gemini".equalsIgnoreCase(model);
            ChatClient chatClient = isGemini ? geminiClient : ollamaClient;
            String systemPrompt = buildSystemPrompt();

            // ③ 핵심: LLM 호출 (Function Calling으로 Tool 자율 호출)
            //    Coordinator 방식과 달리, 어떤 Tool을 호출할지 LLM이 결정합니다.
            String content = chatClient.prompt()
                    .system(systemPrompt)
                    .user(message)
                    .call()
                    .content();

            // ④ Tool이 실행된 결과를 ToolResultCapture에서 수집
            Map<String, Object> capturedData = ToolResultCapture.getAndClear();
            Map<String, List<FlightInfoResponse>> flightData = unwrapToolResult(capturedData);

            // ⑤ Tool이 호출되지 않은 경우
            if (capturedData.isEmpty()) {
                return A2ASearchResponse.fail("Tool 호출 결과가 없습니다.");
            }

            // ⑥ Tool은 호출됐으나 검색 결과가 없는 경우
            if (flightData.isEmpty()) {
                return A2ASearchResponse.fail("검색 결과가 없습니다.");
            }

            // ⑦ 성공: LLM이 생성한 메시지 + Tool 결과를 함께 반환
            String resultMessage = content != null && !content.isBlank() ? content : "검색 완료";
            return A2ASearchResponse.success(resultMessage, flightData);

        } catch (Exception e) {
            log.error("A2A 검색 실패 (model={})", model, e);
            // ⑧ ThreadLocal 누수 방지: 에러가 나도 반드시 clear
            ToolResultCapture.getAndClear();
            return A2ASearchResponse.fail("검색 중 오류가 발생했습니다.");
        }
    }
}
```

> 📖 **Spring Boot 복습**: `@Qualifier`로 같은 타입의 여러 Bean 중 하나를 선택하는 것은
> Spring Boot에서 흔히 하는 일입니다. 예를 들어 여러 `DataSource` Bean이 있을 때
> `@Qualifier("primaryDataSource")`로 선택하는 것과 같은 패턴입니다.

---

## Step 10-4: ToolResultCapture

### MCP Tool 호출 결과 캡처 유틸리티

> ### 왜 결과를 "캡처"해야 할까요?
> A2A 방식에서는 LLM이 Tool을 직접 호출합니다. 그런데 Tool의 반환값은 LLM에게 돌아갑니다.
> 우리는 **최종 결과를 Controller에 반환**해야 합니다.
> 그래서 Tool 실행 시 결과를 `ToolResultCapture`에 **임시 저장**하고,
> `A2ANaturalLanguageService`에서 나중에 꺼내서 사용합니다.

A2A 방식에서 LLM이 MCP Tool을 호출하면, Tool의 반환값을 **ThreadLocal**에 저장합니다. 이후 `A2ANaturalLanguageService`에서 캡처된 결과를 읽어 응답의 data 필드에 사용합니다.

```java
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

    // ① ThreadLocal: 각 스레드마다 독립적인 저장 공간
    //    같은 JVM에서 여러 요청이 동시에 들어와도 데이터가 섞이지 않습니다.
    private static final ThreadLocal<Map<String, Object>> RESULTS = new ThreadLocal<>();

    private ToolResultCapture() {}

    /**
     * Tool 호출 결과를 ThreadLocal에 저장합니다.
     *
     * @param toolName Tool 이름
     * @param result   Tool 실행 결과
     */
    public static void capture(String toolName, Object result) {
        // ② Map이 없으면 새로 생성해서 저장
        if (RESULTS.get() == null) {
            RESULTS.set(new HashMap<>());
        }
        // ③ "flightSearch" → [항공편 목록] 형태로 저장
        RESULTS.get().put(toolName, result);
    }

    /**
     * 캡처된 결과를 조회하고 ThreadLocal을 초기화합니다.
     *
     * @return Tool 이름 → 결과 맵
     */
    public static Map<String, Object> getAndClear() {
        // ④ 저장된 결과를 가져오고
        Map<String, Object> results = RESULTS.get();
        // ⑤ 반드시 remove()로 초기화 (메모리 누수 방지)
        RESULTS.remove();
        return results != null ? results : new HashMap<>();
    }
}
```

> 📖 **Spring Boot 복습**: **ThreadLocal**은 Java의 기본 기능으로, 각 스레드마다 고유한
> 저장 공간을 제공합니다. Spring Security에서 현재 로그인한 사용자 정보를
> `SecurityContextHolder`로 관리하는 것도 같은 원리입니다.
>
> **주의**: ThreadLocal은 사용 후 반드시 `remove()`로 정리해야 합니다.
> 그렇지 않으면 스레드 풀에서 스레드가 재사용될 때 이전 요청의 데이터가 남을 수 있습니다.

### 동작 원리

```
LLM Function Calling
        ↓
MCP Tool 실행
        ↓
ToolResultCapture.capture("toolName", result)  → ThreadLocal에 저장
        ↓
A2ANaturalLanguageService
        ↓
ToolResultCapture.getAndClear()  → ThreadLocal에서 조회 + 초기화
        ↓
A2ASearchResponse DTO에 data로 포함
```

---

## Step 10-5: 응답 DTO

### A2ASearchResponse.java

A2A 방식의 API 최종 응답 DTO입니다.

> ### 왜 DTO를 따로 만들까요?
> `Map<String, Object>`를 그대로 반환해도 되지만, **DTO(Data Transfer Object)**를
> 사용하면 응답 구조가 명확해지고, 프론트엔드 개발자가 API를 이해하기 쉽습니다.
> Spring Boot에서 `@Data` (Lombok)로 getter/setter/toString을 자동 생성합니다.

```java
package com.nhnacademy.flyschedule.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * A2A 검색 응답 DTO (API 최종 응답용)
 */
@Data              // ① getter, setter, toString, equals, hashCode 자동 생성
@Builder           // ② 빌더 패턴으로 객체 생성 (가독성 좋은 코드)
@NoArgsConstructor // ③ 기본 생성자 (Jackson JSON 변환용)
@AllArgsConstructor// ④ 모든 필드 포함 생성자
public class A2ASearchResponse {

    private boolean success;  // ⑤ 성공 여부
    private String message;   // ⑥ 응답 메시지
    private Map<String, List<FlightInfoResponse>> data;  // ⑦ 항공사별 항공편 데이터

    // ⑧ 정적 팩토리 메서드: 성공 응답을 쉽게 생성
    public static A2ASearchResponse success(String message,
                                             Map<String, List<FlightInfoResponse>> data) {
        return A2ASearchResponse.builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    // ⑨ 정적 팩토리 메서드: 실패 응답을 쉽게 생성
    public static A2ASearchResponse fail(String message) {
        return A2ASearchResponse.builder()
                .success(false)
                .message(message)
                .data(Map.of())
                .build();
    }
}
```

> 📖 **Spring Boot 복습**: `@Builder` 패턴은 복잡한 객체를 생성할 때 유용합니다.
> `new A2ASearchResponse(true, "성공", data)` 보다
> `A2ASearchResponse.builder().success(true).message("성공").data(data).build()`가
> 각 필드가 무엇인지 명확하게 보입니다.

### LlmMessageResponse.java

LLM 응답 바인딩용 DTO입니다. Spring AI가 이 DTO로 자동 바인딩합니다.

```java
package com.nhnacademy.flyschedule.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 응답 바인딩용 DTO (.entity() 대상)
 *
 * LLM이 JSON 형식으로 응답하면 Spring AI가 이 DTO로 자동 바인딩합니다.
 * data는 ToolResultCapture에서 조합하므로 이 DTO에는 포함하지 않습니다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmMessageResponse {

    private boolean success;
    private String message;
}
```

---

## Step 10-6: 자연어 검색 컨트롤러

### NaturalLanguageSearchController.java

> ### 왜 두 개의 엔드포인트가 필요할까요?
> Coordinator 방식과 A2A 방식은 **동작 원리가 완전히 다릅니다**.
> 같은 엔드포인트에서 처리할 수도 있지만, 학습 목적과 디버깅 편의를 위해 분리했습니다.
> 실무에서는 하나의 엔드포인트로 통합하거나, 설정으로 전환할 수도 있습니다.

두 가지 방식의 자연어 검색을 지원하는 컨트롤러입니다.

```java
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

    // ① 두 방식의 Service를 모두 주입받습니다.
    private final NaturalLanguageOrchestrationService orchestrationService;
    private final A2ANaturalLanguageService a2aService;

    /**
     * 자연어 항공편 검색 (Coordinator 방식)
     *
     * POST /api/nl-search/search
     * Body: {"message": "내일 오후 2시 이후로 광주에서 제주로 가는 항공편 알려줘"}
     */
    @PostMapping("/search")
    public Map<String, Object> searchByNaturalLanguage(
            @RequestBody Map<String, String> request) {
        // ② 요청에서 message 필드 추출
        String message = request.get("message");
        if (message == null || message.isBlank()) {
            return error("메시지를 입력해주세요.");
        }

        log.info("자연어 검색 요청 (Coordinator 방식): {}", message);

        try {
            // ③ Orchestrator에게 전체 처리를 위임
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
     * POST /api/nl-search/search/a2a
     * Body: {"message": "...", "model": "ollama"}  (model은 optional, 기본값 "ollama")
     */
    @PostMapping("/search/a2a")
    public A2ASearchResponse searchByA2A(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        if (message == null || message.isBlank()) {
            return A2ASearchResponse.fail("메시지를 입력해주세요.");
        }

        // ④ model 파라미터로 LLM 선택 가능 (기본값: ollama)
        String model = request.getOrDefault("model", "ollama");
        log.info("자연어 검색 요청 (A2A 방식, model={}): {}", model, message);

        // ⑤ A2A Service에게 처리를 위임
        return a2aService.searchAndParse(message, model);
    }

    // ⑥ 에러 응답 생성 헬퍼 메서드
    private Map<String, Object> error(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", message);
        return error;
    }
}
```

> 📖 **Spring Boot 복습**: `@RequestBody Map<String, String>`은
> JSON 요청 본문을 Map으로 직접 받는 간단한 방법입니다.
> 실무에서는 전용 DTO(`SearchRequest`)를 만드는 것이 더 안전하지만,
> 학습 단계에서는 Map으로 간단히 처리합니다.

---

## Step 10-7: 테스트

### 테스트 시나리오

```bash
# === Coordinator 방식 테스트 ===

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


# === A2A 방식 테스트 ===

# 시나리오 5: A2A 검색 (Ollama, 기본값)
curl -X POST http://localhost:8080/api/nl-search/search/a2a \
  -H "Content-Type: application/json" \
  -d '{"message": "내일 광주에서 제주로 가는 항공편 알려줘"}'

# 시나리오 6: A2A 검색 (Gemini 모델 지정)
curl -X POST http://localhost:8080/api/nl-search/search/a2a \
  -H "Content-Type: application/json" \
  -d '{"message": "내일 광주에서 제주로 가는 항공편 알려줘", "model": "gemini"}'
```

---

## Step 10-8: LLM과 A2A의 역할 분담

### LLM의 역할

| 역할 | 설명 | 예시 |
|------|------|------|
| **파라미터 추출**| 자연어에서 정보 추출 | "광주" → departure: "광주" |
| **의도 파악**| 사용자의 의도 이해 | "알려줘" → 검색 요청 |
| **Function Calling**| A2A 방식에서 Tool 자율 호출 | LLM이 직접 MCP Tool 선택 |

### A2A의 역할

| 역할 | 설명 | 예시 |
|------|------|------|
| **데이터 변환**| 파라미터 포맷 변환 | "내일" → "20260310" |
| **API 호출**| 외부 시스템 연동 | 공공데이터 API 호출 |
| **필터링**| 조건에 맞는 데이터 추출 | 시간/가격 필터링 |
| **그룹핑**| 데이터 정리 | 항공사별 그룹핑 |

### Coordinator 방식 vs A2A 방식 비교

| 항목 | Coordinator 방식 | A2A 방식 |
|------|-----------------|----------|
| **제어권**| Orchestrator가 흐름 제어 | LLM이 Tool 선택 |
| **LLM 모델**| Ollama | Ollama / Gemini 선택 가능 |
| **예측 가능성**| 높음 (순차적 흐름) | 낮음 (LLM 자율 판단) |
| **유연성**| 낮음 (고정된 단계) | 높음 (LLM이 동적 대응) |
| **엔드포인트**| POST /api/nl-search/search | POST /api/nl-search/search/a2a |
| **응답 형식**| Map<String, Object> | A2ASearchResponse DTO |
| **디버깅**| 쉬움 (단계별 로그) | 어려움 (LLM 판단 추적) |
| **새 조건 추가**| 코드 수정 필요 | 프롬프트 수정으로 대응 가능 |

---

## 요약

이 단계에서 학습한 핵심 내용:

1. **Coordinator 방식**은 코드로 순서를 지정하고, **A2A 방식**은 LLM이 자율적으로 Tool을 선택합니다
2. **LlmAnalysisService**는 정규식 → LLM → 정규식(Fallback) 순서로 파라미터를 추출합니다
3. **ToolResultCapture**는 ThreadLocal을 사용해 LLM이 호출한 Tool의 결과를 안전하게 저장합니다
4. 상황에 따라 **적절한 방식을 선택**하는 것이 중요합니다:
   - 안정성이 중요하면 Coordinator
   - 유연성이 중요하면 A2A

---

## 체크리스트

- [ ] `LlmAnalysisService`가 Ollama ChatClient로 구현됨
- [ ] `NaturalLanguageOrchestrationService`가 10단계 조율을 수행함
- [ ] `A2ANaturalLanguageService`가 Function Calling으로 구현됨
- [ ] `ToolResultCapture`가 ThreadLocal로 Tool 결과를 캡처함
- [ ] `A2ASearchResponse` DTO가 구현됨
- [ ] LLM 파라미터 추출이 동작함 (정규식 fallback 포함)
- [ ] A2A 에이전트 조율이 동작함
- [ ] Coordinator 방식과 A2A 방식 모두 테스트됨
- [ ] 복합 조건 검색이 동작함

---

## 다음 단계

**Step 11: 테스트와 배포**에서 Spring AI 애플리케이션을 테스트하고 배포하는 방법을 학습합니다.

---

## 참고 자료

- [Spring AI Testing Guide](https://docs.spring.io/spring-ai/reference/testing.html)
- [LLM Integration Patterns](https://martinfowler.com/articles/llm-Integration-patterns.html)
- [Prompt Engineering Guide](https://www.promptingguide.ai/)
