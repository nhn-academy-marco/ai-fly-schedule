# Step 5: 다중 Function Calling과 MCP Tool 패턴

Step 3에서는 `@Tool` 메서드 하나를 LLM에게 넘겨 보았고, Step 4에서는 Advisor로 Tool 실행 과정을 들여다보았습니다. 이번 Step에서는 이 프로젝트의 실제 Tool 7개(클래스 3개)가 어떤 원칙으로 나뉘어 있는지 살펴보고, Tool 호출을 기록해 화면에 보여 주는 `ToolResultCapture`를 이해합니다.

**이 Step에서 만들 것**: Tool 3개 클래스(`FlightSearchTool`, `AirportInfoTool`, `AirlineInfoTool`)의 구조를 익히고, Tool을 LLM 없이 직접 호출하거나 LLM에게 넘겨 호출 기록을 확인합니다.

```bash
curl -G "http://localhost:8080/api/mcp/airports/code" --data-urlencode "airportName=광주"
```

```json
{"success":true,"message":"조회 완료","data":{"airportName":"광주","airportCode":"NAARKJJ"}}
```

## 학습 목표

이 Step을 마치면 다음을 할 수 있습니다.

1. `@Tool`과 `@ToolParam`으로 LLM이 호출할 메서드를 정의하고, `@RequestMapping`과의 차이를 설명할 수 있습니다.
2. Tool은 얇게 두고 실제 일은 전문 에이전트에게 맡기는 위임(delegation) 구조를 코드에서 찾아 설명할 수 있습니다.
3. `ToolResultCapture`가 Tool 호출을 어떻게 기록하고, 서비스 계층이 그 기록을 어떻게 꺼내 쓰는지 설명할 수 있습니다.
4. Tool을 빌더의 `defaultTools()`가 아니라 요청마다 `.tools(...)`로 넘기는 이유를 설명할 수 있습니다.
5. LLM이 Tool을 고르기 쉽도록 description을 작성할 수 있습니다.

---

## 전체 흐름

```text
[사용자: "내일 광주에서 제주로 가는 항공편 알려줘"]
       ↓
[ChatClient → LLM: Tool 7개의 설명을 보고 searchFlightsByAirline을 고름]
       ↓
[FlightSearchTool.searchFlightsByAirline("광주", "제주", "2026-10-17")]
       ↓
[FlightSearchAgent → DateParserAgent → AirportCodeAgent → ApiClientService → GroupingAgent]
       ↓
[ToolResultCapture에 호출 기록 → 결과를 LLM에게 전달]
       ↓
[LLM이 한국어 답변 작성 → ToolCallingSearchService가 기록을 꺼내 실행 기록(trace)과 결과 표 작성]
```

사용자가 어떤 API를 부를지 정하지 않아도, LLM이 Tool 설명을 읽고 알맞은 Tool을 고릅니다. 이 Step에서는 그 Tool들이 어떻게 생겼는지에 집중합니다. LLM이 Tool을 고르는 에이전트 전체 구조는 Step 10에서 다룹니다.

---

## MCP Tool 패턴이란?

**MCP(Model Context Protocol)** 는 Anthropic이 제안한, LLM 애플리케이션과 외부 Tool·데이터를 잇는 표준 프로토콜입니다. 이 교재에서 **MCP Tool 패턴**은 LLM이 쓸 Tool을 "기능별로 나누고, 스스로 설명하게 하고, 상태 없이" 설계하는 방식을 가리킵니다.

> **주의**: 이 프로젝트의 "MCP Tool"은 MCP 서버가 아닙니다. Spring AI의 Tool Calling(`@Tool`)으로 만든 Tool이며, 나중에 MCP 서버로 노출할 수 있는 형태로 설계했다는 뜻입니다. 별도 서버 없이 같은 애플리케이션 안에서 `ChatClient`가 Tool을 호출합니다.

### @RequestMapping과 @Tool 비교

두 어노테이션은 "누가 호출하는가"가 다릅니다.

| 비교 항목 | `@RequestMapping` | `@Tool` |
|-----------|-------------------|---------|
| 호출 주체 | 브라우저·HTTP 클라이언트 | LLM |
| 입력 | HTTP 요청(URL, 본문) | LLM이 문장에서 뽑은 파라미터 |
| 출력 | HTTP 응답(JSON, HTML) | LLM에게 전달되는 데이터 |
| 등록 방법 | `@RestController`면 자동 등록 | `@Component` Bean을 `ChatClient`에 `.tools(...)`로 전달 |
| 호출 시점 | 사용자가 URL에 접속할 때 | LLM이 필요하다고 판단할 때 |
| 인터페이스 설명 | URL 경로 | `description` 속성 |

`@Component`만 붙였다고 Tool이 LLM에게 알려지지는 않습니다. `ChatClient`에 Tool 객체를 넘겨야 Spring AI가 `@Tool` 메서드를 찾아 "이런 Tool을 쓸 수 있다"고 LLM에게 알려 줍니다.

### Tool 설계 원칙

| 원칙 | 설명 | 이 프로젝트의 예 |
|------|------|------------------|
| 단일 책임 | Tool 하나는 기능 하나 | `getAirportCode`는 공항 코드만 돌려줌 |
| 자기 설명 | description과 `@ToolParam`만 읽고도 쓰임새를 알 수 있음 | "날짜, YYYY-MM-DD 형식 (예: 2026-03-25)" |
| 상태 비저장(stateless) | 호출마다 필요한 값을 모두 파라미터로 받음 | `searchFlightsByAirline(departure, arrival, date)` |
| 독립성 | 다른 Tool을 먼저 부르지 않아도 동작 | 항공편 검색이 내부에서 공항 코드를 직접 변환 |

상태 비저장이 왜 중요한지는 반대 경우를 떠올리면 알 수 있습니다. `setDeparture()`를 먼저 부르고 `search(arrival)`을 부르게 만들면, LLM이 순서를 지키지 않거나 다른 요청이 끼어들 때 결과가 틀어집니다.

---

## Step 5-1: Tool 구성

`src/main/java/net/java21/flyschedule/mcp` 패키지에 Tool 클래스 3개, `@Tool` 메서드 7개가 있습니다.

```text
mcp
├── FlightSearchTool
│   ├── searchFlightsByAirline()      항공사별로 묶은 항공편
│   ├── searchFlightsAfterTime()      특정 시각 이후 출발
│   └── searchFlightsByPriceRange()   가격 범위
├── AirportInfoTool
│   ├── getAirportList()              전체 공항 목록
│   └── getAirportCode()              공항 이름 → 공항 코드
├── AirlineInfoTool
│   ├── getAirlineList()              전체 항공사 목록
│   └── getAirlineId()                항공사 이름 → 항공사 ID
└── ToolResultCapture                 Tool 호출 기록 (Step 5-3)
```

### 확인하기

Tool은 평범한 Spring Bean이므로 LLM 없이도 호출할 수 있습니다. `McpController`(`/api/mcp/**`)가 Tool 메서드를 그대로 HTTP로 열어 둡니다. 애플리케이션을 실행(`./mvnw spring-boot:run`)한 뒤 호출해 보세요.

```bash
curl -G "http://localhost:8080/api/mcp/airports/code" --data-urlencode "airportName=광주"
curl -G "http://localhost:8080/api/mcp/airports/code" --data-urlencode "airportName=서울"
```

첫 번째는 `"airportCode":"NAARKJJ"`를 돌려줍니다. 두 번째는 `AirportCodeAgent`가 던진 `IllegalArgumentException`을 `GlobalExceptionHandler`가 받아 HTTP 400과 함께 돌려줍니다.

```json
{"success":false,"message":"알 수 없는 공항입니다: 서울","data":null}
```

공항 코드 조회는 공공데이터 포털을 부르지 않으므로 서비스 키 없이도 동작합니다.

---

## Step 5-2: 에이전트 위임(Delegation) 구조

### 왜 필요한가

Tool 안에 API 호출, 응답 파싱, 날짜 변환을 모두 넣으면 Tool이 길어지고, 같은 로직을 다른 Tool이나 Step 9의 Coordinator가 다시 쓸 수 없습니다. 그래서 Tool은 LLM이 들어오는 입구 역할만 하고, 실제 일은 `service/agent` 패키지의 **전문 에이전트**에게 맡깁니다.

```text
Spring MVC:  @RestController → Service    → Repository
이 프로젝트: @Tool           → 전문 에이전트 → ApiClientService
             (입구)          (실제 로직)    (외부 API 호출)
```

> **주의**: 이 교재에서 **전문 에이전트**는 한 가지 일만 하는 결정적(deterministic) 클래스를 뜻합니다. 이름에 Agent가 붙어 있지만 LLM을 쓰지 않으며, 같은 입력에는 언제나 같은 결과를 냅니다. 자세한 내용은 Step 8에서 다룹니다.

### 코드 보기

`src/main/java/net/java21/flyschedule/mcp/AirportInfoTool.java` (Javadoc 생략)

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class AirportInfoTool {

    private final AirportListAgent airportListAgent;

    @Tool(
            description = "전체 공항 목록을 조회합니다. " +
                    "국내 모든 공항의 코드와 이름을 반환합니다."
    )
    public List<AirportInfoResponse> getAirportList() {
        log.info("MCP Tool 호출: getAirportList()");

        return ToolResultCapture.capture("getAirportList", "전체", airportListAgent::getAirportList);
    }

    @Tool(
            description = "공항 이름으로 공항 코드를 조회합니다. " +
                    "공항 이름을 입력하면 공항 코드(예: NAARKJJ)를 반환합니다. " +
                    "지원하는 공항: 김포, 인천, 김해, 광주, 제주, 대구, 청주, 양양, 울산, 여수, 사천, 포항, 무안, 군산, 원주"
    )
    public String getAirportCode(
            @ToolParam(description = "공항 이름 (예: 광주, 김포, 제주)") String airportName) {

        log.info("MCP Tool 호출: getAirportCode(airportName={})", airportName);

        return ToolResultCapture.capture("getAirportCode", airportName, () -> airportListAgent.getAirportCode(airportName));
    }
}
```

**코드 읽기**

1. `@Component`: Spring Bean으로 등록합니다. 이 Bean을 `ToolCallingSearchService`가 주입받아 `.tools(...)`로 LLM에게 넘깁니다.
2. `private final AirportListAgent airportListAgent`: Tool은 API를 직접 부르지 않고 전문 에이전트를 주입받습니다.
3. `@Tool(description = ...)`: LLM은 이 문장을 읽고 언제 이 Tool을 쓸지 정합니다.
4. `@ToolParam(description = ...)`: 파라미터의 의미와 예시를 LLM에게 알려 줍니다.
5. `return ToolResultCapture.capture(...)`: 에이전트에게 일을 맡기고, 그 호출을 기록한 뒤 결과를 그대로 돌려줍니다(Step 5-3).

`AirlineInfoTool`도 같은 모양으로 `AirlineListAgent`에게 맡깁니다. `AirportCodeAgent`처럼 한 번 만든 전문 에이전트는 `FlightSearchAgent`(항공편 검색)와 `AirportListAgent`(공항 정보) 양쪽에서 재사용됩니다.

```text
AirportInfoTool → AirportListAgent ─┬→ ApiClientService.getAirportList()
                                    └→ AirportCodeAgent.getAirportCode()
FlightSearchTool → FlightSearchAgent ─┬→ DateParserAgent
                                      ├→ AirportCodeAgent
                                      ├→ ApiClientService.getFlightSchedule()
                                      └→ GroupingAgent (+ TimeFilterAgent, PriceFilterAgent)
```

### 확인하기

```bash
curl -G "http://localhost:8080/api/mcp/airlines/id" --data-urlencode "airlineName=아시아나"
```

서비스 키(`DATA_GO_KR_SERVICE_KEY`)가 설정되어 있으면 `AirlineListAgent`가 항공사 목록을 받아 이름에 "아시아나"가 들어간 항공사의 ID를 돌려줍니다. Tool 메서드가 에이전트 호출 한 줄뿐이라는 점을 코드와 비교해 확인해 보세요.

---

## Step 5-3: ToolResultCapture — Tool 호출 기록하기

### 왜 필요한가

LLM이 Tool을 호출하면 그 결과는 LLM에게 전달되고, `call().content()`가 돌려주는 것은 LLM이 쓴 자연어 답변뿐입니다. 그런데 화면에는 항공편 목록을 표로 보여 주고, LLM이 어떤 Tool을 어떤 인자로 불렀는지도 실행 기록(trace)으로 보여 줘야 합니다. 그래서 Tool 결과를 LLM과 서비스 계층 두 곳에서 쓸 방법이 필요합니다.

```text
[Tool 실행 결과]
     ├→ 1. LLM에게 전달          → 자연어 답변 (SearchResponse.message)
     └→ 2. ToolResultCapture에 기록 → 항공편 표 (SearchResponse.results) + TOOL 단계 (trace)
```

**ToolResultCapture**는 Tool 호출 한 건 한 건을 `ThreadLocal`에 순서대로 쌓아 두는 유틸리티입니다. `ThreadLocal`은 스레드마다 따로 값을 보관하는 Java 클래스이므로, 동시에 들어온 요청의 기록이 섞이지 않습니다.

### 코드 보기

`src/main/java/net/java21/flyschedule/mcp/ToolResultCapture.java` (클래스 Javadoc 생략)

```java
public final class ToolResultCapture {

    /**
     * Tool 호출 한 건
     *
     * @param result      Tool이 돌려준 값 (실패했으면 null)
     * @param error       실패했을 때의 메시지 (성공했으면 null)
     * @param startNanos  실행 시작 시각 ({@link System#nanoTime()})
     * @param endNanos    실행 종료 시각
     */
    public record ToolCall(String toolName, String arguments, Object result, String error,
                           long startNanos, long endNanos) {

        public boolean failed() {
            return error != null;
        }
    }

    private static final ThreadLocal<List<ToolCall>> CALLS = ThreadLocal.withInitial(ArrayList::new);

    private ToolResultCapture() {
    }

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

    public static List<ToolCall> getAndClear() {
        List<ToolCall> calls = List.copyOf(CALLS.get());
        CALLS.remove();
        return calls;
    }
}
```

**코드 읽기**

1. `record ToolCall(...)`: 호출 한 건의 Tool 이름, 인자 요약, 결과(또는 실패 메시지), 시작·종료 시각을 담습니다.
2. `List<ToolCall>`: Tool 이름을 키로 쓰는 `Map`이 아니라 `List`입니다. LLM이 같은 Tool을 두 번(예: 김포→제주, 광주→제주) 불러도 두 건이 모두 순서대로 남습니다.
3. `capture(이름, 인자 요약, () -> 작업)`: 값이 아니라 작업(`Supplier`)을 받아 직접 실행합니다. 그래서 실제 실행 구간을 잴 수 있고, 예외가 나면 실패로 기록할 수 있습니다.
4. `throw e`: 예외를 삼키지 않고 다시 던집니다. Spring AI가 그 오류 메시지를 LLM에게 전달하므로, LLM은 "알 수 없는 공항"을 보고 다른 이름으로 다시 시도하거나 사용자에게 알릴 수 있습니다.
5. `getAndClear()`: 기록을 복사해 돌려주고 `CALLS.remove()`로 비웁니다. Tomcat은 스레드를 재사용하므로 비우지 않으면 다음 요청에 기록이 섞입니다.

모든 Tool 메서드는 `return ToolResultCapture.capture(...)` 한 줄로 끝납니다. 기록과 반환이 한 문장이므로 기록을 빠뜨리거나 다른 값을 기록하는 실수가 생기지 않습니다.

### 서비스 계층에서 꺼내 쓰기

`src/main/java/net/java21/flyschedule/service/toolcalling/ToolCallingSearchService.java` (`search()` 일부)

```java
ToolResultCapture.getAndClear(); // 이전 요청의 기록이 남아 있지 않도록 비운다

try {
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
    recordToolCalls(calls, trace);
    // ... 생략 (calls가 비었으면 실패 응답, 아니면 lastFlightResult(calls)로 결과 표 작성)
```

**코드 읽기**

1. LLM을 부르기 전에 `getAndClear()`로 남은 기록을 비웁니다.
2. `call()`이 끝나면 그 안에서 실행된 Tool 호출이 모두 기록되어 있습니다. 이를 꺼내 LLM 단계 하나와 Tool마다 TOOL 단계 하나씩을 실행 기록에 남깁니다. 실패한 호출은 제목이 "Tool 실행 실패"가 됩니다.
3. `lastFlightResult(calls)`는 목록을 뒤에서부터 훑어 결과가 `Map`인 호출, 곧 마지막 항공편 검색을 골라 결과 표로 만듭니다. 공항 코드 조회(`String`)나 실패한 호출(`result`가 `null`)은 건너뜁니다.
4. 예외가 나면 `catch` 블록에서도 `getAndClear()`를 불러 그때까지 실행된 Tool을 기록하고 `ThreadLocal`을 비웁니다.

### 왜 ThreadLocal로 충분한가

블로킹 방식의 `call()`에서는 Spring AI가 Tool을 호출한 스레드에서 차례로 실행합니다. Tool 실행과 `getAndClear()`가 같은 요청 스레드에서 일어나므로 `ThreadLocal`로 결과를 주고받을 수 있습니다.

| 구분 | 내용 |
|------|------|
| 장점 | Tool 메서드의 시그니처를 바꾸지 않고 결과를 서비스 계층으로 전달 |
| 장점 | 요청 스레드마다 저장소가 따로 있어 요청끼리 섞이지 않음 |
| 한계 | `stream()`은 Tool을 다른 스레드에서 실행하므로 이 방식을 쓸 수 없음 |
| 한계 | 누군가 반드시 비워야 함. LLM 없이 Tool을 부르는 `McpController` 경로는 꺼내 가는 곳이 없으므로, `config/ToolResultCaptureCleanupFilter`(`OncePerRequestFilter`)가 요청이 끝날 때마다 `getAndClear()`를 부름 |

### 확인하기

```bash
./mvnw test -Dtest='ToolResultCaptureTest,ToolResultCaptureCleanupFilterTest'
```

`ToolResultCaptureTest`의 "작업 결과를 그대로 돌려주고, 호출 순서대로 기록한다", "작업이 실패하면 실패로 기록하고 예외를 그대로 다시 던진다", "다른 스레드의 기록은 보이지 않는다" 테스트가 통과하는지 확인합니다.

---

## Step 5-4: Tool을 ChatClient에 넘기는 방법

### 왜 필요한가

Step 3-2에서 본 것처럼 Tool을 넘기는 곳은 두 군데입니다. 어디에 두느냐에 따라 "어떤 LLM 호출이 Tool을 쓸 수 있는가"가 달라집니다.

| 방법 | 코드 위치 | 효과 |
|------|-----------|------|
| 빌더에 기본 등록 | `ChatClient.builder(...).defaultTools(...)` | 그 `ChatClient`의 모든 요청에 Tool 7개가 붙음 |
| 요청마다 전달 (이 프로젝트) | `chatClient.prompt()...tools(...)` | Tool이 필요한 요청에만 붙음 |

이 프로젝트의 `ChatClientConfig`는 `SimpleLoggerAdvisor`만 기본으로 등록하고 Tool은 등록하지 않습니다. Tool은 `ToolCallingSearchService`만 넘깁니다(Step 5-3의 코드).

같은 `ChatClient`로 검색 조건 추출(`SearchCriteriaExtractor`), 작업 계획(`FlightSearchPlanner`), 결과 요약(`ResultSynthesizer`)도 합니다. 이 호출들은 Tool이 필요 없습니다. Tool을 기본 등록하면 검색 조건만 뽑아야 할 LLM이 직접 항공편을 검색할 수 있고, Tool 7개의 설명만큼 프롬프트가 길어져 작은 모델(qwen2.5:3b)의 응답이 느려집니다.

> **팁**: 요청마다 넘기면 상황에 따라 다른 Tool 묶음을 줄 수도 있습니다. 예를 들어 항공편 Tool만 넘기면 LLM이 고를 수 있는 선택지가 줄어 판단이 안정됩니다.

---

## Step 5-5: 여러 Tool 조합

한 질문에 Tool이 여러 번 불릴 수 있습니다. 어떤 Tool을 몇 번 부를지는 LLM이 정합니다.

**순차 호출**: 앞 Tool의 결과를 보고 다음 Tool을 고릅니다.

```text
사용자: "광주 공항 코드가 뭐야? 그리고 내일 광주에서 제주 항공편 알려줘"
  ↓
LLM: 1) getAirportCode("광주")                            → "NAARKJJ"
LLM: 2) searchFlightsByAirline("광주", "제주", "2026-10-17") → 항공사별 항공편
  ↓
LLM: "광주 공항 코드는 NAARKJJ이고, 내일 항공편은 ..."
```

**한 응답에서 여러 Tool 요청**: 모델에 따라 한 번의 응답에서 Tool 여러 개를 함께 요청하기도 합니다. 요청은 한 번에 오지만 Spring AI는 Tool을 하나씩 차례로 실행합니다.

```text
사용자: "공항 목록과 항공사 목록 알려줘"
  ↓
LLM: getAirportList(), getAirlineList()를 함께 요청
  ↓
Spring AI: getAirportList() 실행 → getAirlineList() 실행 → 두 결과를 LLM에게 전달
```

### 확인하기

```bash
curl -X POST http://localhost:8080/api/search/tool-calling \
  -H "Content-Type: application/json" \
  -d '{"message": "광주 공항 코드가 뭐야? 그리고 내일 광주에서 제주 항공편 알려줘", "model": "ollama"}'
```

응답의 `trace`에서 `kind`가 `TOOL`인 단계를 찾아 `actor`(Tool 이름)와 `detail`(인자 요약과 결과)을 확인합니다. 모델에 따라 공항 코드 조회를 건너뛰고 바로 항공편을 검색하기도 합니다. 다음에 무엇을 할지 LLM이 정하기 때문입니다.

---

## Step 5-6: Tool description 작성법

### 왜 필요한가

LLM은 Tool의 코드를 보지 못합니다. 이름, description, 파라미터 설명만 보고 Tool을 고르고 인자를 채웁니다. 설명이 모호하면 엉뚱한 Tool을 부르거나 인자를 잘못된 형식으로 넘깁니다.

| 원칙 | 나쁜 예 | 좋은 예 |
|------|---------|---------|
| 무엇을 하는지 | "검색" | "항공편을 검색하여 항공사별로 그룹핑하여 반환합니다" |
| 파라미터 형식 | "날짜" | "날짜, YYYY-MM-DD 형식 (예: 2026-03-25)" |
| 반환값 | (없음) | "항공사별로 정리된 항공편 목록을 제공합니다" |
| 제한 사항 | (없음) | "항공사별 최대 3편만 반환합니다" |
| 지원 범위 | (없음) | "지원하는 공항: 김포, 인천, 김해, ..." |

### 코드 보기

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
        @ToolParam(description = "날짜, YYYY-MM-DD 형식 (예: 2026-03-25)") String date) {
    // ... 생략 (Step 6-5)
}
```

**코드 읽기**

1. description에 반환 형태("항공사별로 정리된")와 제한("최대 3편")을 적어, LLM이 결과를 보고 "항공편이 3편뿐"이라고 잘못 요약하지 않게 합니다.
2. 날짜 파라미터는 `YYYY-MM-DD` 형식과 예시를 하나로 못 박았습니다. 작은 모델은 "내일"을 그대로 넘기거나 "10월 17일"처럼 임의의 형식으로 넘기기도 합니다. 형식을 정해 두면 LLM이 시스템 프롬프트의 "오늘 날짜"를 보고 날짜를 계산해 넘깁니다. "내일", "모레"가 넘어와도 `DateParserAgent`가 받아 줍니다.

> **생각해 보기**: Tool 하나가 다른 Tool의 결과를 꼭 먼저 받아야 한다면(예: 공항 코드를 먼저 조회해야 검색 가능), LLM이 순서를 틀릴 때마다 실패합니다. 이 프로젝트는 항공편 검색 Tool이 공항 이름을 직접 받아 내부에서 코드로 바꾸도록 해 이 문제를 피했습니다. 다른 방법은 무엇이 있을까요?

---

## 일반적인 문제 해결

### 문제 1: LLM이 Tool을 부르지 않고 답을 지어냄

응답이 `success: false`이고 `message`가 LLM의 답변 그대로이거나 "LLM이 Tool을 호출하지 않았습니다."입니다.

- description에 "무엇을, 언제" 하는지 구체적으로 적었는지 확인합니다.
- 시스템 프롬프트에 "항공편 정보는 반드시 Tool을 호출해서 얻으라"는 지시가 있는지 확인합니다(`ToolCallingSearchService.systemPrompt()`).
- 작은 모델에서 자주 생깁니다. 같은 요청을 `"model": "gemini"`로 보내 비교해 봅니다.

### 문제 2: 엉뚱한 Tool이 호출됨

- 비슷한 Tool의 description이 서로 구분되는지 확인합니다. 예: 시간 조건이 있으면 `searchFlightsAfterTime`, 가격 조건이 있으면 `searchFlightsByPriceRange`.
- 그 요청에 필요한 Tool만 `.tools(...)`로 넘겨 선택지를 줄입니다.

### 문제 3: Tool 호출 기록이 다음 요청에 섞임

`ToolResultCapture.getAndClear()`를 부르는 곳이 없는 경로에서 생깁니다. `ToolResultCaptureCleanupFilter`가 Bean으로 등록되어 있는지(`@Component`) 확인합니다.

---

## 실습 과제

1. **(기본) Tool 직접 호출하기**: `McpController`의 엔드포인트로 공항 코드 조회와 항공편 검색을 LLM 없이 호출하고, 응답 형식(`success`, `message`, `data`)을 확인하세요.
   - 힌트: `src/main/java/net/java21/flyschedule/controller/McpController.java`의 `@GetMapping` 경로. 예: `curl -G "http://localhost:8080/api/mcp/flights/search" --data-urlencode "departure=광주" --data-urlencode "arrival=제주" --data-urlencode "date=내일"`
   - 완료 기준: 공항 코드 조회는 `"airportCode":"NAARKJJ"`, 모르는 공항은 HTTP 400과 `"success":false`가 나옵니다. 항공편 검색은 항공사마다 최대 3편이 담긴 `data`가 나옵니다(서비스 키 필요).

2. **(기본) 같은 Tool을 두 번 기록하기**: 같은 Tool 이름을 서로 다른 인자로 두 번 `capture()`했을 때 `getAndClear()`가 두 건을 순서대로 돌려주는지 확인하는 테스트를 추가하세요.
   - 힌트: `src/test/java/net/java21/flyschedule/mcp/ToolResultCaptureTest.java`의 `capturesInOrder()`
   - 완료 기준: `./mvnw test -Dtest=ToolResultCaptureTest`가 통과하고, `Map<String, Object>`에 Tool 이름을 키로 저장했다면 어떤 결과가 나왔을지 한 문장으로 설명할 수 있습니다.

3. **(도전) 필터와 인터셉터 비교하기**: `ToolResultCaptureCleanupFilter`가 없으면 `McpController` 경로에서 Tomcat 스레드에 어떤 일이 생기는지 설명하고, 같은 일을 `HandlerInterceptor.afterCompletion()`으로 구현할 때 무엇이 다른지(적용 범위, 실행 시점) 비교하세요.
   - 힌트: `src/main/java/net/java21/flyschedule/config/ToolResultCaptureCleanupFilter.java`, `src/test/java/net/java21/flyschedule/config/ToolResultCaptureCleanupFilterTest.java`
   - 완료 기준: 필터 없이 같은 스레드로 요청을 두 번 처리하는 상황에서 기록이 어떻게 쌓이는지 설명하고, 필터와 인터셉터의 차이를 두 가지 이상 들 수 있습니다.

4. **(도전) 두 번 검색한 결과 확인하기**: `POST /api/search/tool-calling`에 "내일 김포에서 제주, 광주에서 제주 둘 다 찾아줘"를 보내고 `trace`의 TOOL 단계를 확인하세요.
   - 힌트: `ToolCallingSearchService.lastFlightResult()`
   - 완료 기준: TOOL 단계가 두 개 이상이어도 `results`에는 마지막 검색 결과 하나만 담기는 이유를 코드로 설명할 수 있습니다.

---

## 요약

- `@Tool`은 LLM이 호출하는 메서드이고, `@Component` Bean을 `ChatClient`에 `.tools(...)`로 넘겨야 LLM에게 알려집니다.
- Tool은 얇은 입구이고, 실제 일은 LLM을 쓰지 않는 전문 에이전트가 합니다.
- `ToolResultCapture.capture(이름, 인자 요약, () -> 작업)`이 작업을 실행하며 호출을 순서대로 기록하고, 서비스 계층은 `getAndClear()`로 꺼내 실행 기록과 결과 표를 만듭니다.
- Tool은 필요한 요청에서만 넘겨, 조건 추출·계획·요약 같은 LLM 호출이 Tool을 부르지 않게 합니다.
- description은 LLM이 Tool을 고르는 유일한 근거이므로 형식·예시·제한을 구체적으로 적습니다.

Tool의 뼈대를 익혔으니, 다음 Step에서는 그중 가장 중요한 항공편 검색 Tool이 공공데이터 포털을 어떻게 부르는지 끝까지 따라가 봅니다.

---

## 체크리스트

- [ ] `@Tool`과 `@ToolParam`의 역할, `@RequestMapping`과의 차이를 설명할 수 있습니다. (목표 1)
- [ ] `AirportInfoTool`에서 전문 에이전트에게 일을 맡기는 줄을 찾고, 위임 구조의 장점을 설명할 수 있습니다. (목표 2)
- [ ] `capture()`가 값이 아니라 `Supplier`를 받는 이유와, `getAndClear()`를 꼭 불러야 하는 이유를 설명할 수 있습니다. (목표 3)
- [ ] Tool을 `defaultTools()`가 아니라 `.tools(...)`로 넘기는 이유를 설명할 수 있습니다. (목표 4)
- [ ] `FlightSearchTool`의 날짜 파라미터 설명을 `YYYY-MM-DD`로 정한 이유를 설명할 수 있습니다. (목표 5)

---

## 다음 단계

[Step 6: 항공편 검색 Tool 구현](step-6-flight-search-tool.md)에서 `FlightSearchTool`이 `FlightSearchAgent`와 `ApiClientService`를 거쳐 공공데이터 포털 API를 호출하는 과정을 구현합니다.

---

## 참고 자료

- [Spring AI Tool Calling 가이드](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [MCP (Model Context Protocol) 사양](https://modelcontextprotocol.io/)
