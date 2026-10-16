# Step 6: 항공편 검색 Tool 구현

Step 5에서는 Tool 7개의 구조와 `ToolResultCapture`를 살펴보았습니다. 이번 Step에서는 그중 핵심인 `FlightSearchTool`을 끝까지 따라가며, Tool이 공공데이터 포털 API를 호출해 항공편을 돌려주기까지의 과정을 구현합니다.

**이 Step에서 만들 것**: 출발 공항·도착 공항·날짜를 받아 항공사별로 묶은 항공편을 돌려주는 항공편 검색 Tool 3개(기본, 시간 필터, 가격 필터)와, 그 밑에서 공공데이터 포털을 부르는 `ApiClientService`를 완성합니다.

```bash
curl -G "http://localhost:8080/api/mcp/flights/search" \
  --data-urlencode "departure=광주" --data-urlencode "arrival=제주" --data-urlencode "date=내일"
```

```json
{
  "success": true,
  "message": "조회 완료",
  "data": {
    "대한항공": [ { "vihicleId": "KE1903", "airlineNm": "대한항공", "depPlandTime": "202610170710", "...": "..." } ],
    "제주항공": [ "... 항공사마다 최대 3편 ..." ]
  }
}
```

(항공편 번호와 시각은 예시이며, 실제 값은 날짜에 따라 다릅니다. JSON 필드 이름이 `vihicleId`, `depPlandTime`인 이유는 Step 6-2에서 설명합니다.)

## 학습 목표

이 Step을 마치면 다음을 할 수 있습니다.

1. `application.yml`과 `@ConfigurationProperties`로 공공데이터 포털 API 설정을 읽고, 서비스 키를 환경 변수로 관리할 수 있습니다.
2. 공공데이터 포털의 중첩 JSON 응답(`response.body.items.item`)을 DTO로 매핑할 수 있습니다.
3. `RestClient`로 외부 API를 호출하고, 호출 실패를 빈 목록이 아닌 예외(`ExternalApiException`)로 알릴 수 있습니다.
4. `FlightSearchAgent`가 전문 에이전트를 정해진 순서로 호출하는 흐름을 설명할 수 있습니다.
5. `FlightSearchTool`이 결과를 항공사별 3편으로 줄이는 이유를 설명하고, Tool을 직접 호출하거나 LLM에게 넘겨 결과를 확인할 수 있습니다.

---

## 전체 흐름

```text
[LLM 또는 McpController]
       ↓ searchFlightsByAirline("광주", "제주", "2026-10-17")
[FlightSearchTool]                       Tool: 입구, 결과를 항공사별 3편으로 제한
       ↓
[FlightSearchAgent]                      전문 에이전트를 순서대로 호출
       ├→ DateParserAgent    "2026-10-17" → "20261017"
       ├→ AirportCodeAgent   "광주" → "NAARKJJ", "제주" → "NAARKPC"
       ├→ ApiClientService   GET /GetFlightOpratInfoList → List<FlightInfoResponse>
       └→ GroupingAgent      항공사별 TreeMap
       ↓
[ToolResultCapture에 기록 → 결과 반환]
```

이 Step은 아래에서 위로, 곧 `ApiClientService` → `FlightSearchAgent` → `FlightSearchTool` 순서로 만듭니다.

---

## 공공데이터 포털 API와 RestClient

### 국내항공운항정보 API

**공공데이터 포털**(data.go.kr)은 정부·공공기관의 데이터를 API로 제공하는 사이트입니다. 이 프로젝트는 국토교통부(TAGO)의 국내항공운항정보 API를 씁니다.

| 항목 | 내용 |
|------|------|
| 기본 URL | `https://apis.data.go.kr/1613000/DmstcFlightNvgInfo` |
| `/GetFlightOpratInfoList` | 항공편 운항 정보 (`depAirportId`, `arrAirportId`, `depPlandTime`) |
| `/GetArprtList` | 공항 목록 (Step 7) |
| `/GetAirmanList` | 항공사 목록 (Step 7) |

API는 공항 이름이 아니라 `NAARKJJ` 같은 공항 코드를, 날짜는 `20261017` 같은 `YYYYMMDD` 형식만 받습니다. 그래서 사용자 표현을 API 형식으로 바꾸는 전문 에이전트가 필요합니다.

> **팁**: API 응답 모양을 먼저 눈으로 보고 싶다면 `docs/api-test/01-항공편-조회.http`를 IntelliJ HTTP Client로 실행해 보세요. 서비스 키 설정 방법은 `docs/api-test/README.md`에 있습니다.

### RestClient

**RestClient**는 Spring Framework 6.1부터 제공하는 동기식(blocking) HTTP 클라이언트입니다. `restClient.get().uri(url).retrieve().body(type)`처럼 메서드를 이어 쓰는 방식으로 요청과 응답 변환을 한 문장에 적을 수 있습니다.

| 클라이언트 | 방식 | 이 프로젝트에서 |
|------------|------|-----------------|
| `RestTemplate` | 동기식, 오래된 API | 쓰지 않음 |
| `WebClient` | 비동기·반응형 | 쓰지 않음 |
| `RestClient` | 동기식, 간결한 API | `ApiClientService`에서 사용 |

---

## Step 6-1: API 연동 설정

### 왜 필요한가

API 주소와 서비스 키를 코드에 직접 쓰면 키가 저장소에 올라가고, 환경마다 코드를 고쳐야 합니다. 설정은 `application.yml`에 두고, 키는 환경 변수로 넣습니다.

### 코드 보기

`src/main/resources/application.yml` (일부)

```yaml
# 공공데이터 포털 API 설정
data-go-kr:
  api:
    url: https://apis.data.go.kr/1613000/DmstcFlightNvgInfo
    service-key: ${DATA_GO_KR_SERVICE_KEY:}
```

`src/main/java/net/java21/flyschedule/config/DataGoKrApiProperties.java` (Javadoc 생략)

```java
@Data
@Component
@ConfigurationProperties(prefix = "data-go-kr.api")
public class DataGoKrApiProperties {

    private String url;

    private String serviceKey;
}
```

**코드 읽기**

1. `${DATA_GO_KR_SERVICE_KEY:}`: 환경 변수 값을 쓰고, 없으면 빈 문자열을 씁니다. 키가 없어도 애플리케이션은 시작되며, API를 부르는 순간 안내 메시지를 보여 줍니다(Step 6-3).
2. `@ConfigurationProperties(prefix = "data-go-kr.api")`: `url`, `service-key`를 각각 `url`, `serviceKey` 필드에 넣습니다(케밥 표기 → 카멜 표기).

> **주의**: 서비스 키를 `application.yml`의 기본값이나 문서에 적어 커밋하지 마세요. 실행 전에 환경 변수로 설정합니다(예: `export DATA_GO_KR_SERVICE_KEY=발급받은_키`).

---

## Step 6-2: 응답 DTO

### 왜 필요한가

공공데이터 포털의 응답은 여러 겹으로 감싸여 있고, 필드 이름도 `vihicleId`, `depPlandTime`처럼 Java에서 쓰기 불편합니다. DTO로 구조를 그대로 옮기고, `@JsonProperty`로 Java다운 이름을 붙입니다.

```json
{
  "response": {
    "header": { "resultCode": "00", "resultMsg": "NORMAL SERVICE." },
    "body": {
      "items": { "item": [ { "vihicleId": "7C125", "airlineNm": "제주항공", "depPlandTime": 202610161425, "...": "..." } ] },
      "numOfRows": 500, "pageNo": 1, "totalCount": 1
    }
  }
}
```

### 코드 보기

`src/main/java/net/java21/flyschedule/dto/response/FlightInfoResponse.java` (필드 주석 일부 생략)

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightInfoResponse {

    @JsonProperty("vihicleId")
    private String flightId;          // 항공편명 (예: OZ8141)

    @JsonProperty("airlineNm")
    private String airlineName;

    @JsonProperty("depPlandTime")
    private String departureTime;     // 출발 시각 (예: 202503090955)

    @JsonProperty("arrPlandTime")
    private String arrivalTime;

    @JsonProperty("economyCharge")
    private Integer economyCharge;    // 일반석 운임

    // ... 생략 (prestigeCharge, departureAirport(depAirportNm), arrivalAirport(arrAirportNm), airlineId)
}
```

`src/main/java/net/java21/flyschedule/dto/response/ApiResponseWrapper.java` (일부)

```java
@Data
public class ApiResponseWrapper {

    @JsonProperty("response")
    private Response response;

    // ... 생략 (내부 클래스 Response{header, body}, Header{resultCode, resultMessage},
    //           Body{items, numOfRows, pageNo, totalCount}, Items{item})

    /**
     * 아이템 목록 가져오기 (편의 메서드)
     */
    public List<FlightInfoResponse> getItems() {
        return response != null && response.body != null && response.body.items != null
                ? response.body.items.getItem()
                : null;
    }
}
```

**코드 읽기**

1. `@JsonProperty("vihicleId")`: API의 필드 이름(철자가 `vehicle`이 아니라 `vihicle`)을 `flightId`에 매핑합니다.
2. `departureTime`은 `"202503090955"` 같은 `YYYYMMDDHHmm` 문자열입니다. 시각은 앞에서 9~12번째 글자(`substring(8, 12)`)에 있습니다. API는 이 값을 숫자로 보내지만 Jackson이 `String` 필드에 맞게 바꿔 줍니다.
3. `@JsonProperty`는 읽을 때와 쓸 때 모두 적용됩니다. 그래서 `McpController`가 `FlightInfoResponse`를 그대로 돌려주면 JSON 필드 이름도 `flightId`가 아니라 `vihicleId`입니다.
4. `ApiResponseWrapper`는 JSON 구조를 그대로 내부 클래스로 옮기고, `getResultCode()`, `getResultMessage()`, `getTotalCount()`, `getItems()` 편의 메서드로 깊은 경로를 한 번에 꺼냅니다. 중간 값이 `null`이어도 `NullPointerException`이 나지 않습니다.

---

## Step 6-3: ApiClientService — 공공데이터 포털 호출

### 왜 필요한가

외부 API 호출은 실패할 수 있습니다. 서비스 키가 없거나, 네트워크가 끊기거나, API가 오류 코드를 돌려줄 수 있습니다. 이때 빈 목록을 돌려주면 사용자는 "그날 항공편이 없다"고 오해합니다. "항공편이 없다"와 "API를 부르지 못했다"를 구분해야 합니다.

### 코드 보기

`src/main/java/net/java21/flyschedule/service/api/ApiClientService.java` (일부)

```java
@Slf4j
@Service
public class ApiClientService {

    static final int MAX_ROWS = 500;

    private final DataGoKrApiProperties properties;
    private final RestClient restClient;

    public ApiClientService(DataGoKrApiProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    private <T> T get(String url, Class<T> type) {
        if (properties.getServiceKey() == null || properties.getServiceKey().isBlank()) {
            throw new ExternalApiException("공공데이터 포털 서비스 키가 설정되지 않았습니다. 환경 변수 DATA_GO_KR_SERVICE_KEY를 설정하세요.");
        }
        try {
            return restClient.get().uri(url).retrieve().body(type);
        } catch (RestClientException e) {
            log.warn("공공데이터 포털 API 호출 실패: {}", e.getMessage());
            throw new ExternalApiException("공공데이터 포털 API를 호출하지 못했습니다. 잠시 후 다시 시도하세요.", e);
        }
    }

    // ... 생략 (encodeParam: 파라미터 URL 인코딩)

    public List<FlightInfoResponse> getFlightSchedule(
            String depAirportId,
            String arrAirportId,
            String depPlandTime) {

        // Service Key는 인코딩하지 않고 그대로 사용 (공공데이터 포털 요구사항)
        String url = properties.getUrl() + "/GetFlightOpratInfoList" +
                "?serviceKey=" + properties.getServiceKey() +
                "&pageNo=1" +
                "&numOfRows=" + MAX_ROWS +
                "&_type=json" +
                "&depAirportId=" + encodeParam(depAirportId) +
                "&arrAirportId=" + encodeParam(arrAirportId) +
                "&depPlandTime=" + encodeParam(depPlandTime);

        log.info("항공편 API 호출: dep={}, arr={}, date={}", depAirportId, arrAirportId, depPlandTime);

        ApiResponseWrapper response = get(url, ApiResponseWrapper.class);
        if (response == null || !"00".equals(response.getResultCode())) {
            String reason = response != null ? response.getResultMessage() : "응답 없음";
            log.warn("항공편 API 오류: {}", reason);
            throw new ExternalApiException("공공데이터 포털 API 오류: " + reason);
        }

        log.info("항공편 {}건 조회 완료", response.getTotalCount());
        return response.getItems() != null ? response.getItems() : Collections.emptyList();
    }

    // ... 생략 (getAirportList, getAirlineList, 응답 래퍼 내부 클래스: Step 7)
}
```

**코드 읽기**

1. 생성자가 `RestClient.Builder`를 주입받습니다. Spring Boot가 만든 Builder에는 `application.yml`의 `spring.http.client.read-timeout`(300s) 같은 설정이 적용되어 있습니다. `RestClient.create()`로 직접 만들면 이 설정이 빠집니다.
2. `get()`은 서비스 키부터 검사합니다. 키가 비어 있으면 API를 부르지 않고 설정 방법을 안내하는 `ExternalApiException`을 던집니다.
3. HTTP 오류나 연결 실패(`RestClientException`)도 `ExternalApiException`으로 바꿉니다.
4. `numOfRows=MAX_ROWS(500)`: 첫 페이지만 받으므로 하루 운항 편수보다 넉넉해야 합니다. 김포→제주는 하루 100편이 넘습니다(2026-10 기준 118편).
5. `resultCode`가 `"00"`이 아니면 API가 보낸 이유(`resultMsg`)를 담아 예외를 던집니다. `"00"`인데 항공편이 없으면 그때만 빈 목록을 돌려줍니다.
6. 로그에는 URL 전체가 아니라 공항 코드와 날짜만 남깁니다. URL에 서비스 키가 들어 있기 때문입니다.

`ExternalApiException`(`service/api/ExternalApiException.java`)은 메시지와 원인만 받는 단순한 `RuntimeException`입니다. Coordinator·Orchestrator 패턴은 `ChatClientProvider.describeError()`가 이 예외의 메시지를 그대로 화면에 보여 주고, Tool Calling 경로에서는 Spring AI가 Tool 오류 메시지를 LLM에게 전달합니다.

### 확인하기

`ApiClientServiceTest`는 `MockRestServiceServer`로 공공데이터 포털을 흉내 내므로, 서비스 키나 네트워크 없이 실행됩니다.

```bash
./mvnw test -Dtest=ApiClientServiceTest
```

다음 네 테스트가 통과하는지 확인합니다.

| 테스트 | 확인하는 것 |
|--------|-------------|
| `requestsEnoughRows` | URL에 `numOfRows=500`, 공항 코드, 날짜가 들어가고 응답이 `FlightInfoResponse`로 바뀜 |
| `serverError` | HTTP 500이 "호출하지 못했습니다" 예외가 됨 |
| `resultCodeError` | `resultCode`가 `"30"`이면 `resultMsg`를 담은 예외가 됨 |
| `missingServiceKey` | 키가 비어 있으면 API를 부르지 않고 안내 예외를 던짐 |

---

## Step 6-4: FlightSearchAgent — 전문 에이전트 묶기

### 왜 필요한가

"내일 광주에서 제주"를 API 호출로 바꾸려면 날짜 변환, 공항 코드 변환, API 호출, 항공사별 묶기가 차례로 필요합니다. 이 순서를 Tool에 넣으면 Tool이 두꺼워지므로, 순서를 담당하는 `FlightSearchAgent`를 둡니다. 각 단계는 한 가지 일만 하는 전문 에이전트가 맡습니다.

| 전문 에이전트 | 하는 일 | 예 |
|---------------|---------|-----|
| `DateParserAgent` | 날짜 표현 → `YYYYMMDD` | "내일" → "20261017" |
| `AirportCodeAgent` | 공항 이름 → 공항 코드 | "광주" → "NAARKJJ" |
| `GroupingAgent` | 항공편 목록 → 항공사별 `TreeMap` | `{"대한항공": [...], "제주항공": [...]}` |
| `TimeFilterAgent` | 시간 표현 해석, 출발 시각으로 거르기 | "오후 2시" → 14:00 |
| `PriceFilterAgent` | 일반석 운임으로 거르기 | 3만~7만 원 |

각 전문 에이전트의 코드와 설계 원칙은 Step 8에서 자세히 봅니다. 여기서는 이들을 묶는 방법에 집중합니다.

### 코드 보기

`src/main/java/net/java21/flyschedule/service/agent/FlightSearchAgent.java` (Javadoc·로그 생략)

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
            String departure,
            String arrival,
            String date) {
        // ... 생략 (로그)

        // 1. DateParserAgent: 날짜 파싱
        String parsedDate = dateParserAgent.parseDate(date);

        // 2. AirportCodeAgent: 공항 코드 변환
        String depAirportId = airportCodeAgent.getAirportCode(departure);
        String arrAirportId = airportCodeAgent.getAirportCode(arrival);

        // 3. API 호출
        List<FlightInfoResponse> flights = apiClientService.getFlightSchedule(
                depAirportId, arrAirportId, parsedDate);

        // 4. GroupingAgent: 항공사별 그룹핑
        Map<String, List<FlightInfoResponse>> grouped = groupingAgent.groupByAirline(flights);
        // ... 생략 (로그)

        return grouped;
    }

    public Map<String, List<FlightInfoResponse>> searchWithTimeFilter(
            String departure,
            String arrival,
            String date,
            String afterTime) {
        // ... 생략 (로그)

        // 기본 검색 수행
        Map<String, List<FlightInfoResponse>> grouped = searchAndGroupByAirline(departure, arrival, date);

        // TimeFilterAgent: 시간 필터링
        LocalTime filterTime = timeFilterAgent.parseTime(afterTime);
        // ... 생략 (로그)

        // 각 항공사별로 필터링 적용
        return filterEachAirline(grouped, flights -> timeFilterAgent.filterAfterTime(flights, filterTime));
    }

    // ... 생략 (searchWithPriceFilter: 같은 방식으로 priceFilterAgent.filterByPriceRange() 적용)

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

**코드 읽기**

1. 생성자 주입(`@RequiredArgsConstructor`)으로 전문 에이전트 5개와 `ApiClientService`를 받습니다. `FlightSearchAgent`는 변환·필터를 직접 구현하지 않고 맡기기만 합니다.
2. `searchAndGroupByAirline()`은 1~4단계를 정해진 순서로 호출합니다. 어느 단계에서든 예외가 나면 그대로 위로 전달됩니다.
3. 시간·가격 검색은 기본 검색을 먼저 한 뒤, 항공사마다 필터를 적용합니다.
4. `filterEachAirline()`은 결과를 `TreeMap`에 다시 담아 항공사 이름순을 유지하고, 필터 뒤 남은 항공편이 없는 항공사는 뺍니다. 빈 목록(`"대한항공": []`)이 남으면 LLM이 "대한항공 항공편이 있다"고 잘못 요약하기 쉽습니다.

```text
searchWithTimeFilter("광주", "제주", "2026-10-17", "오후 2시")
  ├→ searchAndGroupByAirline()          기본 검색 (항공사별 전체)
  ├→ TimeFilterAgent.parseTime()        "오후 2시" → 14:00
  └→ filterEachAirline(filterAfterTime) 항공사마다 14:00 이후만, 빈 항공사 제거
```

> **생각해 보기**: Step 9의 `FlightSearchCoordinator`도 같은 전문 에이전트를 쓰지만, 필터를 먼저 하고 그룹핑을 마지막에 합니다. 그 순서라면 `filterEachAirline()` 같은 정리 단계가 왜 필요 없을까요?

### 확인하기

```bash
./mvnw test -Dtest=FlightSearchAgentTest
```

`FlightSearchAgentTest`는 `ApiClientService`와 전문 에이전트를 모두 Mockito 가짜 객체(`@Mock`)로 바꾸고, 기본·시간 필터·가격 필터·빈 결과 검색에서 각 에이전트를 올바르게 호출해 결과를 묶는지 검증합니다.

---

## Step 6-5: FlightSearchTool — LLM이 부르는 입구

### 왜 필요한가

LLM은 Tool 결과를 모두 읽고 답변을 씁니다. 김포→제주처럼 하루 100편이 넘는 결과를 그대로 넘기면 프롬프트가 길어져 작은 모델은 응답이 느려지거나 내용이 잘립니다. 그래서 Tool이 LLM에게 돌려줄 때만 항공사별 최대 3편으로 줄입니다.

### 코드 보기

`src/main/java/net/java21/flyschedule/mcp/FlightSearchTool.java` (일부, Javadoc 생략)

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class FlightSearchTool {

    static final int MAX_FLIGHTS_PER_AIRLINE = 3;

    private final FlightSearchAgent flightSearchAgent;

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

    // ... 생략 (searchFlightsAfterTime, searchFlightsByPriceRange: 아래 표 참고)

    private static Map<String, List<FlightInfoResponse>> limitPerAirline(Map<String, List<FlightInfoResponse>> grouped) {
        Map<String, List<FlightInfoResponse>> limited = new LinkedHashMap<>();
        grouped.forEach((airline, flights) ->
                limited.put(airline, flights.subList(0, Math.min(flights.size(), MAX_FLIGHTS_PER_AIRLINE))));
        log.info("MCP Tool 응답: {}개 항공사, {}편 (항공사별 최대 {}편)", limited.size(),
                limited.values().stream().mapToInt(List::size).sum(), MAX_FLIGHTS_PER_AIRLINE);
        return limited;
    }
}
```

나머지 두 Tool도 같은 모양입니다.

| Tool 메서드 | 추가 파라미터 | 위임하는 메서드 | 인자 요약(예) |
|-------------|---------------|-----------------|---------------|
| `searchFlightsByAirline` | 없음 | `searchAndGroupByAirline` | `광주 → 제주, 2026-10-17` |
| `searchFlightsAfterTime` | `String afterTime` ("14:00", "오후 2시") | `searchWithTimeFilter` | `광주 → 제주, 2026-10-17, 14:00 이후` |
| `searchFlightsByPriceRange` | `Integer minPrice`, `Integer maxPrice` (선택) | `searchWithPriceFilter` | `광주 → 제주, 2026-10-17, null~70000원` |

**코드 읽기**

1. Tool 메서드는 로그 한 줄과 `return ToolResultCapture.capture(...)` 한 줄뿐입니다. 실제 검색은 `FlightSearchAgent`가 합니다.
2. 두 번째 인자 `"%s → %s, %s".formatted(...)`는 실행 기록에 "LLM이 어떤 인자로 Tool을 불렀는가"를 보여 주는 요약입니다.
3. 세 Tool 모두 결과를 `limitPerAirline()`에 통과시킵니다. 시간·가격 필터를 거친 뒤에도 한 항공사에 수십 편이 남을 수 있기 때문입니다.
4. `LinkedHashMap`은 넣은 순서를 지키므로, `GroupingAgent`가 만든 항공사 이름순이 그대로 유지됩니다.
5. 검색 중 예외(예: 알 수 없는 공항, `ExternalApiException`)가 나면 `capture()`가 실패로 기록한 뒤 다시 던지고, Spring AI가 그 메시지를 LLM에게 전달합니다.

> **주의**: 3편 제한은 LLM에게 돌려주는 Tool 결과에만 적용됩니다. Coordinator·Orchestrator 패턴은 이 Tool을 거치지 않고 전문 에이전트를 직접 쓰므로 화면에 모든 항공편이 나옵니다.

### 확인하기

LLM 없이 `McpController`로 Tool을 직접 호출해 봅니다(서비스 키 필요).

```bash
# 기본 검색
curl -G "http://localhost:8080/api/mcp/flights/search" \
  --data-urlencode "departure=광주" --data-urlencode "arrival=제주" --data-urlencode "date=내일"

# 14시 이후 출발
curl -G "http://localhost:8080/api/mcp/flights/search/time" \
  --data-urlencode "departure=광주" --data-urlencode "arrival=제주" \
  --data-urlencode "date=내일" --data-urlencode "afterTime=14:00"

# 7만 원 이하 (minPrice 생략)
curl -G "http://localhost:8080/api/mcp/flights/search/price" \
  --data-urlencode "departure=광주" --data-urlencode "arrival=제주" \
  --data-urlencode "date=내일" --data-urlencode "maxPrice=70000"
```

`data`의 항공사마다 항공편이 3편 이하인지, 시간 필터 결과의 `depPlandTime` 뒤 4자리가 모두 `1400` 이상인지 확인합니다. 애플리케이션 로그에는 `MCP Tool 응답: N개 항공사, M편 (항공사별 최대 3편)`이 찍힙니다.

단위 테스트로도 확인할 수 있습니다.

```bash
./mvnw test -Dtest=FlightSearchToolTest
```

---

## Step 6-6: LLM에게 Tool 넘기기

Step 5-4에서 본 대로, 이 프로젝트는 `ChatClientConfig`에 Tool을 기본 등록하지 않고 `ToolCallingSearchService`에서 요청마다 넘깁니다.

`src/main/java/net/java21/flyschedule/service/toolcalling/ToolCallingSearchService.java` (일부)

```java
String answer = chatClientProvider.get(model).prompt()
        .system(systemPrompt())
        .user(message)
        .tools(flightSearchTool, airportInfoTool, airlineInfoTool)
        .call()
        .content();
```

같은 `ChatClient`를 쓰는 `SearchCriteriaExtractor`나 `ResultSynthesizer`에는 `.tools(...)`가 없으므로, 이 호출에서는 LLM이 Tool을 부를 수 없습니다.

### 확인하기

```bash
curl -X POST http://localhost:8080/api/search/tool-calling \
  -H "Content-Type: application/json" \
  -d '{"message": "내일 광주에서 제주로 가는 오후 2시 이후 항공편 알려줘", "model": "ollama"}'
```

응답의 `trace`에서 `kind`가 `TOOL`인 단계를 찾아, LLM이 `searchFlightsByAirline`과 `searchFlightsAfterTime` 중 무엇을 골랐는지, `detail`에 어떤 인자가 담겼는지 확인합니다. `results`의 `flightsByAirline`에는 항공사마다 최대 3편이 담깁니다. Tool Calling 에이전트 전체 구조는 Step 10에서 다룹니다.

---

## 일반적인 문제 해결

### 문제 1: 항공편 대신 오류 메시지가 나옴

응답 `message`(또는 `McpController`의 HTTP 500 응답)가 다음 중 하나라면 `ApiClientService`가 던진 `ExternalApiException`입니다.

| 메시지 | 원인과 해결 |
|--------|-------------|
| "공공데이터 포털 서비스 키가 설정되지 않았습니다…" | `DATA_GO_KR_SERVICE_KEY` 환경 변수를 설정하고 애플리케이션을 다시 시작합니다 |
| "공공데이터 포털 API를 호출하지 못했습니다…" | 네트워크나 API 서버 상태를 확인합니다. 로그의 `공공데이터 포털 API 호출 실패:` 줄에 원인이 있습니다 |
| "공공데이터 포털 API 오류: SERVICE_KEY_IS_NOT_REGISTERED_ERROR" | 공공데이터 포털에서 이 API의 활용 신청이 승인되었는지, 인코딩되지 않은(Decoding) 키를 썼는지 확인합니다 |

### 문제 2: 성공했는데 항공편이 0편

- 그 날짜에 실제로 운항편이 있는 노선인지 확인합니다(과거 날짜나 너무 먼 날짜는 비어 있을 수 있습니다).
- 공항 코드가 올바른지 확인합니다. 잘못된 코드에도 API는 오류 없이 빈 목록을 돌려줍니다(Step 8 문제 해결 참고).

### 문제 3: 시간 필터 결과가 비어 있음

`departureTime`은 `"202503090955"` 형식이므로 시각은 `substring(8, 12)`로 꺼내야 합니다. `TimeFilterAgent`의 `parseDepartureTime()`이 이 형식을 전제로 하며, 읽을 수 없는 값은 경고 로그(`시간 파싱 실패:`)를 남기고 결과에서 뺍니다.

### 문제 4: Tool Calling 응답이 너무 느림

- Tool 결과가 항공사별 3편으로 줄었는지 로그(`MCP Tool 응답:`)로 확인합니다.
- 작은 모델은 컨텍스트가 짧습니다. `application.yml`의 `spring.ai.ollama.chat.options.num-ctx`(4096)가 Tool 설명과 결과를 담기에 충분한지 확인합니다.

---

## 실습 과제

1. **(기본) 필터 결과 비교하기**: `McpController`로 기본 검색과 14시 이후 검색을 호출해 결과를 비교하세요.
   - 힌트: Step 6-5의 curl 명령, `FlightSearchAgent.filterEachAirline()`
   - 완료 기준: 두 결과 모두 항공사마다 3편 이하이고, 14시 이후 항공편이 없는 항공사는 시간 필터 결과에 키 자체가 없다는 것을 확인합니다.

2. **(기본) 날짜 변환 테스트 추가하기**: "글피"와 "2026-12-31"이 올바른 `yyyyMMdd` 값으로 바뀌는지, "다음 주"처럼 지원하지 않는 표현은 `IllegalArgumentException`을 던지는지 확인하는 테스트를 추가하세요.
   - 힌트: `src/test/java/net/java21/flyschedule/service/DateParserAgentTest.java`, `DateParserAgent.parseDate()`의 `switch`
   - 완료 기준: `./mvnw test -Dtest=DateParserAgentTest`가 통과합니다.

3. **(도전) "항공편 없음" 응답 테스트하기**: API가 `resultCode`는 `"00"`이지만 `items`가 없는 응답을 돌려줄 때, `getFlightSchedule()`이 예외 없이 빈 목록을 돌려주는지 확인하는 테스트를 추가하세요.
   - 힌트: `ApiClientServiceTest.resultCodeError()`를 복사해 응답 JSON의 `header`를 `"00"`으로, `body`를 `{"totalCount":0}`으로 바꿉니다.
   - 완료 기준: 새 테스트가 통과하고, "API 오류"와 "항공편 없음"을 가르는 조건문이 `getFlightSchedule()`의 어느 줄인지 짚을 수 있습니다.

4. **(도전) 결과 제한 개수 바꿔 보기**: `MAX_FLIGHTS_PER_AIRLINE`을 1로 바꾸고 Tool Calling 검색을 다시 실행해, LLM 답변과 응답 시간이 어떻게 달라지는지 관찰하세요. 관찰한 뒤에는 3으로 되돌립니다.
   - 힌트: `FlightSearchTool.MAX_FLIGHTS_PER_AIRLINE`, 응답의 `elapsedMs`
   - 완료 기준: 1편·3편일 때의 `elapsedMs`와 답변 내용을 비교해, 제한 값을 정할 때의 장단점을 두 문장으로 설명할 수 있습니다.

---

## 요약

- 공공데이터 포털 설정은 `application.yml`과 `DataGoKrApiProperties`로 읽고, 서비스 키는 `DATA_GO_KR_SERVICE_KEY` 환경 변수로 넣습니다.
- `ApiClientService`는 Boot가 만든 `RestClient.Builder`로 API를 부르고, 키 누락·HTTP 실패·오류 코드를 빈 목록 대신 `ExternalApiException`으로 알립니다. 하루 100편이 넘는 노선을 위해 500건씩 요청합니다.
- `FlightSearchAgent`는 날짜 변환 → 공항 코드 변환 → API 호출 → 그룹핑을 정해진 순서로 호출하고, 필터 뒤 빈 항공사를 지웁니다.
- `FlightSearchTool`의 세 Tool은 `FlightSearchAgent`에 맡기고, `ToolResultCapture`로 기록하며, LLM에게는 항공사별 최대 3편만 돌려줍니다.

항공편 검색 Tool이 완성되었으니, 다음 Step에서는 LLM이 함께 고를 수 있는 공항·항공사 정보 Tool을 추가합니다.

---

## 체크리스트

- [ ] `DATA_GO_KR_SERVICE_KEY`를 설정하고, 서비스 키가 `application.yml`에 들어 있지 않음을 확인했습니다. (목표 1)
- [ ] `response.body.items.item` 구조와 `vihicleId` → `flightId` 매핑을 설명할 수 있습니다. (목표 2)
- [ ] `./mvnw test -Dtest=ApiClientServiceTest`가 통과하고, API 실패를 예외로 알리는 이유를 설명할 수 있습니다. (목표 3)
- [ ] `FlightSearchAgent.searchAndGroupByAirline()`의 네 단계와 `filterEachAirline()`의 역할을 설명할 수 있습니다. (목표 4)
- [ ] `McpController`나 `POST /api/search/tool-calling`으로 항공사별 3편 이하의 결과를 확인했습니다. (목표 5)

---

## 다음 단계

[Step 7: 공항/항공사 정보 Tool 구현](step-7-airport-info-tool.md)에서 공항 코드 조회, 항공사 ID 조회 Tool을 추가하고, 여러 Tool을 한 요청에 함께 넘깁니다.

---

## 참고 자료

- [공공데이터 포털](https://www.data.go.kr/)
- [국내항공운항정보 API 문서](https://www.data.go.kr/data/15098526/openapi.do)
- [RestClient 공식 문서](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-restclient)
