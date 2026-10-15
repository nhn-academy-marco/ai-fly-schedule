# Step 11: Orchestrator-Workers 패턴 — LLM이 작업을 나누고 종합하기

## 학습 목표

이 단계를 마치면 다음을 할 수 있습니다.

1. **Orchestrator-Workers 패턴**이 무엇인지, 어떤 요청에 필요한지 설명할 수 있다
2. Structured Output(`.entity(SearchPlan.class)`)으로 LLM에게 **작업 계획**을 받고, 그 계획을 **코드로 검증**할 수 있다
3. Step 9의 Coordinator 검색 단계를 **Worker로 재사용**해 패턴을 조합할 수 있다
4. **가상 스레드**와 `CompletableFuture`로 Worker를 병렬 실행하고, 실행 기록에서 병렬성을 확인할 수 있다
5. 세 가지 패턴(Coordinator, Tool Calling 에이전트, Orchestrator-Workers)과 A2A 프로토콜을 비교해 **상황에 맞는 패턴을 고를 수 있다**

---

## 전체 흐름

```text
사용자: "이번 주 금요일이나 토요일에 김포나 광주에서 제주 가는 제일 싼 항공편 찾아줘"
  ↓
[SearchController]  POST /api/search/orchestrator
  ↓
[FlightSearchOrchestrator.orchestrate()]
  ↓
① 계획 (LLM)   [FlightSearchPlanner]  요청 → SearchPlan(작업 4개) → validate()
  ↓
② 실행 (Worker, 가상 스레드 병렬)
     [FlightSearchWorker] 김포 → 제주 · 2026-10-16  → FlightSearchCoordinator.search()
     [FlightSearchWorker] 김포 → 제주 · 2026-10-17  → FlightSearchCoordinator.search()
     [FlightSearchWorker] 광주 → 제주 · 2026-10-16  → FlightSearchCoordinator.search()
     [FlightSearchWorker] 광주 → 제주 · 2026-10-17  → FlightSearchCoordinator.search()
  ↓ 모든 Worker가 끝날 때까지 기다림
③ 종합 (LLM)   [ResultSynthesizer]  작업별 요약 4줄 → 비교·추천 답변
  ↓
SearchResponse { pattern: "orchestrator", message: 추천 답변, results: [작업 4개], trace }
```

> 💡 **핵심 개념**: Orchestrator-Workers 패턴에서 LLM은 **무엇을 할지(계획)**와 **결과가 무슨 뜻인지(종합)**만 결정합니다.
> 실제 검색은 Worker가 코드로 병렬 실행합니다. 하위 작업의 **개수와 내용이 미리 정해져 있지 않다**는 점이 Coordinator 패턴과 가장 큰 차이입니다.

---

## Orchestrator-Workers 패턴이란?

### 정의

Anthropic의 글 "Building effective agents"는 워크플로 패턴의 하나로 **orchestrator-workers**를 소개합니다.

> 중앙의 LLM이 작업을 동적으로 나누어 Worker에게 맡기고, 그 결과를 종합한다.
> 필요한 하위 작업을 **미리 예측할 수 없을 때** 적합하다. 병렬화(parallelization)와 비슷해 보이지만,
> 하위 작업이 미리 정해져 있지 않고 **입력에 따라 Orchestrator가 결정한다**는 점이 다르다.

| 역할 | 하는 일 | 이 프로젝트 |
|---|---|---|
| Orchestrator(계획) | 요청을 하위 작업 N개로 나눈다. N은 요청을 보고 LLM이 정한다 | `FlightSearchPlanner` (LLM) |
| Worker | 하위 작업 하나를 처리한다 | `FlightSearchWorker` (코드, 가상 스레드) |
| Orchestrator(종합) | Worker 결과를 모아 최종 답을 만든다 | `ResultSynthesizer` (LLM) |
| 전체 지휘 | 계획 → 병렬 실행 → 종합 순서를 이어 준다 | `FlightSearchOrchestrator` |

> ⚠️ 용어 주의: Step 9의 **Coordinator**는 "코드가 순서를 정하는 순차 워크플로"이고, 이번 단계의 **Orchestrator**는 "LLM이 작업을 동적으로 나누는 지휘자"입니다. 이 교재에서는 두 말을 섞어 쓰지 않습니다.

### 왜 이 요청에는 Orchestrator-Workers가 필요한가

"금요일이나 토요일에 김포나 광주에서 제주"는 날짜 후보 2개 × 출발지 후보 2개 = **검색 4번**이 필요한 요청입니다.

**Coordinator 패턴으로 처리하면**

```text
[SearchCriteriaExtractor]
  정규식: 공항 이름이 3개(김포, 광주, 제주) → "정확히 2개"가 아니므로 포기
  LLM:   .entity(SearchCriteria.class) → SearchCriteria는 출발지·날짜가 하나씩뿐
         → 예: 김포 → 제주 · 2026-10-16 하나만 남고 나머지 3개 조합은 조용히 사라짐
  ↓
[DateParserAgent] → [AirportCodeAgent] → ... 검색 1번
```

Coordinator의 처리 순서는 "조건 하나 → 검색 하나"로 **코드에 고정**되어 있습니다. 조건이 몇 개로 나뉠지 모르는 요청은 표현할 수 없습니다.

**Tool Calling 에이전트로 처리하면** LLM이 `searchFlightsByAirline`을 네 번 부를 수도 있습니다. 하지만

- 네 번을 모두 부를지는 모델에 달려 있고(작은 모델은 한두 번 부르고 답하기 쉽습니다),
- Tool 호출은 LLM 왕복마다 **순차로** 일어나므로 검색 4번 + LLM 왕복 5번이 줄지어 기다리며,
- 화면에는 마지막 검색 결과 하나만 남습니다(`lastFlightResult`).

**Orchestrator-Workers로 처리하면** LLM은 계획에서 "작업 4개"를 정하고, 코드가 4개를 **동시에** 실행하고, LLM이 4개 결과를 **한 번에** 비교합니다. LLM 호출은 작업 수와 관계없이 2번입니다.

### 언제 쓰는가

| 적합한 경우 | 적합하지 않은 경우 |
|---|---|
| 하위 작업의 개수·내용이 입력마다 달라진다 | 처리 순서가 늘 같다 → Coordinator |
| 하위 작업끼리 서로 독립적이라 병렬로 돌릴 수 있다 | 앞 작업 결과를 보고 다음 작업을 정해야 한다 → Tool Calling 에이전트 |
| 여러 결과를 비교·종합해야 한다 | 결과가 하나뿐이고 요약이 필요 없다 |

---

## Step 11-1: FlightSearchPlanner — LLM에게 계획 받기

### SearchPlan: 계획의 형태

`src/main/java/net/java21/flyschedule/service/orchestrator/SearchPlan.java`

```java
/**
 * Orchestrator(LLM)가 만든 작업 계획 (Structured Output 대상)
 */
public record SearchPlan(
        @JsonPropertyDescription("요청을 이렇게 나눈 이유를 한 문장으로")
        String reasoning,

        @JsonPropertyDescription("Worker에게 맡길 검색 작업 목록. 검색 조건이 하나면 1개")
        List<SearchCriteria> tasks
) {
}
```

작업 하나는 Step 9에서 만든 `SearchCriteria`(출발, 도착, 날짜, 시간·가격 조건) 그대로입니다. 그래서 Worker가 Coordinator의 검색 단계를 바로 재사용할 수 있습니다.
`reasoning`은 결과에는 쓰지 않지만, LLM이 **먼저 이유를 적고 나서 목록을 만들게** 해 작업 분해 품질을 높이는 효과가 있습니다.

### 계획 요청

`src/main/java/net/java21/flyschedule/service/orchestrator/FlightSearchPlanner.java`

```java
public SearchPlan plan(String message, ChatClient chatClient) {
    SearchPlan plan = chatClient.prompt()
            .system("""
                    당신은 국내선 항공편 검색 작업을 나누는 Orchestrator입니다.
                    사용자 요청에 출발지, 도착지, 날짜 후보가 여러 개 있으면 조합마다 검색 작업을 하나씩 만드세요.
                    조건이 하나뿐이면 작업도 하나입니다. 작업은 최대 %d개까지만 만드세요.
                    오늘 날짜는 %s(%s)입니다. "이번 주 금요일" 같은 표현은 YYYY-MM-DD로 바꾸세요.
                    공항은 한글 이름(광주, 김포, 제주 등)으로 적고, 없는 조건은 null로 둡니다.
                    """.formatted(MAX_TASKS, LocalDate.now(), LocalDate.now().getDayOfWeek()))
            .user(message)
            .call()
            .entity(SearchPlan.class);

    return validate(plan);
}
```

| 프롬프트 요소 | 이유 |
|---|---|
| "조합마다 검색 작업을 하나씩" | 2 × 2 = 4처럼 **곱으로 나누는 규칙**을 명시합니다 |
| "조건이 하나뿐이면 작업도 하나" | 단순한 요청을 불필요하게 쪼개지 않게 합니다 |
| `최대 %d개` | 프롬프트로 한 번, `validate()`에서 코드로 한 번 더 제한합니다 |
| 오늘 날짜와 **요일**(`THURSDAY` 등) | "이번 주 금요일"을 계산하려면 오늘이 무슨 요일인지 알아야 합니다 |
| 한글 공항 이름, 없으면 `null` | `AirportCodeAgent`가 변환할 수 있는 형태로 받습니다 |

`.entity(SearchPlan.class)`는 Spring AI의 Structured Output입니다. 레코드와 `@JsonPropertyDescription`으로 JSON 스키마를 만들어 프롬프트에 붙이고, LLM의 JSON 응답을 `SearchPlan`으로 바꿔 줍니다.
이 호출에는 `.tools(...)`가 없습니다. `ChatClientConfig`에서 `defaultTools`를 등록하지 않았기 때문에 계획 LLM이 Tool을 부를 일이 없습니다(Step 10 참고).

### validate(): LLM이 만든 계획을 그대로 믿지 않는다

LLM의 출력은 **사용자 입력과 같은 수준으로 검증**해야 합니다. 계획이 잘못되면 그 개수만큼 공공데이터 포털 API를 헛되이 호출하게 됩니다.

```java
SearchPlan validate(SearchPlan plan) {
    if (plan == null || plan.tasks() == null) {
        throw new IllegalArgumentException("검색 계획을 세우지 못했습니다. 출발지와 도착지를 포함해 다시 입력해 주세요.");
    }

    // "광주"와 "광주공항"은 같은 공항이므로, 출발=도착 검사와 중복 제거는 공항 코드로 비교한다
    Set<List<Object>> seen = new HashSet<>();
    List<SearchCriteria> tasks = plan.tasks().stream()
            .filter(SearchCriteria::hasRoute)                                   // ① 출발·도착이 있는가
            .filter(task -> airportCodeAgent.isValidAirport(task.departure())
                    && airportCodeAgent.isValidAirport(task.arrival()))         // ② 아는 공항인가
            .filter(task -> !departureCode(task).equals(arrivalCode(task)))     // ③ 출발 ≠ 도착 (공항 코드로)
            .filter(task -> seen.add(Arrays.asList(departureCode(task), arrivalCode(task), task.date(),
                    task.afterTime(), task.beforeTime(), task.minPrice(), task.maxPrice())))  // ④ 중복 제거 (공항 코드로)
            .limit(MAX_TASKS)                                                   // ⑤ 최대 4개
            .toList();

    if (tasks.size() < plan.tasks().size()) {
        log.warn("계획 검증: 작업 {}개 중 {}개만 사용", plan.tasks().size(), tasks.size());
    }
    if (tasks.isEmpty()) {
        throw new IllegalArgumentException("검색할 수 있는 작업이 없습니다. 지원하는 공항 이름으로 다시 입력해 주세요.");
    }
    return new SearchPlan(plan.reasoning(), tasks);
}

private String departureCode(SearchCriteria task) {
    return airportCodeAgent.getAirportCode(task.departure());
}
// arrivalCode()도 같은 모양 ... 생략
```

| 검증 | LLM이 실제로 저지를 수 있는 실수 |
|---|---|
| ① `hasRoute()` | 출발지를 빠뜨리고 `null`로 둠 |
| ② `isValidAirport()` | "서울", "도쿄"처럼 국내선 공항이 아닌 이름, 또는 `NAARKJJ` 같은 코드를 넣음 |
| ③ 출발 ≠ 도착 | "제주 → 제주", "김해 → 부산"(같은 공항) 같은 의미 없는 작업 |
| ④ 중복 제거 | 같은 조합을 두 번 만듦. "광주 → 제주"와 "광주공항 → 제주국제공항"처럼 **이름만 다른 같은 작업**도 있음 |
| ⑤ `limit(MAX_TASKS)` | 프롬프트의 "최대 4개"를 무시하고 10개를 만듦 → API 호출 폭주 방지 |

**왜 공항 코드로 비교하는가?** `SearchCriteria`는 레코드라 `distinct()`만 써도 필드 값이 똑같은 중복은 걸러집니다. 하지만 LLM은 같은 공항을 "광주", "광주공항"처럼 다르게 적기도 하고, `AirportCodeAgent`는 둘 다 `NAARKJJ`로 바꿉니다. 문자열로 비교하면 같은 검색을 두 번 하게 되므로, 출발·도착은 **공항 코드**로 바꿔 비교하고 나머지 조건은 그대로 비교합니다. `Set.add()`는 처음 보는 값일 때만 `true`를 돌려주므로 "처음 나온 것만 남기기" 필터로 쓸 수 있습니다. (②를 먼저 통과했으므로 `getAirportCode()`가 예외를 던지지 않습니다.)

`validate()`가 `IllegalArgumentException`을 던지면 `FlightSearchOrchestrator`가 받아 `success: false`와 안내 문구로 응답하고, Worker와 종합 단계는 실행하지 않습니다.

> 💡 `validate()`는 패키지 접근 수준이라 테스트에서 LLM 없이 바로 호출할 수 있습니다. 검증 로직을 LLM 호출과 분리해 두면 테스트가 쉬워집니다.

---

## Step 11-2: FlightSearchWorker — Coordinator를 재사용하는 Worker

`src/main/java/net/java21/flyschedule/service/orchestrator/FlightSearchWorker.java`

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class FlightSearchWorker {

    private final FlightSearchCoordinator coordinator;

    public TaskResult execute(SearchCriteria task, ExecutionTrace trace) {
        try {
            return trace.run(Kind.WORKER, "FlightSearchWorker", task.label(),
                    () -> coordinator.search(task, trace),
                    TaskResult::summaryLine);
        } catch (RuntimeException e) {
            // 작업 하나가 실패해도 나머지 작업 결과는 살린다
            log.warn("Worker 실패 ({}): {}", task.label(), e.getMessage());
            return TaskResult.of(task, Map.of());
        }
    }
}
```

### 패턴은 조합된다

Worker는 새 검색 로직을 만들지 않고 Step 9의 `FlightSearchCoordinator.search(criteria, trace)`(2~7단계: 날짜 변환 → 공항 코드 변환 → API 호출 → 시간·가격 필터 → 그룹핑)를 그대로 부릅니다.

```text
[Orchestrator (LLM)]         무엇을 검색할지 결정       ← 판단이 필요한 상위 단계
  ↓ 작업 N개
[Worker]                     작업 하나를 맡음
  ↓
[Coordinator.search (코드)]  정해진 순서로 검색          ← 결과가 늘 같아야 하는 하위 단계
  ↓
[DateParserAgent] → [AirportCodeAgent] → [ApiClientService] → ... → [GroupingAgent]
```

**LLM은 결정이 필요한 위쪽에, 코드는 정확성이 필요한 아래쪽에** 둡니다. 패턴은 서로 배타적인 선택지가 아니라 층층이 쌓을 수 있는 부품입니다.

같은 `trace`를 넘기므로 Coordinator 내부의 CODE 단계(날짜 변환, API 호출 등)도 Worker의 가상 스레드 이름과 함께 실행 기록에 남습니다.

### 실패 격리

`coordinator.search()`가 예외를 던지면(공공데이터 포털 호출 실패를 알리는 `ExternalApiException`, 날짜 형식 오류 등)

1. `trace.run()`이 WORKER 단계를 `"실패: ..."`로 기록하고 예외를 다시 던지고,
2. Worker가 그 예외를 받아 **빈 결과**(`TaskResult.of(task, Map.of())`)를 돌려줍니다.

그래서 4개 중 1개가 실패해도 나머지 3개의 결과로 추천할 수 있습니다. 종합 단계에서는 빈 결과가 `"김포 → 제주 · 2026-10-16: 항공편 없음"`으로 표현됩니다.

---

## Step 11-3: FlightSearchOrchestrator — 가상 스레드로 병렬 실행

### 전체 지휘

`src/main/java/net/java21/flyschedule/service/orchestrator/FlightSearchOrchestrator.java`

```java
public SearchResponse orchestrate(String message, String model) {
    ExecutionTrace trace = new ExecutionTrace();
    String modelName = ChatClientProvider.normalize(model);

    try {
        ChatClient chatClient = chatClientProvider.get(model);

        // 1. 계획: LLM이 요청을 하위 작업으로 나눈다
        SearchPlan plan = trace.run(Kind.LLM, modelName, "작업 계획 (FlightSearchPlanner)",
                () -> planner.plan(message, chatClient),
                p -> p.tasks().size() + "개 작업: " + p.tasks().stream()
                        .map(SearchCriteria::label).collect(Collectors.joining(" / ")));

        // 2. 실행: Worker를 작업마다 가상 스레드에서 병렬로 실행한다
        List<TaskResult> results = runWorkers(plan.tasks(), trace);

        // 3. 종합: LLM이 결과를 비교해 추천한다
        String answer = trace.run(Kind.LLM, modelName, "결과 종합 (ResultSynthesizer)",
                () -> synthesizer.synthesize(message, results, chatClient),
                text -> text.length() + "자 답변");

        return SearchResponse.success(PATTERN, answer, results, trace);

    } catch (IllegalArgumentException e) {
        log.warn("Orchestrator 검색 실패: {}", e.getMessage());
        return SearchResponse.fail(PATTERN, e.getMessage(), trace);
    } catch (Exception e) {
        log.error("Orchestrator 검색 오류", e);
        return SearchResponse.fail(PATTERN, ChatClientProvider.describeError(e), trace);
    }
}
```

계획과 종합 단계에 **같은 `chatClient`**를 씁니다. 화면에서 고른 모델(Ollama 또는 Gemini)이 두 번의 LLM 호출 모두에 쓰입니다.

### runWorkers(): 가상 스레드 + CompletableFuture

```java
private List<TaskResult> runWorkers(List<SearchCriteria> tasks, ExecutionTrace trace) {
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        List<CompletableFuture<TaskResult>> futures = tasks.stream()
                .map(task -> CompletableFuture.supplyAsync(() -> worker.execute(task, trace), executor))
                .toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }
}
```

| 코드 | 의미 |
|---|---|
| `Executors.newVirtualThreadPerTaskExecutor()` | 작업마다 **가상 스레드**(Java 21, JEP 444)를 하나씩 만듭니다 |
| `try (...)` | Java 21의 `ExecutorService`는 `AutoCloseable`입니다. 블록을 나갈 때 남은 작업을 기다리고 정리합니다 |
| `supplyAsync(..., executor)` | 실행기를 꼭 넘깁니다. 생략하면 CPU 코어 수만큼만 도는 공용 `ForkJoinPool`에서 실행됩니다 |
| 먼저 `toList()`로 모두 제출 | 스트림 하나에서 `supplyAsync`와 `join`을 이어 쓰면 하나씩 제출·대기하게 되어 **순차 실행**이 됩니다 |
| `futures.stream().map(join)` | 모두 끝날 때까지 기다리며, 결과는 **계획의 작업 순서대로** 모입니다 |

**왜 가상 스레드인가?** Worker의 시간 대부분은 공공데이터 포털 API 응답을 **기다리는 시간**(블로킹 I/O)입니다. 가상 스레드는 기다리는 동안 OS 스레드(캐리어)를 내어 주므로, 블로킹 코드(`RestClient`)를 그대로 쓰면서도 스레드 풀 크기를 고민하지 않고 동시에 실행할 수 있습니다.

`worker.execute()`가 `RuntimeException`을 모두 잡으므로 `join()`이 예외를 던지는 일은 없습니다. 한 Worker의 실패가 전체 요청을 무너뜨리지 않습니다.

### ExecutionTrace: 여러 스레드가 동시에 기록한다

`src/main/java/net/java21/flyschedule/dto/search/ExecutionTrace.java`

```java
private synchronized void add(TraceStep.Kind kind, String actor, String title, String detail,
                              long start, long end) {
    steps.add(new TraceStep(steps.size() + 1, kind, actor, title, detail,
            toMs(start - startedAt), toMs(end - start), threadName()));
}

/**
 * 가상 스레드는 이름이 비어 있으므로 "virtual-{id}"로 표시합니다.
 */
private static String threadName() {
    Thread thread = Thread.currentThread();
    return thread.isVirtual() ? "virtual-" + thread.threadId() : thread.getName();
}
```

- Worker 4개가 같은 `trace`의 `ArrayList`에 동시에 추가하므로 `add()`와 `steps()`를 `synchronized`로 묶습니다. 그렇지 않으면 기록이 사라지거나 `order`가 겹칩니다.
- 가상 스레드는 기본 이름이 빈 문자열입니다. 그대로 두면 화면에서 어느 Worker가 어느 스레드였는지 구분할 수 없으므로 `virtual-{id}`로 표시합니다.
- `order`는 **기록된 순서**입니다. `trace.run()`은 작업이 끝난 뒤 기록하므로, Worker 내부의 CODE 단계가 해당 WORKER 단계보다 먼저 번호를 받습니다. 실제 시작 시각은 `startMs`를 보세요.

> 참고: Java 21에서는 가상 스레드가 `synchronized` 블록 안에 있는 동안 캐리어 스레드에 고정(pinning)됩니다. 여기서는 목록에 한 줄 추가하는 짧은 구간이라 문제가 없지만, 블록 안에서 I/O를 하면 병렬성이 떨어집니다. (이 제약은 JDK 24의 JEP 491에서 풀렸습니다.)

---

## Step 11-4: ResultSynthesizer — 요약만 넘겨 종합하기

`src/main/java/net/java21/flyschedule/service/orchestrator/ResultSynthesizer.java`

```java
public String synthesize(String message, List<TaskResult> results, ChatClient chatClient) {
    String summaries = results.stream()
            .map(TaskResult::summaryLine)
            .collect(Collectors.joining("\n- ", "- ", ""));

    try {
        String answer = chatClient.prompt()
                .system("""
                        당신은 항공편 검색 결과를 비교해 추천하는 도우미입니다. 한국어로 3~4문장으로 답하세요.
                        아래 검색 결과에 있는 내용만 사용하고, 없는 항공편이나 가격을 지어내지 마세요.
                        """)
                .user("""
                        사용자 요청: %s

                        작업별 검색 결과:
                        %s
                        """.formatted(message, summaries))
                .call()
                .content();
        if (StringUtils.hasText(answer)) {
            return answer;
        }
    } catch (Exception e) {
        log.warn("결과 종합 LLM 호출 실패, 코드 요약 사용: {}", e.getMessage());
    }
    return fallbackSummary(results);
}
```

### 왜 요약 한 줄씩만 넘기는가

LLM에게 넘어가는 내용은 다음과 같습니다(`TaskResult.summaryLine()`).

```text
- 김포 → 제주 · 2026-10-16: 18편, 최저가 진에어 LJ... 06:55 출발 52,000원
- 김포 → 제주 · 2026-10-17: 16편, 최저가 제주항공 7C... 07:10 출발 55,000원
- 광주 → 제주 · 2026-10-16: 7편, 최저가 제주항공 7C... 07:30 출발 45,000원
- 광주 → 제주 · 2026-10-17: 항공편 없음
```

항공편 수십 편의 전체 목록을 넘기면

- 입력 토큰이 크게 늘어 **느리고 비싸며**, 로컬 모델은 컨텍스트 길이(`num-ctx`)를 넘기기 쉽고,
- 정보가 많을수록 작은 모델은 **엉뚱한 항공편을 섞어** 답하기 쉽습니다.

"제일 싼 항공편"을 고르는 데 필요한 정보는 작업별 편수와 최저가뿐입니다. **LLM에게는 판단에 필요한 만큼만** 줍니다. 전체 항공편 목록은 응답의 `results`로 화면에 따로 보여 줍니다.

### 대체 요약(fallback)

```java
String fallbackSummary(List<TaskResult> results) {
    return results.stream()
            .filter(result -> result.cheapest() != null)
            .min(Comparator.comparingInt(result -> result.cheapest().price()))
            .map(best -> "%d개 조건을 검색했습니다. 가장 저렴한 항공편은 %s"
                    .formatted(results.size(), best.summaryLine()))
            .orElse(results.size() + "개 조건을 검색했지만 조건에 맞는 항공편이 없습니다.");
}
```

종합 LLM 호출이 실패해도 Worker가 이미 모은 검색 결과는 버리지 않습니다. 코드로 "최저가 하나"를 골라 답하므로 사용자는 항상 결과를 받습니다.
종합은 "있으면 좋은" 단계이므로 실패를 삼키고, 계획은 "없으면 진행할 수 없는" 단계이므로 실패를 그대로 알립니다. **단계의 중요도에 따라 실패 처리 방식을 다르게** 정한 것입니다.

---

## Step 11-5: 실행 흐름과 화면 타임라인

### 시간 축으로 본 실행

```text
시간 →   0ms                  1,800ms          2,700ms              4,200ms
[LLM]    작업 계획 ████████████
[WORKER] 김포 → 제주 · 10-16                ██████████       (virtual-41)
[WORKER] 김포 → 제주 · 10-17                ████████         (virtual-42)
[WORKER] 광주 → 제주 · 10-16                ██████           (virtual-43)
[WORKER] 광주 → 제주 · 10-17                █████████        (virtual-44)
[LLM]    결과 종합                                     ███████████████
```

(시간은 예시입니다.) 네 Worker 막대가 **같은 위치에서 시작해 겹쳐** 있습니다. 순차 실행이었다면 막대가 계단처럼 이어졌을 것입니다.
전체 시간은 "계획 + **가장 느린** Worker + 종합"에 가깝고, "계획 + Worker 시간의 합 + 종합"보다 짧습니다.

### 화면에서 확인하기

`http://localhost:8080/`에서 **복합** 예시(금·토 × 김포·광주 → 제주)를 고르고 세 패턴을 모두 실행해 보세요.

- 패널 위 통계: `LLM 2회`, `Worker 4개`, `코드 단계 20개`(Worker마다 날짜 변환·공항 코드 변환 2회·API 호출·그룹핑)
- 실행 기록: WORKER 막대 4개가 나란히 겹치고, 각 줄 끝에 `· virtual-NN` 스레드 이름이 서로 다르게 표시됩니다
- 검색 결과: 작업마다 하나씩 4개의 결과 카드
- 같은 문장의 Coordinator 패널에는 결과가 하나뿐이라는 점도 비교해 보세요

---

## Step 11-6: API로 실행해 보기

### curl

```bash
curl -X POST http://localhost:8080/api/search/orchestrator \
  -H "Content-Type: application/json" \
  -d '{"message": "이번 주 금요일이나 토요일에 김포나 광주에서 제주 가는 제일 싼 항공편 찾아줘", "model": "gemini"}'
```

### 응답 예 (2026-10-15 실행, 일부 생략)

```json
{
  "pattern": "orchestrator",
  "success": true,
  "message": "네 가지 조건 중 금요일(10월 16일) 광주 출발 제주항공 07:30 항공편이 45,000원으로 가장 저렴합니다. ...",
  "results": [
    {
      "label": "김포 → 제주 · 2026-10-16",
      "criteria": { "departure": "김포", "arrival": "제주", "date": "2026-10-16",
                    "afterTime": null, "beforeTime": null, "minPrice": null, "maxPrice": null },
      "flightsByAirline": { "진에어": [ /* ... 생략 */ ], "대한항공": [ /* ... 생략 */ ] },
      "totalCount": 18,
      "cheapest": { "flightId": "LJ...", "airline": "진에어", "departureTime": "06:55", "price": 52000 }
    },
    { "label": "김포 → 제주 · 2026-10-17", "totalCount": 16 },
    { "label": "광주 → 제주 · 2026-10-16", "totalCount": 7 },
    { "label": "광주 → 제주 · 2026-10-17", "totalCount": 0, "cheapest": null }
  ],
  "trace": [
    { "order": 1, "kind": "LLM", "actor": "gemini", "title": "작업 계획 (FlightSearchPlanner)",
      "detail": "4개 작업: 김포 → 제주 · 2026-10-16 / 김포 → 제주 · 2026-10-17 / 광주 → 제주 · 2026-10-16 / 광주 → 제주 · 2026-10-17",
      "startMs": 0, "durationMs": 1790, "thread": "http-nio-8080-exec-2" },
    { "order": 2, "kind": "CODE", "actor": "DateParserAgent", "title": "날짜 변환",
      "detail": "2026-10-16 → 20261016", "startMs": 1792, "durationMs": 0, "thread": "virtual-43" },
    // ... 생략 (Worker마다 CODE 단계 5개)
    { "order": 19, "kind": "WORKER", "actor": "FlightSearchWorker", "title": "광주 → 제주 · 2026-10-16",
      "detail": "광주 → 제주 · 2026-10-16: 7편, 최저가 제주항공 7C... 07:30 출발 45,000원",
      "startMs": 1792, "durationMs": 610, "thread": "virtual-43" },
    { "order": 25, "kind": "WORKER", "actor": "FlightSearchWorker", "title": "김포 → 제주 · 2026-10-16",
      "detail": "김포 → 제주 · 2026-10-16: 18편, 최저가 진에어 LJ... 06:55 출발 52,000원",
      "startMs": 1791, "durationMs": 905, "thread": "virtual-41" },
    { "order": 26, "kind": "LLM", "actor": "gemini", "title": "결과 종합 (ResultSynthesizer)",
      "detail": "98자 답변", "startMs": 2698, "durationMs": 1480, "thread": "http-nio-8080-exec-2" }
  ],
  "elapsedMs": 4179
}
```

> 숫자·항공편은 예시입니다. WORKER 단계의 `startMs`가 거의 같고 `thread`가 서로 다르다는 점, WORKER의 `order`가 끝난 순서대로 매겨진다는 점을 확인하세요.

---

## Step 11-7: 테스트

세 클래스 모두 가짜 LLM으로 테스트합니다. LLM의 판단(작업을 몇 개로 나누는가)은 정해 두고, **그 판단을 받은 뒤의 코드**를 검증합니다.

### FlightSearchOrchestratorTest — 병렬성 검증

`src/test/java/net/java21/flyschedule/service/orchestrator/FlightSearchOrchestratorTest.java`

Planner와 Synthesizer는 Mockito 가짜로 바꾸고, Coordinator의 검색 단계는 **작업마다 300ms가 걸리는** 가짜로 만듭니다. Worker는 진짜입니다.

```java
private static final long WORKER_DELAY_MS = 300;

// Coordinator 검색 단계는 작업마다 WORKER_DELAY_MS가 걸린다고 가정
when(coordinator.search(any(), any())).thenAnswer(invocation -> {
    SearchCriteria task = invocation.getArgument(0);
    Thread.sleep(WORKER_DELAY_MS);
    int price = task.departure().equals("광주") ? 45000 : 52000;
    return TaskResult.of(task, Map.of("제주항공", List.of(flight("7C" + price, "제주항공", "1030", "1120", price))));
});

orchestrator = new FlightSearchOrchestrator(provider, planner, new FlightSearchWorker(coordinator), synthesizer);
```

```java
@Test
@DisplayName("Worker는 서로 다른 스레드에서 병렬로 실행된다 (전체 시간 < 작업 시간의 합)")
void workersRunInParallel() {
    when(planner.plan(anyString(), any()))
            .thenReturn(new SearchPlan("2 × 2", List.of(gimpoFri, gimpoSat, gwangjuFri, gwangjuSat)));

    SearchResponse response = orchestrator.orchestrate("금·토 김포·광주 → 제주", "ollama");

    List<TraceStep> workers = response.trace().stream().filter(s -> s.kind() == Kind.WORKER).toList();
    assertThat(workers).hasSize(4);
    assertThat(workers).extracting(TraceStep::thread).doesNotHaveDuplicates().allMatch(name -> name.startsWith("virtual-"));
    assertThat(response.elapsedMs()).isLessThan(WORKER_DELAY_MS * 4);
}
```

- 순차 실행이었다면 최소 300ms × 4 = 1,200ms가 걸립니다. **전체 시간이 작업 시간의 합보다 짧다**는 것으로 병렬 실행을 증명합니다.
- 스레드 이름이 모두 `virtual-`로 시작하고 서로 달라야 합니다.

| 그 밖의 테스트 | 검증 내용 |
|---|---|
| `planExecuteSynthesize` | 실행 기록이 LLM(계획)으로 시작해 LLM(종합)으로 끝나고, 작업 수만큼 WORKER 단계와 결과가 있다 |
| `workerFailureIsolated` | 한 작업에서 예외가 나도 `success: true`, 실패한 작업은 빈 결과, 나머지는 정상 |
| `planFailure` | 계획이 실패하면 Coordinator와 Synthesizer를 **한 번도 부르지 않는다** (`verify(..., never())`) |
| `workerSharesTrace` | Worker가 Coordinator에 같은 `trace`를 넘기고, WORKER 단계의 `detail`이 `summaryLine()`이다 |

### FlightSearchPlannerTest — 계획 검증

`src/test/java/net/java21/flyschedule/service/orchestrator/FlightSearchPlannerTest.java`

`RETURNS_DEEP_STUBS`로 `ChatClient`의 호출 체인 끝(`entity(SearchPlan.class)`)만 정해 둡니다.

```java
ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
when(chatClient.prompt().system(anyString()).user(anyString()).call().entity(SearchPlan.class))
        .thenReturn(fromLlm);   // LLM이 작업 4개로 나눴다고 가정
```

`validate()`는 LLM 없이 직접 호출합니다.

```java
@Test
@DisplayName("알 수 없는 공항, 출발=도착, 경로 없는 작업은 버린다")
void dropsInvalidTasks() {
    SearchPlan result = planner.validate(plan(
            task("광주", "제주", "2026-03-27"),
            task("서울", "제주", "2026-03-27"),
            task("제주", "제주", "2026-03-27"),
            task(null, "제주", "2026-03-27")));

    assertThat(result.tasks()).containsExactly(task("광주", "제주", "2026-03-27"));
}
```

이 밖에 중복 제거와 최대 4개 제한(`dedupeAndLimit`), 빈 계획·`null` 계획의 예외(`emptyPlan`), `"광주공항"`·`"제주국제공항"` 같은 이름 허용(`acceptsAirportSuffix`), `"광주"`와 `"광주공항"`을 같은 공항으로 보는 중복·출발=도착 검사(`normalizesAirportNames`: "광주공항 → 제주국제공항"은 중복, "김해 → 부산"은 출발=도착으로 제거)를 검증합니다.

### ResultSynthesizerTest — 무엇을 넘기는지 검증

`src/test/java/net/java21/flyschedule/service/orchestrator/ResultSynthesizerTest.java`

```java
ArgumentCaptor<String> userMessage = ArgumentCaptor.forClass(String.class);
verify(spec).user(userMessage.capture());
assertThat(userMessage.getValue())
        .contains(gimpo.summaryLine())
        .contains(gwangju.summaryLine())
        .doesNotContain("OZ"); // 넘기지 않은 데이터는 없다
```

`ArgumentCaptor`로 LLM에게 실제로 보낸 사용자 메시지를 붙잡아, **요약 한 줄씩만** 들어갔는지 확인합니다. LLM 호출이 예외를 던질 때 대체 요약을 돌려주는지(`fallbackOnFailure`), 결과가 모두 비었을 때의 문구(`fallbackWhenAllEmpty`)도 검증합니다.

```bash
./mvnw test -Dtest='FlightSearchOrchestratorTest,FlightSearchPlannerTest,ResultSynthesizerTest'
```

---

## 패턴 총정리

### 비교표

| 항목 | Coordinator (Step 9) | Tool Calling 에이전트 (Step 10) | Orchestrator-Workers (Step 11) | A2A 프로토콜 (참고) |
|---|---|---|---|---|
| 다음 단계를 누가 결정하는가 | 코드 | LLM (매 단계) | LLM (계획·종합), 실행은 코드 | 각 에이전트가 자기 일을 결정, 에이전트끼리 메시지로 위임 |
| 하위 작업이 미리 정해져 있는가 | 예 (고정 순서) | 아니요 (LLM이 Tool 호출마다 정함) | 아니요 (LLM이 계획에서 정함) | 상대 에이전트의 능력(Agent Card)에 따라 다름 |
| LLM 호출 수 | 0~1회 | Tool 호출 N회 + 1회 | 2회 (작업 수와 무관) | 에이전트마다 각자 |
| 지연 | 짧음 | 김 (왕복이 순차로 누적) | 중간 (Worker는 병렬) | 네트워크 왕복 추가 |
| 비용 | 낮음 | 높음 | 중간 | 에이전트 수에 비례 |
| 결정성 | 높음 | 낮음 | 중간 (계획만 달라질 수 있음) | 낮음 |
| 적합한 상황 | 처리 순서가 늘 같은 업무 | 다음 행동이 앞 결과에 따라 달라지는 탐색형 작업 | 독립적인 하위 작업으로 쪼갤 수 있고, 그 개수가 입력마다 다른 작업 | **다른 프로세스·조직의 에이전트와 통신할 때** — 이 프로젝트 범위 밖 |
| 이 프로젝트 | `FlightSearchCoordinator` | `ToolCallingSearchService` | `FlightSearchOrchestrator` | 구현 없음 |

### 어떤 패턴을 고를까

Anthropic은 "가장 단순한 해법에서 시작하고, 복잡도가 결과를 확실히 개선할 때만 늘리라"고 권합니다. 이 교재의 결론도 같습니다.

```text
1. 먼저 Coordinator로 충분한지 보라
   처리 순서를 코드로 적을 수 있는가? → 예 → Coordinator (빠르고, 싸고, 테스트하기 쉽다)
   ↓ 아니요
2. 하위 작업을 미리 정할 수 없는가?
   서로 독립적인 작업으로 나눌 수 있다 → Orchestrator-Workers (계획 1번 + 병렬 실행 + 종합 1번)
   앞 결과를 보고 다음 행동을 정해야 한다 → Tool Calling 에이전트
   ↓
3. 에이전트가 다른 서비스·다른 팀·다른 조직에 있는가?
   → 예 → A2A 같은 에이전트 간 프로토콜을 검토
```

이 프로젝트의 문장으로 정리하면 다음과 같습니다.

| 문장 | 적합한 패턴 |
|---|---|
| "내일 광주에서 제주" | Coordinator. 정규식만으로 끝나 LLM 호출 0회 |
| "제주 가는 비행기 중 아시아나만 보여 주고, 없으면 대한항공으로" | Tool Calling 에이전트. 앞 결과에 따라 다음 검색이 달라짐 |
| "금요일이나 토요일에 김포나 광주에서 제주, 제일 싼 거" | Orchestrator-Workers. 독립 작업 4개 + 비교 |

---

## 일반적인 문제 해결

| 증상 | 원인 | 해결 |
|---|---|---|
| `"검색 계획을 세우지 못했습니다..."` | LLM이 JSON 형식을 지키지 못해 `entity()`가 `null`이거나 작업 목록이 없음 | 더 큰 모델(Gemini) 사용, 요청 문장에 출발지·도착지를 명확히 |
| `"검색할 수 있는 작업이 없습니다..."` | 모든 작업이 `validate()`에서 걸러짐 (예: "서울에서 제주") | 지원하는 공항 이름(김포, 광주, 제주 등)으로 입력. 로그의 "계획 검증" 경고 확인 |
| 작업이 4개를 넘을 요청인데 4개만 검색됨 | `MAX_TASKS = 4`로 잘림 | 의도된 제한. 사용자에게 알리려면 실습 과제 1 참고 |
| 결과 하나가 "항공편 없음" | 그 Worker가 실패했거나 실제로 항공편이 없음 | 실행 기록에서 해당 WORKER 단계의 `detail`이 `"실패: ..."`인지 확인. 모든 Worker가 "공공데이터 포털 ..."로 실패했다면 `DATA_GO_KR_SERVICE_KEY` 확인 |
| WORKER 막대가 겹치지 않고 계단처럼 이어짐 | `supplyAsync`에 실행기를 넘기지 않았거나, 제출과 `join`을 한 스트림에서 섞음 | `runWorkers()`의 "모두 제출한 뒤 모두 기다리기" 구조 유지 |
| 답변에 없는 항공편·가격이 나옴 | 종합 LLM의 환각 | 시스템 프롬프트의 "결과에 있는 내용만 사용" 규칙 확인, `results`의 실제 데이터와 대조 |

---

## 실습 과제

1. **(기본)** `MAX_TASKS`를 넘는 계획을 받았을 때 사용자에게 알리도록 하라. `SearchResponse`에 `List<String> warnings` 필드를 추가하고, `FlightSearchPlanner.validate()`가 버린 작업 수와 이유("알 수 없는 공항", "최대 4개 초과" 등)를 경고로 남기도록 바꿔라. `FlightSearchPlannerTest`에 경고 내용을 검증하는 테스트를 추가하라.
2. **(기본)** Worker 실패와 "항공편 없음"을 구분하라. 지금은 둘 다 빈 `TaskResult`입니다. `TaskResult`에 실패 사유를 담을 필드를 추가하고, `ResultSynthesizer`에 넘기는 요약 한 줄이 `"김포 → 제주 · 2026-10-16: 검색 실패(API 타임아웃)"`처럼 바뀌도록 하라. `FlightSearchOrchestratorTest.workerFailureIsolated`를 함께 고쳐라.
3. **(도전)** 계획 단계와 종합 단계의 LLM 호출을 하나로 줄일 수 있는 요청은 줄여라. `SearchPlan`의 작업이 1개뿐이면 `ResultSynthesizer`를 부르지 않고 `TaskResult.summaryLine()`을 그대로 답으로 쓰도록 `FlightSearchOrchestrator`를 바꾸고, 실행 기록의 LLM 단계 수가 1이 되는지 `FlightSearchOrchestratorTest`에 테스트를 추가하라. 이 최적화가 "결과가 무슨 뜻인지는 LLM이 판단한다"는 패턴의 원칙과 어떻게 충돌하는지도 정리하라.
4. **(도전)** Worker마다 제한 시간을 두어라. `CompletableFuture.orTimeout(...)` 또는 `completeOnTimeout(...)`으로 5초 안에 끝나지 않은 작업은 빈 결과로 처리하고, 이를 검증하는 테스트(가짜 Coordinator가 10초 동안 `sleep`)를 추가하라.

---

## 요약

1. **Orchestrator-Workers**는 LLM이 요청을 하위 작업으로 **동적으로** 나누고, Worker가 처리하고, LLM이 종합하는 패턴입니다. 하위 작업이 미리 정해져 있지 않다는 점이 Coordinator와의 핵심 차이입니다.
2. `FlightSearchPlanner`는 `.entity(SearchPlan.class)`로 계획을 받고, `validate()`로 **알 수 없는 공항·출발=도착·중복·개수 초과를 걸러 냅니다**. LLM 출력은 사용자 입력처럼 검증합니다.
3. `FlightSearchWorker`는 Step 9의 `FlightSearchCoordinator.search()`를 재사용합니다. **상위 결정은 LLM, 하위 실행은 코드**로 패턴을 조합하고, 작업 하나의 실패는 빈 결과로 격리합니다.
4. `FlightSearchOrchestrator`는 **가상 스레드**와 `CompletableFuture`로 Worker를 병렬 실행합니다. `ExecutionTrace`는 동기화되어 있고, 스레드 이름 `virtual-N`으로 병렬 실행을 눈으로 확인할 수 있습니다.
5. `ResultSynthesizer`는 **요약 한 줄씩만** LLM에 넘겨 프롬프트를 작게 유지하고, 실패하면 코드로 만든 요약을 돌려줍니다.
6. 패턴을 고를 때는 **먼저 Coordinator로 충분한지 보고**, 필요할 때만 LLM에게 결정을 넘깁니다.

---

## 체크리스트

- [ ] Coordinator와 Orchestrator-Workers의 차이를 "하위 작업이 미리 정해져 있는가"로 설명할 수 있다
- [ ] "금요일이나 토요일에 김포나 광주에서 제주"를 Coordinator가 처리하지 못하는 이유를 안다
- [ ] `FlightSearchPlanner.validate()`의 다섯 가지 검증을 말할 수 있다
- [ ] Worker가 Coordinator의 `search()`를 재사용하는 이유를 설명할 수 있다
- [ ] `runWorkers()`에서 모든 작업을 먼저 제출한 뒤 기다려야 하는 이유를 안다
- [ ] 화면 실행 기록에서 WORKER 막대 4개가 겹치고 스레드 이름이 서로 다른 것을 확인했다
- [ ] `ResultSynthesizer`가 전체 항공편 목록 대신 요약만 넘기는 이유를 안다
- [ ] `FlightSearchOrchestratorTest`, `FlightSearchPlannerTest`, `ResultSynthesizerTest`가 통과한다
- [ ] 세 패턴과 A2A 프로토콜의 차이를 표로 정리할 수 있다

---

## 다음 단계

Step 11은 이 교재의 마지막 단계입니다. Step 1의 ChatClient 설정부터 Tool, 전문 에이전트, 그리고 세 가지 검색 패턴까지의 전체 흐름은 [교재 목차(README)](README.md)에서 다시 확인할 수 있습니다.

---

## 참고 자료

- [Anthropic - Building effective agents](https://www.anthropic.com/engineering/building-effective-agents)
- [Spring 블로그 - Building Effective Agents with Spring AI](https://spring.io/blog/2025/01/21/spring-ai-agentic-patterns)
- [Spring AI Reference - Structured Output Converter](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html)
- [Spring AI Reference - ChatClient API](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [JEP 491: Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491)

---

## 심화 학습

### A2A 프로토콜로 Worker를 별도 서비스로 분리하기

이 프로젝트의 Worker는 같은 JVM 안의 메서드 호출입니다. 만약 항공편 검색은 A 팀이, 호텔 검색은 B 팀이, 렌터카 검색은 외부 회사가 **각자의 서버에서 각자의 에이전트로** 운영한다면 어떻게 될까요?

**A2A(Agent2Agent) 프로토콜**은 Google이 2025년에 발표하고 현재 Linux Foundation에서 관리하는, 독립 실행되는 에이전트끼리 HTTP(JSON-RPC)로 통신하는 표준입니다. MCP가 "에이전트 ↔ Tool·데이터"를 잇는다면 A2A는 "에이전트 ↔ 에이전트"를 잇습니다.

```text
[여행 Orchestrator 에이전트]
  ↓ Agent Card 조회 (이 에이전트는 무엇을 할 수 있는가)
  ↓ A2A 요청 (HTTP, JSON-RPC)
  ├→ [항공편 검색 에이전트 서비스]   ← 이 프로젝트의 Worker를 별도 서비스로 분리한 모습
  ├→ [호텔 검색 에이전트 서비스]
  └→ [렌터카 에이전트 서비스]
```

개념적으로는 `FlightSearchWorker.execute()`의 메서드 호출을 A2A 요청으로 바꾸는 것입니다. 이때 다음 문제가 새로 생깁니다.

- 네트워크 실패·재시도·타임아웃, 그리고 인증
- 오래 걸리는 작업의 상태 조회와 스트리밍 응답
- 상대 에이전트의 능력을 어떻게 알아낼 것인가(Agent Card)

같은 프로세스 안에서 해결되는 문제라면 A2A는 필요 없습니다. **조직·서비스의 경계를 넘을 때** 검토하세요.

- [A2A 프로토콜 공식 사이트](https://a2a-protocol.org)
- [A2A GitHub 저장소](https://github.com/a2aproject/A2A)

### Evaluator-Optimizer 패턴

Anthropic이 소개한 또 다른 워크플로 패턴입니다. 한 LLM이 답을 만들고, 다른 LLM(또는 같은 LLM)이 기준에 따라 평가해 피드백을 주면, 기준을 통과할 때까지 고쳐 쓰기를 반복합니다.

```text
[생성 LLM] 답변 초안 → [평가 LLM] "결과에 없는 가격이 들어 있음" → [생성 LLM] 수정 → [평가 LLM] 통과
```

이 프로젝트에 적용한다면 `ResultSynthesizer`의 답변을 평가 단계에 넣어 "답변의 모든 가격과 항공편명이 `results`에 실제로 있는가"를 검사하고, 통과하지 못하면 다시 쓰게 할 수 있습니다. 이 검사는 LLM이 아니라 **코드로도** 할 수 있다는 점도 함께 생각해 보세요.
