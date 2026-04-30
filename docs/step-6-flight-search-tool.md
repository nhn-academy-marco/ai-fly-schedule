# Step 6: 항공편 검색 Tool 구현

## 학습 목표
이 단계에서는 실제 **공공데이터 포털 API**와 연동하는 항공편 검색 MCP Tool을 구현합니다.

**학습 완료 후 할 수 있는 것:**
- RestClient로 외부 API 호출
- 공공데이터 포털 API 연동
- 항공사별 그룹핁 구현
- 시간/가격 필터링 구현

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

### RestClient의 장점

**1. 유창한 API (Fluent API)**

```java
// RestTemplate (복잡함)
String result = restTemplate.getForObject(
    url,
    String.class,
    params
);

// RestClient (간결함)
String result = restClient.get()
    .uri(url, params)
    .retrieve()
    .body(String.class);
```

**2. 타입 안전성 (Type Safety)**

```java
// 명시적인 제네릭 타입
List<Flight> flights = restClient.get()
    .uri(url)
    .retrieve()
    .body(new ParameterizedTypeReference<List<Flight>>() {});
```

**3. 선언적 설정**

```java
RestClient restClient = RestClient.builder()
    .baseUrl("https://api.example.com")
    .defaultHeader("Authorization", "Bearer {token}")
    .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> {
        // 에러 처리
    })
    .build();
```

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
| `/getDomesticArrivalNumber` | 항공편 도착 정보 | `depAirportId`, `arrAirportId`, `depPlandTime` |
| `/getDomesticFlightSchedule` | 항공편 스케줄 정보 | `depAirportId`, `arrAirportId`, `depPlandTime` |
| `/GetArprtList` | 공항 목록 조회 | - |
| `/GetAirmanList` | 항공사 목록 조회 | - |

---

## Step 6-1: API 연동 설정

### application.yml 설정

```yaml
# 공공데이터 포털 API 설정
data-go-kr:
  api:
    url: https://apis.data.go.kr/1613000/DmstcFlightNvgInfo
    service-key: ${DATA_GO_KR_SERVICE_KEY}  # 환경 변수 사용 권장
```

### ApiProperties.java

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

### FlightInfoResponse.java

```java
package com.nhnacademy.flyschedule.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightInfoResponse {

    @JsonProperty("vihicleId")
    private String flightId;         // 편명 (예: OZ8141)

    @JsonProperty("airlineNm")
    private String airlineName;      // 항공사명 (예: 아시아나항공)

    @JsonProperty("depTime")
    private String departureTime;    // 출발 시간 (예: "09:55")

    @JsonProperty("arrTime")
    private String arrivalTime;      // 도착 시간 (예: "11:25")

    @JsonProperty("economyCharge")
    private Integer economyCharge;   // Economy 요금

    @JsonProperty("depPlandTime")
    private String scheduleDate;     // 스케줄 날짜 (YYYYMMDD)

    @JsonProperty("depAirportNm")
    private String departureAirport; // 출발 공항명

    @JsonProperty("arrAirportNm")
    private String arrivalAirport;   // 도착 공항명
}
```

### ApiResponseWrapper.java

```java
package com.nhnacademy.flyschedule.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ApiResponseWrapper {

    @JsonProperty("response")
    private ApiResponse response;

    @Data
    public static class ApiResponse {
        @JsonProperty("header")
        private ResponseHeader header;

        @JsonProperty("body")
        private ResponseBody body;
    }

    @Data
    public static class ResponseHeader {
        @JsonProperty("resultCode")
        private String resultCode;

        @JsonProperty("resultMsg")
        private String resultMessage;
    }

    @Data
    public static class ResponseBody {
        @JsonProperty("items")
        private List<FlightInfoResponse> items;

        @JsonProperty("numOfRows")
        private Integer numOfRows;

        @JsonProperty("pageNo")
        private Integer pageNo;

        @JsonProperty("totalCount")
        private Integer totalCount;
    }
}
```

---

##  Step 6-3: API 클라이언트 구현

### ApiClientService.java

```java
package com.nhnacademy.flyschedule.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.flyschedule.config.DataGoKrApiProperties;
import com.nhnacademy.flyschedule.dto.response.ApiResponseWrapper;
import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiClientService {

    private final DataGoKrApiProperties apiProperties;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 항공편 스케줄 조회
     *
     * @param depAirportId 출발 공항 ID (예: NAARKJJ)
     * @param arrAirportId 도착 공항 ID (예: NAARKPC)
     * @param date         조회 일자 (YYYYMMDD)
     * @return 항공편 정보 목록
     */
    public List<FlightInfoResponse> getFlightSchedule(
            String depAirportId,
            String arrAirportId,
            String date) {

        try {
            String url = UriComponentsBuilder.fromHttpUrl(apiProperties.getUrl() + "/getDomesticFlightSchedule")
                    .queryParam("serviceKey", apiProperties.getServiceKey())
                    .queryParam("depAirportId", depAirportId)
                    .queryParam("arrAirportId", arrAirportId)
                    .queryParam("depPlandTime", date)
                    .queryParam("_type", "json")
                    .build()
                    .encode(StandardCharsets.UTF_8)
                    .toUriString();

            log.info("API 호출: {}", url);

            ApiResponseWrapper response = restClient.get()
                    .uri(URI.create(url))
                    .retrieve()
                    .body(ApiResponseWrapper.class);

            if (response != null && response.getResponse() != null) {
                String resultCode = response.getResponse().getHeader().getResultCode();

                if ("00".equals(resultCode)) {
                    List<FlightInfoResponse> items = response.getResponse().getBody().getItems();
                    log.info("항공편 {}건 조회 완료", items.size());
                    return items;
                } else {
                    log.error("API 에러: {} - {}",
                            resultCode,
                            response.getResponse().getHeader().getResultMessage());
                }
            }

            return Collections.emptyList();

        } catch (Exception e) {
            log.error("API 호출 실패", e);
            return Collections.emptyList();
        }
    }
}
```

---

## Step 6-4: FlightSearchTool 구현

### FlightSearchTool.java

```java
package com.nhnacademy.flyschedule.mcp;

import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import com.nhnacademy.flyschedule.service.ApiClientService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 항공편 검색 MCP Tool
 *
 * Spring AI의 @Tool annotation을 사용하여 AI 모델이 호출할 수 있는 도구를 정의합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlightSearchTool {

    private final ApiClientService apiClientService;

    /**
     * 항공편을 검색하여 항공사별로 그룹핑합니다.
     */
    @Tool(
            description = "항공편을 검색하여 항공사별로 그룹핑하여 반환합니다. " +
                    "출발 공항, 도착 공항, 날짜를 받아 항공사별로 정리된 항공편 목록을 제공합니다. " +
                    "날짜는 '내일', '모레', '2026-03-10' 형식을 지원합니다. " +
                    "빠른 응답을 위해 항공사별 최대 3편만 반환합니다."
    )
    public Map<String, List<FlightInfoResponse>> searchFlightsByAirline(
            @ToolParam(description = "출발 공항 이름 (예: 광주, 김포, 제주)") String departure,
            @ToolParam(description = "도착 공항 이름 (예: 제주, 김포, 부산)") String arrival,
            @ToolParam(description = "날짜 (예: 내일, 모레, 2026-03-10)") String date) {

        log.info("MCP Tool 호출: searchFlightsByAirline(departure={}, arrival={}, date={})",
                departure, arrival, date);

        // 날짜 포맷 변환 ("내일" → "20260310")
        String formattedDate = parseDate(date);

        // 공항 코드 변환 ("광주" → "NAARKJJ")
        String depAirportId = getAirportCode(departure);
        String arrAirportId = getAirportCode(arrival);

        // API 호출
        List<FlightInfoResponse> allFlights = apiClientService.getFlightSchedule(
                depAirportId, arrAirportId, formattedDate);

        // 항공사별 그룹핑
        Map<String, List<FlightInfoResponse>> groupedFlights = allFlights.stream()
                .collect(Collectors.groupingBy(FlightInfoResponse::getAirlineName));

        // 항공사별 최대 3편만 반환 (LLM 타임아웃 방지)
        Map<String, List<FlightInfoResponse>> limitedFlights = new HashMap<>();
        groupedFlights.forEach((airline, flights) -> {
            if (flights.size() > 3) {
                limitedFlights.put(airline, flights.subList(0, 3));
            } else {
                limitedFlights.put(airline, flights);
            }
        });

        log.info("MCP Tool 응답: {}개 항공사, {}편",
                limitedFlights.size(),
                limitedFlights.values().stream().mapToInt(List::size).sum());

        return limitedFlights;
    }

    /**
     * 날짜 포맷 변환
     */
    private String parseDate(String date) {
        if ("내일".equals(date)) {
            return LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        } else if ("모레".equals(date)) {
            return LocalDate.now().plusDays(2).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        } else if (date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return date.replace("-", "");
        } else {
            return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        }
    }

    /**
     * 공항 코드 변환 (간단 버전)
     */
    private String getAirportCode(String airportName) {
        return switch (airportName) {
            case "김포" -> "NAARKSS";
            case "인천" -> "NAARKII";
            case "김해" -> "NAARKPN";
            case "광주" -> "NAARKJJ";
            case "제주" -> "NAARKPC";
            case "대구" -> "NAARKTN";
            case "청주" -> "NAARKJJ";
            default -> "NAARKJJ";  // 기본값
        };
    }
}
```

---

## Step 6-5: 테스트

### FlightSearchController.java

```java
package com.nhnacademy.flyschedule.controller;

import com.nhnacademy.flyschedule.mcp.FlightSearchTool;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/flight")
public class FlightSearchController {

    private final FlightSearchTool flightSearchTool;

    public FlightSearchController(FlightSearchTool flightSearchTool) {
        this.flightSearchTool = flightSearchTool;
    }

    /**
     * 항공편 검색 테스트
     * GET /api/flight/search?departure=광주&arrival=제주&date=내일
     */
    @GetMapping("/search")
    public String searchFlights(
            @RequestParam String departure,
            @RequestParam String arrival,
            @RequestParam String date) {

        var result = flightSearchTool.searchFlightsByAirline(departure, arrival, date);

        StringBuilder sb = new StringBuilder();
        sb.append("항공편 검색 결과:\n\n");

        result.forEach((airline, flights) -> {
            sb.append("[").append(airline).append("]\n");
            flights.forEach(flight -> {
                sb.append("  - ").append(flight.getFlightId())
                  .append(" (").append(flight.getDepartureTime())
                  .append(" → ").append(flight.getArrivalTime())
                  .append(") ").append(flight.getEconomyCharge()).append("원\n");
            });
            sb.append("\n");
        });

        return sb.toString();
    }
}
```

### 테스트 실행

```bash
curl "http://localhost:8080/api/flight/search?departure=광주&arrival=제주&date=2026-03-10"
```

**예상 응답:**
```
항공편 검색 결과:

[아시아나항공]
  - OZ8141 (09:55 → 11:25) 57900원
  - OZ8143 (14:30 → 16:00) 62000원

[제주항공]
  - 7C1402 (10:30 → 12:00) 45000원
  - 7C1404 (13:00 → 14:30) 48000원
  - 7C1406 (16:00 → 17:30) 52000원
```

---

## Step 6-6: LLM과 통합

### ChatClient에 Tool 등록

```java
@Configuration
public class ChatClientConfig {

    @Bean
    @Primary
    public ChatClient.Builder ollamaChatClientBuilder(
            @Qualifier("ollamaChatModel") ChatModel ollamaChatModel,
            FlightSearchTool flightSearchTool) {

        return ChatClient.builder(ollamaChatModel)
                .defaultTools(flightSearchTool);
    }
}
```

### LLM을 통한 항공편 검색

```bash
curl "http://localhost:8080/api/chat/ollama?question=내일광주에서제주로가는항공편알려줘"
```

**LLM 응답:**
```
네, 내일 광주에서 제주로 가는 항공편을 찾았어요!

아시아나항공 (2편)
- OZ8141편: 09:55 출발 → 11:25 도착, 57,900원
- OZ8143편: 14:30 출발 → 16:00 도착, 62,000원

제주항공 (3편)
- 7C1402편: 10:30 출발 → 12:00 도착, 45,000원
- 7C1404편: 13:00 출발 → 14:30 도착, 48,000원
- 7C1406편: 16:00 출발 → 17:30 도착, 52,000원

총 5편의 항공편이 있으며, 가장 저렴한 것은 제주항공 7C1402편으로 45,000원입니다.
```

---

## 일반적인 문제 해결

### 문제 1: API 응답이 없음

**증상:**빈 목록 반환

**해결 방법:**
1. 서비스키가 올바른지 확인
2. 파라미터 형식 확인 (YYYYMMDD)
3. 공항 코드 확인

### 문제 2: "SERVICE KEY IS NOT REGISTERED ERROR"

**해결 방법:**
1. 공공데이터 포털에서 활용신청
2. Decoding 키 사용 (URL 인코딩된 키 X)

### 문제 3: 응답 시간이 너무 김

**해결 방법:**
1. 항공사별 최대 3편만 반환
2. 캐싱 적용
3. 비동기 처리

---

## 체크리스트

- [ ] `ApiClientService`가 구현됨
- [ ] `FlightSearchTool`이 구현됨
- [ ] 공공데이터 API 연동 성공
- [ ] 항공사별 그룹핑 동작
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
