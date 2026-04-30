# Step 3: Function Calling 기초

## 학습 목표
이 단계에서는 Spring AI의 **Function Calling**기능을 사용하여 LLM이 자바 메서드를 호출할 수 있게 만듭니다.

**학습 완료 후 할 수 있는 것:**
- `@Tool`, `@ToolParam` annotation 사용
- 첫 번째 Function Call 구현
- LLM이 자동으로 Tool을 선택하고 호출하는 과정 이해

---

## Function Calling이란?

**Function Calling**은 LLM이 **자연어 질문을 분석하여 적절한 자바 메서드를 자동으로 호출**하는 기술입니다.

### Function Calling의 기술적 배경

**전통적인 방식의 문제점:**

```
사용자 입력 → if-else 분기 → 정규표현식 파싱 → API 호출
           ↓
         "자연어 이해 어려움"
         "복잡한 분기 로직"
         "유지보수 어려움"
```

**Function Calling의 해결책:**

```
사용자 입력 → LLM 분석 → 자동 함수 선택 → 파라미터 추출 → 함수 호출
           ↓
         "자연어 이해 가능"
         "간결한 코드"
         "확장 용이"
```

### Function Calling의 작동 원리

**1단계: LLM의 추론 (Reasoning)**

LLM은 사용자 입력을 분석하여 다음을 결정합니다:

- **의도 파악**: 사용자가 무엇을 원하는가?
- **함수 선택**: 어떤 함수를 호출해야 하는가?
- **파라미터 추출**: 함수에 전달할 값들은 무엇인가?

예시: "내일 광주에서 제주로 가는 항공편 알려줘"
```
LLM 추론 결과:
- 의도: 항공편 검색
- 필요한 함수: searchFlights()
- 파라미터:
  * departure: "광주"
  * arrival: "제주"
  * date: "내일"
```

**2단계: 함수 호출 (Function Invocation)**

Spring AI는 LLM의 결정에 따라 실제 자바 메서드를 호출합니다:

```java
searchFlightsByAirline(
    departure="광주",
    arrival="제주",
    date="내일"
)
```

**3단계: 결과 통합 (Response Synthesis)**

LLM은 함수 실행 결과를 사용자에게 자연어로 설명합니다:

```
함수 결과: {아시아나항공: [...], 제주항공: [...]}
LLM 응답: "네, 내일 광주에서 제주로 가는 항공편은 아시아나항공 2편,
          제주항공 3편이 있습니다..."
```

### Function Calling vs 전통적인 방식

| 항목 | 전통적인 방식 | Function Calling |
|------|--------------|------------------|
| **자연어 처리**| 정규표현식, if-else | LLM이 자동 처리 |
| **파라미터 추출**| 수동 파싱 로직 | LLM이 자동 추출 |
| **확장성**| 새로운 케이스마다 코드 추가 | Tool만 추가하면 자동 |
| **유지보수**| 복잡한 분기 로직 관리 | 선언적 Tool 정의 |
| **정확도**| 엄격한 규칙만 처리 | 유연한 자연어 이해 |

### Function Calling이 적합한 경우

| 적합한 경우 | 부적합한 경우 |
|-----------|-------------|
| 자연어 입력이 다양함 | 단순 CRUD |
| 파라미터 추출이 복잡함 | 입력 형식이 고정됨 |
| 유연한 확장이 필요함 | 성능이 최우선 |
| 사용자 경험 향상 | 비용 절감이 중요 |

### Function Calling의 동작 과정

```
┌───────────────────────────────────────────────────┐
│  사용자: "내일 광주에서 제주로 가는 항공편 알려줘"  │
└───────────────────────────────────────────────────┘
                    ↓
┌───────────────────────────────────────────────────┐
│  LLM 분석                                          │
│  - 출발 공항: "광주"                              │
│  - 도착 공항: "제주"                              │
│  - 날짜: "내일"                                    │
│  → searchFlightsByAirline() Tool이 적합함을 판단  │
└───────────────────────────────────────────────────┘
                    ↓
┌───────────────────────────────────────────────────┐
│  LLM → Tool 호출                                   │
│  searchFlightsByAirline(                          │
│    departure="광주",                              │
│    arrival="제주",                                │
│    date="내일"                                    │
│  )                                                │
└───────────────────────────────────────────────────┘
                    ↓
┌───────────────────────────────────────────────────┐
│  Tool 실행 결과                                    │
│  {                                                │
│    "아시아나항공": [펀1, 펀2],                   │
│    "제주항공": [펀1, 펀2, 펀3]                   │
│  }                                                │
└───────────────────────────────────────────────────┘
                    ↓
┌───────────────────────────────────────────────────┐
│  LLM → 자연어 응답 생성                           │
│  "네, 내일 광주에서 제주로 가는 항공편은..."     │
└───────────────────────────────────────────────────┘
```

### Function Calling의 장점

| 장점 | 설명 |
|------|------|
| **자연어 인터페이스**| 사용자가 복잡한 API 몰라도 됨 |
| **자동 파라미터 추출**| LLM이 자연어에서 파라미터를 자동 추출 |
| **유연한 Tool 선택**| LLM이 상황에 맞는 Tool을 자동 선택 |
| **확장성**| 새로운 Tool 추가가 쉬움 |

---

## Step 3-1: @Tool Annotation

Spring AI 1.1.2의 공식 `@Tool` annotation을 사용하여 LLM이 호출할 수 있는 메서드를 정의합니다.

### 첫 번째 Tool: 간단한 계산기

`@Component`와 `@Service`는 이미 익숙하시죠? Spring AI의 `@Tool`도 비슷하게 Spring이 관리하는 Bean에서 사용합니다.

```java
package com.nhnacademy.flyschedule.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component  // Spring이 이 클래스를 Bean으로 관리 (익숙하죠?)
public class CalculatorTool {

    /**
     * 두 숫자의 합을 계산하는 Tool
     *
     * LLM은 이 메서드의 description을 보고 언제 호출할지 결정합니다.
     *
     * @param a 첫 번째 숫자 (LLM이 자동으로 추출한 값)
     * @param b 두 번째 숫자 (LLM이 자동으로 추출한 값)
     * @return 두 숫자의 합 (LLM이 이 결과를 사용하여 답변 생성)
     */
    @Tool(description = "두 숫자의 합을 계산합니다.")  // LLM에게 이 함수의 용도 설명
    public int add(
            @ToolParam(description = "첫 번째 숫자") int a,  // 파라미터 설명 (LLM이 이해)
            @ToolParam(description = "두 번째 숫자") int b) {

        // 평범한 자바 메서드! LLM이 자동으로 호출
        return a + b;
    }

    /**
     * 두 숫자의 곱을 계산하는 Tool
     *
     * @Tool 어노테이션이 있으면 LLM이 호출할 수 있습니다.
     */
    @Tool(description = "두 숫자의 곱을 계산합니다.")
    public int multiply(
            @ToolParam(description = "첫 번째 숫자") int a,
            @ToolParam(description = "두 번째 숫자") int b) {

        return a * b;
    }
}
```

### 코드 상세 분석

**1. @Component**

```java
@Component  // Spring Bean 등록 (이미 익숙한 어노테이션!)
public class CalculatorTool { ... }
```

- Spring이 이 클래스를 스캔하여 Bean으로 등록
- `@Service`, `@Repository`와 동일한 역할
- Spring AI가 이 Bean을 스캔하여 `@Tool` 메서드를 찾음

**2. @Tool**

```java
@Tool(description = "두 숫자의 합을 계산합니다.")
public int add(...) { ... }
```

- **역할**: LLM이 이 메서드를 호출할 수 있게 함
- **description**: LLM에게 이 함수가 무엇을 하는지 설명
- **중요**: description을 잘 작성해야 LLM이 적절한 함수를 선택!

**3. @ToolParam**

```java
@ToolParam(description = "첫 번째 숫자") int a
```

- **역할**: 파라미터에 대한 설명
- **필요성**: LLM이 자연어에서 숫자를 추출할 때 참조
- **예시**: "3 더하기 5" → LLM이 `{"a": 3, "b": 5}`로 변환

### LLM의 함수 호출 과정 (상세)

**단계별 동작**

```
사용자 입력: "3 더하기 5랑 7 곱하기 2의 합은?"
    ↓
┌─────────────────────────────────────────────────┐
│  1단계: LLM 분석                                │
│                                                 │
│  사용자 의도 파악:                               │
│  - "3 더하기 5" → add(3, 5) 호출 필요         │
│  - "7 곱하기 2" → multiply(7, 2) 호출 필요    │
│  - "합은?" → 두 결과를 더함                    │
│                                                 │
│  LLM 내부 추론:                                  │
│  {                                               │
│    "function_calls": [                          │
│      {"name": "add", "args": {"a": 3, "b": 5}}, │
│      {"name": "multiply", "args": {"a": 7, "b": 2}} │
│    ]                                            │
│  }                                               │
└─────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────┐
│  2단계: Spring AI가 함수 호출                   │
│                                                 │
│  CalculatorTool.add(3, 5)  →  반환값: 8        │
│  CalculatorTool.multiply(7, 2) →  반환값: 14    │
│                                                 │
│  (주의: 이 부분은 자바 코드가 실행됨!)         │
└─────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────┐
│  3단계: 결과를 LLM에 전달                       │
│                                                 │
│  {                                               │
│    "add": 8,                                    │
│    "multiply": 14                               │
│  }                                               │
└─────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────┐
│  4단계: LLM이 최종 답변 생성                    │
│                                                 │
│  "3 더하기 5는 8이고, 7 곱하기 2는 14입니다.    │
│   두 값을 더하면 22가 됩니다."                   │
└─────────────────────────────────────────────────┘
```

### 왜 description이 중요할까요?

**나쁜 예시:**

```java
@Tool(description = "계산")  // 너무 모호함!
public int add(int a, int b) {
    return a + b;
}
```

LLM의 혼란: "무엇을 계산하지? 더하기? 곱하기?"

**좋은 예시:**

```java
@Tool(description = "두 숫자의 합을 계산합니다.")  // 명확함!
public int add(int a, int b) {
    return a + b;
}
```

LLM의 이해: "아, 더하기는 add() 함수를 호출하면 되는구나!"

### Annotation 설명

| Annotation | 역할 | 필수 여부 |
|------------|------|----------|
| `@Component` | Spring 빈으로 등록 | 필수 |
| `@Tool` | LLM이 호출할 수 있는 메서드임을 명시 | 필수 |
| `@ToolParam` | 파라미터에 대한 설명 (LLM이 이해) | 필수 |

### @Tool 속성

| 속성 | 타입 | 설명 | 예시 |
|------|------|------|------|
| `description` | `String` | Tool의 기능을 설명 (LLM이 Tool 선택 시 참조) | "항공편을 검색합니다" |
| `name` | `String` | Tool 이름 (기본값: 메서드명) | "searchFlights" |

### @ToolParam 속성

| 속성 | 타입 | 설명 | 예시 |
|----------|------|------|------|
| `description` | `String` | 파라미터에 대한 설명 (LLM이 파라미터 추출 시 참조) | "출발 공항 이름" |
| `name` | `String` | 파라미터 이름 (기본값: 변수명) | "departure" |
| `required` | `boolean` | 필수 파라미터 여부 (기본값: true) | false |

---

## Step 3-2: Tool 등록 방법

### 방법 1: ChatClient.Builder에 등록 (권장)

```java
@Configuration
public class ChatClientConfig {

    @Bean
    @Primary
    public ChatClient.Builder ollamaChatClientBuilder(
            @Qualifier("ollamaChatModel") ChatModel ollamaChatModel,
            CalculatorTool calculatorTool) {  // Tool 주입

        return ChatClient.builder(ollamaChatModel)
                .defaultTools(calculatorTool);  // Tool 등록
    }
}
```

### 방법 2: 여러 Tool 등록

```java
@Bean
@Primary
public ChatClient.Builder ollamaChatClientBuilder(
        @Qualifier("ollamaChatModel") ChatModel ollamaChatModel,
        CalculatorTool calculatorTool,
        DateTimeTool dateTimeTool,
        WeatherTool weatherTool) {

    return ChatClient.builder(ollamaChatModel)
            .defaultTools(calculatorTool, dateTimeTool, weatherTool);
}
```

### 방법 3: 패키지 스캔 (자동 등록)

```java
@Configuration
public class ChatClientConfig {

    @Bean
    @Primary
    public ChatClient.Builder ollamaChatClientBuilder(
            @Qualifier("ollamaChatModel") ChatModel ollamaChatModel,
            List<Object> tools) {  // 모든 @Tool 빈 자동 주입

        return ChatClient.builder(ollamaChatModel)
                .defaultTools(tools.toArray(new Object[0]));
    }
}
```

---

## Step 3-3: Function Calling 테스트

### FunctionCallTestService.java

```java
package com.nhnacademy.flyschedule.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class FunctionCallTestService {

    private final ChatClient chatClient;

    public FunctionCallTestService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Function Calling 테스트
     */
    public String testFunctionCalling(String userMessage) {
        return chatClient.prompt()
                .user(userMessage)
                .call()
                .content();
    }
}
```

### FunctionCallTestController.java

```java
package com.nhnacademy.flyschedule.controller;

import com.nhnacademy.flyschedule.service.FunctionCallTestService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test")
public class FunctionCallTestController {

    private final FunctionCallTestService testService;

    public FunctionCallTestController(FunctionCallTestService testService) {
        this.testService = testService;
    }

    /**
     * Function Calling 테스트
     * GET /api/test/function-calling?message=10과20의합은?
     */
    @GetMapping("/function-calling")
    public String testFunctionCalling(@RequestParam String message) {
        return testService.testFunctionCalling(message);
    }
}
```

---

## Step 3-4: 실행 및 테스트

### 1. 애플리케이션 실행

```bash
mvn spring-boot:run
```

### 2. Function Calling 테스트

```bash
curl "http://localhost:8080/api/test/function-calling?message=10과20의합은얼마야?"
```

**예상 응답:**
```
10과 20의 합은 30입니다.
```

### 3. 곱하기 테스트

```bash
curl "http://localhost:8080/api/test/function-calling?message=5와7을곱해줘"
```

**예상 응답:**
```
5와 7을 곱하면 35입니다.
```

---

## Step 3-5: Function Calling 실행 과정

### 콘솔 로그 분석

```
DEBUG o.s.ai.chat.client.ChatClient    : Sending prompt: 10과 20의 합은?
DEBUG o.s.a.c.m.OllamaChatModel        : Tool calling decision: ADD
DEBUG o.s.ai.chat.client.ChatClient    : Calling tool: add(a=10, b=20)
INFO  c.n.f.tool.CalculatorTool        : Tool executed: 10 + 20 = 30
DEBUG o.s.ai.chat.client.ChatClient    : Tool result: 30
DEBUG o.s.a.c.m.OllamaChatModel        : Generating response with tool result
DEBUG o.s.a.c.m.OllamaChatModel        : Final response: 10과 20의 합은 30입니다.
```

### 실행 과정 상세

```
1. 사용자 질문
   "10과 20의 합은?"

2. LLM 분석
   - 의도: 덧셈 계산
   - Tool 선택: add()
   - 파라미터 추출: a=10, b=20

3. Tool 호출
   CalculatorTool.add(10, 20)

4. Tool 실행 결과
   30

5. LLM 응답 생성
   "10과 20의 합은 30입니다."
```

---

## Step 3-6: 복잡한 Tool 구현

### 날짜 계산 Tool

```java
@Component
public class DateTimeTool {

    /**
     * "내일", "모레" 같은 상대적 날짜를 실제 날짜로 변환
     */
    @Tool(description = "상대적 날짜를 실제 날짜(YYYY-MM-DD)로 변환합니다. " +
            "'내일', '모레', '일주일 뒤' 등을 지원합니다.")
    public String parseDate(
            @ToolParam(description = "상대적 날짜 표현 (예: 내일, 모레, 3일 뒤)") String relativeDate) {

        LocalDate today = LocalDate.now();

        return switch (relativeDate) {
            case "내일" -> today.plusDays(1).toString();
            case "모레" -> today.plusDays(2).toString();
            case "글피" -> today.plusDays(3).toString();
            default -> {
                if (relativeDate.contains("일 뒤")) {
                    int days = Integer.parseInt(relativeDate.replaceAll("[^0-9]", ""));
                    yield today.plusDays(days).toString();
                }
                yield today.toString();
            }
        };
    }
}
```

### 사용 예시

```bash
curl "http://localhost:8080/api/test/function-calling?message=내일날짜알려줘"
```

**응답:**
```
내일은 2026-03-10입니다.
```

---

## Tool Description 가이드

### 좋은 Description의 조건

| 조건 | 설명 | 나쁜 예시 | 좋은 예시 |
|------|------|----------|----------|
| **명확성**| 무엇을 하는지 명확히 설명 | "계산함" | "두 숫자의 합을 계산합니다" |
| **파라미터 설명**| 각 파라미터의 역할 설명 | "숫자" | "첫 번째 숫자 (정수)" |
| **반환값 설명**| 무엇을 반환하는지 설명 | (없음) | "계산된 합계 (정수)를 반환합니다" |
| **사용 예시**| 언제 사용하는지 예시 | (없음) | "덧셈이 필요할 때 사용합니다" |

### Description 예시

```java
@Tool(
    description = """
    항공편을 검색하여 항공사별로 그룹핑하여 반환합니다.
    출발 공항, 도착 공항, 날짜를 받아 항공사별로 정리된 항공편 목록을 제공합니다.

    사용 예시:
    - "내일 광주에서 제주로 가는 항공편"
    - "모레 김포에서 부산으로 가는 스케줄"

    파라미터:
    - departure: 출발 공항 이름 (예: 광주, 김포, 제주)
    - arrival: 도착 공항 이름 (예: 제주, 김포, 부산)
    - date: 날짜 (예: 내일, 모레, 2026-03-10)

    반환값: 항공사별 항공편 목록 (Map<항공사명, List<항공편>>)
    """
)
public Map<String, List<FlightInfoResponse>> searchFlightsByAirline(
    @ToolParam(description = "출발 공항 이름 (예: 광주, 김포, 제주)") String departure,
    @ToolParam(description = "도착 공항 이름 (예: 제주, 김포, 부산)") String arrival,
    @ToolParam(description = "날짜 (예: 내일, 모레, 2026-03-10)") String date
) {
    // 구현
}
```

---

## 일반적인 문제 해결

### 문제 1: LLM이 Tool을 호출하지 않음

**증상:**Tool을 등록했는데 LLM이 직접 답변만 함

**원인:**Description이 불명확하여 LLM이 Tool 사용을 인지하지 못함

**해결 방법:**
1. `@Tool`의 `description`을 더 명확하게 작성
2. 파라미터의 `@ToolParam(description)` 상세화
3. LLM 모델 변경 (Ollama → Gemini)

### 문제 2: 파라미터 추출 실패

**증상:**`null` 또는 잘못된 파라미터가 전달됨

**원인:**LLM이 파라미터를 제대로 추출하지 못함

**해결 방법:**
1. `@ToolParam`의 `description`에 예시 추가
2. 파라미터 타입 명확히 (String → int 등)
3. Temperature 낮추기 (0.0 ~ 0.3)

### 문제 3: 여러 Tool 중 원하지 않는 Tool이 호출됨

**증상:**다른 Tool이 호출됨

**해결 방법:**
1. 각 Tool의 `description`을 더 구체적으로
2. Tool 이름을 명확하게 (`search` → `searchFlightsByAirline`)
3. `required = false`로 선택적 파라미터 구분

---

## Function Calling 패턴

### 패턴 1: 단일 Tool

```
사용자 → LLM → 단일 Tool → 결과 → 응답
```

### 패턴 2: 다중 Tool (Chaining)

```
사용자 → LLM → Tool1 → 결과1 → LLM → Tool2 → 결과2 → 응답
```

**예시:**"내일 광주에서 제주 항공편 중 가장 저렴한 거"

1. `parseDate("내일")` → "2026-03-10"
2. `searchFlightsByAirline("광주", "제주", "2026-03-10")` → 항공편 목록
3. `findCheapest(flights)` → 최저가 항공편

### 패턴 3: 병렬 Tool 호출

```
사용자 → LLM → Tool1 ─┐
                ├→ 결과 통합 → 응답
                → Tool2 ─┘
```

**예시:**"서울과 부산 날씨 알려줘"

1. `getWeather("서울")` (병렬)
2. `getWeather("부산")` (병렬)
3. 결과 통합

---

## 체크리스트

- [ ] `CalculatorTool.java`가 생성됨
- [ ] `@Tool`, `@ToolParam` annotation 사용됨
- [ ] Tool이 `ChatClient.Builder`에 등록됨
- [ ] Function Calling 테스트가 동작함
- [ ] 콘솔에서 Tool 호출 로그 확인됨
- [ ] LLM이 적절한 Tool을 선택함
- [ ] 파라미터가 올바르게 추출됨

---

## 다음 단계

**Step 4: Tool Callback과 실행 과정 이해**에서 Function Calling의 실행 과정을 디버깅하는 방법을 학습합니다.

---

## 참고 자료

- [Spring AI Function Calling 공식 문서](https://docs.spring.io/spring-ai/reference/api/functioncalling.html)
- [@Tool Annotation API](https://docs.spring.io/spring-ai/reference/api/functioncalling.html#_tool_annotation)
- [Function Calling Best Practices](https://docs.spring.io/spring-ai/reference/api/functioncalling.html#_best_practices)

---

## 심화 학습

### 왜 @Tool을 사용하나요?

1. **표준화된 방식**: Spring AI 1.1.2의 공식 annotation
2. **타입 안전성**: 컴파일 타임에 체크
3. **자동 등록**: `@Component`와 함께 자동으로 빈 등록
4. **LLM 호환성**: OpenAI, Gemini, Ollama 등 모두 지원

### Function Calling vs 일반 API 호출

| 항목 | Function Calling | 일반 API |
|------|------------------|----------|
| **인터페이스**| 자연어 | HTTP 요청 |
| **파라미터 추출**| LLM이 자동 추출 | 수동 파싱 |
| **에러 처리**| LLM이 재시도 | 수동 처리 |
| **유연성**| 높음 (LLM이 상황 판단) | 낮음 (고정 로직) |
