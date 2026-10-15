# Step 5: 다중 Function Calling과 MCP Tool 패턴

## 학습 목표

이 단계를 마치면 다음을 할 수 있습니다:

1. **@Tool 어노테이션**으로 AI 모델이 호출할 수 있는 메서드를 정의할 수 있습니다
2. **에이전트 위임(Delegation) 패턴**으로 Tool과 비즈니스 로직을 분리할 수 있습니다
3. **ToolResultCapture**를 사용하여 Tool 호출 기록(이름·인자·결과)을 서비스 계층에서 활용할 수 있습니다
4. **여러 Tool을 조합**하여 복잡한 사용자 요청을 처리할 수 있습니다

> **핵심 개념**: `@Tool` 어노테이션은 AI 모델이 호출할 수 있는 메서드를 등록하는 어노테이션입니다.
> Spring MVC의 `@RequestMapping`과 비슷하지만, URL이 아니라 **AI 모델이 자연어를 분석하고 판단하여 호출하는 메서드**를 정의합니다.
> `@RequestMapping`은 웹 브라우저가 URL로 호출한다면, `@Tool`은 AI 모델이 필요할 때 자동으로 호출합니다.

---

## 전체 흐름

이 단계에서 구현하는 전체 구조를 한눈에 파악해 봅시다.

```text
[사용자: "광주에서 제주 내일 항공편 알려줘"]
       ↓
[ChatClient → LLM: "이 질문에 답하려면 FlightSearchTool을 호출해야겠다!"]
       ↓
[FlightSearchTool.searchFlightsByAirline("광주", "제주", "내일")]
       ↓
[FlightSearchAgent → DateParserAgent → AirportCodeAgent → ApiClientService → GroupingAgent]
       ↓
[결과를 LLM에게 전달 → LLM이 한국어로 정리 → 사용자에게 응답]
```

이처럼 **사용자가 직접 API를 호출하지 않아도**, AI 모델이 상황을 판단해서 알맞은 Tool을 자동으로 호출합니다.

---

## MCP Tool 패턴이란?

**MCP (Model Context Protocol) Tool 패턴**은 LLM이 사용할 수 있는 **도구**(Tool)를 체계적으로 설계하고 구현하는 패턴입니다.

> **참고**: 이 프로젝트의 "MCP Tool"은 MCP 프로토콜로 통신하는 MCP 서버/클라이언트가 아니라, Spring AI의 **Tool Calling**(`@Tool`)으로 구현한 도구입니다.
> MCP의 도구 설계 방식을 참고했을 뿐, 별도의 MCP 서버를 실행하지 않고 같은 애플리케이션 안에서 `ChatClient`가 Tool을 직접 호출합니다.

> **Spring Boot 복습**: Spring Boot에서 `@RestController`를 만들면 Spring이 자동으로 이를 찾아 웹 요청에 매핑합니다.
> MCP Tool도 비슷합니다. `@Component` 클래스 안에 `@Tool` 메서드를 만들고 `ChatClient`에 넘기면(`.tools()` 또는 `defaultTools()`), Spring AI가 `@Tool` 메서드를 찾아 AI 모델에게 "이런 도구들을 사용할 수 있어"라고 알려 줍니다. (`@Component`만으로 자동 등록되지는 않습니다.)

### @RequestMapping vs @Tool

두 어노테이션은 "누가 호출하느냐"가 다릅니다.

| 비교 항목 | @RequestMapping | @Tool |
|-----------|----------------|-------|
| **호출 주체** | 웹 브라우저/클라이언트 | AI 모델(LLM) |
| **입력** | HTTP 요청 (URL, Body) | 자연어에서 AI가 추출한 파라미터 |
| **출력** | HTTP 응답 (JSON, HTML) | AI 모델에게 전달되는 데이터 |
| **등록 방법** | `@RestController` | `@Component` + `.tools()` (또는 `defaultTools()`) |
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
// ③ @ToolParam: 각 파라미터의 의미를 AI 모델에게 설명합니다
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
| **단일 책임** | 하나의 Tool은 하나의 명확한 기능만 | `searchFlights`, `getAirports` |
| **명확한 인터페이스** | 파라미터와 반환값이 명확해야 함 | `String departure`, `Map<String, List<Flight>>` |
| **독립성** | 각 Tool은 독립적으로 동작 가능 | `searchFlights`는 `getAirports` 없이도 동작 |
| **재사용성** | 다른 Tool에서도 사용 가능 | `getAirportCode`는 여러 Tool에서 활용 |

---

## Step 5-1: Tool 카테고리 분류

### Tool 카테고리 구조

```text
MCP Tools (net.java21.flyschedule.mcp)
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

이 프로젝트에서는 Tool이 직접 비즈니스 로직을 수행하지 않고, **전문화된 에이전트**(Agent)에게 작업을 위임합니다.

### 왜 에이전트에게 위임할까요?

Spring Boot에서 Service가 Repository에 데이터 조회를 위임하는 것과 같은 이유입니다.

```text
// Spring Boot에서 이미 배운 패턴:
@RestController → Service → Repository
   (진입점)      (비즈니스 로직)  (데이터 접근)

// Spring AI에서도 같은 패턴:
@Tool → Agent → ApiClientService
(진입점)  (비즈니스 로직) (외부 API 호출)
```

- **Tool**은 "AI 모델이 호출하는 입구"입니다. 얇게 유지합니다.
- **에이전트**(Agent)는 "실제 로직을 수행하는 구현체"입니다.
- 이렇게 분리하면 Tool의 코드가 깔끔해지고, 에이전트는 다른 Tool에서도 재사용할 수 있습니다.
- `AirportCodeAgent`를 예로 들면, `FlightSearchTool`(`FlightSearchAgent` 경유)과 `AirportInfoTool`(`AirportListAgent` 경유) 모두에서 사용됩니다.

> **용어 주의**: 이 교재에서 "에이전트"라고 부르는 `service/agent/*` 클래스(전문 에이전트)는 **LLM을 쓰지 않는** 결정적(deterministic) 컴포넌트입니다. 같은 입력이면 언제나 같은 결과를 냅니다. 이름에 Agent가 붙어 있지만 LLM이 판단하는 "AI 에이전트"와는 다르며, Step 8에서 자세히 다룹니다.

### 아키텍처 구조

```text
MCP Tool (진입점)
  ↓ 위임
에이전트 (작업 조율)
  ↓ 호출
ApiClientService (API 호출)
```

### 실제 구현 예시

아래 코드에서 Tool이 얼마나 얇은지, 그리고 실제 로직이 에이전트에게 위임되는지 확인해 보세요.

**AirportInfoTool**은 직접 API를 호출하지 않고 `AirportListAgent`에게 위임합니다.

```java
// AirportInfoTool.java
// ① @Component: Spring이 관리하는 Bean으로 등록합니다 (ToolCallingSearchService가 주입받아 .tools()로 넘길 수 있게 함)
// ② @RequiredArgsConstructor: final 필드에 대해 생성자를 자동 생성합니다 (DI)
@Slf4j
@Component                                                        // ①
@RequiredArgsConstructor                                          // ②
public class AirportInfoTool {

    // ③ 에이전트를 주입받습니다. Tool은 직접 API를 호출하지 않습니다
    private final AirportListAgent airportListAgent;               // ③

    // ④ @Tool: AI 모델이 이 메서드를 호출할 수 있게 합니다
    // ⑤ description: AI 모델이 언제 이 Tool을 사용해야 할지 판단하는 근거입니다
    @Tool(description = "전체 공항 목록을 조회합니다. ...")         // ④ ⑤
    public List<AirportInfoResponse> getAirportList() {
        log.info("MCP Tool 호출: getAirportList()");

        // ⑥ Tool은 에이전트에게 위임하고, 그 작업을 실행·기록하며 결과를 돌려줍니다 (Step 5-3)
        return ToolResultCapture.capture("getAirportList", "전체", airportListAgent::getAirportList);  // ⑥
    }

    @Tool(description = "공항 이름으로 공항 코드를 조회합니다. ...")
    public String getAirportCode(
            @ToolParam(description = "공항 이름 (예: 광주, 김포, 제주)") String airportName) {
        log.info("MCP Tool 호출: getAirportCode(airportName={})", airportName);

        // 역시 에이전트에게 위임
        return ToolResultCapture.capture("getAirportCode", airportName, () -> airportListAgent.getAirportCode(airportName));  // ⑥
    }
}
```

**AirlineInfoTool**도 같은 패턴으로 `AirlineListAgent`에게 위임합니다.

```java
// AirlineInfoTool.java
@Slf4j
@Component
@RequiredArgsConstructor
public class AirlineInfoTool {

    private final AirlineListAgent airlineListAgent;

    @Tool(description = "전체 항공사 목록을 조회합니다. ...")
    public List<AirlineInfoResponse> getAirlineList() {
        log.info("MCP Tool 호출: getAirlineList()");

        // AirlineListAgent에 위임
        return ToolResultCapture.capture("getAirlineList", "전체", airlineListAgent::getAirlineList);
    }

    @Tool(description = "항공사 이름으로 항공사 ID를 조회합니다. ...")
    public String getAirlineId(
            @ToolParam(description = "항공사 이름 (예: 아시아나항공, 대한항공, 제주항공)") String airlineName) {
        log.info("MCP Tool 호출: getAirlineId(airlineName={})", airlineName);

        // AirlineListAgent에 위임
        return ToolResultCapture.capture("getAirlineId", airlineName, () -> airlineListAgent.getAirlineId(airlineName));
    }
}
```

### 에이전트 계층 구조

> **Spring Boot 복습**: Spring Boot에서 Controller → Service → Repository 계층 구조를 배웠죠?
> Tool → 에이전트 → ApiClientService도 같은 계층 구조입니다. 각 계층이 하나의 역할만 담당합니다.

```text
Tool (MCP 진입점)
 └── 에이전트 (작업 조율자)
      ├── 에이전트 (하위 전문 에이전트)
      └── ApiClientService (외부 API 호출)
```

예를 들어 `AirportListAgent`는 `ApiClientService`와 `AirportCodeAgent`를 사용합니다(로그 출력은 생략).

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

**ToolResultCapture**는 LLM이 요청해 실행된 Tool 호출을 `ThreadLocal`에 기록하는 유틸리티 클래스입니다. Tool이 실행될 때 이름·인자·결과(또는 실패 메시지)·실행 시각을 남기면, `ToolCallingSearchService`가 LLM 호출이 끝난 뒤 그 기록을 읽어 화면용 결과와 실행 기록(trace)을 만듭니다.

### 왜 결과를 캡처해야 할까요?

AI 모델이 Tool을 호출하면, 반환값은 AI 모델에게 전달되어 자연어로 변환됩니다. `call().content()`가 돌려주는 것은 그 자연어 답변뿐입니다.
하지만 화면에는 **항공편 목록을 표로 보여 주고, LLM이 어떤 Tool을 어떤 인자로 불렀는지도 타임라인으로 보여 줘야** 합니다.
ToolResultCapture는 이렇게 "Tool 결과를 LLM과 서비스 계층 두 곳에서 사용"해야 할 때 필요합니다.

```text
[Tool 실행 결과]
     ↓
  1. AI 모델에게 전달 → 자연어 답변 (SearchResponse.message)
  2. ToolResultCapture에 기록 → 항공편 표 (SearchResponse.results) + 실행 기록 (TOOL 단계)
```

### 구현

`src/main/java/net/java21/flyschedule/mcp/ToolResultCapture.java`

```java
package net.java21.flyschedule.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ToolResultCapture {

    /**
     * Tool 호출 한 건
     *
     * @param result      Tool이 돌려준 값 (실패했으면 null)
     * @param error       실패했을 때의 메시지 (성공했으면 null)
     * @param startNanos  실행 시작 시각 (System.nanoTime())
     * @param endNanos    실행 종료 시각
     */
    public record ToolCall(String toolName, String arguments, Object result, String error,
                           long startNanos, long endNanos) {

        public boolean failed() {
            return error != null;
        }
    }

    // withInitial: 처음 get() 할 때 빈 ArrayList를 만들어 줍니다 (호출 순서 유지)
    private static final ThreadLocal<List<ToolCall>> CALLS = ThreadLocal.withInitial(ArrayList::new);

    private ToolResultCapture() {
    }

    /**
     * Tool 작업을 실행하면서 호출을 기록하고, 결과를 그대로 돌려줍니다.
     * 작업이 예외를 던지면 실패로 기록한 뒤 예외를 다시 던집니다. (Spring AI가 LLM에게 오류를 전달)
     */
    public static <T> T capture(String toolName, String arguments, Supplier<T> action) {
        long start = System.nanoTime();
        try {
            T result = action.get();
            CALLS.get().add(new ToolCall(toolName, arguments, result, null, start, System.nanoTime()));
            return result;
        } catch (RuntimeException e) {
            CALLS.get().add(new ToolCall(toolName, arguments, null, e.getMessage(), start, System.nanoTime()));
            throw e;
        }
    }

    /**
     * 지금까지의 호출 기록을 꺼내고 비웁니다. (호출 순서 유지)
     */
    public static List<ToolCall> getAndClear() {
        List<ToolCall> calls = List.copyOf(CALLS.get());
        CALLS.remove();  // 스레드 풀 재사용 시 이전 요청 데이터가 남지 않도록 제거
        return calls;
    }
}
```

**왜 이렇게 설계했을까요?**

- **`Map`이 아니라 `List<ToolCall>`**: 한 요청에서 LLM이 같은 Tool을 두 번(예: 김포→제주, 광주→제주) 부를 수 있습니다. Tool 이름을 키로 쓰는 Map이면 앞의 결과가 덮어써지지만, List는 호출을 모두 순서대로 남깁니다
- **`arguments`를 함께 기록**: 실행 기록에 "광주 → 제주, 내일"처럼 LLM이 넘긴 인자를 보여 주기 위해서입니다
- **`capture()`가 결과를 그대로 반환**: `return ToolResultCapture.capture(...)` 한 줄로 기록과 반환을 함께 처리하므로, 기록을 빠뜨리거나 다른 값을 기록하는 실수를 막습니다
- **값이 아니라 작업(`Supplier`)을 받음**: 결과 값만 받으면 Tool이 언제 시작해 얼마나 걸렸는지, 예외로 끝났는지를 알 수 없습니다. 작업을 `capture()` 안에서 실행하면 실제 실행 구간(`startNanos`~`endNanos`)을 재고, 예외가 나면 실패(`error`)로 기록한 뒤 다시 던질 수 있습니다. 예외를 삼키지 않으므로 Spring AI가 오류 메시지를 LLM에게 그대로 전달합니다

### 사용 방법

모든 `@Tool` 메서드는 실제 작업을 람다로 감싸 `ToolResultCapture.capture()`에 넘깁니다.

```java
@Tool(description = "항공편을 검색하여 항공사별로 그룹핑하여 반환합니다. ...")
public Map<String, List<FlightInfoResponse>> searchFlightsByAirline(...) {
    log.info("MCP Tool 호출: ...");

    return ToolResultCapture.capture("searchFlightsByAirline",
            "%s → %s, %s".formatted(departure, arrival, date),
            () -> limitPerAirline(flightSearchAgent.searchAndGroupByAirline(departure, arrival, date)));
}
```

서비스 계층(`ToolCallingSearchService`)은 LLM 호출 전에 기록을 비우고, 호출이 끝난 뒤 `getAndClear()`로 꺼냅니다.

```java
// ToolCallingSearchService.search() (일부)
ToolResultCapture.getAndClear(); // 이전 요청의 기록이 남아 있지 않도록 비운다

long start = System.nanoTime();
String answer = chatClientProvider.get(model).prompt()
        .system(systemPrompt())
        .user(message)
        .tools(flightSearchTool, airportInfoTool, airlineInfoTool)
        .call()
        .content();

// LLM 호출 구간을 먼저, 그 안에서 실행된 Tool 호출을 이어서 기록한다
List<ToolCall> calls = ToolResultCapture.getAndClear();
trace.record(Kind.LLM, ChatClientProvider.normalize(model),
        "Tool 선택과 답변 작성", "Tool " + calls.size() + "회 호출 후 답변", start);
recordToolCalls(calls, trace);   // Tool마다 TOOL 단계 (실제 시작·종료 시각, 실패면 "Tool 실행 실패")

// 가장 마지막에 실행된 항공편 검색(Map 결과)을 화면용 TaskResult로 바꾼다
TaskResult result = lastFlightResult(calls);
```

`lastFlightResult()`는 목록을 뒤에서부터 훑어 결과가 `Map`인(항공편 검색) 호출을 고릅니다. 공항 코드 조회처럼 `String`을 돌려준 호출이나 실패한 호출(`result`가 `null`)은 건너뜁니다. 호출 순서가 유지되어야 "마지막에 호출된 항공편 검색 결과"를 정확히 고를 수 있습니다. 예외가 나도 `catch` 블록에서 `getAndClear()`를 불러 `ThreadLocal`을 비우고, 그때까지 실행된 Tool 호출도 실행 기록에 남깁니다.

전체 호출 순서를 그림으로 보면 다음과 같습니다. 블로킹 방식의 `call()`에서는 Spring AI(`DefaultToolCallingManager`)가 Tool을 호출한 스레드에서 순차적으로 실행합니다. Tool 실행과 `getAndClear()`가 같은 요청 스레드에서 일어나기 때문에 `ThreadLocal`로 결과를 주고받을 수 있습니다. (`stream()`을 사용하면 Tool이 다른 스레드에서 실행될 수 있으므로 이 방식이 동작하지 않습니다.)

```text
SearchController.toolCalling()   (POST /api/search/tool-calling)
  ↓
ToolCallingSearchService.search()
  ↓ ToolResultCapture.getAndClear()   ← 이전 기록 비우기
  ↓ chatClient.prompt().system(...).user(message).tools(...).call()
LLM → FlightSearchTool.searchFlightsByAirline() 호출 요청
  ↓
FlightSearchTool → FlightSearchAgent.searchAndGroupByAirline()
  ↓ return ToolResultCapture.capture("searchFlightsByAirline", "광주 → 제주, 2026-10-16", () -> ...)
LLM → 자연어 응답(content) 생성
  ↓
ToolCallingSearchService: ToolResultCapture.getAndClear() → List<ToolCall>
  ↓ trace: LLM 단계 → TOOL 단계(호출 순서대로)
SearchResponse.success("tool-calling", answer, [TaskResult], trace) 반환
```

### ThreadLocal 기반 캡처의 장점과 한계

| 구분 | 내용 |
|------|------|
| **장점: 요청별 격리** | 각 요청 스레드마다 독립적인 저장소 |
| **장점: 단순한 전달** | Tool 메서드 시그니처를 바꾸지 않고 Tool 결과를 서비스 계층으로 전달 |
| **한계: 스레드 의존** | Tool이 다른 스레드에서 실행되는 `stream()`에서는 쓸 수 없음 |
| **한계: 비우기 책임** | `getAndClear()`를 부르지 않으면 스레드 풀의 스레드에 기록이 남음. 그래서 LLM 없이 Tool을 직접 부르는 `McpController` 경로를 위해 `ToolResultCaptureCleanupFilter`(`OncePerRequestFilter`)가 요청이 끝날 때마다 기록을 비움 |

---

## Step 5-4: Tool 등록 패턴

> **Spring Boot 복습**: `@Configuration` 클래스에서 `@Bean` 메서드로 객체를 등록하는 방법은 이미 배웠습니다.
> Tool은 두 곳 중 하나에서 ChatClient에 연결합니다. 빌더의 `.defaultTools()`(모든 요청) 또는 요청의 `.tools()`(그 요청만)입니다. (Step 3-2)

### 방법 1: 빌더에 기본 등록

```java
@Bean
public ChatClient ollamaChatClient(@Qualifier("ollamaChatModel") ChatModel ollamaChatModel,
                                   FlightSearchTool flightSearchTool,
                                   AirportInfoTool airportInfoTool,
                                   AirlineInfoTool airlineInfoTool) {
    return ChatClient.builder(ollamaChatModel)
            .defaultTools(flightSearchTool, airportInfoTool, airlineInfoTool)  // 모든 요청에 Tool 7개 전달
            .build();
}
```

- 장점: 서비스 코드가 Tool을 몰라도 됨
- 단점: Tool이 필요 없는 LLM 호출에도 Tool 정의가 붙음

### 방법 2: 요청마다 전달 (이 프로젝트의 방식)

```java
// ToolCallingSearchService: Tool 3개 클래스(@Tool 메서드 7개)를 생성자로 주입받아 이 요청에만 전달
chatClientProvider.get(model).prompt()
        .system(systemPrompt())
        .user(message)
        .tools(flightSearchTool, airportInfoTool, airlineInfoTool)
        .call()
        .content();
```

**장점:**
- 어떤 호출이 어떤 Tool을 쓸 수 있는지 코드에 명확히 드러남
- 검색 조건 추출·작업 계획·결과 요약처럼 Tool이 필요 없는 호출이 엉뚱한 Tool을 부르지 않음
- 상황별로 다른 Tool 세트를 넘길 수 있음 (예: 항공편 Tool만)

---

## Step 5-5: Tool 조합 예시

### 예시 1: 단일 Tool 호출

```text
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

```text
사용자: "광주 공항 코드가 뭐야? 그리고 내일 광주에서 제주 항공편 알려줘"
  ↓
LLM: 1) AirportInfoTool.getAirportCode("광주") 호출
  ↓     "NAARKJJ"
LLM: 2) FlightSearchTool.searchFlightsByAirline("광주", "제주", "내일") 호출
  ↓     FlightSearchAgent → DateParserAgent → AirportCodeAgent → ApiClientService → GroupingAgent
  ↓     항공편 목록
LLM: "광주 공항 코드는 NAARKJJ입니다. 내일 항공편은..." 응답
```

### 예시 3: 다중 Tool 호출 (한 번에 여러 Tool 요청)

LLM이 한 번의 응답에서 여러 Tool 호출을 함께 요청할 수 있습니다(모델에 따라 지원 여부가 다릅니다). 요청은 한 번에 오지만, Spring AI는 요청받은 Tool들을 차례대로 하나씩 실행합니다.

```text
사용자: "공항 리스트와 항공사 리스트 알려줘"
  ↓
LLM: 1) AirportInfoTool.getAirportList() 호출 ─┐
     2) AirlineInfoTool.getAirlineList() 호출 ─┴→ 동시에 요청
  ↓
LLM: 결과 통합
LLM: "전국 공항은 15개이고, 항공사는 8개입니다..." 응답
```

---

## Step 5-6: Tool Description 최적화

### 좋은 Description의 5가지 원칙

| 원칙 | 설명 | 나쁜 예시 | 좋은 예시 |
|------|------|----------|----------|
| **1. 무엇을 하는지** | 기능 명확히 설명 | "검색" | "항공편을 검색하여 항공사별로 정리합니다" |
| **2. 언제 사용하는지** | 사용 상황 명시 | (없음) | "사용자가 특정 날짜의 항공편을 물어볼 때 사용" |
| **3. 파라미터 설명** | 각 파라미터 역할 명시 | "날짜" | "날짜 (예: 내일, 모레, 2025-03-09)" |
| **4. 반환값 설명** | 무엇을 반환하는지 명시 | (없음) | "항공사별 항공편 목록을 Map 형태로 반환" |
| **5. 제한 사항** | 제한이 있으면 명시 | (없음) | "항공사별 최대 3편만 반환 (응답 속도 최적화)" |

### 실제 프로젝트의 Description 예시

`src/main/java/net/java21/flyschedule/mcp/FlightSearchTool.java` (일부)

```java
@Tool(
        description = "항공편을 검색하여 항공사별로 그룹핑하여 반환합니다. " +
                "출발 공항, 도착 공항, 날짜를 받아 항공사별로 정리된 항공편 목록을 제공합니다. " +
                "날짜는 '2026-03-25' 형식을 권장하며 '내일', '모레'도 지원합니다. " +
                "항공사별 최대 3편만 반환합니다."
)
public Map<String, List<FlightInfoResponse>> searchFlightsByAirline(
        @ToolParam(description = "출발 공항 이름 (예: 광주, 김포, 제주)") String departure,
        @ToolParam(description = "도착 공항 이름 (예: 제주, 김포, 부산)") String arrival,
        @ToolParam(description = "날짜, YYYY-MM-DD 형식 (예: 2026-03-25)") String date) { ... }
```

날짜 파라미터 설명에 `YYYY-MM-DD` 형식을 못 박은 이유: 작은 모델은 "내일"을 그대로 넘기기도 하고 "10월 16일"처럼 임의의 형식으로 넘기기도 합니다. 형식과 예시를 하나로 정해 두면 LLM이 시스템 프롬프트의 "오늘 날짜"를 보고 직접 날짜를 계산해 넘깁니다. ("내일", "모레"는 `DateParserAgent`가 받아 주므로 넘어와도 동작합니다.)

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

`FlightSearchTool`의 세 검색 Tool은 모두 결과를 `limitPerAirline()`에 통과시켜 항공사별 최대 `MAX_FLIGHTS_PER_AIRLINE`(3)편만 LLM에게 돌려줍니다.

```java
static final int MAX_FLIGHTS_PER_AIRLINE = 3;

// 소형 모델은 컨텍스트가 짧아 결과가 길면 응답이 느려지거나 잘린다
private static Map<String, List<FlightInfoResponse>> limitPerAirline(Map<String, List<FlightInfoResponse>> grouped) {
    Map<String, List<FlightInfoResponse>> limited = new LinkedHashMap<>();   // 항공사 순서 유지
    grouped.forEach((airline, flights) ->
            limited.put(airline, flights.subList(0, Math.min(flights.size(), MAX_FLIGHTS_PER_AIRLINE))));
    // ... 생략 (로그)
    return limited;
}
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

**증상:** 다른 Tool이 호출되거나 Tool이 호출되지 않음

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

**증상:** 엉뚱한 Tool이 호출됨

**해결 방법:**
1. 관련 Tool끼리 그룹화
2. `defaultTools()` 대신 요청마다 `.tools(...)`로 상황별로 다른 Tool 세트 사용
3. Tool 수 줄이기 (비슷한 Tool 통합)

### 문제 3: Tool 호출 순서 문제

**증상:** 선행 Tool의 결과가 필요한데 먼저 호출되지 않음

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
    // FlightSearchAgent가 내부적으로 AirportCodeAgent를 호출하여 공항 코드 변환 (기록 부분은 생략)
    return flightSearchAgent.searchAndGroupByAirline(departure, arrival, date);
}
```

---

## 실습 과제

1. **(기본)** `McpController`의 `/api/mcp/**` 엔드포인트로 Tool을 LLM 없이 직접 호출해 보세요. 예: `curl "http://localhost:8080/api/mcp/airports/code?airportName=광주"`, `curl "http://localhost:8080/api/mcp/flights/search?departure=광주&arrival=제주&date=내일"`. Tool이 평범한 자바 메서드라는 점을 확인하세요.
2. **(기본)** `ToolResultCaptureTest`를 읽고, 같은 Tool을 서로 다른 인자로 두 번 `capture()`했을 때 `getAndClear()`가 두 건을 순서대로 돌려주는지 확인하는 테스트를 추가하세요. 옛 방식처럼 `Map<String, Object>`에 Tool 이름을 키로 저장했다면 어떤 결과가 나왔을지 설명하세요.
3. **(도전)** `McpController`로 Tool을 직접 호출하면 `capture()`는 실행되지만 `getAndClear()`를 부르는 곳이 없습니다. `config/ToolResultCaptureCleanupFilter`와 `ToolResultCaptureCleanupFilterTest`를 읽고, 이 필터가 없으면 Tomcat 스레드에 어떤 일이 생기는지 설명하세요. 같은 일을 `HandlerInterceptor`의 `afterCompletion`으로 구현한다면 필터와 무엇이 다른지(적용 범위, 실행 시점)도 비교하세요.
4. **(도전)** `POST /api/search/tool-calling`에 "내일 김포에서 제주, 광주에서 제주 둘 다 찾아줘"를 보내고 `trace`의 TOOL 단계를 확인하세요. LLM이 Tool을 두 번 부르더라도 `results`에는 마지막 검색 결과 하나만 담기는 이유를 `lastFlightResult()`로 설명하세요.

---

## 요약

이 단계에서 배운 핵심 내용을 정리합니다.

1. **@Tool 어노테이션**: AI 모델이 자연어를 분석하여 필요할 때 자동으로 호출하는 메서드를 만듭니다
2. **에이전트 위임 패턴**: Tool은 얇은 진입점이고, 에이전트가 실제 비즈니스 로직을 수행합니다
   - Spring Boot의 Controller → Service → Repository와 같은 계층 구조
3. **ToolResultCapture**: `capture(이름, 인자 요약, () -> 작업)`이 작업을 실행하며 호출 기록(`ToolCall`: 이름·인자·결과·실패 메시지·실행 시각)을 ThreadLocal에 순서대로 쌓고 결과를 그대로 돌려줍니다. 서비스 계층은 `getAndClear()`로 꺼내 실행 기록과 화면용 결과를 만들고, `ToolResultCaptureCleanupFilter`가 요청마다 남은 기록을 비웁니다
4. **Tool 등록**: 빌더의 `defaultTools()` 대신 Tool이 필요한 요청에서만 `.tools(...)`로 넘깁니다
5. **Tool Description 최적화**: AI 모델이 언제 어떤 Tool을 사용할지 정확히 판단하도록 description을 상세하게 작성합니다
6. **다중 Tool 조합**: 하나의 사용자 질문에 여러 Tool이 호출될 수 있습니다 (LLM이 여러 Tool을 한 번에 요청해도 Spring AI는 순서대로 실행합니다)

---

## 체크리스트

- [ ] Tool이 기능별로 분리됨 (FlightSearchTool, AirportInfoTool, AirlineInfoTool)
- [ ] 각 Tool이 단일 책임을 가짐
- [ ] Tool Description이 명확함
- [ ] Tool을 요청마다 `.tools(...)`로 넘기는 이유를 설명할 수 있음
- [ ] Tool이 에이전트에게 작업을 위임함
- [ ] 모든 Tool이 `return ToolResultCapture.capture(...)`로 호출을 기록함
- [ ] 다중 Tool이 정상적으로 동작함

---

## 다음 단계

[**Step 6: 항공편 검색 Tool 구현**](step-6-flight-search-tool.md)에서 실제 공공데이터 포털 API와 연동하는 MCP Tool을 구현합니다.

---

## 참고 자료

- [Spring AI Tool Calling 가이드](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [MCP (Model Context Protocol) 사양](https://modelcontextprotocol.io/)
