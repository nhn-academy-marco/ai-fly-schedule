# Step 7: 공항/항공사 정보 Tool 구현

## 학습 목표

이 단계를 마치면 다음을 할 수 있습니다:

1. **공항 목록/코드 조회 Tool**을 `@Tool` 애노테이션으로 구현할 수 있다
2. **항공사 목록/ID 조회 Tool**을 에이전트 위임 패턴으로 구현할 수 있다
3. **AirportCodeAgent**에서 공항 이름-코드 매핑을 관리하는 방법을 이해한다
4. **여러 Tool을 ChatClient에 등록**하여 AI 모델이 상황에 맞게 선택하도록 할 수 있다

> 💡 **핵심 개념**: 이 단계에서는 Step 6의 항공편 검색 Tool에 이어 **공항 정보 Tool**과 **항공사 정보 Tool**을 추가합니다.
> 사용자가 "공항 리스트 보여줘"라고 하면 AI 모델이 `AirportInfoTool`을 호출하고,
> "제주항공 ID가 뭐야?"라고 하면 `AirlineInfoTool`을 호출합니다.
> 각 Tool은 `@Component` 클래스 안에 `@Tool` 메서드를 정의합니다.

---

## 전체 흐름

```
[사용자: "광주 공항 코드가 뭐야? 그리고 내일 광주에서 제주 항공편 알려줘"]
       ↓
[LLM: "두 개의 Tool을 호출해야겠다!"]
       ↓
  ┌─────────────────────────────────────────────┐
  │ 1) AirportInfoTool.getAirportCode("광주")    │
  │    → AirportListAgent → AirportCodeAgent      │
  │    → "NAARKJJ" 반환                           │
  ├─────────────────────────────────────────────┤
  │ 2) FlightSearchTool.searchFlightsByAirline   │
  │    ("광주", "제주", "내일")                     │
  │    → FlightSearchAgent → DateParserAgent →    │
  │      AirportCodeAgent → API → GroupingAgent   │
  │    → 항공사별 항공편 목록 반환                   │
  └─────────────────────────────────────────────┘
       ↓
[LLM: "광주 공항 코드는 NAARKJJ이고, 내일 항공편은..."]
```

---

## 에이전트 위임(Delegation) 패턴이란?

**에이전트 위임 패턴**은 MCP Tool이 직접 비즈니스 로직을 수행하지 않고, 전문화된 **에이전트(Agent)** 클래스에게 작업을 위임하는 설계 패턴입니다.

### 왜 Tool이 직접 로직을 처리하지 않을까요?

Spring Boot에서 Controller가 직접 DB에 접근하지 않고 Service에 위임하는 것과 같은 이유입니다:

```
// Spring Boot에서 이미 배운 패턴:
Controller (진입점) → Service (비즈니스 로직) → Repository (데이터 접근)

// Spring AI에서도 같은 패턴:
Tool (AI 진입점) → Agent (비즈니스 로직) → ApiClientService (외부 API)
```

### 위임이 필요한 이유

**문제 상황 (Tool이 직접 모든 로직을 처리):**

```
AirportInfoTool
  ├── API 호출 로직
  ├── 응답 파싱 로직
  ├── 캐시 관리 로직
  └── 에러 처리 로직
```

**위임 패턴 해결:**

```
AirportInfoTool (얇은 진입점)
  ↓ 위임
AirportListAgent (작업 조율)
  ↓ 호출
ApiClientService (API 호출)
  ↓ 사용
AirportCodeAgent (코드 매핑)
```

### 위임 패턴의 장점

| 장점 | 설명 |
|------|------|
| **단일 책임**| Tool은 진입점만 담당, Agent가 실제 작업 수행 |
| **재사용성**| Agent를 여러 Tool에서 공유 가능 |
| **테스트 용이**| Agent를 독립적으로 단위 테스트 가능 |
| **확장성**| 새로운 기능 추가 시 Agent만 수정 |

### 이 프로젝트의 위임 구조

```
AirportInfoTool
 └── AirportListAgent
      ├── ApiClientService.getAirportList()
      └── AirportCodeAgent.getAirportCode()

AirlineInfoTool
 └── AirlineListAgent
      ├── ApiClientService.getAirlineList()
      └── 항공사 이름 → ID 검색
```

---

## 공항/항공사 정보 API 소개

> 📖 **Spring Boot 복습**: 외부 API를 호출할 때 `application.yml`에 URL과 API 키를 설정하고,
> `@ConfigurationProperties`로 읽어오는 방법은 Step 6에서 이미 사용했습니다.
> 공항/항공사 API도 같은 방식으로 설정합니다.

### 공공데이터 포털 엔드포인트

| 엔드포인트 | 설명 | 반환 데이터 |
|-----------|------|-----------|
| `/GetArprtList` | 전체 공항 목록 | 공항 ID, 공항명 |
| `/GetAirmanList` | 전체 항공사 목록 | 항공사 ID, 항공사명 |

---

## Step 7-1: DTO 정의

> 📖 **Spring Boot 복습**: DTO(Data Transfer Object)는 데이터를 전달하는 객체입니다.
> `@JsonProperty`는 JSON 필드명과 Java 필드명을 매핑합니다.
> 공공데이터 API의 응답 필드명(예: `airportId`, `airportNm`)을 Java에서 사용하기 쉬운 이름으로 변환합니다.

### AirportInfoResponse.java

```java
package com.nhnacademy.flyschedule.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 공항 정보 응답 DTO
 */
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

/**
 * 항공사 정보 응답 DTO
 */
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

> **참고:** `AirlineInfoResponse`는 `airlineId`와 `airlineName` 필드만 가집니다. `category`(FSC/LCC) 필드는 존재하지 않습니다.

---

## Step 7-2: ApiClientService 확장

> 📖 **Spring Boot 복습**: 하나의 `@Service` 클래스에 여러 메서드를 추가하는 것은 Spring Boot에서 흔한 패턴입니다.
> `ApiClientService`에 공항 목록과 항공사 목록 조회 메서드를 추가합니다.
> 내부 클래스를 사용하여 API 응답 구조를 파싱하는 것도 이미 배운 내용입니다.

### ApiClientService에 공항/항공사 목록 메서드 추가

`ApiClientService`는 공항/항공사 목록 API 응답을 파싱하기 위해 내부 클래스(`AirportListResponse`, `AirlineListResponse`)를 사용합니다.

```java
// com.nhnacademy.flyschedule.service.api.ApiClientService

/**
 * 공항 목록 조회
 */
public List<AirportInfoResponse> getAirportList() {
    // Service Key는 인코딩하지 않고 그대로 사용
    String url = properties.getUrl() + "/GetArprtList" +
            "?serviceKey=" + properties.getServiceKey();

    log.info("공항 목록 API 호출");

    AirportListResponse response = restClient.get()
            .uri(url)
            .retrieve()
            .body(AirportListResponse.class);

    if (response != null && "00".equals(response.getResultCode())) {
        return response.getItems() != null ? response.getItems() : Collections.emptyList();
    }

    return Collections.emptyList();
}

/**
 * 항공사 목록 조회
 */
public List<AirlineInfoResponse> getAirlineList() {
    String url = properties.getUrl() + "/GetAirmanList" +
            "?serviceKey=" + properties.getServiceKey() +
            "&_type=json";

    log.info("항공사 목록 API 호출");

    AirlineListResponse response = restClient.get()
            .uri(url)
            .retrieve()
            .body(AirlineListResponse.class);

    if (response != null && "00".equals(response.getResultCode())) {
        return response.getItems() != null ? response.getItems() : Collections.emptyList();
    }

    return Collections.emptyList();
}

/**
 * 공항 목록 응답 래퍼 (내부 클래스)
 */
@lombok.Data
private static class AirportListResponse {
    private Response response;

    @lombok.Data
    public static class Response {
        private Header header;
        private Body body;
    }

    @lombok.Data
    public static class Header {
        private String resultCode;
        private String resultMsg;
    }

    @lombok.Data
    public static class Body {
        private Items items;
    }

    @lombok.Data
    public static class Items {
        private List<AirportInfoResponse> item;
    }

    public String getResultCode() {
        return response != null && response.header != null ? response.header.getResultCode() : null;
    }

    public List<AirportInfoResponse> getItems() {
        return response != null && response.body != null && response.body.items != null
                ? response.body.items.getItem()
                : null;
    }
}

/**
 * 항공사 목록 응답 래퍼 (내부 클래스)
 */
@lombok.Data
private static class AirlineListResponse {
    private Response response;

    @lombok.Data
    public static class Response {
        private Header header;
        private Body body;
    }

    @lombok.Data
    public static class Header {
        private String resultCode;
        private String resultMsg;
    }

    @lombok.Data
    public static class Body {
        private Items items;
    }

    @lombok.Data
    public static class Items {
        private List<AirlineInfoResponse> item;
    }

    public String getResultCode() {
        return response != null && response.header != null ? response.header.getResultCode() : null;
    }

    public List<AirlineInfoResponse> getItems() {
        return response != null && response.body != null && response.body.items != null
                ? response.body.items.getItem()
                : null;
    }
}
```

> **참고:** 공항/항공사 목록 API 응답은 항공편 API와 동일하게 `response.body.items.item` 중첩 구조를 사용합니다. `ApiClientService`는 이를 파싱하기 위해 전용 내부 클래스를 정의합니다.

---

## Step 7-3: AirportListAgent 구현

`AirportListAgent`는 공항 정보 조회와 공항 코드 변환을 담당하는 에이전트입니다.

> 📖 **Spring Boot 복습**: `@RequiredArgsConstructor`는 `final` 필드에 대한 생성자를 자동 생성합니다.
> Spring Boot에서 의존성 주입(DI)을 위해 사용하는 Lombok 애노테이션입니다.
> `AirportListAgent`는 `ApiClientService`와 `AirportCodeAgent`를 주입받아 사용합니다.

```java
package com.nhnacademy.flyschedule.service.agent;

import com.nhnacademy.flyschedule.dto.response.AirportInfoResponse;
import com.nhnacademy.flyschedule.service.api.ApiClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 공항 목록 조회 A2A 에이전트
 *
 * 공항 정보를 조회하고 공항 코드를 매핑합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AirportListAgent {

    private final ApiClientService apiClientService;
    private final AirportCodeAgent airportCodeAgent;

    /**
     * 전체 공항 목록을 조회합니다.
     */
    public List<AirportInfoResponse> getAirportList() {
        log.info("AirportListAgent: 공항 목록 조회");

        List<AirportInfoResponse> airports = apiClientService.getAirportList();

        log.info("공항 {}개 조회 완료", airports.size());

        return airports;
    }

    /**
     * 공항 이름으로 공항 코드를 조회합니다.
     */
    public String getAirportCode(String airportName) {
        log.info("AirportListAgent: 공항 코드 조회 - {}", airportName);

        return airportCodeAgent.getAirportCode(airportName);
    }
}
```

### AirportCodeAgent.java

`AirportCodeAgent`는 공항 이름과 코드의 매핑을 관리합니다.

### 왜 공항 코드 매핑이 필요할까요?

공공데이터 API는 공항 이름("광주")이 아니라 **공항 코드("NAARKJJ")**를 사용합니다.
하지만 사용자는 "광주"라고 입력합니다. 그래서 이름→코드 변환이 필요합니다:

```
사용자 입력: "광주"  →  AirportCodeAgent 변환  →  API 파라미터: "NAARKJJ"
사용자 입력: "제주"  →  AirportCodeAgent 변환  →  API 파라미터: "NAARKPC"
```

> 📖 **Spring Boot 복습**: `static { }` 블록은 클래스가 메모리에 로드될 때 한 번만 실행됩니다.
> `HashMap`에 미리 값을 넣어두면, 매번 API를 호출하지 않고도 빠르게 코드를 조회할 수 있습니다.

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
        AIRPORT_CODE_MAP.put("울산", "NAARKNU");

        // 호남권
        AIRPORT_CODE_MAP.put("광주", "NAARKJJ");
        AIRPORT_CODE_MAP.put("광주공항", "NAARKJJ");
        AIRPORT_CODE_MAP.put("여수", "NAARKJY");
        AIRPORT_CODE_MAP.put("무안", "NAARKJB");

        // 영남권
        AIRPORT_CODE_MAP.put("대구", "NAARKTN");
        AIRPORT_CODE_MAP.put("포항", "NAARKPK");

        // 충청/강원권
        AIRPORT_CODE_MAP.put("청주", "NAARKNJ");
        AIRPORT_CODE_MAP.put("양양", "NAARKNY");

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

    public boolean isValidAirport(String airportName) {
        if (airportName == null || airportName.isBlank()) {
            return false;
        }
        return AIRPORT_CODE_MAP.containsKey(airportName.trim());
    }
}
```

---

## Step 7-4: AirportInfoTool 구현

`AirportInfoTool`은 `AirportListAgent`에게 작업을 위임합니다.

### 왜 Tool은 이렇게 단순할까요?

Tool은 "AI 모델이 호출하는 진입점"이므로 **로직이 거의 없어야 합니다**:
- `@Tool` description으로 AI 모델에게 "이 도구가 뭘 하는지" 알려줍니다
- `@ToolParam` description으로 "각 파라미터가 뭔지" 알려줍니다
- 실제 로직은 Agent에게 위임합니다

```java

```java
package com.nhnacademy.flyschedule.mcp;

import com.nhnacademy.flyschedule.dto.response.AirportInfoResponse;
import com.nhnacademy.flyschedule.service.agent.AirportListAgent;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 공항 정보 MCP Tool
 *
 * Spring AI의 @Tool annotation을 사용하여 공항 관련 도구를 정의합니다.
 */
@Slf4j
@Component                                                        // ① Spring Bean으로 등록 (Spring AI가 Tool을 찾을 수 있게 함)
@RequiredArgsConstructor                                          // ② final 필드에 대한 생성자 자동 생성 (DI)
public class AirportInfoTool {

    private final AirportListAgent airportListAgent;               // ③ Agent를 주입받음

    // ④ @Tool: AI 모델이 이 메서드를 호출할 수 있게 합니다
    // ⑤ description: AI 모델이 언제 이 Tool을 사용해야 할지 판단합니다
    @Tool(
            description = "전체 공항 목록을 조회합니다. " +
                    "국내 모든 공항의 코드와 이름을 반환합니다."          // ⑤
    )
    public List<AirportInfoResponse> getAirportList() {
        log.info("MCP Tool 호출: getAirportList()");

        // ⑥ Tool은 단순히 Agent에게 위임합니다
        return airportListAgent.getAirportList();                   // ⑥
    }

    @Tool(
            description = "공항 이름으로 공항 코드를 조회합니다. " +
                    "공항 이름을 입력하면 해당 공항의 IATA 코드를 반환합니다. " +
                    "지원하는 공항: 김포, 인천, 김해, 광주, 제주, 대구, 청주, 양양, 울산, 여수, 사천, 포항, 무안 등"
    )
    public String getAirportCode(
            // ⑦ @ToolParam: AI 모델이 파라미터의 의미를 이해합니다
            @ToolParam(description = "공항 이름 (예: 광주, 김포, 제주)") String airportName) {  // ⑦

        log.info("MCP Tool 호출: getAirportCode(airportName={})", airportName);

        // 역시 Agent에게 위임
        return airportListAgent.getAirportCode(airportName);
    }
}
```
```

---

## Step 7-5: AirlineListAgent 구현

`AirlineListAgent`는 항공사 정보 조회와 항공사 ID 검색을 담당합니다.

```java
package com.nhnacademy.flyschedule.service.agent;

import com.nhnacademy.flyschedule.dto.response.AirlineInfoResponse;
import com.nhnacademy.flyschedule.service.api.ApiClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 항공사 목록 조회 A2A 에이전트
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AirlineListAgent {

    private final ApiClientService apiClientService;

    /**
     * 전체 항공사 목록을 조회합니다.
     */
    public List<AirlineInfoResponse> getAirlineList() {
        log.info("AirlineListAgent: 항공사 목록 조회");

        List<AirlineInfoResponse> airlines = apiClientService.getAirlineList();

        log.info("항공사 {}개 조회 완료", airlines.size());

        return airlines;
    }

    /**
     * 항공사 이름으로 항공사 ID를 조회합니다.
     */
    public String getAirlineId(String airlineName) {
        log.info("AirlineListAgent: 항공사 ID 조회 - {}", airlineName);

        List<AirlineInfoResponse> airlines = apiClientService.getAirlineList();

        return airlines.stream()
                .filter(airline -> airline.getAirlineName().contains(airlineName))
                .findFirst()
                .map(AirlineInfoResponse::getAirlineId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "알 수 없는 항공사입니다: " + airlineName));
    }
}
```

> **참고:** `AirlineListAgent.getAirlineId()`는 매번 API를 호출하여 항공사 목록에서 검색합니다. `contains()`를 사용하므로 부분 일치도 지원합니다.

---

## Step 7-6: AirlineInfoTool 구현

`AirlineInfoTool`은 `AirlineListAgent`에게 작업을 위임합니다.

```java
package com.nhnacademy.flyschedule.mcp;

import com.nhnacademy.flyschedule.dto.response.AirlineInfoResponse;
import com.nhnacademy.flyschedule.service.agent.AirlineListAgent;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 항공사 정보 MCP Tool
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AirlineInfoTool {

    private final AirlineListAgent airlineListAgent;

    /**
     * 전체 항공사 목록을 조회합니다.
     */
    @Tool(
            description = "전체 항공사 목록을 조회합니다. " +
                    "국내 모든 항공사의 코드와 이름을 반환합니다."
    )
    public List<AirlineInfoResponse> getAirlineList() {
        log.info("MCP Tool 호출: getAirlineList()");

        // A2A: Coordinator → AirlineListAgent
        return airlineListAgent.getAirlineList();
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

        // A2A: Coordinator → AirlineCodeAgent
        return airlineListAgent.getAirlineId(airlineName);
    }
}
```

---

## Step 7-7: ChatClient에 Tool 등록

> 📖 **Spring Boot 복습**: `@Configuration` 클래스에서 `@Bean` 메서드로 여러 컴포넌트를 조립하는 방법은 이미 배웠습니다.
> 아래 코드는 세 개의 Tool Bean(`FlightSearchTool`, `AirportInfoTool`, `AirlineInfoTool`)을
> ChatClient에 등록합니다. AI 모델은 이렇게 등록된 Tool 중에서 필요한 것을 자동으로 선택합니다.

### 비교해보기: Tool 선택은 누가 하나요?

| 비교 항목 | Spring MVC | Spring AI Tool |
|-----------|-----------|----------------|
| **어떤 메서드를 호출할지** | URL 경로로 개발자가 지정 | AI 모델이 description을 보고 자동 판단 |
| **등록 방법** | `@RequestMapping("/api/...")` | `.defaultTools(tool1, tool2, ...)` |
| **파라미터 전달** | HTTP 요청에서 추출 | AI 모델이 자연어에서 추출 |

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

## 전체 아키텍처 요약

```
사용자 질문
  ↓
ChatClient (Spring AI)
  ↓
MCP Tool (진입점)
  ├── FlightSearchTool → FlightSearchAgent → (DateParserAgent, AirportCodeAgent, ApiClientService, GroupingAgent)
  ├── AirportInfoTool → AirportListAgent → (ApiClientService, AirportCodeAgent)
  └── AirlineInfoTool → AirlineListAgent → ApiClientService
```

### 계층별 책임

| 계층 | 클래스 | 책임 |
|------|--------|------|
| **MCP Tool**| `FlightSearchTool`, `AirportInfoTool`, `AirlineInfoTool` | LLM 진입점, @Tool 어노테이션, 결과 제한, ToolResultCapture |
| **Agent**| `FlightSearchAgent`, `AirportListAgent`, `AirlineListAgent` | 작업 조율, 하위 에이전트 호출 |
| **전문 Agent**| `DateParserAgent`, `AirportCodeAgent`, `GroupingAgent`, `TimeFilterAgent`, `PriceFilterAgent` | 단일 기능 수행 |
| **Service**| `ApiClientService` | 외부 API 호출, 응답 파싱 |

---

## 일반적인 문제 해결

### 문제 1: 공항명이 정확히 일치하지 않음

**증상:**"광주공항" vs "광주"로 인해 조회 실패

**해결 방법:**`AirportCodeAgent`가 "광주"와 "광주공항" 모두 지원합니다. 공항 이름뿐만 아니라 "공항" 접미사가 포함된 형태도 매핑되어 있습니다.

### 문제 2: 항공사 이름 부분 일치

**증상:**"아시아나"로 검색했는데 "아시아나항공"과 매칭이 안 됨

**해결 방법:**`AirlineListAgent.getAirlineId()`는 `contains()`를 사용하므로 "아시아나"로 검색해도 "아시아나항공"과 매칭됩니다.

### 문제 3: 공항 코드 형식 입력

**증상:**이미 공항 코드(예: "NAARKJJ")를 알고 있는 경우

**해결 방법:**`AirportCodeAgent.getAirportCode()`는 공항 코드 형식(NAARKxx)이 입력되면 그대로 반환합니다.

---

## 요약

이 단계에서 배운 핵심 내용을 정리합니다:

1. **AirportInfoTool**: 공항 목록 조회와 공항 코드 변환을 제공하는 2개의 `@Tool` 메서드
2. **AirlineInfoTool**: 항공사 목록 조회와 항공사 ID 검색을 제공하는 2개의 `@Tool` 메서드
3. **AirportCodeAgent**: `static HashMap`으로 공항 이름-코드 매핑을 관리합니다
   - "광주" → "NAARKJJ", "제주" → "NAARKPC" 등
   - 이미 코드 형식이면 그대로 반환
4. **에이전트 위임 패턴**: Tool은 진입점만 담당, Agent가 실제 로직을 수행
   - Tool → Agent → ApiClientService (Controller → Service → Repository와 동일한 구조)
5. **다중 Tool 등록**: `.defaultTools(tool1, tool2, tool3)`로 여러 Tool을 AI 모델에 제공

---

## 체크리스트

- [ ] `AirportInfoTool`이 `AirportListAgent`에게 위임함
- [ ] `AirlineInfoTool`이 `AirlineListAgent`에게 위임함
- [ ] 공항 목록 조회가 동작함
- [ ] 항공사 목록 조회가 동작함
- [ ] 공항 코드 조회가 동작함 (`AirportCodeAgent` 매핑 사용)
- [ ] 항공사 ID 조회가 동작함 (`contains()` 부분 일치)
- [ ] `AirlineInfoResponse`에 `category` 필드가 없음
- [ ] LLM이 Tool을 호출하여 정보 조회

---

## 다음 단계

**Step 8: A2A 개념과 단일 에이전트 구현**에서 A2A 오케스트레이션 패턴을 학습하고 전문화된 에이전트를 구현합니다.

---

## 참고 자료

- [공공데이터포털 공항 목록 API](https://www.data.go.kr/data/15068628/openapi.do)
- [공공데이터포털 항공사 목록 API](https://www.data.go.kr/data/15068629/openapi.do)
