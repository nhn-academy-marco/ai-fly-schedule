# Step 9: Coordinator 패턴 — 코드가 제어하는 워크플로

## 학습 목표

이 단계를 마치면 다음을 할 수 있습니다:

1. **Coordinator 패턴**이 무엇인지, "다음 단계를 누가 결정하는가"라는 기준으로 설명할 수 있다
2. Coordinator 패턴을 업계 용어(**workflow / prompt chaining**, **sequential orchestration**)와 연결하고, Step 10의 Tool Calling 에이전트, Step 11의 Orchestrator-Workers 패턴과 구분할 수 있다
3. **정규식을 먼저, LLM은 실패했을 때만** 쓰는 `SearchCriteriaExtractor`를 이해하고, `.entity(SearchCriteria.class)`로 **Structured Output**을 받을 수 있다
4. `FlightSearchCoordinator`가 전문 에이전트를 정해진 순서로 호출하면서 `ExecutionTrace`에 실행 기록을 남기는 방법을 설명할 수 있다
5. `POST /api/search/coordinator`를 호출하고 응답(`SearchResponse`)의 결과와 실행 기록을 읽을 수 있다
6. 가짜 LLM과 가짜 API로 Coordinator를 테스트할 수 있다

---

> 💡 **핵심 개념**: **Coordinator 패턴**은 **코드가 처리 순서를 정하고**, 전문 에이전트를 차례로 호출하는 워크플로입니다.
> LLM은 "자연어 문장에서 검색 조건을 뽑는 일"이 정규식으로 안 될 때만 잠깐 도와줄 뿐, **다음에 무엇을 할지는 결정하지 않습니다.**
>
> 일상생활에 비유하면: **업무 매뉴얼대로 일하는 여행사 직원**입니다. 고객의 말을 메모지(검색 조건)로 정리한 뒤,
> 매뉴얼에 적힌 순서대로 "날짜 담당 → 공항 담당 → 항공편 조회 → 시간 필터 → 가격 필터 → 항공사별 정리"를 진행합니다.
> 메모가 잘 안 써질 때만 선배(LLM)에게 "이 손님 말이 무슨 뜻이에요?"라고 물어봅니다.

---

## 전체 흐름

```text
[사용자: "내일 광주에서 제주 오전 10시 이후 8만원 이하"]
       ↓
[SearchController  POST /api/search/coordinator]
       ↓
[FlightSearchCoordinator.coordinate(message, model)]
       ↓
1. SearchCriteriaExtractor   문장 → SearchCriteria   (정규식, 실패하면 LLM)
       ↓  ─── 여기서부터 search(criteria, trace) ───
2. DateParserAgent           "2026-03-25" → "20260325"
3. AirportCodeAgent ×2       "광주" → "NAARKJJ", "제주" → "NAARKPC"
4. ApiClientService          공공데이터 포털 API 호출
5. TimeFilterAgent           10:00 이후 출발만 (조건이 있을 때만)
6. PriceFilterAgent          8만 원 이하만 (조건이 있을 때만)
7. GroupingAgent             항공사별 그룹핑
       ↓
[SearchResponse  pattern="coordinator", results=[TaskResult], trace=[TraceStep...]]
```

2~7단계는 모두 Step 8에서 만든 **전문 에이전트**입니다. 이 단계에서 새로 만드는 것은 1단계의 `SearchCriteriaExtractor`와, 순서를 정하는 `FlightSearchCoordinator`, 그리고 실행 기록을 담는 DTO입니다.

---

## Coordinator 패턴이란?

### 다음 단계를 누가 결정하는가

LLM을 쓰는 시스템을 비교할 때 가장 중요한 질문은 **"다음 단계를 누가 결정하는가?"**입니다.
Coordinator 패턴의 답은 **"개발자가 작성한 코드"**입니다.

```java
// FlightSearchCoordinator.search() 의 뼈대
date       = dateParserAgent.parseDate(...);       // 항상 2단계
depCode    = airportCodeAgent.getAirportCode(...); // 항상 3단계
flights    = apiClientService.getFlightSchedule(...);
if (afterTime != null) flights = timeFilterAgent.filterAfterTime(...);   // 조건 분기도 코드가 결정
if (maxPrice  != null) flights = priceFilterAgent.filterByPriceRange(...);
grouped    = groupingAgent.groupByAirline(flights);
```

같은 문장을 넣으면 언제나 같은 순서로 같은 에이전트가 호출됩니다. 그래서 **예측할 수 있고, 빠르고, 테스트하기 쉽습니다.**

### 업계 용어와의 대응

| 출처 | 용어 | 뜻 |
|------|------|----|
| Anthropic, "Building effective agents" | **Workflow** / **Prompt chaining** | LLM과 도구를 **미리 정한 코드 경로**로 연결. 각 단계의 출력이 다음 단계의 입력 |
| Microsoft Azure Architecture Center | **Sequential orchestration** | 에이전트(또는 처리 단계)를 정해진 순서의 파이프라인으로 연결 |
| 이 교재 | **Coordinator 패턴** | 코드가 순서를 정하고 전문 에이전트를 차례로 호출 |

Anthropic은 LLM 시스템을 **workflow**(코드가 정한 경로를 따름)와 **agent**(LLM이 스스로 경로를 정함)로 나눕니다.
Coordinator 패턴은 workflow 쪽이며, 이 프로젝트에서는 LLM이 끼어드는 곳마저 1단계 한 군데로 줄였습니다.

### 세 가지 패턴 비교

이 프로젝트는 같은 문장을 세 가지 방식으로 처리합니다. 화면(`/`)에서 세 결과를 나란히 비교할 수 있습니다.

| 항목 | **Coordinator** (Step 9) | **Tool Calling 에이전트** (Step 10) | **Orchestrator-Workers** (Step 11) |
|------|--------------------------|-------------------------------------|-------------------------------------|
| **다음 단계를 누가 결정?** | 코드 | LLM (어떤 Tool을 언제 부를지) | LLM이 작업을 나누고, 각 작업은 코드가 처리 |
| **하위 작업이 미리 정해져 있나?** | 예 (7단계 고정) | 아니요 (LLM이 그때그때 고름) | 아니요 (LLM이 요청마다 1~4개로 분해) |
| **LLM 호출 수** | 0~1회 (정규식 실패 시 1회) | 2회 이상 (Tool 결과를 보고 다시 판단) | 2회 (계획 1회 + 종합 1회) |
| **장점** | 빠름, 결과가 일정, 비용 적음, 테스트 쉬움 | 예상 못 한 질문에도 유연 | "김포나 광주에서" 같은 복합 요청 처리, Worker 병렬 실행 |
| **단점** | 정해진 흐름 밖의 요청은 처리 못 함 | 느림, 결과가 들쭉날쭉, 작은 모델은 Tool을 잘못 고름 | 구조가 복잡, LLM 계획이 틀릴 수 있음 |
| **이 프로젝트** | `FlightSearchCoordinator` | `ToolCallingSearchService` | `FlightSearchOrchestrator` |

> ⚠️ **용어 주의**: "Coordinator"와 "Orchestrator"는 일상어로는 비슷하지만 이 교재에서는 **다른 패턴**입니다.
> Coordinator는 **코드**가 순서를 정하고, Orchestrator(Step 11)는 **LLM**이 작업을 나눕니다.

**언제 Coordinator를 고를까요?** 처리 절차가 업무 규칙으로 정해져 있고(항공편 검색은 언제나 "변환 → 조회 → 필터 → 정리"), 결과가 매번 같아야 하며, 응답이 빨라야 할 때입니다. Anthropic도 "가장 단순한 방법으로 시작하고, 필요할 때만 복잡도를 올리라"고 권합니다.

---

## Step 9-1: 검색 조건 DTO — SearchCriteria

**파일**: `src/main/java/net/java21/flyschedule/dto/search/SearchCriteria.java`

세 가지 패턴이 모두 이 레코드로 검색 조건을 주고받습니다.

```java
public record SearchCriteria(
        @JsonPropertyDescription("출발 공항 한글 이름 (예: 광주, 김포, 제주)")
        String departure,

        @JsonPropertyDescription("도착 공항 한글 이름 (예: 제주, 김포, 김해)")
        String arrival,

        @JsonPropertyDescription("출발 날짜, YYYY-MM-DD 형식")
        String date,

        @JsonPropertyDescription("이 시각 이후 출발, HH:mm 형식. 조건이 없으면 null")
        String afterTime,

        @JsonPropertyDescription("이 시각 이전 출발, HH:mm 형식. 조건이 없으면 null")
        String beforeTime,

        @JsonPropertyDescription("최소 가격(원). 조건이 없으면 null")
        Integer minPrice,

        @JsonPropertyDescription("최대 가격(원). 조건이 없으면 null")
        Integer maxPrice
) {

    public boolean hasRoute() {
        return departure != null && !departure.isBlank() && arrival != null && !arrival.isBlank();
    }

    /** 화면과 로그에 쓰는 짧은 이름 (예: "광주 → 제주 · 2026-03-25") */
    public String label() {
        StringBuilder label = new StringBuilder(departure + " → " + arrival);
        // date, afterTime("10:00 이후"), beforeTime("18:00 이전") ... 생략
        if (minPrice != null) {
            label.append(" · ").append(won(minPrice)).append(" 이상");
        }
        if (maxPrice != null) {
            label.append(" · ").append(won(maxPrice)).append(" 이하");
        }
        return label.toString();
    }

    private static String won(int price) {
        return price % 10000 == 0 ? (price / 10000) + "만원" : "%,d원".formatted(price);
    }
}
```

`label()`은 실행 기록의 1단계 `detail`, 결과 카드 제목, LLM에게 넘기는 요약 한 줄에 모두 쓰입니다. 그래서 조건이 하나라도 빠지면 "왜 이렇게 검색했는지"를 알 수 없습니다. 최소 가격도 `"3만원 이상"`으로 표시하고, LLM이 `55500`처럼 만원 단위가 아닌 값을 주면 `"55,500원 이하"`로 정확히 보여 줍니다.

### 왜 @JsonPropertyDescription을 붙일까요?

이 레코드는 LLM의 **Structured Output** 대상입니다. `.entity(SearchCriteria.class)`를 호출하면 Spring AI의 `BeanOutputConverter`가 레코드에서 **JSON Schema**를 만들어 프롬프트에 붙이고, LLM의 JSON 응답을 다시 레코드로 바꿉니다.

이때 `@JsonPropertyDescription`의 문장이 스키마의 `description`으로 들어갑니다.

```json
{
  "type": "object",
  "properties": {
    "departure": { "type": "string", "description": "출발 공항 한글 이름 (예: 광주, 김포, 제주)" },
    "date":      { "type": "string", "description": "출발 날짜, YYYY-MM-DD 형식" },
    "maxPrice":  { "type": "integer", "description": "최대 가격(원). 조건이 없으면 null" }
  }
}
```

필드 이름만으로는 LLM이 `date`에 "내일"을 넣을지 "2026-03-25"를 넣을지, `departure`에 "NAARKJJ"를 넣을지 "광주"를 넣을지 알 수 없습니다.
설명을 붙이면 **다음 단계의 전문 에이전트가 받을 수 있는 형식**으로 값을 채우도록 유도할 수 있습니다. 즉 설명문이 LLM과 전문 에이전트 사이의 **계약서** 역할을 합니다.

---

## Step 9-2: SearchCriteriaExtractor — 정규식 먼저, LLM은 나중에

**파일**: `src/main/java/net/java21/flyschedule/service/ai/SearchCriteriaExtractor.java`

```java
@Slf4j
@Service
public class SearchCriteriaExtractor {

    /** 정규식 파싱에서 인식하는 공항 이름 (AirportCodeAgent가 변환할 수 있는 이름) */
    private static final List<String> AIRPORT_NAMES = List.of(
            "김포", "인천", "김해", "부산", "사천", "울산", "광주", "여수", "무안", "군산",
            "대구", "포항", "청주", "양양", "원주", "제주");

    private static final Pattern DATE_PATTERN =
            Pattern.compile("(\\d{4})[년\\-.]\\s*(\\d{1,2})[월\\-.]\\s*(\\d{1,2})일?");
    private static final Pattern TIME_PATTERN =
            Pattern.compile("(오전|오후)?\\s*(\\d{1,2})\\s*시(?:\\s*(\\d{1,2})\\s*분)?\\s*(이후|부터|이전|까지|전)?");
    private static final Pattern COMPLEX_DATE_PATTERN =
            Pattern.compile("요일|주말|이번\\s*주|다음\\s*주|다다음|글피|\\d+\\s*일\\s*(뒤|후)|\\d{1,2}\\s*월\\s*\\d{1,2}\\s*일");
    private static final Pattern PRICE_PATTERN =
            Pattern.compile("(\\d+)\\s*만\\s*원\\s*(이하|미만|까지|이상|초과|부터)?");

    /** 추출 결과 (usedLlm이 false면 정규식으로 처리) */
    public record Extraction(SearchCriteria criteria, boolean usedLlm) {
    }

    public Extraction extract(String message, Supplier<ChatClient> chatClient) {
        // ① 정규식 먼저
        Optional<SearchCriteria> parsed = parseWithRegex(message);
        if (parsed.isPresent()) {
            return new Extraction(parsed.get(), false);
        }

        // ② 정규식이 포기했을 때만 LLM (Structured Output)
        SearchCriteria criteria = chatClient.get().prompt()
                .system("""
                        항공편 검색 문장에서 검색 조건을 추출합니다.
                        오늘 날짜는 %s입니다. "내일", "다음 주 금요일" 같은 표현은 이 날짜를 기준으로 YYYY-MM-DD로 바꿉니다.
                        공항은 공항 코드가 아니라 한글 이름(광주, 김포, 제주 등)으로 적습니다.
                        문장에 없는 조건은 null로 둡니다.
                        """.formatted(LocalDate.now()))
                .user(message)
                .call()
                .entity(SearchCriteria.class);

        // ③ LLM 결과도 검증
        if (criteria == null || !criteria.hasRoute()) {
            throw new IllegalArgumentException("출발 공항과 도착 공항을 찾지 못했습니다. 예: \"내일 광주에서 제주\"");
        }
        return new Extraction(criteria, true);
    }
}
```

### 정규식 파싱: 공항이 "정확히 두 개"일 때만

```java
Optional<SearchCriteria> parseWithRegex(String message) {
    // 공항: 메시지에 나온 순서대로 정렬한 뒤, "~에서"가 붙은 공항을 출발지로 본다
    List<String> found = AIRPORT_NAMES.stream()
            .filter(message::contains)
            .sorted(Comparator.comparingInt(message::indexOf))
            .toList();
    if (found.size() != 2) {
        // 공항이 0~1개면 정보 부족, 3개 이상이면 "김포나 광주에서 제주"처럼 해석이 필요하므로 LLM에 맡긴다
        return Optional.empty();
    }
    String first = found.get(0);
    String second = found.get(1);
    boolean secondIsDeparture = message.contains(second + "에서") && !message.contains(first + "에서");
    String departure = secondIsDeparture ? second : first;
    String arrival = secondIsDeparture ? first : second;

    // 날짜: 2026년 3월 25일, 2026-03-25, 내일, 모레 (없으면 오늘)
    String date = LocalDate.now().toString();
    Matcher dateMatcher = DATE_PATTERN.matcher(message);
    boolean hasAbsoluteDate = dateMatcher.find();
    if (!hasAbsoluteDate && COMPLEX_DATE_PATTERN.matcher(message).find()) {
        // "다음 주 금요일", "3일 뒤"처럼 계산이 필요한 날짜는 정규식으로 처리하지 않고 LLM에 맡긴다
        return Optional.empty();
    }
    // ... 절대 날짜 → "YYYY-MM-DD", "모레"/"내일" → 오늘 + 2/1일 (생략)

    // 시간: 10시 이후, 오후 2시 30분 이전 → "HH:mm"
    // 가격: 3만원에서 5만원 사이, 6만원 이하, 4만원 이상 → 원 단위 정수
    // ... 생략

    return Optional.of(new SearchCriteria(departure, arrival, date, afterTime, beforeTime, minPrice, maxPrice));
}
```

| 입력 | 정규식 결과 | 이유 |
|------|-------------|------|
| "내일 광주에서 제주" | 광주 → 제주, 내일 | 공항 2개 |
| "광주로 가는 제주에서 출발 항공편" | 제주 → 광주 | "~에서"가 붙은 쪽이 출발지 |
| "제주 가는 항공편" | 포기 → LLM | 공항 1개 |
| "서울에서 부산" | 포기 → LLM | "서울"은 공항 이름이 아님 |
| "김포나 광주에서 제주" | 포기 → LLM | 공항 3개, 해석이 필요 |
| "다음 주 금요일 광주에서 제주" | 포기 → LLM | 계산이 필요한 날짜 (`COMPLEX_DATE_PATTERN`) |
| "3월 25일 광주에서 제주" | 포기 → LLM | 연도 없는 날짜도 계산 필요 |

**설계 포인트**

- **확실할 때만 정규식으로 끝냅니다.** 공항이 정확히 두 개라는 조건은 "출발지와 도착지가 모두 있고 모호하지 않다"는 뜻입니다. 조금이라도 모호하면 `Optional.empty()`로 LLM에 넘깁니다. 정규식이 **틀린 답**을 내는 것보다 **답을 포기**하는 편이 안전합니다.
- **날짜도 같은 원칙입니다.** 공항이 두 개여도 "다음 주 금요일"처럼 계산이 필요한 날짜가 있으면 포기합니다. 그대로 두면 날짜를 못 찾은 정규식이 기본값(오늘)으로 검색해 **조용히 틀린 결과**를 내기 때문입니다. `COMPLEX_DATE_PATTERN`은 요일·주말·이번 주·다음 주·다다음·글피·"N일 뒤/후"·연도 없는 "M월 D일"을 잡아 LLM으로 넘깁니다.
- **LLM 호출은 비쌉니다.** 정규식 경로는 몇 밀리초, LLM 경로는 작은 로컬 모델에서 수 초가 걸립니다. 자주 들어오는 단순한 문장은 정규식으로 끝내면 빠르고 **결과도 항상 같습니다.**
- **시스템 프롬프트에 오늘 날짜를 넣습니다.** LLM은 오늘이 며칠인지 모르므로 "내일"을 계산하려면 기준 날짜가 필요합니다.
- **LLM 결과도 믿지 않습니다.** `hasRoute()`로 출발·도착이 채워졌는지 확인하고, 없으면 사용자가 고칠 수 있는 안내 문장과 함께 `IllegalArgumentException`을 던집니다. 공항 이름이 틀렸다면 다음 단계의 `AirportCodeAgent`가 한 번 더 거릅니다.

### 왜 ChatClient가 아니라 Supplier<ChatClient>를 받을까요?

```java
criteriaExtractor.extract(message, () -> chatClientProvider.get(model));
```

`ChatClientProvider.get("gemini")`는 Gemini API 키가 설정되지 않았으면 `IllegalArgumentException`을 던집니다.
`ChatClient`를 바로 받으면 정규식으로 충분한 문장도 **모델 설정을 먼저 확인**하다가 실패합니다.

`Supplier`로 받으면 **LLM이 정말 필요할 때(`chatClient.get()`)만** 모델을 꺼냅니다. 그래서 Gemini 키가 없어도 "내일 광주에서 제주"는 정규식으로 검색됩니다. 이를 **지연 평가(lazy evaluation)**라고 합니다. `FlightSearchCoordinatorTest`의 `regexPathIgnoresModelConfig()`가 이 동작을 확인합니다.

---

## Step 9-3: FlightSearchCoordinator — 순서를 정하는 코드

**파일**: `src/main/java/net/java21/flyschedule/service/coordinator/FlightSearchCoordinator.java`

### coordinate(): 요청 하나를 처리

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class FlightSearchCoordinator {

    public static final String PATTERN = "coordinator";

    private final ChatClientProvider chatClientProvider;
    private final SearchCriteriaExtractor criteriaExtractor;
    private final DateParserAgent dateParserAgent;
    private final AirportCodeAgent airportCodeAgent;
    private final ApiClientService apiClientService;
    private final TimeFilterAgent timeFilterAgent;
    private final PriceFilterAgent priceFilterAgent;
    private final GroupingAgent groupingAgent;

    public SearchResponse coordinate(String message, String model) {
        ExecutionTrace trace = new ExecutionTrace();
        try {
            // 1단계: 검색 조건 추출 (정규식이 실패하면 LLM)
            long start = System.nanoTime();
            SearchCriteriaExtractor.Extraction extraction;
            try {
                extraction = criteriaExtractor.extract(message, () -> chatClientProvider.get(model));
            } catch (RuntimeException e) {
                trace.record(Kind.CODE, "SearchCriteriaExtractor", "검색 조건 추출", "실패: " + e.getMessage(), start);
                throw e;   // 기록만 남기고 아래 catch에서 실패 응답으로
            }
            trace.record(extraction.usedLlm() ? Kind.LLM : Kind.CODE, "SearchCriteriaExtractor",
                    extraction.usedLlm() ? "LLM으로 검색 조건 추출" : "정규식으로 검색 조건 추출",
                    extraction.criteria().label(), start);

            // 2~7단계: 정해진 순서대로 전문 에이전트 호출
            TaskResult result = search(extraction.criteria(), trace);

            String summary = result.isEmpty() ? "조건에 맞는 항공편이 없습니다." : result.summaryLine();
            return SearchResponse.success(PATTERN, summary, List.of(result), trace);

        } catch (IllegalArgumentException e) {
            log.warn("Coordinator 검색 실패: {}", e.getMessage());
            return SearchResponse.fail(PATTERN, e.getMessage(), trace);
        } catch (Exception e) {
            log.error("Coordinator 검색 오류", e);
            return SearchResponse.fail(PATTERN, ChatClientProvider.describeError(e), trace);
        }
    }
```

1단계는 `trace.run(...)`이 아니라 `trace.record(..., start)`로 기록합니다. **결과를 보고 나서야** 정규식으로 끝났는지(CODE) LLM을 썼는지(LLM) 알 수 있기 때문입니다. 시작 시각만 미리 재 두고, 끝난 뒤 종류를 정해 기록합니다. 추출이 실패해도(LLM 서버 연결 실패, 출발·도착 없음) `"실패: ..."` 단계를 남기므로, 실패 응답의 실행 기록이 비어 있지 않고 **1단계에서 멈췄다**는 것이 보입니다.

### search(): 검색 조건 하나를 2~7단계로 처리

```java
    public TaskResult search(SearchCriteria criteria, ExecutionTrace trace) {
        String date = trace.run(Kind.CODE, "DateParserAgent", "날짜 변환",
                () -> dateParserAgent.parseDate(criteria.date()),
                parsed -> criteria.date() + " → " + parsed);

        String depAirportId = trace.run(Kind.CODE, "AirportCodeAgent", "출발 공항 코드 변환",
                () -> airportCodeAgent.getAirportCode(criteria.departure()),
                code -> criteria.departure() + " → " + code);
        String arrAirportId = trace.run(Kind.CODE, "AirportCodeAgent", "도착 공항 코드 변환",
                () -> airportCodeAgent.getAirportCode(criteria.arrival()),
                code -> criteria.arrival() + " → " + code);

        List<FlightInfoResponse> flights = trace.run(Kind.CODE, "ApiClientService", "공공데이터 포털 API 호출",
                () -> apiClientService.getFlightSchedule(depAirportId, arrAirportId, date),
                list -> list.size() + "편 조회");

        if (criteria.afterTime() != null) {
            List<FlightInfoResponse> source = flights;
            flights = trace.run(Kind.CODE, "TimeFilterAgent", criteria.afterTime() + " 이후 출발만 남기기",
                    () -> timeFilterAgent.filterAfterTime(source, timeFilterAgent.parseTime(criteria.afterTime())),
                    list -> source.size() + "편 → " + list.size() + "편");
        }
        // beforeTime 필터 ... 생략 (afterTime과 같은 모양)
        if (criteria.minPrice() != null || criteria.maxPrice() != null) {
            List<FlightInfoResponse> source = flights;
            flights = trace.run(Kind.CODE, "PriceFilterAgent", "가격 범위로 거르기",
                    () -> priceFilterAgent.filterByPriceRange(source, criteria.minPrice(), criteria.maxPrice()),
                    list -> source.size() + "편 → " + list.size() + "편");
        }

        List<FlightInfoResponse> filtered = flights;
        Map<String, List<FlightInfoResponse>> grouped = trace.run(Kind.CODE, "GroupingAgent", "항공사별 그룹핑",
                () -> groupingAgent.groupByAirline(filtered),
                map -> map.size() + "개 항공사");

        return TaskResult.of(criteria, grouped);
    }
}
```

**설계 포인트**

- **순서는 코드 그 자체입니다.** 위에서 아래로 읽으면 처리 순서가 보입니다. 필터는 그룹핑 전에 적용하므로, 필터 후 0편이 된 항공사가 빈 목록으로 남지 않습니다.
- **조건 분기도 코드가 결정합니다.** 시간·가격 조건이 없으면 필터 단계를 아예 건너뛰고, 실행 기록에도 남지 않습니다.
- **`source` 지역 변수**: 람다 안에서는 effectively final 변수만 쓸 수 있으므로, 바뀌기 전 목록을 `source`에 담아 넘깁니다. 덕분에 "12편 → 8편"처럼 전후 개수도 기록할 수 있습니다.
- **`search()`를 public으로 분리한 이유**: Step 11의 Orchestrator-Workers 패턴에서 `FlightSearchWorker`가 LLM이 만든 하위 작업마다 이 메서드를 **그대로 재사용**합니다. "검색 조건 하나를 처리하는 법"은 한곳에만 있습니다.

---

## Step 9-4: 실행 기록 — ExecutionTrace와 TraceStep

**파일**: `src/main/java/net/java21/flyschedule/dto/search/ExecutionTrace.java`, `TraceStep.java`

세 패턴을 비교하려면 "어느 단계를 **누가** 결정·실행했고 **얼마나** 걸렸는지"를 봐야 합니다. `ExecutionTrace`는 요청 하나의 실행 기록입니다.

```java
public class ExecutionTrace {

    private final long startedAt = System.nanoTime();
    private final List<TraceStep> steps = new ArrayList<>();

    /** 작업을 실행하고 걸린 시간과 결과 요약을 기록합니다. */
    public <T> T run(TraceStep.Kind kind, String actor, String title,
                     Supplier<T> action, Function<T, String> describe) {
        long start = System.nanoTime();
        try {
            T result = action.get();
            add(kind, actor, title, describe.apply(result), start, System.nanoTime());
            return result;
        } catch (RuntimeException e) {
            add(kind, actor, title, "실패: " + e.getMessage(), start, System.nanoTime());
            throw e;   // 기록만 하고 예외는 그대로 전달
        }
    }

    /** 이미 끝난 일을 기록합니다. (예: LLM이 요청해 실행된 Tool 호출) */
    public void record(TraceStep.Kind kind, String actor, String title, String detail) { /* ... */ }

    /** 시작 시각을 알고 있는 일을 끝난 뒤에 기록합니다. */
    public void record(TraceStep.Kind kind, String actor, String title, String detail, long startNanos) { /* ... */ }

    /** 시작·종료 시각을 알고 있는 일을 기록합니다. (예: Tool 실행 구간) */
    public void record(TraceStep.Kind kind, String actor, String title, String detail,
                       long startNanos, long endNanos) { /* ... */ }

    public synchronized List<TraceStep> steps() {
        return List.copyOf(steps);
    }

    private synchronized void add(TraceStep.Kind kind, String actor, String title, String detail,
                                  long start, long end) {
        steps.add(new TraceStep(steps.size() + 1, kind, actor, title, detail,
                toMs(start - startedAt), toMs(end - start), threadName()));
    }

    /** 가상 스레드는 이름이 비어 있으므로 "virtual-{id}"로 표시합니다. */
    private static String threadName() {
        Thread thread = Thread.currentThread();
        return thread.isVirtual() ? "virtual-" + thread.threadId() : thread.getName();
    }
    // ... 생략
}
```

| 메서드 | 언제 쓰나 | 이 프로젝트의 예 |
|--------|-----------|------------------|
| `run(kind, actor, title, action, describe)` | 실행과 기록을 함께 | 2~7단계의 전문 에이전트 호출 |
| `record(..., startNanos)` | 실행이 끝난 뒤에야 종류를 알 때 | 1단계 (정규식이냐 LLM이냐) |
| `record(..., startNanos, endNanos)` | 다른 곳에서 이미 실행된 일의 구간을 알 때 | Step 10에서 LLM이 부른 Tool 호출 |
| `record(...)` | 시각 정보 없이 이미 끝난 일 | (길이 0인 단계로 기록) |

- **`run()`의 람다 두 개**: `action`은 실제 일, `describe`는 결과를 한 줄로 요약하는 함수입니다. 에이전트 코드는 기록을 전혀 모르고, 기록은 Coordinator가 맡습니다.
- **실패도 기록합니다.** 예외가 나면 "실패: 알 수 없는 공항입니다: 서울"처럼 남기고 다시 던집니다. 그래서 실패 응답에도 **어디까지 진행했는지** 보입니다.
- **`synchronized`**: Coordinator는 한 스레드에서만 기록하지만, Step 11의 Worker는 여러 가상 스레드에서 동시에 기록합니다. 같은 클래스를 쓰기 위해 처음부터 동기화했습니다.

`TraceStep`의 `kind`는 실행 기록에서 가장 중요한 값입니다.

```java
public enum Kind {
    CODE,    // 개발자가 작성한 코드가 순서를 정해 실행
    LLM,     // LLM 호출 (판단·계획·요약)
    TOOL,    // LLM이 호출을 요청해 실행된 Tool
    WORKER   // Orchestrator가 맡긴 하위 작업
}
```

**화면에서 보이는 것**: `/` 화면의 각 패턴 패널은 상단에 "전체 ○○ms · LLM ○회 · Tool ○회 · Worker ○개 · 코드 단계 ○개"를 보여 주고, 그 아래 **실행 기록 타임라인**에 단계마다 순번, `actor`, `title`, 걸린 시간, `detail`, 스레드 이름, 그리고 `startMs`·`durationMs`로 그린 막대를 표시합니다.
Coordinator 패널은 대부분 **CODE 단계만** 보이고, 막대 대부분을 API 호출이 차지하는 것을 확인할 수 있습니다.

---

## Step 9-5: 응답 DTO — SearchResponse, TaskResult, FlightView

**파일**: `src/main/java/net/java21/flyschedule/dto/search/`

세 패턴이 **같은 응답 형식**을 쓰므로 화면과 테스트를 하나로 만들 수 있습니다.

```java
public record SearchResponse(
        String pattern,            // "coordinator", "tool-calling", "orchestrator"
        boolean success,
        String message,            // 사용자에게 보여 줄 메시지 (LLM 답변 또는 안내 문구)
        List<TaskResult> results,  // Orchestrator-Workers는 하위 작업마다 하나
        List<TraceStep> trace,
        long elapsedMs
) {
    public static SearchResponse success(String pattern, String message, List<TaskResult> results,
                                         ExecutionTrace trace) { /* ... */ }

    public static SearchResponse fail(String pattern, String message, ExecutionTrace trace) { /* results는 빈 목록 */ }
}
```

```java
public record TaskResult(
        String label,                                  // "광주 → 제주 · 2026-03-25"
        SearchCriteria criteria,
        Map<String, List<FlightView>> flightsByAirline,
        int totalCount,
        FlightView cheapest
) {
    public static TaskResult of(SearchCriteria criteria, Map<String, List<FlightInfoResponse>> grouped) { /* ... */ }

    /** LLM에게 결과를 요약해 줄 때도 쓰는 한 줄 설명 */
    public String summaryLine() {
        // "광주 → 제주 · 2026-03-25: 5편, 최저가 제주항공 7C1402 10:30 출발 45,000원"
    }
}
```

`FlightView`는 화면용 항공편 한 건입니다. 공공데이터 포털의 `"202603250955"`를 `"09:55"`로 바꾸고, 운임 0원(정보 없음)은 `null`로 바꿉니다.
Coordinator는 LLM 답변이 없으므로 `summaryLine()`을 그대로 `message`로 씁니다.

---

## Step 9-6: SearchController — API 엔드포인트

**파일**: `src/main/java/net/java21/flyschedule/controller/SearchController.java`

```java
@Slf4j
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final FlightSearchCoordinator coordinator;
    private final ToolCallingSearchService toolCallingService;
    private final FlightSearchOrchestrator orchestrator;

    @PostMapping("/coordinator")
    public SearchResponse coordinator(@RequestBody SearchRequest request) {
        return handle(FlightSearchCoordinator.PATTERN, request, coordinator::coordinate);
    }

    // /tool-calling (Step 10), /orchestrator (Step 11) ... 생략

    private SearchResponse handle(String pattern, SearchRequest request,
                                  BiFunction<String, String, SearchResponse> searcher) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            return SearchResponse.fail(pattern, "검색할 문장을 입력해 주세요.", new ExecutionTrace());
        }
        return searcher.apply(request.message().trim(), request.model());
    }
}
```

요청은 `SearchRequest(String message, String model)`이며, `model`은 `"ollama"` 또는 `"gemini"`입니다(생략하면 Ollama). 빈 문장 검사를 컨트롤러 한곳에서 하므로 세 패턴의 서비스는 입력 검증을 반복하지 않습니다.

### 실행해 보기

```bash
curl -X POST http://localhost:8080/api/search/coordinator \
  -H "Content-Type: application/json" \
  -d '{"message": "내일 광주에서 제주 오전 10시 이후 8만원 이하", "model": "ollama"}'
```

응답 예시입니다(오늘이 2026-03-24라고 가정, 항공편 목록과 `cheapest`의 일부 필드는 줄였습니다).

```json
{
  "pattern": "coordinator",
  "success": true,
  "message": "광주 → 제주 · 2026-03-25 · 10:00 이후 · 8만원 이하: 5편, 최저가 제주항공 7C1402 10:30 출발 45,000원",
  "results": [
    {
      "label": "광주 → 제주 · 2026-03-25 · 10:00 이후 · 8만원 이하",
      "criteria": {
        "departure": "광주", "arrival": "제주", "date": "2026-03-25",
        "afterTime": "10:00", "beforeTime": null, "minPrice": null, "maxPrice": 80000
      },
      "flightsByAirline": {
        "아시아나항공": [
          { "flightId": "OZ8143", "airline": "아시아나항공", "departureAirport": "광주",
            "arrivalAirport": "제주", "departureTime": "13:20", "arrivalTime": "14:10", "price": 57900 }
        ],
        "제주항공": [
          { "flightId": "7C1402", "airline": "제주항공", "departureAirport": "광주",
            "arrivalAirport": "제주", "departureTime": "10:30", "arrivalTime": "11:20", "price": 45000 }
        ]
      },
      "totalCount": 5,
      "cheapest": { "flightId": "7C1402", "airline": "제주항공", "departureTime": "10:30", "price": 45000 }
    }
  ],
  "trace": [
    { "order": 1, "kind": "CODE", "actor": "SearchCriteriaExtractor", "title": "정규식으로 검색 조건 추출",
      "detail": "광주 → 제주 · 2026-03-25 · 10:00 이후 · 8만원 이하", "startMs": 0, "durationMs": 1, "thread": "http-nio-8080-exec-1" },
    { "order": 2, "kind": "CODE", "actor": "DateParserAgent", "title": "날짜 변환",
      "detail": "2026-03-25 → 20260325", "startMs": 1, "durationMs": 0, "thread": "http-nio-8080-exec-1" },
    { "order": 3, "kind": "CODE", "actor": "AirportCodeAgent", "title": "출발 공항 코드 변환",
      "detail": "광주 → NAARKJJ", "startMs": 1, "durationMs": 0, "thread": "http-nio-8080-exec-1" },
    { "order": 4, "kind": "CODE", "actor": "AirportCodeAgent", "title": "도착 공항 코드 변환",
      "detail": "제주 → NAARKPC", "startMs": 1, "durationMs": 0, "thread": "http-nio-8080-exec-1" },
    { "order": 5, "kind": "CODE", "actor": "ApiClientService", "title": "공공데이터 포털 API 호출",
      "detail": "12편 조회", "startMs": 1, "durationMs": 412, "thread": "http-nio-8080-exec-1" },
    { "order": 6, "kind": "CODE", "actor": "TimeFilterAgent", "title": "10:00 이후 출발만 남기기",
      "detail": "12편 → 8편", "startMs": 413, "durationMs": 0, "thread": "http-nio-8080-exec-1" },
    { "order": 7, "kind": "CODE", "actor": "PriceFilterAgent", "title": "가격 범위로 거르기",
      "detail": "8편 → 5편", "startMs": 413, "durationMs": 0, "thread": "http-nio-8080-exec-1" },
    { "order": 8, "kind": "CODE", "actor": "GroupingAgent", "title": "항공사별 그룹핑",
      "detail": "3개 항공사", "startMs": 413, "durationMs": 0, "thread": "http-nio-8080-exec-1" }
  ],
  "elapsedMs": 414
}
```

**읽는 법**: 모든 단계가 `CODE`이고 LLM 호출은 0회입니다. 전체 시간 대부분이 공공데이터 포털 API 호출(5번)입니다.
정규식이 처리하지 못하는 문장(예: "이번 주 금요일 서울에서 제주도")을 보내면 1번 단계만 `"kind": "LLM"`, `"title": "LLM으로 검색 조건 추출"`로 바뀌고 2번 이후는 그대로입니다. 브라우저에서 `http://localhost:8080/`을 열면 같은 결과를 타임라인으로 볼 수 있습니다.

---

## Step 9-7: 에러 처리

Coordinator의 에러 처리는 **예외 종류에 따라 두 갈래**입니다. 어느 경우든 HTTP 500이 아니라 `success: false`인 `SearchResponse`를 돌려주고, 그때까지의 실행 기록을 함께 담습니다.

| 예외 | 뜻 | 응답 메시지 | 발생 위치 |
|------|----|-------------|-----------|
| `IllegalArgumentException` | **사용자가 고칠 수 있는 입력 문제** | 예외 메시지를 그대로 | `SearchCriteriaExtractor`(경로 없음), `DateParserAgent`, `AirportCodeAgent`, `TimeFilterAgent`, `ChatClientProvider.get()`(Gemini 키 없음) |
| `ExternalApiException` | **공공데이터 포털 문제** (서비스 키 없음, HTTP 오류, `resultCode != "00"`) | 예외 메시지를 그대로 (`describeError`가 먼저 확인) | `ApiClientService` |
| 그 밖의 `Exception` | **시스템 문제** (LLM 서버 연결 실패, 잘못된 API 키 등) | `ChatClientProvider.describeError(e)` | LLM 호출 |

```java
} catch (IllegalArgumentException e) {
    return SearchResponse.fail(PATTERN, e.getMessage(), trace);           // "알 수 없는 공항입니다: 서울"
} catch (Exception e) {
    return SearchResponse.fail(PATTERN, ChatClientProvider.describeError(e), trace);
}
```

전문 에이전트들이 입력 오류를 일부러 `IllegalArgumentException`과 **사람이 읽을 수 있는 메시지**로 알리도록 만든 이유가 여기 있습니다(Step 8). Coordinator는 메시지를 고치지 않고 그대로 사용자에게 전달합니다.

### describeError: 기술적인 예외를 안내 문장으로

**파일**: `src/main/java/net/java21/flyschedule/config/ChatClientProvider.java`

```java
public static String describeError(Exception e) {
    if (e instanceof net.java21.flyschedule.service.api.ExternalApiException) {
        return e.getMessage(); // 공공데이터 포털 오류는 LLM 오류와 구분해 그대로 보여 준다
    }
    Throwable cause = e;
    while (cause != null) {   // 원인 예외(cause)를 따라 내려가며 확인
        if (cause instanceof org.springframework.web.client.ResourceAccessException
                || cause instanceof java.net.ConnectException) {
            return "LLM 서버에 연결할 수 없습니다. Ollama가 실행 중인지(spring.ai.ollama.base-url) 확인하거나 다른 모델을 선택하세요.";
        }
        String message = cause.getMessage();
        if (message != null && message.contains("API key not valid")) {
            return "Gemini API 키가 올바르지 않습니다. GOOGLE_GENAI_API_KEY를 확인하세요.";
        }
        cause = cause.getCause();
    }
    return "LLM 호출 중 오류가 발생했습니다: " + e.getMessage();
}
```

Spring AI의 예외는 여러 겹으로 감싸여 올라오므로 `getCause()`를 따라 내려가며 진짜 원인을 찾습니다. 학습자가 가장 자주 겪는 두 가지(Ollama 서버 꺼짐, Gemini 키 오류)를 해결 방법이 담긴 문장으로 바꿉니다. 맨 앞의 `ExternalApiException` 검사가 없으면, 공공데이터 포털 호출 실패(원인이 `ResourceAccessException`)도 "LLM 서버에 연결할 수 없습니다"로 잘못 안내됩니다.

### 재시도 설정: spring.ai.retry

```yaml
# application.yml
spring:
  http:
    client:
      read-timeout: 300s     # 작은 로컬 모델은 응답이 느리므로 5분

# 기본값(최대 10회, 대기 간격 5배씩 증가)은 LLM 서버가 꺼져 있을 때 응답이 수십 분 멈추므로 실습용으로 줄인다
spring.ai.retry:
  max-attempts: 2
  backoff:
    initial-interval: 1s
    max-interval: 5s
```

Spring AI는 모델 호출이 실패하면 자동으로 재시도합니다. 기본값은 최대 10회, 대기 간격을 5배씩 늘려 가므로, Ollama 서버가 꺼져 있으면 사용자는 **오류 메시지를 보기까지 아주 오래** 기다려야 합니다.
실습에서는 **2회까지만, 1~5초 간격**으로 줄여 빨리 실패하고 `describeError()`의 안내를 보여 주도록 했습니다. 운영 환경에서는 일시적인 네트워크 오류를 견디도록 횟수를 조금 늘리되, 전체 대기 시간의 상한을 함께 계산해야 합니다.

### 외부 API 실패는?

"항공편이 없다"와 "API를 호출하지 못했다"는 다른 상황입니다. 호출 실패를 빈 목록으로 숨기면 사용자는 항공편이 없다고 오해하므로, `ApiClientService`는 실패를 **`ExternalApiException`**으로 알립니다.

- 서비스 키가 비어 있으면 API를 부르지 않고 "공공데이터 포털 서비스 키가 설정되지 않았습니다. 환경 변수 DATA_GO_KR_SERVICE_KEY를 설정하세요."
- HTTP 오류·타임아웃이면 "공공데이터 포털 API를 호출하지 못했습니다. 잠시 후 다시 시도하세요."
- 응답의 `resultCode`가 `"00"`이 아니면 "공공데이터 포털 API 오류: (응답 메시지)"

이때 Coordinator는 `success: false`와 위 메시지로 응답하고, 실행 기록의 "공공데이터 포털 API 호출" 단계에 `"실패: ..."`가 남습니다. 반대로 API가 정상 응답했는데 항공편이 0건이면 `success: true`, `"조건에 맞는 항공편이 없습니다."`입니다. `ApiClientServiceTest`가 `MockRestServiceServer`로 이 세 가지 실패를 검증합니다.

> 📌 `ApiClientService`는 한 번에 `MAX_ROWS = 500`건을 요청합니다. 김포→제주처럼 하루 100편이 넘는 노선이 있어 100으로 두면 뒤쪽 항공편이 잘리기 때문입니다. 또 생성자로 Spring Boot가 만든 `RestClient.Builder`를 받아야 `spring.http.client.read-timeout` 설정이 적용됩니다.

---

## Step 9-8: Coordinator 테스트

LLM과 외부 API는 느리고 결과가 바뀌므로, 테스트에서는 **이 두 가지만 가짜(mock)로 바꾸고 전문 에이전트는 실제 객체**를 씁니다.
그러면 "코드가 정한 순서"와 "에이전트끼리 주고받는 값"을 실제와 같은 조건으로 검증할 수 있습니다.

### FlightSearchCoordinatorTest

**파일**: `src/test/java/net/java21/flyschedule/service/coordinator/FlightSearchCoordinatorTest.java`

```java
@BeforeEach
void setUp() {
    apiClientService = mock(ApiClientService.class);                 // 가짜 외부 API
    chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);         // 가짜 LLM
    ChatClientProvider provider = mock(ChatClientProvider.class);
    when(provider.get(anyString())).thenReturn(chatClient);
    when(apiClientService.getFlightSchedule(anyString(), anyString(), anyString())).thenReturn(apiFlights);

    coordinator = new FlightSearchCoordinator(provider, new SearchCriteriaExtractor(),
            new DateParserAgent(), new AirportCodeAgent(), apiClientService,          // 실제 에이전트
            new TimeFilterAgent(), new PriceFilterAgent(), new GroupingAgent());
}

@Test
@DisplayName("코드가 정한 순서대로 에이전트를 호출하고, 모든 단계가 CODE로 기록된다")
void fixedOrderAllCode() {
    SearchResponse response = coordinator.coordinate("내일 광주에서 제주로 가는 항공편", "ollama");

    assertThat(response.trace()).extracting(TraceStep::actor).containsExactly(
            "SearchCriteriaExtractor", "DateParserAgent", "AirportCodeAgent", "AirportCodeAgent",
            "ApiClientService", "GroupingAgent");
    assertThat(response.trace()).extracting(TraceStep::kind).containsOnly(Kind.CODE);
}

@Test
@DisplayName("정규식이 실패하면 1단계만 LLM으로 처리하고, 나머지 순서는 그대로다")
void llmOnlyForExtraction() {
    when(chatClient.prompt().system(anyString()).user(anyString()).call().entity(SearchCriteria.class))
            .thenReturn(new SearchCriteria("김포", "제주", "2026-03-27", null, null, null, null));

    SearchResponse response = coordinator.coordinate("이번 주 금요일 서울에서 제주도", "gemini");

    assertThat(response.trace().get(0).kind()).isEqualTo(Kind.LLM);
    assertThat(response.trace().subList(1, response.trace().size()))
            .extracting(TraceStep::kind).containsOnly(Kind.CODE);
    verify(apiClientService).getFlightSchedule("NAARKSS", "NAARKPC", "20260327");
}
```

- **`RETURNS_DEEP_STUBS`**: `chatClient.prompt().system(...).user(...).call().entity(...)`처럼 이어지는 호출 사슬 끝의 값만 정해 줄 수 있습니다. 실제 LLM 없이 "LLM이 이런 JSON을 돌려줬다"는 상황을 만듭니다.
- **`containsExactly`로 순서 검증**: Coordinator 패턴의 핵심인 "순서가 고정되어 있다"를 그대로 테스트로 옮겼습니다.
- **`verify(...)`로 에이전트 간 값 전달 검증**: "김포"와 "2026-03-27"이 `NAARKSS`, `20260327`로 바뀌어 API에 넘어갔는지 확인합니다.

이 밖에 다음 동작도 테스트합니다.

| 테스트 메서드 | 확인 내용 |
|---------------|-----------|
| `callsApiWithConvertedValues` | "내일 광주에서 제주" → `("NAARKJJ", "NAARKPC", 내일 날짜)`로 API 호출 |
| `filtersOnlyWhenRequested` | 시간·가격 조건이 있을 때만 필터 단계 추가, 4편 → 2편, 최저가 7C1402 |
| `regexPathIgnoresModelConfig` | Gemini 키가 없어도 정규식 경로는 성공, LLM 경로는 키 안내 메시지 |
| `unknownAirport` | 알 수 없는 공항이면 "실패"가 기록되고 API는 호출되지 않음 |
| `failResponse` | LLM도 공항을 못 찾으면 `success: false`와 안내 메시지, 실행 기록에 1단계 "실패" 한 줄 |
| `emptyResult` | API 결과가 없으면 성공이지만 "조건에 맞는 항공편이 없습니다." |

### SearchCriteriaExtractorTest

**파일**: `src/test/java/net/java21/flyschedule/service/ai/SearchCriteriaExtractorTest.java`

정규식 파싱은 `parseWithRegex()`(패키지 접근 범위)를 직접 호출해 표 형태로 검증합니다.

```java
@ParameterizedTest
@CsvSource(nullValues = "null", value = {
        "'광주에서 제주 10시 이후', '10:00', null",
        "'광주에서 제주 오후 2시 30분 이후', '14:30', null",
        "'광주에서 제주 오후 6시까지', null, '18:00'",
        "'광주에서 제주 오전 12시 이후', '00:00', null"
        // ... 생략
})
@DisplayName("시간 조건을 24시간 HH:mm으로 바꾼다")
void times(String message, String after, String before) { /* ... */ }

@ParameterizedTest
@CsvSource({ "'제주 가는 항공편'", "'서울에서 부산'", "'김포나 광주에서 제주'" })
@DisplayName("공항이 정확히 두 개가 아니면 정규식은 포기한다 (LLM에 맡김)")
void giveUpWhenNotExactlyTwoAirports(String message) {
    assertThat(extractor.parseWithRegex(message)).isEmpty();
}

@Test
@DisplayName("정규식으로 충분하면 LLM을 호출하지 않는다")
void regexSkipsLlm() {
    SearchCriteriaExtractor.Extraction extraction = extractor.extract("내일 광주에서 제주", () -> chatClient);

    assertThat(extraction.usedLlm()).isFalse();
    verifyNoInteractions(chatClient);   // LLM을 한 번도 건드리지 않았는지 확인
}
```

`verifyNoInteractions(chatClient)`는 "정규식 경로에서는 LLM 비용이 0"이라는 설계 의도를 테스트로 못 박습니다. `giveUpOnComplexDates()`는 "다음 주 금요일", "이번 주 토요일", "3일 뒤", "3월 25일"이 들어간 문장에서 정규식이 포기하는지 확인합니다.
실행 기록 자체는 `ExecutionTraceTest`(순서, 실패 기록, 스레드 안전성), API 경로는 `SearchControllerTest`(`@WebMvcTest`, 빈 문장 검사)에서 따로 검증합니다.

```bash
./mvnw test -Dtest='FlightSearchCoordinatorTest,SearchCriteriaExtractorTest,ExecutionTraceTest,SearchControllerTest'
```

---

## 일반적인 문제 해결

### 문제 1: "LLM 서버에 연결할 수 없습니다"

정규식으로 처리되지 않는 문장이라 LLM을 호출했는데 Ollama에 연결하지 못한 경우입니다. `spring.ai.ollama.base-url`의 서버가 실행 중인지 확인하세요.
"내일 광주에서 제주"처럼 공항 두 개가 들어간 문장은 LLM 없이 동작하므로, 먼저 이런 문장으로 나머지 단계가 정상인지 확인하면 원인을 좁힐 수 있습니다.

### 문제 2: "Gemini API 키가 설정되지 않았습니다"

`model`을 `"gemini"`로 보냈는데 `GOOGLE_GENAI_API_KEY` 환경 변수가 없는 경우입니다. 키를 설정하거나 `"ollama"`를 쓰세요. 정규식 경로는 키가 없어도 동작합니다(`Supplier<ChatClient>` 덕분).

### 문제 3: LLM 경로에서 "알 수 없는 공항입니다: 서울"

LLM이 공항 대신 도시 이름을 돌려준 경우입니다. 실행 기록의 1단계 `detail`에서 LLM이 뽑은 조건을 확인하세요. `SearchCriteria`의 `@JsonPropertyDescription`이나 시스템 프롬프트에 "서울은 김포로" 같은 규칙을 추가하면 개선할 수 있습니다(실습 과제 3).

### 문제 4: LLM 경로에서 "날짜 형식이 올바르지 않습니다"

작은 모델이 `date`에 "내일"이나 "2026/03/27"을 넣은 경우입니다. `DateParserAgent`는 "내일"은 처리하지만 `/` 형식은 처리하지 않습니다. 실행 기록의 1단계와 2단계 `detail`을 비교해 보세요.

### 문제 5: 조건을 넣었는데 필터 단계가 실행 기록에 없음

정규식이 시간·가격 표현을 인식하지 못한 경우입니다. 예를 들어 "10시 넘어서"는 `TIME_PATTERN`의 조건어(`이후|부터|이전|까지|전`)에 없으므로 시간 조건이 `null`이 됩니다. 1단계 `detail`(검색 조건 라벨)을 확인하세요.

---

## 실습 과제

1. **(기본) "다음 주 월요일" 처리하기**: 지금은 "다음 주 월요일"이 `COMPLEX_DATE_PATTERN`에 걸려 LLM으로 넘어갑니다. `parseWithRegex()`가 `COMPLEX_DATE_PATTERN` 검사 **전에** "다음 주 월요일"~"다음 주 일요일"을 직접 계산하도록 고치세요(힌트: `LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))`, "다음 주"의 뜻을 먼저 정하세요). `giveUpOnComplexDates()`의 해당 행을 옮기고 새 매개변수화 테스트를 추가한 뒤, `FlightSearchCoordinatorTest` 방식으로 이 문장이 LLM 없이(`Kind.CODE`) 처리되는지 확인하세요.
2. **(기본) 시간 표현 늘리기**: "10시 넘어서", "오후 3시 지나서"를 "이후"로 인식하도록 `TIME_PATTERN`을 고치고 `times()` 테스트에 행을 추가하세요. "오전 10시"의 "전"이 "이전"으로 잘못 인식되지 않는지도 테스트하세요.
3. **(도전) LLM 결과 보정 단계 추가**: LLM이 "서울"을 돌려주면 "김포"로 바꾸는 보정 로직을 어디에 둘지 결정하세요. (a) 시스템 프롬프트, (b) `SearchCriteria`의 설명, (c) LLM 호출 뒤 코드 중 하나를 골라 구현하고, 선택 이유와 실행 기록에 어떻게 남길지(`trace.record(...)`)를 설명하세요.
4. **(도전) 도착 시각 필터 단계 추가**: "오후 6시 전에 도착"처럼 도착 시각 조건을 지원하도록 `SearchCriteria`에 `arriveBefore` 필드(`@JsonPropertyDescription` 포함)를 추가하고, `TimeFilterAgent`에 필터 메서드를, `FlightSearchCoordinator.search()`에 조건부 단계를 추가하세요. `filtersOnlyWhenRequested()`를 참고해 새 단계가 조건이 있을 때만 실행 기록에 나타나는지 테스트하세요.

---

## 요약

1. **Coordinator 패턴**은 **코드가 처리 순서를 정하는** 워크플로입니다. 업계 용어로는 Anthropic의 workflow(prompt chaining), Azure의 sequential orchestration에 해당합니다.
2. LLM은 1단계 `SearchCriteriaExtractor`에서 **정규식이 실패했을 때만** 쓰이고, 다음 단계를 결정하지 않습니다. 정규식은 공항이 **정확히 두 개**일 때만 결과를 냅니다.
3. LLM 결과는 `.call().entity(SearchCriteria.class)`로 **Structured Output**을 받으며, `@JsonPropertyDescription`이 JSON Schema 설명이 되어 다음 단계가 받을 형식을 알려 줍니다.
4. `Supplier<ChatClient>`로 모델을 **필요할 때만** 꺼내므로, 정규식 경로는 모델 설정과 무관하게 동작합니다.
5. `FlightSearchCoordinator.search()`는 2~7단계를 처리하며, Step 11의 Worker가 그대로 재사용합니다.
6. `ExecutionTrace`는 단계마다 **종류(CODE·LLM·TOOL·WORKER)**, 실행자, 걸린 시간, 스레드를 기록해 세 패턴을 화면에서 비교할 수 있게 합니다.
7. 입력 오류(`IllegalArgumentException`)와 공공데이터 포털 오류(`ExternalApiException`)는 메시지를 그대로, LLM 오류는 `describeError()`로 바꿔 `success: false` 응답에 담습니다. `spring.ai.retry`는 빨리 실패하도록 줄여 두었습니다.

---

## 체크리스트

- [ ] Coordinator, Tool Calling 에이전트, Orchestrator-Workers를 "다음 단계를 누가 결정하는가"로 구분할 수 있음
- [ ] `SearchCriteriaExtractor`가 LLM을 부르는 조건(공항이 두 개가 아님, 계산이 필요한 날짜)을 설명할 수 있음
- [ ] `@JsonPropertyDescription`이 Structured Output에서 하는 역할을 설명할 수 있음
- [ ] `Supplier<ChatClient>`를 쓰는 이유를 설명할 수 있음
- [ ] `ExecutionTrace.run()`과 `record()`의 쓰임새 차이를 설명할 수 있음
- [ ] `curl`로 `POST /api/search/coordinator`를 호출하고 실행 기록을 읽을 수 있음
- [ ] `FlightSearchCoordinatorTest`, `SearchCriteriaExtractorTest`가 통과함

---

## 다음 단계

[Step 10: Tool Calling 에이전트](step-10-tool-calling-agent.md)에서는 순서를 코드가 아니라 **LLM이 직접** 정하게 합니다. LLM에게 Tool 목록만 주고, 어떤 Tool을 언제 부를지 스스로 결정하게 한 뒤 Coordinator와 실행 기록을 비교해 봅니다.

---

## 참고 자료

- [Anthropic - Building effective agents](https://www.anthropic.com/engineering/building-effective-agents)
- [Microsoft Azure Architecture Center - AI agent orchestration patterns](https://learn.microsoft.com/azure/architecture/ai-ml/guide/ai-agent-design-patterns)
- [Spring AI Reference - Structured Output Converter](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html)
- [Spring AI Reference - ChatClient API](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [Mockito - RETURNS_DEEP_STUBS](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html#RETURNS_DEEP_STUBS)
