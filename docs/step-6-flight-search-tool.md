# Step 6: 항공편 검색 Tool 구현

## 학습 목표

이 단계를 마치면 다음을 할 수 있습니다:

1. **RestClient**를 사용하여 외부 공공데이터 포털 API를 호출할 수 있습니다
2. **여러 에이전트를 체인으로 연결**하여 복잡한 검색 로직을 구성할 수 있습니다
3. **시간/가격 필터링 Tool**을 구현하여 조건부 검색을 할 수 있습니다
4. **ToolResultCapture**로 Tool 호출을 기록하여 화면용 결과와 실행 기록에 활용할 수 있습니다

> **핵심 개념**: 이 단계에서는 Step 5에서 배운 Tool 패턴을 실제 공공데이터 포털 API와 연동합니다.
> `FlightSearchTool`이 `FlightSearchAgent`에게 작업을 위임하고, 이 에이전트는 여러 하위 에이전트
> (DateParserAgent, AirportCodeAgent, GroupingAgent)를 체인으로 연결하여 동작합니다.

---

## 전체 흐름

```text
[사용자: "광주에서 제주 내일 항공편 알려줘"]
       ↓
[LLM: "FlightSearchTool을 호출해야겠다!"]
       ↓
[FlightSearchTool.searchFlightsByAirline("광주", "제주", "내일")]
       ↓
[FlightSearchAgent → DateParserAgent → AirportCodeAgent → ApiClientService.getFlightSchedule()]
       ↓
[GroupingAgent가 항공사별로 그룹핑 → FlightSearchTool이 항공사당 최대 3편만 반환]
       ↓
[결과를 LLM에게 전달 → LLM이 한국어로 정리 → 사용자에게 응답]
```

---

## RestClient란?

Spring Framework 6.1부터 도입된 **RestClient**는 동기식 HTTP 클라이언트를 위한 **현대적인 API**입니다.

### RestClient의 기술적 진화

**Spring의 HTTP 클라이언트 역사:**

```text
Spring 3.x   → RestTemplate    (복잡한 API)
Spring 5.x   → WebClient       (반응형, Non-blocking)
Spring 6.1+  → RestClient      (동기식, 간결한 API)
                                  ↓
                        RestTemplate의 사용성 +
                        WebClient의 현대성
```

### RestClient vs RestTemplate vs WebClient

| 항목 | RestTemplate | WebClient | RestClient |
|------|-------------|-----------|------------|
| **출시** | Spring 3.x | Spring 5.x | Spring 6.1 |
| **스타일** | 명령형 | 반응형 | 유창한 API |
| **차단/비차단** | 차단(Blocking) | 비차단(Non-blocking) | 차단(Blocking) |
| **복잡도** | 복잡함 | 중간 | 간결함 |
| **권장 사용** | 레거시 | 비동기 필요 시 | 일반적인 HTTP 호출 |

### RestClient 사용 시나리오

| 시나리오 | 적합한 클라이언트 | 이유 |
|---------|------------------|------|
| **일반적인 API 호출** | RestClient | 간결하고 직관적 |
| **대량의 병렬 요청** | WebClient | Non-blocking I/O |
| **레거시 코드** | RestTemplate | 이미 사용 중 |
| **간단한 외부 API 연동** | RestClient | 학습 곡선이 낮음 |

---

## 공공데이터 포털 API 소개

### 국내항공운항정보 API

| 항목 | 내용 |
|------|------|
| **API 이름** | 국내항공운항정보 |
| **제공기관** | 국토교통부 (TAGO) |
| **설명** | 국내 공항 간의 항공편 운항 스케줄 정보 |
| **URL** | https://apis.data.go.kr/1613000/DmstcFlightNvgInfo |

### 주요 엔드포인트

| 엔드포인트 | 설명 | 파라미터 |
|-----------|------|----------|
| `/GetFlightOpratInfoList` | 항공편 운항 정보 조회 | `depAirportId`, `arrAirportId`, `depPlandTime` |
| `/GetArprtList` | 공항 목록 조회 | - |
| `/GetAirmanList` | 항공사 목록 조회 | - |

> **참고:** 실제 API 엔드포인트는 `/GetFlightOpratInfoList`입니다. `/getDomesticFlightSchedule`은 존재하지 않습니다.

---

## Step 6-1: API 연동 설정

> **Spring Boot 복습**: `application.yml`에서 외부 API 설정값을 관리하고, `@ConfigurationProperties`로 읽어 오는 방법은 이미 배웠습니다.
> 공공데이터 포털 API도 같은 방식으로 설정합니다. `service-key`는 환경 변수로 관리하여 소스 코드에 노출되지 않게 합니다.

### application.yml 설정

```yaml
# 공공데이터 포털 API 설정
data-go-kr:
  api:
    url: https://apis.data.go.kr/1613000/DmstcFlightNvgInfo
    service-key: ${DATA_GO_KR_SERVICE_KEY:}  # 환경 변수에서 읽음 (기본값 없음)
```

> **보안 주의:** 서비스 키를 `application.yml`의 기본값으로 적거나 문서·저장소에 커밋하지 마세요. 실행 전에 `DATA_GO_KR_SERVICE_KEY` 환경 변수로 설정합니다.

### DataGoKrApiProperties.java

```java
package net.java21.flyschedule.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "data-go-kr.api")
public class DataGoKrApiProperties {

    private String url;
    private String serviceKey;
}
```

---

## Step 6-2: DTO 정의

> **Spring Boot 복습**: DTO(Data Transfer Object)는 데이터를 전달하기 위한 객체입니다.
> `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`는 Lombok 어노테이션으로,
> getter/setter, 생성자 등을 자동 생성합니다. `@JsonProperty`는 JSON 필드명과 Java 필드명을 매핑합니다.

### FlightInfoResponse.java

```java
package net.java21.flyschedule.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 항공편 정보 응답 DTO
 *
 * 공공데이터 포털 API 응답을 매핑합니다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightInfoResponse {

    /** 항공편명 (예: OZ8141) */
    @JsonProperty("vihicleId")
    private String flightId;

    /** 항공사명 (예: 아시아나항공) */
    @JsonProperty("airlineNm")
    private String airlineName;

    /** 출발시간 (예: 202503090955, YYYYMMDDHHmm 형식) */
    @JsonProperty("depPlandTime")
    private String departureTime;

    /** 도착시간 (예: 202503091045, YYYYMMDDHHmm 형식) */
    @JsonProperty("arrPlandTime")
    private String arrivalTime;

    /** 일반석 운임 */
    @JsonProperty("economyCharge")
    private Integer economyCharge;

    /** 비즈니스석 운임 */
    @JsonProperty("prestigeCharge")
    private Integer prestigeCharge;

    /** 출발공항명 */
    @JsonProperty("depAirportNm")
    private String departureAirport;

    /** 도착공항명 */
    @JsonProperty("arrAirportNm")
    private String arrivalAirport;

    /** 항공사 ID */
    @JsonProperty("airlineId")
    private String airlineId;
}
```

> **주의:** `depPlandTime`과 `arrPlandTime`은 `YYYYMMDDHHmm` 형식입니다(예: `"202503090955"`). 단순한 `"09:55"` 형식이 아닙니다. 시간 부분은 문자열의 뒤 4자리(HHmm)에서 추출해야 합니다.

### ApiResponseWrapper.java

API 응답은 중첩된 구조를 가집니다. `items` 안에 `item` 리스트가 있습니다.

```java
package net.java21.flyschedule.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 공공데이터 포털 API 응답 래퍼
 *
 * API 응답의 전체 구조를 매핑합니다.
 */
@Data
public class ApiResponseWrapper {

    @JsonProperty("response")
    private Response response;

    @Data
    public static class Response {
        @JsonProperty("header")
        private Header header;

        @JsonProperty("body")
        private Body body;
    }

    @Data
    public static class Header {
        @JsonProperty("resultCode")
        private String resultCode;

        @JsonProperty("resultMsg")
        private String resultMessage;
    }

    @Data
    public static class Body {
        @JsonProperty("items")
        private Items items;

        @JsonProperty("numOfRows")
        private Integer numOfRows;

        @JsonProperty("pageNo")
        private Integer pageNo;

        @JsonProperty("totalCount")
        private Integer totalCount;
    }

    @Data
    public static class Items {
        @JsonProperty("item")
        private List<FlightInfoResponse> item;
    }

    /** 결과 코드 가져오기 (편의 메서드) */
    public String getResultCode() {
        return response != null && response.header != null
                ? response.header.getResultCode()
                : null;
    }

    /** 결과 메시지 가져오기 (편의 메서드) */
    public String getResultMessage() {
        return response != null && response.header != null
                ? response.header.getResultMessage()
                : null;
    }

    /** 전체 카운트 가져오기 (편의 메서드) */
    public Integer getTotalCount() {
        return response != null && response.body != null
                ? response.body.getTotalCount()
                : null;
    }

    /** 아이템 목록 가져오기 (편의 메서드) */
    public List<FlightInfoResponse> getItems() {
        return response != null && response.body != null && response.body.items != null
                ? response.body.items.getItem()
                : null;
    }
}
```

> **주의:** 응답 구조는 `response.body.items.item` 형태의 중첩 구조입니다. `body`가 직접 `List<FlightInfoResponse>`를 가지는 것이 아닙니다.

---

## Step 6-3: API 클라이언트 구현

> **Spring Boot 복습**: `@Service`는 Spring이 관리하는 Bean을 만드는 어노테이션입니다.
> `ApiClientService`는 외부 공공데이터 포털 API를 호출하는 역할만 담당합니다.
> Spring Boot에서 배운 Service 계층과 동일한 역할입니다.

### ApiClientService.java

`src/main/java/net/java21/flyschedule/service/api/ApiClientService.java` (공항·항공사 목록 조회와 응답 래퍼 내부 클래스는 생략)

```java
package net.java21.flyschedule.service.api;

import net.java21.flyschedule.config.DataGoKrApiProperties;
import net.java21.flyschedule.dto.response.ApiResponseWrapper;
import net.java21.flyschedule.dto.response.FlightInfoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class ApiClientService {

    /**
     * 한 번에 받을 최대 건수. 김포→제주처럼 하루 100편이 넘는 노선이 있으므로(2026-10 기준 118편)
     * 100으로 두면 뒤쪽 항공편이 잘립니다.
     */
    static final int MAX_ROWS = 500;

    private final DataGoKrApiProperties properties;
    private final RestClient restClient;

    /**
     * Spring Boot가 만든 RestClient.Builder를 사용해야 application.yml의
     * spring.http.client.* (연결·읽기 타임아웃) 설정이 적용됩니다. RestClient.create()는 이 설정을 쓰지 않습니다.
     */
    public ApiClientService(DataGoKrApiProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    /**
     * GET 요청을 보내고 응답을 변환합니다. 서비스 키가 없거나 호출이 실패하면 ExternalApiException을 던집니다.
     */
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

    /**
     * 항공운항정보 조회
     *
     * @param depAirportId 출발공항ID (예: NAARKJJ - 광주)
     * @param arrAirportId 도착공항ID (예: NAARKPC - 제주)
     * @param depPlandTime 출발일 (예: 20250309)
     * @return 항공편 정보 목록
     */
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

        // URL에는 서비스 키가 포함되므로 URL 전체를 로그로 남기지 않습니다
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
}
```

`ExternalApiException`은 같은 패키지의 단순한 `RuntimeException`입니다.

```java
public class ExternalApiException extends RuntimeException {
    public ExternalApiException(String message) { super(message); }
    public ExternalApiException(String message, Throwable cause) { super(message, cause); }
}
```

**왜 이렇게 설계했을까요?**

- **`RestClient.create()`가 아니라 주입받은 `RestClient.Builder`**: Spring Boot가 자동 설정한 Builder에는 `spring.http.client.read-timeout`(Step 1) 같은 설정이 이미 적용되어 있습니다. `RestClient.create()`로 직접 만들면 이 설정을 쓰지 않습니다. 테스트에서는 같은 Builder에 `MockRestServiceServer`를 붙여 실제 API 없이 응답을 흉내 낼 수 있습니다(`ApiClientServiceTest`)
- **`numOfRows=MAX_ROWS(500)`**: 한 페이지만 받으므로, 하루 운항 편수보다 작게 잡으면 뒤쪽 항공편이 조용히 잘립니다. 김포→제주는 하루 100편이 넘습니다
- **실패를 빈 목록으로 숨기지 않음**: "항공편이 없다"와 "API를 호출하지 못했다"는 다른 상황입니다. 호출 실패나 오류 코드(`resultCode != "00"`)를 빈 목록으로 돌려주면 사용자는 항공편이 없다고 오해합니다. 그래서 `ExternalApiException`을 던지고, 세 검색 패턴은 `ChatClientProvider.describeError()`를 통해 이 메시지를 그대로 화면에 보여 줍니다
- **서비스 키 검사를 먼저**: 키가 비어 있으면 API를 부르지 않고 설정 방법을 안내합니다

> **참고:** 실제 엔드포인트는 `/GetFlightOpratInfoList`이며, Service Key는 인코딩하지 않고 그대로 사용합니다(공공데이터 포털 요구사항). 공항 목록(`getAirportList()`)과 항공사 목록(`getAirlineList()`)도 같은 `get()`을 거치므로, 키가 없거나 호출이 실패하면 똑같이 `ExternalApiException`이 납니다.

---

## Step 6-4: 전문 에이전트 구현

### 왜 여러 에이전트로 나눌까요?

"광주에서 제주 내일 항공편"이라는 사용자 요청을 처리하려면 여러 단계가 필요합니다.

```text
"내일" → 날짜 변환 (DateParserAgent)
"광주" → 공항 코드 (AirportCodeAgent)
결과 → 항공사별 분류 (GroupingAgent)
"오후 2시 이후" → 시간 필터 (TimeFilterAgent)
"3만원~7만원" → 가격 필터 (PriceFilterAgent)
```

각 에이전트가 **하나의 변환/처리만** 담당하면, 코드가 단순해지고 버그도 줄어듭니다.

> **용어 주의**: 이 교재는 이런 클래스를 **전문 에이전트**라고 부르지만, 모두 LLM을 쓰지 않는 결정적(deterministic) 컴포넌트입니다. 같은 입력에는 언제나 같은 결과를 내므로 단위 테스트로 검증할 수 있습니다(`src/test/java/net/java21/flyschedule/service/*AgentTest.java`). 전문 에이전트의 설계 원칙은 Step 8에서 다룹니다.

> **Spring Boot 복습**: Spring Boot에서 `@Service` 클래스 하나가 너무 많은 일을 하면
> 유지보수가 어려워집니다. 단일 책임 원칙(SRP)을 지키기 위해 여러 Service로 나누는 것과 같습니다.

### DateParserAgent.java

자연어 날짜 표현을 API 요청 형식(YYYYMMDD)으로 변환합니다.

```java
package net.java21.flyschedule.service.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 날짜 파싱 에이전트
 *
 * 지원하는 표현: "오늘", "내일", "모레", "글피", "2026-03-25"
 */
@Slf4j
@Service
public class DateParserAgent {

    private static final DateTimeFormatter API_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter INPUT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public String parseDate(String dateInput) {
        if (dateInput == null || dateInput.isBlank()) {
            return LocalDate.now().format(API_DATE_FORMATTER);
        }

        String normalized = dateInput.trim();

        // 이미 API 형식(YYYYMMDD)인 경우 그대로 반환
        if (normalized.matches("\\d{8}")) {
            return normalized;
        }

        normalized = normalized.toLowerCase();

        return switch (normalized) {
            case "오늘" -> LocalDate.now().format(API_DATE_FORMATTER);
            case "내일" -> LocalDate.now().plusDays(1).format(API_DATE_FORMATTER);
            case "모레", "내일모레" -> LocalDate.now().plusDays(2).format(API_DATE_FORMATTER);
            case "글피" -> LocalDate.now().plusDays(3).format(API_DATE_FORMATTER);
            default -> parseSpecificDate(normalized);   // 앞뒤 공백을 지운 값으로 변환 ("  2025-03-10  " → "20250310")
        };
    }

    private String parseSpecificDate(String dateInput) {
        try {
            LocalDate date = LocalDate.parse(dateInput, INPUT_DATE_FORMATTER);
            return date.format(API_DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "날짜 형식이 올바르지 않습니다. (YYYY-MM-DD 또는 '내일', '모레' 등)");
        }
    }
}
```

### AirportCodeAgent.java

공항 이름을 공항 코드로 변환합니다.

> **Java 복습**: `static { }` 블록은 클래스가 로드될 때 한 번 실행되는 초기화 블록입니다.
> `HashMap`에 공항 이름과 코드를 미리 매핑해 두어, 빠르게 조회할 수 있습니다.
> `register()` 도우미 메서드는 "광주", "광주공항", "광주국제공항"처럼 이름 변형을 한꺼번에 등록합니다.

```java
package net.java21.flyschedule.service.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 공항 코드 매핑 에이전트
 */
@Slf4j
@Service
public class AirportCodeAgent {

    /**
     * 공항 이름 → 공항 코드
     *
     * <p>코드는 공공데이터 포털 국내항공운항정보 API의 공항 목록(GetArprtList) 기준입니다.
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

    // "X", "X공항", "X국제공항"을 모두 같은 코드로 등록합니다
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
            return normalized;
        }

        String code = AIRPORT_CODE_MAP.get(normalized);
        if (code == null) {
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

> **참고:** 공항 코드 매핑은 `AirportCodeAgent`의 `static Map`에서 관리합니다. `FlightSearchTool`에 하드코딩되어 있지 않습니다. 또한 공항 코드 형식(예: `NAARKJJ`)이 입력되면 그대로 반환합니다.

### GroupingAgent.java

항공편을 항공사별로 그룹핑합니다.

```java
package net.java21.flyschedule.service.agent;

import net.java21.flyschedule.dto.response.FlightInfoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

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
                                String flightId = flight.getFlightId();
                                if (flightId != null && flightId.length() >= 2) {
                                    return flightId.substring(0, 2);
                                }
                                return "알 수 없는 항공사";
                            }
                            return airlineName;
                        },
                        TreeMap::new,
                        Collectors.toList()
                ));

        log.info("그룹핑 완료: {}개 항공사, {}개 항공편", grouped.size(), flights.size());
        return grouped;
    }
}
```

### TimeFilterAgent.java

출발 시간을 기준으로 항공편을 필터링합니다.

```java
package net.java21.flyschedule.service.agent;

import net.java21.flyschedule.dto.response.FlightInfoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TimeFilterAgent {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmm");

    // "오후 2시", "오전 9시 30분", "14시" 형식
    private static final Pattern KOREAN_TIME_PATTERN =
            Pattern.compile("(오전|오후)?\\s*(\\d{1,2})\\s*시(?:\\s*(\\d{1,2})\\s*분)?");

    // "14:00", "1400", "9:30" 형식
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
                        return !departureTime.isBefore(afterTime);
                    } catch (Exception e) {
                        log.warn("시간 파싱 실패: {}", flight.getDepartureTime());
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }

    private LocalTime parseDepartureTime(String departureTime) {
        if (departureTime == null || departureTime.length() < 8) {
            throw new IllegalArgumentException("잘못된 출발 시간 형식");
        }

        // "202503090955" 형식에서 시간 부분(0955) 추출
        String timePart = departureTime.substring(8, 12);
        return LocalTime.parse(timePart, TIME_FORMATTER);
    }
}
```

> **참고:** 실제 소스에는 기준 시간 이전 항공편을 고르는 `filterBeforeTime()`도 있습니다(여기서는 생략).

### PriceFilterAgent.java

운임을 기준으로 항공편을 필터링합니다.

```java
package net.java21.flyschedule.service.agent;

import net.java21.flyschedule.dto.response.FlightInfoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PriceFilterAgent {

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
                    if (price == null || price == 0) return false;
                    if (minPrice != null && price < minPrice) return false;
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

    public double calculateAveragePrice(List<FlightInfoResponse> flights) {
        return flights.stream()
                .filter(f -> f.getEconomyCharge() != null && f.getEconomyCharge() > 0)
                .mapToInt(FlightInfoResponse::getEconomyCharge)
                .average()
                .orElse(0.0);
    }
}
```

---

## Step 6-5: FlightSearchAgent 구현

`FlightSearchAgent`는 여러 하위 에이전트를 정해진 순서로 호출하여 항공편 검색 작업을 수행하는 **상위 에이전트**입니다.

### 왜 상위 에이전트가 필요할까요?

Tool(FlightSearchTool)은 "AI 모델이 호출하는 진입점"이므로 최대한 얇게 유지해야 합니다.
`FlightSearchAgent`는 **어떤 순서로 어떤 에이전트를 호출할지 조율**하는 역할을 담당합니다.

```text
Tool: "광주, 제주, 내일" → FlightSearchAgent에게 전달
                                    ↓
                    FlightSearchAgent가 순서대로 조율:
                    1. DateParserAgent: "내일" → "20260310"
                    2. AirportCodeAgent: "광주" → "NAARKJJ"
                    3. ApiClientService: getFlightSchedule() API 호출
                    4. GroupingAgent: 항공사별 분류
```

```java
package net.java21.flyschedule.service.agent;

import net.java21.flyschedule.dto.response.FlightInfoResponse;
import net.java21.flyschedule.service.api.ApiClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.*;
import java.util.function.UnaryOperator;

/**
 * 항공편 검색 에이전트
 *
 * 여러 하위 에이전트를 조율:
 * - DateParserAgent: 날짜 파싱
 * - AirportCodeAgent: 공항 코드 변환
 * - TimeFilterAgent: 시간 필터링
 * - PriceFilterAgent: 가격 필터링
 * - GroupingAgent: 항공사별 그룹핑
 */
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

    /**
     * 항공편을 검색하여 항공사별로 그룹핑합니다.
     */
    public Map<String, List<FlightInfoResponse>> searchAndGroupByAirline(
            String departure, String arrival, String date) {

        // 1. DateParserAgent: 날짜 파싱 ("내일" → "20260310")
        String parsedDate = dateParserAgent.parseDate(date);

        // 2. AirportCodeAgent: 공항 코드 변환 ("광주" → "NAARKJJ")
        String depAirportId = airportCodeAgent.getAirportCode(departure);
        String arrAirportId = airportCodeAgent.getAirportCode(arrival);

        // 3. API 호출
        List<FlightInfoResponse> flights = apiClientService.getFlightSchedule(
                depAirportId, arrAirportId, parsedDate);

        // 4. GroupingAgent: 항공사별 그룹핑
        return groupingAgent.groupByAirline(flights);
    }

    /**
     * 시간 필터를 적용하여 항공편을 검색합니다.
     */
    public Map<String, List<FlightInfoResponse>> searchWithTimeFilter(
            String departure, String arrival, String date, String afterTime) {

        Map<String, List<FlightInfoResponse>> grouped =
                searchAndGroupByAirline(departure, arrival, date);

        LocalTime filterTime = timeFilterAgent.parseTime(afterTime);

        // 각 항공사별로 필터링 적용
        return filterEachAirline(grouped, flights -> timeFilterAgent.filterAfterTime(flights, filterTime));
    }

    /**
     * 가격 필터를 적용하여 항공편을 검색합니다.
     */
    public Map<String, List<FlightInfoResponse>> searchWithPriceFilter(
            String departure, String arrival, String date,
            Integer minPrice, Integer maxPrice) {

        Map<String, List<FlightInfoResponse>> grouped =
                searchAndGroupByAirline(departure, arrival, date);

        // 각 항공사별로 필터링 적용
        return filterEachAirline(grouped, flights -> priceFilterAgent.filterByPriceRange(flights, minPrice, maxPrice));
    }

    /**
     * 항공사별 목록에 필터를 적용하고, 남은 항공편이 없는 항공사는 결과에서 뺍니다.
     */
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

### 에이전트 체인 흐름

```text
searchAndGroupByAirline("광주", "제주", "내일")
  │
  ├─ DateParserAgent: "내일" → "20260310"
  ├─ AirportCodeAgent: "광주" → "NAARKJJ", "제주" → "NAARKPC"
  ├─ ApiClientService: /GetFlightOpratInfoList API 호출
  └─ GroupingAgent: 항공사별 그룹핑

searchWithTimeFilter("광주", "제주", "내일", "오후 2시")
  │
  ├─ searchAndGroupByAirline() → 기본 검색
  ├─ TimeFilterAgent.parseTime("오후 2시") → 14:00
  └─ TimeFilterAgent.filterAfterTime() → 14:00 이후 항공편만

searchWithPriceFilter("광주", "제주", "내일", 30000, 70000)
  │
  ├─ searchAndGroupByAirline() → 기본 검색
  └─ PriceFilterAgent.filterByPriceRange() → 3만~7만원 항공편만
```

> **참고: FlightSearchAgent와 FlightSearchCoordinator**
> `FlightSearchAgent`는 Tool(`FlightSearchTool`)이 호출하는 진입점입니다. Step 9에서 배울 `FlightSearchCoordinator`도 날짜 변환 → 공항 코드 변환 → API 호출 → 필터 → 그룹핑을 같은 전문 에이전트로 처리하지만, 단계마다 실행 기록(`ExecutionTrace`)을 남기고 시간·가격 조건을 한 번에 적용한다는 점이 다릅니다.

> **왜 `filterEachAirline()`으로 다시 모을까요?** 단순히 `Collectors.toMap()`으로 모으면 결과가 `HashMap`이 되어 `GroupingAgent`가 만든 `TreeMap`의 항공사 이름 순서가 사라지고, 조건에 맞는 항공편이 하나도 없는 항공사도 빈 목록(`"대한항공": []`)으로 남습니다. LLM은 빈 목록을 보고 "대한항공 항공편이 있다"고 잘못 요약하기도 합니다. 그래서 `TreeMap`에 담아 순서를 유지하고, 남은 항공편이 없는 항공사는 아예 뺍니다.

### Spring Boot 계층 구조와 비교해 보기

| Spring Boot 계층 | Spring AI 프로젝트 계층 | 역할 |
|------------------|------------------------|------|
| Controller | Tool (`FlightSearchTool`) | 외부(웹/AI)에서 호출하는 진입점 |
| Service | 에이전트 (`FlightSearchAgent`) | 비즈니스 로직 조율 |
| Repository | ApiClientService | 외부 데이터 소스 접근 |
| 유틸리티 | 하위 에이전트 (`DateParserAgent` 등) | 단일 기능 변환/처리 |

---

## Step 6-6: FlightSearchTool 구현

`FlightSearchTool`은 MCP 진입점으로, `FlightSearchAgent`에게 작업을 위임하고 `ToolResultCapture`로 호출을 기록하면서 결과를 돌려줍니다.

### 왜 항공사별로 최대 3편만 반환할까요?

AI 모델(LLM)은 반환받은 데이터를 모두 읽고 자연어로 요약합니다.
데이터가 너무 많으면:
- 응답 시간이 길어지고 (타임아웃 발생 가능)
- AI 모델이 중요한 정보를 놓칠 수 있습니다

그래서 항공사별로 **대표 항공편 3개**만 반환하는 것입니다.

`src/main/java/net/java21/flyschedule/mcp/FlightSearchTool.java` (메서드 주석 생략)

```java
package net.java21.flyschedule.mcp;

import net.java21.flyschedule.dto.response.FlightInfoResponse;
import net.java21.flyschedule.service.agent.FlightSearchAgent;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 항공편 검색 MCP Tool
 *
 * Spring AI의 @Tool annotation을 사용하여 AI 모델이 호출할 수 있는 도구를 정의합니다.
 * 실제 검색은 전문 에이전트(FlightSearchAgent)에게 맡기고 결과를 반환합니다.
 */
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

    @Tool(
            description = "특정 시간 이후 출발하는 항공편을 검색하여 항공사별로 그룹핑합니다. " +
                    "시간 조건을 만족하는 항공편만 필터링하여 제공합니다. " +
                    "시간 형식은 '14:00', '오후 2시' 등을 지원합니다."
    )
    public Map<String, List<FlightInfoResponse>> searchFlightsAfterTime(
            @ToolParam(description = "출발 공항 이름 (예: 광주, 김포, 제주)") String departure,
            @ToolParam(description = "도착 공항 이름 (예: 제주, 김포, 부산)") String arrival,
            @ToolParam(description = "날짜, YYYY-MM-DD 형식 (예: 2026-03-25)") String date,
            @ToolParam(description = "기준 시간 (예: 14:00, 오후 2시)") String afterTime) {

        log.info("MCP Tool 호출: searchFlightsAfterTime(departure={}, arrival={}, date={}, afterTime={})",
                departure, arrival, date, afterTime);

        return ToolResultCapture.capture("searchFlightsAfterTime",
                "%s → %s, %s, %s 이후".formatted(departure, arrival, date, afterTime),
                () -> limitPerAirline(flightSearchAgent.searchWithTimeFilter(departure, arrival, date, afterTime)));
    }

    @Tool(
            description = "특정 가격 범위 내의 항공편을 검색하여 항공사별로 그룹핑합니다. " +
                    "예산에 맞는 항공편만 필터링하여 제공합니다. " +
                    "최소 가격과 최대 가격은 선택 사항입니다."
    )
    public Map<String, List<FlightInfoResponse>> searchFlightsByPriceRange(
            @ToolParam(description = "출발 공항 이름 (예: 광주, 김포, 제주)") String departure,
            @ToolParam(description = "도착 공항 이름 (예: 제주, 김포, 부산)") String arrival,
            @ToolParam(description = "날짜, YYYY-MM-DD 형식 (예: 2026-03-25)") String date,
            @ToolParam(description = "최소 가격 (선택사항, 예: 30000)") Integer minPrice,
            @ToolParam(description = "최대 가격 (선택사항, 예: 70000)") Integer maxPrice) {

        log.info("MCP Tool 호출: searchFlightsByPriceRange(departure={}, arrival={}, date={}, minPrice={}, maxPrice={})",
                departure, arrival, date, minPrice, maxPrice);

        return ToolResultCapture.capture("searchFlightsByPriceRange",
                "%s → %s, %s, %s~%s원".formatted(departure, arrival, date, minPrice, maxPrice),
                () -> limitPerAirline(flightSearchAgent.searchWithPriceFilter(departure, arrival, date, minPrice, maxPrice)));
    }

    /**
     * LLM에게 돌려줄 결과가 너무 길어지지 않도록 항공사별로 최대 MAX_FLIGHTS_PER_AIRLINE편만 남깁니다.
     * (소형 모델은 컨텍스트가 짧아 결과가 길면 응답이 느려지거나 잘립니다.)
     */
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

> **참고**: 세 검색 Tool 모두 결과를 `limitPerAirline()`에 통과시킵니다. 시간·가격 필터를 거친 결과도 한 항공사에 수십 편이 남을 수 있기 때문입니다(김포→제주는 하루 100편이 넘습니다). 화면에 보여 주는 Coordinator·Orchestrator 패턴의 결과는 이 Tool을 거치지 않으므로 제한이 없습니다.
>
> 날짜 파라미터 설명을 `YYYY-MM-DD 형식 (예: 2026-03-25)`으로 정한 이유는 Step 5-6을 참고하세요.

### ToolResultCapture로 호출 기록하기

세 메서드 모두 `return ToolResultCapture.capture(Tool 이름, 인자 요약, () -> 작업)` 형태로 끝납니다. `capture()`는 넘겨받은 작업을 실행하면서 호출 기록(결과 또는 실패 메시지, 실행 시작·종료 시각)을 `ThreadLocal`의 `List<ToolCall>`에 쌓고 결과를 그대로 돌려주므로, 실행·기록·반환을 한 줄로 처리합니다. 공공데이터 포털 호출이 실패해 `ExternalApiException`이 나면 실패로 기록한 뒤 예외를 다시 던지고, Spring AI가 그 오류를 LLM에게 전달합니다. 인자 요약(예: `"광주 → 제주, 2026-10-16"`)은 화면의 실행 기록에 "LLM이 어떤 인자로 Tool을 불렀는가"를 보여 주는 데 쓰입니다. `ToolResultCapture` 클래스 전체 코드와 설계 이유는 Step 5-3을 참고하세요.

---

## Step 6-7: ChatClient에 Tool 전달

> **Spring Boot 복습**: `FlightSearchTool`은 `@Component`로 등록된 Bean이므로, Tool을 쓰는 서비스가 생성자로 주입받을 수 있습니다.
> Tool은 빌더의 `.defaultTools()`(모든 요청)나 요청의 `.tools()`(그 요청만)로 ChatClient에 넘깁니다. (Step 3-2)

이 프로젝트는 `ChatClientConfig`에 Tool을 기본 등록하지 않고, Tool을 쓰는 `ToolCallingSearchService`에서 요청마다 넘깁니다.

`src/main/java/net/java21/flyschedule/service/toolcalling/ToolCallingSearchService.java` (일부)

```java
@Service
@RequiredArgsConstructor
public class ToolCallingSearchService {

    private final ChatClientProvider chatClientProvider;
    private final FlightSearchTool flightSearchTool;
    private final AirportInfoTool airportInfoTool;
    private final AirlineInfoTool airlineInfoTool;

    public SearchResponse search(String message, String model) {
        // ... 생략
        String answer = chatClientProvider.get(model).prompt()
                .system(systemPrompt())
                .user(message)
                .tools(flightSearchTool, airportInfoTool, airlineInfoTool)  // 이 요청에만 Tool 전달
                .call()
                .content();
        // ... 생략 (ToolResultCapture.getAndClear()로 호출 기록을 꺼내 실행 기록에 남김)
    }
}
```

같은 `ChatClient`를 쓰는 `SearchCriteriaExtractor`(검색 조건 추출)나 `ResultSynthesizer`(결과 요약)에는 `.tools(...)`가 없으므로, 이 호출들은 LLM이 Tool을 부를 수 없습니다. `defaultTools`로 등록했다면 이 호출에도 Tool 7개의 정의가 붙어, 검색 조건만 뽑아야 할 LLM이 직접 항공편을 검색하는 일이 생길 수 있습니다.

### 실행해 보기

```bash
curl -X POST http://localhost:8080/api/search/tool-calling \
  -H "Content-Type: application/json" \
  -d '{"message": "내일 광주에서 제주로 가는 오후 2시 이후 항공편 알려줘"}'
```

응답의 `trace`에서 `kind`가 `TOOL`인 단계를 보면 LLM이 `searchFlightsByAirline`과 `searchFlightsAfterTime` 중 무엇을 골랐는지 확인할 수 있습니다. Tool Calling 에이전트 전체 구조는 Step 10에서 다룹니다.

---

## 일반적인 문제 해결

### 문제 1: API 응답이 없음

**증상:** 응답 `message`가 "공공데이터 포털 서비스 키가 설정되지 않았습니다…", "공공데이터 포털 API를 호출하지 못했습니다…", "공공데이터 포털 API 오류: …" 중 하나이거나, 성공했는데 항공편이 0편

**해결 방법:**
1. 앞의 세 메시지는 `ApiClientService`가 던진 `ExternalApiException`입니다. 서비스 키 환경 변수(`DATA_GO_KR_SERVICE_KEY`)와 네트워크, 메시지에 담긴 API 오류 내용을 확인합니다
2. 0편이라면 파라미터 형식(YYYYMMDD)과 공항 코드(NAARKJJ 등)를 확인하고, 그 날짜에 실제 운항편이 있는 노선인지 확인합니다

### 문제 2: "SERVICE KEY IS NOT REGISTERED ERROR"

**해결 방법:**
1. 공공데이터 포털에서 활용 신청
2. Decoding 키 사용 (URL 인코딩된 키 X)

### 문제 3: 응답 시간이 너무 김

**해결 방법:**
1. Tool 결과를 항공사별 최대 3편으로 제한 (`limitPerAirline()`)
2. 캐싱 적용
3. 비동기 처리

### 문제 4: depPlandTime 시간 파싱 오류

**증상:** 시간 필터링이 동작하지 않음

**해결 방법:**
- `depPlandTime`은 `"202503090955"` 형식이므로, 시간 부분은 `substring(8, 12)`로 추출
- `TimeFilterAgent.parseDepartureTime()`에서 올바르게 파싱되는지 확인

---

## 실습 과제

1. **(기본)** `McpController`로 Tool을 직접 호출해 `searchFlightsByAirline`과 `searchFlightsAfterTime`의 결과를 비교하세요. 두 결과 모두 항공사별 최대 3편인지, 시간 필터 뒤 항공편이 남지 않은 항공사는 빠졌는지 확인하세요. 예: `curl "http://localhost:8080/api/mcp/flights/search/time?departure=광주&arrival=제주&date=내일&afterTime=14:00"`
2. **(기본)** `DateParserAgentTest`에 "글피"와 "2026-12-31"이 각각 올바른 `yyyyMMdd` 값으로 바뀌는지, "다음 주"처럼 지원하지 않는 표현은 `IllegalArgumentException`을 던지는지 확인하는 테스트를 추가하세요.
3. **(도전)** `ApiClientServiceTest`(`MockRestServiceServer` 사용)를 참고해, API가 `resultCode`는 `"00"`이지만 `items`가 비어 있는 응답을 돌려줄 때 `getFlightSchedule()`이 예외 없이 빈 목록을 돌려주는지 확인하는 테스트를 추가하세요. "API 오류"와 "항공편 없음"을 구분하는 기준이 코드의 어디에 있는지 설명하세요.
4. **(도전)** Tool Calling 경로에서는 LLM이 공항 이름을 "제주도"처럼 넘길 수 있습니다. `AirportCodeAgent`가 "제주도"를 제주 공항(`NAARKPC`)으로 인식하도록 등록하고 `AirportCodeAgentTest`에 테스트를 추가하세요. (정규식 경로인 `SearchCriteriaExtractor`는 "제주도"에 포함된 "제주"를 이미 찾아내므로 영향을 받지 않습니다.)

---

## 요약

이 단계에서 배운 핵심 내용을 정리합니다.

1. **RestClient**: Spring 6.1의 현대적인 HTTP 클라이언트로, 공공데이터 포털 API를 호출합니다
2. **에이전트 체인**: DateParserAgent → AirportCodeAgent → ApiClientService → GroupingAgent가 순차적으로 동작합니다
3. **FlightSearchAgent(상위 에이전트)**: 여러 하위 에이전트를 정해진 순서로 호출하는 Service 계층 역할입니다
4. **FlightSearchTool**: 3개의 `@Tool` 메서드를 제공하여 AI 모델이 상황에 맞게 선택합니다
   - `searchFlightsByAirline`: 기본 검색
   - `searchFlightsAfterTime`: 시간 필터링 검색
   - `searchFlightsByPriceRange`: 가격 필터링 검색
5. **ToolResultCapture**: 각 Tool이 `return ToolResultCapture.capture(이름, 인자 요약, () -> 작업)`으로 작업을 실행·기록하면, `ToolCallingSearchService`가 `getAndClear()`로 꺼내 화면용 결과와 실행 기록을 만듭니다
6. **실패를 숨기지 않기**: `ApiClientService`는 서비스 키 누락, HTTP 실패, API 오류 코드를 빈 목록 대신 `ExternalApiException`으로 알리고, 하루 100편이 넘는 노선도 잘리지 않도록 `numOfRows=500`으로 요청합니다
7. **Tool 전달**: Tool은 `ChatClientConfig`에 기본 등록하지 않고, `ToolCallingSearchService`에서 요청마다 `.tools(...)`로 넘깁니다

---

## 체크리스트

- [ ] `ApiClientService`가 주입받은 `RestClient.Builder`로 `/GetFlightOpratInfoList` 엔드포인트를 호출함
- [ ] API 호출 실패가 빈 목록이 아니라 `ExternalApiException`으로 전달됨
- [ ] `ApiResponseWrapper`의 중첩 구조(`response.body.items.item`)가 올바르게 매핑됨
- [ ] `FlightInfoResponse`의 `depPlandTime`/`arrPlandTime`이 YYYYMMDDHHmm 형식으로 처리됨
- [ ] `FlightSearchTool`이 `FlightSearchAgent`에게 작업을 위임함
- [ ] `FlightSearchAgent`가 하위 에이전트 체인(DateParser → AirportCode → API → Grouping)을 실행함
- [ ] 세 Tool 메서드가 `return ToolResultCapture.capture(...)`로 호출을 기록함
- [ ] `ToolCallingSearchService`가 요청마다 `.tools(...)`로 Tool을 넘기는 이유를 설명할 수 있음
- [ ] LLM이 Tool을 호출하여 항공편 검색
- [ ] 응답 시간이 적정 수준 (5초 이내)

---

## 다음 단계

[**Step 7: 공항/항공사 정보 Tool 구현**](step-7-airport-info-tool.md)에서 공항 코드 조회, 항공사 정보 조회 등의 Tool을 추가로 구현합니다.

---

## 참고 자료

- [공공데이터 포털](https://www.data.go.kr/)
- [국내항공운항정보 API 문서](https://www.data.go.kr/data/15098526/openapi.do)
- [RestClient 공식 문서](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-restclient)
