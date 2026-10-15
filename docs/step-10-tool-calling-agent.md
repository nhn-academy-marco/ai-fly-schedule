# Step 10: Tool Calling 에이전트 — LLM이 제어하는 검색

## 학습 목표

이 단계를 마치면 다음을 할 수 있습니다.

1. **Tool Calling 에이전트**가 무엇인지, Step 9의 **Coordinator 패턴**과 무엇이 다른지("다음 단계를 누가 결정하는가") 설명할 수 있다
2. `ChatClient`에 요청마다 `.tools(...)`로 Tool을 넘기고, Spring AI가 LLM의 요청대로 Tool을 실행하는 과정을 이해한다
3. **ToolResultCapture**로 LLM이 호출한 Tool의 이름·인자·결과·실행 구간·실패 여부를 기록하고, 그것으로 실행 기록(TOOL 단계)과 화면용 결과를 만들 수 있다
4. LLM이 Tool을 부르지 않거나 엉뚱한 Tool·인자를 고르는 **실패 모드**를 알고 대응할 수 있다
5. 실제 LLM 없이 **가짜 LLM**으로 Tool Calling 흐름을 테스트할 수 있다

---

## 전체 흐름

```text
사용자: "내일 광주에서 제주로 가는 항공편 알려줘"
  ↓
[SearchController]  POST /api/search/tool-calling
  ↓
[ToolCallingSearchService.search()]
  ↓ system(규칙 + 오늘 날짜) / user(질문) / tools(Tool 3개)
[ChatClient.call()]
  ↓
  [LLM] "searchFlightsByAirline(광주, 제주, 2026-10-16)를 호출해 줘"
    ↓ Spring AI(ToolCallingManager)가 같은 스레드에서 Tool 실행
  [FlightSearchTool] → [FlightSearchAgent] → 공공데이터 포털 API
    ↓ ToolResultCapture.capture(...)로 호출 기록 → 결과를 LLM에게 전달
  [LLM] 결과를 읽고 답변 작성 (필요하면 다른 Tool을 더 호출)
  ↓
[ToolResultCapture.getAndClear()] → LLM 단계 + TOOL 단계 기록, 마지막 항공편 결과
  ↓
SearchResponse { pattern: "tool-calling", message: LLM 답변, results, trace }
```

> 💡 **핵심 개념**: Step 9의 Coordinator 패턴에서는 **코드**가 "날짜 변환 → 공항 코드 변환 → API 호출 → 필터 → 그룹핑" 순서를 정했습니다.
> 이번 단계의 Tool Calling 에이전트에서는 코드가 **질문과 사용할 수 있는 Tool 목록만** 넘기고,
> 어떤 Tool을 어떤 인자로 몇 번 부를지는 **LLM이** 결정합니다.

---

## Tool Calling 에이전트란?

### 워크플로와 에이전트

Anthropic의 글 "Building effective agents"는 LLM을 쓰는 시스템을 두 가지로 나눕니다.

| 구분 | 정의 | 이 교재 |
|---|---|---|
| **워크플로(workflow)** | LLM과 Tool이 **미리 정해 둔 코드 경로**를 따라 실행됨 | Step 9 Coordinator 패턴 |
| **에이전트(agent)** | LLM이 **스스로 과정과 Tool 사용을 결정**하며 작업을 끝냄 | Step 10 Tool Calling 에이전트 |

Tool Calling 에이전트는 다음 세 가지를 LLM에게 맡깁니다.

- **어떤 Tool을** 부를지 (`searchFlightsByAirline`? `searchFlightsAfterTime`? 아니면 `getAirportCode` 먼저?)
- **어떤 인자로** 부를지 ("내일" → `"2026-10-16"`, "오후 2시" → `"14:00"`)
- **몇 번** 부를지 (한 번으로 끝낼지, 결과를 보고 다른 Tool을 더 부를지)

### Spring AI가 Tool을 실행하는 방식

LLM은 Tool을 직접 실행하지 못합니다. LLM이 하는 일은 "이 Tool을 이 인자로 불러 달라"는 **요청을 응답에 담는 것**뿐입니다.
실제 실행은 Spring AI가 맡습니다.

```text
ChatClient.call()
  ↓
[ChatModel] LLM 호출 ① → 응답: tool_call(searchFlightsByAirline, {"departure":"광주", ...})
  ↓
[ToolCallingManager] @Tool 메서드를 찾아 실행 → 결과를 JSON으로 바꿔 대화에 추가
  ↓
[ChatModel] LLM 호출 ② → 응답: tool_call이 없으면 최종 답변, 있으면 위 과정을 반복
  ↓
content() → 최종 답변 문자열
```

이 반복은 `call()` 한 번 안에서 일어나고, `call()`을 부른 **같은 스레드**에서 순서대로 실행됩니다.
그래서 Tool이 두 번 호출되면 실제 LLM HTTP 요청은 최소 세 번(호출 요청 2번 + 최종 답변 1번) 오갑니다.
뒤에서 볼 `ToolResultCapture`가 `ThreadLocal`을 쓸 수 있는 것도 이 "같은 스레드" 성질 덕분입니다.

### 단일 에이전트이지 A2A가 아니다

이름이 비슷해 헷갈리기 쉬운 개념을 정리합니다.

| 개념 | 무엇과 무엇의 통신인가 | 이 프로젝트 |
|---|---|---|
| Tool Calling 에이전트 | LLM 하나 ↔ 같은 JVM 안의 `@Tool` 메서드 | `ToolCallingSearchService` |
| MCP | 에이전트 ↔ Tool·데이터(표준 프로토콜) | `mcp/*`는 MCP 서버로 노출할 수 있게 설계한 `@Tool` |
| A2A(Agent2Agent) 프로토콜 | 독립 실행되는 에이전트 ↔ 에이전트(HTTP, JSON-RPC) | **구현 없음** (Step 11 심화 학습에서 개념만 소개) |

`ToolCallingSearchService`는 **LLM 하나가 Tool을 쓰는 단일 에이전트**입니다. 여러 에이전트가 서로 메시지를 주고받는 A2A 구조가 아닙니다.

### Step 9 Coordinator와 무엇이 다른가

같은 문장 "내일 광주에서 제주 오후 2시 이후"를 두 패턴으로 처리하면 다음과 같습니다.

```text
Coordinator (Step 9) — 코드가 다음 단계를 결정
[SearchCriteriaExtractor] → [DateParserAgent] → [AirportCodeAgent ×2]
  → [ApiClientService] → [TimeFilterAgent] → [GroupingAgent]
  순서는 FlightSearchCoordinator 코드에 고정. "이후"를 보고 TimeFilterAgent를 부르는 것도 코드의 if 문.

Tool Calling 에이전트 (Step 10) — LLM이 다음 단계를 결정
[LLM] "시간 조건이 있으니 searchFlightsAfterTime을 부르자"
  → [FlightSearchTool.searchFlightsAfterTime(광주, 제주, 2026-10-16, 14:00)]
  → [LLM] 결과를 보고 답변 작성
  어떤 Tool을 고를지는 Tool 설명(description)을 읽은 LLM의 판단.
```

---

## Step 10-1: ToolCallingSearchService — 요청마다 Tool 넘기기

### 핵심 코드

`src/main/java/net/java21/flyschedule/service/toolcalling/ToolCallingSearchService.java`

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolCallingSearchService {

    public static final String PATTERN = "tool-calling";

    private final ChatClientProvider chatClientProvider;
    private final FlightSearchTool flightSearchTool;
    private final AirportInfoTool airportInfoTool;
    private final AirlineInfoTool airlineInfoTool;

    public SearchResponse search(String message, String model) {
        ExecutionTrace trace = new ExecutionTrace();
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
            // ... 생략 (Step 10-3에서 설명)
        } catch (IllegalArgumentException e) {
            ToolResultCapture.getAndClear();
            return SearchResponse.fail(PATTERN, e.getMessage(), trace);
        } catch (Exception e) {
            log.error("Tool Calling 검색 실패 (model={})", model, e);
            recordToolCalls(ToolResultCapture.getAndClear(), trace); // 실패 전까지 실행된 Tool도 남기고, ThreadLocal을 비운다
            return SearchResponse.fail(PATTERN, ChatClientProvider.describeError(e), trace);
        }
    }
}
```

코드에는 "어떤 Tool을 부를지"를 정하는 `if` 문이 하나도 없습니다. 그 판단은 모두 LLM에게 넘어갔습니다.

### 시스템 프롬프트: 오늘 날짜와 규칙

```java
private String systemPrompt() {
    return """
            당신은 국내선 항공편 검색 도우미입니다. 반드시 한국어로 답변하세요.
            항공편 정보는 반드시 Tool을 호출해서 얻고, Tool 결과에 없는 내용은 지어내지 마세요.
            오늘 날짜: %s
            규칙:
            - 날짜는 "2026-06-25" 형식으로 Tool에 전달합니다.
            - 시간은 24시간 형식("14:00")으로 전달합니다.
            - 공항은 한글 이름 그대로 전달합니다. (예: "광주", "제주")
            - 답변은 항공편 수와 가장 저렴한 항공편을 중심으로 3~4문장으로 요약합니다.
            """.formatted(LocalDate.now());
}
```

| 줄 | 왜 필요한가 |
|---|---|
| "반드시 Tool을 호출해서 얻고 … 지어내지 마세요" | LLM이 Tool 없이 그럴듯한 항공편을 만들어 내는 **환각**을 막습니다 |
| `오늘 날짜: %s` | LLM은 오늘이 며칠인지 모릅니다. 날짜가 없으면 "내일"을 학습 데이터 시점 기준으로 계산합니다 |
| 날짜·시간·공항 형식 규칙 | Tool 인자 형식을 하나로 맞춰, Tool 안의 `DateParserAgent`·`AirportCodeAgent`가 처리할 수 있게 합니다 |
| 답변 길이 | 작은 모델은 길게 쓰다가 엉뚱한 내용을 덧붙이기 쉽습니다. 출력 토큰도 줄어 응답이 빨라집니다 |

> 💡 시스템 프롬프트는 **LLM이 Tool을 고르는 기준**입니다. Coordinator에서 코드의 `if` 문이 하던 역할을 이제 자연어 규칙이 대신합니다.

### 왜 Tool을 `defaultTools`가 아니라 요청마다 넘기는가

`ChatClientConfig`는 Tool을 기본값으로 등록하지 않습니다.

`src/main/java/net/java21/flyschedule/config/ChatClientConfig.java`

```java
@Bean
public ChatClient ollamaChatClient(@Qualifier("ollamaChatModel") ChatModel ollamaChatModel) {
    return ChatClient.builder(ollamaChatModel)
            .defaultAdvisors(new SimpleLoggerAdvisor())
            .build();   // defaultTools(...)를 쓰지 않는다
}
```

같은 `ChatClient`를 다른 곳에서도 씁니다.

| 사용하는 곳 | LLM에게 시키는 일 | Tool이 필요한가 |
|---|---|---|
| `SearchCriteriaExtractor` (Step 9) | 문장 → `SearchCriteria` 추출 | 아니요 |
| `FlightSearchPlanner` (Step 11) | 요청 → 검색 작업 계획 | 아니요 |
| `ResultSynthesizer` (Step 11) | 결과 요약 → 추천 문장 | 아니요 |
| `ToolCallingSearchService` (Step 10) | Tool을 골라 검색하고 답변 | **예** |

`defaultTools`로 등록하면 위의 모든 호출에 Tool 목록이 따라갑니다. 그러면

- 파라미터만 뽑으면 되는 호출에서 LLM이 `searchFlightsByAirline`을 불러 버려 **불필요한 API 호출**이 생기고,
- Tool 7개의 이름·설명·JSON 스키마가 매번 프롬프트에 들어가 **입력 토큰과 지연이 늘어나며**,
- 작은 모델은 Structured Output(JSON) 대신 Tool 호출로 답해 **파싱에 실패**하기도 합니다.

그래서 Tool은 **필요한 곳에서만 요청 단위로** `.tools(...)`에 넘깁니다.

### 넘기는 Tool

`.tools(flightSearchTool, airportInfoTool, airlineInfoTool)`에 넘긴 세 객체에는 `@Tool` 메서드가 모두 7개 있습니다(Step 6, Step 7).

| 클래스 | `@Tool` 메서드 |
|---|---|
| `FlightSearchTool` | `searchFlightsByAirline`, `searchFlightsAfterTime`, `searchFlightsByPriceRange` |
| `AirportInfoTool` | `getAirportList`, `getAirportCode` |
| `AirlineInfoTool` | `getAirlineList`, `getAirlineId` |

Spring AI는 각 메서드의 `@Tool(description = ...)`과 `@ToolParam(description = ...)`으로 Tool 정의(JSON 스키마)를 만들어 LLM에게 보냅니다.
LLM이 보는 것은 **이 설명뿐**이므로, 설명의 품질이 곧 Tool 선택의 품질입니다.

---

## Step 10-2: ToolResultCapture — LLM이 부른 Tool 기록하기

### 왜 필요한가

`call().content()`는 LLM의 **최종 답변 문자열**만 돌려줍니다. 그 사이에 어떤 Tool이 어떤 인자로 실행됐는지, 결과가 무엇이었는지는 알 수 없습니다.
하지만 화면에는 다음이 필요합니다.

- 실행 기록: LLM이 어떤 Tool을 어떤 순서로 불렀는가 (TOOL 단계)
- 검색 결과: 항공사별 항공편 목록 (LLM이 쓴 문장이 아니라 **실제 데이터**)

그래서 Tool이 실행될 때마다 이름·인자·결과와 실행 시각을 남겨 두고, `call()`이 끝난 뒤 꺼내 씁니다.

### 코드

`src/main/java/net/java21/flyschedule/mcp/ToolResultCapture.java`

```java
public final class ToolResultCapture {

    /** Tool 호출 한 건 (result는 실패하면 null, error는 성공하면 null, 시각은 System.nanoTime()) */
    public record ToolCall(String toolName, String arguments, Object result, String error,
                           long startNanos, long endNanos) {

        public boolean failed() {
            return error != null;
        }
    }

    private static final ThreadLocal<List<ToolCall>> CALLS = ThreadLocal.withInitial(ArrayList::new);

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

    /** 지금까지의 호출 기록을 꺼내고 비웁니다. (호출 순서 유지) */
    public static List<ToolCall> getAndClear() {
        List<ToolCall> calls = List.copyOf(CALLS.get());
        CALLS.remove();
        return calls;
    }
}
```

### `capture()`가 작업을 `Supplier`로 받는 이유

모든 Tool 메서드는 실제 작업을 람다로 넘기고, `capture(...)`의 반환값을 그대로 돌려줍니다.

`src/main/java/net/java21/flyschedule/mcp/FlightSearchTool.java`

```java
public Map<String, List<FlightInfoResponse>> searchFlightsAfterTime(
        @ToolParam(description = "출발 공항 이름 (예: 광주, 김포, 제주)") String departure,
        // ... 생략
        @ToolParam(description = "기준 시간 (예: 14:00, 오후 2시)") String afterTime) {

    return ToolResultCapture.capture("searchFlightsAfterTime",
            "%s → %s, %s, %s 이후".formatted(departure, arrival, date, afterTime),
            () -> limitPerAirline(flightSearchAgent.searchWithTimeFilter(departure, arrival, date, afterTime)));
}
```

- **결과를 그대로 돌려줍니다.** `capture()`가 `T`를 반환하므로 Tool 메서드의 반환 타입과 동작은 바뀌지 않고, 기록은 **부수 효과**로만 남습니다.
- **실행 구간을 잽니다.** 이미 계산된 결과가 아니라 작업 자체를 받기 때문에, `capture()`가 실행 전후의 `System.nanoTime()`을 남길 수 있습니다. 그래서 화면 타임라인에 Tool 막대가 실제 위치와 길이로 그려집니다.
- **실패도 기록합니다.** 작업이 예외를 던지면(예: `getAirportCode("서울")`) `error`에 메시지를 담아 기록한 뒤 예외를 다시 던집니다. Spring AI는 그 오류를 LLM에게 전달하고, 실행 기록에는 "Tool 실행 실패" 단계가 남습니다.
- **`limitPerAirline()`**: `FlightSearchTool`의 세 검색 Tool은 모두 항공사별 최대 3편(`MAX_FLIGHTS_PER_AIRLINE`)만 LLM에게 돌려줍니다. 김포→제주처럼 하루 100편이 넘는 노선의 결과를 그대로 넘기면 작은 모델의 컨텍스트(`num-ctx: 4096`)를 넘기 때문입니다.

Tool 7개(`FlightSearchTool` 3개, `AirportInfoTool` 2개, `AirlineInfoTool` 2개)가 모두 같은 방식으로 기록합니다.

`arguments`는 JSON이 아니라 사람이 읽기 좋은 짧은 문장(`"광주 → 제주, 2026-10-16"`)입니다. 실행 기록과 결과 제목에 그대로 쓰기 위해서입니다.

### 왜 `ThreadLocal`인가

웹 서버는 여러 요청을 동시에 처리합니다. 기록을 정적 `List` 하나에 모으면 사용자 A의 Tool 호출이 사용자 B의 응답에 섞입니다.

```text
[요청 A 스레드] call() → LLM → searchFlightsByAirline(광주, 제주) → capture → A의 목록
[요청 B 스레드] call() → LLM → getAirportCode(김포)              → capture → B의 목록
```

앞에서 본 것처럼 Spring AI 1.1.4는 `call()`에서 Tool을 **`call()`을 부른 스레드에서** 순서대로 실행합니다.
그래서 `ThreadLocal`에 담으면 요청마다 기록이 자연스럽게 나뉘고, 호출 순서도 유지됩니다.

| 주의할 점 | 이유와 대처 |
|---|---|
| 요청 시작 시 `getAndClear()` | 톰캣은 스레드를 재사용합니다. 앞 요청이 비정상 종료돼 남긴 기록이 섞이지 않도록 먼저 비웁니다 |
| 모든 `catch`에서 `getAndClear()` | 예외가 나도 기록을 남겨 두지 않습니다(`ThreadLocal` 누수 방지). LLM 오류일 때는 꺼낸 기록을 `recordToolCalls()`로 실행 기록에 남깁니다. `getAndClear()`는 내부에서 `CALLS.remove()`를 부릅니다 |
| `ToolResultCaptureCleanupFilter` | `McpController`(`/api/mcp/**`)처럼 LLM 없이 Tool을 직접 부르는 경로에는 기록을 꺼내 가는 곳이 없습니다. 이 `OncePerRequestFilter`가 요청이 끝날 때마다 `getAndClear()`를 불러 기록이 쌓이지 않게 합니다 |
| **`stream()`에서는 쓸 수 없음** | `stream()`은 Reactor 스케줄러의 **다른 스레드**에서 Tool을 실행합니다. 이때는 `ToolContext`로 요청별 저장소를 넘기거나 Advisor에서 응답 메시지의 Tool 호출을 읽는 방식이 필요합니다 |

`src/test/java/net/java21/flyschedule/mcp/ToolResultCaptureTest.java`가 "다른 스레드의 기록은 보이지 않는다"를 검증합니다.

```java
@Test
@DisplayName("다른 스레드의 기록은 보이지 않는다 (요청끼리 섞이지 않음)")
void isolatedPerThread() {
    CompletableFuture.runAsync(() -> ToolResultCapture.capture("getAirportList", "전체", List::of)).join();

    assertThat(ToolResultCapture.getAndClear()).isEmpty();
}
```

---

## Step 10-3: 실행 기록(TOOL 단계)과 화면용 결과 만들기

### TOOL 단계 기록

`call()`이 끝나면 기록을 꺼내, LLM 단계를 먼저 남기고 Tool 호출을 이어서 남깁니다.

```java
List<ToolCall> calls = ToolResultCapture.getAndClear();
trace.record(Kind.LLM, ChatClientProvider.normalize(model),
        "Tool 선택과 답변 작성", "Tool " + calls.size() + "회 호출 후 답변", start);
recordToolCalls(calls, trace);
```

```java
private static void recordToolCalls(List<ToolCall> calls, ExecutionTrace trace) {
    calls.forEach(call -> trace.record(Kind.TOOL, call.toolName(),
            call.failed() ? "Tool 실행 실패" : "LLM이 요청한 Tool 실행",
            call.arguments() + " → " + (call.failed() ? "실패: " + call.error() : describe(call.result())),
            call.startNanos(), call.endNanos()));
}
```

- 먼저 `Kind.LLM` 단계 하나. 시작 시각 `start`를 넘겨 **`call()` 전체에 걸린 시간**을 기록합니다. Tool 호출은 모두 이 구간 **안에서** 일어났으므로, 감싸는 쪽을 먼저 적는 것이 읽기 자연스럽습니다.
- 이어서 Tool 호출마다 `Kind.TOOL` 단계 하나. `actor`는 Tool 이름, `detail`은 "인자 → 결과 요약"(실패면 "인자 → 실패: 메시지")입니다.
- `record(..., startNanos, endNanos)` 오버로드(Step 9-4)로 `capture()`가 잰 **실제 실행 구간**을 그대로 넘깁니다.

`describe()`는 결과 타입에 따라 짧은 요약을 만듭니다.

```java
private static String describe(Object result) {
    if (result instanceof Map<?, ?> map) {          // 항공편 검색: 항공사별 목록
        int count = map.values().stream().mapToInt(v -> v instanceof List<?> list ? list.size() : 0).sum();
        return map.size() + "개 항공사, " + count + "편";
    }
    if (result instanceof List<?> list) {           // 공항·항공사 목록
        return list.size() + "건";
    }
    return String.valueOf(result);                  // 공항 코드, 항공사 ID
}
```

> 💡 TOOL 단계는 `call()`이 끝난 뒤 기록되지만, 시각은 실행 당시의 값입니다. 그래서 화면 타임라인에서 TOOL 막대는 **LLM 막대 안쪽**, 실제로 실행된 위치에 실제 길이로 그려집니다. TOOL 막대 사이의 빈 구간이 LLM이 Tool 결과를 읽고 다음 행동을 정하는 시간입니다.
> 또 LLM 단계는 `call()` 안에서 오간 **여러 번의 LLM 요청을 하나로 묶은** 기록입니다.

### `lastFlightResult()`: 마지막 항공편 검색 결과를 쓰는 이유

```java
@SuppressWarnings("unchecked")
private TaskResult lastFlightResult(List<ToolCall> calls) {
    for (int i = calls.size() - 1; i >= 0; i--) {
        if (calls.get(i).result() instanceof Map<?, ?> map) {
            return TaskResult.of(null, (Map<String, List<FlightInfoResponse>>) map)
                    .withLabel(calls.get(i).arguments());
        }
    }
    return null;
}
```

- **뒤에서부터** 찾습니다. LLM이 `searchFlightsByAirline`으로 넓게 찾아본 뒤 `searchFlightsAfterTime`으로 다시 좁혔다면, 마지막 결과가 사용자 의도에 가장 가깝기 때문입니다.
- `Map` 결과만 항공편 목록으로 봅니다. `getAirportCode`의 `String`, `getAirportList`의 `List`, 실패한 호출(`result`가 `null`)은 건너뜁니다.
- Coordinator와 달리 `SearchCriteria`가 없으므로 `criteria`는 `null`이고, 제목은 Tool 인자 문장(`"광주 → 제주, 2026-10-16"`)으로 붙입니다.

> 💡 **화면의 항공편 목록은 LLM의 답변이 아니라 Tool의 실제 반환값**입니다. LLM 답변 문장에 오류가 있더라도 표는 공공데이터 포털 데이터 그대로입니다.

### 성공·실패 판단

```java
if (calls.isEmpty()) {
    return SearchResponse.fail(PATTERN,
            StringUtils.hasText(answer) ? answer : "LLM이 Tool을 호출하지 않았습니다.", trace);
}

TaskResult result = lastFlightResult(calls);
String text = StringUtils.hasText(answer) ? answer : "검색을 마쳤습니다.";
return SearchResponse.success(PATTERN, text, result == null ? List.of() : List.of(result), trace);
```

| 상황 | 응답 |
|---|---|
| Tool을 한 번도 부르지 않음 | `success: false`, `message`에는 LLM의 답(예: "어느 공항에서 출발하시나요?") |
| Tool은 불렀지만 항공편 검색 Tool은 아님 (예: `getAirportList`만) | `success: true`, `results: []` |
| 항공편 검색 Tool을 부름 | `success: true`, `results`에 마지막 검색 결과 하나 |
| Gemini 키 없음 (`IllegalArgumentException`) | `success: false`, 키 설정 안내 문구 |
| LLM 연결 실패 등 | `success: false`, `ChatClientProvider.describeError(e)`의 안내 문구. 그때까지 실행된 Tool은 실행 기록에 남음 |

Tool을 부르지 않은 답을 실패로 보는 이유는 시스템 프롬프트의 "항공편 정보는 반드시 Tool을 호출해서 얻으라"는 규칙 때문입니다.
Tool 없이 나온 답은 되묻기이거나 **지어낸 항공편**일 수 있으므로 성공으로 보여 주지 않습니다.

---

## Step 10-4: 실패 모드와 대응

Tool Calling 에이전트의 유연함은 곧 불확실함입니다. 특히 `qwen2.5:3b` 같은 작은 로컬 모델에서는 다음 실패가 자주 나타납니다.

### 실패 1: Tool을 부르지 않는다

```text
사용자: "제주 가고 싶어"
LLM:    "어느 공항에서 출발하시나요? 날짜도 알려 주세요."   ← Tool 호출 0회
```

정보가 부족하면 되묻는 것이 오히려 올바른 행동입니다. 앞에서 본 대로 `success: false`와 함께 LLM의 질문을 그대로 보여 줍니다.
반대로 정보가 충분한데도 Tool을 부르지 않고 항공편을 **지어내는** 경우도 있으며, 이 역시 같은 규칙으로 실패 처리됩니다.

### 실패 2: 엉뚱한 Tool이나 인자를 고른다

다음은 작은 모델에서 흔히 볼 수 있는 호출 예입니다.

```text
사용자: "이번 주 금요일 오후 2시 이후 광주에서 제주 가는 비행기"

LLM 호출 ①: getAirportCode("광주")                       → "NAARKJJ"
LLM 호출 ②: getAirportCode("제주")                       → "NAARKPC"
LLM 호출 ③: searchFlightsByAirline("NAARKJJ", "NAARKPC", "이번 주 금요일")
             └ 문제 1. 공항 이름 자리에 공항 코드를 넣음 → AirportCodeAgent가 코드를 그대로 받아 주지만, ①②는 쓸모없는 왕복
             └ 문제 2. 날짜를 YYYY-MM-DD로 바꾸지 않음   → DateParserAgent가 형식 오류
             └ 문제 3. "오후 2시 이후"인데 시간 조건 Tool(searchFlightsAfterTime)을 고르지 않음
```

원인은 대부분 **LLM이 보는 정보가 모호하기 때문**입니다.

- `getAirportCode`의 설명이 "공항 코드를 반환한다"이니, 검색 전에 코드로 바꿔야 한다고 추측합니다.
- `FlightSearchTool`의 `@Tool` 설명에 "'내일', '모레'도 지원합니다"라고 적혀 있어, `@ToolParam`이 `YYYY-MM-DD`를 요구해도 날짜를 변환하지 않아도 된다고 판단합니다.

Tool이 예외를 던지면 Spring AI는 기본 설정(`spring.ai.tools.throw-exception-on-error=false`)에서 예외 메시지를 Tool 결과로 LLM에게 돌려주고, LLM은 인자를 고쳐 다시 시도하기도 합니다.
예외는 `capture()`가 실행한 작업 안에서 발생하므로, **실패한 호출도 "Tool 실행 실패" 단계로 남습니다.** 실행 기록에서 "실패 → 인자를 고친 재호출" 흐름을 그대로 볼 수 있습니다.

### 대응 방법

| 대응 | 방법 | 이 프로젝트의 예 |
|---|---|---|
| **Tool 설명 개선** | "언제 이 Tool을 쓰는지 / 쓰지 말아야 하는지"를 설명에 적는다. 예시 값을 시스템 프롬프트 규칙과 맞춘다 | `getAirportCode`에 "항공편 검색 Tool은 공항 이름을 그대로 받으므로 검색 전에 이 Tool을 부를 필요가 없다"를 추가 |
| **시스템 프롬프트에 제약** | 인자 형식, 우선순위, 금지 사항을 규칙으로 적는다 | 이미 "날짜는 YYYY-MM-DD", "공항은 한글 이름 그대로" 규칙이 있음 |
| **Tool 개수 줄이기** | 이 화면에 필요 없는 Tool은 넘기지 않는다 | 항공사 Tool 2개를 빼면 선택지가 7개 → 5개 |
| **Tool 안에서 검증** | Tool이 잘못된 인자를 받으면 **무엇이 틀렸고 어떻게 고쳐야 하는지**를 메시지로 돌려준다 | `DateParserAgent`의 예외 메시지 "날짜 형식이 올바르지 않습니다. (YYYY-MM-DD 또는 '내일', '모레' 등)" |
| **더 큰 모델 사용** | Tool 선택 정확도는 모델 크기에 크게 좌우된다 | 화면에서 Ollama 대신 Gemini 선택 |
| **결정이 필요 없는 일은 코드로** | 순서가 늘 같다면 애초에 LLM에게 맡기지 않는다 | Step 9 Coordinator 패턴 |

---

## Step 10-5: API로 실행해 보기

### 엔드포인트

| 메서드 | URL | 요청 본문 |
|---|---|---|
| POST | `/api/search/tool-calling` | `{"message": "...", "model": "ollama" 또는 "gemini"}` |

`SearchController`는 세 패턴(`/coordinator`, `/tool-calling`, `/orchestrator`)을 같은 요청·응답 형식으로 제공합니다. 메시지가 비어 있으면 서비스를 부르지 않고 "검색할 문장을 입력해 주세요."로 실패 응답을 돌려줍니다.

### curl

```bash
curl -X POST http://localhost:8080/api/search/tool-calling \
  -H "Content-Type: application/json" \
  -d '{"message": "내일 광주에서 제주로 가는 항공편 알려줘", "model": "gemini"}'
```

### 응답 예 (2026-10-15 실행, 일부 생략)

```json
{
  "pattern": "tool-calling",
  "success": true,
  "message": "내일 광주에서 제주로 가는 항공편은 3개 항공사 7편입니다. 가장 저렴한 항공편은 ...",
  "results": [
    {
      "label": "광주 → 제주, 2026-10-16",
      "criteria": null,
      "flightsByAirline": {
        "제주항공": [
          {
            "flightId": "7C...",
            "airline": "제주항공",
            "departureAirport": "광주",
            "arrivalAirport": "제주",
            "departureTime": "07:30",
            "arrivalTime": "08:25",
            "price": 45000
          }
          // ... 생략
        ]
        // ... 생략 (항공사별 최대 3편)
      },
      "totalCount": 7,
      "cheapest": { "flightId": "7C...", "price": 45000 }
    }
  ],
  "trace": [
    {
      "order": 1, "kind": "LLM", "actor": "gemini",
      "title": "Tool 선택과 답변 작성",
      "detail": "Tool 1회 호출 후 답변",
      "startMs": 0, "durationMs": 2841, "thread": "http-nio-8080-exec-1"
    },
    {
      "order": 2, "kind": "TOOL", "actor": "searchFlightsByAirline",
      "title": "LLM이 요청한 Tool 실행",
      "detail": "광주 → 제주, 2026-10-16 → 3개 항공사, 7편",
      "startMs": 1203, "durationMs": 388, "thread": "http-nio-8080-exec-1"
    }
  ],
  "elapsedMs": 2842
}
```

> 숫자와 항공편은 실행 환경과 날짜마다 다릅니다. 같은 문장이라도 모델이 `getAirportCode`를 먼저 부르면 TOOL 단계가 3개가 될 수도 있습니다. **이것이 결정성이 낮다는 뜻입니다.**

### 화면에서 비교하기

브라우저에서 `http://localhost:8080/`을 열고 Coordinator와 Tool Calling을 함께 선택해 실행하면, 같은 문장에 대해 두 패턴의 실행 기록이 나란히 표시됩니다.

- Coordinator: 짧은 CODE 막대가 차례로 이어지고, LLM은 0~1회
- Tool Calling: 요청 시간 대부분을 차지하는 긴 LLM 막대 하나와, 그 안쪽에 실제 실행 위치로 그려진 TOOL 막대 1~N개

패널 위쪽의 통계(전체 시간, LLM·Tool·Worker 횟수, 코드 단계 수)로도 두 패턴의 차이를 바로 비교할 수 있습니다.

---

## Step 10-6: 가짜 LLM으로 테스트하기

### 아이디어

진짜 LLM으로 테스트하면 느리고, 결과가 매번 다르며, 네트워크와 API 키가 필요합니다.
그런데 우리가 검증하고 싶은 것은 LLM의 판단이 아니라 **"Tool이 호출되면 그 기록이 올바르게 TOOL 단계와 결과로 바뀌는가"**입니다.

Spring AI는 `call()` 안에서 같은 스레드로 Tool을 실행합니다. 그렇다면 테스트에서는 **`call().content()`가 호출될 때 Tool 메서드를 직접 실행**하면 됩니다. 실제 LLM이 Tool을 요청했을 때와 똑같은 일이 같은 스레드에서 일어납니다.

### 코드

`src/test/java/net/java21/flyschedule/service/toolcalling/ToolCallingSearchServiceTest.java`

```java
@BeforeEach
void setUp() {
    flightSearchAgent = mock(FlightSearchAgent.class);
    airportListAgent = mock(AirportListAgent.class);
    flightSearchTool = new FlightSearchTool(flightSearchAgent);   // Tool은 진짜, 그 아래 에이전트만 가짜
    airportInfoTool = new AirportInfoTool(airportListAgent);
    AirlineInfoTool airlineInfoTool = new AirlineInfoTool(mock(AirlineListAgent.class));

    ChatClient chatClient = mock(ChatClient.class);
    requestSpec = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF); // system()/user()/tools()가 자기 자신을 반환
    callSpec = mock(ChatClient.CallResponseSpec.class);
    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callSpec);
    // ... 생략
}

/** LLM이 Tool을 호출한 뒤 answer를 답한 것처럼 동작하게 한다 */
private void llmCallsToolsThenAnswers(Supplier<String> llmTurn) {
    when(callSpec.content()).thenAnswer(invocation -> llmTurn.get());
}
```

`RETURNS_SELF`는 빌더 체인(`prompt().system(...).user(...).tools(...)`)을 끊김 없이 이어 주는 Mockito 설정입니다.

```java
@Test
@DisplayName("LLM 호출 구간이 먼저 기록되고, 그 안에서 LLM이 요청한 Tool 호출이 순서대로 TOOL 단계로 기록된다")
void recordsToolCalls() {
    when(airportListAgent.getAirportCode("광주")).thenReturn("NAARKJJ");
    when(flightSearchAgent.searchAndGroupByAirline("광주", "제주", "2026-03-25"))
            .thenReturn(Map.of("제주항공", List.of(flight("7C1402", "제주항공", "1030", "1120", 45000))));
    llmCallsToolsThenAnswers(() -> {
        airportInfoTool.getAirportCode("광주");                          // LLM 호출 ①이 요청했다고 가정
        flightSearchTool.searchFlightsByAirline("광주", "제주", "2026-03-25"); // LLM 호출 ②가 요청했다고 가정
        return "제주항공 7C1402편이 45,000원으로 가장 저렴합니다.";           // 최종 답변
    });

    SearchResponse response = service.search("3월 25일 광주에서 제주", "gemini");

    assertThat(response.trace()).extracting(TraceStep::kind)
            .containsExactly(Kind.LLM, Kind.TOOL, Kind.TOOL);
    assertThat(response.trace()).extracting(TraceStep::actor)
            .containsExactly("gemini", "getAirportCode", "searchFlightsByAirline");
    // Tool 실행 구간은 LLM 호출 구간 안에 있다 (타임라인에서 LLM 막대 안에 Tool 막대가 그려짐)
    TraceStep llm = response.trace().get(0);
    assertThat(response.trace().subList(1, 3)).allSatisfy(tool -> {
        assertThat(tool.startMs()).isGreaterThanOrEqualTo(llm.startMs());
        assertThat(tool.startMs() + tool.durationMs()).isLessThanOrEqualTo(llm.startMs() + llm.durationMs());
    });
    assertThat(response.results()).singleElement().satisfies(result -> {
        assertThat(result.label()).isEqualTo("광주 → 제주, 2026-03-25");
        assertThat(result.cheapest().flightId()).isEqualTo("7C1402");
    });
}
```

같은 방식으로 다음 시나리오도 검증합니다.

| 테스트 메서드 | 검증 내용 |
|---|---|
| `passesToolsPerRequest` | `.tools(...)`가 요청마다 3개 객체로 호출되고, 시스템 프롬프트에 오늘 날짜가 들어간다 |
| `usesLastFlightResult` | 검색 Tool을 두 번 부르면 마지막(`searchFlightsAfterTime`) 결과를 쓴다 |
| `limitsThreePerAirline` | 검색 Tool이 항공사별 최대 3편만 돌려준다 (프롬프트 길이 제한) |
| `noToolCall` | Tool 호출 0회 → `success: false`, LLM의 질문이 `message`로, 실행 기록은 LLM 단계 하나 |
| `failedToolIsRecorded` | `getAirportCode("서울")`이 실패한 뒤 LLM이 김포로 다시 검색하면, 실행 기록이 "Tool 선택과 답변 작성 → Tool 실행 실패 → LLM이 요청한 Tool 실행" 순서로 남는다 |
| `llmFailure` | Tool 실행 뒤 LLM 호출이 실패하면 실패 응답에 그때까지 실행된 Tool이 기록되고, `ThreadLocal`이 비워진다 |
| `clearsLeftovers` | 이전 요청이 남긴 기록이 새 요청에 섞이지 않는다 |

```bash
./mvnw test -Dtest=ToolCallingSearchServiceTest,ToolResultCaptureTest
```

> 💡 이 테스트는 "LLM이 올바른 Tool을 고르는가"는 검증하지 않습니다. 그것은 모델·프롬프트 품질의 문제이므로 실제 모델로 대표 문장 몇 개를 돌려 보는 **평가(evaluation)**로 따로 확인해야 합니다.

---

## Step 10-7: Coordinator와 Tool Calling 에이전트 비교

| 항목 | Coordinator 패턴 (Step 9) | Tool Calling 에이전트 (Step 10) |
|---|---|---|
| 다음 단계를 누가 결정하는가 | **코드** (`FlightSearchCoordinator`의 고정 순서) | **LLM** (Tool 설명을 읽고 판단) |
| 결정성 | 높음. 같은 입력이면 같은 단계 | 낮음. 같은 입력이라도 Tool 선택·횟수가 달라질 수 있음 |
| LLM 호출 수 | 0~1회 (정규식이 실패할 때만 조건 추출) | 최소 2회 (Tool 호출 요청 N회 + 최종 답변 1회) |
| 지연 | 짧음 (정규식이면 API 호출 시간이 대부분) | 김 (LLM 왕복이 Tool 호출마다 추가) |
| 비용 | 낮음 | 높음. 매 왕복마다 Tool 정의와 앞선 결과가 입력 토큰으로 다시 들어감 |
| 새 조건 대응 | 코드 수정 필요 (`if` 문, 새 에이전트) | Tool만 추가하면 LLM이 알아서 활용 |
| 답변 형태 | 코드가 만든 요약 한 줄 | 자연스러운 문장 |
| 디버깅 | 쉬움. 실행 기록이 코드 순서 그대로 | 어려움. "왜 이 Tool을 골랐는가"는 프롬프트·모델에 달림 |
| 적합한 상황 | 처리 순서가 정해진 반복 업무, 정확성·비용이 중요할 때 | 질문 형태가 다양하고, 필요한 단계를 미리 알 수 없을 때 |

> 💡 둘 중 하나만 골라야 하는 것은 아닙니다. 다음 Step 11에서는 **상위 결정은 LLM이, 하위 실행은 Coordinator 코드가** 맡도록 두 방식을 조합합니다.

---

## 일반적인 문제 해결

| 증상 | 원인 | 해결 |
|---|---|---|
| `"LLM 서버에 연결할 수 없습니다..."` | Ollama가 꺼져 있거나 `spring.ai.ollama.base-url`이 틀림 | Ollama 실행 확인, 또는 `"model": "gemini"` 사용 |
| `"Gemini API 키가 설정되지 않았습니다..."` | `GOOGLE_GENAI_API_KEY` 환경 변수 없음 | 환경 변수를 설정하고 재시작 |
| `success: false`인데 `message`가 LLM의 질문 | LLM이 Tool을 부르지 않음 | 출발지·도착지·날짜를 모두 넣어 다시 질문 |
| `results`가 비어 있음 | 항공편 검색이 아닌 Tool만 호출됨 (예: `getAirportList`) | 실행 기록의 TOOL 단계를 확인하고 Tool 설명·프롬프트 보완 |
| 응답이 매우 느림(수십 초 이상) | 작은 로컬 모델 + Tool 왕복 여러 번 | Gemini 사용, 넘기는 Tool 줄이기. 읽기 타임아웃은 `spring.http.client.read-timeout: 300s` |
| 다른 사용자의 Tool 기록이 섞임 | `stream()`이나 별도 스레드에서 Tool을 실행 | `call()`을 쓰거나 `ThreadLocal` 대신 `ToolContext` 사용 |
| 다른 서비스의 LLM 호출이 갑자기 항공편을 검색함 | `ChatClientConfig`에 `defaultTools`를 추가함 | Tool은 `ToolCallingSearchService`에서만 `.tools(...)`로 전달 |
| TOOL 단계가 "Tool 실행 실패", detail에 "공공데이터 포털 ..." | Tool 안에서 `ExternalApiException` 발생 (서비스 키 없음, API 오류) | `DATA_GO_KR_SERVICE_KEY` 확인. 같은 문장을 Coordinator로 보내면 오류 메시지를 바로 볼 수 있음 |

---

## 실습 과제

1. **(기본)** `AirportInfoTool.getAirportCode`의 `@Tool` 설명에 "항공편 검색 Tool은 공항 이름을 그대로 받으므로, 검색하려고 이 Tool을 먼저 부를 필요는 없다"는 문장을 추가하라. Ollama로 "내일 광주에서 제주" 를 5번 실행해 수정 전후 TOOL 단계 개수를 비교하라.
2. **(기본)** `ToolCallingSearchServiceTest`에 "LLM이 `getAirportList`만 호출했을 때 `success`는 `true`이고 `results`는 비어 있다"를 검증하는 테스트를 추가하라.
3. **(도전)** `lastFlightResult()`는 마지막 항공편 검색 결과 하나만 화면에 보여 준다. LLM이 "김포 → 제주"와 "광주 → 제주"처럼 **서로 다른 노선**을 검색했다면 둘 다 `results`에 담고, 같은 노선을 조건만 바꿔 다시 검색했다면 마지막 것만 남기도록 바꿔라. 노선이 같은지 판단할 기준(Tool 인자 문자열? 공항 코드?)을 정하고 `ToolCallingSearchServiceTest`에 두 경우의 테스트를 추가하라.
4. **(도전)** Tool 인자 검증을 추가하라. `FlightSearchTool`에서 날짜가 `YYYY-MM-DD`가 아니면 검색하지 않고 "date는 YYYY-MM-DD 형식이어야 합니다. 오늘 날짜를 기준으로 다시 계산하세요." 같은 **LLM이 스스로 고칠 수 있는 메시지**를 담은 예외를 던져라. 검증을 `capture()`에 넘기는 람다 안에 두면 실행 기록에 "Tool 실행 실패"로 남는지 `failedToolIsRecorded()`를 참고해 테스트하라.

---

## 요약

1. **Tool Calling 에이전트**는 LLM 하나가 Tool 목록을 받아 어떤 Tool을 어떤 인자로 몇 번 부를지 스스로 결정하는 **단일 에이전트**입니다. A2A 프로토콜과는 관계없습니다.
2. LLM은 Tool 호출을 **요청**만 하고, 실행은 Spring AI가 `call()`을 부른 스레드에서 합니다.
3. Tool은 `defaultTools`가 아니라 **요청마다 `.tools(...)`**로 넘겨, Tool이 필요 없는 LLM 호출에 영향을 주지 않습니다.
4. **ToolResultCapture**는 Tool 작업을 직접 실행하며 `ThreadLocal`에 이름·인자·결과·실행 구간·실패 여부를 남기고, `getAndClear()`로 꺼내 LLM 단계 뒤의 TOOL 단계와 화면용 결과를 만듭니다. `stream()`에서는 이 방식을 쓸 수 없습니다.
5. Tool을 부르지 않거나 엉뚱한 Tool·인자를 고르는 실패는 **Tool 설명, 시스템 프롬프트 제약, Tool 안의 검증**으로 줄입니다.
6. `call().content()`가 Tool 메서드를 직접 실행하게 만들면 **가짜 LLM**으로 흐름을 테스트할 수 있습니다.

---

## 체크리스트

- [ ] Coordinator 패턴과 Tool Calling 에이전트의 차이를 "다음 단계를 누가 결정하는가"로 설명할 수 있다
- [ ] `ToolCallingSearchService`가 시스템 프롬프트에 오늘 날짜를 넣는 이유를 안다
- [ ] Tool을 `defaultTools`로 등록하지 않는 이유를 설명할 수 있다
- [ ] 모든 `@Tool` 메서드가 `ToolResultCapture.capture(...)`의 반환값을 돌려준다
- [ ] `ThreadLocal`을 쓸 수 있는 조건(`call()`)과 쓸 수 없는 경우(`stream()`)를 안다
- [ ] `POST /api/search/tool-calling`의 응답에서 TOOL 단계를 확인했다
- [ ] `ToolCallingSearchServiceTest`가 통과한다
- [ ] 작은 모델에서 Tool 선택이 틀렸을 때 고칠 수 있는 방법을 세 가지 이상 말할 수 있다

---

## 다음 단계

[Step 11: Orchestrator-Workers 패턴 — LLM이 작업을 나누고 종합하기](step-11-orchestrator-workers.md)에서는 "금요일이나 토요일에 김포나 광주에서 제주"처럼 **하위 작업의 개수를 미리 알 수 없는 요청**을, LLM이 작업을 나누고 Worker가 병렬로 처리하는 방식으로 해결합니다.

---

## 참고 자료

- [Spring AI Reference - Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [Spring AI Reference - ChatClient API](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [Anthropic - Building effective agents](https://www.anthropic.com/engineering/building-effective-agents)
- [Spring 블로그 - Building Effective Agents with Spring AI](https://spring.io/blog/2025/01/21/spring-ai-agentic-patterns)
