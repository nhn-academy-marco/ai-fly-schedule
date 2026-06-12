# Step 4: Tool Callback과 실행 과정 이해

## 학습 목표
이 단계에서는 **ChatClient Advisor**를 사용하여 Function Calling의 실행 과정을 모니터링하고 디버깅하는 방법을 학습합니다.

**학습 완료 후 할 수 있는 것:**
- `SimpleLoggerAdvisor`로 실행 과정 로깅
- Function Calling 실행 과정 이해
- LLM의 Tool 선택 과정 확인
- 디버깅 및 성능 최적화

---

## ChatClient Advisor란?

**ChatClient Advisor**는 ChatClient의 요청/응답 과정에 개입하여 로깅, 수정, 모니터링할 수 있는 메커니즘입니다.

### Advisor가 필요한 이유

| 이유 | 설명 | 예시 |
|------|------|------|
| **디버깅**| LLM이 어떤 Tool을 선택했는지 확인 | "왜 다른 Tool이 호출되었지?" |
| **성능 모니터링**| 각 단계의 실행 시간 측정 | "이 Tool이 너무 느려" |
| **보안 로깅**| 어떤 파라미터로 호출되었는지 기록 | "민감 정보가 노출되었나?" |
| **요청/응답 수정**| 프롬프트나 결과를 가공 | "응답이 너무 길어서 줄여야겠어" |

### Advisor 실행 타이밍

```
┌───────────────────────────────────────────────────────────┐
│  사용자 질문                                               │
│  "내일 광주에서 제주로 가는 항공편 알려줘"                 │
└───────────────────────────────────────────────────────────┘
                    ↓
┌───────────────────────────────────────────────────────────┐
│  Advisor: 요청 로깅                                       │
│  - 사용자 요청 기록                                        │
└───────────────────────────────────────────────────────────┘
                    ↓
┌───────────────────────────────────────────────────────────┐
│  LLM 분석 및 Tool 선택                                     │
│  → FlightSearchTool 선택                                  │
└───────────────────────────────────────────────────────────┘
                    ↓
┌───────────────────────────────────────────────────────────┐
│  Tool 실행                                                 │
│  FlightSearchTool.search("광주", "제주", "내일")           │
└───────────────────────────────────────────────────────────┘
                    ↓
┌───────────────────────────────────────────────────────────┐
│  Advisor: 응답 로깅                                       │
│  - Tool 실행 결과 기록                                     │
│  - 최종 응답 내용 기록                                     │
└───────────────────────────────────────────────────────────┘
```

---

## Step 4-1: SimpleLoggerAdvisor 적용

### ChatClientConfig에 SimpleLoggerAdvisor 추가

```java
package com.nhnacademy.flyschedule.config;

import com.nhnacademy.flyschedule.mcp.AirlineInfoTool;
import com.nhnacademy.flyschedule.mcp.AirportInfoTool;
import com.nhnacademy.flyschedule.mcp.FlightSearchTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * ChatClient Configuration for Spring AI
 */
@Configuration
public class ChatClientConfig {

    /**
     * Ollama ChatClient.Builder (기본)
     */
    @Bean
    @Primary
    public ChatClient.Builder ollamaChatClientBuilder(
            @Qualifier("ollamaChatModel") ChatModel ollamaChatModel,
            FlightSearchTool flightSearchTool,
            AirportInfoTool airportInfoTool,
            AirlineInfoTool airlineInfoTool) {

        return ChatClient.builder(ollamaChatModel)
                .defaultTools(flightSearchTool, airportInfoTool, airlineInfoTool)
                .defaultAdvisors(new SimpleLoggerAdvisor());  // 로깅 어드바이저 추가
    }
}
```

### SimpleLoggerAdvisor가 로깅하는 정보

```
DEBUG o.s.a.chat.client.advisor.SimpleLoggerAdvisor :
>> request: user text: "내일 광주에서 제주로 가는 항공편 알려줘"

DEBUG o.s.a.chat.client.advisor.SimpleLoggerAdvisor :
<< chat response: "네, 내일 광주에서 제주로 가는 항공편을 찾았어요..."
```

---

## Step 4-2: Tool 실행 로그 확인

### application.yml 로그 설정

```yaml
logging:
  level:
    org.springframework.ai: DEBUG
    com.nhnacademy.flyschedule: DEBUG
```

### 콘솔 로그 예시

```
DEBUG o.s.a.c.client.advisor.SimpleLoggerAdvisor :
>> request: user text: "내일 광주에서 제주로 가는 항공편 알려줘"

INFO  c.n.f.mcp.FlightSearchTool     : 항공편 검색 시작: 광주 -> 제주 (내일)
INFO  c.n.f.mcp.FlightSearchTool     : 항공편 6편 검색 완료

DEBUG o.s.a.c.client.advisor.SimpleLoggerAdvisor :
<< chat response: "네, 내일 광주에서 제주로 가는 항공편을 찾았어요..."
```

---

## Step 4-3: 사용자 정의 로깅이 필요한 경우

### CustomLoggingAdvisor 구현

```java
package com.nhnacademy.flyschedule.config;

import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 사용자 정의 로깅 어드바이저
 */
@Component
public class CustomLoggingAdvisor implements CallAroundAdvisor {

    private static final Logger log = LoggerFactory.getLogger(CustomLoggingAdvisor.class);

    private final Map<String, Long> startTimeMap = new HashMap<>();

    @Override
    public String getName() {
        return "CustomLoggingAdvisor";
    }

    @Override
    public ChatResponse aroundCall(
            UserMessage userMessage,
            ChatOptions options,
            CallAroundAdvisorChain chain) {

        String requestId = java.util.UUID.randomUUID().toString().substring(0, 8);
        startTimeMap.put(requestId, System.currentTimeMillis());

        log.info("========================================");
        log.info("요청 ID: {}", requestId);
        log.info("사용자 요청: {}", userMessage.getContent());
        log.info("========================================");

        try {
            ChatResponse response = chain.nextAroundCall(userMessage, options);

            long duration = System.currentTimeMillis() - startTimeMap.get(requestId);

            log.info("========================================");
            log.info("응답 ID: {}", requestId);
            log.info("전체 실행 시간: {}ms", duration);
            log.info("응답 내용: {}", response.getResult().getOutput().getContent());
            log.info("========================================");

            return response;

        } finally {
            startTimeMap.remove(requestId);
        }
    }
}
```

### ChatClientConfig에 Custom Advisor 등록

```java
return ChatClient.builder(ollamaChatModel)
        .defaultTools(flightSearchTool, airportInfoTool, airlineInfoTool)
        .defaultAdvisors(
                new SimpleLoggerAdvisor(),
                customLoggingAdvisor  // 사용자 정의 어드바이저
        );
```

---

## Step 4-4: 테스트

### 기존 컨트롤러로 테스트

프로젝트에 이미 구현된 `NaturalLanguageSearchController`를 사용합니다:

```bash
curl "http://localhost:8080/api/search/natural?query=내일광주에서제주로가는항공편알려줘"
```

### 콘솔에서 확인되는 로그

```
========================================
요청 ID: a1b2c3d4
사용자 요청: 내일광주에서제주로가는항공편알려줘
========================================

DEBUG o.s.a.c.client.advisor.SimpleLoggerAdvisor :
>> request: user text: "내일광주에서제주로가는항공편알려줘"

INFO  c.n.f.mcp.FlightSearchTool     : 항공편 검색 시작: 광주 -> 제주 (내일)
INFO  c.n.f.mcp.FlightSearchTool     : 항공편 6편 검색 완료

DEBUG o.s.a.c.client.advisor.SimpleLoggerAdvisor :
<< chat response: "네, 내일 광주에서 제주로 가는 항공편을 찾았어요..."

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
package com.nhnacademy.flyschedule.config;

import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 성능 모니터링 어드바이저
 */
@Component
public class PerformanceMonitoringAdvisor implements CallAroundAdvisor {

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
    public ChatResponse aroundCall(
            UserMessage userMessage,
            ChatOptions options,
            CallAroundAdvisorChain chain) {

        long startTime = System.currentTimeMillis();
        String requestId = java.util.UUID.randomUUID().toString().substring(0, 8);

        try {
            ChatResponse response = chain.nextAroundCall(userMessage, options);

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
            log.info("  평균 실행 시간: {:.2f}ms", avg);
            log.info("  최소/최대: {}ms / {}ms", min, max);
            log.info("--------------------------------------------------");
        });

        log.info("======================================================");
    }
}
```

### 통계 확인용 컨트롤러

```java
@RestController
@RequestMapping("/api/monitoring")
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

```
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

```java
@Component
public class CachingAdvisor implements CallAroundAdvisor {

    private final Cache<String, ChatResponse> cache =
        Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    @Override
    public ChatResponse aroundCall(
            UserMessage userMessage,
            ChatOptions options,
            CallAroundAdvisorChain chain) {

        String cacheKey = userMessage.getContent();

        ChatResponse cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            log.info("캐시 히트: {}", cacheKey);
            return cached;
        }

        ChatResponse response = chain.nextAroundCall(userMessage, options);
        cache.put(cacheKey, response);

        return response;
    }
}
```

### 패턴 2: 재시도 (RetryAdvisor)

```java
@Component
public class RetryAdvisor implements CallAroundAdvisor {

    private static final int MAX_RETRIES = 3;

    @Override
    public ChatResponse aroundCall(
            UserMessage userMessage,
            ChatOptions options,
            CallAroundAdvisorChain chain) {

        Exception lastException = null;

        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                return chain.nextAroundCall(userMessage, options);
            } catch (Exception e) {
                lastException = e;
                log.warn("실패 {}회, 재시도 중...", i + 1);

                if (i < MAX_RETRIES - 1) {
                    try {
                        Thread.sleep(1000 * (i + 1));  // 지수 백오프
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
@Component
public class ErrorHandlingAdvisor implements CallAroundAdvisor {

    @Override
    public ChatResponse aroundCall(
            UserMessage userMessage,
            ChatOptions options,
            CallAroundAdvisorChain chain) {

        try {
            return chain.nextAroundCall(userMessage, options);

        } catch (Exception e) {
            log.error("LLM 호출 실패", e);

            // 사용자 친화적인 에러 메시지 생성
            String errorMessage = "죄송합니다. 일시적인 오류가 발생했습니다. " +
                                  "잠시 후 다시 시도해주세요.";

            throw new RuntimeException(errorMessage, e);
        }
    }
}
```

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

Advisor는 등록된 순서대로 실행됩니다 (First In, First Executed):

```java
.defaultAdvisors(
    new CachingAdvisor(),          // 1. 먼저 캐시 확인
    new RetryAdvisor(),            // 2. 실패 시 재시도
    new SimpleLoggerAdvisor()      // 3. 마지막으로 로깅
)
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

## 체크리스트

- [ ] `SimpleLoggerAdvisor`가 ChatClient에 등록됨
- [ ] 콘솔에서 요청/응답 로그 확인됨
- [ ] 로그 레벨이 DEBUG로 설정됨
- [ ] `CustomLoggingAdvisor`가 필요에 따라 구현됨
- [ ] `PerformanceMonitoringAdvisor`로 성능 측정
- [ ] 운영 환경에 맞는 Advisor 구성 완료

---

## 다음 단계

**Step 5: 다중 Tool 호출과 MCP 패턴**에서 여러 Tool을 효과적으로 관리하는 방법을 학습합니다.

---

## 참고 자료

- [Spring AI ChatClient 공식 문서](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [ChatClient.Advisor API](https://docs.spring.io/spring-ai/reference/api/chatclient.html#_advisors)
- [Spring AI Observability](https://docs.spring.io/spring-ai/reference/observability.html)

---

## 심화 학습

### Advisor 실행 순서 이해

Advisor는 **파이프라인 패턴**으로 실행됩니다:

```
요청 → [Advisor1] → [Advisor2] → [Advisor3] → ChatClient
응답 ← [Advisor1] ← [Advisor2] ← [Advisor3] ← ChatModel
```

```java
defaultAdvisors(
    new CachingAdvisor(),           // 1. 요청 → 캐시 확인
    new RetryAdvisor(),             // 2. 요청 → 재시도 로직
    new SimpleLoggerAdvisor()       // 3. 요청/응답 → 로깅
)
```

**실행 순서:**
1. **요청 시:** CachingAdvisor → RetryAdvisor → SimpleLoggerAdvisor → ChatClient
2. **응답 시:** SimpleLoggerAdvisor → RetryAdvisor → CachingAdvisor → 사용자

### Spring AI 내장 Advisor

| Advisor | 용도 | 설명 |
|---------|------|------|
| `SimpleLoggerAdvisor` | 로깅 | 요청/응답을 간단히 로깅 |
| `QuestionAnswerAdvisor` | RAG | 문서 검색 결과를 컨텍스트에 추가 |
| `PromptChatMemoryAdvisor` | 메모리 | 대화 기록을 유지 |
| `VectorStoreChatMemoryAdvisor` | 벡터 검색 | 벡터 DB에서 관련 문서 검색 |

### 사용자 정의 Advisor 구현 시 주의사항

1. **순서 의존성:** 캐싱은 가장 먼저, 로깅은 가장 나중에
2. **스레드 안전성:** 여러 요청이 동시에 처리됨을 고려
3. **성능 고려:** 무거운 로직은 비동기로 처리
4. **에러 처리:** 적절한 예외 처리 및 로깅

```java
// 좋은 예
@Override
public ChatResponse aroundCall(
        UserMessage userMessage,
        ChatOptions options,
        CallAroundAdvisorChain chain) {

    try {
        // 전처리
        log.debug("요청 수신: {}", userMessage.getContent());

        // 체인 호출
        ChatResponse response = chain.nextAroundCall(userMessage, options);

        // 후처리
        log.debug("응답 반환: {}", response.getResult().getOutput().getContent());

        return response;

    } catch (Exception e) {
        log.error("Advisor 실행 중 오류", e);
        throw e;  // 반드시 재throws
    }
}
```
