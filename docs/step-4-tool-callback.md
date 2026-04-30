# Step 4: Tool Callback과 실행 과정 이해

## 학습 목표
이 단계에서는 **Tool Calling Callback**을 사용하여 Function Calling의 실행 과정을 모니터링하고 디버깅하는 방법을 학습합니다.

**학습 완료 후 할 수 있는 것:**
- `ToolCallingCallback` 구현
- Function Calling 실행 과정 로깅
- LLM의 Tool 선택 과정 이해
- 디버깅 및 성능 최적화

---

## Tool Calling Callback이란?

**Tool Calling Callback**은 Function Calling의 **모든 단계**를 가로채서 로깅, 모니터링, 수정할 수 있는 인터페이스입니다.

### Callback이 필요한 이유

| 이유 | 설명 | 예시 |
|------|------|------|
| **디버깅**| LLM이 어떤 Tool을 선택했는지 확인 | "왜 다른 Tool이 호출되었지?" |
| **성능 모니터링**| 각 Tool의 실행 시간 측정 | "이 Tool이 너무 느려" |
| **보안 로깅**| 어떤 파라미터로 호출되었는지 기록 | "민감 정보가 노출되었나?" |
| **결과 수정**| Tool 실행 결과를 가공 | "응답이 너무 길어서 줄여야겠어" |

### Callback 실행 타이밍

```
┌───────────────────────────────────────────────────────────┐
│  사용자 질문                                               │
│  "내일 광주에서 제주로 가는 항공편 알려줘"                 │
└───────────────────────────────────────────────────────────┘
                    ↓
┌───────────────────────────────────────────────────────────┐
│  Callback: onRequest()                                    │
│  - 사용자 요청 로깅                                        │
└───────────────────────────────────────────────────────────┘
                    ↓
┌───────────────────────────────────────────────────────────┐
│  LLM 분석 및 Tool 선택                                     │
│  → searchFlightsByAirline 선택                            │
└───────────────────────────────────────────────────────────┘
                    ↓
┌───────────────────────────────────────────────────────────┐
│  Callback: beforeToolCall()                               │
│  - Tool 이름 로깅                                          │
│  - 파라미터 로깅                                           │
│  - 시작 시간 기록                                          │
└───────────────────────────────────────────────────────────┘
                    ↓
┌───────────────────────────────────────────────────────────┐
│  Tool 실행                                                 │
│  searchFlightsByAirline("광주", "제주", "내일")            │
└───────────────────────────────────────────────────────────┘
                    ↓
┌───────────────────────────────────────────────────────────┐
│  Callback: afterToolCall()                                │
│  - 실행 결과 로깅                                          │
│  - 실행 시간 계산                                          │
│  - 결과 가공 (선택)                                        │
└───────────────────────────────────────────────────────────┘
                    ↓
┌───────────────────────────────────────────────────────────┐
│  Callback: onResponse()                                   │
│  - 최종 응답 로깅                                          │
│  - 전체 실행 시간 계산                                     │
└───────────────────────────────────────────────────────────┘
```

---

## Step 4-1: LoggingCallback 구현

### LoggingCallback.java 생성

```java
package com.nhnacademy.flyschedule.config;

import org.springframework.ai.chat.client.advisor.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.CallAroundAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ChatResponseMetadata;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tool Calling 실행 과정을 로깅하는 Callback
 *
 * Spring AI 1.1.2의 ToolCallingCallback을 사용하여
 * Function Calling의 모든 단계를 모니터링합니다.
 */
@Component
public class LoggingCallback {

    private static final Logger log = LoggerFactory.getLogger(LoggingCallback.class);

    // 각 Tool의 실행 시간을 저장
    private final Map<String, Long> toolExecutionTimes = new ConcurrentHashMap<>();

    /**
     * Tool 호출 전 실행되는 메서드
     *
     * @param toolName Tool 이름
     * @param arguments Tool 파라미터
     */
    public void beforeToolCall(String toolName, Map<String, Object> arguments) {
        log.info("========================================");
        log.info(" Tool 호출 시작");
        log.info("Tool 이름: {}", toolName);
        log.info("파라미터: {}", arguments);
        log.info("========================================");

        toolExecutionTimes.put(toolName, System.currentTimeMillis());
    }

    /**
     * Tool 호출 후 실행되는 메서드
     *
     * @param toolName Tool 이름
     * @param result Tool 실행 결과
     */
    public void afterToolCall(String toolName, Object result) {
        Long startTime = toolExecutionTimes.get(toolName);
        long duration = startTime != null
                ? System.currentTimeMillis() - startTime
                : 0;

        log.info("========================================");
        log.info(" Tool 호출 완료");
        log.info("Tool 이름: {}", toolName);
        log.info("실행 시간: {}ms", duration);
        log.info("결과 타입: {}", result != null ? result.getClass().getSimpleName() : "null");

        // 결과가 너무 크면 요약해서 로깅
        if (result != null) {
            String resultStr = result.toString();
            if (resultStr.length() > 500) {
                log.info("결과 (요약): {}... (총 {}글자)",
                        resultStr.substring(0, 500), resultStr.length());
            } else {
                log.info("결과: {}", resultStr);
            }
        }
        log.info("========================================");

        toolExecutionTimes.remove(toolName);
    }

    /**
     * LLM 응답 생성 후 실행되는 메서드
     *
     * @param request 사용자 요청
     * @param response LLM 응답
     * @param duration 전체 실행 시간
     */
    public void onResponse(String request, ChatResponse response, long duration) {
        log.info("========================================");
        log.info(" LLM 응답 생성 완료");
        log.info("요청: {}", request);
        log.info("전체 실행 시간: {}ms ({}초)", duration, duration / 1000.0);

        // Tool 호출 횟수 확인
        if (response != null && response.getMetadata() != null) {
            ChatResponseMetadata metadata = response.getMetadata();
            log.info("모델: {}", metadata.getModel());
            log.info("토큰 사용량: {}", metadata.getUsage());
        }

        // 생성된 응답
        if (response != null && !response.getResults().isEmpty()) {
            Generation generation = response.getResults().get(0);
            String content = generation.getOutput().getContent();
            log.info("응답: {}", content);
        }
        log.info("========================================");
    }
}
```

---

## Step 4-2: ChatClient에 Callback 등록

### ChatClientConfig 수정

```java
package com.nhnacademy.flyschedule.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ChatClientConfig {

    private final LoggingCallback loggingCallback;

    public ChatClientConfig(LoggingCallback loggingCallback) {
        this.loggingCallback = loggingCallback;
    }

    @Bean
    @Primary
    public ChatClient.Builder ollamaChatClientBuilder(
            @Qualifier("ollamaChatModel") ChatModel ollamaChatModel,
            Object[] tools) {

        return ChatClient.builder(ollamaChatModel)
                .defaultTools(tools)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor()  // 기본 로깅 어드바이저
                );
    }
}
```

---

## Step 4-3: Callback이 포함된 서비스

### MonitoredChatService.java

```java
package com.nhnacademy.flyschedule.service;

import com.nhnacademy.flyschedule.config.LoggingCallback;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

@Service
public class MonitoredChatService {

    private final ChatClient chatClient;
    private final LoggingCallback loggingCallback;

    public MonitoredChatService(
            ChatClient.Builder chatClientBuilder,
            LoggingCallback loggingCallback) {

        this.chatClient = chatClientBuilder.build();
        this.loggingCallback = loggingCallback;
    }

    /**
     * Function Calling with Callback
     */
    public String chatWithMonitoring(String userMessage) {
        long startTime = System.currentTimeMillis();

        // Callback: onRequest 단계
        loggingCallback.beforeRequest(userMessage);

        // LLM 호출
        ChatResponse response = chatClient.prompt()
                .user(userMessage)
                .call()
                .chatResponse();

        long duration = System.currentTimeMillis() - startTime;

        // Callback: onResponse 단계
        loggingCallback.onResponse(userMessage, response, duration);

        return response.getResult().getOutput().getContent();
    }
}
```

---

## Step 4-4: Callback 테스트

### CallbackTestController.java

```java
package com.nhnacademy.flyschedule.controller;

import com.nhnacademy.flyschedule.service.MonitoredChatService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/callback")
public class CallbackTestController {

    private final MonitoredChatService chatService;

    public CallbackTestController(MonitoredChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Callback 테스트
     * GET /api/callback/test?message=내일광주에서제주로가는항공편알려줘
     */
    @GetMapping("/test")
    public String testCallback(@RequestParam String message) {
        return chatService.chatWithMonitoring(message);
    }
}
```

### 테스트 실행

```bash
curl "http://localhost:8080/api/callback/test?message=내일광주에서제주로가는항공편알려줘"
```

### 콘솔 로그 예시

```
========================================
 Tool 호출 시작
Tool 이름: searchFlightsByAirline
파라미터: {departure=광주, arrival=제주, date=내일}
========================================
INFO  c.n.f.service.FlightSearchAgent    : 항공편 검색 시작: 광주 -> 제주 (내일)
INFO  c.n.f.service.FlightSearchAgent    : 항공편 6편 검색 완료
========================================
 Tool 호출 완료
Tool 이름: searchFlightsByAirline
실행 시간: 2345ms
결과 타입: LinkedHashMap
결과 (요약): {아시아나항공=[FlightInfoResponse(...)], 제주항공=[...]}
========================================
========================================
 LLM 응답 생성 완료
요청: 내일광주에서제주로가는항공편알려줘
전체 실행 시간: 5234ms (5.234초)
모델: qwen2.5:latest
응답: 네, 내일 광주에서 제주로 가는 항공편을 찾았어요...
========================================
```

---

## Step 4-5: 성능 모니터링

### PerformanceMonitor 구현

```java
package com.nhnacademy.flyschedule.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tool 성능 모니터링
 */
@Component
public class PerformanceMonitor {

    private static final Logger log = LoggerFactory.getLogger(PerformanceMonitor.class);

    // Tool별 호출 횟수
    private final ConcurrentHashMap<String, AtomicInteger> callCounts = new ConcurrentHashMap<>();

    // Tool별 총 실행 시간
    private final ConcurrentHashMap<String, AtomicLong> totalDurations = new ConcurrentHashMap<>();

    // Tool별 최소/최대 실행 시간
    private final ConcurrentHashMap<String, AtomicLong> minDurations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> maxDurations = new ConcurrentHashMap<>();

    /**
     * Tool 실행 기록
     */
    public void recordToolCall(String toolName, long duration) {
        // 호출 횟수 증가
        callCounts.computeIfAbsent(toolName, k -> new AtomicInteger(0)).incrementAndGet();

        // 총 실행 시간 증가
        totalDurations.computeIfAbsent(toolName, k -> new AtomicLong(0)).addAndGet(duration);

        // 최소/최대 시간 업데이트
        minDurations.computeIfAbsent(toolName, k -> new AtomicLong(Long.MAX_VALUE))
                .updateAndGet(current -> Math.min(current, duration));

        maxDurations.computeIfAbsent(toolName, k -> new AtomicLong(0))
                .updateAndGet(current -> Math.max(current, duration));
    }

    /**
     * 성능 통계 출력
     */
    public void printStatistics() {
        log.info("==================== Tool 성능 통계 ====================");

        callCounts.forEach((toolName, count) -> {
            long total = totalDurations.get(toolName).get();
            long min = minDurations.get(toolName).get();
            long max = maxDurations.get(toolName).get();
            double avg = (double) total / count.get();

            log.info("Tool: {}", toolName);
            log.info("  호출 횟수: {}", count.get());
            log.info("  총 실행 시간: {}ms ({}초)", total, total / 1000.0);
            log.info("  평균 실행 시간: {:.2f}ms", avg);
            log.info("  최소 실행 시간: {}ms", min);
            log.info("  최대 실행 시간: {}ms", max);
            log.info("--------------------------------------------------");
        });

        log.info("======================================================");
    }

    /**
     * 통계 초기화
     */
    public void reset() {
        callCounts.clear();
        totalDurations.clear();
        minDurations.clear();
        maxDurations.clear();
        log.info("성능 통계가 초기화되었습니다.");
    }
}
```

### 성능 모니터링 결과 예시

```
==================== Tool 성능 통계 ====================
Tool: searchFlightsByAirline
  호출 횟수: 15
  총 실행 시간: 35234ms (35.234초)
  평균 실행 시간: 2348.93ms
  최소 실행 시간: 1234ms
  최대 실행 시간: 5234ms
--------------------------------------------------
Tool: searchFlightsAfterTime
  호출 횟수: 8
  총 실행 시간: 18456ms (18.456초)
  평균 실행 시간: 2307.00ms
  최소 실행 시간: 1890ms
  최대 실행 시간: 3456ms
--------------------------------------------------
Tool: searchFlightsByPriceRange
  호출 횟수: 5
  총 실행 시간: 12345ms (12.345초)
  평균 실행 시간: 2469.00ms
  최소 실행 시간: 2100ms
  최대 실행 시간: 3200ms
--------------------------------------------------
======================================================
```

---

## Step 4-6: 고급 Callback 패턴

### 패턴 1: 결과 캐싱

```java
@Component
public class CachingCallback {

    private final Map<String, Object> cache = new ConcurrentHashMap<>();

    public void afterToolCall(String toolName, Object result) {
        String cacheKey = toolName + result.hashCode();
        cache.put(cacheKey, result);
        log.info("결과 캐싱: {} (키: {})", toolName, cacheKey);
    }

    public Object getCachedResult(String toolName, String params) {
        String cacheKey = toolName + params.hashCode();
        Object cached = cache.get(cacheKey);
        if (cached != null) {
            log.info("캐시 히트: {}", cacheKey);
        }
        return cached;
    }
}
```

### 패턴 2: 결과 필터링

```java
@Component
public class ResultFilteringCallback {

    public Object afterToolCall(String toolName, Object result) {
        // 결과가 너무 크면 요약
        if (result instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) result;
            if (map.size() > 100) {
                log.warn("결과가 너무 큽니다: {}개 항목 → 50개로 제한", map.size());
                return map.entrySet().stream()
                        .limit(50)
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue
                        ));
            }
        }
        return result;
    }
}
```

### 패턴 3: 에러 핸들링

```java
@Component
public class ErrorHandlingCallback {

    public Object handleToolError(String toolName, Exception e) {
        log.error("Tool 실행 실패: {}", toolName, e);

        // 사용자 친화적인 에러 메시지 반환
        return Map.of(
                "error", true,
                "message", "일시적인 오류가 발생했습니다. 다시 시도해주세요.",
                "toolName", toolName
        );
    }
}
```

---

## 일반적인 문제 해결

### 문제 1: Callback이 호출되지 않음

**증상:**로그가 출력되지 않음

**해결 방법:**
1. Callback이 `@Component`로 등록되었는지 확인
2. Service에 Callback이 주입되었는지 확인
3. 로그 레벨이 DEBUG/INFO인지 확인

### 문제 2: 성능 저하

**증상:**Callback 추가 후 응답 시간이 느려짐

**해결 방법:**
1. 불필요한 로깅 제거
2. 비동기 로깅 사용
3. 결과 로깅 크기 제한

```java
// 비동기 로깅
@Async
public void logAsync(String message) {
    log.info(message);
}
```

### 문제 3: 메모리 누수

**증상:**장기 실행 시 메모리 사용량 증가

**해결 방법:**
1. `ConcurrentHashMap` 크기 제한
2. 주기적인 통계 초기화
3. 오래된 캐시 삭제

---

## Callback 활용 시나리오

### 시나리오 1: 개발 환경

| 목적 | Callback 동작 |
|------|---------------|
| 디버깅 | 모든 로그를 상세하게 출력 |
| 파라미터 확인 | 모든 파라미터를 로깅 |
| 결과 확인 | 전체 결과를 로깅 |

### 시나리오 2: 운영 환경

| 목적 | Callback 동작 |
|------|---------------|
| 모니터링 | 성능 통계만 수집 |
| 보안 | 민감 정보 마스킹 |
| 최적화 | 캐싱 적용 |

---

## 체크리스트

- [ ] `LoggingCallback.java`가 생성됨
- [ ] Callback이 Service에서 사용됨
- [ ] 콘솔에서 Tool 호출 로그 확인됨
- [ ] 실행 시간이 측정됨
- [ ] `PerformanceMonitor`가 구현됨
- [ ] 성능 통계가 출력됨

---

## 다음 단계

**Step 5: 다중 Function Calling과 MCP Tool 패턴**에서 여러 Tool을 효과적으로 관리하는 방법을 학습합니다.

---

## 참고 자료

- [Spring AI Callback 공식 문서](https://docs.spring.io/spring-ai/reference/api/chatclient.html#_tool_calling_callbacks)
- [ChatClient.Advisor API](https://docs.spring.io/spring-ai/reference/api/chatclient.html#_advisors)
- [Spring AI Observability](https://docs.spring.io/spring-ai/reference/observability.html)

---

## 심화 학습

### Callback vs Advisor

| 항목 | Callback | Advisor |
|------|----------|---------|
| **목적**| Tool 실행 과정 모니터링 | 요청/응답 수정 |
| **타이밍**| Tool 호출 전후 | 전체 요청/응답 |
| **사용 예시**| 로깅, 성능 측정 | 캐싱, 재시도, 보안 |
| **적용 범위**| Tool 호출 단계 | 전체 ChatClient |

### Callback 체이닝

여러 Callback을 순차적으로 실행할 수 있습니다:

```java
public class ChainedCallback {

    private final List<ToolCallingCallback> callbacks;

    public ChainedCallback(List<ToolCallingCallback> callbacks) {
        this.callbacks = callbacks;
    }

    public void beforeToolCall(String toolName, Map<String, Object> arguments) {
        callbacks.forEach(callback -> callback.beforeToolCall(toolName, arguments));
    }
}
```
