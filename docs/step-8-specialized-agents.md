# Step 8: 전문 에이전트 설계와 구현

Step 6과 7에서 Tool을 만들면서 `DateParserAgent`, `AirportCodeAgent`, `GroupingAgent` 같은 `service/agent` 클래스를 여러 번 만났습니다. 이번 Step에서는 이 클래스들을 하나씩 뜯어보며 "왜 이렇게 잘게 나눴는가"를 정리하고, Spring 없이 단위 테스트하는 방법을 익힙니다. 여기서 정리한 부품은 Step 9~11의 세 가지 검색 패턴이 그대로 함께 씁니다.

**이 Step에서 만들 것**: 전문 에이전트 다섯 개(날짜 변환, 공항 코드 변환, 시간 필터, 가격 필터, 그룹핑)의 설계 의도를 코드로 확인하고, LLM·네트워크 없이 몇 초 안에 끝나는 단위 테스트를 실행·추가합니다.

```bash
./mvnw test -Dtest='*AgentTest'
```

```text
[INFO] Tests run: ..., Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## 학습 목표

이 Step을 마치면 다음을 할 수 있습니다.

1. 이 교재의 전문 에이전트가 무엇이고 왜 LLM을 쓰지 않는지, LLM 기반 에이전트와 비교해 설명할 수 있습니다.
2. 기능을 단일 책임 단위로 나누면 테스트와 재사용이 쉬워지는 이유를 이 프로젝트의 예로 설명할 수 있습니다.
3. `DateParserAgent`, `AirportCodeAgent`, `TimeFilterAgent`, `PriceFilterAgent`, `GroupingAgent`의 코드를 읽고 입력 검증·실패 처리 방식을 설명할 수 있습니다.
4. 전문 에이전트를 Spring 컨텍스트 없이 `new`로 만들어 매개변수화 테스트를 작성할 수 있습니다.
5. 이 교재의 전문 에이전트, MCP, A2A(Agent2Agent) 프로토콜의 차이를 구분할 수 있습니다.

---

## 전체 흐름

다섯 전문 에이전트가 항공편 검색 한 번에서 맡는 일입니다.

```text
[검색 조건: 출발 "광주", 도착 "제주", 날짜 "2026-03-25", 10:00 이후, 8만 원 이하]
       ↓
① DateParserAgent.parseDate("2026-03-25")               → "20260325"
② AirportCodeAgent.getAirportCode("광주")                → "NAARKJJ"
③ AirportCodeAgent.getAirportCode("제주")                → "NAARKPC"
       ↓
[ApiClientService.getFlightSchedule("NAARKJJ", "NAARKPC", "20260325")]  → 항공편 목록
       ↓
④ TimeFilterAgent.filterAfterTime(목록, 10:00)           → 10시 이후 출발만
⑤ PriceFilterAgent.filterByPriceRange(목록, null, 80000) → 8만 원 이하만
⑥ GroupingAgent.groupByAirline(목록)                     → {"대한항공": [...], "제주항공": [...]}
```

이 순서를 누가 정하는지는 이 Step의 관심사가 아닙니다. 같은 부품을 세 가지 패턴이 함께 씁니다.

```text
[전문 에이전트 (service/agent)]
       ↓ 사용
[Step 9  Coordinator 패턴]       코드가 순서를 정해 차례로 호출
[Step 10 Tool Calling 에이전트]  LLM이 고른 Tool(FlightSearchTool)이 FlightSearchAgent를 통해 호출
[Step 11 Orchestrator-Workers]  Worker가 Coordinator의 검색 단계를 그대로 재사용
```

---

## 전문 에이전트란?

### 이 교재에서 말하는 "에이전트"

AI 분야에서 **에이전트(agent)** 는 보통 "LLM이 스스로 판단해 Tool을 고르고 행동하는 프로그램"을 뜻합니다. 그런데 이 프로젝트의 `service/agent` 패키지 클래스는 LLM을 전혀 쓰지 않습니다. 이 교재는 이들을 **전문 에이전트**(또는 전문 컴포넌트)라고 부릅니다. 전문 에이전트는 한 가지 일만 하는 결정적(deterministic) 클래스, 곧 같은 입력에 언제나 같은 결과를 내는 클래스입니다.

| 구분 | 이 교재의 전문 에이전트 | LLM 기반 에이전트 |
|------|-------------------------|-------------------|
| 판단 주체 | 개발자가 작성한 코드 | LLM |
| 결과 | 같은 입력 → 항상 같은 결과 | 실행할 때마다 달라질 수 있음 |
| 속도·비용 | 밀리초 단위, 비용 없음 | 수 초 단위, 토큰 비용 발생 |
| 이 프로젝트의 예 | `DateParserAgent`, `AirportCodeAgent` | Step 10의 Tool Calling 에이전트 |

여행사의 전담 직원에 비유할 수 있습니다. 날짜 담당은 "내일"을 날짜로 바꾸는 일만, 공항 담당은 "광주"를 공항 코드로 바꾸는 일만 합니다. 누가 어떤 순서로 일을 맡길지는 Step 9~11에서 세 가지 방식으로 정합니다.

> **주의**: 이 교재에서 "전문 에이전트"는 언제나 LLM을 쓰지 않는 결정적 클래스를 뜻합니다. LLM이 판단하는 에이전트는 Step 10에서 Tool Calling 에이전트라는 이름으로 따로 다룹니다.

### 왜 기능을 잘게 나눌까요?

날짜 변환, 공항 코드 변환, 시간·가격 필터, 그룹핑을 `FlightSearchService` 하나에 모두 넣을 수도 있습니다. 하지만 나누면 세 가지를 얻습니다.

| 이점 | 설명 | 이 프로젝트의 예 |
|------|------|------------------|
| 단일 책임(SRP) | 클래스가 바뀌는 이유가 하나뿐 | 공항이 추가되면 `AirportCodeAgent`만 수정 |
| 테스트 용이성 | 의존성이 없어 `new`로 만들어 바로 검증 | `new TimeFilterAgent().parseTime("오후 2시")` |
| 재사용 | 여러 패턴이 같은 부품을 공유 | Coordinator, Tool, Worker가 같은 에이전트 사용 |

재사용이 이 교재에서 특히 중요합니다. Step 9~11은 "누가 다음 단계를 결정하는가"만 다르고, 실제로 날짜를 바꾸고 항공편을 거르는 부품은 같습니다. 그래서 세 패턴의 결과를 공정하게 비교할 수 있습니다.

코드로 보면 전문 에이전트는 그냥 `@Service`가 붙은 Spring Bean입니다. 다만 일반 Service와 성격이 다릅니다.

| 항목 | 일반적인 Service | 전문 에이전트 |
|------|------------------|---------------|
| 책임 | 도메인 기능 여러 개를 묶기도 함 | 변환·필터 하나만 담당 |
| 의존성 | Repository 등 많음 | 대부분 없음 (`new`로 생성 가능) |
| 입력·출력 | 엔티티, DTO | 문자열, `List<FlightInfoResponse>` 같은 단순한 값 |
| 호출하는 쪽 | Controller | Coordinator, Tool, Worker |

### A2A 프로토콜과의 차이

**A2A(Agent2Agent) 프로토콜**은 Google이 2025년에 발표한 에이전트 간 통신 표준이며, 지금은 Linux Foundation이 관리합니다. 이름이 비슷해 헷갈리기 쉬우므로 분명히 구분합니다.

> **주의**: A2A 프로토콜은 이 프로젝트에 구현되어 있지 않습니다. 이 프로젝트의 전문 에이전트끼리는 Java 메서드 호출로만 협력합니다.

A2A에서 각 에이전트는 독립 실행되는 서비스입니다. 에이전트는 자기 이름, 기능(skill), 접속 주소, 인증 방식을 **Agent Card**(JSON 문서)로 공개하고, 다른 에이전트는 이를 읽은 뒤 HTTP 위의 JSON-RPC로 작업(Task)을 맡깁니다. 서로 다른 회사·프레임워크·언어로 만든 에이전트끼리 협업하게 하는 것이 목적입니다.

| 항목 | A2A 프로토콜 | 이 교재의 전문 에이전트 |
|------|--------------|-------------------------|
| 실행 위치 | 각자 다른 서버(프로세스) | 같은 Spring 애플리케이션 안 |
| 통신 방식 | HTTP + JSON-RPC | Java 메서드 호출 |
| 자기소개 | Agent Card | 없음 (Spring DI로 주입) |
| 내부 판단 | 대개 LLM 기반 | LLM 없음, 결정적 코드 |

MCP가 에이전트와 Tool·데이터를 잇는 표준이라면, A2A는 에이전트와 에이전트를 잇는 표준입니다. 이 프로젝트의 "MCP Tool"도 MCP 서버가 아니라 Spring AI `@Tool`로 만든 Tool입니다(Step 5). 전문 에이전트 하나를 나중에 다른 팀이 쓰는 독립 서비스로 떼어 내야 한다면, 그때 A2A 같은 프로토콜로 감싸 공개할 수 있습니다. 단일 책임으로 잘 나눠 두면 이런 분리도 쉬워집니다.

---

## Step 8-1: 전문 에이전트 한눈에 보기

`src/main/java/net/java21/flyschedule/service/agent` 패키지의 클래스는 모두 LLM을 쓰지 않습니다.

| 클래스 | 책임 | 하지 않는 일 | 주로 쓰는 곳 |
|--------|------|--------------|--------------|
| `DateParserAgent` | 날짜 표현 → `YYYYMMDD` | 공항 변환, API 호출 | Coordinator, `FlightSearchAgent` |
| `AirportCodeAgent` | 공항 이름 → 공항 코드 | 날짜 변환, 항공편 검색 | Coordinator, `FlightSearchAgent`, `AirportListAgent` |
| `TimeFilterAgent` | 시간 해석, 출발 시각으로 거르기 | 가격 필터, 검색 | Coordinator, `FlightSearchAgent` |
| `PriceFilterAgent` | 운임으로 거르기, 최저가 찾기 | 시간 필터, 검색 | Coordinator, `FlightSearchAgent` |
| `GroupingAgent` | 항공사별·시간대별 묶기 | 필터, 검색 | Coordinator, `FlightSearchAgent` |
| `FlightSearchAgent` | 위 에이전트를 묶어 검색 | 직접 변환·필터 구현 | `FlightSearchTool` (Step 6) |
| `AirportListAgent` | 공항 목록 조회, 공항 코드 조회 | 항공편 검색 | `AirportInfoTool` (Step 7) |
| `AirlineListAgent` | 항공사 목록 조회, 항공사 ID 검색 | 항공편 검색 | `AirlineInfoTool` (Step 7) |

이 Step에서는 위 다섯 개(변환 2개, 필터 2개, 그룹핑 1개)를 자세히 봅니다. 아래 세 개는 Step 6~7에서 이미 다뤘습니다.

---

## Step 8-2: DateParserAgent

### 왜 필요한가

공공데이터 포털 API는 `"20260325"` 같은 `YYYYMMDD` 형식만 받습니다. 사용자와 LLM은 "내일", "2026-03-25"처럼 다양하게 말하므로 이를 한곳에서 변환합니다.

### 코드 보기

`src/main/java/net/java21/flyschedule/service/agent/DateParserAgent.java` (Javadoc 생략)

```java
@Slf4j
@Service
public class DateParserAgent {

    private static final DateTimeFormatter API_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter INPUT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public String parseDate(String dateInput) {
        log.info("DateParserAgent: 날짜 파싱 요청 - {}", dateInput);

        // ① 값이 없으면 오늘
        if (dateInput == null || dateInput.isBlank()) {
            return LocalDate.now().format(API_DATE_FORMATTER);
        }

        String normalized = dateInput.trim();

        // ② 이미 API 형식(숫자 8자리)이면 그대로
        if (normalized.matches("\\d{8}")) {
            log.info("DateParserAgent: 이미 API 형식의 날짜 - {}", normalized);
            return normalized;
        }

        normalized = normalized.toLowerCase();

        // ③ 상대적 날짜 표현
        return switch (normalized) {
            case "오늘" -> LocalDate.now().format(API_DATE_FORMATTER);
            case "내일" -> LocalDate.now().plusDays(1).format(API_DATE_FORMATTER);
            case "모레", "내일모레" -> LocalDate.now().plusDays(2).format(API_DATE_FORMATTER);
            case "글피" -> LocalDate.now().plusDays(3).format(API_DATE_FORMATTER);
            default -> parseSpecificDate(normalized);  // ④ 그 밖에는 YYYY-MM-DD로 시도
        };
    }

    private String parseSpecificDate(String dateInput) {
        try {
            LocalDate date = LocalDate.parse(dateInput, INPUT_DATE_FORMATTER);
            return date.format(API_DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            log.error("잘못된 날짜 형식: {}", dateInput);
            throw new IllegalArgumentException("날짜 형식이 올바르지 않습니다. (YYYY-MM-DD 또는 '내일', '모레' 등)");
        }
    }
}
```

**코드 읽기**

1. 입력 형식은 여러 개여도 출력 형식은 `YYYYMMDD` 하나입니다. 호출하는 쪽은 결과를 그대로 API에 넘기면 됩니다.
2. `trim()`한 `normalized`를 끝까지 쓰므로 `"  2026-03-25  "`도 `"20260325"`가 됩니다. LLM이나 화면이 넘긴 값에 공백이 섞여도 실패하지 않습니다.
3. 이미 변환된 값(`"20260325"`)을 다시 넣어도 그대로 돌려줍니다. 호출하는 쪽이 변환 여부를 신경 쓰지 않아도 됩니다.
4. 실패는 `IllegalArgumentException`으로 알립니다. 사용자가 고칠 수 있는 입력 오류라는 뜻이며, Step 9의 Coordinator는 이 예외를 받아 메시지를 그대로 실패 응답에 담습니다.
5. `switch` 표현식(Java 14+)으로 상대 날짜 규칙을 한눈에 볼 수 있게 했습니다.

```java
// (오늘이 2026-03-24라고 가정)
dateParser.parseDate("내일");          // "20260325"
dateParser.parseDate("모레");          // "20260326"
dateParser.parseDate("2026-03-25");    // "20260325"
dateParser.parseDate("20260325");      // "20260325" (이미 형식이 맞음)
dateParser.parseDate(" 2026-03-25 ");  // "20260325" (앞뒤 공백 무시)
dateParser.parseDate("2026/03/25");    // IllegalArgumentException
```

### 확인하기

```bash
./mvnw test -Dtest=DateParserAgentTest
```

"상대적 날짜 표현을 정확히 파싱해야 한다"(오늘·내일·모레·내일모레·글피), "앞뒤 공백은 무시하고 날짜를 변환해야 한다", "잘못된 날짜 형식은 예외를 발생시켜야 한다" 테스트가 통과하는지 확인합니다.

---

## Step 8-3: AirportCodeAgent

### 왜 필요한가

사용자는 "광주에서 제주"라고 말하지만, API는 `NAARKJJ`, `NAARKPC` 같은 공항 코드만 이해합니다. 게다가 코드가 틀려도 API는 오류 없이 빈 목록을 돌려주므로, 잘못된 코드는 "항공편이 없다"는 결과 뒤에 숨어 버립니다. 그래서 코드를 한곳에 모아 관리하고 테스트로 고정합니다.

### 코드 보기

`src/main/java/net/java21/flyschedule/service/agent/AirportCodeAgent.java` (Javadoc 일부 생략)

```java
@Slf4j
@Service
public class AirportCodeAgent {

    private static final Map<String, String> AIRPORT_CODE_MAP = new HashMap<>();

    static {
        // 수도권
        register("NAARKSS", "김포");
        register("NAARKSI", "인천");

        // 부산/경남권
        register("NAARKPK", "김해", "부산");
        register("NAARKPS", "사천");
        register("NAARKPU", "울산");

        // 호남권
        register("NAARKJJ", "광주");
        register("NAARKJY", "여수");
        register("NAARKJB", "무안");
        register("NAARKJK", "군산");

        // 영남권
        register("NAARKTN", "대구");
        register("NAARKTH", "포항", "포항경주");

        // 충청/강원권
        register("NAARKTU", "청주");
        register("NAARKNY", "양양");
        register("NAARKNW", "원주");

        // 제주권
        register("NAARKPC", "제주");
    }

    private static void register(String code, String... names) {
        for (String name : names) {
            AIRPORT_CODE_MAP.put(name, code);
            AIRPORT_CODE_MAP.put(name + "공항", code);
            AIRPORT_CODE_MAP.put(name + "국제공항", code);
        }
    }

    public String getAirportCode(String airportName) {
        if (airportName == null || airportName.isBlank()) {
            throw new IllegalArgumentException("공항 이름을 입력해주세요.");
        }

        String normalized = airportName.trim();

        // 이미 공항 코드 형식인 경우 (NAARKSS, NAARKJJ 등)
        if (normalized.matches("NAARK[A-Z]{2}")) {
            log.info("공항 코드 입력됨: {}", normalized);
            return normalized;
        }

        String code = AIRPORT_CODE_MAP.get(normalized);

        if (code == null) {
            log.warn("알 수 없는 공항: {}", airportName);
            throw new IllegalArgumentException("알 수 없는 공항입니다: " + airportName);
        }

        return code;
    }

    // ... 생략 (isValidAirport: 표에 등록된 이름인지 true/false로 돌려줌)
}
```

**코드 읽기**

1. 코드는 공공데이터 포털의 공항 목록 API(`GetArprtList`) 응답을 기준으로 정했습니다.
2. `register()`는 이름 하나마다 "김해", "김해공항", "김해국제공항" 세 가지 키를 한 번에 등록합니다. 별칭("부산" → 김해공항)도 같은 줄에 적을 수 있어 표를 읽기 쉽습니다.
3. `static` 블록은 클래스가 로딩될 때 한 번만 실행됩니다. 바뀌지 않는 참조 데이터이므로 요청마다 공항 목록 API를 부르지 않습니다.
4. "서울"처럼 공항이 아닌 지명은 추측하지 않고 `IllegalArgumentException`을 던집니다. 추측은 Step 9의 LLM 단계가 맡고, 이 에이전트는 확실한 것만 변환합니다.

| 공항 (등록 이름) | 코드 |
|------------------|------|
| 김포 | NAARKSS |
| 인천 | NAARKSI |
| 김해, 부산 | NAARKPK |
| 사천 | NAARKPS |
| 울산 | NAARKPU |
| 광주 | NAARKJJ |
| 여수 | NAARKJY |
| 무안 | NAARKJB |
| 군산 | NAARKJK |
| 대구 | NAARKTN |
| 포항, 포항경주 | NAARKTH |
| 청주 | NAARKTU |
| 양양 | NAARKNY |
| 원주 | NAARKNW |
| 제주 | NAARKPC |

```java
airportAgent.getAirportCode("광주");          // "NAARKJJ"
airportAgent.getAirportCode("부산");          // "NAARKPK" (김해공항)
airportAgent.getAirportCode("김포공항");      // "NAARKSS"
airportAgent.getAirportCode("제주국제공항");  // "NAARKPC"
airportAgent.getAirportCode("NAARKJJ");       // "NAARKJJ" (이미 코드)
airportAgent.getAirportCode("서울");          // IllegalArgumentException
airportAgent.isValidAirport("화성");          // false
```

> **주의**: Step 9의 `SearchCriteriaExtractor`도 정규식으로 공항 이름을 찾을 때 자체 이름 목록(`AIRPORT_NAMES`)을 씁니다. 공항을 새로 추가하면 두 곳을 함께 고쳐야 합니다(실습 과제 1).

### 확인하기

```bash
./mvnw test -Dtest=AirportCodeAgentTest
```

---

## Step 8-4: TimeFilterAgent

### 왜 필요한가

"오후 2시 이후", "14:30 이후"처럼 시간 조건도 표현이 다양합니다. 시간 표현을 `LocalTime`으로 바꾸는 일과, 항공편의 출발 시각(`"202603250955"`)을 읽어 거르는 일을 한 클래스에 모읍니다.

### 코드 보기

`src/main/java/net/java21/flyschedule/service/agent/TimeFilterAgent.java` (일부)

```java
private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmm");

/**
 * 한글 시간 표현: "오후 2시", "오전9시 30분", "14시", "14시 30분"
 */
private static final Pattern KOREAN_TIME_PATTERN =
        Pattern.compile("(오전|오후)?\\s*(\\d{1,2})\\s*시(?:\\s*(\\d{1,2})\\s*분)?");

/**
 * 숫자 시간 표현: "14:00", "9:30", "1400", "930"
 */
private static final Pattern NUMERIC_TIME_PATTERN =
        Pattern.compile("(\\d{1,2}):?(\\d{2})");

public LocalTime parseTime(String timeInput) {
    if (timeInput == null || timeInput.isBlank()) {
        throw new IllegalArgumentException("시간을 입력해주세요.");
    }

    String normalized = timeInput.trim();

    try {
        Matcher korean = KOREAN_TIME_PATTERN.matcher(normalized);
        if (korean.find()) {
            int hour = Integer.parseInt(korean.group(2));
            int minute = korean.group(3) != null ? Integer.parseInt(korean.group(3)) : 0;
            String meridiem = korean.group(1);
            if ("오후".equals(meridiem) && hour < 12) {
                hour += 12; // 오후 2시 → 14시
            } else if ("오전".equals(meridiem) && hour == 12) {
                hour = 0;   // 오전 12시 → 0시
            }
            return LocalTime.of(hour, minute);
        }

        Matcher numeric = NUMERIC_TIME_PATTERN.matcher(normalized);
        if (numeric.matches()) {
            return LocalTime.of(Integer.parseInt(numeric.group(1)), Integer.parseInt(numeric.group(2)));
        }
    } catch (DateTimeException e) {
        // 25시, 70분처럼 범위를 벗어난 값은 아래에서 함께 처리
    }

    throw new IllegalArgumentException("시간 형식이 올바르지 않습니다. (HH:mm 또는 '오후 2시' 등)");
}

public List<FlightInfoResponse> filterAfterTime(List<FlightInfoResponse> flights, LocalTime afterTime) {
    if (flights == null || flights.isEmpty()) {
        return List.of();
    }

    return flights.stream()
            .filter(flight -> {
                try {
                    LocalTime departureTime = parseDepartureTime(flight.getDepartureTime());
                    return !departureTime.isBefore(afterTime);   // 기준 시각 "이후"는 같은 시각을 포함
                } catch (Exception e) {
                    log.warn("시간 파싱 실패: {}", flight.getDepartureTime());
                    return false;
                }
            })
            .collect(Collectors.toList());
}

// ... 생략 (filterBeforeTime: !departureTime.isAfter(beforeTime) 조건만 다름,
//           parseDepartureTime: "202603250955"에서 substring(8, 12)로 시각 부분을 꺼냄)
```

**코드 읽기**

1. 정규식 두 개를 순서대로 시도합니다. 한글 표현("오후 2시 30분")을 먼저 보고, 아니면 숫자 표현("14:30", "1430")을 봅니다.
2. 오전·오후 경계: "오후 12시"는 정오(12:00), "오전 12시"는 자정(00:00)입니다. 실수하기 쉬운 부분이라 테스트로 고정해 두었습니다.
3. `LocalTime.of(25, 0)`은 `DateTimeException`을 던집니다. 이를 잡아 다른 형식 오류와 같은 `IllegalArgumentException`으로 바꿔, 호출하는 쪽이 예외 하나만 처리하게 했습니다.
4. 필터는 실패하지 않습니다. 출발 시각을 읽을 수 없는 항공편 하나 때문에 전체 검색이 실패하면 안 되므로, 그 항공편만 경고 로그를 남기고 뺍니다. `null`이나 빈 목록이 들어와도 빈 목록을 돌려줍니다.
5. `filterBeforeTime()`은 Step 9의 Coordinator가 "오후 6시 이전" 같은 조건에 씁니다.

### 확인하기

```bash
./mvnw test -Dtest=TimeFilterAgentTest
```

---

## Step 8-5: PriceFilterAgent

### 왜 필요한가

"8만 원 이하", "4만 원 이상"처럼 가격 조건은 한쪽만 주어지는 경우가 많습니다. 또 공공데이터 포털은 운임 정보가 없을 때 `0`을 돌려주므로, 그대로 두면 0원짜리 항공편이 최저가로 뽑힙니다.

### 코드 보기

`src/main/java/net/java21/flyschedule/service/agent/PriceFilterAgent.java` (일부)

```java
public List<FlightInfoResponse> filterByPriceRange(
        List<FlightInfoResponse> flights,
        Integer minPrice,
        Integer maxPrice) {

    if (flights == null || flights.isEmpty()) {
        return List.of();
    }

    return flights.stream()
            .filter(flight -> {
                Integer price = flight.getEconomyCharge();

                // 가격 정보가 없는 경우 제외
                if (price == null || price == 0) {
                    return false;
                }

                // 최소 가격 필터
                if (minPrice != null && price < minPrice) {
                    return false;
                }

                // 최대 가격 필터
                return maxPrice == null || price <= maxPrice;
            })
            .collect(Collectors.toList());
}

public FlightInfoResponse findCheapest(List<FlightInfoResponse> flights) {
    return flights.stream()
            .filter(f -> f.getEconomyCharge() != null && f.getEconomyCharge() > 0)
            .min((f1, f2) -> f1.getEconomyCharge().compareTo(f2.getEconomyCharge()))
            .orElse(null);
}

// ... 생략 (findMostExpensive, calculateAveragePrice: 같은 조건으로 0원·null 제외)
```

**코드 읽기**

1. `minPrice`, `maxPrice`를 `Integer`로 받아 `null`을 "제한 없음"으로 씁니다. "8만 원 이하"는 `(목록, null, 80000)`, "4만 원 이상"은 `(목록, 40000, null)`입니다.
2. 운임이 `null`이거나 0원이면 "정보 없음"으로 보고 필터와 최저가 계산 모두에서 뺍니다. 화면용 `FlightView`도 같은 이유로 0원을 `null`로 바꿉니다(Step 9).

### 확인하기

```bash
./mvnw test -Dtest=PriceFilterAgentTest
```

`filterByPriceRange_flightsWithoutPrice_excluded()`가 운임이 `null`이거나 0원인 항공편이 빠지는지 확인합니다.

---

## Step 8-6: GroupingAgent

### 왜 필요한가

화면과 LLM 모두 "항공사별로" 결과를 보기를 원합니다. 묶는 순서가 요청마다 바뀌면 화면이 흔들리고, 외부 데이터에 항공사 이름이 빠져 있으면 `null` 키가 생길 수 있습니다.

### 코드 보기

`src/main/java/net/java21/flyschedule/service/agent/GroupingAgent.java` (일부)

```java
public Map<String, List<FlightInfoResponse>> groupByAirline(List<FlightInfoResponse> flights) {
    if (flights == null || flights.isEmpty()) {
        return new TreeMap<>();
    }

    Map<String, List<FlightInfoResponse>> grouped = flights.stream()
            .collect(Collectors.groupingBy(
                    flight -> {
                        String airlineName = flight.getAirlineName();
                        // 항공사명이 null이거나 빈 값일 경우 처리
                        if (airlineName == null || airlineName.trim().isEmpty()) {
                            // 항공편 ID에서 항공사 코드 추출 (예: OZ8141 -> OZ)
                            String flightId = flight.getFlightId();
                            if (flightId != null && flightId.length() >= 2) {
                                String airlineCode = flightId.substring(0, 2);
                                // ... 생략 (경고 로그)
                                return airlineCode;
                            }
                            // ... 생략 (경고 로그)
                            return "알 수 없는 항공사";
                        }
                        return airlineName;
                    },
                    TreeMap::new, // 정렬 순서 유지
                    Collectors.toList()
            ));

    log.info("그룹핑 완료: {}개 항공사, {}개 항공편", grouped.size(), flights.size());
    // ... 생략 (항공사별 편수 로그)

    return grouped;
}

// ... 생략 (groupByTimeRange: "아침 (06:00-12:00)", "점심 (12:00-14:00)" 같은 시간대별 묶기)
```

**코드 읽기**

1. `Collectors.groupingBy(분류 함수, TreeMap::new, Collectors.toList())`: 결과를 `TreeMap`에 담아 항공사 이름순으로 정렬합니다. 화면에 표시되는 순서가 요청마다 바뀌지 않습니다.
2. 항공사 이름이 비어 있으면 항공편 ID의 앞 두 글자(항공사 코드)로 묶습니다. 외부 API 데이터는 언제든 비어 있을 수 있다고 가정합니다.
3. 필터를 먼저 하고 그룹핑을 마지막에 하면, 필터 결과 0편이 된 항공사가 빈 목록으로 남지 않습니다. Step 9의 Coordinator가 이 순서를 지킵니다. 그룹핑을 먼저 하는 `FlightSearchAgent`는 필터 뒤에 빈 항공사를 따로 지웁니다(Step 6-4의 `filterEachAirline()`).

### 확인하기

```bash
./mvnw test -Dtest=GroupingAgentTest
```

---

## Step 8-7: 에이전트 조합 — FlightSearchAgent 다시 보기

Step 6-4에서 만든 `FlightSearchAgent`는 위 에이전트들을 묶는 조합용 에이전트입니다. 직접 변환·필터를 구현하지 않고 다른 전문 에이전트에 맡기기만 합니다.

```text
FlightSearchAgent.searchWithTimeFilter("광주", "제주", "2026-03-25", "10:00")
  ├→ DateParserAgent.parseDate()           "2026-03-25" → "20260325"
  ├→ AirportCodeAgent.getAirportCode() ×2  → "NAARKJJ", "NAARKPC"
  ├→ ApiClientService.getFlightSchedule()  → 항공편 목록
  ├→ GroupingAgent.groupByAirline()        → 항공사별 TreeMap
  ├→ TimeFilterAgent.parseTime()           "10:00" → 10:00
  └→ filterEachAirline(filterAfterTime)    항공사마다 거르고 빈 항공사 제거
```

이처럼 "부품을 정해진 순서로 호출하는 코드"를 체계적으로 만든 것이 다음 Step의 Coordinator 패턴입니다. Step 9의 `FlightSearchCoordinator`는 같은 일을 하되 단계마다 실행 기록(`ExecutionTrace`)을 남기고, 시간·가격 조건을 한 번에 조합합니다.

> **생각해 보기**: `FlightSearchAgent`(그룹핑 → 필터)와 `FlightSearchCoordinator`(필터 → 그룹핑)는 같은 부품을 다른 순서로 씁니다. 두 순서의 결과는 항상 같을까요? 다르다면 어떤 경우일까요?

---

## Step 8-8: 전문 에이전트 단위 테스트

### 왜 필요한가

전문 에이전트는 LLM도, 네트워크도, Spring 컨텍스트도 필요 없습니다. `new`로 만들어 바로 검증할 수 있다는 점이 이렇게 나눈 가장 큰 이유입니다. 테스트 파일은 `src/test/java/net/java21/flyschedule/service/` 아래에 있습니다.

| 테스트 | 검증 내용 |
|--------|-----------|
| `DateParserAgentTest` | 상대 날짜(오늘·내일·모레·내일모레·글피), `YYYY-MM-DD` 변환, 앞뒤 공백 무시, 잘못된 형식의 예외 |
| `AirportCodeAgentTest` | 공항 이름·"공항"·"국제공항" 접미사 → 공식 코드, 빈 값·모르는 공항의 예외 |
| `TimeFilterAgentTest` | "오후 2시 30분", "오전 12시" 등 시간 파싱, 이후 필터, 잘못된 출발 시각 제외 |
| `PriceFilterAgentTest` | 최소·최대·범위 필터, 운임 `null`·0원 제외, 최저가·최고가·평균 |
| `GroupingAgentTest` | 항공사별·시간대별 그룹핑, 정렬 순서, 빈 목록·`null` 처리 |
| `FlightSearchAgentTest` | 의존성을 Mockito `@Mock`으로 바꿔 조합 순서 검증 |

### 매개변수화 테스트로 변환표 검증하기

**매개변수화 테스트(parameterized test)** 는 같은 테스트 메서드를 여러 입력으로 반복 실행하는 JUnit 5 기능입니다. `AirportCodeAgentTest`는 `@CsvSource`로 "입력 → 기대 코드" 표를 그대로 테스트로 옮깁니다.

`src/test/java/net/java21/flyschedule/service/AirportCodeAgentTest.java` (일부)

```java
@DisplayName("AirportCodeAgent 단위 테스트")
class AirportCodeAgentTest {

    private final AirportCodeAgent airportCodeAgent = new AirportCodeAgent();   // Spring 없이 생성

    @ParameterizedTest
    @CsvSource({
            "'김포', 'NAARKSS'",
            "'김포공항', 'NAARKSS'",
            // ... 생략
            "'부산', 'NAARKPK'",
            "'김해', 'NAARKPK'",
            // ... 생략
            "'김해국제공항', 'NAARKPK'"
    })
    @DisplayName("공항 이름을 정확한 코드로 변환해야 한다")
    void getAirportCode_validAirports_returnsCorrectCode(String airportName, String expectedCode) {
        String result = airportCodeAgent.getAirportCode(airportName);

        assertThat(result).isEqualTo(expectedCode);
    }

    @Test
    @DisplayName("알 수 없는 공항 이름은 예외를 발생시켜야 한다")
    void getAirportCode_unknownAirport_throwsException() {
        assertThatThrownBy(() -> airportCodeAgent.getAirportCode("파리"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("알 수 없는 공항입니다");
    }
}
```

**코드 읽기**

1. `new AirportCodeAgent()`: `@SpringBootTest` 없이 객체를 직접 만듭니다. 테스트가 밀리초 단위로 끝납니다.
2. `@CsvSource`의 한 줄이 테스트 한 번입니다. 공항 코드를 잘못 고치면 API는 빈 목록을 돌려줄 뿐 오류를 내지 않으므로, 이런 "조용한 실패"를 막는 가장 확실한 방법이 이 변환표 테스트입니다.
3. `assertThatThrownBy(...)`: 예외 타입과 메시지까지 확인해, "모르는 입력은 추측하지 않는다"는 약속을 고정합니다.

### 경계값 테스트

`TimeFilterAgentTest`는 실수하기 쉬운 오전·오후 경계를 표로 고정합니다.

```java
@ParameterizedTest
@CsvSource({
        "'오후 2시 30분', '14:30'",
        "'오전 9시 30분', '09:30'",
        "'오전 12시', '00:00'",
        "'오후 12시', '12:00'",
        "'14시', '14:00'",
        "'9:30', '09:30'",
        "'930', '09:30'"
})
@DisplayName("분 단위와 한 자리 시간을 포함한 다양한 형식을 파싱해야 한다")
void parseTime_variousFormats_returnsLocalTime(String input, String expected) {
    LocalTime result = timeFilterAgent.parseTime(input);

    assertThat(result).isEqualTo(LocalTime.parse(expected));
}
```

필터 메서드가 "실패하지 않는다"는 약속도 테스트합니다. `filterAfterTime_invalidDepartureTime_excludesInvalid()`는 출발 시각이 `"invalid"`인 항공편이 섞여 있어도 예외 없이 그 항공편만 빠지는지 확인합니다.

### 확인하기

```bash
# 전문 에이전트 테스트만 실행
./mvnw test -Dtest='*AgentTest'

# 하나만 실행
./mvnw test -Dtest=AirportCodeAgentTest
```

LLM 서버나 공공데이터 포털 키가 없어도 모두 통과해야 합니다. 몇 초 안에 끝나므로 코드를 고칠 때마다 돌려 보세요.

> **팁**: 전체 테스트 수는 기능이 추가되면 바뀝니다. 지금 몇 개인지는 `./mvnw test`의 마지막 `Tests run:` 줄로 확인하세요.

---

## 일반적인 문제 해결

### 문제 1: 검색은 성공하는데 항공편이 0건

공항 코드가 틀렸을 가능성이 큽니다. 공공데이터 포털 API는 잘못된 코드에 오류 대신 빈 목록을 돌려줍니다. `AirportCodeAgent`의 코드를 공항 목록(`GET /api/mcp/airports`) 결과와 비교하고, `AirportCodeAgentTest`에 해당 공항을 추가합니다.

### 문제 2: "알 수 없는 공항입니다: 서울"

`AirportCodeAgent`는 공항 이름만 압니다. "서울"처럼 공항이 여러 개인 도시 이름은 일부러 등록하지 않았습니다. "김포"나 "인천"처럼 공항 이름으로 입력하거나, Step 9에서 LLM이 해석하도록 맡깁니다.

### 문제 3: "날짜 형식이 올바르지 않습니다"

`DateParserAgent`는 상대 날짜(오늘·내일·모레·내일모레·글피), `YYYY-MM-DD`, `YYYYMMDD`만 받습니다. `2026/03/25`나 "다음 주 금요일"은 지원하지 않습니다(앞뒤 공백은 지운 뒤 변환하므로 문제가 되지 않습니다). 계산이 필요한 날짜는 Step 9의 `SearchCriteriaExtractor`가 LLM으로 `YYYY-MM-DD`로 바꿔 넘깁니다.

### 문제 4: 최저가가 0원으로 나옴

`PriceFilterAgent`를 거치지 않고 직접 `min()`을 계산했을 때 생깁니다. 운임 0원은 "정보 없음"이므로 제외해야 합니다. `findCheapest()`를 쓰거나 같은 조건을 넣습니다.

---

## 실습 과제

1. **(기본) 공항 별칭 추가하기**: "제주도"를 제주 공항(`NAARKPC`)으로 인식하도록 `AirportCodeAgent`에 등록하세요. 그다음 같은 이름을 `SearchCriteriaExtractor.AIRPORT_NAMES`에도 넣으면 "광주에서 제주도"가 왜 정규식으로 처리되지 않는지 설명하세요.
   - 힌트: `AirportCodeAgent`의 `static` 블록에 `register(...)`, `AirportCodeAgentTest`의 `@CsvSource`. 두 번째 질문은 `SearchCriteriaExtractor`의 `.filter(message::contains)`와 "공항이 정확히 두 개일 때만" 규칙을 보세요.
   - 완료 기준: `"'제주도', 'NAARKPC'"`, `"'제주도공항', 'NAARKPC'"` 줄을 추가한 `./mvnw test -Dtest=AirportCodeAgentTest`가 통과하고, "제주"와 "제주도"가 함께 잡혀 공항이 세 개로 세어지는 이유를 설명할 수 있습니다.

2. **(기본) 날짜 표현 확장하기**: `DateParserAgent`가 `"2026.03.25"`와 `"2026/03/25"`도 받도록 고치세요.
   - 힌트: `parseSpecificDate()`에서 `.`과 `/`를 `-`로 바꾼 뒤 파싱합니다. 기존 `DateParserAgentTest`의 `parseDate_invalidFormat_throwsException`, `parseDate_invalidDateFormat_throwsException`이 어떤 입력을 쓰는지 먼저 확인하세요.
   - 완료 기준: 두 형식이 `"20260325"`가 되는 테스트가 추가되고, 기존 테스트 중 깨지는 것이 있다면 왜 깨지는지 설명한 뒤 기대값을 고쳐 `./mvnw test -Dtest=DateParserAgentTest`가 통과합니다.

3. **(도전) 새 전문 에이전트 만들기**: 항공편을 출발 시각순으로 정렬하는 `SortAgent`를 `service/agent` 패키지에 만드세요. 출발 시각을 읽을 수 없는 항공편은 맨 뒤로 보냅니다.
   - 힌트: 테스트 데이터는 `src/test/java/net/java21/flyschedule/support/TestFlights.java`의 `flight(flightId, airline, depTime, arrTime, price)`로 만듭니다. 출발 시각 읽기는 `TimeFilterAgent.parseDepartureTime()`을 참고하세요.
   - 완료 기준: `new SortAgent()`로 만든 단위 테스트(정상 정렬, 잘못된 시각은 맨 뒤, 빈 목록·`null`)가 Spring 컨텍스트 없이 통과합니다.

4. **(도전) 소요 시간 필터**: 출발·도착 시각으로 비행 시간을 계산해 주어진 시간 이내의 항공편만 남기는 `filterByDuration(List<FlightInfoResponse>, Duration)`을 `TimeFilterAgent`에 추가하세요.
   - 힌트: `Duration.between(출발 LocalTime, 도착 LocalTime)`. `FlightInfoResponse`의 `arrivalTime`도 `"YYYYMMDDHHmm"` 형식입니다.
   - 완료 기준: 1시간 이내 항공편만 남는 테스트와, 도착 시각이 비어 있는 항공편이 예외 없이 결과에서 빠지는 테스트가 통과합니다.

---

## 요약

- 이 교재의 전문 에이전트는 한 가지 일만 하는 결정적 클래스이며 LLM을 쓰지 않습니다.
- 기능을 나누면 단일 책임, 테스트 용이성, 재사용을 얻고, Step 9~11의 세 패턴이 같은 부품을 공유해 공정하게 비교할 수 있습니다.
- 변환 에이전트(`DateParserAgent`, `AirportCodeAgent`)는 확실하지 않은 입력을 `IllegalArgumentException`으로 거절하고, 필터·그룹핑 에이전트는 데이터 한 건이 잘못되어도 전체가 실패하지 않습니다.
- 전문 에이전트는 `new`로 만들어 매개변수화 테스트로 빠르게 검증합니다.
- A2A 프로토콜은 독립 실행되는 에이전트끼리 HTTP(JSON-RPC)로 통신하는 표준이며, 이 프로젝트에는 구현되어 있지 않습니다.

부품은 모두 준비되었습니다. 다음 Step에서는 이 부품들을 "코드가 정한 순서"로 호출하는 첫 번째 검색 패턴을 만듭니다.

---

## 체크리스트

- [ ] 전문 에이전트와 LLM 기반 에이전트의 차이를 판단 주체·결과·비용 측면에서 설명할 수 있습니다. (목표 1)
- [ ] 공항이 하나 추가될 때 어느 클래스를 고쳐야 하는지로 단일 책임과 재사용의 이점을 설명할 수 있습니다. (목표 2)
- [ ] `register()`가 등록하는 세 가지 키, 오전·오후 12시 처리 규칙, 운임 0원을 빼는 이유를 설명할 수 있습니다. (목표 3)
- [ ] `./mvnw test -Dtest='*AgentTest'`가 모두 통과하고, `@CsvSource` 줄을 하나 추가해 테스트를 늘려 보았습니다. (목표 4)
- [ ] 전문 에이전트, MCP, A2A 프로토콜이 각각 무엇을 잇는지 한 문장씩 말할 수 있습니다. (목표 5)

---

## 다음 단계

[Step 9: Coordinator 패턴 — 코드가 제어하는 워크플로](step-9-coordinator-pattern.md)에서는 자연어 문장에서 검색 조건을 뽑고, 이 Step의 전문 에이전트를 코드가 정한 순서대로 호출하는 `FlightSearchCoordinator`를 만듭니다.

---

## 참고 자료

- [Single Responsibility Principle](https://en.wikipedia.org/wiki/Single-responsibility_principle)
- [JUnit 5 User Guide - Parameterized Tests](https://junit.org/junit5/docs/current/user-guide/#writing-tests-parameterized-tests)
- [A2A Protocol (Agent2Agent)](https://a2a-protocol.org/)
- [Model Context Protocol](https://modelcontextprotocol.io/)
- [공공데이터 포털 - 국토교통부 국내항공운항정보](https://www.data.go.kr/)
