# Step 4: ChatClient Advisor와 Tool 실행 과정 이해

## 학습 목표

이 단계를 마치면 다음을 할 수 있습니다:

- `SimpleLoggerAdvisor`를 사용하여 LLM의 요청과 응답을 로깅할 수 있습니다
- 사용자 정의 Advisor를 구현하여 Function Calling의 실행 시간과 결과를 모니터링할 수 있습니다
- Advisor의 파이프라인 패턴을 이해하고 실행 순서를 제어할 수 있습니다
- 개발 환경과 운영 환경에 맞는 Advisor 조합을 구성할 수 있습니다

---

> **핵심 개념**: Advisor는 ChatClient의 요청/응답 과정에 "끼어드는(Intercept)" 메커니즘입니다. 쉽게 말해, Spring MVC의 `HandlerInterceptor`나 서블릿의 `Filter`와 같은 역할이라고 생각하면 됩니다. 컨트롤러에 요청이 도달하기 전/후에 공통 로직을 실행하는 Filter처럼, LLM에 요청이 전달되기 전/후에 로깅이나 모니터링 로직을 실행하는 것이 Advisor입니다.

---

## 전체 흐름

Advisor가 Function Calling 과정에서 동작하는 위치를 보여 주는 흐름도입니다.

```text
[사용자 요청: "내일 광주에서 제주로 항공편 알려줘"]
                    ↓
┌─────────────────────────────────────┐
│ Advisor #1: 요청 로깅               │  ← "어떤 질문이 들어왔는지" 기록
└─────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────┐
│ Advisor #2: 실행 시간 측정 시작     │  ← 타이머 시작
└─────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────┐
│ ChatClient → LLM 호출               │
│   LLM이 Tool 선택: FlightSearchTool │
│   Tool 실행: searchFlightsByAirline │
│   LLM이 자연어로 응답 생성          │
└─────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────┐
│ Advisor #2: 실행 시간 측정 종료     │  ← 타이머 종료, 소요 시간 계산
└─────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────┐
│ Advisor #1: 응답 로깅               │  ← "어떤 답변이 나갔는지" 기록
└─────────────────────────────────────┘
                    ↓
[사용자에게 응답 전달]
```

Spring MVC Filter와의 비교:

```text
Spring MVC:  [HTTP 요청] → [Filter] → [Interceptor] → [Controller] → [Service]
Spring AI:   [사용자 질문] → [Advisor #1] → [Advisor #2] → [ChatClient → LLM]
```

---

## ChatClient Advisor란?

**ChatClient Advisor**는 ChatClient의 요청/응답 과정에 개입하여 로깅, 수정, 모니터링할 수 있는 메커니즘입니다.

### Advisor가 필요한 이유

| 이유 | 설명 | 예시 |
|------|------|------|
| **디버깅** | LLM에 보낸 요청과 최종 응답 확인 | "프롬프트가 의도대로 전달되었나?" |
| **성능 모니터링** | 각 단계의 실행 시간 측정 | "이 Tool이 너무 느려" |
| **보안 로깅** | 어떤 요청이 들어왔는지 기록 | "민감 정보가 노출되었나?" |
| **요청/응답 수정** | 프롬프트나 결과를 가공 | "응답이 너무 길어서 줄여야겠어" |

### Advisor 실행 타이밍

```text
┌───────────────────────────────────────────────────────────┐
│  사용자 질문                                              │
│  "내일 광주에서 제주로 가는 항공편 알려줘"                │
└───────────────────────────────────────────────────────────┘
                    ↓
┌───────────────────────────────────────────────────────────┐
│  Advisor: 요청 로깅                                       │
│  - 사용자 요청 기록                                       │
└───────────────────────────────────────────────────────────┘
                    ↓
┌───────────────────────────────────────────────────────────┐
│  LLM 분석 및 Tool 선택                                    │
│  → FlightSearchTool 선택                                  │
└───────────────────────────────────────────────────────────┘
                    ↓
┌───────────────────────────────────────────────────────────┐
│  Tool 실행                                                │
│  searchFlightsByAirline("광주", "제주", "내일")           │
└───────────────────────────────────────────────────────────┘
                    ↓
┌───────────────────────────────────────────────────────────┐
│  Advisor: 응답 로깅                                       │
│  - 최종 응답 내용 기록                                    │
│  (Tool 실행은 ChatModel 내부에서 처리되므로               │
│   Advisor는 Tool 호출 이후의 최종 응답을 받음)            │
└───────────────────────────────────────────────────────────┘
```

---

## Step 4-1: SimpleLoggerAdvisor 적용

### 왜 이렇게 할까요?

Step 3에서 Function Calling을 구현했지만, LLM이 실제로 어떤 Tool을 선택했는지, 어떤 파라미터를 넘겼는지, Tool 실행에 얼마나 시간이 걸렸는지 알기 어렵습니다. 콘솔에 아무 로그도 출력되지 않기 때문에, 문제가 발생해도 원인을 파악할 수 없습니다.

`SimpleLoggerAdvisor`는 Spring AI에서 기본으로 제공하는 Advisor로, LLM에게 보낸 요청과 받은 응답을 자동으로 로깅합니다. 다만 기본 설정에서는 Tool 실행이 ChatModel 내부에서 처리되므로, Advisor에는 첫 요청과 Tool 실행 후의 최종 응답만 보입니다. 어떤 Tool이 어떤 인자로 실행됐는지는 Tool 메서드 안의 로그(Step 4-2)로 확인합니다.

> **참고**: Spring AI 1.1에는 Tool 호출 루프를 ChatModel 대신 Advisor 체인에서 수행하는 `ToolCallAdvisor`도 있습니다. 이를 등록하면 그 뒤에 오는 Advisor가 Tool 호출 단계마다 요청/응답을 볼 수 있습니다. 이 교재에서는 기본 방식(ChatModel 내부 실행)을 사용합니다.

> **Spring Boot 복습**: `SimpleLoggerAdvisor`는 Spring MVC의 `CommonsRequestLoggingFilter`와 비슷한 역할을 합니다. 필터를 추가하면 모든 HTTP 요청이 자동으로 로깅되듯, Advisor를 추가하면 모든 LLM 요청/응답이 자동으로 로깅됩니다. Step 2에서 본 `ChatClientConfig`의 빌더에 `.defaultAdvisors()` 한 줄만 추가하면 됩니다.

### ChatClientConfig에 SimpleLoggerAdvisor 추가

실제 프로젝트의 `ChatClientConfig`는 이미 두 ChatClient 모두에 `SimpleLoggerAdvisor`를 등록해 두었습니다.

```java
@Configuration  // ① Spring 설정 클래스 (Step 2에서 이미 배웠습니다)
public class ChatClientConfig {

    @Bean
    public ChatClient ollamaChatClient(@Qualifier("ollamaChatModel") ChatModel ollamaChatModel) {  // ② Ollama 모델 주입
        return ChatClient.builder(ollamaChatModel)          // ③ 빌더 생성
                .defaultAdvisors(new SimpleLoggerAdvisor()) // ④ 로깅 Advisor 추가 (이번 단계의 핵심)
                .build();                                   // ⑤ ChatClient 완성
    }

    @Bean
    public ChatClient geminiChatClient(@Qualifier("googleGenAiChatModel") ChatModel geminiChatModel) {
        return ChatClient.builder(geminiChatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}
```

Tool과 달리 Advisor는 `defaultAdvisors`로 **기본 등록**했다는 점에 주목하세요. 로깅은 Tool이 있든 없든 모든 LLM 호출(검색 조건 추출, 작업 계획, 결과 요약, Tool Calling)에 필요하고, 등록해도 LLM의 판단에는 영향을 주지 않기 때문입니다. 반대로 Tool은 LLM의 판단을 바꾸므로 필요한 요청에서만 `.tools(...)`로 넘깁니다(Step 3).

### SimpleLoggerAdvisor가 로깅하는 정보

`SimpleLoggerAdvisor`는 DEBUG 레벨로 요청(`ChatClientRequest`)과 응답(`ChatResponse`의 JSON)을 출력합니다. 아래는 핵심만 줄여서 표시한 예시입니다.

```text
DEBUG o.s.a.c.c.advisor.SimpleLoggerAdvisor - request: ChatClientRequest[prompt=Prompt{messages=[UserMessage{content='내일 광주에서 제주로 가는 항공편 알려줘', ...}], ...}, context={}]

DEBUG o.s.a.c.c.advisor.SimpleLoggerAdvisor - response: { "result" : { "output" : { "text" : "네, 내일 광주에서 제주로 가는 항공편을 찾았어요...", ... } } }
```

---

## Step 4-2: Tool 실행 로그 확인

### application.yml 로그 설정

```yaml
logging:
  level:
    org.springframework.ai: DEBUG
    net.java21.flyschedule: DEBUG
```

> 이 프로젝트는 `src/main/resources/logback-spring.xml`로 로그를 설정합니다. `net.java21.flyschedule` 패키지는 기본 프로필에서 DEBUG로 출력되므로, `SimpleLoggerAdvisor` 로그를 보려면 `org.springframework.ai.chat.client.advisor` 로거의 레벨만 DEBUG로 올리면 됩니다.

### 콘솔 로그 예시

```text
DEBUG o.s.a.c.c.advisor.SimpleLoggerAdvisor - request: ChatClientRequest[prompt=Prompt{messages=[UserMessage{content='내일 광주에서 제주로 가는 항공편 알려줘', ...}], ...}]

INFO  n.j.flyschedule.mcp.FlightSearchTool - MCP Tool 호출: searchFlightsByAirline(departure=광주, arrival=제주, date=2026-10-16)
INFO  n.j.flyschedule.mcp.FlightSearchTool - MCP Tool 응답: 2개 항공사, 6편 (항공사별 최대 3편)

DEBUG o.s.a.c.c.advisor.SimpleLoggerAdvisor - response: { "result" : { "output" : { "text" : "네, 내일 광주에서 제주로 가는 항공편을 찾았어요...", ... } } }
```

### 로그 대신 실행 기록으로 남기기: ToolResultCapture

로그는 개발자가 콘솔에서 볼 때는 충분하지만, 화면에 "LLM이 어떤 Tool을 어떤 인자로 불렀는가"를 보여 주려면 Tool 호출을 데이터로 모아야 합니다. 앞에서 보았듯 기본 설정의 Advisor에는 Tool 호출 단계가 보이지 않으므로, 이 프로젝트는 Tool 메서드가 직접 호출 기록을 남기게 했습니다.

`src/main/java/net/java21/flyschedule/mcp/ToolResultCapture.java` (주석 생략)

```java
public final class ToolResultCapture {

    public record ToolCall(String toolName, String arguments, Object result, String error,
                           long startNanos, long endNanos) {

        public boolean failed() {
            return error != null;
        }
    }

    private static final ThreadLocal<List<ToolCall>> CALLS = ThreadLocal.withInitial(ArrayList::new);

    public static <T> T capture(String toolName, String arguments, Supplier<T> action) {
        long start = System.nanoTime();
        try {
            T result = action.get();                       // Tool 작업을 여기서 실행
            CALLS.get().add(new ToolCall(toolName, arguments, result, null, start, System.nanoTime()));
            return result;                                 // 결과를 그대로 돌려주므로 return 문에 바로 쓸 수 있음
        } catch (RuntimeException e) {
            CALLS.get().add(new ToolCall(toolName, arguments, null, e.getMessage(), start, System.nanoTime()));
            throw e;                                       // 실패로 기록한 뒤 다시 던짐 (Spring AI가 LLM에게 오류 전달)
        }
    }

    public static List<ToolCall> getAndClear() {
        List<ToolCall> calls = List.copyOf(CALLS.get());   // 호출 순서 유지
        CALLS.remove();
        return calls;
    }
}
```

- 모든 `@Tool` 메서드는 `return ToolResultCapture.capture("getAirportCode", airportName, () -> airportListAgent.getAirportCode(airportName));`처럼 실제 작업을 `Supplier`로 넘깁니다. `capture()`가 작업을 직접 실행하므로 실행 시작·종료 시각을 잴 수 있고, 예외가 나도 "실패한 호출"로 기록할 수 있습니다
- `ToolCallingSearchService`는 `call()`이 끝난 뒤 `getAndClear()`로 기록을 꺼내, LLM 단계 다음에 Tool 호출마다 TOOL 단계(실제 실행 구간)를 남깁니다 (Step 10)
- `ThreadLocal`을 쓸 수 있는 이유: Spring AI 1.1.4의 `call()`은 Tool을 호출한 스레드에서 순서대로 실행합니다. `stream()`은 다른 스레드에서 Tool을 실행하므로 이 방식을 쓸 수 없습니다

---

## Step 4-3: 사용자 정의 로깅이 필요한 경우

### 왜 이렇게 할까요?

`SimpleLoggerAdvisor`는 기본적인 요청/응답 로깅만 제공합니다. 하지만 실제 개발에서는 다음과 같은 추가 정보가 필요할 수 있습니다.

- **실행 시간**: LLM 호출에 얼마나 시간이 걸렸는지 측정하고 싶을 때
- **요청 추적**: 여러 요청이 동시에 들어올 때 각 요청을 구분하고 싶을 때
- **상세 로깅**: Tool의 입력값과 출력값을 더 자세히 기록하고 싶을 때

이럴 때는 Spring AI의 `CallAdvisor` 인터페이스를 구현하여 나만의 Advisor를 만들 수 있습니다. Spring MVC에서 `HandlerInterceptor`를 구현하여 커스텀 로깅을 만들었던 것과 동일한 방식입니다.

> **Spring Boot 복습**: `CallAdvisor`는 AOP(관점 지향 프로그래밍)의 Around Advice와 같은 패턴입니다. `adviseCall()` 안에서 호출하는 `chain.nextCall()`이 실제 LLM 호출이며, 그 앞뒤로 원하는 로직을 실행할 수 있습니다. 이것은 Servlet Filter의 `doFilter()` 호출이나 Spring Interceptor의 `preHandle()`/`postHandle()`과 동일한 구조입니다. (Spring AI 1.1에서는 동기 호출용 `CallAdvisor`와 스트리밍용 `StreamAdvisor`(`adviseStream()`)로 나뉩니다.)

### CustomLoggingAdvisor 구현

```java
package net.java21.flyschedule.config;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 사용자 정의 로깅 Advisor
 */
@Component  // ① Spring Bean으로 등록하여 다른 곳에서 주입받을 수 있게 함
public class CustomLoggingAdvisor implements CallAdvisor {  // ② CallAdvisor 인터페이스 구현

    private static final Logger log = LoggerFactory.getLogger(CustomLoggingAdvisor.class);

    private final Map<String, Long> startTimeMap = new ConcurrentHashMap<>();  // ③ 요청별 시작 시간 저장 (동시 요청 대비)

    @Override
    public String getName() {  // ④ Advisor의 이름 반환 (로그에서 식별용)
        return "CustomLoggingAdvisor";
    }

    @Override
    public int getOrder() {  // ⑤ 실행 순서: 값이 작을수록 먼저(바깥쪽에서) 실행
        return 0;
    }

    @Override
    public ChatClientResponse adviseCall(
            ChatClientRequest request,   // ⑥ 사용자 메시지와 옵션을 담은 요청(Prompt + context)
            CallAdvisorChain chain) {    // ⑦ 다음 Advisor 또는 실제 LLM 호출을 수행하는 체인

        // ⑧ 고유한 요청 ID 생성 (여러 요청이 동시에 들어와도 구분 가능)
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        startTimeMap.put(requestId, System.currentTimeMillis());  // ⑨ 시작 시간 기록

        log.info("========================================");
        log.info("요청 ID: {}", requestId);
        log.info("사용자 요청: {}", request.prompt().getUserMessage().getText());  // ⑩ 사용자 질문 내용 로깅
        log.info("========================================");

        try {
            // ⑪ 핵심: chain.nextCall()이 실제 LLM 호출을 수행합니다
            // 이 줄 앞의 코드는 "요청 전 처리", 뒤의 코드는 "응답 후 처리"가 됩니다
            ChatClientResponse response = chain.nextCall(request);

            long duration = System.currentTimeMillis() - startTimeMap.get(requestId);  // ⑫ 소요 시간 계산

            log.info("========================================");
            log.info("응답 ID: {}", requestId);
            log.info("전체 실행 시간: {}ms", duration);  // ⑬ LLM 호출 + Tool 실행 포함 전체 시간
            log.info("응답 내용: {}", response.chatResponse().getResult().getOutput().getText());  // ⑭ LLM이 생성한 답변
            log.info("========================================");

            return response;

        } finally {
            startTimeMap.remove(requestId);  // ⑮ 메모리 누수 방지: 요청 완료 후 정리
        }
    }
}
```

### ChatClientConfig에 Custom Advisor 등록

`ollamaChatClient()` 메서드의 파라미터에 `CustomLoggingAdvisor customLoggingAdvisor`를 추가해 주입받은 뒤 등록합니다.

```java
@Bean
public ChatClient ollamaChatClient(@Qualifier("ollamaChatModel") ChatModel ollamaChatModel,
                                   CustomLoggingAdvisor customLoggingAdvisor) {
    return ChatClient.builder(ollamaChatModel)
            .defaultAdvisors(
                    new SimpleLoggerAdvisor(),
                    customLoggingAdvisor  // 사용자 정의 Advisor (파라미터로 주입)
            )
            .build();
}
```

특정 요청에만 Advisor를 적용하고 싶다면 `chatClient.prompt().advisors(customLoggingAdvisor)...`처럼 요청 단위로 넘길 수도 있습니다.

---

## Step 4-4: 테스트

### 기존 컨트롤러로 테스트

LLM이 Tool을 호출하는 과정을 보려면 Tool Calling 에이전트 엔드포인트를 사용합니다. (Coordinator 엔드포인트는 정규식으로 처리되면 LLM을 부르지 않으므로 Advisor 로그가 남지 않습니다.)

```bash
curl -X POST http://localhost:8080/api/search/tool-calling \
  -H "Content-Type: application/json" \
  -d '{"message": "내일 광주에서 제주로 가는 항공편 알려줘"}'
```

응답 JSON의 `trace`에는 `kind`가 `LLM`인 단계("Tool 선택과 답변 작성")가 먼저, 그 안에서 실행된 `TOOL` 단계(`searchFlightsByAirline`, "LLM이 요청한 Tool 실행")가 이어서 담겨 있습니다. TOOL 단계의 시작 시각과 소요 시간은 LLM 단계 구간 안에 들어갑니다. 콘솔 로그와 비교해 보세요.

### 콘솔에서 확인되는 로그

```text
========================================
요청 ID: a1b2c3d4
사용자 요청: 내일 광주에서 제주로 가는 항공편 알려줘
========================================

DEBUG o.s.a.c.c.advisor.SimpleLoggerAdvisor - request: ChatClientRequest[prompt=Prompt{messages=[UserMessage{content='내일 광주에서 제주로 가는 항공편 알려줘', ...}], ...}]

INFO  n.j.flyschedule.mcp.FlightSearchTool - MCP Tool 호출: searchFlightsByAirline(departure=광주, arrival=제주, date=2026-10-16)
INFO  n.j.flyschedule.mcp.FlightSearchTool - MCP Tool 응답: 2개 항공사, 6편 (항공사별 최대 3편)

DEBUG o.s.a.c.c.advisor.SimpleLoggerAdvisor - response: { "result" : { "output" : { "text" : "네, 내일 광주에서 제주로 가는 항공편을 찾았어요...", ... } } }

========================================
응답 ID: a1b2c3d4
전체 실행 시간: 5234ms
응답 내용: 네, 내일 광주에서 제주로 가는 항공편을 찾았어요...
========================================
```

---

## Step 4-5: 성능 모니터링

### PerformanceMonitoringAdvisor 구현

```java
package net.java21.flyschedule.config;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 성능 모니터링 Advisor
 */
@Component
public class PerformanceMonitoringAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(PerformanceMonitoringAdvisor.class);

    private final ConcurrentHashMap<String, AtomicInteger> callCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> totalDurations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> minDurations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> maxDurations = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "PerformanceMonitoringAdvisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {

        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString().substring(0, 8);

        try {
            ChatClientResponse response = chain.nextCall(request);

            long duration = System.currentTimeMillis() - startTime;
            recordCall("ChatClient", duration);

            log.info("요청 ID: {}, 실행 시간: {}ms", requestId, duration);

            return response;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("요청 ID: {}, 실행 실패, 소요 시간: {}ms", requestId, duration, e);
            throw e;
        }
    }

    private void recordCall(String name, long duration) {
        callCounts.computeIfAbsent(name, k -> new AtomicInteger(0)).incrementAndGet();
        totalDurations.computeIfAbsent(name, k -> new AtomicLong(0)).addAndGet(duration);
        minDurations.computeIfAbsent(name, k -> new AtomicLong(Long.MAX_VALUE))
                .updateAndGet(current -> Math.min(current, duration));
        maxDurations.computeIfAbsent(name, k -> new AtomicLong(0))
                .updateAndGet(current -> Math.max(current, duration));
    }

    public void printStatistics() {
        log.info("==================== 성능 통계 ====================");

        callCounts.forEach((name, count) -> {
            long total = totalDurations.get(name).get();
            long min = minDurations.get(name).get();
            long max = maxDurations.get(name).get();
            double avg = (double) total / count.get();

            log.info("이름: {}", name);
            log.info("  호출 횟수: {}", count.get());
            log.info("  평균 실행 시간: {}ms", String.format("%.2f", avg));  // SLF4J는 {:.2f} 형식을 지원하지 않음
            log.info("  최소/최대: {}ms / {}ms", min, max);
            log.info("--------------------------------------------------");
        });

        log.info("======================================================");
    }

    public void reset() {
        callCounts.clear();
        totalDurations.clear();
        minDurations.clear();
        maxDurations.clear();
    }
}
```

### 통계 확인용 컨트롤러

```java
@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor  // Lombok: final 필드 생성자 주입
public class MonitoringController {

    private final PerformanceMonitoringAdvisor performanceAdvisor;

    @GetMapping("/stats")
    public String getStatistics() {
        performanceAdvisor.printStatistics();
        return "통계가 콘솔에 출력되었습니다.";
    }

    @GetMapping("/stats/reset")
    public String resetStatistics() {
        performanceAdvisor.reset();
        return "통계가 초기화되었습니다.";
    }
}
```

### 성능 통계 출력 예시

```text
==================== 성능 통계 ====================
이름: ChatClient
  호출 횟수: 25
  평균 실행 시간: 4823.45ms
  최소/최대: 1234ms / 8756ms
--------------------------------------------------
======================================================
```

---

## Step 4-6: 고급 Advisor 패턴

### 패턴 1: 요청 캐싱 (CachingAdvisor)

아래 예시는 Caffeine 캐시 라이브러리(`com.github.ben-manes.caffeine:caffeine`) 의존성이 있다고 가정합니다.

```java
@Slf4j
@Component
public class CachingAdvisor implements CallAdvisor {

    private final Cache<String, ChatClientResponse> cache =
        Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    @Override
    public String getName() {
        return "CachingAdvisor";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;  // 가장 먼저 실행
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {

        String cacheKey = request.prompt().getUserMessage().getText();

        ChatClientResponse cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            log.info("캐시 히트: {}", cacheKey);
            return cached;
        }

        ChatClientResponse response = chain.nextCall(request);
        cache.put(cacheKey, response);

        return response;
    }
}
```

### 패턴 2: 재시도 (RetryAdvisor)

> **참고**: Spring AI는 모델 호출 단계에서 이미 재시도를 합니다. 이 프로젝트는 `application.yml`의 `spring.ai.retry`로 최대 2회, 1~5초 간격으로 줄여 두었습니다(Step 1). 아래 `RetryAdvisor`는 Advisor 구조를 익히기 위한 예제이며, 함께 쓰면 재시도가 곱절로 늘어날 수 있으니 주의하세요.

```java
@Slf4j
@Component
public class RetryAdvisor implements CallAdvisor {

    private static final int MAX_RETRIES = 3;

    @Override
    public String getName() {
        return "RetryAdvisor";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {

        Exception lastException = null;

        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                // 같은 체인을 여러 번 호출하려면 copy(this)로 이후 체인을 복제해서 사용합니다
                return chain.copy(this).nextCall(request);
            } catch (Exception e) {
                lastException = e;
                log.warn("실패 {}회, 재시도 중...", i + 1);

                if (i < MAX_RETRIES - 1) {
                    try {
                        Thread.sleep(1000L * (i + 1));  // 선형 백오프 (1초, 2초, ...)
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("재시도 중단", ie);
                    }
                }
            }
        }

        throw new RuntimeException("최대 재시도 횟수 초과", lastException);
    }
}
```

### 패턴 3: 에러 핸들링 (ErrorHandlingAdvisor)

```java
@Slf4j
@Component
public class ErrorHandlingAdvisor implements CallAdvisor {

    @Override
    public String getName() {
        return "ErrorHandlingAdvisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {

        try {
            return chain.nextCall(request);

        } catch (Exception e) {
            log.error("LLM 호출 실패", e);

            // 사용자 친화적인 에러 메시지 생성
            String errorMessage = "죄송합니다. 일시적인 오류가 발생했습니다. " +
                                  "잠시 후 다시 시도해 주세요.";

            throw new RuntimeException(errorMessage, e);
        }
    }
}
```

---

## Advisor 활용 시나리오

### 시나리오 1: 개발 환경

| 목적 | Advisor 동작 |
|------|---------------|
| 디버깅 | `SimpleLoggerAdvisor`로 상세 로그 출력 |
| 파라미터 확인 | `CustomLoggingAdvisor`로 요청/응답 로깅 |
| 실행 시간 측정 | `PerformanceMonitoringAdvisor` 적용 |

### 시나리오 2: 운영 환경

| 목적 | Advisor 동작 |
|------|---------------|
| 모니터링 | `PerformanceMonitoringAdvisor`로 통계만 수집 |
| 안정성 | `RetryAdvisor`, `ErrorHandlingAdvisor` 적용 |
| 최적화 | `CachingAdvisor`로 응답 캐싱 |

---

## 일반적인 문제 해결

### 문제 1: 로그가 출력되지 않음

**증상:** SimpleLoggerAdvisor가 동작하지 않음

**해결 방법:**
1. `application.yml`에 로그 레벨 설정 확인
2. Advisor가 ChatClient에 등록되었는지 확인
3. Spring Boot 로그 설정 확인

```yaml
logging:
  level:
    org.springframework.ai: DEBUG
```

### 문제 2: 성능 저하

**증상:** Advisor 추가 후 응답 시간이 느려짐

**해결 방법:**
1. 불필요한 Advisor 제거
2. 로그 레벨을 WARN으로 조정
3. 캐싱 적용

### 문제 3: Advisor 실행 순서

**증상:** 예상과 다른 순서로 Advisor가 실행됨

**해결 방법:**

Advisor의 실행 순서는 각 Advisor의 `getOrder()` 값으로 결정됩니다. 값이 작을수록 먼저(바깥쪽에서) 실행되며, 값이 같을 때만 등록 순서를 따릅니다. `SimpleLoggerAdvisor`의 기본 order는 `0`이므로, 원하는 순서가 있다면 `getOrder()` 값을 명시적으로 지정합니다.

```java
.defaultAdvisors(
    new CachingAdvisor(),          // order: HIGHEST_PRECEDENCE       → 1. 먼저 캐시 확인
    new RetryAdvisor(),            // order: HIGHEST_PRECEDENCE + 100 → 2. 실패 시 재시도
    new SimpleLoggerAdvisor()      // order: 0                         → 3. 마지막으로 로깅
)
```

---

## 실습 과제

1. **(기본)** `org.springframework.ai.chat.client.advisor` 로거를 DEBUG로 올리고 `POST /api/search/tool-calling`을 호출하세요. `SimpleLoggerAdvisor`의 request 로그에 Tool 정의가 포함되는지, `POST /api/search/coordinator`에 영어 문장을 보냈을 때의 request 로그에는 Tool 정의가 없는지 비교하세요.
2. **(기본)** 본문의 `CustomLoggingAdvisor`를 만들어 `ollamaChatClient`에 등록하고, Orchestrator 패턴으로 검색했을 때 한 번의 요청에서 LLM 호출이 몇 번 일어나는지 로그로 세어 보세요.
3. **(도전)** `ToolResultCaptureTest`의 `capturesInOrder()`와 `recordsFailure()`를 읽고, 성공하는 `Supplier`와 예외를 던지는 `Supplier`를 차례로 `capture()`했을 때 `getAndClear()`가 두 건을 호출 순서대로 돌려주며 두 번째 기록만 `failed()`가 `true`이고 `error`에 예외 메시지가 담기는지 확인하는 테스트를 작성하세요.
4. **(도전)** `ToolCallAdvisor`를 등록해 Tool 호출 루프를 Advisor 체인에서 수행하게 바꾸고, `SimpleLoggerAdvisor` 로그에 Tool 호출 단계마다 요청/응답이 찍히는지 확인하세요. 실험이 끝나면 원래대로 되돌리세요.

---

## 요약

이번 단계에서는 Advisor를 사용하여 Function Calling의 실행 과정을 모니터링하는 방법을 배웠습니다:

1. **SimpleLoggerAdvisor**: Spring AI에서 제공하는 기본 로깅 Advisor로, `.defaultAdvisors(new SimpleLoggerAdvisor())` 한 줄로 요청/응답 로깅을 활성화했습니다. Advisor는 모든 호출에 필요하므로 기본 등록하고, Tool은 필요한 요청에만 넘깁니다
2. **ToolResultCapture**: 기본 Advisor에는 Tool 호출이 보이지 않으므로, Tool 메서드가 `capture(이름, 인자 요약, () -> 작업)`으로 작업을 실행하면서 호출 기록(결과·실패·실행 시각)을 남기고, 서비스가 `getAndClear()`로 꺼내 실행 기록에 씁니다
3. **CallAdvisor 인터페이스**: `adviseCall()` 안에서 `chain.nextCall()` 앞뒤로 로직을 실행하는 Around Advice 패턴으로, 실행 시간 측정이나 상세 로깅 같은 커스텀 로직을 구현했습니다
4. **Advisor 파이프라인**: 여러 Advisor를 등록하면 `getOrder()` 값이 작은 순서대로 실행되며, 응답은 역순으로 처리됩니다
5. **실전 활용**: 개발 환경에서는 상세 로깅, 운영 환경에서는 성능 모니터링과 캐싱 Advisor를 조합하여 사용합니다

여기까지 Step 1~4를 통해 Spring AI의 기본 설정부터 ChatClient 사용, Function Calling, 그리고 실행 과정 모니터링까지 모두 학습했습니다.

---

## 체크리스트

- [ ] `SimpleLoggerAdvisor`가 ChatClient에 등록됨 (`ChatClientConfig`에서 확인)
- [ ] Advisor는 기본 등록하고 Tool은 요청마다 넘기는 이유를 설명할 수 있음
- [ ] `ToolResultCapture.capture()`와 `getAndClear()`의 역할을 이해함
- [ ] 콘솔에서 요청/응답 로그 확인됨
- [ ] 로그 레벨이 DEBUG로 설정됨
- [ ] `CustomLoggingAdvisor`가 필요에 따라 구현됨
- [ ] `PerformanceMonitoringAdvisor`로 성능 측정
- [ ] 운영 환경에 맞는 Advisor 구성 완료

---

## 다음 단계

[**Step 5: 다중 Function Calling과 MCP Tool 패턴**](step-5-mcp-tool-pattern.md)에서 여러 Tool을 효과적으로 관리하는 방법을 학습합니다.

---

## 참고 자료

- [Spring AI ChatClient 공식 문서](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [ChatClient.Advisor API](https://docs.spring.io/spring-ai/reference/api/chatclient.html#_advisors)
- [Spring AI Advisors API](https://docs.spring.io/spring-ai/reference/api/advisors.html)
- [Spring AI Observability](https://docs.spring.io/spring-ai/reference/observability/index.html)

---

## 심화 학습

### Advisor 실행 순서 이해

Advisor는 **파이프라인 패턴**으로 실행됩니다. 순서는 `getOrder()` 값이 작은 Advisor부터입니다.

```text
요청 → [Advisor1] → [Advisor2] → [Advisor3] → ChatModel
응답 ← [Advisor1] ← [Advisor2] ← [Advisor3] ← ChatModel
```

```java
.defaultAdvisors(
    new CachingAdvisor(),           // 1. 요청 → 캐시 확인 (HIGHEST_PRECEDENCE)
    new RetryAdvisor(),             // 2. 요청 → 재시도 로직 (HIGHEST_PRECEDENCE + 100)
    new SimpleLoggerAdvisor()       // 3. 요청/응답 → 로깅 (0)
)
```

**실행 순서:**
1. **요청 시:** CachingAdvisor → RetryAdvisor → SimpleLoggerAdvisor → ChatModel
2. **응답 시:** SimpleLoggerAdvisor → RetryAdvisor → CachingAdvisor → 사용자

### Spring AI 내장 Advisor

| Advisor | 용도 | 설명 |
|---------|------|------|
| `SimpleLoggerAdvisor` | 로깅 | 요청/응답을 간단히 로깅 |
| `MessageChatMemoryAdvisor` | 메모리 | 대화 기록을 메시지 목록으로 프롬프트에 추가 |
| `PromptChatMemoryAdvisor` | 메모리 | 대화 기록을 시스템 프롬프트 텍스트에 추가 |
| `SafeGuardAdvisor` | 보안 | 금지어가 포함된 요청을 차단 |
| `QuestionAnswerAdvisor` | RAG | 문서 검색 결과를 컨텍스트에 추가 (`spring-ai-advisors-vector-store` 모듈) |
| `VectorStoreChatMemoryAdvisor` | 메모리(벡터 저장소) | 대화 기록을 벡터 DB에 저장하고 관련 기록을 검색 (`spring-ai-advisors-vector-store` 모듈) |

### 사용자 정의 Advisor 구현 시 주의사항

1. **순서 의존성:** 캐싱은 가장 먼저, 로깅은 가장 나중에
2. **스레드 안전성:** 여러 요청이 동시에 처리됨을 고려
3. **성능 고려:** 무거운 로직은 비동기로 처리
4. **에러 처리:** 적절한 예외 처리 및 로깅

```java
// 좋은 예
@Override
public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {

    try {
        // 전처리
        log.debug("요청 수신: {}", request.prompt().getUserMessage().getText());

        // 체인 호출
        ChatClientResponse response = chain.nextCall(request);

        // 후처리
        log.debug("응답 반환: {}", response.chatResponse().getResult().getOutput().getText());

        return response;

    } catch (Exception e) {
        log.error("Advisor 실행 중 오류", e);
        throw e;  // 반드시 다시 던지기
    }
}
```
