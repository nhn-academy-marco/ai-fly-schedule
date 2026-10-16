# Step 3: Function Calling 기초

Step 2에서 만든 `ChatClient`는 LLM에게 질문하고 글이나 JSON을 돌려받기만 했습니다. 하지만 LLM은 오늘의 항공편을 알지 못하고, 큰 수의 곱셈도 자주 틀립니다. 이번에는 LLM이 우리가 만든 자바 메서드를 호출해 달라고 요청하게 만들어, 정확한 계산과 실시간 데이터를 코드에 맡깁니다.

**이 Step에서 만들 것**: 실습용 계산기 Tool(`CalculatorTool`)과 테스트용 엔드포인트를 만들어, "10과 20의 합은?"이라는 질문에 LLM이 `add(10, 20)`을 호출하게 합니다. 그런 다음 프로젝트의 실제 Tool(`FlightSearchTool` 등)과, Tool을 요청마다 넘기는 `ToolCallingSearchService`의 코드를 읽습니다.

```text
$ curl -G "http://localhost:8080/api/test/function-calling" --data-urlencode "message=10과 20의 합은 얼마야?"
10과 20의 합은 30입니다.
```

## 학습 목표

이 Step을 마치면 다음을 할 수 있습니다.

1. Function Calling이 무엇인지, LLM과 Spring AI가 각각 어떤 일을 맡는지 설명할 수 있다.
2. `@Tool`과 `@ToolParam`으로 LLM이 호출할 수 있는 메서드를 정의할 수 있다.
3. Tool을 넘기는 두 방법(`defaultTools`, 요청마다 `.tools(...)`)을 구분하고, 이 프로젝트가 요청마다 넘기는 이유를 설명할 수 있다.
4. 실습용 Tool을 만들어 curl로 Function Calling을 실행하고, 로그로 Tool 실행을 확인할 수 있다.
5. 좋은 Tool 설명(description)의 기준을 말하고, 프로젝트의 Tool 7개를 그 기준으로 읽을 수 있다.

---

## 전체 흐름

```text
[사용자]  "10과 20의 합은?"
  ↓
[서비스]  chatClient.prompt().user(...).tools(calculatorTool).call()
  ↓  질문 + Tool 목록(이름, 설명, 파라미터 JSON 스키마)
[LLM]  "add(a=10, b=20)을 호출해 줘"   ← Tool 호출 요청만 보냄
  ↓
[Spring AI]  CalculatorTool.add(10, 20) 실행 → 30
  ↓  Tool 결과
[LLM]  "10과 20의 합은 30입니다."
  ↓
[서비스]  .content()로 최종 답변 반환
```

기존 방식과 비교하면 다음과 같습니다.

```text
기존:              [입력] → [if-else·정규식 파싱] → [메서드 호출] → [결과 포맷팅]
Function Calling:  [입력] → [LLM이 Tool과 인자 결정] → [Spring AI가 메서드 실행] → [LLM이 답변 작성]
```

---

## Function Calling이란?

**Function Calling**은 LLM이 질문을 보고 "어떤 메서드를 어떤 인자로 호출할지" 정해 요청하면, 애플리케이션이 그 메서드를 실행해 결과를 LLM에게 돌려주는 기능입니다. 이때 LLM이 호출할 수 있게 공개한 메서드를 **Tool**이라고 부릅니다. Spring AI 공식 문서는 같은 기능을 Tool Calling이라고 부릅니다.

### 누가 무엇을 하는가

| 주체 | 하는 일 | 하지 않는 일 |
|------|---------|--------------|
| LLM | 질문의 의도를 파악하고, Tool과 인자를 골라 호출을 요청하고, 결과로 답변 작성 | 코드를 직접 실행하지 않음 |
| Spring AI | Tool 목록을 JSON 스키마로 만들어 LLM에게 보내고, LLM의 요청대로 메서드를 실행해 결과를 다시 보냄 | 어떤 Tool을 쓸지 정하지 않음 |
| 우리 코드(Tool) | 계산, API 조회처럼 정확해야 하는 실제 작업 | 자연어를 해석하지 않음 |

LLM은 코드를 실행하지 않는다는 점이 중요합니다. 실행은 언제나 JVM 안에서, 우리가 공개한 메서드로만 일어납니다.

### 언제 쓰는가

| 알맞은 경우 | 알맞지 않은 경우 |
|-------------|------------------|
| 입력 문장의 형태가 다양해 규칙으로 파싱하기 어려움 | 입력 형식이 고정되어 있음 (폼, 버튼) |
| 질문에 따라 필요한 작업이 달라짐 | 처리 순서가 항상 같음 |
| 실시간 데이터나 정확한 계산이 필요함 | 응답 속도와 비용이 가장 중요함 |

이 프로젝트의 Coordinator 패턴(Step 9)은 처리 순서가 항상 같으므로 Tool을 쓰지 않고 코드가 순서를 정합니다. 반대로 Tool Calling 에이전트(Step 10)는 LLM이 Tool을 골라 부르게 합니다. 두 방식을 비교하는 것이 이 교재 후반의 핵심입니다.

---

## Step 3-1: @Tool 어노테이션

### 왜 필요한가

LLM이 메서드를 호출하게 하려면, LLM에게 "이런 메서드가 있고, 이럴 때 쓰고, 이런 인자를 받는다"고 알려야 합니다. `@Tool`과 `@ToolParam`이 그 정보를 메서드에 붙이는 어노테이션입니다. Spring AI는 이 정보로 Tool의 이름, 설명, 파라미터 JSON 스키마를 만듭니다.

### 어떻게 하는가

실습용 계산기 Tool을 만들어 봅니다. 프로젝트에 들어 있지 않은 파일이므로 직접 만듭니다. 프로젝트 코드와 섞이지 않도록 `practice` 패키지에 둡니다.

파일(직접 만들기): `src/main/java/net/java21/flyschedule/practice/CalculatorTool.java`

```java
package net.java21.flyschedule.practice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CalculatorTool {

    @Tool(description = "두 정수의 합을 계산합니다. 덧셈이 필요할 때 사용합니다.")
    public long add(
            @ToolParam(description = "첫 번째 정수") long a,
            @ToolParam(description = "두 번째 정수") long b) {
        log.info("Tool 실행: {} + {} = {}", a, b, a + b);
        return a + b;
    }

    @Tool(description = "두 정수의 곱을 계산합니다. 곱셈이 필요할 때 사용합니다.")
    public long multiply(
            @ToolParam(description = "첫 번째 정수") long a,
            @ToolParam(description = "두 번째 정수") long b) {
        log.info("Tool 실행: {} * {} = {}", a, b, a * b);
        return a * b;
    }
}
```

**코드 읽기**

1. `@Component`: 서비스에서 생성자로 주입받기 위해 빈으로 등록합니다. Spring AI가 `@Tool` 메서드를 자동으로 찾아 등록하지는 않습니다. ChatClient에 직접 넘겨야 합니다(Step 3-2).
2. `@Tool(description = ...)`: LLM은 이 설명을 읽고 언제 이 Tool을 쓸지 정합니다. 설명이 모호하면 LLM이 Tool을 고르지 못합니다.
3. `@ToolParam(description = ...)`: LLM이 문장에서 값을 뽑아 이 파라미터에 넣을 때 참고합니다. "10과 20의 합"에서 `{"a": 10, "b": 20}`을 만들어 냅니다.
4. 메서드 본문은 평범한 자바 코드입니다. 반환값은 JSON으로 바뀌어 LLM에게 전달되고, LLM은 그 값으로 답변을 씁니다.

| 어노테이션 | 속성 | 기본값 | 뜻 |
|------------|------|--------|-----|
| `@Tool` | `description` | 메서드 이름 | Tool이 하는 일. LLM이 Tool을 고를 때 참고 |
| `@Tool` | `name` | 메서드 이름 | LLM에게 보이는 Tool 이름 |
| `@Tool` | `returnDirect` | `false` | `true`면 Tool 결과를 LLM에게 다시 보내지 않고 그대로 최종 응답으로 반환 |
| `@ToolParam` | `description` | 없음 | 파라미터의 의미와 형식 |
| `@ToolParam` | `required` | `true` | `false`면 LLM이 값을 비워도 되는 선택 파라미터 |

### 확인하기

다음 절에서 이 Tool을 `ChatClient`에 넘긴 뒤 Step 3-3에서 실행해 확인합니다. 지금은 `./mvnw compile`로 컴파일만 확인합니다.

---

## Step 3-2: Tool 등록 방법

### 왜 필요한가

`@Tool` 메서드를 만들었어도 `ChatClient`가 그 존재를 모르면 LLM에게 알려 줄 수 없습니다. `@Service`를 만들어도 주입하지 않으면 쓸 수 없는 것과 같습니다. Tool을 넘기는 위치는 두 가지입니다.

| 방법 | 코드 | Tool 목록이 붙는 범위 |
|------|------|------------------------|
| 기본값으로 등록 | `ChatClient.builder(model).defaultTools(...)` | 이 ChatClient로 보내는 모든 요청 |
| 요청마다 전달 | `chatClient.prompt()...tools(...)` | `.tools(...)`를 붙인 그 요청만 |

### 어떻게 하는가

**방법 1: 빌더에 기본 등록** (설명용 예시, 이 프로젝트는 쓰지 않음)

```java
@Bean
public ChatClient calculatorChatClient(@Qualifier("ollamaChatModel") ChatModel ollamaChatModel,
                                       CalculatorTool calculatorTool) {
    return ChatClient.builder(ollamaChatModel)
            .defaultTools(calculatorTool)   // 이 ChatClient의 모든 요청에 Tool 목록이 붙음
            .build();
}
```

이 ChatClient의 용도가 Tool 사용 하나뿐이라면 편리합니다.

**방법 2: 요청마다 전달** (이 프로젝트의 방식)

파일: `src/main/java/net/java21/flyschedule/service/toolcalling/ToolCallingSearchService.java`

```java
String answer = chatClientProvider.get(model).prompt()
        .system(systemPrompt())
        .user(message)
        .tools(flightSearchTool, airportInfoTool, airlineInfoTool)
        .call()
        .content();
```

**코드 읽기**

1. `.tools(...)`에 `@Tool` 메서드를 가진 객체를 넘깁니다. 세 객체에 들어 있는 Tool 7개가 이 요청에만 붙습니다.
2. 같은 `ChatClient`를 쓰더라도 `.tools(...)`가 없는 요청에는 Tool 목록이 가지 않습니다.
3. `.call()` 안에서 LLM 호출 → Tool 실행 → LLM 재호출이 필요한 만큼 반복되고, `.content()`는 마지막 답변만 돌려줍니다.

이 프로젝트가 요청마다 넘기는 이유는, 같은 `ChatClient`를 Tool이 필요 없는 곳에서도 쓰기 때문입니다.

| LLM 호출 위치 | 하는 일 | Tool 필요 |
|---------------|---------|-----------|
| `SearchCriteriaExtractor` | 문장에서 검색 조건 추출 (`.entity`) | 아니요 |
| `FlightSearchPlanner` | 요청을 검색 작업으로 나누기 (`.entity`) | 아니요 |
| `ResultSynthesizer` | 작업별 결과 요약 (`.content`) | 아니요 |
| `ToolCallingSearchService` | LLM이 Tool을 골라 항공편 검색 | 예 |

`defaultTools`로 등록했다면 위의 세 호출에도 Tool 목록이 붙습니다. 그러면 검색 조건만 뽑아야 할 LLM이 직접 `searchFlightsByAirline`을 부르는 식의 의도하지 않은 호출이 생기고, Tool 설명만큼 프롬프트가 길어져 응답도 느려집니다. 요청마다 넘기면 어떤 호출이 Tool을 쓸 수 있는지가 코드에 그대로 드러납니다.

> **팁**: 여러 Tool 객체를 `MethodToolCallbackProvider.builder().toolObjects(...).build()`로 묶어 `ToolCallbackProvider` 빈으로 만들면, 같은 Tool 묶음을 여러 곳에서 `.toolCallbacks(provider)`로 재사용할 수 있습니다. 이 프로젝트는 Tool을 쓰는 곳이 한 곳뿐이라 쓰지 않습니다.

### 확인하기

```bash
./mvnw test -Dtest=ToolCallingSearchServiceTest#passesToolsPerRequest
```

이 테스트는 가짜 `ChatClient`로 `ToolCallingSearchService.search()`를 실행한 뒤, 요청 스펙의 `tools(...)`가 Tool 객체 세 개로 호출되었는지(`verify(requestSpec).tools(any(), any(), any())`) 검증합니다.

---

## Step 3-3: 실습 — Function Calling 실행하기

### 왜 필요한가

Tool을 만들고 넘기는 방법을 배웠으니, LLM이 실제로 Tool을 고르고 인자를 올바르게 뽑는지 눈으로 확인합니다. 결과가 분명한 계산기로 먼저 시험하면 LLM의 판단을 확인하기 쉽습니다.

### 어떻게 하는가

다음 두 파일은 실습용이며 프로젝트에 들어 있지 않습니다. Step 3-1과 같은 `practice` 패키지에 직접 만듭니다.

파일(직접 만들기): `src/main/java/net/java21/flyschedule/practice/FunctionCallTestService.java`

```java
package net.java21.flyschedule.practice;

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

    public String ask(String message) {
        return chatClient.prompt()
                .user(message)
                .tools(calculatorTool)   // 이 요청에만 Tool 전달
                .call()
                .content();
    }
}
```

파일(직접 만들기): `src/main/java/net/java21/flyschedule/practice/FunctionCallTestController.java`

```java
package net.java21.flyschedule.practice;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FunctionCallTestController {

    private final FunctionCallTestService testService;

    @GetMapping("/api/test/function-calling")
    public String ask(@RequestParam String message) {
        return testService.ask(message);
    }
}
```

**코드 읽기**

- Step 2의 LLM 호출과 달라진 것은 `.tools(calculatorTool)` 한 줄뿐입니다. 이 한 줄로 요청에 Tool 목록이 붙고, Spring AI가 Function Calling을 처리합니다.
- `@Qualifier("ollamaChatClient")`로 Ollama 클라이언트를 고릅니다. Gemini 키가 있다면 `geminiChatClient`로 바꿔 비교해 보세요.

Tool이 실행되는 순간을 보려면 Spring AI의 Tool 실행 로그를 켭니다.

파일: `src/main/resources/application.yml`에 추가

```yaml
logging:
  level:
    org.springframework.ai.model.tool: DEBUG
```

### 확인하기

애플리케이션을 다시 실행한 뒤 호출합니다.

```bash
curl -G "http://localhost:8080/api/test/function-calling" \
  --data-urlencode "message=10과 20의 합은 얼마야?"
```

```text
10과 20의 합은 30입니다.
```

콘솔에서 Spring AI가 Tool을 실행한 로그와, Tool 메서드 안에서 남긴 로그를 함께 확인합니다(앞부분의 날짜·스레드는 생략).

```text
DEBUG o.s.a.m.t.DefaultToolCallingManager - Executing tool call: add
INFO  n.j.f.practice.CalculatorTool - Tool 실행: 10 + 20 = 30
```

곱셈도 시험해 봅니다.

```bash
curl -G "http://localhost:8080/api/test/function-calling" \
  --data-urlencode "message=123456 곱하기 789는?"
```

`Executing tool call: multiply` 로그가 나오고 답이 `97406784`이면 LLM이 Tool로 계산한 것입니다. 로그 없이 다른 숫자가 나왔다면 LLM이 Tool을 건너뛰고 직접 계산한 것입니다.

> **주의**: 작은 모델(qwen2.5 3B)은 Tool을 부르지 않고 답을 지어내거나, 인자를 잘못 뽑기도 합니다. 같은 질문을 여러 번 보내 결과가 매번 같은지 확인하세요. Function Calling의 품질은 모델의 능력에 크게 좌우됩니다.

---

## Step 3-4: 프로젝트의 Tool 읽기

### 왜 필요한가

계산기는 원리를 보기 위한 예제였습니다. 이 프로젝트의 실제 Tool은 항공편 검색, 공항 코드 조회, 항공사 조회를 맡습니다. 실제 Tool을 읽으면 좋은 description이 어떤 모습인지, Tool이 실제 작업을 어디에 맡기는지 알 수 있습니다.

### 어떻게 하는가

파일: `src/main/java/net/java21/flyschedule/mcp/FlightSearchTool.java`

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

    log.info("MCP Tool 호출: searchFlightsByAirline(departure={}, arrival={}, date={})",
            departure, arrival, date);

    return ToolResultCapture.capture("searchFlightsByAirline",
            "%s → %s, %s".formatted(departure, arrival, date),
            () -> limitPerAirline(flightSearchAgent.searchAndGroupByAirline(departure, arrival, date)));
}
```

**코드 읽기**

1. description은 무엇을 하는지(검색해 항공사별로 묶음), 무엇을 받는지(출발·도착·날짜), 형식(`2026-03-25`), 제약(항공사별 최대 3편)을 모두 밝힙니다.
2. `@ToolParam`마다 예시 값을 붙여, LLM이 공항 코드(`NAARKJJ`)가 아니라 한글 이름을 넘기게 유도합니다.
3. 실제 검색은 `FlightSearchAgent`에 맡기고, `limitPerAirline()`으로 결과를 항공사별 3편으로 줄입니다. 작은 모델은 컨텍스트가 짧아 Tool 결과가 길면 답이 느려지거나 잘리기 때문입니다.
4. `ToolResultCapture.capture(...)`는 Tool 호출을 기록하는 장치입니다. Step 4에서 자세히 봅니다.

프로젝트의 Tool은 세 클래스에 모두 7개입니다(`src/main/java/net/java21/flyschedule/mcp/`).

| 클래스 | Tool 메서드 | 파라미터 | 하는 일 |
|--------|-------------|----------|---------|
| `FlightSearchTool` | `searchFlightsByAirline` | departure, arrival, date | 항공편 검색, 항공사별 그룹핑 |
| `FlightSearchTool` | `searchFlightsAfterTime` | departure, arrival, date, afterTime | 기준 시각 이후 출발 항공편 |
| `FlightSearchTool` | `searchFlightsByPriceRange` | departure, arrival, date, minPrice, maxPrice | 가격 범위 안의 항공편 |
| `AirportInfoTool` | `getAirportList` | 없음 | 전체 공항 목록 |
| `AirportInfoTool` | `getAirportCode` | airportName | 공항 이름 → 공항 코드 |
| `AirlineInfoTool` | `getAirlineList` | 없음 | 전체 항공사 목록 |
| `AirlineInfoTool` | `getAirlineId` | airlineName | 항공사 이름 → 항공사 ID |

패키지 이름이 `mcp`인 것은 이 Tool들을 MCP(Model Context Protocol, 에이전트와 Tool을 잇는 표준 프로토콜) 서버로도 노출할 수 있게 설계했다는 뜻입니다. 지금은 MCP 서버가 아니라 Spring AI의 `@Tool`로만 쓰입니다(Step 5).

### 좋은 description의 기준

| 기준 | 나쁜 예 | 좋은 예 |
|------|---------|---------|
| 하는 일을 분명히 | "검색" | "항공편을 검색하여 항공사별로 그룹핑하여 반환합니다." |
| 입력 형식과 예시 | "날짜" | "날짜, YYYY-MM-DD 형식 (예: 2026-03-25)" |
| 다른 Tool과의 구분 | 세 검색 Tool의 설명이 똑같음 | "특정 시간 이후 출발하는...", "특정 가격 범위 내의..." |
| 결과의 제약 | (없음) | "항공사별 최대 3편만 반환합니다." |

### 확인하기

Tool은 평범한 메서드이므로 LLM 없이도 호출해 볼 수 있습니다. `McpController`는 Tool을 직접 호출하는 REST 엔드포인트를 제공합니다(`DATA_GO_KR_SERVICE_KEY` 필요).

```bash
curl -G "http://localhost:8080/api/mcp/flights/search" \
  --data-urlencode "departure=광주" \
  --data-urlencode "arrival=제주" \
  --data-urlencode "date=2026-10-17"
```

`data`에 항공사 이름을 키로 한 항공편 목록이 항공사마다 최대 3편씩 들어 있으면 됩니다. 단위 테스트로도 확인할 수 있습니다.

```bash
./mvnw test -Dtest=FlightSearchToolTest
```

---

## Step 3-5: Tool 호출의 여러 모양

LLM은 질문에 따라 Tool을 한 번 부르기도 하고, 여러 번 부르기도 합니다. 몇 번, 어떤 순서로 부를지는 LLM이 정합니다.

```text
단일 호출:  [LLM] → searchFlightsByAirline → [LLM] → 답변

연쇄 호출:  [LLM] → getAirportCode("광주") → [LLM] → searchFlightsByAirline(...) → [LLM] → 답변
           (앞 Tool의 결과를 보고 다음 Tool을 고름)

병렬 호출:  [LLM] → searchFlightsByAirline(김포→제주) ┐
                    searchFlightsByAirline(광주→제주) ┘ → [LLM] → 답변
           (한 응답에서 여러 호출을 함께 요청, 지원 여부는 모델마다 다름)
```

병렬 호출이라고 해도 Spring AI는 요청받은 Tool을 차례대로 실행한 뒤 결과를 한꺼번에 LLM에게 돌려줍니다. "김포나 광주에서 제주 가는 항공편"처럼 검색이 여러 번 필요한 요청을 LLM에게 맡길지, 코드가 작업을 나눌지는 Step 10과 Step 11에서 비교합니다.

> **생각해 보기**: Tool을 몇 번 부를지 LLM이 정한다면, 응답 시간과 비용은 미리 예측할 수 있을까요? 이 질문이 Coordinator 패턴과 Tool Calling 에이전트를 가르는 기준이 됩니다.

---

## 일반적인 문제 해결

### LLM이 Tool을 호출하지 않음

증상: `Executing tool call` 로그 없이 LLM이 바로 답합니다.

1. 그 요청에 `.tools(...)`를 붙였는지 확인합니다.
2. `@Tool`의 description에 "언제 쓰는지"를 더 분명히 적습니다.
3. 시스템 프롬프트에 "항공편 정보는 반드시 Tool을 호출해서 얻으세요" 같은 규칙을 둡니다. `ToolCallingSearchService.systemPrompt()`가 그렇게 합니다.
4. 모델을 바꿔 봅니다. 작은 모델은 Tool 호출을 자주 건너뜁니다.

### 인자가 null이거나 잘못 들어옴

1. `@ToolParam` description에 형식과 예시를 적습니다(예: "YYYY-MM-DD 형식 (예: 2026-03-25)").
2. 날짜처럼 계산이 필요한 값은 시스템 프롬프트에 오늘 날짜를 넣어 줍니다.
3. temperature를 낮게(0.0~0.3) 둡니다.

### 원하지 않는 Tool이 호출됨

1. 비슷한 Tool끼리 description이 겹치지 않게 차이를 적습니다.
2. 이름을 구체적으로 짓습니다(`search` 대신 `searchFlightsByAirline`).
3. 그 요청에 필요한 Tool만 `.tools(...)`로 넘깁니다. Tool이 필요 없는 호출에 `defaultTools`가 붙어 있지 않은지 확인합니다.

### Tool에서 예외가 남

Tool이 예외를 던지면 Spring AI는 기본 설정에서 예외 메시지를 Tool 결과로 LLM에게 전달하고, LLM이 그 메시지를 보고 답합니다. 예를 들어 `getAirportCode("서울")`은 "알 수 없는 공항입니다: 서울" 예외를 던지고, LLM은 이를 보고 다른 공항 이름으로 다시 시도하거나 사용자에게 안내합니다.

---

## 실습 과제

### 과제 1 (기본): Tool이 있을 때와 없을 때

`FunctionCallTestService.ask()`에서 `.tools(calculatorTool)`를 지운 메서드를 하나 더 만들고, "123456 곱하기 789는?"을 각각 다섯 번씩 물어봅니다.

- 힌트: Step 3-3의 `FunctionCallTestService`, 정답은 97406784
- 완료 기준: 두 방식의 정답 횟수를 표로 정리하고, Tool을 쓸 때 `Executing tool call: multiply` 로그가 찍히는 것을 확인한다.

### 과제 2 (기본): 프로젝트 Tool 표 만들기

`FlightSearchTool`, `AirportInfoTool`, `AirlineInfoTool`의 `@Tool` 메서드 7개를 읽고, 메서드 이름, 파라미터, 반환 타입, description의 첫 문장을 표로 정리합니다.

- 힌트: `src/main/java/net/java21/flyschedule/mcp/`
- 완료 기준: 7개 행이 모두 채워져 있고, 세 검색 Tool의 description이 서로 어떻게 구분되는지 한 문장으로 적는다.

### 과제 3 (도전): defaultTools의 부작용 관찰

`ChatClientConfig.ollamaChatClient()`에 `FlightSearchTool`을 주입받아 `.defaultTools(flightSearchTool)`을 추가합니다. Step 2-7의 로그 설정을 켜고 화면에서 Orchestrator-Workers 패턴으로 검색합니다.

- 힌트: `SimpleLoggerAdvisor`의 request 로그에서 `FlightSearchPlanner`의 계획 요청을 찾습니다.
- 완료 기준: Tool이 필요 없는 계획 요청에도 Tool 정의가 붙어 나가는 것을 로그로 확인하고, 그 결과 생길 수 있는 문제를 두 가지 적는다. 실험 뒤 원래대로 되돌린다.

### 과제 4 (도전): returnDirect 실험

`CalculatorTool`에 `@Tool(description = "...", returnDirect = true)`인 메서드(예: `subtract`)를 추가하고 "50에서 8을 빼 줘"를 물어봅니다.

- 힌트: `returnDirect = true`면 Tool 결과가 LLM에게 돌아가지 않습니다.
- 완료 기준: 응답이 자연어 문장이 아니라 Tool의 반환값(`42`) 그대로 오는 것을 확인하고, 이 옵션이 어울리는 상황을 한 가지 적는다.

---

## 요약

- Function Calling은 LLM이 Tool과 인자를 정해 호출을 요청하고, Spring AI가 그 메서드를 실행해 결과를 돌려주는 기능입니다. LLM은 코드를 실행하지 않습니다.
- `@Tool(description)`과 `@ToolParam(description)`으로 Tool을 정의하며, 설명의 품질이 Tool 선택과 인자 추출의 정확도를 좌우합니다.
- Tool은 `defaultTools`로 기본 등록하거나 요청마다 `.tools(...)`로 넘깁니다. 이 프로젝트는 `ToolCallingSearchService`에서만 `.tools(...)`를 씁니다.
- 프로젝트의 Tool 7개는 `mcp` 패키지에 있고, 실제 작업은 전문 에이전트(`FlightSearchAgent` 등)에 맡깁니다.

그런데 LLM이 어떤 Tool을 어떤 인자로 불렀는지는 지금까지 로그로만 보았습니다. Step 4에서는 Advisor로 요청과 응답을 들여다보고, Tool 호출을 데이터로 기록하는 방법을 배웁니다.

---

## 체크리스트

- [ ] Function Calling에서 LLM, Spring AI, Tool 코드가 각각 맡는 일을 설명할 수 있다. (목표 1)
- [ ] `@Tool`, `@ToolParam`으로 `CalculatorTool`을 만들었다. (목표 2)
- [ ] `defaultTools`와 `.tools(...)`의 차이와, 이 프로젝트가 요청마다 넘기는 이유를 설명할 수 있다. (목표 3)
- [ ] curl로 `/api/test/function-calling`을 호출하고 `Executing tool call` 로그를 확인했다. (목표 4)
- [ ] 좋은 description의 기준을 말하고, 프로젝트 Tool 7개를 표로 정리했다. (목표 5)

---

## 다음 단계

[Step 4: ChatClient Advisor와 Tool 실행 과정 이해](step-4-tool-callback.md)에서 Advisor로 LLM 요청·응답을 기록하고, `ToolResultCapture`로 Tool 호출을 실행 기록으로 남기는 방법을 배웁니다.

---

## 참고 자료

- [Spring AI Tool Calling 공식 문서](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [@Tool 어노테이션 (Methods as Tools, 1.1 문서)](https://docs.spring.io/spring-ai/reference/1.1/api/tools.html#_methods_as_tools)

---

## 심화 학습

### Tool 정의는 어떻게 LLM에게 전달되는가

Spring AI는 `@Tool` 메서드마다 이름, 설명, 파라미터 JSON 스키마를 만들어 LLM API의 Tool 정의 형식으로 보냅니다. `add(long a, long b)`는 대략 다음과 같이 전달됩니다.

```json
{
  "name": "add",
  "description": "두 정수의 합을 계산합니다. 덧셈이 필요할 때 사용합니다.",
  "parameters": {
    "type": "object",
    "properties": {
      "a": { "type": "integer", "format": "int64", "description": "첫 번째 정수" },
      "b": { "type": "integer", "format": "int64", "description": "두 번째 정수" }
    },
    "required": ["a", "b"]
  }
}
```

Step 2의 Structured Output이 `@JsonPropertyDescription`으로 응답 스키마를 설명했다면, Function Calling은 `@ToolParam`으로 입력 스키마를 설명합니다. 두 기능 모두 "LLM에게 JSON 스키마를 보여 주고, 그 형식에 맞는 JSON을 받는다"는 같은 원리 위에 있습니다.

### Function Calling과 일반 API 호출

| 항목 | Function Calling | 일반 API 호출 |
|------|------------------|---------------|
| 호출을 정하는 쪽 | LLM | 개발자의 코드 |
| 입력 | 자연어 | 정해진 파라미터 |
| 오류 처리 | 예외 메시지가 LLM에게 전달되고 LLM이 대응 | 코드에서 직접 처리 |
| 예측 가능성 | 낮음 (호출 횟수·순서가 달라질 수 있음) | 높음 |
