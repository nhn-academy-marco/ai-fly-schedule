# Step 4: ChatClient Advisor와 Tool 실행 과정 이해

Step 3에서 LLM이 Tool을 호출하게 만들었지만, LLM이 어떤 프롬프트를 받았고 어떤 Tool을 어떤 인자로 불렀는지는 콘솔 로그 몇 줄로만 알 수 있었습니다. 이번에는 ChatClient의 요청과 응답 앞뒤에 끼어드는 Advisor로 LLM 호출을 들여다보고, Tool 호출을 데이터로 기록해 응답의 실행 기록(`trace`)으로 보여 주는 방법을 배웁니다.

**이 Step에서 만들 것**: `SimpleLoggerAdvisor` 로그를 켜서 LLM 요청·응답을 확인하고, `ToolResultCapture`가 남긴 Tool 호출 기록이 `POST /api/search/tool-calling` 응답에 `TOOL` 단계로 나오는 것을 확인합니다. 마지막으로 LLM 호출 시간을 재는 Advisor를 직접 만들어 등록합니다.

```json
"trace": [
  { "order": 1, "kind": "LLM",  "actor": "ollama", "title": "Tool 선택과 답변 작성", "detail": "Tool 1회 호출 후 답변", ... },
  { "order": 2, "kind": "TOOL", "actor": "searchFlightsByAirline", "title": "LLM이 요청한 Tool 실행", "detail": "광주 → 제주, 2026-10-17 → 2개 항공사, 6편", ... }
]
```

## 학습 목표

이 Step을 마치면 다음을 할 수 있습니다.

1. Advisor가 무엇이고 ChatClient 호출의 어느 지점에 끼어드는지 설명할 수 있다.
2. `SimpleLoggerAdvisor` 로그를 켜서 LLM에 보낸 요청과 받은 응답을 확인할 수 있다.
3. 기본 설정에서 Tool 실행이 Advisor에 보이지 않는 이유를 설명하고, `ToolResultCapture`가 Tool 호출을 기록하는 방식을 설명할 수 있다.
4. `CallAdvisor`를 구현해 LLM 호출 시간을 기록하는 Advisor를 만들고 등록할 수 있다.
5. `getOrder()`로 여러 Advisor의 실행 순서를 정할 수 있다.

---

## 전체 흐름

```text
[서비스]  chatClient.prompt()...tools(...).call()
  ↓
[Advisor 1 (order 작음, 바깥쪽)]  요청 전 처리   예: 시간 측정 시작
  ↓
[Advisor 2 (SimpleLoggerAdvisor)]  요청 로그
  ↓
[ChatModel]  LLM 호출
  │   ↳ LLM이 Tool 호출 요청 → Spring AI가 Tool 실행 → LLM 재호출   (ChatModel 안에서 반복)
  │        ↳ Tool 메서드가 ToolResultCapture에 호출 기록
  ↓
[Advisor 2]  응답 로그 (Tool 실행이 끝난 뒤의 최종 응답)
  ↓
[Advisor 1]  응답 후 처리   예: 소요 시간 기록
  ↓
[서비스]  ToolResultCapture.getAndClear()로 Tool 호출 기록을 꺼내 trace에 TOOL 단계로 남김
```

Spring MVC와 비교하면 다음과 같습니다.

```text
Spring MVC:  [HTTP 요청] → [Filter] → [Interceptor] → [컨트롤러]
Spring AI:   [prompt()]  → [Advisor] → [Advisor]     → [ChatModel → LLM]
```

---

## Advisor란?

**Advisor**는 ChatClient의 요청이 LLM에 가기 전과 응답이 돌아온 뒤에 공통 처리를 끼워 넣는 구성 요소입니다. 서블릿 `Filter`가 컨트롤러 앞뒤에서 공통 로직을 실행하듯, Advisor는 LLM 호출 앞뒤에서 로깅·측정·프롬프트 보강 같은 일을 합니다.

| 쓰임 | 예 |
|------|-----|
| 디버깅 | 실제로 나간 프롬프트와 받은 응답을 로그로 확인 (`SimpleLoggerAdvisor`) |
| 측정 | LLM 호출 시간, 토큰 사용량 기록 |
| 프롬프트 보강 | 대화 기록이나 검색한 문서를 프롬프트에 덧붙이기 (`MessageChatMemoryAdvisor` 등) |
| 차단·가공 | 금지어가 든 요청 막기, 응답 다듬기 |

> **주의**: 기본 설정에서 Tool 호출 루프는 `ChatModel` 안에서 돕니다. 그래서 Advisor는 첫 요청과, Tool 실행이 모두 끝난 뒤의 최종 응답만 봅니다. "LLM이 어떤 Tool을 어떤 인자로 불렀는가"는 Advisor 로그만으로는 알 수 없습니다. 이 빈틈을 채우는 것이 Step 4-2의 `ToolResultCapture`입니다.

---

## Step 4-1: SimpleLoggerAdvisor로 요청·응답 보기

### 왜 필요한가

LLM이 엉뚱한 답을 하거나 `.entity()` 변환에 실패하면, 실제로 어떤 프롬프트가 나갔고 어떤 응답이 왔는지 봐야 원인을 찾을 수 있습니다. `SimpleLoggerAdvisor`는 Spring AI가 기본 제공하는 Advisor로, 요청과 응답을 DEBUG 레벨 로그로 남깁니다.

### 어떻게 하는가

이 프로젝트는 Step 2의 `ChatClientConfig`에서 이미 두 ChatClient 모두에 등록해 두었습니다.

파일: `src/main/java/net/java21/flyschedule/config/ChatClientConfig.java` (일부)

```java
return ChatClient.builder(ollamaChatModel)
        .defaultAdvisors(new SimpleLoggerAdvisor())
        .build();
```

Tool과 달리 Advisor는 `defaultAdvisors`로 기본 등록했다는 점에 주목하세요. 로깅은 모든 LLM 호출(검색 조건 추출, 작업 계획, 결과 요약, Tool Calling)에 필요하고, 등록해도 LLM의 판단에 영향을 주지 않습니다. 반대로 Tool은 LLM의 판단을 바꾸므로 필요한 요청에서만 `.tools(...)`로 넘깁니다(Step 3).

`SimpleLoggerAdvisor`의 로그는 DEBUG 레벨이고, 이 프로젝트의 기본 로그 설정(`src/main/resources/logback-spring.xml`)은 `org.springframework`를 INFO로 둡니다. 그래서 Advisor 로거의 레벨만 올립니다.

파일: `src/main/resources/application.yml`에 추가

```yaml
logging:
  level:
    org.springframework.ai.chat.client.advisor: DEBUG
```

### 확인하기

애플리케이션을 다시 실행하고 Tool Calling 에이전트 엔드포인트를 호출합니다. Coordinator 엔드포인트는 한국어 문장이 정규식으로 처리되면 LLM을 부르지 않으므로 Advisor 로그가 남지 않습니다.

```bash
curl -X POST http://localhost:8080/api/search/tool-calling \
  -H "Content-Type: application/json" \
  -d '{"message": "내일 광주에서 제주로 가는 항공편 알려줘"}'
```

콘솔 로그(날짜·스레드 생략, 줄여서 표시):

```text
DEBUG o.s.a.c.c.a.SimpleLoggerAdvisor - request: ChatClientRequest[prompt=Prompt{messages=[SystemMessage{textContent='당신은 국내선 항공편 검색 도우미입니다. ...'}, UserMessage{content='내일 광주에서 제주로 가는 항공편 알려줘', ...}], modelOptions=...}, context={...}]
INFO  n.j.flyschedule.mcp.FlightSearchTool - MCP Tool 호출: searchFlightsByAirline(departure=광주, arrival=제주, date=2026-10-17)
INFO  n.j.flyschedule.mcp.FlightSearchTool - MCP Tool 응답: 2개 항공사, 6편 (항공사별 최대 3편)
DEBUG o.s.a.c.c.a.SimpleLoggerAdvisor - response: { "result" : { "output" : { "text" : "내일 광주에서 제주로 가는 항공편은 모두 6편입니다. ...", ... } } }
```

request 로그 한 줄과 response 로그 한 줄 사이에 Tool 로그가 끼어 있습니다. Tool 실행이 Advisor 바깥이 아니라 `ChatModel` 안에서 일어났다는 증거입니다.

---

## Step 4-2: Tool 호출을 실행 기록으로 남기기 — ToolResultCapture

### 왜 필요한가

로그는 개발자가 콘솔에서 볼 때는 충분하지만, 화면에 "LLM이 어떤 Tool을 어떤 인자로 불렀고 얼마나 걸렸는가"를 보여 주려면 Tool 호출을 데이터로 모아야 합니다. 기본 설정의 Advisor에는 Tool 호출 단계가 보이지 않으므로, 이 프로젝트는 Tool 메서드가 직접 호출 기록을 남기게 했습니다.

### 어떻게 하는가

파일: `src/main/java/net/java21/flyschedule/mcp/ToolResultCapture.java` (주석 생략)

```java
public final class ToolResultCapture {

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

1. `ToolCall`: Tool 호출 한 건입니다. 이름, 인자 요약, 결과, 실패 메시지, 시작·종료 시각(`System.nanoTime()`)을 담습니다.
2. `capture(이름, 인자 요약, 작업)`: 실제 작업을 `Supplier`로 받아 직접 실행합니다. 그래서 실행 시간을 잴 수 있고, 예외가 나면 실패로 기록한 뒤 다시 던집니다. 다시 던진 예외는 Spring AI가 LLM에게 오류로 전달합니다.
3. 결과를 그대로 돌려주므로 Tool 메서드는 `return ToolResultCapture.capture(...)` 한 줄로 기록과 반환을 함께 합니다.
4. `getAndClear()`: 지금까지의 기록을 호출 순서대로 꺼내고 비웁니다.
5. `ThreadLocal`: 요청마다 기록이 섞이지 않게 스레드별로 따로 저장합니다. Spring AI 1.1.4의 `call()`은 Tool을 호출한 스레드에서 차례로 실행하므로 이 방식이 동작합니다. `stream()`은 다른 스레드에서 Tool을 실행하므로 쓸 수 없습니다.

모든 Tool 메서드는 같은 모양으로 기록합니다.

파일: `src/main/java/net/java21/flyschedule/mcp/AirportInfoTool.java` (일부)

```java
return ToolResultCapture.capture("getAirportCode", airportName, () -> airportListAgent.getAirportCode(airportName));
```

`ToolCallingSearchService`는 `call()`이 끝난 뒤 기록을 꺼내, LLM 단계 다음에 Tool 호출마다 TOOL 단계를 남깁니다(Step 10에서 전체를 읽습니다).

파일: `src/main/java/net/java21/flyschedule/service/toolcalling/ToolCallingSearchService.java` (일부)

```java
// LLM 호출 구간을 먼저, 그 안에서 실행된 Tool 호출을 이어서 기록한다
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

> **주의**: `ThreadLocal`은 비우지 않으면 남습니다. Tomcat은 요청 처리 스레드를 재사용하므로, 남은 기록이 다음 요청에 섞일 수 있습니다. 그래서 `ToolCallingSearchService.search()`는 시작할 때와 예외가 났을 때도 `getAndClear()`를 부르고, LLM 없이 Tool을 직접 부르는 `McpController` 경로를 위해 `ToolResultCaptureCleanupFilter`(`config` 패키지)가 요청이 끝날 때마다 기록을 비웁니다.

### 확인하기

Step 4-1의 curl 응답에서 `trace`를 봅니다.

```json
"trace": [
  { "order": 1, "kind": "LLM",  "actor": "ollama", "title": "Tool 선택과 답변 작성", "detail": "Tool 1회 호출 후 답변", "startMs": 0, "durationMs": 5120, ... },
  { "order": 2, "kind": "TOOL", "actor": "searchFlightsByAirline", "title": "LLM이 요청한 Tool 실행", "detail": "광주 → 제주, 2026-10-17 → 2개 항공사, 6편", "startMs": 2310, "durationMs": 480, ... }
]
```

TOOL 단계의 구간(`startMs`부터 `startMs + durationMs`까지)이 LLM 단계의 구간 안에 들어가는지 확인하세요. Tool이 LLM 호출 도중에 실행되었다는 뜻입니다. 화면(`http://localhost:8080`)의 타임라인에서도 LLM 막대 안에 Tool 막대가 그려집니다.

테스트로도 확인합니다.

```bash
./mvnw test -Dtest=ToolResultCaptureTest
```

`capturesInOrder`(호출 순서 유지), `recordsTiming`(실행 구간 기록), `recordsFailure`(실패 기록 후 예외 다시 던짐), `isolatedPerThread`(스레드별 분리) 등이 통과하면 됩니다.

---

## Step 4-3: 사용자 정의 Advisor 만들기

### 왜 필요한가

`SimpleLoggerAdvisor`는 요청과 응답을 그대로 찍을 뿐입니다. "LLM 호출 하나에 몇 ms가 걸렸는가", "한 번의 검색에서 LLM을 몇 번 불렀는가" 같은 정보가 필요하면 Advisor를 직접 만듭니다. 동기 호출용 Advisor는 `CallAdvisor` 인터페이스를 구현합니다(스트리밍용은 `StreamAdvisor`).

### 어떻게 하는가

다음 파일은 실습용이며 프로젝트에 들어 있지 않습니다.

파일(직접 만들기): `src/main/java/net/java21/flyschedule/practice/TimingAdvisor.java`

```java
package net.java21.flyschedule.practice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

@Slf4j
public class TimingAdvisor implements CallAdvisor {

    @Override
    public String getName() {
        return "TimingAdvisor";
    }

    @Override
    public int getOrder() {
        return -100;   // SimpleLoggerAdvisor(0)보다 바깥쪽에서 실행
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long start = System.currentTimeMillis();
        log.info("LLM 요청: {}", request.prompt().getUserMessage().getText());

        ChatClientResponse response = chain.nextCall(request);   // 다음 Advisor 또는 실제 LLM 호출

        log.info("LLM 응답: {}ms", System.currentTimeMillis() - start);
        return response;
    }
}
```

**코드 읽기**

1. `getName()`: 로그와 디버깅에서 Advisor를 구분하는 이름입니다.
2. `getOrder()`: 값이 작을수록 바깥쪽에서 먼저 실행됩니다. Step 4-4에서 자세히 봅니다.
3. `chain.nextCall(request)`: 체인의 다음 Advisor, 마지막에는 실제 LLM 호출로 이어집니다. 이 줄 앞은 "요청 전 처리", 뒤는 "응답 후 처리"입니다. AOP의 Around Advice나 `Filter.doFilter()`와 같은 구조입니다.
4. 시작 시각을 지역 변수로 두면 요청마다 따로 잡히므로 동시 요청에도 안전합니다. Advisor 객체는 여러 요청이 함께 쓰므로, 요청별 값을 필드에 저장하지 않습니다.
5. 측정 시간에는 Tool 실행 시간도 포함됩니다. Tool 루프가 `chain.nextCall()` 안(`ChatModel`)에서 돌기 때문입니다.

`ChatClientConfig`에 등록합니다. 실습이 끝나면 원래대로 되돌립니다.

```java
return ChatClient.builder(ollamaChatModel)
        .defaultAdvisors(new SimpleLoggerAdvisor(), new TimingAdvisor())
        .build();
```

특정 요청에만 적용하려면 `chatClient.prompt().advisors(new TimingAdvisor())...`처럼 요청 단위로 넘깁니다.

### 확인하기

영어 문장으로 Coordinator 검색을 하면 LLM 호출이 한 번 일어나므로 다음 로그가 한 쌍 나옵니다.

```text
INFO  n.j.f.practice.TimingAdvisor - LLM 요청: flights from Gwangju to Jeju tomorrow
INFO  n.j.f.practice.TimingAdvisor - LLM 응답: 3874ms
```

Orchestrator-Workers 패턴으로 검색하면 계획과 종합 단계에서 LLM을 부르므로 로그가 여러 쌍 나옵니다. 몇 쌍인지 세어 보세요(실습 과제 2).

---

## Step 4-4: Advisor 실행 순서

### 왜 필요한가

Advisor가 여러 개면 순서가 결과를 바꿉니다. 예를 들어 캐시 Advisor가 로깅 Advisor보다 안쪽에 있으면, 캐시에서 바로 응답해도 로그가 남지 않습니다.

### 어떻게 하는가

순서는 등록 순서가 아니라 각 Advisor의 `getOrder()` 값으로 정해집니다. 값이 작을수록 요청을 먼저(바깥쪽에서) 받고, 응답은 거꾸로 나중에 받습니다. `SimpleLoggerAdvisor`의 기본 order는 `0`입니다.

```text
요청:  [TimingAdvisor -100] → [SimpleLoggerAdvisor 0] → [ChatModel]
응답:  [TimingAdvisor -100] ← [SimpleLoggerAdvisor 0] ← [ChatModel]
```

| order 값 | 상수 | 위치 |
|----------|------|------|
| 가장 작음 | `Ordered.HIGHEST_PRECEDENCE` | 가장 바깥 (요청을 가장 먼저 받음) |
| `0` | | `SimpleLoggerAdvisor` 기본값 |
| 가장 큼 | `Ordered.LOWEST_PRECEDENCE` | 가장 안쪽 (`ChatModel` 바로 앞) |

### 확인하기

`TimingAdvisor.getOrder()`를 `100`으로 바꾸고 다시 실행합니다. `TimingAdvisor`의 "LLM 요청" 로그가 `SimpleLoggerAdvisor`의 request 로그보다 뒤에 찍히면 순서가 바뀐 것입니다.

---

## Step 4-5: Advisor 활용 패턴

실무에서 자주 만드는 Advisor의 모양입니다. 모두 `chain.nextCall()` 앞뒤에 무엇을 하느냐의 차이일 뿐입니다.

| 패턴 | `nextCall()` 앞 | `nextCall()` 뒤 | 권장 order |
|------|------------------|-----------------|-----------|
| 캐싱 | 같은 질문의 응답이 캐시에 있으면 `nextCall()` 없이 바로 반환 | 응답을 캐시에 저장 | 가장 바깥 |
| 오류 변환 | 없음 | 예외를 잡아 사용자용 메시지로 바꿔 다시 던짐 | 바깥 |
| 측정 | 시작 시각 기록 | 소요 시간·토큰 사용량 기록 | 바깥 |
| 로깅 | 요청 기록 | 응답 기록 | 안쪽 |

캐싱 Advisor의 핵심은 다음과 같습니다(설명용 예시, Caffeine 같은 캐시 라이브러리가 필요).

```java
@Override
public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
    String key = request.prompt().getUserMessage().getText();
    ChatClientResponse cached = cache.getIfPresent(key);
    if (cached != null) {
        return cached;                          // LLM을 부르지 않음
    }
    ChatClientResponse response = chain.nextCall(request);
    cache.put(key, response);
    return response;
}
```

> **주의**: 재시도 Advisor는 따로 만들지 않는 편이 좋습니다. Spring AI는 모델 호출 단계에서 이미 재시도를 하고, 이 프로젝트는 `spring.ai.retry`로 최대 2회까지로 줄여 두었습니다(Step 1). 재시도 Advisor를 더하면 재시도 횟수가 곱절로 늘어납니다. 오류를 사용자용 문장으로 바꾸는 일도 이 프로젝트에서는 Advisor 대신 `ChatClientProvider.describeError()`가 맡습니다(Step 2).

---

## 일반적인 문제 해결

### SimpleLoggerAdvisor 로그가 나오지 않음

1. `logging.level.org.springframework.ai.chat.client.advisor: DEBUG`를 추가했는지 확인합니다. `org.springframework.ai`만 올려도 됩니다.
2. 그 요청이 실제로 LLM을 불렀는지 확인합니다. Coordinator 패턴의 한국어 문장은 정규식으로 처리되어 LLM을 부르지 않습니다.
3. `ChatClientConfig`의 `defaultAdvisors(...)`가 지워지지 않았는지 확인합니다.

### Advisor 로그에 Tool 호출이 보이지 않음

정상입니다. 기본 설정에서 Tool 루프는 `ChatModel` 안에서 돌므로 Advisor에는 최종 응답만 보입니다. Tool 호출은 Tool 메서드의 로그나 응답의 `trace`(TOOL 단계)로 확인합니다.

### TOOL 단계가 비어 있거나 다른 요청의 기록이 섞임

1. Tool 메서드가 `ToolResultCapture.capture(...)`로 작업을 감쌌는지 확인합니다. 직접 만든 Tool이라면 이 부분이 빠지기 쉽습니다.
2. `stream()`을 쓰고 있지 않은지 확인합니다. 다른 스레드에서 Tool이 실행되면 `ThreadLocal`에 기록되지 않습니다.
3. `ToolResultCaptureCleanupFilter`가 빈으로 등록되어 있는지(`@Component`) 확인합니다.

### Advisor 순서가 예상과 다름

등록 순서가 아니라 `getOrder()` 값이 순서를 정합니다. 값이 같을 때만 등록 순서를 따르므로, 순서가 중요하면 값을 명시합니다.

---

## 실습 과제

### 과제 1 (기본): Tool 정의가 붙는 요청 구분하기

Step 4-1의 로그 설정을 켜고 `POST /api/search/tool-calling`(한국어 문장)과 `POST /api/search/coordinator`(영어 문장)를 각각 호출합니다.

- 힌트: `SimpleLoggerAdvisor`의 request 로그에서 `toolCallbacks` 또는 Tool 이름(`searchFlightsByAirline`)을 찾습니다.
- 완료 기준: Tool Calling 요청에는 Tool 정의가 있고 Coordinator의 검색 조건 추출 요청에는 없다는 것을 로그 일부를 인용해 보인다.

### 과제 2 (기본): 한 번의 검색에서 LLM 호출 횟수 세기

Step 4-3의 `TimingAdvisor`를 `ollamaChatClient`(또는 키가 있다면 `geminiChatClient`)에 등록하고, 세 패턴으로 같은 문장을 검색합니다.

- 힌트: "LLM 요청" 로그의 개수, 응답 `trace`에서 `kind`가 `LLM`인 단계의 개수
- 완료 기준: 세 패턴의 LLM 호출 횟수와 총 소요 시간을 표로 정리하고, 로그 개수와 `trace`의 LLM 단계 개수가 일치하는지 확인한다.

### 과제 3 (도전): ToolResultCapture 테스트 추가

`ToolResultCaptureTest`에 "성공 → 실패 → 성공" 순서로 세 번 `capture()`한 뒤 `getAndClear()`를 확인하는 테스트를 추가합니다.

- 힌트: 기존 `capturesInOrder()`와 `recordsFailure()`를 본뜨고, 실패하는 호출은 `assertThatThrownBy`로 감쌉니다.
- 완료 기준: 기록이 세 건이고 순서가 유지되며, 두 번째만 `failed()`가 `true`이고 `error`에 예외 메시지가 담긴다는 단언이 `./mvnw test -Dtest=ToolResultCaptureTest`로 통과한다.

### 과제 4 (도전): ToolCallAdvisor로 Tool 루프 옮기기

Spring AI 1.1의 `ToolCallAdvisor`를 등록하면 Tool 호출 루프가 `ChatModel` 대신 Advisor 체인에서 돕니다. `ollamaChatClient`에 `ToolCallAdvisor`를 등록하고 `POST /api/search/tool-calling`을 호출합니다.

- 힌트: `ToolCallAdvisor.builder().build()`, `SimpleLoggerAdvisor`보다 바깥쪽 order
- 완료 기준: `SimpleLoggerAdvisor`의 request/response 로그가 Tool 호출 단계마다 찍히는 것을 확인하고, 기본 방식과의 차이를 두세 문장으로 정리한다. 실험 뒤 원래대로 되돌린다.

---

## 요약

- Advisor는 ChatClient 요청 전과 응답 후에 공통 처리를 끼워 넣는 구성 요소이며, `CallAdvisor.adviseCall()`의 `chain.nextCall()` 앞뒤가 그 자리입니다.
- `SimpleLoggerAdvisor`는 모든 LLM 호출에 필요하므로 기본 등록하고, 로그는 `org.springframework.ai.chat.client.advisor`를 DEBUG로 올려 봅니다.
- 기본 설정에서 Tool 루프는 `ChatModel` 안에서 돌기 때문에 Advisor에 보이지 않습니다. 그래서 Tool 메서드가 `ToolResultCapture.capture()`로 호출을 기록하고, 서비스가 `getAndClear()`로 꺼내 `trace`의 TOOL 단계로 남깁니다.
- 여러 Advisor의 순서는 `getOrder()` 값이 정하며, 작을수록 바깥쪽입니다.

Step 1~4에서 Spring AI의 기본기(설정, ChatClient, Function Calling, Advisor)를 익혔습니다. Step 5부터는 이 기본기로 여러 Tool을 체계적으로 설계하고, 전문 에이전트와 세 가지 검색 패턴을 만들어 갑니다.

---

## 체크리스트

- [ ] Advisor가 ChatClient 호출의 어느 지점에 끼어드는지 그림으로 설명할 수 있다. (목표 1)
- [ ] `SimpleLoggerAdvisor` 로그를 켜고 Tool Calling 요청의 request·response 로그를 확인했다. (목표 2)
- [ ] Advisor에 Tool 호출이 보이지 않는 이유와 `ToolResultCapture.capture()`, `getAndClear()`의 역할을 설명하고, 응답 `trace`의 TOOL 단계를 확인했다. (목표 3)
- [ ] `CallAdvisor`를 구현한 `TimingAdvisor`를 만들어 등록하고 소요 시간 로그를 확인했다. (목표 4)
- [ ] `getOrder()` 값을 바꿔 Advisor 실행 순서가 바뀌는 것을 확인했다. (목표 5)

---

## 다음 단계

[Step 5: 다중 Function Calling과 MCP Tool 패턴](step-5-mcp-tool-pattern.md)에서 여러 Tool을 역할별로 나누고 관리하는 방법을 배웁니다.

---

## 참고 자료

- [Spring AI ChatClient 공식 문서](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [ChatClient.Advisor API](https://docs.spring.io/spring-ai/reference/api/chatclient.html#_advisors)
- [Spring AI Advisors API](https://docs.spring.io/spring-ai/reference/api/advisors.html)
- [Spring AI Observability](https://docs.spring.io/spring-ai/reference/observability/index.html)

---

## 심화 학습

### Spring AI 내장 Advisor

| Advisor | 용도 | 설명 |
|---------|------|------|
| `SimpleLoggerAdvisor` | 로깅 | 요청·응답을 DEBUG 로그로 남김 |
| `MessageChatMemoryAdvisor` | 대화 기록 | 이전 대화를 메시지 목록으로 프롬프트에 추가 |
| `PromptChatMemoryAdvisor` | 대화 기록 | 이전 대화를 시스템 프롬프트 텍스트에 추가 |
| `SafeGuardAdvisor` | 보안 | 금지어가 포함된 요청을 차단 |
| `ToolCallAdvisor` | Tool 실행 | Tool 호출 루프를 Advisor 체인에서 수행 |
| `QuestionAnswerAdvisor` | RAG | 벡터 저장소에서 찾은 문서를 프롬프트에 추가 (`spring-ai-advisors-vector-store` 모듈) |
| `VectorStoreChatMemoryAdvisor` | 대화 기록 | 대화를 벡터 저장소에 저장하고 관련 기록을 찾아 추가 (`spring-ai-advisors-vector-store` 모듈) |

### Advisor로 Tool 호출을 기록하지 않은 이유

`ToolCallAdvisor`를 쓰면 Advisor 체인에서 Tool 단계마다 요청·응답을 볼 수 있으므로, 이론상 Advisor로도 Tool 호출을 기록할 수 있습니다. 그래도 이 프로젝트가 Tool 메서드 쪽에서 기록하는 데는 이유가 있습니다.

- Tool의 실제 실행 구간(시작·종료 시각)과 실패 여부를 가장 정확히 아는 곳은 Tool 메서드 자신입니다.
- LLM 없이 Tool을 직접 부르는 경로(`McpController`, 단위 테스트)에서도 같은 기록 방식이 동작합니다.
- 기본 Tool 실행 방식을 바꾸지 않아도 되므로, Spring AI 버전이 바뀌어도 영향이 적습니다.

> **생각해 보기**: 반대로 Tool 메서드마다 `capture(...)`를 써야 한다는 점은 단점입니다. Tool이 수십 개로 늘어난다면 어떤 방식이 나을까요?
