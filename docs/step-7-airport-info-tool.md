# Step 7: 공항/항공사 정보 Tool 구현

## 학습 목표
이 단계에서는 공항 정보와 항공사 정보를 조회하는 **MCP Tool**을 구현합니다.

**학습 완료 후 할 수 있는 것:**
- 공항 목록 조회 Tool 구현
- 공항 코드 조회 Tool 구현
- 항공사 목록 조회 Tool 구현
- 항공사 ID 조회 Tool 구현

---

## 캐싱(Caching)이란?

**캐싱**은 자주 사용되는 데이터를 **메모리에 저장**하여 반복적인 데이터베이스 또는 API 호출을 줄이는 기술입니다.

### 캐싱의 필요성

**문제 상황:**

```
사용자1: "광주 공항 코드가 뭐야?" → API 호출 (200ms)
사용자2: "광주 공항 코드가 뭐야?" → API 호출 (200ms)
사용자3: "광주 공항 코드가 뭐야?" → API 호출 (200ms)
...

문제점:
- 같은 데이터를 반복해서 가져옴
- 외부 API 서버에 부하
- 응답 시간 지연
```

**캐싱 해결:**

```
첫 번째 요청: "광주 공항 코드가 뭐야?" → API 호출 → 캐시 저장 (200ms)
이후 요청들: "광주 공항 코드가 뭐야?" → 캐시에서 반환 (1ms)

이점:
- 응답 속도 200배 향상
- 외부 API 호출 감소
- 사용자 경험 개선
```

### 캐싱 전략 비교

| 전략 | 설명 | 장점 | 단점 | 사용 사례 |
|------|------|------|------|-----------|
| **로컬 캐시**| 애플리케이션 메모리에 저장 | 빠름 | 분산 환경에서 중복 | 공항 코드, 항공사 ID |
| **분산 캐시**| Redis 등 별도 서버 | 일관성 | 복잡도 증가 | 세션, 대용량 데이터 |
| **HTTP 캐시**| 브라우저/CDN 캐시 | 서버 부하 감소 | 제어 어려움 | 정적 리소스 |
| **데이터베이스 캐시**| DB 내부 캐시 | 자동화 | 제한적 | 쿼리 결과 |

### 로컬 캐시 구현 방식

**1. Map 사용 (간단한 방식)**

```java
@Component
public class SimpleCache {
    private final Map<String, String> cache = new HashMap<>();

    public String get(String key) {
        return cache.get(key);
    }

    public void put(String key, String value) {
        cache.put(key, value);
    }
}
```

**2. Caffeine 사용 (권장)**

```java
@Component
public class CaffeineCache {
    private final Cache<String, String> cache = Caffeine.newBuilder()
        .expireAfterWrite(10, TimeUnit.MINUTES)  // 10분 후 만료
        .maximumSize(1000)                       // 최대 1000개
        .build();

    public String get(String key) {
        return cache.getIfPresent(key);
    }
}
```

**3. Spring Cache (AOP)**

```java
@Cacheable(value = "airports", key = "#name")
public String getAirportCode(String name) {
    // API 호출
}
```

### 캐싱 시 고려사항

| 고려사항 | 설명 | 예시 |
|---------|------|------|
| **만료 정책**| 언제 캐시를 삭제할까? | 10분, 1시간, 하루 |
| **크기 제한**| 얼마나 저장할까? | 1000개, 10000개 |
| **갱신 전략**| 어떻게 업데이트할까? | TTL, LRU, LFU |
| **일관성**| 데이터 변경 시 어떻게? | 즉시 무효화, 주기적 갱신 |

### 이 프로젝트의 캐싱 전략

**공항/항공사 정보 캐싱:**

```
특징:
- 자주 변경되지 않음 (거의 변하지 않음)
- 자주 조회됨
- 데이터 크기가 작음

전략:
- 애플리케이션 시작 시 로드
- Map에 저장 (별도 만료 없음)
- 평생 재사용

이유:
- 공항/항공사 정보는 거의 변하지 않음
- 복잡한 캐시 라이브러리 불필요
- 단순하고 빠른 구현
```

---

## 공항/항공사 정보 API 소개

### 공공데이터 포털 엔드포인트

| 엔드포인트 | 설명 | 반환 데이터 |
|-----------|------|-----------|
| `/GetArprtList` | 전체 공항 목록 | 공항 ID, 공항명 |
| `/GetAirmanList` | 전체 항공사 목록 | 항공사 ID, 항공사명 |

---

## Step 7-1: DTO 정의

### AirportInfoResponse.java

```java
package com.nhnacademy.flyschedule.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private String region;         // 지역 (예: 호남권)
}
```

### AirlineInfoResponse.java

```java
package com.nhnacademy.flyschedule.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AirlineInfoResponse {

    @JsonProperty("airlineId")
    private String airlineId;      // 항공사 ID (예: AAR)

    @JsonProperty("airlineNm")
    private String airlineName;    // 항공사명 (예: 아시아나항공)

    @JsonProperty("category")
    private String category;       // FSC/LCC 구분
}
```

---

## Step 7-2: ApiClientService 확장

### ApiClientService에 메서드 추가

```java
/**
 * 전체 공항 목록 조회
 *
 * @return 공항 정보 목록
 */
public List<AirportInfoResponse> getAirportList() {
    try {
        String url = UriComponentsBuilder.fromHttpUrl(apiProperties.getUrl() + "/GetArprtList")
                .queryParam("serviceKey", apiProperties.getServiceKey())
                .queryParam("_type", "json")
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();

        log.info("공항 목록 API 호출");

        ApiResponseWrapper response = restClient.get()
                .uri(URI.create(url))
                .retrieve()
                .body(ApiResponseWrapper.class);

        if (response != null && "00".equals(response.getResponse().getHeader().getResultCode())) {
            List<AirportInfoResponse> airports = response.getResponse().getBody().getItems();
            log.info("공항 {}건 조회 완료", airports.size());
            return airports;
        }

        return Collections.emptyList();

    } catch (Exception e) {
        log.error("공항 목록 조회 실패", e);
        return Collections.emptyList();
    }
}

/**
 * 전체 항공사 목록 조회
 *
 * @return 항공사 정보 목록
 */
public List<AirlineInfoResponse> getAirlineList() {
    try {
        String url = UriComponentsBuilder.fromHttpUrl(apiProperties.getUrl() + "/GetAirmanList")
                .queryParam("serviceKey", apiProperties.getServiceKey())
                .queryParam("_type", "json")
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();

        log.info("항공사 목록 API 호출");

        ApiResponseWrapper response = restClient.get()
                .uri(URI.create(url))
                .retrieve()
                .body(ApiResponseWrapper.class);

        if (response != null && "00".equals(response.getResponse().getHeader().getResultCode())) {
            List<AirlineInfoResponse> airlines = response.getResponse().getBody().getItems();
            log.info("항공사 {}건 조회 완료", airlines.size());
            return airlines;
        }

        return Collections.emptyList();

    } catch (Exception e) {
        log.error("항공사 목록 조회 실패", e);
        return Collections.emptyList();
    }
}
```

---

## Step 7-3: AirportInfoTool 구현

### AirportInfoTool.java

```java
package com.nhnacademy.flyschedule.mcp;

import com.nhnacademy.flyschedule.dto.response.AirportInfoResponse;
import com.nhnacademy.flyschedule.service.ApiClientService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 공항 정보 MCP Tool
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AirportInfoTool {

    private final ApiClientService apiClientService;

    // 공항명 → 코드 캐시
    private Map<String, String> airportCodeCache;

    /**
     * 전체 공항 목록을 조회합니다.
     */
    @Tool(
            description = "전체 공항 목록을 조회합니다. " +
                    "국내 모든 공항의 코드와 이름을 반환합니다. " +
                    "사용자가 '공항 리스트', '공항 목록', '어느 공항이 있어?' 등을 물을 때 사용합니다."
    )
    public List<AirportInfoResponse> getAirportList() {
        log.info("MCP Tool 호출: getAirportList()");

        List<AirportInfoResponse> airports = apiClientService.getAirportList();

        // 캐시 업데이트
        airportCodeCache = airports.stream()
                .collect(Collectors.toMap(
                        AirportInfoResponse::getAirportName,
                        AirportInfoResponse::getAirportId
                ));

        return airports;
    }

    /**
     * 공항 이름으로 공항 코드를 조회합니다.
     */
    @Tool(
            description = "공항 이름으로 공항 코드를 조회합니다. " +
                    "공항 이름을 입력하면 해당 공항의 IATA 코드를 반환합니다. " +
                    "지원하는 공항: 김포, 인천, 김해, 광주, 제주, 대구, 청주, 양양, 울산, 여수, 사천, 무안 등"
    )
    public String getAirportCode(
            @ToolParam(description = "공항 이름 (예: 광주, 김포, 제주)") String airportName) {

        log.info("MCP Tool 호출: getAirportCode(airportName={})", airportName);

        // 캐시가 없으면 먼저 로드
        if (airportCodeCache == null) {
            getAirportList();
        }

        String code = airportCodeCache.get(airportName);

        if (code == null) {
            log.warn("공항 코드를 찾을 수 없음: {}", airportName);
            return "알 수 없는 공항입니다: " + airportName;
        }

        log.info("공항 코드 조회 결과: {} → {}", airportName, code);
        return code;
    }
}
```

---

## Step 7-4: AirlineInfoTool 구현

### AirlineInfoTool.java

```java
package com.nhnacademy.flyschedule.mcp;

import com.nhnacademy.flyschedule.dto.response.AirlineInfoResponse;
import com.nhnacademy.flyschedule.service.ApiClientService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 항공사 정보 MCP Tool
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AirlineInfoTool {

    private final ApiClientService apiClientService;

    // 항공사명 → ID 캐시
    private Map<String, String> airlineIdCache;

    /**
     * 전체 항공사 목록을 조회합니다.
     */
    @Tool(
            description = "전체 항공사 목록을 조회합니다. " +
                    "국내 모든 항공사의 코드와 이름을 반환합니다. " +
                    "사용자가 '항공사 리스트', '어떤 항공사가 있어?' 등을 물을 때 사용합니다."
    )
    public List<AirlineInfoResponse> getAirlineList() {
        log.info("MCP Tool 호출: getAirlineList()");

        List<AirlineInfoResponse> airlines = apiClientService.getAirlineList();

        // 캐시 업데이트
        airlineIdCache = airlines.stream()
                .collect(Collectors.toMap(
                        AirlineInfoResponse::getAirlineName,
                        AirlineInfoResponse::getAirlineId
                ));

        return airlines;
    }

    /**
     * 항공사 이름으로 항공사 ID를 조회합니다.
     */
    @Tool(
            description = "항공사 이름으로 항공사 ID를 조회합니다. " +
                    "항공사 이름을 입력하면 해당 항공사의 IATA 코드를 반환합니다. " +
                    "지원하는 항공사: 대한항공, 아시아나항공, 제주항공, 에어부산, 에어서울, 진에어, 티웨이항공 등"
    )
    public String getAirlineId(
            @ToolParam(description = "항공사 이름 (예: 아시아나항공, 대한항공, 제주항공)") String airlineName) {

        log.info("MCP Tool 호출: getAirlineId(airlineName={})", airlineName);

        // 캐시가 없으면 먼저 로드
        if (airlineIdCache == null) {
            getAirlineList();
        }

        String id = airlineIdCache.get(airlineName);

        if (id == null) {
            log.warn("항공사 ID를 찾을 수 없음: {}", airlineName);
            return "알 수 없는 항공사입니다: " + airlineName;
        }

        log.info("항공사 ID 조회 결과: {} → {}", airlineName, id);
        return id;
    }
}
```

---

## Step 7-5: ChatClient에 Tool 등록

### ChatClientConfig 업데이트

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
                .defaultTools(
                        flightSearchTool,
                        airportInfoTool,
                        airlineInfoTool
                );
    }
}
```

---

## Step 7-6: 테스트

### InfoToolController.java

```java
package com.nhnacademy.flyschedule.controller;

import com.nhnacademy.flyschedule.mcp.AirlineInfoTool;
import com.nhnacademy.flyschedule.mcp.AirportInfoTool;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/info")
public class InfoToolController {

    private final AirportInfoTool airportTool;
    private final AirlineInfoTool airlineTool;

    public InfoToolController(AirportInfoTool airportTool, AirlineInfoTool airlineTool) {
        this.airportTool = airportTool;
        this.airlineTool = airlineTool;
    }

    /**
     * 공항 목록 조회
     * GET /api/info/airports
     */
    @GetMapping("/airports")
    public String getAirports() {
        var airports = airportTool.getAirportList();

        StringBuilder sb = new StringBuilder();
        sb.append("전국 공항 목록:\n\n");

        airports.forEach(airport -> {
            sb.append(String.format("- %s (%s) [%s]\n",
                    airport.getAirportName(),
                    airport.getAirportId(),
                    airport.getRegion()));
        });

        return sb.toString();
    }

    /**
     * 항공사 목록 조회
     * GET /api/info/airlines
     */
    @GetMapping("/airlines")
    public String getAirlines() {
        var airlines = airlineTool.getAirlineList();

        StringBuilder sb = new StringBuilder();
        sb.append("국내 항공사 목록:\n\n");

        airlines.forEach(airline -> {
            sb.append(String.format("- %s (%s) [%s]\n",
                    airline.getAirlineName(),
                    airline.getAirlineId(),
                    airline.getCategory()));
        });

        return sb.toString();
    }

    /**
     * 공항 코드 조회
     * GET /api/info/airport-code?name=광주
     */
    @GetMapping("/airport-code")
    public String getAirportCode(@RequestParam String name) {
        return "공항 코드: " + airportTool.getAirportCode(name);
    }

    /**
     * 항공사 ID 조회
     * GET /api/info/airline-id?name=아시아나항공
     */
    @GetMapping("/airline-id")
    public String getAirlineId(@RequestParam String name) {
        return "항공사 ID: " + airlineTool.getAirlineId(name);
    }
}
```

### 테스트 실행

```bash
# 공항 목록
curl "http://localhost:8080/api/info/airports"

# 항공사 목록
curl "http://localhost:8080/api/info/airlines"

# 공항 코드 조회
curl "http://localhost:8080/api/info/airport-code?name=광주"

# 항공사 ID 조회
curl "http://localhost:8080/api/info/airline-id?name=아시아나항공"
```

### LLM을 통한 테스트

```bash
# 공항 목록 조회
curl "http://localhost:8080/api/chat/gemini?question=공항리스트보여줘"

# 항공사 목록 조회
curl "http://localhost:8080/api/chat/gemini?question=항공사리스트알려줘"

# 공항 코드 조회
curl "http://localhost:8080/api/chat/gemini?question=광주공항코드가뭐야?"

# 항공사 ID 조회
curl "http://localhost:8080/api/chat/gemini?question=아시아나항공코드알려줘"
```

---

## Step 7-7: 캐싱 전략

### 왜 캐싱이 필요한가?

| 이유 | 설명 |
|------|------|
| **성능**| 매번 API 호출하지 않음 |
| **비용 절감**| API 호출 횟수 감소 |
| **응답 속도**| 메모리에서 바로 반환 |

### 캐싱 구현 패턴

```java
@Component
public class CachedAirportInfoTool {

    private final ApiClientService apiClientService;

    // 캐시 (10분 유효)
    private volatile List<AirportInfoResponse> cachedAirports;
    private volatile long cacheTime = 0;
    private static final long CACHE_DURATION = 10 * 60 * 1000;  // 10분

    @Tool(description = "전체 공항 목록 조회 (캐싱)")
    public List<AirportInfoResponse> getAirportList() {
        // 캐시 유효성 검사
        long now = System.currentTimeMillis();
        if (cachedAirports != null && (now - cacheTime) < CACHE_DURATION) {
            log.info("캐시된 공항 목록 반환");
            return cachedAirports;
        }

        // 캐시 미스 → API 호출
        log.info("공항 목록 API 호출 및 캐싱");
        cachedAirports = apiClientService.getAirportList();
        cacheTime = now;

        return cachedAirports;
    }

    /**
     * 캐시 무효화
     */
    public void clearCache() {
        cachedAirports = null;
        cacheTime = 0;
        log.info("공항 목록 캐시가 초기화되었습니다.");
    }
}
```

---

## 일반적인 문제 해결

### 문제 1: 공항명이 정확히 일치하지 않음

**증상:**"광주공항" vs "광주"로 인해 조회 실패

**해결 방법:**
1. 공백 제거
2. 동의어 처리
3. 퍼지 매칭

```java
private String normalizeAirportName(String name) {
    return name.replaceAll("\\s+", "")  // 공백 제거
            .replace("공항", "")      // "공항" 접미사 제거
            .replaceAll("\\(.*\\)", "");  // 괄호 내용 제거
}
```

### 문제 2: 캐시로 인해 최신 정보 반영 안 됨

**해결 방법:**
1. 주기적 캐시 초기화
2. 수동 캐시 초기화 엔드포인트 제공
3. 캐시 만료 시간 단축

---

## 체크리스트

- [ ] `AirportInfoTool`이 구현됨
- [ ] `AirlineInfoTool`이 구현됨
- [ ] 공항 목록 조회가 동작함
- [ ] 항공사 목록 조회가 동작함
- [ ] 공항 코드 조회가 동작함
- [ ] 항공사 ID 조회가 동작함
- [ ] 캐싱이 적용됨
- [ ] LLM이 Tool을 호출하여 정보 조회

---

## 다음 단계

**Step 8: A2A 개념과 단일 에이전트 구현**에서 A2A 오케스트레이션 패턴을 학습하고 전문화된 에이전트를 구현합니다.

---

## 참고 자료

- [공공데이터포털 공항 목록 API](https://www.data.go.kr/data/15068628/openapi.do)
- [공공데이터포털 항공사 목록 API](https://www.data.go.kr/data/15068629/openapi.do)
