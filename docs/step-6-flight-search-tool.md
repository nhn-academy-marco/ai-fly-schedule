# Step 6: 항공편 검색 Tool 구현

## 학습 목표

이 단계를 마치면 다음을 할 수 있습니다:

1. **RestClient**를 사용하여 외부 공공데이터 API를 호출할 수 있다
2. **여러 에이전트를 연결(체인)**하여 복잡한 검색 로직을 구성할 수 있다
3. **시간/가격 필터링 Tool**을 구현하여 조건부 검색을 할 수 있다
4. **ToolResultCapture**로 Tool 결과를 캡처하여 API 응답에 활용할 수 있다

> 💡 **핵심 개념**: 이 단계에서는 Step 5에서 배운 Tool 패턴을 실제 공공데이터 API와 연동합니다.
> `FlightSearchTool`이 `FlightSearchAgent`에게 작업을 위임하고, Agent는 여러 하위 에이전트
> (DateParserAgent, AirportCodeAgent, GroupingAgent)를 체인으로 연결하여 동작합니다.

---

## 전체 흐름

```
[사용자: "광주에서 제주 내일 항공편 알려줘"]
       ↓
[LLM: "FlightSearchTool을 호출해야겠다!"]
       ↓
[FlightSearchTool.searchFlightsByAirline("광주", "제주", "내일")]
       ↓
[FlightSearchAgent → DateParserAgent → AirportCodeAgent → API 호출]
       ↓
[GroupingAgent가 항공사별로 그룹핑 → 항공사당 최대 3편만 반환]
       ↓
[결과를 LLM에게 전달 → LLM이 한국어로 정리 → 사용자에게 응답]
```

---

## RestClient란?

Spring Framework 6.1부터 도입된 **RestClient**는 동기식 HTTP 클라이언트를 위한 **현대적인 API**입니다.

### RestClient의 기술적 진화

**Spring의 HTTP 클라이언트 역사:**

```
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
| **출시**| Spring 3.x | Spring 5.x | Spring 6.1 |
| **스타일**| 명령형 | 반응형 | 유창한 API |
| **차단/비차단**| 차단(Blocking) | 비차단(Non-blocking) | 차단(Blocking) |
| **복잡도**| 복잡함 | 중간 | 간결함 |
| **권장 사용**| 레거시 | 비동기 필요 시 | 일반적인 HTTP 호출 |

### RestClient 사용 시나리오

| 시나리오 | 적합한 클라이언트 | 이유 |
|---------|------------------|------|
| **일반적인 API 호출**| RestClient | 간결하고 직관적 |
| **대량의 병렬 요청**| WebClient | Non-blocking I/O |
| **레거시 코드**| RestTemplate | 이미 사용 중 |
| **간단한 외부 API 연동**| RestClient | 학습 곡선이 낮음 |

---

## 공공데이터 포털 API 소개

### 국내항공운항정보 API

| 항목 | 내용 |
|------|------|
| **API 이름**| 국내항공운항정보 |
| **제공기관**| 한국공항공사 |
| **설명**| 국내 공항 간의 항공편 운항 스케줄 정보 |
| **URL**| https://apis.data.go.kr/1613000/DmstcFlightNvgInfo |

### 주요 엔드포인트

| 엔드포인트 | 설명 | 파라미터 |
|-----------|------|----------|
| `/GetFlightOpratInfoList` | 항공편 운항 정보 조회 | `depAirportId`, `arrAirportId`, `depPlandTime` |
| `/GetArprtList` | 공항 목록 조회 | - |
| `/GetAirmanList` | 항공사 목록 조회 | - |

> **참고:** 실제 API 엔드포인트는 `/GetFlightOpratInfoList`입니다. `/getDomesticFlightSchedule`은 존재하지 않습니다.

---

## Step 6-1: API 연동 설정

> 📖 **Spring Boot 복습**: `application.yml`에서 외부 API 설정값을 관리하고, `@ConfigurationProperties`로 읽어오는 방법은 이미 배웠습니다.
> 공공데이터 포털 API도 같은 방식으로 설정합니다. `service-key`는 환경 변수로 관리하여 소스 코드에 노출되지 않게 합니다.

### application.yml 설정

```yaml
# 공공데이터 포털 API 설정
data-go-kr:
  api:
    url: https://apis.data.go.kr/1613000/DmstcFlightNvgInfo
    service-key: ${DATA_GO_KR_SERVICE_KEY}  # 환경 변수 사용 권장
```

### DataGoKrApiProperties.java

```java
package com.nhnacademy.flyschedule.config;

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

> 📖 **Spring Boot 복습**: DTO(Data Transfer Object)는 데이터를 전달하기 위한 객체입니다.
> `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`는 Lombok 애노테이션으로,
> getter/setter, 생성자 등을 자동 생성합니다. `@JsonProperty`는 JSON 필드명과 Java 필드명을 매핑합니다.

### FlightInfoResponse.java

```java
package com.nhnacademy.flyschedule.dto.response;

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
package com.nhnacademy.flyschedule.dto.response;

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

> 📖 **Spring Boot 복습**: `@Service`는 Spring이 관리하는 Bean을 만드는 애노테이션입니다.
> `ApiClientService`는 외부 공공데이터 API를 호출하는 역할만 담당합니다.
> Spring Boot에서 배운 Service 계층과 동일한 역할입니다.

### ApiClientService.java

```java
package com.nhnacademy.flyschedule.service.api;

import com.nhnacademy.flyschedule.config.DataGoKrApiProperties;
import com.nhnacademy.flyschedule.dto.response.ApiResponseWrapper;
import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class ApiClientService {

    private final DataGoKrApiProperties properties;
    private final RestClient restClient;

    public ApiClientService(DataGoKrApiProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.create();
    }

    /**
     * 파라미터 URL 인코딩
     */
    private String encodeParam(String param) {
        try {
            return URLEncoder.encode(param, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("파라미터 인코딩 실패: {}", param);
            return param;
        }
    }

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

        String url = properties.getUrl() + "/GetFlightOpratInfoList" +
                "?serviceKey=" + properties.getServiceKey() +
                "&pageNo=1" +
                "&numOfRows=100" +
                "&_type=json" +
                "&depAirportId=" + encodeParam(depAirportId) +
                "&arrAirportId=" + encodeParam(arrAirportId) +
                "&depPlandTime=" + encodeParam(depPlandTime);

        log.info("항공편 API 호출 URL: {}", url);

        ApiResponseWrapper response = restClient.get()
                .uri(url)
                .retrieve()
                .body(ApiResponseWrapper.class);

        if (response != null && "00".equals(response.getResultCode())) {
            log.info("항공편 {}건 조회 완료", response.getTotalCount());
            return response.getItems() != null ? response.getItems() : Collections.emptyList();
        }

        log.warn("API 호출 실패: {}", response != null ? response.getResultMessage() : "null");
        return Collections.emptyList();
    }
}
```

> **참고:** 실제 엔드포인트는 `/GetFlightOpratInfoList`이며, Service Key는 인코딩하지 않고 그대로 사용합니다(공공데이터 포털 요구사항).

---

## Step 6-4: A2A 에이전트 구현

### 왜 여러 에이전트로 나눌까요?

"광주에서 제주 내일 항공편"이라는 사용자 요청을 처리하려면 여러 단계가 필요합니다:

```
"내일" → 날짜 변환 (DateParserAgent)
"광주" → 공항 코드 (AirportCodeAgent)
결과 → 항공사별 분류 (GroupingAgent)
"오후 2시 이후" → 시간 필터 (TimeFilterAgent)
"3만원~7만원" → 가격 필터 (PriceFilterAgent)
```

각 에이전트가 **하나의 변환/처리만** 담당하면, 코드가 단순해지고 버그도 줄어듭니다.

> 📖 **Spring Boot 복습**: Spring Boot에서 `@Service` 클래스 하나가 너무 많은 일을 하면
> 유지보수가 어려워집니다. 단일 책임 원칙(SRP)을 지키기 위해 여러 Service로 나누는 것과 같습니다.

### DateParserAgent.java

자연어 날짜 표현을 API 요청 형식(YYYYMMDD)으로 변환합니다.

```java
package com.nhnacademy.flyschedule.service.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 날짜 파싱 A2A 에이전트
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
            default -> parseSpecificDate(dateInput);
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

> 📖 **Spring Boot 복습**: `static { }` 블록은 클래스가 로드될 때 한 번 실행되는 초기화 블록입니다.
> `HashMap`에 공항 이름과 코드를 미리 매핑해 두어, 빠르게 조회할 수 있습니다.

```java
package com.nhnacademy.flyschedule.service.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 공항 코드 매핑 A2A 에이전트
 */
@Slf4j
@Service
public class AirportCodeAgent {

    private static final Map<String, String> AIRPORT_CODE_MAP = new HashMap<>();

    static {
        // 수도권
        AIRPORT_CODE_MAP.put("김포", "NAARKSS");
        AIRPORT_CODE_MAP.put("김포공항", "NAARKSS");
        AIRPORT_CODE_MAP.put("인천", "NAARKSI");
        AIRPORT_CODE_MAP.put("인천공항", "NAARKSI");

        // 부산/경남권
        AIRPORT_CODE_MAP.put("김해", "NAARKJB");
        AIRPORT_CODE_MAP.put("김해공항", "NAARKJB");
        AIRPORT_CODE_MAP.put("부산", "NAARKJB");
        AIRPORT_CODE_MAP.put("사천", "NAARKPS");
        AIRPORT_CODE_MAP.put("사천공항", "NAARKPS");
        AIRPORT_CODE_MAP.put("울산", "NAARKNU");
        AIRPORT_CODE_MAP.put("울산공항", "NAARKNU");

        // 호남권
        AIRPORT_CODE_MAP.put("광주", "NAARKJJ");
        AIRPORT_CODE_MAP.put("광주공항", "NAARKJJ");
        AIRPORT_CODE_MAP.put("여수", "NAARKJY");
        AIRPORT_CODE_MAP.put("여수공항", "NAARKJY");
        AIRPORT_CODE_MAP.put("무안", "NAARKJB");
        AIRPORT_CODE_MAP.put("무안공항", "NAARKJB");

        // 영남권
        AIRPORT_CODE_MAP.put("대구", "NAARKTN");
        AIRPORT_CODE_MAP.put("대구공항", "NAARKTN");
        AIRPORT_CODE_MAP.put("포항", "NAARKPK");
        AIRPORT_CODE_MAP.put("포항공항", "NAARKPK");

        // 충청/강원권
        AIRPORT_CODE_MAP.put("청주", "NAARKNJ");
        AIRPORT_CODE_MAP.put("청주공항", "NAARKNJ");
        AIRPORT_CODE_MAP.put("양양", "NAARKNY");
        AIRPORT_CODE_MAP.put("양양공항", "NAARKNY");

        // 제주권
        AIRPORT_CODE_MAP.put("제주", "NAARKPC");
        AIRPORT_CODE_MAP.put("제주공항", "NAARKPC");
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
package com.nhnacademy.flyschedule.service.agent;

import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
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
package com.nhnacademy.flyschedule.service.agent;

import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TimeFilterAgent {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmm");

    public LocalTime parseTime(String timeInput) {
        if (timeInput == null || timeInput.isBlank()) {
            throw new IllegalArgumentException("시간을 입력해주세요.");
        }

        String normalized = timeInput.trim().toLowerCase();

        // "오후 2시" 형식 처리
        if (normalized.contains("오후")) {
            String numbersOnly = normalized.replaceAll("[^0-9]", "");
            if (!numbersOnly.isEmpty()) {
                int hour = Integer.parseInt(numbersOnly);
                if (hour < 12) hour += 12;
                if (hour >= 24) hour = 12;
                return LocalTime.of(hour, 0);
            }
        }

        // "오전 9시" 형식 처리
        if (normalized.contains("오전")) {
            String numbersOnly = normalized.replaceAll("[^0-9]", "");
            if (!numbersOnly.isEmpty()) {
                int hour = Integer.parseInt(numbersOnly);
                if (hour == 12) hour = 0;
                return LocalTime.of(hour, 0);
            }
        }

        // "HH:mm" 또는 "HHmm" 형식 처리
        String cleaned = normalized.replace(":", "");
        return LocalTime.parse(cleaned, TIME_FORMATTER);
    }

    public List<FlightInfoResponse> filterAfterTime(List<FlightInfoResponse> flights, LocalTime afterTime) {
        if (flights == null || flights.isEmpty()) {
            return List.of();
        }

        return flights.stream()
                .filter(flight -> {
                    try {
                        // depPlandTime은 "202503090955" 형식 → 시간 부분(0955) 추출
                        String timePart = flight.getDepartureTime().substring(8, 12);
                        LocalTime departureTime = LocalTime.parse(timePart, TIME_FORMATTER);
                        return !departureTime.isBefore(afterTime);
                    } catch (Exception e) {
                        log.warn("시간 파싱 실패: {}", flight.getDepartureTime());
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }
}
```

### PriceFilterAgent.java

운임을 기준으로 항공편을 필터링합니다.

```java
package com.nhnacademy.flyschedule.service.agent;

import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
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

`FlightSearchAgent`은 여러 하위 에이전트를 조율하여 항공편 검색 작업을 수행하는 **코디네이터 에이전트**입니다.

### 왜 코디네이터 에이전트가 필요할까요?

Tool(FlightSearchTool)은 "AI 모델이 호출하는 진입점"이므로 최대한 얇게 유지해야 합니다.
FlightSearchAgent은 **어떤 순서로 어떤 에이전트를 호출할지 조율**하는 역할을 담당합니다:

```
Tool: "광주, 제주, 내일" → FlightSearchAgent에게 전달
                                    ↓
                    Agent가 순서대로 조율:
                    1. DateParserAgent: "내일" → "20260310"
                    2. AirportCodeAgent: "광주" → "NAARKJJ"
                    3. ApiClientService: API 호출
                    4. GroupingAgent: 항공사별 분류
```

```java
package com.nhnacademy.flyschedule.service.agent;

import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import com.nhnacademy.flyschedule.service.api.ApiClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 항공편 검색 A2A 에이전트
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

        return grouped.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> timeFilterAgent.filterAfterTime(entry.getValue(), filterTime)
                ));
    }

    /**
     * 가격 필터를 적용하여 항공편을 검색합니다.
     */
    public Map<String, List<FlightInfoResponse>> searchWithPriceFilter(
            String departure, String arrival, String date,
            Integer minPrice, Integer maxPrice) {

        Map<String, List<FlightInfoResponse>> grouped =
                searchAndGroupByAirline(departure, arrival, date);

        return grouped.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> priceFilterAgent.filterByPriceRange(
                                entry.getValue(), minPrice, maxPrice)
                ));
    }
}
```

### 에이전트 체인 흐름

```
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

### Spring Boot 계층 구조와 비교해보기

| Spring Boot 계층 | Spring AI 프로젝트 계층 | 역할 |
|------------------|------------------------|------|
| Controller | Tool (`FlightSearchTool`) | 외부(웹/AI)에서 호출하는 진입점 |
| Service | Agent (`FlightSearchAgent`) | 비즈니스 로직 조율 |
| Repository | ApiClientService | 외부 데이터 소스 접근 |
| 유틸리티 | 하위 Agent (`DateParserAgent` 등) | 단일 기능 변환/처리 |

---

## Step 6-6: FlightSearchTool 구현

`FlightSearchTool`은 MCP 진입점으로, `FlightSearchAgent`에게 작업을 위임하고 `ToolResultCapture`로 결과를 캡처합니다.

### 왜 항공사별로 최대 3편만 반환할까요?

AI 모델(LLM)은 반환받은 데이터를 모두 읽고 자연어로 요약합니다.
데이터가 너무 많으면:
- 응답 시간이 길어지고 (타임아웃 발생 가능)
- AI 모델이 중요한 정보를 놓칠 수 있습니다

그래서 항공사별로 **대표 항공편 3개**만 반환하는 것입니다.

```java
package com.nhnacademy.flyschedule.mcp;

import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import com.nhnacademy.flyschedule.service.agent.FlightSearchAgent;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 항공편 검색 MCP Tool
 *
 * A2A 에이전트들에게 작업을 위임하여 결과를 반환합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlightSearchTool {

    private final FlightSearchAgent flightSearchAgent;

    @Tool(
            description = "항공편을 검색하여 항공사별로 그룹핑하여 반환합니다. " +
                    "출발 공항, 도착 공항, 날짜를 받아 항공사별로 정리된 항공편 목록을 제공합니다. " +
                    "날짜는 '내일', '모레', '2025-03-09' 형식을 지원합니다. " +
                    "빠른 응답을 위해 항공사별 최대 3편만 반환합니다."
    )
    public Map<String, List<FlightInfoResponse>> searchFlightsByAirline(
            @ToolParam(description = "출발 공항 이름 (예: 광주, 김포, 제주)") String departure,
            @ToolParam(description = "도착 공항 이름 (예: 제주, 김포, 부산)") String arrival,
            @ToolParam(description = "날짜 (예: 내일, 모레, 2025-03-09)") String date) {

        log.info("MCP Tool 호출: searchFlightsByAirline(departure={}, arrival={}, date={})",
                departure, arrival, date);

        // A2A: Coordinator → FlightSearchAgent
        Map<String, List<FlightInfoResponse>> allFlights =
                flightSearchAgent.searchAndGroupByAirline(departure, arrival, date);

        // 항공사별 최대 3편만 반환 (LLM 타임아웃 방지)
        Map<String, List<FlightInfoResponse>> limitedFlights = new HashMap<>();
        allFlights.forEach((airline, flights) -> {
            if (flights.size() > 3) {
                limitedFlights.put(airline, flights.subList(0, 3));
            } else {
                limitedFlights.put(airline, flights);
            }
        });

        log.info("MCP Tool 응답: {}개 항공사, {}편 (제한됨)",
                limitedFlights.size(),
                limitedFlights.values().stream().mapToInt(List::size).sum());

        ToolResultCapture.capture("searchFlightsByAirline", limitedFlights);
        return limitedFlights;
    }

    @Tool(
            description = "특정 시간 이후 출발하는 항공편을 검색하여 항공사별로 그룹핑합니다. " +
                    "시간 조건을 만족하는 항공편만 필터링하여 제공합니다. " +
                    "시간 형식은 '14:00', '오후 2시' 등을 지원합니다."
    )
    public Map<String, List<FlightInfoResponse>> searchFlightsAfterTime(
            @ToolParam(description = "출발 공항 이름 (예: 광주, 김포, 제주)") String departure,
            @ToolParam(description = "도착 공항 이름 (예: 제주, 김포, 부산)") String arrival,
            @ToolParam(description = "날짜 (예: 내일, 모레, 2025-03-09)") String date,
            @ToolParam(description = "기준 시간 (예: 14:00, 오후 2시)") String afterTime) {

        log.info("MCP Tool 호출: searchFlightsAfterTime(departure={}, arrival={}, date={}, afterTime={})",
                departure, arrival, date, afterTime);

        // A2A: Coordinator → TimeFilterAgent → FlightSearchAgent → GroupingAgent
        Map<String, List<FlightInfoResponse>> result =
                flightSearchAgent.searchWithTimeFilter(departure, arrival, date, afterTime);
        ToolResultCapture.capture("searchFlightsAfterTime", result);
        return result;
    }

    @Tool(
            description = "특정 가격 범위 내의 항공편을 검색하여 항공사별로 그룹핑합니다. " +
                    "예산에 맞는 항공편만 필터링하여 제공합니다. " +
                    "최소 가격과 최대 가격은 선택 사항입니다."
    )
    public Map<String, List<FlightInfoResponse>> searchFlightsByPriceRange(
            @ToolParam(description = "출발 공항 이름 (예: 광주, 김포, 제주)") String departure,
            @ToolParam(description = "도착 공항 이름 (예: 제주, 김포, 부산)") String arrival,
            @ToolParam(description = "날짜 (예: 내일, 모레, 2025-03-09)") String date,
            @ToolParam(description = "최소 가격 (선택사항, 예: 30000)") Integer minPrice,
            @ToolParam(description = "최대 가격 (선택사항, 예: 70000)") Integer maxPrice) {

        log.info("MCP Tool 호출: searchFlightsByPriceRange(departure={}, arrival={}, date={}, minPrice={}, maxPrice={})",
                departure, arrival, date, minPrice, maxPrice);

        // A2A: Coordinator → PriceFilterAgent → FlightSearchAgent → GroupingAgent
        Map<String, List<FlightInfoResponse>> result =
                flightSearchAgent.searchWithPriceFilter(departure, arrival, date, minPrice, maxPrice);
        ToolResultCapture.capture("searchFlightsByPriceRange", result);
        return result;
    }
}
```

### ToolResultCapture.java

```java
package com.nhnacademy.flyschedule.mcp;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP Tool 호출 결과를 캡처하는 유틸리티
 *
 * LLM이 Tool을 호출하면 반환값을 ThreadLocal에 저장합니다.
 * 서비스 계층에서 캡처된 결과를 읽어 응답의 data 필드에 사용합니다.
 */
public final class ToolResultCapture {

    private static final ThreadLocal<Map<String, Object>> RESULTS = new ThreadLocal<>();

    private ToolResultCapture() {}

    public static void capture(String toolName, Object result) {
        if (RESULTS.get() == null) {
            RESULTS.set(new HashMap<>());
        }
        RESULTS.get().put(toolName, result);
    }

    public static Map<String, Object> getAndClear() {
        Map<String, Object> results = RESULTS.get();
        RESULTS.remove();
        return results != null ? results : new HashMap<>();
    }
}
```

---

## Step 6-7: ChatClient에 Tool 등록

> 📖 **Spring Boot 복습**: `@Configuration` 클래스에서 `@Bean`으로 객체를 등록하는 방법은 이미 배웠습니다.
> `ChatClient.builder()`에 `.defaultTools()`로 Tool Bean을 전달하면, AI 모델이 해당 Tool을 사용할 수 있게 됩니다.
> `@Primary`는 같은 타입의 Bean이 여러 개일 때 기본 Bean을 지정하는 애노테이션입니다.
> `@Qualifier`는 특정 Bean을 이름으로 찾을 때 사용합니다.

```java
@Configuration
public class ChatClientConfig {

    @Bean
    @Primary
    public ChatClient.Builder ollamaChatClientBuilder(
            @Qualifier("ollamaChatModel") ChatModel ollamaChatModel,
            FlightSearchTool flightSearchTool,
            AirportInfoTool airportInfoTool,
            AirlineInfoTool airlineInfoTool) {

        return ChatClient.builder(ollamaChatModel)
                .defaultTools(flightSearchTool, airportInfoTool, airlineInfoTool);
    }

    @Bean
    public ChatClient.Builder geminiChatClientBuilder(
            @Qualifier("googleGenAiChatModel") ChatModel geminiChatModel,
            FlightSearchTool flightSearchTool,
            AirportInfoTool airportInfoTool,
            AirlineInfoTool airlineInfoTool) {

        return ChatClient.builder(geminiChatModel)
                .defaultTools(flightSearchTool, airportInfoTool, airlineInfoTool);
    }
}
```

---

## 일반적인 문제 해결

### 문제 1: API 응답이 없음

**증상:**빈 목록 반환

**해결 방법:**
1. 서비스키가 올바른지 확인
2. 파라미터 형식 확인 (YYYYMMDD)
3. 공항 코드 확인 (NAARKJJ 등)

### 문제 2: "SERVICE KEY IS NOT REGISTERED ERROR"

**해결 방법:**
1. 공공데이터 포털에서 활용신청
2. Decoding 키 사용 (URL 인코딩된 키 X)

### 문제 3: 응답 시간이 너무 김

**해결 방법:**
1. 항공사별 최대 3편만 반환
2. 캐싱 적용
3. 비동기 처리

### 문제 4: depPlandTime 시간 파싱 오류

**증상:**시간 필터링이 동작하지 않음

**해결 방법:**
- `depPlandTime`은 `"202503090955"` 형식이므로, 시간 부분은 `substring(8, 12)`로 추출
- `TimeFilterAgent.parseDepartureTime()`에서 올바르게 파싱되는지 확인

---

## 요약

이 단계에서 배운 핵심 내용을 정리합니다:

1. **RestClient**: Spring 6.1의 현대적인 HTTP 클라이언트로, 공공데이터 API를 호출합니다
2. **에이전트 체인**: DateParserAgent -> AirportCodeAgent -> API -> GroupingAgent가 순차적으로 동작합니다
3. **FlightSearchAgent(코디네이터)**: 여러 하위 에이전트를 조율하는 Service 계층 역할입니다
4. **FlightSearchTool**: 3개의 `@Tool` 메서드를 제공하여 AI 모델이 상황에 맞게 선택합니다
   - `searchFlightsByAirline`: 기본 검색
   - `searchFlightsAfterTime`: 시간 필터링 검색
   - `searchFlightsByPriceRange`: 가격 필터링 검색
5. **ToolResultCapture**: Tool 결과를 ThreadLocal에 저장하여 컨트롤러에서도 JSON으로 전달할 수 있습니다

---

## 체크리스트

- [ ] `ApiClientService`가 `/GetFlightOpratInfoList` 엔드포인트로 API 호출
- [ ] `ApiResponseWrapper`의 중첩 구조(`response.body.items.item`)가 올바르게 매핑됨
- [ ] `FlightInfoResponse`의 `depPlandTime`/`arrPlandTime`이 YYYYMMDDHHmm 형식으로 처리됨
- [ ] `FlightSearchTool`이 `FlightSearchAgent`에게 작업을 위임함
- [ ] `FlightSearchAgent`가 하위 에이전트 체인(DateParser → AirportCode → API → Grouping)을 실행함
- [ ] `ToolResultCapture.capture()`가 호출됨
- [ ] LLM이 Tool을 호출하여 항공편 검색
- [ ] 응답 시간이 적정 수준 (5초 이내)

---

## 다음 단계

**Step 7: 공항/항공사 정보 Tool**에서 공항 코드 조회, 항공사 정보 조회 등의 Tool을 추가로 구현합니다.

---

## 참고 자료

- [공공데이터포털](https://www.data.go.kr/)
- [국내항공운항정보 API 문서](https://www.data.go.kr/data/15098526/openapi.do)
- [RestClient 공식 문서](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-restclient)
