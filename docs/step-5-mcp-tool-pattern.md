# Step 5: 다중 Function Calling과 MCP Tool 패턴

## 학습 목표

이 단계를 마치면 다음을 할 수 있습니다:

1. **@Tool 애노테이션**으로 AI 모델이 호출할 수 있는 함수를 정의할 수 있다
2. **에이전트 위임(Delegation) 패턴**으로 Tool과 비즈니스 로직을 분리할 수 있다
3. **ToolResultCapture**를 사용하여 Tool 호출 결과를 컨트롤러에서 활용할 수 있다
4. **여러 Tool을 조합**하여 복잡한 사용자 요청을 처리할 수 있다

> 💡 **핵심 개념**: `@Tool` 애노테이션은 AI 모델이 호출할 수 있는 함수를 등록하는 애노테이션입니다.
> Spring MVC의 `@RequestMapping`과 비슷하지만, URL이 아니라 **AI 모델이 자연어를 분석해서 판단하여 호출하는 함수**를 정의합니다.
> `@RequestMapping`은 웹 브라우저가 URL로 호출한다면, `@Tool`은 AI 모델이 필요할 때 자동으로 호출합니다.

---

## 전체 흐름

이 단계에서 구현하는 전체 구조를 한눈에 파악해 봅시다:

```
[사용자: "광주에서 제주 내일 항공편 알려줘"]
       ↓
[ChatClient → LLM: "이 질문에 답하려면 FlightSearchTool을 호출해야겠다!"]
       ↓
[FlightSearchTool.searchFlightsByAirline("광주", "제주", "내일")]
       ↓
[FlightSearchAgent → DateParserAgent → AirportCodeAgent → API 호출 → GroupingAgent]
       ↓
[결과를 LLM에게 전달 → LLM이 한국어로 정리 → 사용자에게 응답]
```

이처럼 **사용자가 직접 API를 호출하지 않아도**, AI 모델이 상황을 판단해서 알맞은 Tool을 자동으로 호출합니다.

---

## MCP Tool 패턴이란?

**MCP (Model Context Protocol) Tool 패턴**은 LLM이 사용할 수 있는 **도구(Tool)**를 체계적으로 설계하고 구현하는 패턴입니다.

> 📖 **Spring Boot 복습**: Spring Boot에서 `@RestController`를 만들면 Spring이 자동으로 이를 찾아 웹 요청에 매핑합니다.
> MCP Tool도 비슷합니다. `@Component` 클래스 안에 `@Tool` 메서드를 만들면, Spring AI가 자동으로 이를 찾아 AI 모델에게 "이런 도구들을 사용할 수 있어"라고 알려줍니다.

### @RequestMapping vs @Tool

두 애노테이션은 "누가 호출하느냐"가 다릅니다:

| 비교 항목 | @RequestMapping | @Tool |
|-----------|----------------|-------|
| **호출 주체** | 웹 브라우저/클라이언트 | AI 모델(LLM) |
| **입력** | HTTP 요청 (URL, Body) | 자연어에서 AI가 추출한 파라미터 |
| **출력** | HTTP 응답 (JSON, HTML) | AI 모델에게 전달되는 데이터 |
| **등록 방법** | `@RestController` | `@Component` + `defaultTools()` |
| **언제 호출?** | 사용자가 URL 접속 | AI 모델이 필요하다고 판단할 때 |
| **인터페이스 설명** | URL 경로로 설명 | `description` 속성으로 AI에게 설명 |

### MCP (Model Context Protocol)의 기원

**MCP**는 Anthropic이 제안한 프로토콜로, AI 모델이 외부 시스템과 상호작용할 수 있는 표준 방식을 정의합니다.

**핵심 목표:**
- AI 모델이 도구(Tools)를 통해 실제 행동을 수행
- 모델과 도구 간의 표준화된 인터페이스 제공
- 확장 가능한 도구 생태계 조성

### MCP Tool의 설계 철학

**1. 선언적 (Declarative)**

```java
// 나쁜 예시: 절차적 코드
// AI가 뭘 해야 할지 직접 if문으로 판단해야 합니다
if (userInput.contains("항공편") && userInput.contains("검색")) {
    if (userInput.contains("광주")) {
        // 광주 관련 로직
    }
}

// 좋은 예시: 선언적 Tool
// ① @Tool: 이 메서드를 AI 모델이 호출할 수 있는 도구로 등록합니다
// ② description: AI 모델이 이 도구의 용도를 이해하는 데 사용합니다
// ③ @ToolParam: 각 매개변수의 의미를 AI 모델에게 설명합니다
@Tool(description = "항공편을 검색합니다")                          // ① ②
public Map<String, List<Flight>> searchFlights(
    @ToolParam(description = "출발 공항") String departure,          // ③
    @ToolParam(description = "도착 공항") String arrival             // ③
) { ... }
```

**2. 자기 설명적 (Self-Descriptive)**

Tool은 스스로를 설명할 수 있어야 합니다:

```java
@Tool(
    description = """
    항공편을 검색하여 항공사별로 그룹핑합니다.

    언제 사용:
    - 사용자가 특정 노선의 항공편을 물을 때
    - 특정 날짜의 스케줄을 조회할 때

    파라미터:
    - departure: 출발 공항 이름
    - arrival: 도착 공항 이름
    - date: 날짜 (YYYY-MM-DD 형식)

    반환값:
    - 항공사별 항공편 목록 (Map 형태)
    """
)
```

**3. 상태 비저장 (Stateless)**

각 Tool 호출은 독립적이어야 합니다:

```java
// 나쁜 예시: 상태 의존
public class BadTool {
    private String cachedDeparture;  // 상태 저장

    public void setDeparture(String dep) { ... }
    public Map<String, List<Flight>> search(String arr) { ... }
}

// 좋은 예시: 상태 비저장
public class GoodTool {
    public Map<String, List<Flight>> search(
        String departure,
        String arrival
    ) { ... }
}
```

### MCP Tool의 핵심 원칙

| 원칙 | 설명 | 예시 |
|------|------|------|
| **단일 책임**| 하나의 Tool은 하나의 명확한 기능만 | `searchFlights`, `getAirports` |
| **명확한 인터페이스**| 파라미터와 반환값이 명확해야 함 | `String departure`, `Map<String, List<Flight>>` |
| **독립성**| 각 Tool은 독립적으로 동작 가능 | `searchFlights`는 `getAirports` 없이도 동작 |
| **재사용성**| 다른 Tool에서도 사용 가능 | `getAirportCode`는 여러 Tool에서 활용 |

---

## Step 5-1: Tool 카테고리 분류

### Tool 카테고리 구조

```
MCP Tools (com.nhnacademy.flyschedule.mcp)
├── FlightSearchTool              # 항공편 검색 Tool
│   ├── searchFlightsByAirline()  # 항공사별 그룹핑 검색
│   ├── searchFlightsAfterTime()  # 시간 필터링 검색
│   └── searchFlightsByPriceRange()  # 가격 필터링 검색
│
├── AirportInfoTool               # 공항 정보 Tool
│   ├── getAirportList()          # 전체 공항 목록
│   └── getAirportCode()          # 공항 코드 조회
│
└── AirlineInfoTool               # 항공사 정보 Tool
    ├── getAirlineList()          # 전체 항공사 목록
    └── getAirlineId()            # 항공사 ID 조회
```

---

## Step 5-2: 에이전트 위임(Delegation) 패턴

이 프로젝트에서는 Tool이 직접 비즈니스 로직을 수행하지 않고, **전문화된 에이전트(Agent)**에게 작업을 위임합니다.

### 왜 Agent에게 위임할까요?

Spring Boot에서 Service가 Repository에 데이터 조회를 위임하는 것과 같은 이유입니다:

```
// Spring Boot에서 이미 배운 패턴:
@RestController → Service → Repository
   (진입점)      (비즈니스 로직)  (데이터 접근)

// Spring AI에서도 같은 패턴:
@Tool → Agent → ApiClientService
(진입점)  (비즈니스 로직) (외부 API 호출)
```

- **Tool**은 "AI 모델이 호출하는 입구"입니다. 얇게 유지합니다.
- **Agent**는 "실제 로직을 수행하는 구현체"입니다.
- 이렇게 분리하면 Tool의 코드가 깔끔해지고, Agent는 다른 Tool에서도 재사용할 수 있습니다.
- `AirportCodeAgent`를 예로 들면, `FlightSearchTool`과 `AirportInfoTool` 모두에서 사용됩니다.

### 아키텍처 구조

```
MCP Tool (진입점)
  ↓ 위임
Agent (작업 조율)
  ↓ 호출
ApiClientService (API 호출)
```

### 실제 구현 예시

아래 코드에서 Tool이 얼마나 얇은지, 그리고 실제 로직이 Agent에게 위임되는지를 확인해 보세요:

**AirportInfoTool**은 직접 API를 호출하지 않고 `AirportListAgent`에게 위임합니다:

```java
// AirportInfoTool.java
// ① @Component: Spring이 관리하는 Bean으로 등록합니다 (Spring AI가 Tool을 찾을 수 있게 함)
// ② @RequiredArgsConstructor: final 필드에 대해 생성자를 자동 생성합니다 (DI)
@Slf4j
@Component                                                        // ①
@RequiredArgsConstructor                                          // ②
public class AirportInfoTool {

    // ③ Agent를 주입받습니다. Tool은 직접 API를 호출하지 않습니다
    private final AirportListAgent airportListAgent;               // ③

    // ④ @Tool: AI 모델이 이 메서드를 호출할 수 있게 합니다
    // ⑤ description: AI 모델이 언제 이 Tool을 사용해야 할지 판단하는 근거입니다
    @Tool(description = "전체 공항 목록을 조회합니다. ...")         // ④ ⑤
    public List<AirportInfoResponse> getAirportList() {
        log.info("MCP Tool 호출: getAirportList()");

        // ⑥ Tool은 단순히 Agent에게 위임만 합니다!
        return airportListAgent.getAirportList();                  // ⑥
    }

    @Tool(description = "공항 이름으로 공항 코드를 조회합니다. ...")
    public String getAirportCode(
            @ToolParam(description = "공항 이름 (예: 광주, 김포, 제주)") String airportName) {
        log.info("MCP Tool 호출: getAirportCode(airportName={})", airportName);

        // 역시 Agent에게 위임
        return airportListAgent.getAirportCode(airportName);       // ⑥
    }
}
```
@Slf4j
@Component
@RequiredArgsConstructor
public class AirportInfoTool {

    private final AirportListAgent airportListAgent;

    @Tool(description = "전체 공항 목록을 조회합니다. ...")
    public List<AirportInfoResponse> getAirportList() {
        log.info("MCP Tool 호출: getAirportList()");

        // A2A: Coordinator → AirportListAgent
        return airportListAgent.getAirportList();
    }

    @Tool(description = "공항 이름으로 공항 코드를 조회합니다. ...")
    public String getAirportCode(
            @ToolParam(description = "공항 이름 (예: 광주, 김포, 제주)") String airportName) {
        log.info("MCP Tool 호출: getAirportCode(airportName={})", airportName);

        // A2A: Coordinator → AirportCodeAgent
        return airportListAgent.getAirportCode(airportName);
    }
}
```

**AirlineInfoTool**도 같은 패턴으로 `AirlineListAgent`에게 위임합니다:

```java
// AirlineInfoTool.java
@Slf4j
@Component
@RequiredArgsConstructor
public class AirlineInfoTool {
@Slf4j
@Component
@RequiredArgsConstructor
public class AirlineInfoTool {

    private final AirlineListAgent airlineListAgent;

    @Tool(description = "전체 항공사 목록을 조회합니다. ...")
    public List<AirlineInfoResponse> getAirlineList() {
        log.info("MCP Tool 호출: getAirlineList()");

        // A2A: Coordinator → AirlineListAgent
        return airlineListAgent.getAirlineList();
    }

    @Tool(description = "항공사 이름으로 항공사 ID를 조회합니다. ...")
    public String getAirlineId(
            @ToolParam(description = "항공사 이름 (예: 아시아나항공, 대한항공, 제주항공)") String airlineName) {
        log.info("MCP Tool 호출: getAirlineId(airlineName={})", airlineName);

        // A2A: Coordinator → AirlineCodeAgent
        return airlineListAgent.getAirlineId(airlineName);
    }
}
```

### 에이전트 계층 구조

> 📖 **Spring Boot 복습**: Spring Boot에서 Controller → Service → Repository 계층 구조를 배웠죠?
> Tool → Agent → ApiClientService도 같은 계층 구조입니다. 각 계층이 하나의 역할만 담당합니다.

```
Tool (MCP 진입점)
 └── Agent (작업 조율자)
      ├── Agent (하위 전문 에이전트)
      └── ApiClientService (외부 API 호출)
```

예를 들어 `AirportListAgent`는 `ApiClientService`와 `AirportCodeAgent`를 사용합니다:

```java
// AirportListAgent.java
@Service
@RequiredArgsConstructor
public class AirportListAgent {

    private final ApiClientService apiClientService;
    private final AirportCodeAgent airportCodeAgent;

    public List<AirportInfoResponse> getAirportList() {
        return apiClientService.getAirportList();
    }

    public String getAirportCode(String airportName) {
        return airportCodeAgent.getAirportCode(airportName);
    }
}
```

---

## Step 5-3: ToolResultCapture 패턴

### ToolResultCapture란?

**ToolResultCapture**는 MCP Tool의 호출 결과를 `ThreadLocal`에 저장하는 유틸리티 클래스입니다. LLM이 Tool을 호출하면 반환값을 캡처하여, 컨트롤러/서비스 계층에서 응답 데이터로 활용할 수 있습니다.

### 왜 결과를 캡처해야 할까요?

AI 모델이 Tool을 호출하면, 반환값은 AI 모델에게 전달되어 자연어로 변환됩니다.
하지만 **컨트롤러에서도 이 데이터를 JSON 형태로 클라이언트에게 전달**해야 할 때가 있습니다.
ToolResultCapture는 이렇게 "두 곳에서 같은 데이터를 사용"해야 할 때 필요합니다.

```
[Tool 실행 결과]
     ↓
  ┌──────────────────────────────────┐
  │  1. AI 모델에게 전달 → 자연어 응답   │
  │  2. ToolResultCapture 저장 → JSON 응답 │  ← 두 가지 용도!
  └──────────────────────────────────┘
```

### 구현

```java
// com.nhnacademy.flyschedule.mcp.ToolResultCapture
package com.nhnacademy.flyschedule.mcp;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP Tool 호출 결과를 캡처하는 유틸리티
 *
 * A2A 방식에서 LLM이 Tool을 호출하면, Tool의 반환값을 ThreadLocal에 저장합니다.
 * 서비스 계층에서 Tool 호출 후 캡처된 결과를 읽어 응답의 data 필드에 사용합니다.
 */
public final class ToolResultCapture {

    private static final ThreadLocal<Map<String, Object>> RESULTS = new ThreadLocal<>();

    private ToolResultCapture() {}

    public static void capture(String toolName, Object result) {
        if (RESULTS.get() == null) {
            RESULTS.set(new HashMap<>());
        }
        RESULTS.get().put(toolName, result);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getAndClear() {
        Map<String, Object> results = RESULTS.get();
        RESULTS.remove();
        return results != null ? results : new HashMap<>();
    }
}
```

### 사용 방법

Tool 메서드에서 `ToolResultCapture.capture()`를 호출합니다:

```java
@Tool(description = "항공편을 검색하여 항공사별로 그룹핑하여 반환합니다. ...")
public Map<String, List<FlightInfoResponse>> searchFlightsByAirline(...) {
    // ... 검색 로직 ...

    ToolResultCapture.capture("searchFlightsByAirline", limitedFlights);
    return limitedFlights;
}
```

서비스 계층에서 `getAndClear()`로 결과를 가져옵니다:

```java
// 서비스 계층
String llmResponse = chatClient.prompt()
        .user(question)
        .call()
        .content();

Map<String, Object> toolResults = ToolResultCapture.getAndClear();
// toolResults를 응답 데이터에 포함
```

### ThreadLocal 기반 캡처의 장점

| 장점 | 설명 |
|------|------|
| **스레드 안전**| 각 요청 스레드마다 독립적인 저장소 |
| **Tool 투명성**| Tool이 캡처 로직에 의존하지 않음 |
| **간편한 조회**| 서비스 계층에서 한 번에 모든 결과 조회 |

---

## Step 5-4: Tool 등록 패턴

> 📖 **Spring Boot 복습**: `@Configuration` 클래스에서 `@Bean` 메서드로 객체를 등록하는 방법은 이미 배웠습니다.
> Tool 등록도 같은 방식입니다. `ChatClient.builder()`에 `.defaultTools()`로 Tool Bean을 연결하면 됩니다.

### 명시적 등록 (권장)

```java
@Configuration
public class ChatClientConfig {

    @Bean
    @Primary
    public ChatClient.Builder ollamaChatClientBuilder(
            @Qualifier("ollamaChatModel") ChatModel ollamaChatModel,
            FlightSearchTool flightSearchTool,
            AirportInfoTool airportInfoTool,
            AirlineInfoTool airlineInfoTool) {

        return ChatClient.builder(ollamaChatModel)
                .defaultTools(flightSearchTool, airportInfoTool, airlineInfoTool);
    }

    @Bean
    public ChatClient.Builder geminiChatClientBuilder(
            @Qualifier("googleGenAiChatModel") ChatModel geminiChatModel,
            FlightSearchTool flightSearchTool,
            AirportInfoTool airportInfoTool,
            AirlineInfoTool airlineInfoTool) {

        return ChatClient.builder(geminiChatModel)
                .defaultTools(flightSearchTool, airportInfoTool, airlineInfoTool);
    }
}
```

**장점:**
- 어떤 Tool이 등록되었는지 명확
- 순서 제어 가능
- 선택적 Tool 등록 가능

---

## Step 5-5: Tool 조합 예시

### 예시 1: 단일 Tool 호출

```
사용자: "공항 리스트 보여줘"
  ↓
LLM: AirportInfoTool.getAirportList() 호출
  ↓
AirportInfoTool → AirportListAgent → ApiClientService.getAirportList()
  ↓
Tool: 공항 목록 반환
  ↓
LLM: "전국 공항 목록입니다..." 응답
```

### 예시 2: 다중 Tool 호출 (순차적)

```
사용자: "광주 공항 코드가 뭐야? 그리고 내일 광주에서 제주 항공편 알려줘"
  ↓
LLM: 1) AirportInfoTool.getAirportCode("광주") 호출
  ↓     "NAARKJJ"
LLM: 2) FlightSearchTool.searchFlightsByAirline("광주", "제주", "내일") 호출
  ↓     FlightSearchAgent → DateParserAgent → AirportCodeAgent → API → GroupingAgent
  ↓     항공편 목록
LLM: "광주 공항 코드는 NAARKJJ입니다. 내일 항공편은..." 응답
```

### 예시 3: 다중 Tool 호출 (병렬)

```
사용자: "공항 리스트와 항공사 리스트 알려줘"
  ↓
LLM: 1) AirportInfoTool.getAirportList() 호출  ┐
     2) AirlineInfoTool.getAirlineList() 호출  ┤ (병렬)
  ↓                                             ┘
LLM: 결과 통합
LLM: "전국 공항은 15개이고, 항공사는 8개입니다..." 응답
```

---

## Step 5-6: Tool Description 최적화

### 좋은 Description의 5가지 원칙

| 원칙 | 설명 | 나쁜 예시 | 좋은 예시 |
|------|------|----------|----------|
| **1. 무엇을 하는지**| 기능 명확히 설명 | "검색" | "항공편을 검색하여 항공사별로 정리합니다" |
| **2. 언제 사용하는지**| 사용 상황 명시 | (없음) | "사용자가 특정 날짜의 항공편을 물어볼 때 사용" |
| **3. 파라미터 설명**| 각 파라미터 역할 명시 | "날짜" | "날짜 (예: 내일, 모레, 2025-03-09)" |
| **4. 반환값 설명**| 무엇을 반환하는지 명시 | (없음) | "항공사별 항공편 목록을 Map 형태로 반환" |
| **5. 제한 사항**| 제한이 있으면 명시 | (없음) | "항공사별 최대 3편만 반환 (응답 속도 최적화)" |

### 실제 프로젝트의 Description 예시

```java
@Tool(
    description = "항공편을 검색하여 항공사별로 그룹핑하여 반환합니다. " +
            "출발 공항, 도착 공항, 날짜를 받아 항공사별로 정리된 항공편 목록을 제공합니다. " +
            "날짜는 '내일', '모레', '2025-03-09' 형식을 지원합니다. " +
            "빠른 응답을 위해 항공사별 최대 3편만 반환합니다."
)
public Map<String, List<FlightInfoResponse>> searchFlightsByAirline(
    @ToolParam(description = "출발 공항 이름 (예: 광주, 김포, 제주)") String departure,
    @ToolParam(description = "도착 공항 이름 (예: 제주, 김포, 부산)") String arrival,
    @ToolParam(description = "날짜 (예: 내일, 모레, 2025-03-09)") String date
) { ... }
```

---

## Step 5-7: Tool 설계 패턴

### 패턴 1: 조회 Tool (Read-Only)

```java
@Component
public class ReadOnlyTool {

    @Tool(description = "데이터 조회 (변경 없음)")
    public DataType getData(String id) {
        return repository.findById(id);
    }
}
```

**특징:**
- 부작용(Side Effect) 없음
- 여러 번 호출해도 안전
- 캐싱 가능

### 패턴 2: 명령 Tool (Command)

```java
@Component
public class CommandTool {

    @Tool(description = "데이터 변경 (주의 필요)")
    public Result updateData(String id, Data data) {
        log.warn("데이터 변경: id={}", id);
        return repository.update(id, data);
    }
}
```

**특징:**
- 부작용 있음
- 한 번만 호출해야 함
- 권한 체크 필요

### 패턴 3: 필터링 Tool

```java
@Component
public class FilterTool {

    @Tool(description = "조건에 맞는 데이터만 필터링")
    public List<DataType> filterData(
            List<DataType> data,
            @ToolParam(description = "최소값") Integer min,
            @ToolParam(description = "최대값") Integer max) {

        return data.stream()
                .filter(item -> item.getValue() >= min && item.getValue() <= max)
                .toList();
    }
}
```

**특징:**
- 다른 Tool의 결과를 입력으로 받음
- 순수 함수 형태
- 재사용 가능

---

## Tool 성능 최적화

### 최적화 1: 결과 제한

```java
// 항공사별 최대 3편만 반환 (LLM 타임아웃 방지)
Map<String, List<FlightInfoResponse>> limitedFlights = new HashMap<>();
allFlights.forEach((airline, flights) -> {
    if (flights.size() > 3) {
        limitedFlights.put(airline, flights.subList(0, 3));
    } else {
        limitedFlights.put(airline, flights);
    }
});
```

### 최적화 2: 캐싱

```java
@Component
public class CachedTool {

    private final Cache<String, List<Flight>> cache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    @Tool(description = "항공편 검색 (캐싱)")
    public List<Flight> searchFlights(String departure, String arrival, String date) {
        String key = departure + "-" + arrival + "-" + date;

        return cache.get(key, k -> {
            return apiClient.search(departure, arrival, date);
        });
    }
}
```

---

## 일반적인 문제 해결

### 문제 1: LLM이 적절한 Tool을 선택하지 못함

**증상:**다른 Tool이 호출되거나 Tool이 호출되지 않음

**해결 방법:**
1. Description 더 구체화
2. Tool 이름 명확하게
3. 파라미터 설명 상세화

```java
// 나쁜 예시
@Tool(description = "검색")
public List<Flight> search(String from, String to) { ... }

// 좋은 예시
@Tool(description = "항공편을 검색하여 항공사별로 그룹핑합니다")
public Map<String, List<Flight>> searchFlightsByAirline(
    @ToolParam(description = "출발 공항 이름 (예: 광주, 김포)") String departure,
    @ToolParam(description = "도착 공항 이름 (예: 제주, 부산)") String arrival
) { ... }
```

### 문제 2: Tool이 너무 많아서 LLM이 혼란스러워함

**증상:**엉뚱한 Tool이 호출됨

**해결 방법:**
1. 관련 Tool끼리 그룹화
2. `defaultTools()` 대신 특정 상황별로 다른 Tool 세트 사용
3. Tool 수 줄이기 (비슷한 Tool 통합)

### 문제 3: Tool 호출 순서 문제

**증상:**선행 Tool의 결과가 필요한데 먼저 호출되지 않음

**해결 방법:**
1. Description에 "먼저 X를 호출해야 함" 명시
2. 필요한 데이터를 파라미터로 받도록 설계
3. Tool 내부에서 다른 에이전트 호출 (이 프로젝트의 방식)

```java
@Tool(
    description = """
    항공편을 검색합니다.

    주의: 공항 이름(예: "광주")을 입력하면 내부적으로 공항 코드로 변환합니다.
    """
)
public Map<String, List<FlightInfoResponse>> searchFlightsByAirline(
    @ToolParam(description = "출발 공항 이름 (예: 광주, 김포, 제주)") String departure,
    @ToolParam(description = "도착 공항 이름 (예: 제주, 김포, 부산)") String arrival,
    @ToolParam(description = "날짜 (예: 내일, 모레, 2025-03-09)") String date
) {
    // FlightSearchAgent가 내부적으로 AirportCodeAgent를 호출하여 공항 코드 변환
    return flightSearchAgent.searchAndGroupByAirline(departure, arrival, date);
}
```

---

## 요약

이 단계에서 배운 핵심 내용을 정리합니다:

1. **@Tool 애노테이션**: AI 모델이 자연어를 분석하여 필요할 때 자동으로 호출하는 함수를 만듭니다
2. **에이전트 위임 패턴**: Tool은 얇은 진입점이고, Agent가 실제 비즈니스 로직을 수행합니다
   - Spring Boot의 Controller → Service → Repository와 같은 계층 구조
3. **ToolResultCapture**: Tool 실행 결과를 ThreadLocal에 저장하여 컨트롤러에서도 활용할 수 있게 합니다
4. **Tool Description 최적화**: AI 모델이 언제 어떤 Tool을 사용할지 정확히 판단하도록 description을 상세하게 작성합니다
5. **다중 Tool 조합**: 하나의 사용자 질문에 여러 Tool이 순차적 또는 병렬로 호출될 수 있습니다

---

## 체크리스트

- [ ] Tool이 기능별로 분리됨 (FlightSearchTool, AirportInfoTool, AirlineInfoTool)
- [ ] 각 Tool이 단일 책임을 가짐
- [ ] Tool Description이 명확함
- [ ] Tool이 ChatClient에 명시적으로 등록됨
- [ ] Tool이 에이전트에게 작업을 위임함
- [ ] ToolResultCapture로 결과가 캡처됨
- [ ] 다중 Tool이 정상적으로 동작함

---

## 다음 단계

**Step 6: 항공편 검색 Tool 구현**에서 실제 공공데이터 API와 연동하는 MCP Tool을 구현합니다.

---

## 참고 자료

- [Spring AI Function Calling 가이드](https://docs.spring.io/spring-ai/reference/api/functioncalling.html)
- [MCP (Model Context Protocol) 사양](https://modelcontextprotocol.io/)
- [Tool Design Best Practices](https://docs.spring.io/spring-ai/reference/api/functioncalling.html#_best_practices_for_tool_design)
