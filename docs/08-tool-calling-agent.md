# 8장 Tool Calling Agent — LLM이 결정한다

"모레 김포에서 제주 오후 2시 이후 항공편" — 7장의 Coordinator는 이 문장을 받으면 정규식으로 조건을 뽑고, `afterTime`이 있으니 `TimeFilterAgent`를 부르는 `if` 문을 탔습니다. 다음 단계를 정한 것은 개발자가 쓴 **코드**였습니다.

이번 장에서는 같은 문장을 LLM에게 통째로 맡깁니다. 코드는 질문과 Tool 목록만 넘기고, "시간 조건이 있으니 `searchFlightsAfterTime`을 부르자"는 판단은 LLM이 합니다. 이 책의 질문 "다음 단계를 누가 결정하는가"에 대해, 이 장의 답은 **LLM**입니다. 그 대가로 무엇을 얻고 무엇을 잃는지가 이 장의 주제입니다.

## 8.1 LLM에게 넘기는 세 가지 결정

Anthropic의 글 [Building effective agents](https://www.anthropic.com/engineering/building-effective-agents)는 LLM을 쓰는 시스템을 둘로 나눕니다. 코드가 정해 둔 경로를 따라가는 **workflow**와, LLM이 스스로 경로를 정하는 **agent**입니다. 7장의 Coordinator 패턴은 workflow이고, 이 장의 **Tool Calling Agent**는 agent입니다.

Tool Calling Agent는 LLM 하나가 Tool 목록을 받아 다음 세 가지를 스스로 정합니다.

| 결정 | 예 |
|---|---|
| **어떤 Tool**을 부를지 | `searchFlightsByAirline`인가, `searchFlightsAfterTime`인가, `searchFlightsByPriceRange`인가 |
| **어떤 인자**로 부를지 | "모레" → `"2026-10-18"`, "오후 2시" → `"14:00"` |
| **몇 번** 부를지 | 한 번으로 끝낼지, 결과를 보고 다른 Tool을 더 부를지 |

같은 문장을 두 패턴으로 처리하면 차이가 분명해집니다.

| | Coordinator 패턴(7장) | Tool Calling Agent(이 장) |
|---|---|---|
| 처리 순서 | `FlightSearchCoordinator` 코드에 고정 | LLM이 Tool 설명을 읽고 정함 |
| "이후"를 보고 시간 필터를 쓰는 판단 | 코드의 `if (criteria.afterTime() != null)` | LLM이 `searchFlightsAfterTime`을 고름 |
| 처음 보는 표현("저녁 비행기") | 정규식에 없으면 LLM fallback 또는 실패 | LLM이 해석해 볼 수 있음 |
| 같은 입력의 결과 | 항상 같음 | 실행마다 달라질 수 있음 |

LLM이 Tool을 직접 실행하지는 않는다는 점은 [4장](04-tool-calling.md)에서 보았습니다. LLM은 "이 Tool을 이 인자로 불러 달라"고 **요청**하고, 실행은 Spring AI가 맡습니다. 이 장에서 LLM에게 넘기는 Tool은 [5장](05-domain-tools.md)에서 만든 `FlightSearchTool`의 항공편 검색 Tool 3개입니다. 공항·항공사 조회 Tool은 넘기지 않습니다(이유는 5장, 효과는 8.9).

## 8.2 ToolCallingSearchService — 코드에 if 문이 없다

`src/main/java/net/java21/flyschedule/service/toolcalling/ToolCallingSearchService.java`

```java
    public SearchResponse search(String message, String model, ExecutionTrace trace) {
        ToolResultCapture.getAndClear(); // 이전 요청의 기록이 남아 있지 않도록 비운다
        ToolResultCapture.bind(trace);   // Tool이 실행되는 즉시 TOOL 단계로 기록 (화면에서 실시간으로 보임)

        long start = System.nanoTime();
        try {
            String answer = chatClientProvider.get(model).prompt()
                    .system(systemPrompt())
                    .user(DateContext.annotate(message)) // 날짜 표현 옆에 코드가 계산한 날짜를 붙여 보낸다
                    // 항공편 검색 Tool 3개만 넘긴다. 공항·항공사 조회 Tool(AirportInfoTool, AirlineInfoTool)은
                    // 검색 Tool이 한글 공항 이름을 직접 받으므로 필요 없고, 넘기면 LLM이 매번 읽어야 할 Tool 설명만 늘어난다
                    .tools(flightSearchTool)
                    .call()
                    .content();

            // Tool 단계는 실행될 때 이미 기록됐다. LLM 호출 구간(Tool 실행을 감싸는 전체 구간)을 마지막에 남긴다
            List<ToolCall> calls = ToolResultCapture.getAndClear();
            trace.record(Kind.LLM, ChatClientProvider.normalize(model),
                    "Tool 선택과 답변 작성", "Tool " + calls.size() + "회 호출 후 답변", start);

            if (calls.isEmpty()) {
                return SearchResponse.fail(PATTERN,
                        StringUtils.hasText(answer) ? answer : "LLM이 Tool을 호출하지 않았습니다.", trace);
            }

            TaskResult result = lastFlightResult(calls);
            String text = StringUtils.hasText(answer) ? answer : "검색을 마쳤습니다.";
            return SearchResponse.success(PATTERN, text, result == null ? List.of() : List.of(result), trace);

        } catch (IllegalArgumentException e) {
            ToolResultCapture.getAndClear();
            return SearchResponse.fail(PATTERN, e.getMessage(), trace);
        } catch (Exception e) {
            log.error("Tool Calling 검색 실패 (model={})", model, e);
            ToolResultCapture.getAndClear(); // 실패 전까지 실행된 Tool은 이미 기록됐다. ThreadLocal만 비운다
            trace.record(Kind.LLM, ChatClientProvider.normalize(model), "Tool 선택과 답변 작성",
                    "실패: " + ChatClientProvider.describeError(e), start);
            return SearchResponse.fail(PATTERN, ChatClientProvider.describeError(e), trace);
        }
    }
```

코드 읽기

1. `ToolResultCapture.bind(trace)`: 이 작업의 실행 기록을 현재 스레드에 연결합니다. 이제 `call()` 안에서 Tool이 실행될 때마다 그 즉시 TOOL 단계가 `trace`에 쌓입니다(8.6).
2. `chatClientProvider.get(model)`: 화면에서 고른 모델(Ollama 또는 Gemini)의 `ChatClient`를 돌려줍니다([2장](02-chatclient.md)). Gemini 키가 없으면 `IllegalArgumentException`이 나고, 첫 번째 `catch`가 그 안내 문장을 그대로 응답합니다.
3. `.user(DateContext.annotate(message))`: 사용자 문장을 그대로 보내지 않고, "이번 주 토요일" 같은 표현 옆에 코드가 계산한 날짜를 붙여 보냅니다(8.4).
4. `.tools(flightSearchTool)`: 이 요청에서 LLM이 쓸 수 있는 Tool로 `FlightSearchTool` 객체 하나(검색 메서드 3개)를 넘깁니다. 주석대로, 쓰지 않는 Tool을 넘기면 LLM이 매번 읽어야 할 Tool 설명만 늘어납니다.
5. `.call().content()`: Tool 호출이 몇 번 오가든 모두 끝난 뒤 최종 답변 문자열만 돌려줍니다.
6. `ToolResultCapture.getAndClear()`: `call()` 동안 실행된 Tool의 이름·인자·결과·실행 구간을 꺼내고, `bind`로 맺은 연결도 풉니다. 꺼낸 기록은 Tool 호출 횟수와 화면용 결과를 만드는 데 씁니다. 이어서 `call()` 전체 구간을 LLM 단계로 **마지막에** 기록합니다.
7. LLM 호출이 실패하면(두 번째 `catch`) LLM 단계를 "실패: ..."로 남깁니다. 그 전에 실행된 TOOL 단계는 이미 기록되어 있으므로 그대로 남습니다.
8. Tool을 한 번도 부르지 않았으면 실패로 처리합니다. 이유는 8.4에서 봅니다.

코드에는 "어떤 Tool을 부를지" 정하는 분기가 하나도 없습니다. 그 판단은 모두 `call()` 안, LLM에게 넘어갔습니다.

## 8.3 call() 한 번 안에서 일어나는 일

`call()`은 메서드 호출 한 번이지만, 안에서는 LLM과 여러 번 오갑니다.

```mermaid
sequenceDiagram
    participant S as ToolCallingSearchService(코드)
    participant AI as Spring AI ChatModel
    participant L as LLM
    participant T as FlightSearchTool(Tool)
    S->>AI: prompt().system().user().tools().call()
    rect rgb(241,235,253)
        AI->>L: 요청 1 - 질문과 Tool 정의 3개
        L-->>AI: tool_call searchFlightsAfterTime(김포, 제주, 2026-10-18, 14시)
        AI->>T: @Tool 메서드 실행 (같은 스레드)
        T-->>AI: 항공편 요약문 (그 즉시 TOOL 단계 기록)
        AI->>L: 요청 2 - Tool 결과를 대화에 덧붙임
        L-->>AI: 최종 답변 (tool_call 없음)
    end
    AI-->>S: content() 답변 문자열
    S->>S: LLM 단계 기록 (TOOL 단계는 실행 때 이미 기록됨)
```

그림 읽기: 보라색 구간 전체가 실행 기록의 **LLM 단계 하나**입니다. Tool 실행은 그 구간 **안에서** 일어나므로, 화면 타임라인에서도 TOOL 막대는 LLM 막대 안쪽에 그려집니다. 기록되는 순서는 반대입니다. TOOL 단계는 실행되는 순간 먼저 남고, 구간 전체를 감싸는 LLM 단계는 `call()`이 끝난 뒤 마지막에 남습니다.

Tool을 한 번 부르면 모델과는 최소 두 번 오갑니다(Tool 요청 1번 + 최종 답변 1번). LLM이 Tool을 두 번에 나눠 부르면 세 번입니다. 코드에서 보이는 LLM 호출은 `call()` 한 번이지만, 비용과 지연은 왕복 수만큼 늘어납니다.

또 하나 중요한 성질이 있습니다. Spring AI 1.1.4는 `call()`에서 Tool을 **`call()`을 부른 그 스레드에서 순서대로** 실행합니다. 5장의 `ToolResultCapture`가 `ThreadLocal`에 기록을 담아도 요청끼리 섞이지 않는 이유가 이것입니다. `stream()`은 다른 스레드에서 Tool을 실행하므로 이 방식을 쓸 수 없습니다.

## 8.4 system 프롬프트 — Coordinator의 if 문을 대신하는 규칙

코드에서 분기가 사라졌다면, LLM은 무엇을 기준으로 판단할까요? Tool 설명(`@Tool`, `@ToolParam`)과 system 프롬프트입니다.

`src/main/java/net/java21/flyschedule/service/toolcalling/ToolCallingSearchService.java`

```java
    private String systemPrompt() {
        return """
                당신은 국내선 항공편 검색 도우미입니다. 반드시 한국어로 답변하세요.
                항공편 정보는 반드시 Tool을 호출해서 얻고, Tool 결과에 없는 내용은 지어내지 마세요.
                규칙:
                - 날짜는 "2026-06-25" 형식으로 Tool에 전달합니다.
                - 시간은 24시간 형식("14:00")으로 전달합니다.
                - 공항은 한글 이름 그대로 전달합니다. (예: "광주", "제주")
                - "내일", "이번 주 금요일" 같은 날짜는 계산하지 말고 아래 날짜표에서 찾습니다.
                - 답변은 항공편 수와 가장 저렴한 항공편을 중심으로 2~3문장으로 짧게 요약합니다. 항공편 목록을 나열하지 마세요.

                %s
                """.formatted(DateContext.describe());
    }
```

| 설계 | 이유 |
|---|---|
| **근거 제한(grounding)**: "반드시 Tool을 호출해서 얻고, Tool 결과에 없는 내용은 지어내지 마세요" | LLM은 그럴듯한 항공편 번호와 가격을 지어낼 수 있습니다(환각). 답의 근거를 Tool 결과로 묶어 둡니다 |
| **날짜를 붙인 사용자 문장** `.user(DateContext.annotate(message))` | 날짜표를 준 뒤에도 운영에서 `qwen2.5:3b`가 "이번 주 토요일 김포에서 제주"를 2026-10-18로(정답 10-17) 검색했습니다. 그래서 LLM이 고를 일도 남기지 않고 `이번 주 토요일(2026-10-17)`처럼 코드가 날짜를 붙여 보냅니다([2장](02-chatclient.md)) |
| **날짜표** `%s` ← `DateContext.describe()` | LLM은 오늘이 며칠인지 모릅니다. 오늘·내일·모레와 이번 주·다음 주의 요일별 날짜를 표로 주고, "계산하지 말고 날짜표에서 찾으라"고 합니다. 오늘 날짜 한 줄만 주었을 때 작은 모델이 "이번 주 금요일"을 하루 늦게 계산한 사례가 있었습니다([2장](02-chatclient.md)) |
| **짧은 규칙 목록** | 긴 문단보다 한 줄짜리 규칙이 작은 모델에게도 잘 지켜집니다. 규칙이 Tool 인자 형식을 하나로 맞춰 Tool 안의 `DateParserAgent`·`AirportCodeAgent`가 처리할 수 있게 합니다 |
| **답변 길이 제한** "2~3문장으로 짧게", "항공편 목록을 나열하지 마세요" | 출력 토큰이 줄어 빨라지고, 작은 모델이 길게 쓰다 엉뚱한 내용을 덧붙이는 일이 줄어듭니다. 항공편 목록은 화면의 표가 보여 주므로 답변에서 되풀이할 필요가 없습니다. 처음에는 3~4문장이었는데, 8.9의 측정 뒤 줄였습니다 |

8.2의 코드에서 Tool 호출이 0회면 실패로 처리한 것도 근거 제한의 연장입니다. Tool 없이 나온 답은 되묻기("어느 공항에서 출발하시나요?")이거나 지어낸 항공편일 수 있으므로, 성공으로 보여 주지 않고 LLM의 말을 안내 문장으로만 보여 줍니다.

> **생각해 보기**
> 날짜는 두 단계로 LLM의 손을 떠났습니다. 날짜표는 **계산**을 없애고 **찾기**만 남겼고, `annotate()`는 찾기마저 없앴습니다. 날짜 계산처럼 답이 하나인 일은 LLM보다 코드(`DateParserAgent`, 6장)가 잘합니다. 이 system 프롬프트에서 아직 LLM에게 맡긴 일 가운데 코드로 옮길 수 있는 것이 있을까요? "오후 2시" → "14:00" 같은 변환은 어떨까요?

## 8.5 Tool은 요청마다 넘긴다

`ChatClientConfig`는 Tool을 `defaultTools(...)`로 등록하지 않습니다([4장](04-tool-calling.md)). 같은 `ChatClient`를 여러 곳에서 쓰기 때문입니다.

| 사용하는 곳 | LLM에게 시키는 일 | Tool 필요 |
|---|---|---|
| `SearchCriteriaExtractor`(7장) | 문장 → `SearchCriteria` | 아니요 |
| `FlightSearchPlanner`(9장) | 요청 → 작업 계획 | 아니요 |
| `ResultSynthesizer`(9장) | 결과 요약 → 추천 문장 | 아니요 |
| `ToolCallingSearchService`(이 장) | Tool을 골라 검색하고 답변 | 예 |

기본값으로 등록하면 모든 호출에 Tool의 이름·설명·JSON 스키마가 따라갑니다. 입력 토큰과 지연이 늘고, 조건만 뽑으면 되는 호출에서 LLM이 검색 Tool을 불러 API를 헛되이 호출하며, 작은 모델은 Structured Output(JSON) 대신 Tool 호출로 답해 파싱에 실패하기도 합니다. 그래서 Tool은 필요한 요청에서만 `.tools(...)`로 넘깁니다.

## 8.6 실행 기록에 TOOL 단계 남기기

`call().content()`는 답변 문자열만 줍니다. 그 사이에 무엇이 실행됐는지는 5장의 `ToolResultCapture.capture(...)`가 Tool마다 남긴 기록으로 압니다. 8.2에서 `bind(trace)`로 실행 기록을 연결해 두었으므로, `capture()`는 기록을 쌓는 김에 7장의 `TraceStep` 형식으로 TOOL 단계도 바로 남깁니다.

`src/main/java/net/java21/flyschedule/mcp/ToolResultCapture.java`

```java
public static void bind(ExecutionTrace trace) {
    LIVE_TRACE.set(trace);
}

private static void add(ToolCall call) {
    CALLS.get().add(call);
    ExecutionTrace trace = LIVE_TRACE.get();
    if (trace != null) {
        trace.record(TraceStep.Kind.TOOL, call.toolName(),
                call.failed() ? "Tool 실행 실패" : "LLM이 요청한 Tool 실행",
                call.arguments() + " → " + (call.failed() ? "실패: " + call.error() : summarize(call.result())),
                call.startNanos(), call.endNanos());
    }
}
```

코드 읽기

1. `LIVE_TRACE`: `bind()`로 연결한 실행 기록을 담는 `ThreadLocal`입니다. Tool은 `call()`을 부른 스레드에서 실행되므로(8.3) 같은 스레드에서 이 값을 꺼낼 수 있습니다. 연결된 기록이 없으면(예: `McpController`로 Tool을 직접 부를 때) TOOL 단계를 남기지 않습니다.
2. 기록 시점: TOOL 단계는 Tool이 끝나는 **그 순간** 남습니다. 그래서 작업 API로 진행 상황을 조회하는 화면에서는 LLM이 아직 답을 쓰는 중에도 TOOL 단계가 먼저 나타납니다.
3. 시각: 넘기는 시각은 `capture()`가 잰 `startNanos`·`endNanos`입니다. TOOL 막대는 실제로 실행된 위치와 길이로 그려지고, 나중에 기록되는 LLM 막대가 그 바깥을 감쌉니다.
4. `detail`: "인자 → 결과 요약"입니다. `summarize()`는 항공사별 `Map`이면 "5개 항공사, 14편", `List`면 "N건"처럼 짧게 줄입니다. Tool이 예외를 던졌으면 제목이 "Tool 실행 실패", `detail`이 "인자 → 실패: 메시지"가 됩니다.

화면 아래의 항공편 표는 LLM이 쓴 문장이 아니라 Tool의 실제 반환값입니다.

`src/main/java/net/java21/flyschedule/service/toolcalling/ToolCallingSearchService.java`

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

뒤에서부터 찾아 **마지막** 항공편 검색 결과(`Map`)를 씁니다. LLM이 넓게 검색한 뒤 시간 조건으로 다시 좁혔다면 마지막 결과가 의도에 가장 가깝기 때문입니다. 공항 코드(`String`), 공항 목록(`List`), 실패한 호출(`null`)은 건너뜁니다. LLM의 답변 문장이 틀려도 표는 공공데이터 포털 데이터 그대로입니다.

> **직접 확인하기**
> 앱을 실행하고(`./mvnw spring-boot:run`) 동기 엔드포인트로 요청합니다.
> ```bash
> curl -X POST http://localhost:8080/api/search/tool-calling \
>   -H "Content-Type: application/json" \
>   -d '{"message": "모레 김포에서 제주 오후 2시 이후 항공편", "model": "gemini"}'
> ```
> 기대 결과: `"pattern": "tool-calling"`, `trace`에는 `"kind": "TOOL"` 단계가 먼저 나오고, 마지막 단계가 `"kind": "LLM"`, `"title": "Tool 선택과 답변 작성"`입니다. 각 TOOL 단계의 `startMs`는 LLM 단계의 `startMs`보다 크거나 같고, 끝나는 시각(`startMs + durationMs`)은 LLM 단계 안에 있습니다. 화면(`http://localhost:8080/`)에서 같은 문장을 Tool Calling 패턴으로 실행하면 TOOL 막대가 LLM 막대 안쪽에 그려집니다.

화면은 동기 엔드포인트 대신 작업(job) API(`POST /api/search/tool-calling/jobs` → `GET /api/search/jobs/{jobId}`)를 1.5초 간격으로 조회합니다. LLM 호출이 오래 걸려도 프록시 타임아웃에 걸리지 않게 하기 위해서입니다. 자세한 내용은 [10장](10-choosing-testing-operating.md)에서 다룹니다. TOOL 단계는 실행되는 즉시 기록되므로, 화면에서는 LLM이 답을 쓰는 동안에도 TOOL 단계가 먼저 나타나고 LLM 단계는 마지막에 붙습니다. 9장의 Orchestrator와 비교해 보세요.

## 8.7 실패 사례와 대응

Tool Calling Agent의 유연함은 곧 불확실함입니다. 특히 `qwen2.5:3b-instruct-q4_K_M` 같은 작은 로컬 모델에서 다음 실패가 자주 보입니다. 실패를 알아보는 곳은 실행 기록의 TOOL 단계입니다.

| 실패 | 실행 기록에서 보이는 모습 | 이 프로젝트의 처리 | 더 할 수 있는 대응 |
|---|---|---|---|
| LLM이 Tool을 부르지 않음 | LLM 단계 하나, "Tool 0회 호출 후 답변" | `success: false`, LLM의 말을 그대로 보여 줌 | 프롬프트의 "반드시 Tool을 호출" 규칙 강조, 더 큰 모델 |
| 엉뚱한 Tool을 고름("오후 2시 이후"인데 `searchFlightsByAirline`) | TOOL 단계의 이름이 의도와 다름 | 결과는 그대로 보여 줌 | Tool 설명에 "언제 쓰는지"를 명확히, 넘기는 Tool 수 줄이기 |
| 엉뚱한 인자(날짜를 `"이번 주 금요일"` 그대로, 공항 자리에 `NAARKJJ`) | "Tool 실행 실패", `detail`에 형식 오류 메시지 | 예외 메시지가 Tool 결과로 LLM에게 돌아가고, LLM이 고쳐서 다시 부르기도 함 | Tool 안의 검증 메시지를 "어떻게 고치라"는 문장으로 |
| 없는 공항("서울에서 제주") | 검색 Tool이 "알 수 없는 공항입니다: 서울"로 실패 → 다른 공항으로 재시도 | 실패한 호출도 TOOL 단계로 남음 | 시스템 프롬프트에 지원 공항 목록 |
| 날짜를 잘못 셈("이번 주 토요일"을 하루 늦게) | TOOL 단계 `detail`의 날짜가 기대와 다름 | 날짜표를 넣었지만 그래도 틀려, 사용자 문장에 코드가 계산한 날짜를 붙여 보냄(`DateContext.annotate`) | 코드가 계산할 수 있는 표현을 늘리기 |
| Gemini에서 Tool 실행 직후 `Failed to parse JSON` | TOOL 단계는 성공, LLM 단계가 "실패: ..." | Tool 결과를 `{"result": ...}` JSON으로 감쌈(`CompactFlightResultConverter`, 5장) | 제공자를 바꿀 때 Tool 결과 형식 점검 |
| 외부 API 오류(`ExternalApiException`) | Tool 실행 실패, 공공데이터 포털 오류 메시지 | Tool 결과로 LLM에게 전달, 밖으로 나오면 `describeError`가 메시지를 그대로 보여 줌 | 서비스 키(`DATA_GO_KR_SERVICE_KEY`) 확인 |
| LLM 연결 실패 | 그때까지 실행된 TOOL 단계 뒤에 "실패: ..."로 끝난 LLM 단계 | "LLM 서버에 연결할 수 없습니다..." 안내. 재시도는 `spring.ai.retry.max-attempts: 2`로 줄여 둠 | 다른 모델 선택 |
| 응답이 너무 늦음 | 전체 시간이 수십 초 | 화면은 작업 API로 프록시 타임아웃을 피함 | 넘기는 Tool 수, Tool 결과, 답변 길이 줄이기(8.9) |

"서울에서 제주" 같은 실패-재시도 흐름은 테스트로도 확인할 수 있습니다.

> **직접 확인하기**
> ```bash
> ./mvnw test -Dtest=ToolCallingSearchServiceTest#failedToolIsRecorded
> ```
> 기대 결과: 테스트가 통과합니다. 이 테스트는 `getAirportCode("서울")`이 실패한 뒤 김포로 다시 검색한 상황에서 실행 기록 제목이 "Tool 실행 실패 → LLM이 요청한 Tool 실행 → Tool 선택과 답변 작성" 순서로 남는지 검사합니다.

대응 방법을 정리하면, 결국 LLM이 보는 정보를 **분명하게** 하는 일입니다. Tool 설명, `@ToolParam` 설명, system 프롬프트 규칙, Tool이 돌려주는 오류 메시지가 모두 LLM의 판단 재료입니다. 그래도 순서가 늘 같은 일이라면, 애초에 LLM에게 맡기지 않는 것이 가장 확실한 대응입니다(7장).

## 8.8 결정성, 지연, 비용

| | Coordinator 패턴(정규식 경로) | Tool Calling Agent |
|---|---|---|
| 코드에서 보이는 LLM 호출 | 0회 | 최소 1회(`call()`) |
| 실제 모델 왕복 | 0회 | Tool을 부르면 2회 이상 |
| 실측 지연 | 약 0.6초(대부분 공공데이터 API 대기) | 모델에 따라 수 초 ~ 수십 초(개선 전 Ollama 실측 약 99초, 8.9) |
| 같은 입력에 같은 결과 | 예 | 보장되지 않음 |

Ollama처럼 `temperature: 0.0`으로 두어도 Tool 선택이 늘 같다고 보장되지는 않습니다. 모델 버전이 바뀌거나 Tool 설명 한 줄만 바뀌어도 고르는 Tool이 달라질 수 있습니다. 원격 Ollama의 3B 모델은 호출 한 번에 수십 초가 걸리기도 하는데, 왕복이 두세 번 쌓이면 1분을 넘기기 쉽습니다. 실제로 이 앱은 동기 요청이 nginx의 기본 60초 제한에 걸려 504 오류를 낸 적이 있고, 그래서 화면이 작업 API를 쓰게 되었습니다.

데모 사이트(https://fly.java21.net)에서 Ollama `qwen2.5:3b` 모델로 "광주에서 제주" 검색을 실행했을 때 남은 실제 실행 기록입니다. 8.9의 개선을 적용하기 **전**의 기록입니다.

| 순서 | kind | actor | detail | 시작 | 걸린 시간 |
|---|---|---|---|---|---|
| 1 | TOOL | searchFlightsByAirline | 광주 → 제주, 2026-10-17 → 5개 항공사, 14편 | 약 36.4초 | 약 84ms |
| 2 | LLM | ollama | Tool 1회 호출 후 답변 | 0초 | 99,362ms |

표 읽기: 전체 약 99.4초 가운데 Tool(공공데이터 포털 호출 포함)은 0.1초도 걸리지 않았습니다. LLM이 Tool을 고르는 데 약 36초, Tool 결과를 받아 답을 쓰는 데 약 63초가 걸렸습니다. 지연의 거의 전부가 모델 왕복입니다. 이 요청을 동기 엔드포인트로 보냈다면 nginx의 60초 제한을 넘겨 504로 끝났을 것입니다. 이 숫자를 어떻게 줄였는지 다음 절에서 봅니다.

> **생각해 보기**
> 같은 문장을 다섯 번 보냈을 때 TOOL 단계 수가 매번 다르다면, 이 결과를 운영 서비스에서 그대로 써도 될까요? "어떤 요청이라면 괜찮은가"를 기준으로 답해 보세요.

## 8.9 측정하고 개선하기

99초는 작업 API 덕분에 화면이 끊기지는 않지만, 사용자가 기다리기에는 너무 깁니다. 무엇을 줄여야 할지 정하려면 먼저 시간이 **어디에** 쓰이는지 알아야 합니다. 이 앱은 실행 기록에 각 단계의 시작 시각과 길이를 남기므로, 감으로 고치지 않고 숫자를 보고 고칠 수 있습니다.

### CPU에서 시간이 쓰이는 곳

데모 사이트의 Ollama 서버는 GPU 없이 CPU로 3B 모델을 돌립니다. CPU에서 LLM 호출 한 번의 시간은 크게 두 부분입니다.

- **입력 토큰 읽기**: system 프롬프트, 사용자 문장, Tool 정의, (두 번째 왕복이면) Tool 결과까지 모두 읽어야 첫 토큰을 만들 수 있습니다. 입력이 길수록 오래 걸립니다.
- **출력 토큰 만들기**: 답을 한 토큰씩 만듭니다([1장](01-llm-and-spring-ai.md)). 답이 길수록 오래 걸립니다.

Tool Calling의 `call()` 한 번에는 왕복이 두 번 있습니다(8.3). 개선 전 기록에 대입하면 다음과 같습니다.

| 구간 | 입력(읽기) | 출력(쓰기) | 개선 전 |
|---|---|---|---|
| 왕복 1: Tool 선택 | system 프롬프트 + 질문 + **Tool 정의 7개** | Tool 호출 요청(짧은 JSON) | 약 36.4초 |
| Tool 실행 | - | - | 약 0.08초 |
| 왕복 2: 답변 작성 | 왕복 1의 입력 전체 + **Tool 결과 JSON(필드 9개짜리 항공편 14편)** | **3~4문장 답변** | 약 63초 |

굵게 쓴 부분이 줄일 수 있는 부분입니다. Tool 실행은 0.1초도 안 되므로 여기를 최적화해도 소용이 없습니다.

### 손잡이 여섯 개

지연을 줄이는 손잡이는 여섯 가지입니다. 앞의 다섯은 이 앱에 실제로 적용했습니다.

| 손잡이 | 이 앱에서 한 일 | 줄어드는 것 | 다룬 곳 |
|---|---|---|---|
| 넘기는 Tool 수 | 7개 → 3개(`.tools(flightSearchTool)`) | 두 왕복의 입력 | [5장](05-domain-tools.md), 8.2 |
| Tool 결과 크기 | `CompactFlightResultConverter`로 한 줄 요약 | 왕복 2의 입력 | [5장](05-domain-tools.md) |
| 답변 길이 | "2~3문장으로 짧게", "목록을 나열하지 마세요" | 왕복 2의 출력 | 8.4 |
| 출력 상한 | Ollama `max-tokens: 512` | 최악의 경우 출력 | [1장](01-llm-and-spring-ai.md) |
| 추론 단계 | Gemini `thinking-level: minimal` | 답하기 전 추론 시간 | [1장](01-llm-and-spring-ai.md) |
| 모델 선택 | 화면에서 Ollama와 Gemini를 고름 | 전부 | [1장](01-llm-and-spring-ai.md) |

### 개선 뒤의 실측

같은 데모 사이트에서 개선 뒤 작업 API의 실행 기록으로 잰 숫자입니다. Ollama는 여러 번 실행한 범위입니다.

| 패턴 / 모델 | 전체 | 구간별 |
|---|---|---|
| Tool Calling / Ollama (개선 전) | 99.4초 | Tool 선택 36.4초, 답변 작성 약 63초 |
| Tool Calling / Ollama (개선 후) | 23.5~25.7초 | Tool 선택 약 14.6초, 답변 작성 약 9~11초 |
| Tool Calling / Gemini 3.5 Flash-Lite | 2.7초 | Tool 선택 1.5초, 답변 작성 약 1.2초 |
| Orchestrator / Gemini 3.5 Flash-Lite | 3.9초 | 계획 2.5초, Worker 4개 병렬 약 0.1초, 종합 1.3초 |
| Orchestrator / Ollama | 31.5초 | 계획 22.1초, 종합 9.3초 |

```mermaid
flowchart LR
    subgraph BEFORE["개선 전 Ollama 99.4초"]
        B1["왕복 1 Tool 선택<br/>36.4초"]:::llm --> B2["Tool 실행<br/>0.1초 미만"]:::tool --> B3["왕복 2 답변 작성<br/>약 63초"]:::llm
    end
    subgraph AFTER["개선 후 Ollama 약 24초"]
        A1["왕복 1 Tool 선택<br/>약 14.6초"]:::llm --> A2["Tool 실행<br/>0.1초 안팎"]:::tool --> A3["왕복 2 답변 작성<br/>약 9~11초"]:::llm
    end
    classDef code fill:#eef0f3,stroke:#5b6472,color:#1d2129
    classDef llm fill:#f1ebfd,stroke:#7c4ddb,color:#1d2129
    classDef tool fill:#fdf0e3,stroke:#d9822b,color:#1d2129
    classDef worker fill:#e6f6f0,stroke:#1f9d74,color:#1d2129
```

그림 읽기: 보라색(LLM) 두 칸이 모두 줄었습니다. 왕복 1은 Tool 정의가 줄어 약 36초에서 15초 가까이로, 왕복 2는 Tool 결과와 답변이 함께 줄어 약 63초에서 10초 안팎으로 줄었습니다. 주황색 Tool 칸은 처음부터 작았습니다.

숫자에서 읽을 수 있는 것은 세 가지입니다.

- **CPU 모델에서는 입력도 비용입니다.** 왕복 1의 출력(Tool 호출 요청)은 개선 전후가 거의 같은데도 시간이 절반 아래로 줄었습니다. 줄어든 것은 읽어야 할 Tool 정의입니다.
- **모델 선택이 가장 큰 손잡이입니다.** 같은 코드, 같은 Tool에서 Gemini는 2.7초, Ollama는 약 24초입니다. 그래도 나머지 손잡이는 어느 모델에서나 입력과 출력을 줄이므로 비용과 지연에 도움이 됩니다.
- **Orchestrator의 Worker는 거의 공짜입니다.** 검색 4개를 병렬로 돌리는 데 약 0.1초입니다. 시간은 LLM이 일하는 계획과 종합에 쓰입니다. Ollama에서 계획이 22.1초로 가장 깁니다. Structured Output의 JSON 스키마를 읽고 작업 여러 개짜리 JSON을 써야 하기 때문입니다([9장](09-orchestrator-workers.md)).

개선 후에도 Ollama는 20~30초가 걸립니다. 동기 요청이었다면 여전히 불안한 숫자입니다. **작업 API**([10장](10-choosing-testing-operating.md))가 있어서 이런 긴 호출도 화면에서 쓸 수 있고, 진행 중에도 TOOL 단계가 먼저 보여 사용자가 무작정 기다리지 않습니다.

> **직접 확인하기**
> 앱을 실행하고 작업 API로 같은 문장을 두 모델로 보냅니다.
> ```bash
> curl -s -X POST http://localhost:8080/api/search/tool-calling/jobs \
>   -H "Content-Type: application/json" \
>   -d '{"message": "내일 광주에서 제주", "model": "ollama"}'
> curl -s http://localhost:8080/api/search/jobs/{jobId}
> ```
> 기대 결과: `"status":"DONE"`이 되면 `trace`에서 TOOL 단계의 `startMs`(Tool 선택까지 걸린 시간)와 LLM 단계의 `durationMs`(전체)를 읽어 위 표처럼 정리합니다. `"model": "gemini"`(키 필요)로도 반복합니다. 숫자는 서버 사양과 부하에 따라 다르지만, 두 모델의 차이와 구간별 비율은 비슷하게 나옵니다.

> **생각해 보기**
> 왕복 1(Tool 선택)을 더 줄이려면 무엇을 바꿀 수 있을까요? Tool 설명 문장, system 프롬프트의 날짜표, `@ToolParam` 설명은 모두 입력 토큰입니다. 무엇을 줄이면 속도는 빨라지지만 Tool 선택의 정확도가 떨어질 수 있는지도 함께 생각해 보세요.

## 8.10 가짜 LLM으로 테스트하기

실제 LLM으로 테스트하면 느리고, 결과가 매번 다르고, 키가 필요합니다. 그래서 `ToolCallingSearchServiceTest`는 LLM을 흉내 냅니다. 핵심은 **`call().content()`가 불릴 때, LLM이 요청한 것처럼 Tool 메서드를 직접 실행하는 것**입니다.

`src/test/java/net/java21/flyschedule/service/toolcalling/ToolCallingSearchServiceTest.java`

```java
ChatClient chatClient = mock(ChatClient.class);
requestSpec = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
callSpec = mock(ChatClient.CallResponseSpec.class);
when(chatClient.prompt()).thenReturn(requestSpec);
when(requestSpec.call()).thenReturn(callSpec);
// ...

/** LLM이 Tool을 호출한 뒤 answer를 답한 것처럼 동작하게 한다 */
private void llmCallsToolsThenAnswers(Supplier<String> llmTurn) {
    when(callSpec.content()).thenAnswer(invocation -> llmTurn.get());
}

@Test
@DisplayName("Tool은 실행되는 즉시 TOOL 단계로 기록되고, 그 전체를 감싸는 LLM 구간이 마지막에 기록된다")
void recordsToolCalls() {
    // ...
    llmCallsToolsThenAnswers(() -> {
        airportInfoTool.getAirportCode("광주");
        flightSearchTool.searchFlightsByAirline("광주", "제주", "2026-03-25");
        return "제주항공 7C1402편이 45,000원으로 가장 저렴합니다.";
    });

    SearchResponse response = service.search("3월 25일 광주에서 제주", "gemini");
    // ...
    assertThat(response.trace()).extracting(TraceStep::kind)
            .containsExactly(Kind.TOOL, Kind.TOOL, Kind.LLM);
```

코드 읽기

1. `RETURNS_SELF`: `.system(...)`, `.user(...)`, `.tools(...)`처럼 자기 자신을 돌려주는 체인 메서드가 모두 같은 가짜 객체를 돌려줍니다. 체인을 한 줄씩 정해 둘 필요가 없습니다.
2. `thenAnswer(...)`: `content()`가 불리는 **순간** 람다가 실행됩니다. 람다 안에서 진짜 `FlightSearchTool`·`AirportInfoTool` 메서드를 부르므로, 실제 `call()`처럼 같은 스레드에서 `ToolResultCapture`에 기록이 쌓입니다.
3. Tool 안쪽(`FlightSearchAgent`, `AirportListAgent`)만 Mockito 가짜라서 공공데이터 포털도 부르지 않습니다.

LLM의 판단(어떤 Tool을 부를지)은 테스트가 정해 두고, **그 판단을 받은 뒤의 코드**(기록, 결과 선택, 실패 처리)를 검증하는 방식입니다. 같은 테스트 클래스에는 Tool 0회(`noToolCall`), 마지막 결과 사용(`usesLastFlightResult`), 요청마다 Tool 전달과 오늘 날짜(`passesToolsPerRequest`), LLM 연결 실패(`llmFailure`), LLM이 답을 쓰기 전에 TOOL 단계가 이미 기록되는지(`toolStepsAppearWhileLlmIsRunning`), 이전 요청 기록 정리(`clearsLeftovers`) 테스트도 있습니다. 가짜 LLM 기법 전체는 10장에서 한 번 더 정리합니다.

> **직접 확인하기**
> ```bash
> ./mvnw test -Dtest=ToolCallingSearchServiceTest
> ```
> 기대 결과: 모든 테스트가 통과합니다. LLM 서버나 API 키가 없어도 됩니다.

## 핵심 정리

- Tool Calling Agent는 어떤 Tool을, 어떤 인자로, 몇 번 부를지를 LLM이 정합니다. 코드에는 분기 대신 `.tools(...)`와 system 프롬프트가 남습니다.
- `call()` 한 번 안에서 모델과 2회 이상 오가며, Tool은 같은 스레드에서 실행됩니다. 실행 기록에는 Tool이 실행되는 즉시 TOOL 단계가 남고, 그 구간 전체를 감싸는 LLM 단계 하나가 마지막에 남습니다.
- 사용자 문장에는 코드가 계산한 날짜를 붙여 보내고(`DateContext.annotate`), system 프롬프트에는 날짜표, 짧은 규칙 목록, "Tool 결과에 없는 내용은 지어내지 마라"는 근거 제한, 짧은 답변 규칙을 넣습니다. Tool 0회 응답은 실패로 봅니다.
- Tool은 `defaultTools`가 아니라 필요한 요청에서, 필요한 것(검색 Tool 3개)만 넘깁니다.
- 지연은 실행 기록으로 재고 고칩니다. CPU 모델에서는 입력 토큰과 출력 토큰이 모두 시간입니다. Tool 수·Tool 결과·답변 길이를 줄여 Ollama Tool Calling을 99.4초에서 약 24초로 줄였습니다.
- 유연한 대신 결정성이 낮고, 느리고, 비쌉니다. 실패는 TOOL 단계에서 찾고, LLM이 보는 정보를 분명하게 해서 줄입니다.

## 연습 문제

**1. [기본] system 프롬프트의 날짜표 검사하기**
system 프롬프트에는 `DateContext.describe()`가 만든 날짜표가 들어갑니다. 그런데 지금 테스트는 오늘 날짜만 검사합니다. 이번 주·다음 주 줄도 검사해 보세요.
- **힌트**: `ToolCallingSearchService.systemPrompt()`, `ToolCallingSearchServiceTest.passesToolsPerRequest`의 `verify(requestSpec).system(contains(...))`. 기대 문자열은 `DateContext.describe()`를 그대로 쓰거나, 그 안의 `"이번 주: "` 줄을 씁니다.
- **완료 기준**: 날짜표가 프롬프트에 들어갔는지 검사하는 테스트를 추가하고 `./mvnw test`가 통과합니다.

**2. [기본] Tool을 다시 늘려 보기**
8.9에서 Tool을 7개에서 3개로 줄였습니다. 거꾸로 `.tools(flightSearchTool, airportInfoTool, airlineInfoTool)`로 7개를 다시 넘기고 시간을 비교해 보세요. 실험 뒤 원래대로 되돌립니다.
- **힌트**: `ToolCallingSearchService` 생성자에 `AirportInfoTool`, `AirlineInfoTool`을 추가하고, 테스트의 `verify(requestSpec).tools(flightSearchTool)`와 생성자 호출도 맞춥니다.
- **완료 기준**: 화면이나 작업 API로 같은 문장을 Ollama로 세 번씩 실행해, Tool 3개와 7개일 때의 TOOL 단계 `startMs`와 전체 시간을 표로 적습니다. 되돌린 뒤 `./mvnw test -Dtest=ToolCallingSearchServiceTest`가 통과합니다.

**3. [도전] Tool을 부르지 않은 이유 구분하기**
지금은 Tool 0회면 LLM의 답을 그대로 실패 메시지로 씁니다. 답이 질문(되묻기)인지, 항공편을 지어낸 것인지 구분해 다른 안내를 보여 주세요.
- **힌트**: `search()`의 `calls.isEmpty()` 분기. 예를 들어 답에 `?`가 있으면 되묻기, 편명 패턴(`[A-Z0-9]{2}\d{3,4}`)이 있으면 지어낸 답으로 봅니다.
- **완료 기준**: `noToolCall` 테스트 옆에 "지어낸 편명이 있는 답" 테스트를 추가해, 응답 메시지가 LLM의 답이 아니라 "Tool 결과 없이 만든 답이라 보여 주지 않습니다" 같은 안내인지 검증합니다.

**4. [도전] Tool 결과 요약을 더 알차게**
TOOL 단계의 `detail`은 "5개 항공사, 14편"까지만 보여 줍니다. 여기에 최저가("최저 45,000원")를 덧붙여 보세요.
- **힌트**: `ToolResultCapture.summarize(Object)`. `Map`의 값은 `List<FlightInfoResponse>`이고 일반석 가격은 `getEconomyCharge()`입니다(`null`일 수 있음). 결과가 비었을 때도 생각합니다.
- **완료 기준**: `ToolResultCaptureTest`에 `summarize` 테스트를 추가해 "1개 항공사, 2편, 최저 45,000원" 같은 문자열을 검증하고, `ToolCallingSearchServiceTest`가 그대로 통과합니다.

LLM에게 모든 단계를 맡기면 유연하지만 느리고 불안정했습니다. 다음 장에서는 LLM에게 "나누기"와 "종합하기"만 맡기고 실행은 코드에 돌려주는 [9장 Orchestrator-Workers 패턴](09-orchestrator-workers.md)을 만듭니다.
