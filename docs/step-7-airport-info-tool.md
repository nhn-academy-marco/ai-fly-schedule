# Step 7: 공항/항공사 정보 Tool 구현

Step 6에서 만든 항공편 검색 Tool만으로는 "광주 공항 코드가 뭐야?", "제주항공 ID는?" 같은 질문에 답할 수 없습니다. 이번 Step에서는 공항 정보 Tool과 항공사 정보 Tool을 추가하고, 세 Tool 클래스를 한 요청에 함께 넘겨 LLM이 필요한 Tool을 고르게 합니다.

**이 Step에서 만들 것**: `AirportInfoTool`(공항 목록, 공항 코드)과 `AirlineInfoTool`(항공사 목록, 항공사 ID)을 만들고, 두 질문이 섞인 문장에서 LLM이 Tool을 두 개 고르는 것을 실행 기록으로 확인합니다.

```bash
curl -G "http://localhost:8080/api/mcp/airports/code" --data-urlencode "airportName=광주국제공항"
```

```json
{"success":true,"message":"조회 완료","data":{"airportName":"광주국제공항","airportCode":"NAARKJJ"}}
```

## 학습 목표

이 Step을 마치면 다음을 할 수 있습니다.

1. 공공데이터 포털의 공항·항공사 목록 API 응답을 DTO와 내부 응답 래퍼 클래스로 매핑할 수 있습니다.
2. 공항·항공사 정보 Tool을 만들고, 일을 `AirportListAgent`·`AirlineListAgent`에게 맡기는 구조를 설명할 수 있습니다.
3. `AirportCodeAgent`가 공항 이름 → 코드 매핑을 관리하는 방식(`register()`)과 그 한계를 설명할 수 있습니다.
4. Tool 클래스 3개를 `.tools(...)`로 한 요청에 넘기고, 실행 기록에서 LLM이 고른 Tool을 확인할 수 있습니다.

---

## 전체 흐름

```text
[사용자: "광주 공항 코드가 뭐야? 그리고 내일 광주에서 제주 항공편 알려줘"]
       ↓
[LLM: Tool 7개 중 두 개를 고름]
       ├→ 1) AirportInfoTool.getAirportCode("광주")
       │      → AirportListAgent → AirportCodeAgent → "NAARKJJ"
       └→ 2) FlightSearchTool.searchFlightsByAirline("광주", "제주", "2026-10-17")
              → FlightSearchAgent → ... → 항공사별 항공편 (항공사별 최대 3편)
       ↓
[LLM: "광주 공항 코드는 NAARKJJ이고, 내일 항공편은 ..."]
```

새로 만드는 Tool도 Step 5에서 본 위임 구조를 그대로 따릅니다.

```text
AirportInfoTool → AirportListAgent ─┬→ ApiClientService.getAirportList()   (공공데이터 포털)
                                    └→ AirportCodeAgent.getAirportCode()   (고정 매핑)
AirlineInfoTool → AirlineListAgent ──→ ApiClientService.getAirlineList()   (공공데이터 포털)
```

---

## Step 7-1: 공항·항공사 목록 API와 DTO

### 왜 필요한가

Step 6의 항공편 API와 같은 국내항공운항정보 API(`https://apis.data.go.kr/1613000/DmstcFlightNvgInfo`)에 공항 목록과 항공사 목록 오퍼레이션이 있습니다. 응답 필드 이름(`airportNm`, `airlineNm`)을 Java다운 이름으로 매핑할 DTO가 필요합니다.

| 오퍼레이션 | 설명 | 주요 응답 필드 |
|-----------|------|----------------|
| `/GetArprtList` | 전체 공항 목록 | `airportId`, `airportNm` |
| `/GetAirmanList` | 전체 항공사 목록 | `airlineId`, `airlineNm` |

### 코드 보기

`src/main/java/net/java21/flyschedule/dto/response/AirportInfoResponse.java` (import 생략)

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AirportInfoResponse {

    @JsonProperty("airportId")
    private String airportId;      // 공항 ID (예: NAARKJJ)

    @JsonProperty("airportNm")
    private String airportName;    // 공항명 (예: 광주)

    @JsonProperty("region")
    private String region;
}
```

`src/main/java/net/java21/flyschedule/dto/response/AirlineInfoResponse.java` (import 생략)

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AirlineInfoResponse {

    @JsonProperty("airlineId")
    private String airlineId;      // 항공사 ID (예: AAR)

    @JsonProperty("airlineNm")
    private String airlineName;    // 항공사명 (예: 아시아나항공)
}
```

**코드 읽기**

1. 반환하는 공항 ID(`NAARKJJ` 등)는 공공데이터 포털이 쓰는 코드이며, 3자리 IATA 코드(`KWJ` 등)가 아닙니다.
2. `AirlineInfoResponse`에는 `airlineId`와 `airlineName` 두 필드만 있습니다.

---

## Step 7-2: ApiClientService에 목록 조회 추가

### 왜 필요한가

목록 API도 항공편 API와 같은 `response.body.items.item` 중첩 구조로 응답합니다. 다만 항목 타입이 다르므로 전용 응답 래퍼가 필요합니다. `ApiClientService`는 이를 `private static` 내부 클래스(`AirportListResponse`, `AirlineListResponse`)로 둡니다.

### 코드 보기

`src/main/java/net/java21/flyschedule/service/api/ApiClientService.java` (일부)

```java
public List<AirportInfoResponse> getAirportList() {
    // Service Key는 인코딩하지 않고 그대로 사용
    // _type=json 파라미터 제거 (공항 목록 API는 JSON이 기본)
    String url = properties.getUrl() + "/GetArprtList" +
            "?serviceKey=" + properties.getServiceKey();

    log.info("공항 목록 API 호출");

    AirportListResponse response = get(url, AirportListResponse.class);

    if (response != null && "00".equals(response.getResultCode())) {
        return response.getItems() != null ? response.getItems() : Collections.emptyList();
    }

    return Collections.emptyList();
}

// ... 생략 (getAirlineList: URL이 "/GetAirmanList" + "&_type=json"이고 AirlineListResponse를 쓰는 것만 다름)

@lombok.Data
private static class AirportListResponse {
    private Response response;

    // ... 생략 (내부 클래스 Response{header, body}, Header{resultCode, resultMsg}, Body{items}, Items{item})

    public String getResultCode() {
        return response != null && response.header != null ? response.header.getResultCode() : null;
    }

    public List<AirportInfoResponse> getItems() {
        return response != null && response.body != null && response.body.items != null
                ? response.body.items.getItem()
                : null;
    }
}

// ... 생략 (AirlineListResponse: 항목 타입만 AirlineInfoResponse로 다름)
```

**코드 읽기**

1. 실제 HTTP 호출은 Step 6-3의 private `get(url, type)`이 맡습니다. 따라서 서비스 키가 없거나 HTTP 호출이 실패하면 `ExternalApiException`이 납니다.
2. 응답 래퍼를 `private static` 내부 클래스로 둔 이유는, 이 구조가 `ApiClientService` 밖에서 쓰이지 않기 때문입니다. 밖으로는 `List<AirportInfoResponse>`만 내보냅니다.
3. 공항 목록 URL에는 `_type=json`이 없습니다. 주석대로 이 오퍼레이션은 JSON이 기본입니다.

> **주의**: 항공편 조회(`getFlightSchedule`)와 달리, 목록 조회는 `resultCode`가 `"00"`이 아니면 예외 없이 빈 목록을 돌려줍니다. 서비스 키는 있지만 활용 신청이 안 된 경우 "공항이 0개"로 보일 수 있으므로, 목록이 비어 있으면 로그와 공공데이터 포털의 활용 신청 상태를 함께 확인하세요.

### 확인하기

```bash
curl http://localhost:8080/api/mcp/airports
curl http://localhost:8080/api/mcp/airlines
```

서비스 키가 설정되어 있으면 `data`에 `airportId`·`airportName`(또는 `airlineId`·`airlineName`) 쌍의 목록이 나옵니다. `McpController`가 DTO를 `Map`으로 바꿔 돌려주므로 여기서는 필드 이름이 `airportNm`이 아니라 `airportName`입니다.

---

## Step 7-3: AirportCodeAgent — 공항 이름을 코드로

### 왜 필요한가

API는 공항 이름("광주")이 아니라 공항 코드("NAARKJJ")를 받습니다. 사용자는 "광주", "광주공항", "광주국제공항"처럼 다양하게 말하므로, 이름을 코드로 바꾸는 표가 필요합니다. 이 표는 공항 정보 Tool(`getAirportCode`)과 항공편 검색(`FlightSearchAgent`)이 함께 씁니다.

### 코드 보기

`src/main/java/net/java21/flyschedule/service/agent/AirportCodeAgent.java` (일부)

```java
private static final Map<String, String> AIRPORT_CODE_MAP = new HashMap<>();

static {
    // 수도권
    register("NAARKSS", "김포");
    register("NAARKSI", "인천");

    // 부산/경남권
    register("NAARKPK", "김해", "부산");
    // ... 생략 (사천, 울산, 광주, 여수, 무안, 군산, 대구, 포항, 청주, 양양, 원주, 제주. 전체 표는 Step 8-3)
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
```

**코드 읽기**

1. `static { }` 블록은 클래스가 처음 로딩될 때 한 번만 실행됩니다. 바뀌지 않는 참조 데이터이므로 요청마다 공항 목록 API를 부르지 않습니다.
2. `register("NAARKPK", "김해", "부산")`: 이름마다 "X", "X공항", "X국제공항" 세 가지 키를 같은 코드로 등록하고, 별칭("부산")도 한 줄에 적습니다.
3. 이미 코드 형식(`NAARK` + 대문자 2개)이면 그대로 돌려주므로, LLM이 코드를 넘겨도 동작합니다.
4. 표에 없는 이름은 추측하지 않고 `IllegalArgumentException`을 던집니다.

`AirportListAgent`는 이 메서드를 그대로 호출합니다. 공항 목록은 `ApiClientService`에, 공항 코드는 `AirportCodeAgent`에 맡기는 얇은 에이전트입니다.

> **생각해 보기**: 공항 목록 Tool(`getAirportList`)은 공공데이터 포털의 실시간 목록을, 공항 코드 Tool(`getAirportCode`)은 코드에 고정된 표를 씁니다. 공공데이터 포털에 새 공항이 추가되면 두 Tool의 답이 어떻게 어긋날까요?

---

## Step 7-4: AirportInfoTool

`AirportInfoTool`의 전체 코드는 Step 5-2에서 이미 보았습니다. 여기서는 description이 LLM의 선택에 어떤 역할을 하는지에 집중합니다.

`src/main/java/net/java21/flyschedule/mcp/AirportInfoTool.java` (일부)

```java
@Tool(
        description = "공항 이름으로 공항 코드를 조회합니다. " +
                "공항 이름을 입력하면 공항 코드(예: NAARKJJ)를 반환합니다. " +
                "지원하는 공항: 김포, 인천, 김해, 광주, 제주, 대구, 청주, 양양, 울산, 여수, 사천, 포항, 무안, 군산, 원주"
)
public String getAirportCode(
        @ToolParam(description = "공항 이름 (예: 광주, 김포, 제주)") String airportName) {

    log.info("MCP Tool 호출: getAirportCode(airportName={})", airportName);

    return ToolResultCapture.capture("getAirportCode", airportName, () -> airportListAgent.getAirportCode(airportName));
}
```

**코드 읽기**

1. description의 "지원하는 공항" 목록은 `AirportCodeAgent`의 표와 같은 15개 공항입니다. LLM은 이 목록을 보고 "서울" 대신 "김포"를 넘기는 식으로 인자를 고칠 수 있습니다.
2. 알 수 없는 공항이면 `AirportCodeAgent`의 예외가 `capture()`를 지나 실패로 기록되고, Spring AI가 그 메시지를 LLM에게 전달합니다.
3. 결과가 `String`이므로 실행 기록(trace)의 TOOL 단계에는 남지만, 결과 표(`results`)는 만들지 않습니다. `ToolCallingSearchService.lastFlightResult()`는 항공편 `Map`을 돌려준 호출만 고르기 때문입니다.

---

## Step 7-5: AirlineListAgent와 AirlineInfoTool

### 왜 필요한가

항공사 ID는 고정 표가 아니라 공공데이터 포털의 항공사 목록에서 찾습니다. 사용자가 "아시아나"처럼 이름 일부만 말해도 찾을 수 있어야 합니다.

### 코드 보기

`src/main/java/net/java21/flyschedule/service/agent/AirlineListAgent.java` (일부)

```java
public String getAirlineId(String airlineName) {
    log.info("AirlineListAgent: 항공사 ID 조회 - {}", airlineName);

    // 항공사 목록에서 검색
    List<AirlineInfoResponse> airlines = apiClientService.getAirlineList();

    return airlines.stream()
            .filter(airline -> airline.getAirlineName() != null && airline.getAirlineName().contains(airlineName))
            .findFirst()
            .map(AirlineInfoResponse::getAirlineId)
            .orElseThrow(() -> new IllegalArgumentException("알 수 없는 항공사입니다: " + airlineName));
}
```

`src/main/java/net/java21/flyschedule/mcp/AirlineInfoTool.java` (일부)

```java
@Tool(
        description = "항공사 이름으로 항공사 ID를 조회합니다. " +
                "항공사 이름을 입력하면 항공사 ID(예: AAR, KAL)를 반환합니다. " +
                "지원하는 항공사: 대한항공, 아시아나항공, 제주항공, 에어부산, 에어서울, 진에어, 티웨이항공 등"
)
public String getAirlineId(
        @ToolParam(description = "항공사 이름 (예: 아시아나항공, 대한항공, 제주항공)") String airlineName) {

    log.info("MCP Tool 호출: getAirlineId(airlineName={})", airlineName);

    return ToolResultCapture.capture("getAirlineId", airlineName, () -> airlineListAgent.getAirlineId(airlineName));
}
```

**코드 읽기**

1. `contains(airlineName)`: 부분 일치로 찾으므로 "아시아나"로도 "아시아나항공"을 찾습니다. 여러 항공사가 일치하면 목록에서 처음 나온 항공사를 고릅니다.
2. `airline.getAirlineName() != null`: API 응답에 이름 없는 항공사가 섞여 있어도 `NullPointerException` 없이 건너뜁니다.
3. 호출할 때마다 `apiClientService.getAirlineList()`로 공공데이터 포털을 부릅니다(실습 과제 3에서 개선합니다).
4. `getAirlineList()` Tool은 `airlineListAgent::getAirlineList`를 `capture()`에 넘기기만 합니다.

### 확인하기

```bash
curl -G "http://localhost:8080/api/mcp/airlines/id" --data-urlencode "airlineName=아시아나"
curl -G "http://localhost:8080/api/mcp/airlines/id" --data-urlencode "airlineName=없는항공"
```

첫 번째는 `"airlineId"`에 아시아나항공의 ID가 담겨 나오고, 두 번째는 HTTP 400과 `"알 수 없는 항공사입니다: 없는항공"`이 나옵니다(서비스 키 필요).

---

## Step 7-6: 여러 Tool을 한 요청에 넘기기

### 왜 필요한가

사용자 질문 하나에 공항 정보와 항공편 검색이 함께 필요할 수 있습니다. Tool 클래스 3개를 한 요청에 넘기면, LLM이 `@Tool` 메서드 7개의 description을 보고 필요한 것을 골라 부릅니다.

| 비교 항목 | Spring MVC | Spring AI Tool |
|-----------|-----------|----------------|
| 어떤 메서드를 부를지 | URL 경로로 개발자가 지정 | LLM이 description을 보고 판단 |
| 등록 방법 | `@RequestMapping("/api/...")` | `.tools(tool1, tool2, ...)` |
| 파라미터 전달 | HTTP 요청에서 추출 | LLM이 문장에서 추출 |

### 코드 보기

`src/main/java/net/java21/flyschedule/service/toolcalling/ToolCallingSearchService.java` (일부)

```java
String answer = chatClientProvider.get(model).prompt()
        .system(systemPrompt())
        .user(message)
        .tools(flightSearchTool, airportInfoTool, airlineInfoTool)
        .call()
        .content();
```

**코드 읽기**

1. Tool 클래스 3개를 넘기면 Spring AI가 각 클래스의 `@Tool` 메서드 7개를 찾아 LLM에게 알립니다.
2. `ChatClientConfig`는 `SimpleLoggerAdvisor`만 기본 등록하고 Tool은 등록하지 않습니다(Step 2-1, Step 5-4). 그래서 조건 추출·계획·요약 같은 다른 LLM 호출은 Tool을 부르지 않습니다.

### 확인하기

```bash
curl -X POST http://localhost:8080/api/search/tool-calling \
  -H "Content-Type: application/json" \
  -d '{"message": "광주 공항 코드가 뭐야? 그리고 내일 광주에서 제주 항공편 알려줘", "model": "ollama"}'
```

응답 `trace`의 TOOL 단계에서 `getAirportCode`와 `searchFlightsByAirline`이 어떤 순서로 호출되었는지 확인합니다. 모델에 따라 공항 코드 조회를 건너뛰고 바로 항공편을 검색하기도 합니다. 다음에 무엇을 할지 LLM이 정한다는 점이 Step 10에서 배울 Tool Calling 에이전트의 핵심입니다.

화면(`http://localhost:8080`)에서 Tool Calling 에이전트를 골라 같은 문장을 검색하면, 실행 기록 타임라인에서 LLM 단계 아래에 TOOL 단계가 순서대로 나타납니다.

---

## 전체 구조 정리

| 계층 | 클래스 | 책임 |
|------|--------|------|
| Tool | `FlightSearchTool`, `AirportInfoTool`, `AirlineInfoTool` | LLM의 입구, `ToolResultCapture`로 호출 기록 (`FlightSearchTool`은 항공사별 3편 제한도 담당) |
| 묶는 에이전트 | `FlightSearchAgent`, `AirportListAgent`, `AirlineListAgent` | Tool의 요청을 받아 다른 에이전트와 `ApiClientService`를 호출 |
| 한 가지 일만 하는 에이전트 | `DateParserAgent`, `AirportCodeAgent`, `GroupingAgent`, `TimeFilterAgent`, `PriceFilterAgent` | 변환·필터·그룹핑 |
| 외부 API | `ApiClientService` | 공공데이터 포털 호출, 응답 변환 |

모든 에이전트는 `service/agent` 패키지의 전문 에이전트이며 LLM을 쓰지 않습니다. 이 구조에서 LLM이 판단하는 곳은 "어떤 Tool을 어떤 인자로 부를지" 하나뿐입니다.

---

## 일반적인 문제 해결

### 문제 1: "알 수 없는 공항입니다: 서울"

`AirportCodeAgent`는 공항 이름만 압니다. "서울"처럼 공항이 여러 개인 도시 이름은 일부러 등록하지 않았습니다. "김포"나 "인천"으로 입력합니다. "광주공항", "광주국제공항"처럼 접미사가 붙은 이름은 `register()`가 함께 등록하므로 문제없습니다.

### 문제 2: 공항·항공사 목록이 비어 있음

목록 조회는 `resultCode`가 `"00"`이 아니면 빈 목록을 돌려줍니다(Step 7-2의 주의). 서비스 키가 이 API에 대해 활용 승인되었는지, 애플리케이션 로그에 `공항 목록 API 호출` 다음 오류가 없는지 확인합니다. 키가 아예 없으면 빈 목록이 아니라 "서비스 키가 설정되지 않았습니다" 오류가 납니다.

### 문제 3: LLM이 공항 코드 Tool을 쓰지 않고 바로 검색함

정상입니다. 항공편 검색 Tool이 공항 이름을 직접 받아 내부에서 코드로 바꾸므로, LLM은 코드 조회가 필요 없다고 판단할 수 있습니다. Tool 선택은 LLM이 정합니다.

---

## 실습 과제

1. **(기본) 부분 일치와 접미사 확인하기**: `McpController`로 항공사 이름 일부와 접미사가 붙은 공항 이름을 조회하세요.
   - 힌트: `curl -G "http://localhost:8080/api/mcp/airlines/id" --data-urlencode "airlineName=아시아나"`, `curl -G "http://localhost:8080/api/mcp/airports/code" --data-urlencode "airportName=김해국제공항"`
   - 완료 기준: "아시아나"로 아시아나항공의 ID가, "김해국제공항"으로 `NAARKPK`가 나옵니다.

2. **(기본) 결과 표가 비는 이유 설명하기**: 화면(`http://localhost:8080`)에서 Tool Calling 에이전트로 "제주항공 ID가 뭐야?"를 검색하고, 실행 기록에서 LLM이 고른 Tool을 확인하세요.
   - 힌트: `ToolCallingSearchService.lastFlightResult()`
   - 완료 기준: TOOL 단계에 `getAirlineId`가 있고, 항공편 결과 표가 비어 있는 이유를 "`String` 결과는 `Map`이 아니어서 건너뛴다"는 말로 설명할 수 있습니다.

3. **(도전) 항공사 목록 캐시하기**: `AirlineListAgent.getAirlineId()`가 호출할 때마다 API를 부르지 않도록, 항공사 목록을 한 번만 조회해 재사용하게 바꾸세요(필드 캐시 또는 Spring `@Cacheable`).
   - 힌트: `src/main/java/net/java21/flyschedule/service/agent/AirlineListAgent.java`. 테스트는 `FlightSearchAgentTest`처럼 `@Mock ApiClientService`를 쓰면 `verify(apiClientService, times(1)).getAirlineList()`로 호출 횟수를 셀 수 있습니다.
   - 완료 기준: `getAirlineId()`를 두 번 불러도 `getAirlineList()`가 한 번만 호출되고, 이름이 `null`인 항공사가 섞인 목록에서도 원하는 ID를 찾는 테스트가 통과합니다.

4. **(도전) Tool 설명과 매핑 표 맞추기**: `AirportInfoTool`의 description에 적힌 15개 공항이 모두 `AirportCodeAgent`로 변환되는지 확인하는 테스트를 추가하세요.
   - 힌트: `src/test/java/net/java21/flyschedule/service/AirportCodeAgentTest.java`의 `@ParameterizedTest`와 `@ValueSource`
   - 완료 기준: `./mvnw test -Dtest=AirportCodeAgentTest`가 통과하고, 지금 테스트에서 정확한 코드로 비교하지 않는 공항(예: 사천은 테스트에 없고, 양양·여수는 `NAARK`로 시작하는지만 확인)도 기대 코드와 비교됩니다.

---

## 요약

- 공항·항공사 목록 API는 항공편 API와 같은 중첩 구조이며, `ApiClientService`의 내부 응답 래퍼 클래스로 매핑합니다. 목록 API의 오류 코드는 빈 목록이 된다는 점에 주의합니다.
- `AirportInfoTool`과 `AirlineInfoTool`은 각각 `AirportListAgent`, `AirlineListAgent`에게 일을 맡기고 `ToolResultCapture`로 호출을 기록합니다.
- `AirportCodeAgent`는 `static` 블록의 고정 표로 공항 이름("X", "X공항", "X국제공항")을 코드로 바꾸고, 모르는 이름은 예외로 거절합니다.
- `ToolCallingSearchService`는 Tool 클래스 3개(메서드 7개)를 한 요청에 넘기고, LLM이 그중 필요한 Tool을 골라 부릅니다.

Tool 쪽 구조가 모두 갖춰졌습니다. 다음 Step에서는 Tool 아래에서 실제 일을 하는 전문 에이전트를 하나씩 뜯어보며 설계 원칙과 테스트 방법을 정리합니다.

---

## 체크리스트

- [ ] `AirportInfoResponse`, `AirlineInfoResponse`의 필드 매핑과 `ApiClientService` 내부 응답 래퍼의 역할을 설명할 수 있습니다. (목표 1)
- [ ] `McpController`로 공항 코드·항공사 ID를 조회하고, 각 Tool이 어느 에이전트에게 일을 맡기는지 말할 수 있습니다. (목표 2)
- [ ] `register()`가 등록하는 세 가지 키와, 고정 표 방식의 한계를 설명할 수 있습니다. (목표 3)
- [ ] `POST /api/search/tool-calling`의 `trace`에서 LLM이 고른 TOOL 단계를 확인했습니다. (목표 4)

---

## 다음 단계

[Step 8: 전문 에이전트 설계와 구현](step-8-specialized-agents.md)에서 지금까지 만든 `service/agent` 클래스를 "한 가지 일만 하는 결정적 컴포넌트"라는 관점에서 다시 정리하고, Step 9~11의 세 가지 검색 패턴이 이들을 어떻게 재사용하는지 준비합니다.

---

## 참고 자료

- [국토교통부_(TAGO)_국내항공운항정보 API](https://www.data.go.kr/data/15098526/openapi.do) (공항 목록 `GetArprtList`, 항공사 목록 `GetAirmanList` 포함)
