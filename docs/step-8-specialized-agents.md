# Step 8: 전문 에이전트 설계와 구현

## 학습 목표

이 단계를 마치면 다음을 할 수 있습니다:

1. 이 교재에서 말하는 **전문 에이전트(전문 컴포넌트)**가 무엇이고, 왜 LLM을 쓰지 않는지 설명할 수 있다
2. 기능을 **단일 책임** 단위로 나누면 테스트와 재사용이 왜 쉬워지는지 설명할 수 있다
3. `DateParserAgent`, `AirportCodeAgent`, `TimeFilterAgent`, `PriceFilterAgent`, `GroupingAgent`의 코드를 읽고 설계 의도를 설명할 수 있다
4. 전문 에이전트를 Spring 컨텍스트 없이 `new`로 만들어 **단위 테스트**할 수 있다
5. 이 교재의 전문 에이전트와 **A2A(Agent2Agent) 프로토콜**의 차이를 구분할 수 있다

---

> 💡 **핵심 개념**: 전문 에이전트는 **한 가지 일만 하는 결정적(deterministic) 클래스**입니다.
> 같은 입력을 넣으면 언제나 같은 결과가 나옵니다.
>
> 일상생활에 비유하면: 여행사의 **전담 직원**과 같습니다. 날짜 담당 직원은 "내일"을 "2026년 3월 25일"로 바꾸는 일만 하고,
> 공항 담당 직원은 "광주"를 공항 코드로 바꾸는 일만 합니다. 누가 어떤 순서로 이 직원들에게 일을 맡길지는
> 다음 단계(Step 9~11)에서 세 가지 방식으로 정합니다.

---

## 전체 흐름

이 단계에서 살펴볼 다섯 전문 에이전트가 항공편 검색 한 번에서 맡는 일입니다.

```text
[검색 조건: 출발 "광주", 도착 "제주", 날짜 "2026-03-25", 10:00 이후, 8만 원 이하]
       ↓
① DateParserAgent.parseDate("2026-03-25")         → "20260325"
② AirportCodeAgent.getAirportCode("광주")          → "NAARKJJ"
③ AirportCodeAgent.getAirportCode("제주")          → "NAARKPC"
       ↓
[ApiClientService.getFlightSchedule("NAARKJJ", "NAARKPC", "20260325")]  → 항공편 목록
       ↓
④ TimeFilterAgent.filterAfterTime(목록, 10:00)     → 10시 이후 출발만
⑤ PriceFilterAgent.filterByPriceRange(목록, null, 80000) → 8만 원 이하만
⑥ GroupingAgent.groupByAirline(목록)               → {"대한항공": [...], "제주항공": [...]}
```

이 순서를 **누가 정하는지**는 이 단계의 관심사가 아닙니다. 같은 에이전트를 세 가지 패턴이 함께 씁니다.

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

AI 분야에서 **에이전트(agent)**는 보통 "LLM이 스스로 판단해 도구를 고르고 행동하는 프로그램"을 뜻합니다.
그런데 이 프로젝트의 `service/agent` 패키지에 있는 클래스들은 **LLM을 전혀 사용하지 않습니다.**

| 구분 | 이 교재의 전문 에이전트 | 일반적인 의미의 AI 에이전트 |
|------|-------------------------|-----------------------------|
| **판단 주체** | 개발자가 작성한 코드 | LLM |
| **결과** | 같은 입력 → 항상 같은 결과 (결정적) | 실행할 때마다 달라질 수 있음 |
| **속도·비용** | 밀리초 단위, 비용 없음 | 수 초 단위, 토큰 비용 발생 |
| **이 프로젝트의 예** | `DateParserAgent`, `AirportCodeAgent` | Step 10의 Tool Calling 에이전트 |

그래도 "에이전트"라는 이름을 쓰는 이유는, 이 클래스들이 **자기 분야의 일을 맡아 처리하는 전담 실행자**이기 때문입니다.
더 정확한 이름은 **전문 컴포넌트**이며, 이 교재에서는 두 말을 같은 뜻으로 씁니다.

> ⚠️ **용어 주의**: 이 교재에서 "전문 에이전트"라고 하면 언제나 **LLM을 쓰지 않는 결정적 클래스**를 뜻합니다.
> LLM이 판단하는 에이전트는 Step 10에서 **Tool Calling 에이전트**라는 이름으로 따로 다룹니다.

### 왜 기능을 잘게 나눌까요?

`FlightSearchService` 하나에 날짜 변환, 공항 코드 변환, 시간·가격 필터, 그룹핑을 모두 넣을 수도 있습니다.
하지만 이렇게 나누면 세 가지 이점이 생깁니다.

| 이점 | 설명 | 이 프로젝트의 예 |
|------|------|------------------|
| **단일 책임 (SRP)** | 클래스가 바뀌는 이유가 하나뿐 | 공항이 추가되면 `AirportCodeAgent`만 수정 |
| **테스트 용이성** | 의존성이 없어 `new`로 만들어 바로 검증 | `new TimeFilterAgent().parseTime("오후 2시")` |
| **재사용** | 세 가지 패턴이 같은 부품을 공유 | Coordinator, Tool, Worker가 모두 같은 에이전트 사용 |

특히 마지막 **재사용**이 이 교재에서 중요합니다. Step 9~11은 "누가 다음 단계를 결정하는가"만 다르고,
실제로 날짜를 바꾸고 항공편을 거르는 부품은 같습니다. 그래서 세 패턴의 결과를 공정하게 비교할 수 있습니다.

> 📖 **Spring Boot 복습**: `UserService`에 로그인, 회원 가입, 비밀번호 변경, 이메일 발송을 모두 넣으면
> 하나를 고칠 때 다른 기능이 망가지기 쉽습니다. 그래서 `AuthService`, `EmailService`처럼 나눕니다.
> 전문 에이전트도 같은 원리이며, 코드로 보면 그냥 `@Service`가 붙은 Spring Bean입니다.

### 전문 에이전트와 일반 Service의 차이

| 항목 | 일반적인 Service | 전문 에이전트 |
|------|------------------|---------------|
| **어노테이션** | `@Service` | `@Service` (같음) |
| **책임** | 도메인 기능 여러 개를 묶기도 함 | 한 가지 변환·필터만 담당 |
| **상태** | Repository 등 의존성이 많음 | 대부분 의존성 없음 (`new`로 생성 가능) |
| **입력·출력** | 엔티티, DTO | 문자열, `List<FlightInfoResponse>` 같은 단순한 값 |
| **호출하는 쪽** | Controller | Coordinator, Tool, Worker |

**결론: 전문 에이전트는 "작고 결정적이며 의존성이 거의 없는" Spring Service입니다.**

---

## A2A 프로토콜과의 차이

> 📌 **A2A(Agent2Agent) 프로토콜은 이 프로젝트에 구현되어 있지 않습니다.** 이름이 비슷해 헷갈리기 쉬우므로 여기서 분명히 구분합니다.
>
> **A2A(Agent2Agent) 프로토콜**은 Google이 2025년에 발표한 **에이전트 간 통신 표준**이며, 지금은 Linux Foundation이 관리합니다.
>
> - 각 에이전트는 **독립 실행되는 서비스**(별도 서버)입니다.
> - 에이전트는 자신의 이름, 기능(skill), 접속 주소, 인증 방식을 **Agent Card**(JSON 문서)로 공개합니다.
> - 다른 에이전트는 Agent Card를 읽고 **HTTP 위의 JSON-RPC**로 작업(Task)을 맡기고 결과를 받습니다. 오래 걸리는 작업은 스트리밍이나 상태 조회로 진행 상황을 받습니다.
> - 서로 다른 회사·프레임워크·언어로 만든 에이전트끼리도 협업할 수 있게 하는 것이 목적입니다.
>
> | 항목 | A2A 프로토콜 | 이 교재의 전문 에이전트 |
> |------|--------------|-------------------------|
> | **실행 위치** | 각자 다른 서버(프로세스) | 같은 Spring 애플리케이션 안 |
> | **통신 방식** | HTTP + JSON-RPC | Java 메서드 호출 |
> | **자기 소개** | Agent Card | 없음 (Spring DI로 주입) |
> | **내부 판단** | 대개 LLM 기반 에이전트 | LLM 없음, 결정적 코드 |
>
> **MCP와 A2A 한 줄 비교**: MCP가 **에이전트 ↔ Tool·데이터**를 잇는 표준이라면, A2A는 **에이전트 ↔ 에이전트**를 잇는 표준입니다.
> (이 프로젝트의 "MCP Tool"도 MCP 서버가 아니라 Spring AI `@Tool`로 만든 Tool이며, MCP 서버로 노출할 수 있는 형태로 설계했다는 뜻입니다. Step 5 참고)

나중에 전문 에이전트 하나를 다른 팀이 쓰는 독립 서비스로 떼어 내야 한다면, 그때 A2A 같은 프로토콜로 감싸서 공개할 수 있습니다.
단일 책임으로 잘 나눠 두면 이런 분리도 쉬워집니다.

---

## Step 8-1: 전문 에이전트 한눈에 보기

`src/main/java/net/java21/flyschedule/service/agent` 패키지의 클래스는 모두 LLM을 쓰지 않습니다.

| 클래스 | 책임 | 하지 않는 일 | 주로 쓰는 곳 |
|--------|------|--------------|--------------|
| `DateParserAgent` | 날짜 표현 → `YYYYMMDD` | 공항 변환, API 호출 | Coordinator, FlightSearchAgent |
| `AirportCodeAgent` | 공항 이름 → 공항 코드 | 날짜 변환, 항공편 검색 | Coordinator, FlightSearchAgent |
| `TimeFilterAgent` | 출발 시각으로 거르기 | 가격 필터, 검색 | Coordinator, FlightSearchAgent |
| `PriceFilterAgent` | 운임으로 거르기, 최저가 찾기 | 시간 필터, 검색 | Coordinator, FlightSearchAgent |
| `GroupingAgent` | 항공사별·시간대별 묶기 | 필터, 검색 | Coordinator, FlightSearchAgent |
| `FlightSearchAgent` | 위 에이전트를 묶어 검색 | 직접 변환·필터 구현 | `FlightSearchTool` (Step 6) |
| `AirportListAgent` | 공항 목록 조회·검색 | 항공편 검색 | `AirportInfoTool` (Step 7) |
| `AirlineListAgent` | 항공사 목록 조회·검색 | 항공편 검색 | `AirlineInfoTool` (Step 7) |

이 단계에서는 위 다섯 개(변환 2개, 필터 2개, 그룹핑 1개)를 자세히 봅니다.
`AirportListAgent`, `AirlineListAgent`는 Step 7에서 이미 다뤘습니다.

---

## Step 8-2: DateParserAgent

**파일**: `src/main/java/net/java21/flyschedule/service/agent/DateParserAgent.java`

공공데이터 포털 API는 `"20260325"` 같은 `YYYYMMDD` 형식만 받습니다. 사용자는 "내일", "2026-03-25"처럼 다양하게 말하므로 이를 한곳에서 변환합니다.

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
            default -> parseSpecificDate(normalized);  // ④ 그 밖에는 YYYY-MM-DD로 시도 (공백 제거한 값)
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

**설계 포인트**

- **입력 형식이 여러 개여도 출력 형식은 하나**입니다. 호출하는 쪽은 결과가 언제나 `YYYYMMDD`라고 믿고 API에 넘기면 됩니다.
- **앞뒤 공백은 먼저 지웁니다.** `trim()`한 `normalized`를 끝까지 사용하므로 `"  2026-03-25  "`도 `"20260325"`가 됩니다. LLM이나 화면이 넘긴 값에 공백이 섞여도 실패하지 않습니다.
- **이미 변환된 값도 받아 줍니다.** `"20260325"`를 다시 넣어도 그대로 돌려주므로, 호출하는 쪽이 변환 여부를 신경 쓰지 않아도 됩니다.
- **실패는 `IllegalArgumentException`으로 알립니다.** 사용자가 고칠 수 있는 입력 오류라는 뜻이며, Step 9의 Coordinator는 이 예외를 받아 메시지를 그대로 실패 응답에 담습니다.
- **switch 표현식**(Java 14+)으로 상대 날짜 규칙을 한눈에 볼 수 있게 했습니다.

```java
// (오늘이 2026-03-24라고 가정)
dateParser.parseDate("내일");        // "20260325"
dateParser.parseDate("모레");        // "20260326"
dateParser.parseDate("2026-03-25");  // "20260325"
dateParser.parseDate("20260325");    // "20260325" (이미 형식이 맞음)
dateParser.parseDate(" 2026-03-25 "); // "20260325" (앞뒤 공백 무시)
dateParser.parseDate("2026/03/25");  // IllegalArgumentException
```

---

## Step 8-3: AirportCodeAgent

**파일**: `src/main/java/net/java21/flyschedule/service/agent/AirportCodeAgent.java`

사용자는 "광주에서 제주"라고 말하지만, API는 `NAARKJJ`, `NAARKPC` 같은 공항 코드만 이해합니다.

```java
@Slf4j
@Service
public class AirportCodeAgent {

    /**
     * 공항 이름 → 공항 코드
     *
     * <p>코드는 공공데이터 포털 국내항공운항정보 API의 공항 목록(GetArprtList) 기준입니다.
     * "광주"와 "광주공항"처럼 "공항"이 붙은 이름도 함께 등록합니다.
     */
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

    // 이름 하나마다 "X", "X공항", "X국제공항" 세 가지 키를 같은 코드로 등록
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

        // 이미 공항 코드 형식이면 그대로 (NAARKSS, NAARKJJ 등)
        if (normalized.matches("NAARK[A-Z]{2}")) {
            return normalized;
        }

        String code = AIRPORT_CODE_MAP.get(normalized);
        if (code == null) {
            log.warn("알 수 없는 공항: {}", airportName);
            throw new IllegalArgumentException("알 수 없는 공항입니다: " + airportName);
        }
        return code;
    }

    public boolean isValidAirport(String airportName) {
        if (airportName == null || airportName.isBlank()) {
            return false;
        }
        return AIRPORT_CODE_MAP.containsKey(airportName.trim());
    }
}
```

**설계 포인트**

- **공식 코드만 씁니다.** 코드는 공공데이터 포털의 공항 목록 API(`GetArprtList`) 응답을 기준으로 정했습니다. 코드가 틀리면 API는 오류 없이 빈 목록을 돌려주므로, 잘못된 코드는 "항공편이 없다"는 결과로 숨어 버립니다. 그래서 코드를 한곳에 모아 관리합니다.
- **`register()` 도우미 메서드**: 이름 하나마다 "김해", "김해공항", "김해국제공항" 세 가지 키를 한 번에 등록합니다. 별칭("부산" → 김해공항)도 같은 줄에 적을 수 있어 표를 읽기 쉽습니다.
- **`static` 블록**: 바뀌지 않는 참조 데이터이므로 클래스가 로딩될 때 한 번만 만듭니다. 매 요청마다 공항 목록 API를 부르지 않아도 됩니다.
- **모르는 공항은 예외**: "서울"처럼 공항이 아닌 지명은 추측하지 않고 `IllegalArgumentException`을 던집니다. 추측은 Step 9의 LLM 단계가 맡고, 이 에이전트는 확실한 것만 변환합니다.

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

> 📖 **참고**: Step 9의 `SearchCriteriaExtractor`도 정규식으로 공항 이름을 찾을 때 같은 이름 목록을 씁니다.
> 공항을 새로 추가하면 두 곳을 함께 고쳐야 한다는 점을 기억해 두세요. (실습 과제 1)

---

## Step 8-4: TimeFilterAgent

**파일**: `src/main/java/net/java21/flyschedule/service/agent/TimeFilterAgent.java`

시간 표현을 `LocalTime`으로 바꾸고, 출발 시각으로 항공편을 거릅니다.

```java
@Slf4j
@Service
public class TimeFilterAgent {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmm");

    /** 한글 시간 표현: "오후 2시", "오전9시 30분", "14시", "14시 30분" */
    private static final Pattern KOREAN_TIME_PATTERN =
            Pattern.compile("(오전|오후)?\\s*(\\d{1,2})\\s*시(?:\\s*(\\d{1,2})\\s*분)?");

    /** 숫자 시간 표현: "14:00", "9:30", "1400", "930" */
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

    // filterBeforeTime()은 !departureTime.isAfter(beforeTime) 조건만 다르다
    // ... 생략

    /** "202603250955" 형식에서 시각 부분(0955)을 꺼낸다 */
    private LocalTime parseDepartureTime(String departureTime) {
        if (departureTime == null || departureTime.length() < 8) {
            throw new IllegalArgumentException("잘못된 출발 시간 형식");
        }
        String timePart = departureTime.substring(8, 12);
        return LocalTime.parse(timePart, TIME_FORMATTER);
    }
}
```

**설계 포인트**

- **두 가지 정규식을 순서대로 시도**합니다. 한글 표현("오후 2시 30분")을 먼저 보고, 아니면 숫자 표현("14:30", "1430")을 봅니다.
- **오전·오후 경계**: "오후 12시"는 정오(12:00), "오전 12시"는 자정(00:00)입니다. 실수하기 쉬운 부분이라 테스트로 고정해 두었습니다.
- **범위를 벗어난 값**: `LocalTime.of(25, 0)`은 `DateTimeException`을 던집니다. 이를 잡아 다른 형식 오류와 같은 `IllegalArgumentException`으로 바꿔, 호출하는 쪽이 예외 하나만 처리하면 되게 했습니다.
- **필터는 실패하지 않습니다.** 출발 시각을 읽을 수 없는 항공편 하나 때문에 전체 검색이 실패하면 안 되므로, 그 항공편만 경고 로그를 남기고 결과에서 뺍니다. `null`이나 빈 목록이 들어와도 빈 목록을 돌려줍니다.

---

## Step 8-5: PriceFilterAgent

**파일**: `src/main/java/net/java21/flyschedule/service/agent/PriceFilterAgent.java`

```java
@Slf4j
@Service
public class PriceFilterAgent {

    public List<FlightInfoResponse> filterByPriceRange(
            List<FlightInfoResponse> flights, Integer minPrice, Integer maxPrice) {

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
                    if (minPrice != null && price < minPrice) {
                        return false;
                    }
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

    // findMostExpensive(), calculateAveragePrice() ... 생략
}
```

**설계 포인트**

- **`null`은 "제한 없음"**: `minPrice`, `maxPrice`를 `Integer`로 받아 한쪽만 지정할 수 있습니다. "8만 원 이하"는 `(목록, null, 80000)`, "4만 원 이상"은 `(목록, 40000, null)`입니다.
- **운임 0원은 "정보 없음"**: 공공데이터 포털 API는 운임 정보가 없을 때 `0`을 돌려줍니다. 0원짜리 항공권이 최저가로 뽑히면 안 되므로 필터와 최저가 계산 모두에서 뺍니다. (화면용 `FlightView`도 같은 이유로 0원을 `null`로 바꿉니다. Step 9 참고)

---

## Step 8-6: GroupingAgent

**파일**: `src/main/java/net/java21/flyschedule/service/agent/GroupingAgent.java`

```java
@Slf4j
@Service
public class GroupingAgent {

    public Map<String, List<FlightInfoResponse>> groupByAirline(List<FlightInfoResponse> flights) {
        if (flights == null || flights.isEmpty()) {
            return new TreeMap<>();
        }

        Map<String, List<FlightInfoResponse>> grouped = flights.stream()
                .collect(Collectors.groupingBy(
                        flight -> {
                            String airlineName = flight.getAirlineName();
                            if (airlineName == null || airlineName.trim().isEmpty()) {
                                // 항공사명이 없으면 항공편 ID 앞 두 글자 (예: OZ8141 → OZ)
                                String flightId = flight.getFlightId();
                                if (flightId != null && flightId.length() >= 2) {
                                    return flightId.substring(0, 2);
                                }
                                return "알 수 없는 항공사";
                            }
                            return airlineName;
                        },
                        TreeMap::new, // 항공사 이름순 정렬
                        Collectors.toList()
                ));

        log.info("그룹핑 완료: {}개 항공사, {}개 항공편", grouped.size(), flights.size());
        return grouped;
    }

    // groupByTimeRange(): "아침 (06:00-12:00)", "점심 (12:00-14:00)" 같은 시간대별 묶기 ... 생략
}
```

**설계 포인트**

- **`TreeMap`으로 정렬**: 항공사 이름순으로 정렬되므로 화면에 표시되는 순서가 요청마다 바뀌지 않습니다.
- **빠진 데이터에 대한 대비**: 항공사 이름이 비어 있으면 항공편 ID의 앞 두 글자(항공사 코드)로 묶습니다. 외부 API 데이터는 언제든 비어 있을 수 있다는 가정으로 작성했습니다.
- **그룹핑은 마지막에**: 필터를 먼저 하고 그룹핑을 나중에 하면, 필터 결과 0편이 된 항공사가 빈 목록으로 남지 않습니다. Step 9의 Coordinator가 이 순서를 지킵니다. (그룹핑부터 하는 `FlightSearchAgent`는 필터 뒤에 빈 항공사를 따로 지웁니다. Step 8-7 참고)

---

## Step 8-7: 에이전트 조합 — FlightSearchAgent

**파일**: `src/main/java/net/java21/flyschedule/service/agent/FlightSearchAgent.java`

`FlightSearchAgent`는 직접 변환·필터를 구현하지 않고 다른 전문 에이전트에 **위임**하는 조합용 에이전트입니다. Step 6의 `FlightSearchTool`이 이 클래스를 사용합니다.

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class FlightSearchAgent {

    private final ApiClientService apiClientService;
    private final DateParserAgent dateParserAgent;
    private final AirportCodeAgent airportCodeAgent;
    private final TimeFilterAgent timeFilterAgent;
    private final PriceFilterAgent priceFilterAgent;
    private final GroupingAgent groupingAgent;

    public Map<String, List<FlightInfoResponse>> searchAndGroupByAirline(
            String departure, String arrival, String date) {

        String parsedDate = dateParserAgent.parseDate(date);                // 위임
        String depAirportId = airportCodeAgent.getAirportCode(departure);   // 위임
        String arrAirportId = airportCodeAgent.getAirportCode(arrival);     // 위임

        List<FlightInfoResponse> flights = apiClientService.getFlightSchedule(
                depAirportId, arrAirportId, parsedDate);

        return groupingAgent.groupByAirline(flights);                       // 위임
    }

    public Map<String, List<FlightInfoResponse>> searchWithTimeFilter(
            String departure, String arrival, String date, String afterTime) {

        Map<String, List<FlightInfoResponse>> grouped = searchAndGroupByAirline(departure, arrival, date);
        LocalTime filterTime = timeFilterAgent.parseTime(afterTime);

        return filterEachAirline(grouped, flights -> timeFilterAgent.filterAfterTime(flights, filterTime));
    }

    // searchWithPriceFilter()도 같은 방식으로 priceFilterAgent.filterByPriceRange()를 적용 ... 생략

    /** 항공사별 목록에 필터를 적용하고, 남은 항공편이 없는 항공사는 결과에서 뺀다 */
    private Map<String, List<FlightInfoResponse>> filterEachAirline(
            Map<String, List<FlightInfoResponse>> grouped,
            UnaryOperator<List<FlightInfoResponse>> filter) {
        Map<String, List<FlightInfoResponse>> filtered = new TreeMap<>();
        grouped.forEach((airline, flights) -> {
            List<FlightInfoResponse> remaining = filter.apply(flights);
            if (!remaining.isEmpty()) {
                filtered.put(airline, remaining);
            }
        });
        return filtered;
    }
}
```

`FlightSearchAgent`는 이미 그룹핑된 결과에 필터를 걸기 때문에, `filterEachAirline()`이 "14시 이후 0편" 같은 빈 항공사를 지워 줍니다. 그렇지 않으면 LLM이 받는 Tool 결과에 `"대한항공": []`처럼 빈 항목이 남아 "대한항공은 0편"이라는 불필요한 문장을 만들기 쉽습니다. `TreeMap`을 쓰는 이유는 `GroupingAgent`와 같습니다(항공사 이름순).

이렇게 "부품을 정해진 순서로 호출하는 코드"를 체계적으로 만든 것이 다음 단계의 **Coordinator 패턴**입니다.
Step 9의 `FlightSearchCoordinator`는 같은 일을 하되 **단계마다 실행 기록을 남기고**, 시간·가격 조건을 한 번에 조합할 수 있게 확장합니다.

> 📖 **Spring Boot 복습**: `@RequiredArgsConstructor`는 `final` 필드만 받는 생성자를 Lombok이 만들어 줍니다.
> 생성자가 하나뿐이면 Spring이 `@Autowired` 없이도 그 생성자로 의존성을 주입합니다.

---

## Step 8-8: 전문 에이전트 단위 테스트

전문 에이전트는 LLM도, 네트워크도, Spring 컨텍스트도 필요 없습니다. **`new`로 만들어 바로 검증**할 수 있다는 점이 이렇게 나눈 가장 큰 이유입니다.

테스트 파일은 `src/test/java/net/java21/flyschedule/service/` 아래에 있습니다.

| 테스트 | 검증 내용 |
|--------|-----------|
| `DateParserAgentTest` | 상대 날짜(오늘·내일·모레·내일모레·글피), `YYYY-MM-DD` 변환, 앞뒤 공백 무시, 잘못된 형식의 예외 |
| `AirportCodeAgentTest` | 공항 이름·"공항"·"국제공항" 접미사 → 공식 코드, 빈 값·모르는 공항의 예외 |
| `TimeFilterAgentTest` | "오후 2시 30분", "오전 12시" 등 시간 파싱, 이후 필터, 잘못된 출발 시각 제외 |
| `PriceFilterAgentTest` | 최소·최대·범위 필터, 운임 `null`·0원 제외, 최저가·평균 |
| `GroupingAgentTest` | 항공사별·시간대별 그룹핑, 빈 목록 처리 |

### 매개변수화 테스트로 변환표 검증하기

`AirportCodeAgentTest`는 `@CsvSource`로 "입력 → 기대 코드" 표를 그대로 테스트로 옮깁니다.

```java
@DisplayName("AirportCodeAgent 단위 테스트")
class AirportCodeAgentTest {

    private final AirportCodeAgent airportCodeAgent = new AirportCodeAgent();   // Spring 없이 생성

    @ParameterizedTest
    @CsvSource({
            "'김포', 'NAARKSS'",
            "'김포공항', 'NAARKSS'",
            "'광주', 'NAARKJJ'",
            "'제주', 'NAARKPC'",
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

공항 코드를 잘못 고치면 API는 빈 목록을 돌려줄 뿐 오류를 내지 않습니다. 이런 "조용한 실패"를 막는 가장 확실한 방법이 이 변환표 테스트입니다.

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

필터 메서드는 "실패하지 않는다"는 약속도 테스트합니다.

```java
@Test
@DisplayName("잘못된 형식의 출발 시간을 가진 항공편은 필터링에서 제외되어야 한다")
void filterAfterTime_invalidDepartureTime_excludesInvalid() {
    List<FlightInfoResponse> flights = List.of(
            FlightInfoResponse.builder().flightId("7C1402").departureTime("202503090955").build(),
            FlightInfoResponse.builder().flightId("OZ8141").departureTime("invalid").build()
    );

    List<FlightInfoResponse> result = timeFilterAgent.filterAfterTime(flights, LocalTime.of(0, 0));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getFlightId()).isEqualTo("7C1402");
}
```

`PriceFilterAgentTest`의 `filterByPriceRange_flightsWithoutPrice_excluded()`도 같은 방식으로 운임이 `null`이거나 0원인 항공편이 빠지는지 확인합니다.

### 테스트 실행

```bash
# 전문 에이전트 테스트만 실행
./mvnw test -Dtest='*AgentTest'

# 하나만 실행
./mvnw test -Dtest=AirportCodeAgentTest
```

LLM 서버나 공공데이터 포털 키가 없어도 모두 통과해야 합니다. 몇 초 안에 끝나므로 코드를 고칠 때마다 돌려 보세요.

---

## 일반적인 문제 해결

### 문제 1: 검색은 성공하는데 항공편이 0건

공항 코드가 틀렸을 가능성이 큽니다. 공공데이터 포털 API는 잘못된 코드에 오류 대신 빈 목록을 돌려줍니다.
`AirportCodeAgent`의 코드를 공항 목록 API(`/api/mcp/airports` 또는 `AirportInfoTool`) 결과와 비교하고, `AirportCodeAgentTest`에 해당 공항을 추가하세요.

### 문제 2: "알 수 없는 공항입니다: 서울"

`AirportCodeAgent`는 공항 이름만 압니다. "서울"처럼 여러 공항이 있는 도시 이름은 일부러 등록하지 않았습니다.
"김포"나 "인천"처럼 공항 이름으로 입력하거나, Step 9에서 LLM이 해석하도록 맡기세요.

### 문제 3: "날짜 형식이 올바르지 않습니다"

`DateParserAgent`는 상대 날짜(오늘·내일·모레·내일모레·글피), `YYYY-MM-DD`, `YYYYMMDD`만 받습니다.
`2026/03/25`나 "다음 주 금요일"은 지원하지 않습니다(앞뒤 공백은 지운 뒤 변환하므로 문제가 되지 않습니다). 계산이 필요한 날짜는 Step 9의 `SearchCriteriaExtractor`가 LLM으로 `YYYY-MM-DD`로 바꿔서 넘깁니다.

### 문제 4: 최저가가 0원으로 나옴

`PriceFilterAgent`를 거치지 않고 직접 `min()`을 계산했을 때 생깁니다. 운임 0원은 "정보 없음"이므로 제외해야 합니다. `findCheapest()`를 쓰거나 같은 조건을 넣으세요.

---

## 실습 과제

1. **(기본) 공항 별칭 추가하기**: `GET /api/mcp/airports`로 공항 목록을 확인한 뒤, 사용자가 자주 쓸 만한 별칭(예: "제주도" → `NAARKPC`. 단, "서울"처럼 공항이 여러 개인 도시 이름은 제외)을 `AirportCodeAgent`의 `static` 블록에 `register(...)`로 추가하세요. `AirportCodeAgentTest`의 `@CsvSource`에 새 별칭을 추가해 확인하세요. 그다음 "제주도"를 `SearchCriteriaExtractor.AIRPORT_NAMES`에도 넣으면 "광주에서 제주도"가 왜 정규식으로 처리되지 않는지(힌트: `message::contains`) 설명해 보세요.
2. **(기본) 날짜 표현 확장하기**: `DateParserAgent`가 `"2026.03.25"`와 `"2026/03/25"`도 받도록 고치고, `DateParserAgentTest`의 `parseDate_invalidFormat_throwsException`이 어떻게 바뀌어야 하는지 정리한 뒤 새 테스트를 작성하세요. 이어서 `"3일 뒤"`처럼 숫자와 "일 뒤"로 된 표현도 지원해 보고, 이것을 `DateParserAgent`에서 처리할 때와 Step 9의 LLM에 맡길 때의 장단점을 비교해 보세요.
3. **(도전) 새 전문 에이전트 만들기**: 항공편을 출발 시각순으로 정렬하는 `SortAgent`를 `service/agent` 패키지에 만드세요. 출발 시각을 읽을 수 없는 항공편은 맨 뒤로 보내고, `TestFlights.flight(...)`를 활용한 단위 테스트를 작성하세요. (Spring 컨텍스트 없이 `new SortAgent()`로 테스트해야 합니다)
4. **(도전) 소요 시간 필터**: 출발·도착 시각으로 비행 시간을 계산해 "1시간 이내" 항공편만 남기는 `filterByDuration(List<FlightInfoResponse>, Duration)` 메서드를 `TimeFilterAgent`에 추가하고, 도착 시각이 비어 있는 항공편이 결과에서 빠지는지 테스트하세요.

---

## 요약

1. 이 교재의 **전문 에이전트(전문 컴포넌트)**는 한 가지 일만 하는 **결정적 클래스**이며 LLM을 쓰지 않습니다.
2. 기능을 나누면 **단일 책임**, **테스트 용이성**, **재사용**을 얻습니다. Step 9~11의 세 패턴이 모두 같은 에이전트를 사용합니다.
3. `DateParserAgent`와 `AirportCodeAgent`는 사람의 표현을 API 형식으로 바꾸고, 확실하지 않은 입력은 `IllegalArgumentException`으로 거절합니다.
4. `TimeFilterAgent`, `PriceFilterAgent`, `GroupingAgent`는 목록을 받아 목록(또는 Map)을 돌려주며, 데이터 한 건이 잘못되어도 전체가 실패하지 않습니다.
5. 전문 에이전트는 `new`로 만들어 **매개변수화 테스트**로 빠르게 검증할 수 있습니다.
6. **A2A 프로토콜**은 독립 실행되는 에이전트끼리 HTTP(JSON-RPC)로 통신하는 표준이며, 이 프로젝트에는 구현되어 있지 않습니다.

---

## 체크리스트

- [ ] 전문 에이전트와 LLM 기반 에이전트의 차이를 설명할 수 있음
- [ ] A2A 프로토콜과 이 교재의 전문 에이전트, MCP의 차이를 설명할 수 있음
- [ ] `AirportCodeAgent`의 `register()`가 등록하는 세 가지 키를 말할 수 있음
- [ ] `TimeFilterAgent`의 오전·오후 12시 처리 규칙을 설명할 수 있음
- [ ] `PriceFilterAgent`가 운임 0원을 제외하는 이유를 설명할 수 있음
- [ ] `./mvnw test -Dtest='*AgentTest'`가 모두 통과함

---

## 다음 단계

[Step 9: Coordinator 패턴 — 코드가 제어하는 워크플로](step-9-coordinator-pattern.md)에서는 자연어 문장에서 검색 조건을 뽑고, 이 단계의 전문 에이전트를 **코드가 정한 순서대로** 호출하는 `FlightSearchCoordinator`를 만듭니다.

---

## 참고 자료

- [Single Responsibility Principle](https://en.wikipedia.org/wiki/Single-responsibility_principle)
- [JUnit 5 User Guide - Parameterized Tests](https://junit.org/junit5/docs/current/user-guide/#writing-tests-parameterized-tests)
- [A2A Protocol (Agent2Agent)](https://a2a-protocol.org/)
- [Model Context Protocol](https://modelcontextprotocol.io/)
- [공공데이터 포털 - 국토교통부 국내항공운항정보](https://www.data.go.kr/)
