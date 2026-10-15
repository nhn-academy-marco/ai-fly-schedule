# Step 3: Function Calling 기초

## 학습 목표

이 단계를 마치면 다음을 할 수 있습니다:

- `@Tool`과 `@ToolParam` 어노테이션을 사용하여 LLM이 호출할 수 있는 자바 메서드를 정의할 수 있습니다
- LLM이 사용자의 자연어 질문을 분석하여 적절한 Tool을 자동으로 선택하고 호출하는 과정을 이해할 수 있습니다
- ChatClient에 Tool을 넘기는 두 가지 방법(`defaultTools`와 요청마다 `.tools(...)`)을 구분하고, 이 프로젝트가 요청마다 넘기는 이유를 설명할 수 있습니다
- 여러 Tool을 조합하여 복잡한 사용자 요청을 처리할 수 있습니다

---

> **핵심 개념**: Function Calling은 LLM이 자연어 질문을 분석하여 적절한 자바 메서드를 **자동으로** 호출하는 기술입니다. 쉽게 말해, 사용자가 "내일 광주에서 제주로 가는 항공편 알려줘"라고 말하면, LLM이 이 문장을 분석해서 `searchFlightsByAirline("광주", "제주", "내일")`을 호출해 달라고 요청하고, Spring AI가 그 요청대로 메서드를 실행해 줍니다. LLM은 코드를 직접 실행하지 않습니다. 개발자가 if-else로 입력을 파싱할 필요가 없습니다.

---

## 전체 흐름

Function Calling이 동작하는 전체 과정은 다음과 같습니다:

```text
[사용자: "10과 20의 합은?"]
            ↓
[Controller → Service → ChatClient]
            ↓
[LLM이 질문 분석]
   - 의도: 덧셈 계산
   - 선택: add() Tool
   - 파라미터: a=10, b=20
            ↓
[Spring AI가 자동으로 CalculatorTool.add(10, 20) 호출]
            ↓
[Tool 실행 결과: 30]
            ↓
[LLM이 결과를 받아 자연어로 응답 생성]
   "10과 20의 합은 30입니다."
            ↓
[사용자에게 응답 전달]
```

기존 방식과 비교:
```text
기존: [사용자 입력] → [if-else 파싱] → [직접 메서드 호출] → [결과 포맷팅]
AI:   [사용자 입력] → [LLM이 자동 분석] → [자동 메서드 호출] → [자연어 응답]
```

---

## Function Calling이란?

**Function Calling**은 LLM이 **자연어 질문을 분석하여 호출할 자바 메서드와 인자를 결정**하고, Spring AI가 그 메서드를 **자동으로 실행**하는 기술입니다.

### Function Calling의 기술적 배경

**전통적인 방식의 문제점:**

```text
사용자 입력 → if-else 분기 → 정규표현식 파싱 → API 호출
           ↓
         "자연어 이해 어려움"
         "복잡한 분기 로직"
         "유지보수 어려움"
```

**Function Calling의 해결책:**

```text
사용자 입력 → LLM 분석 → 자동 Tool 선택 → 파라미터 추출 → 메서드 호출
           ↓
         "자연어 이해 가능"
         "간결한 코드"
         "확장 용이"
```

### Function Calling의 작동 원리

**1단계: LLM의 추론 (Reasoning)**

LLM은 사용자 입력을 분석하여 다음을 결정합니다:

- **의도 파악**: 사용자가 무엇을 원하는가?
- **Tool 선택**: 어떤 Tool(메서드)을 호출해야 하는가?
- **파라미터 추출**: 메서드에 전달할 값들은 무엇인가?

예시: "내일 광주에서 제주로 가는 항공편 알려줘"
```text
LLM 추론 결과:
- 의도: 항공편 검색
- 필요한 Tool: searchFlightsByAirline()
- 파라미터:
  * departure: "광주"
  * arrival: "제주"
  * date: "내일"
```

**2단계: 메서드 호출 (Function Invocation)**

Spring AI는 LLM의 결정에 따라 실제 자바 메서드를 호출합니다:

```text
searchFlightsByAirline(
    departure="광주",
    arrival="제주",
    date="내일"
)
```

**3단계: 결과 통합 (Response Synthesis)**

LLM은 메서드 실행 결과를 사용자에게 자연어로 설명합니다:

```text
Tool 결과: {아시아나항공: [...], 제주항공: [...]}
LLM 응답: "네, 내일 광주에서 제주로 가는 항공편은 아시아나항공 2편,
          제주항공 3편이 있습니다..."
```

### Function Calling vs 전통적인 방식

| 항목 | 전통적인 방식 | Function Calling |
|------|--------------|------------------|
| **자연어 처리** | 정규표현식, if-else | LLM이 자동 처리 |
| **파라미터 추출** | 수동 파싱 로직 | LLM이 자동 추출 |
| **확장성** | 새로운 케이스마다 코드 추가 | Tool만 추가하면 자동 |
| **유지보수** | 복잡한 분기 로직 관리 | 선언적 Tool 정의 |
| **정확도** | 엄격한 규칙만 처리 | 유연한 자연어 이해 |

### Function Calling이 적합한 경우

| 적합한 경우 | 부적합한 경우 |
|-----------|-------------|
| 자연어 입력이 다양함 | 단순 CRUD |
| 파라미터 추출이 복잡함 | 입력 형식이 고정됨 |
| 유연한 확장이 필요함 | 성능이 최우선 |
| 사용자 경험 향상 | 비용 절감이 중요 |

### Function Calling의 동작 과정

```text
┌────────────────────────────────────────────────────┐
│  사용자: "내일 광주에서 제주로 가는 항공편 알려줘" │
└────────────────────────────────────────────────────┘
                    ↓
┌────────────────────────────────────────────────────┐
│  LLM 분석                                          │
│  - 출발 공항: "광주"                               │
│  - 도착 공항: "제주"                               │
│  - 날짜: "내일"                                    │
│  → searchFlightsByAirline() Tool이 적합함을 판단   │
└────────────────────────────────────────────────────┘
                    ↓
┌────────────────────────────────────────────────────┐
│  LLM이 Tool 호출 요청 → Spring AI가 실행           │
│  searchFlightsByAirline(                           │
│    departure="광주",                               │
│    arrival="제주",                                 │
│    date="내일"                                     │
│  )                                                 │
└────────────────────────────────────────────────────┘
                    ↓
┌────────────────────────────────────────────────────┐
│  Tool 실행 결과                                    │
│  {                                                 │
│    "아시아나항공": [편1, 편2],                     │
│    "제주항공": [편1, 편2, 편3]                     │
│  }                                                 │
└────────────────────────────────────────────────────┘
                    ↓
┌────────────────────────────────────────────────────┐
│  LLM → 자연어 응답 생성                            │
│  "네, 내일 광주에서 제주로 가는 항공편은..."       │
└────────────────────────────────────────────────────┘
```

### Function Calling의 장점

| 장점 | 설명 |
|------|------|
| **자연어 인터페이스** | 사용자가 복잡한 API 몰라도 됨 |
| **자동 파라미터 추출** | LLM이 자연어에서 파라미터를 자동 추출 |
| **유연한 Tool 선택** | LLM이 상황에 맞는 Tool을 자동 선택 |
| **확장성** | 새로운 Tool 추가가 쉬움 |

---

## Step 3-1: @Tool 어노테이션

### 왜 이렇게 할까요?

Step 2까지는 ChatClient로 LLM에게 질문하고 답변을 받기만 했습니다. 하지만 LLM은 "10과 20의 합은?" 같은 질문에도 계산 결과를 틀릴 수 있습니다(특히 복잡한 계산에서). 그리고 "내일 광주에서 제주로 가는 항공편" 같은 질문은 LLM이 실시간 항공편 데이터를 가지고 있지 않아서 답변할 수 없습니다.

Function Calling은 이 문제를 해결합니다. LLM이 직접 답변하는 대신, 정확한 계산이나 실시간 데이터 조회는 **우리가 만든 자바 메서드**에게 맡기는 것입니다. LLM은 어떤 메서드를 호출할지, 어떤 파라미터를 넘길지만 결정합니다.

> **Spring Boot 복습**: `@Component`는 Spring이 클래스를 스캔하여 Bean으로 등록하게 만드는 어노테이션입니다. `@Service`, `@Repository`도 `@Component`의 특수화 버전입니다. 여기서는 `@Component`를 사용하는데, Tool 클래스는 비즈니스 로직(Service)이나 데이터 접근(Repository)보다는 "AI가 호출할 수 있는 기능"이라는 의미이기 때문입니다.

Spring AI 1.1.4의 공식 `@Tool` 어노테이션을 사용하여 LLM이 호출할 수 있는 메서드를 정의합니다.

### 첫 번째 Tool: 간단한 계산기

`@Component`와 `@Service`는 이미 익숙하시죠? Spring AI의 `@Tool`도 비슷하게 Spring이 관리하는 Bean에서 사용합니다.

```java
package net.java21.flyschedule.mcp;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component  // ① Spring이 이 클래스를 Bean으로 관리 (익숙하죠?)
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
    @Tool(description = "두 숫자의 합을 계산합니다.")  // ② LLM에게 이 메서드의 용도를 설명하는 어노테이션
    public int add(
            @ToolParam(description = "첫 번째 숫자") int a,  // ③ LLM이 "3 더하기 5"에서 3을 추출하여 이 파라미터에 전달
            @ToolParam(description = "두 번째 숫자") int b) {  // ④ LLM이 "3 더하기 5"에서 5를 추출하여 이 파라미터에 전달

        // ⑤ 평범한 자바 메서드! LLM이 아닌 JVM이 실행합니다
        return a + b;
    }

    /**
     * 두 숫자의 곱을 계산하는 Tool
     *
     * @Tool 어노테이션이 있으면 LLM이 호출할 수 있습니다.
     */
    @Tool(description = "두 숫자의 곱을 계산합니다.")  // ⑥ "곱하기" 관련 질문이 오면 LLM이 이 메서드를 선택
    public int multiply(
            @ToolParam(description = "첫 번째 숫자") int a,
            @ToolParam(description = "두 번째 숫자") int b) {

        return a * b;  // ⑦ 반환값이 LLM에게 전달되어 최종 답변에 사용됩니다
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
- `defaultTools()`나 `.tools()`로 넘기면 Spring AI가 이 객체에서 `@Tool` 메서드를 찾음 (자동 스캔되지는 않음, Step 3-2 참고)

**2. @Tool**

```java
@Tool(description = "두 숫자의 합을 계산합니다.")
public int add(...) { ... }
```

- **역할**: LLM이 이 메서드를 호출할 수 있게 함
- **description**: LLM에게 이 메서드가 무엇을 하는지 설명
- **중요**: description을 잘 작성해야 LLM이 적절한 Tool을 선택!

**3. @ToolParam**

```java
@ToolParam(description = "첫 번째 숫자") int a
```

- **역할**: 파라미터에 대한 설명
- **필요성**: LLM이 자연어에서 숫자를 추출할 때 참조
- **예시**: "3 더하기 5" → LLM이 `{"a": 3, "b": 5}`로 변환

### LLM의 Tool 호출 과정 (상세)

**단계별 동작**

```text
사용자 입력: "3 더하기 5랑 7 곱하기 2의 합은?"
    ↓
┌─────────────────────────────────────────────────────┐
│  1단계: LLM 분석                                    │
│                                                     │
│  사용자 의도 파악:                                  │
│  - "3 더하기 5" → add(3, 5) 호출 필요               │
│  - "7 곱하기 2" → multiply(7, 2) 호출 필요          │
│  - "합은?" → 두 결과를 더함                         │
│                                                     │
│  LLM 내부 추론:                                     │
│  {                                                  │
│    "function_calls": [                              │
│      {"name": "add", "args": {"a": 3, "b": 5}},     │
│      {"name": "multiply", "args": {"a": 7, "b": 2}} │
│    ]                                                │
│  }                                                  │
└─────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────┐
│  2단계: Spring AI가 메서드 호출                     │
│                                                     │
│  CalculatorTool.add(3, 5)  →  반환값: 8             │
│  CalculatorTool.multiply(7, 2) →  반환값: 14        │
│                                                     │
│  (주의: 이 부분은 자바 코드가 실행됨!)              │
└─────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────┐
│  3단계: 결과를 LLM에 전달                           │
│                                                     │
│  {                                                  │
│    "add": 8,                                        │
│    "multiply": 14                                   │
│  }                                                  │
└─────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────┐
│  4단계: LLM이 최종 답변 생성                        │
│                                                     │
│  "3 더하기 5는 8이고, 7 곱하기 2는 14입니다.        │
│   두 값을 더하면 22가 됩니다."                      │
└─────────────────────────────────────────────────────┘
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

LLM의 이해: "아, 더하기는 add() 메서드를 호출하면 되는구나!"

### 어노테이션 설명

| 어노테이션 | 역할 | 필수 여부 |
|------------|------|----------|
| `@Component` | Spring 빈으로 등록 (생성자 주입으로 받기 위함) | 선택 (`defaultTools()`, `.tools()`에는 일반 객체도 전달 가능) |
| `@Tool` | LLM이 호출할 수 있는 메서드임을 명시 | 필수 |
| `@ToolParam` | 파라미터에 대한 설명 (LLM이 이해) | 선택 (권장) |

### @Tool 속성

| 속성 | 타입 | 설명 | 예시 |
|------|------|------|------|
| `description` | `String` | Tool의 기능을 설명 (LLM이 Tool 선택 시 참조, 비우면 메서드명 사용) | "항공편을 검색합니다" |
| `name` | `String` | Tool 이름 (기본값: 메서드명) | "searchFlights" |
| `returnDirect` | `boolean` | `true`면 Tool 결과를 LLM에 다시 보내지 않고 바로 반환 (기본값: false) | true |

### @ToolParam 속성

| 속성 | 타입 | 설명 | 예시 |
|----------|------|------|------|
| `description` | `String` | 파라미터에 대한 설명 (LLM이 파라미터 추출 시 참조) | "출발 공항 이름" |
| `required` | `boolean` | 필수 파라미터 여부 (기본값: true) | false |

---

## Step 3-2: Tool 등록 방법

### 왜 이렇게 할까요?

`@Tool` 어노테이션을 붙인 메서드를 만들었지만, ChatClient가 이 Tool의 존재를 알아야 LLM에게 "이런 Tool을 사용할 수 있어"라고 알려줄 수 있습니다. 마치 `@Service` 클래스를 만들어도 다른 Bean에 주입하지 않으면 사용할 수 없는 것과 같습니다. Spring AI는 `@Tool` Bean을 자동으로 찾아 등록하지 않으므로, 개발자가 직접 ChatClient에 Tool을 넘겨야 합니다.

Tool을 넘기는 위치는 두 가지입니다.

| 방법 | 코드 | Tool이 전달되는 범위 |
|------|------|----------------------|
| 방법 1: 기본값으로 등록 | `ChatClient.builder(model).defaultTools(...)` | 이 ChatClient로 보내는 **모든** 요청 |
| 방법 2: 요청마다 전달 | `chatClient.prompt()...tools(...)` | `.tools(...)`를 붙인 **그 요청**만 |

> **Spring Boot 복습**: Step 2에서 `ChatClientConfig`가 `ollamaChatClient`, `geminiChatClient` 빈을 만들었습니다. `CalculatorTool`은 `@Component`로 등록된 Bean이므로, `@Bean` 메서드의 파라미터나 서비스의 생성자로 주입받을 수 있습니다.

### 방법 1: 빌더에 defaultTools로 등록

```java
@Bean
public ChatClient ollamaChatClient(@Qualifier("ollamaChatModel") ChatModel ollamaChatModel,
                                   CalculatorTool calculatorTool) {   // Tool 주입
    return ChatClient.builder(ollamaChatModel)
            .defaultTools(calculatorTool)                            // 모든 요청에 Tool 목록이 붙음
            .build();
}
```

여러 Tool은 `defaultTools(calculatorTool, dateTimeTool)`처럼 나열합니다. 한 번 등록하면 서비스 코드에서 Tool을 신경 쓰지 않아도 되므로, **이 ChatClient의 용도가 Tool 사용 하나뿐일 때** 편리합니다.

### 방법 2: 요청마다 .tools(...)로 전달

```java
String answer = chatClient.prompt()
        .user(message)
        .tools(calculatorTool, dateTimeTool)   // 이 요청에만 Tool 목록이 붙음
        .call()
        .content();
```

같은 ChatClient를 쓰더라도 `.tools(...)`를 붙이지 않은 요청에는 Tool 목록이 전달되지 않습니다.

### 이 프로젝트의 선택: 요청마다 .tools(...)

이 프로젝트의 `ChatClientConfig`는 `defaultTools`를 쓰지 않고, Tool이 필요한 `ToolCallingSearchService`에서만 요청마다 Tool을 넘깁니다.

`src/main/java/net/java21/flyschedule/service/toolcalling/ToolCallingSearchService.java` (일부)

```java
String answer = chatClientProvider.get(model).prompt()
        .system(systemPrompt())
        .user(message)
        .tools(flightSearchTool, airportInfoTool, airlineInfoTool)
        .call()
        .content();
```

**왜 이렇게 설계했을까요?** 같은 `ChatClient`를 Tool이 필요 없는 곳에서도 쓰기 때문입니다.

| LLM 호출 위치 | 하는 일 | Tool 필요 |
|---------------|---------|-----------|
| `SearchCriteriaExtractor` | 문장에서 검색 조건 추출 (`.entity`) | 아니요 |
| `FlightSearchPlanner` | 요청을 검색 작업으로 나누기 (`.entity`) | 아니요 |
| `ResultSynthesizer` | 작업별 결과를 요약 (`.content`) | 아니요 |
| `ToolCallingSearchService` | LLM이 Tool을 골라 항공편 검색 | **예** |

`defaultTools`로 등록했다면 위의 세 호출에도 Tool 목록이 붙습니다. 그러면 검색 조건만 뽑아야 할 LLM이 직접 `searchFlightsByAirline`을 부르는 등 의도하지 않은 Tool 호출이 생기고, Tool 설명만큼 프롬프트가 길어져 응답도 느려집니다. Tool을 쓸 곳에서만 `.tools(...)`로 넘기면 어떤 호출이 Tool을 쓸 수 있는지가 코드에 그대로 드러납니다.

### 참고: ToolCallbackProvider로 묶어서 등록

여러 Tool 객체를 `MethodToolCallbackProvider`로 묶어 Bean으로 만들면, 여러 곳에서 같은 Tool 묶음을 재사용할 수 있습니다. 묶음도 기본값(`defaultToolCallbacks`)과 요청 단위(`toolCallbacks`) 두 방식으로 넘길 수 있습니다.

```java
@Bean
public ToolCallbackProvider calculatorTools(CalculatorTool calculatorTool, DateTimeTool dateTimeTool) {
    return MethodToolCallbackProvider.builder()
            .toolObjects(calculatorTool, dateTimeTool)  // @Tool 메서드를 ToolCallback으로 변환
            .build();
}

// 사용하는 쪽 (요청 단위)
chatClient.prompt()
        .user(message)
        .toolCallbacks(calculatorTools)
        .call()
        .content();
```

실제 프로젝트에서 Tool이 호출되는 흐름은 다음과 같습니다:

```text
[ToolCallingSearchService]
    ↓  chatClient.prompt().system(...).user(message)
    ↓      .tools(flightSearchTool, airportInfoTool, airlineInfoTool).call()
[LLM이 Tool 선택: searchFlightsByAirline(departure, arrival, date)]
    ↓
[FlightSearchTool.searchFlightsByAirline()]
    ↓
[FlightSearchAgent.searchAndGroupByAirline()]  → 항공사별 항공편 Map 반환
    ↓
[LLM이 Tool 결과를 바탕으로 자연어 응답 생성]
```

---

## Step 3-3: Function Calling 테스트

### 왜 이렇게 할까요?

Tool을 만들고 ChatClient에 넘기는 방법을 배웠지만, 실제로 사용자의 요청이 들어왔을 때 LLM이 Tool을 정확히 선택하고 파라미터를 올바르게 추출하는지 확인해야 합니다. 간단한 계산기 Tool로 먼저 테스트해 보면서, LLM이 "10과 20의 합은?"이라는 질문을 보면 `add(10, 20)`을 자동으로 호출하는지 검증합니다.

> **Spring Boot 복습**: Service와 Controller의 구조는 Step 2와 동일합니다. `@Service`에서 ChatClient를 사용하고, `@RestController`에서 HTTP 요청을 처리합니다. Step 2와 달라진 점은 `.tools(calculatorTool)` 한 줄뿐인데, 이 한 줄로 Spring AI가 Function Calling을 수행합니다.

> 아래 `CalculatorTool`, `FunctionCallTestService`, `FunctionCallTestController`는 실습용 예제이며 프로젝트에는 포함되어 있지 않습니다. 직접 만들어 보세요.

### FunctionCallTestService.java

```java
package net.java21.flyschedule.service;

import net.java21.flyschedule.mcp.CalculatorTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class FunctionCallTestService {

    private final ChatClient chatClient;
    private final CalculatorTool calculatorTool;

    public FunctionCallTestService(@Qualifier("ollamaChatClient") ChatClient chatClient,
                                   CalculatorTool calculatorTool) {
        this.chatClient = chatClient;
        this.calculatorTool = calculatorTool;
    }

    /**
     * Function Calling 테스트
     */
    public String testFunctionCalling(String userMessage) {
        return chatClient.prompt()
                .user(userMessage)
                .tools(calculatorTool)   // 이 요청에만 Tool 전달
                .call()
                .content();
    }
}
```

### FunctionCallTestController.java

```java
package net.java21.flyschedule.controller;

import net.java21.flyschedule.service.FunctionCallTestService;
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
./mvnw spring-boot:run
```

### 2. Function Calling 테스트

```bash
curl -G "http://localhost:8080/api/test/function-calling" \
  --data-urlencode "message=10과 20의 합은 얼마야?"
```

**예상 응답:**
```text
10과 20의 합은 30입니다.
```

### 3. 곱하기 테스트

```bash
curl -G "http://localhost:8080/api/test/function-calling" \
  --data-urlencode "message=5와 7을 곱해 줘"
```

**예상 응답:**
```text
5와 7을 곱하면 35입니다.
```

---

## Step 3-5: Function Calling 실행 과정

### 콘솔 로그 분석

`logging.level.org.springframework.ai.model.tool: DEBUG`를 설정하면 Spring AI의 `DefaultToolCallingManager`가 Tool 실행 시 로그를 남깁니다. `CalculatorTool`의 로그는 메서드 안에 `log.info(...)`를 추가했을 때 출력되는 예시입니다. 요청/응답 전체 내용은 Step 4의 `SimpleLoggerAdvisor`로 확인합니다.

```text
DEBUG o.s.a.m.tool.DefaultToolCallingManager : Executing tool call: add
INFO  n.j.f.mcp.CalculatorTool               : Tool executed: 10 + 20 = 30
```

### 실행 과정 상세

```text
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
            "'내일', '모레', '글피', '3일 뒤' 등을 지원합니다.")
    public String parseDate(
            @ToolParam(description = "상대적 날짜 표현 (예: 내일, 모레, 3일 뒤)") String relativeDate) {

        LocalDate today = LocalDate.now();

        return switch (relativeDate) {
            case "내일" -> today.plusDays(1).toString();
            case "모레" -> today.plusDays(2).toString();
            case "글피" -> today.plusDays(3).toString();
            default -> {
                if (relativeDate.matches("\\d+일 뒤")) {  // 예: "3일 뒤"
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
curl -G "http://localhost:8080/api/test/function-calling" \
  --data-urlencode "message=내일 날짜 알려 줘"
```

**응답:**
```text
내일은 2026-03-10입니다.
```

---

## Tool Description 가이드

### 좋은 Description의 조건

| 조건 | 설명 | 나쁜 예시 | 좋은 예시 |
|------|------|----------|----------|
| **명확성** | 무엇을 하는지 명확히 설명 | "계산함" | "두 숫자의 합을 계산합니다" |
| **파라미터 설명** | 각 파라미터의 역할 설명 | "숫자" | "첫 번째 숫자 (정수)" |
| **반환값 설명** | 무엇을 반환하는지 설명 | (없음) | "계산된 합계 (정수)를 반환합니다" |
| **사용 예시** | 언제 사용하는지 예시 | (없음) | "덧셈이 필요할 때 사용합니다" |

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
    // 구현 생략 (실제 구현은 FlightSearchTool 참고)
    return flightSearchAgent.searchAndGroupByAirline(departure, arrival, date);
}
```

---

## Function Calling 패턴

### 패턴 1: 단일 Tool

```text
사용자 → LLM → 단일 Tool → 결과 → 응답
```

### 패턴 2: 다중 Tool (Chaining)

```text
사용자 → LLM → Tool1 → 결과1 → LLM → Tool2 → 결과2 → 응답
```

**예시:** "내일 광주에서 제주 항공편 중 가장 저렴한 거"

1. `parseDate("내일")` → "2026-03-10"
2. `searchFlightsByAirline("광주", "제주", "2026-03-10")` → 항공편 목록
3. `findCheapest(flights)` → 최저가 항공편

### 패턴 3: 병렬 Tool 호출

```text
사용자 → LLM ─┬→ Tool1 ─┐
              │          ├→ 결과 통합 → 응답
              └→ Tool2 ─┘
```

**예시:** "서울과 부산 날씨 알려줘"

1. `getWeather("서울")`
2. `getWeather("부산")`
3. 결과 통합

> LLM이 한 번의 응답에서 여러 Tool 호출을 함께 요청하는 것을 "병렬 Tool 호출"이라고 합니다(지원 여부는 모델에 따라 다름). Spring AI는 요청받은 Tool들을 차례대로 실행한 뒤 결과를 한꺼번에 LLM에 돌려줍니다.

---

## 일반적인 문제 해결

### 문제 1: LLM이 Tool을 호출하지 않음

**증상:** Tool을 등록했는데 LLM이 직접 답변만 함

**원인:** description이 불명확하여 LLM이 Tool 사용을 인지하지 못함

**해결 방법:**
1. 그 요청에 `.tools(...)`를 붙였는지(또는 `defaultTools`로 등록했는지) 확인
2. `@Tool`의 `description`을 더 명확하게 작성
3. 파라미터의 `@ToolParam(description)` 상세화
4. LLM 모델 변경 (Ollama → Gemini). 작은 모델은 Tool 호출을 건너뛰고 답을 지어내기도 합니다

### 문제 2: 파라미터 추출 실패

**증상:** `null` 또는 잘못된 파라미터가 전달됨

**원인:** LLM이 파라미터를 제대로 추출하지 못함

**해결 방법:**
1. `@ToolParam`의 `description`에 예시 추가
2. 파라미터 타입 명확히 (String → int 등)
3. Temperature 낮추기 (0.0 ~ 0.3)

### 문제 3: 여러 Tool 중 원하지 않는 Tool이 호출됨

**증상:** 다른 Tool이 호출됨

**해결 방법:**
1. 각 Tool의 `description`을 더 구체적으로
2. Tool 이름을 명확하게 (`search` → `searchFlightsByAirline`)
3. `required = false`로 선택적 파라미터 구분
4. 그 요청에 필요한 Tool만 `.tools(...)`로 넘기기 (Tool이 필요 없는 호출에 `defaultTools`가 붙어 있지 않은지 확인)

---

## 실습 과제

1. **(기본)** 본문의 `CalculatorTool`과 `FunctionCallTestService`를 만들고, `.tools(calculatorTool)`가 있을 때와 지웠을 때 "123456 곱하기 789는?"에 대한 답이 어떻게 달라지는지 비교하세요. (LLM 혼자서는 큰 수 곱셈을 자주 틀립니다.)
2. **(기본)** `FlightSearchTool`, `AirportInfoTool`, `AirlineInfoTool`의 `@Tool` 메서드 7개를 찾아 표로 정리하세요. (메서드 이름, 파라미터, 반환 타입, description의 첫 문장)
3. **(도전)** `ChatClientConfig`의 `ollamaChatClient`에 일부러 `.defaultTools(flightSearchTool)`을 추가한 뒤, 화면에서 Orchestrator 패턴으로 검색해 보세요. `SimpleLoggerAdvisor` 로그에서 계획 요청(`FlightSearchPlanner`)에도 Tool 정의가 붙어 나가는지 확인하고, 실험이 끝나면 원래대로 되돌리세요.
4. **(도전)** `CalculatorTool`에 `@Tool(returnDirect = true)`인 메서드를 하나 추가하고, LLM이 결과를 다시 받지 않고 바로 응답이 끝나는 것을 확인하세요.

---

## 요약

이번 단계에서는 LLM이 자바 메서드를 직접 호출하는 Function Calling을 구현했습니다:

1. **@Tool 어노테이션**: `@Tool(description=...)`로 LLM이 호출할 수 있는 메서드를 정의하고, `@ToolParam(description=...)`으로 각 파라미터의 의미를 LLM에게 설명했습니다
2. **Tool 등록**: 빌더의 `defaultTools()`로 모든 요청에 등록하거나, 요청마다 `.tools(...)`로 넘길 수 있습니다. 이 프로젝트는 Tool이 필요 없는 LLM 호출이 Tool을 부르지 않도록 `ToolCallingSearchService`에서만 `.tools(...)`를 씁니다
3. **LLM의 추론 과정**: LLM이 사용자의 자연어 질문을 분석하여 어떤 Tool을 호출할지, 어떤 파라미터를 넘길지 스스로 결정하는 과정을 이해했습니다
4. **description의 중요성**: Tool과 파라미터의 description이 명확해야 LLM이 정확하게 Tool을 선택하고 파라미터를 추출한다는 것을 배웠습니다

다음 단계에서는 ChatClient의 Advisor로 Function Calling의 실행 과정을 모니터링하고 디버깅하는 방법을 배우겠습니다.

---

## 체크리스트

- [ ] `CalculatorTool.java`가 생성됨
- [ ] `@Tool`, `@ToolParam` 어노테이션 사용됨
- [ ] `defaultTools`와 요청마다 `.tools(...)`의 차이를 설명할 수 있음
- [ ] 실습용 `FunctionCallTestService`에서 `.tools(calculatorTool)`로 Tool을 넘김
- [ ] Function Calling 테스트가 동작함
- [ ] 콘솔에서 Tool 호출 로그 확인됨
- [ ] LLM이 적절한 Tool을 선택함
- [ ] 파라미터가 올바르게 추출됨

---

## 다음 단계

[Step 4: ChatClient Advisor와 Tool 실행 과정 이해](step-4-tool-callback.md)에서 Advisor로 Function Calling 실행 과정을 로깅하고 모니터링하는 방법을 학습합니다.

---

## 참고 자료

- [Spring AI Tool Calling 공식 문서](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [@Tool 어노테이션 (Methods as Tools, 1.1 문서)](https://docs.spring.io/spring-ai/reference/1.1/api/tools.html#_methods_as_tools)

---

## 심화 학습

### 왜 @Tool을 사용하나요?

1. **표준화된 방식**: Spring AI 1.1.4의 공식 어노테이션
2. **스키마 자동 생성**: 메서드 시그니처와 `@ToolParam`으로 입력 JSON 스키마를 자동 생성
3. **Bean 관리**: `@Component`로 등록하고 `.tools()`(또는 `defaultTools()`)로 ChatClient에 연결
4. **LLM 호환성**: OpenAI, Gemini, Ollama 등 모두 지원

### Function Calling vs 일반 API 호출

| 항목 | Function Calling | 일반 API |
|------|------------------|----------|
| **인터페이스** | 자연어 | HTTP 요청 |
| **파라미터 추출** | LLM이 자동 추출 | 수동 파싱 |
| **에러 처리** | Tool 예외 메시지가 LLM에 전달됨 (기본 설정), LLM이 대응 | 수동 처리 |
| **유연성** | 높음 (LLM이 상황 판단) | 낮음 (고정 로직) |
