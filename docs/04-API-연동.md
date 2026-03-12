# API 연동 구현 가이드

## 🎯 학습 목표

- RestTemplate을 이용한 외부 API 호출
- 요청 파라미터 구성 방법
- JSON 응답 파싱

---

## 📋 API 스펙 확인

### 항공운항정보 조회 API

| 항목 | 값 |
|------|-----|
| **기본 URL** | `https://apis.data.go.kr/1613000/DmstcFlightNvgInfo` |
| **엔드포인트** | `/GetFlightOpratInfoList` |
| **전체 URL** | `https://apis.data.go.kr/1613000/DmstcFlightNvgInfo/GetFlightOpratInfoList` |
| **Method** | GET |
| **포맷** | JSON, XML |

### 요청 파라미터

| 파라미터명 | 타입 | 필수 | 설명 | 예시 |
|-----------|------|------|------|------|
| serviceKey | String | ✅ | 인증키 | 발급받은키 |
| pageNo | Integer | ❌ | 페이지 번호 | 1 |
| numOfRows | Integer | ❌ | 페이지당 결과 수 | 10 |
| _type | String | ❌ | 응답 타입 | json, xml |
| depAirportId | String | ✅ | 출발공항ID | NAARKJJ (광주) |
| arrAirportId | String | ✅ | 도착공항ID | NAARKPC (제주) |
| depPlandTime | String | ✅ | 출발일 | 20250309 |
| airlineId | String | ❌ | 항공사ID | AAR (아시아나) |

### 응답 필드

| 필드명 | 타입 | 설명 | 예시 |
|--------|------|------|------|
| resultCode | String | 결과코드 | 00 |
| resultMsg | String | 결과메시지 | OK |
| numOfRows | Integer | 페이지당 결과 수 | 10 |
| pageNo | Integer | 페이지 번호 | 1 |
| totalCount | Integer | 전체 결과 수 | 5 |
| vihicleId | String | 항공편명 | OZ8141 |
| airlineNm | String | 항공사명 | 아시아나항공 |
| depPlandTime | String | 출발시간 | 202503090955 |
| arrPlandTime | String | 도착시간 | 202503091045 |
| economyCharge | Integer | 일반석운임 | 57900 |
| prestigeCharge | Integer | 비즈니스석운임 | 0 |
| depAirportNm | String | 출발공항 | 광주 |
| arrAirportNm | String | 도착공항 | 제주 |

---

## 📦 DTO 생성

### FlightInfoResponse.java

```java
package com.example.flyschedule.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightInfoResponse {

    @JsonProperty("vihicleId")
    private String flightId;          // 항공편명

    @JsonProperty("airlineNm")
    private String airlineName;        // 항공사명

    @JsonProperty("depPlandTime")
    private String departureTime;      // 출발시간

    @JsonProperty("arrPlandTime")
    private String arrivalTime;        // 도착시간

    @JsonProperty("economyCharge")
    private Integer economyCharge;     // 일반석운임

    @JsonProperty("prestigeCharge")
    private Integer prestigeCharge;    // 비즈니스석운임

    @JsonProperty("depAirportNm")
    private String departureAirport;   // 출발공항

    @JsonProperty("arrAirportNm")
    private String arrivalAirport;     // 도착공항
}
```

### ApiResponseWrapper.java

```java
package com.example.flyschedule.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class ApiResponseWrapper {

    @JsonProperty("resultCode")
    private String resultCode;

    @JsonProperty("resultMsg")
    private String resultMessage;

    @JsonProperty("numOfRows")
    private Integer numOfRows;

    @JsonProperty("pageNo")
    private Integer pageNo;

    @JsonProperty("totalCount")
    private Integer totalCount;

    @JsonProperty("item")
    private List<FlightInfoResponse> items;
}
```

---

## 🔧 API 호출 서비스 구현

### ApiClientService.java

```java
package com.example.flyschedule.service;

import com.example.flyschedule.dto.response.ApiResponseWrapper;
import com.example.flyschedule.dto.response.FlightInfoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
public class ApiClientService {

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final String serviceKey;

    public ApiClientService(RestTemplate restTemplate,
                           @Value("${data-go-kr.api.url}") String apiUrl,
                           @Value("${data-go-kr.api.service-key}") String serviceKey) {
        this.restTemplate = restTemplate;
        this.apiUrl = apiUrl;
        this.serviceKey = serviceKey;
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

        // URL 빌드
        String url = UriComponentsBuilder.fromHttpUrl(apiUrl + "/GetFlightOpratInfoList")
                .queryParam("serviceKey", serviceKey)
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", 100)
                .queryParam("_type", "json")
                .queryParam("depAirportId", depAirportId)
                .queryParam("arrAirportId", arrAirportId)
                .queryParam("depPlandTime", depPlandTime)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();

        log.info("API 호출 URL: {}", url);

        // API 호출
        ApiResponseWrapper response = restTemplate.getForObject(url, ApiResponseWrapper.class);

        if (response != null && "00".equals(response.getResultCode())) {
            log.info("항공편 {}건 조회 완료", response.getTotalCount());
            return response.getItems();
        }

        log.warn("API 호출 실패: {}", response != null ? response.getResultMessage() : "null");
        return List.of();
    }
}
```

---

## ✅ 테스트 코드 작성

### ApiClientServiceTest.java

```java
package com.example.flyschedule.service;

import com.example.flyschedule.dto.response.FlightInfoResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ApiClientServiceTest {

    @Autowired
    private ApiClientService apiClientService;

    @Test
    void 항공편_조회_테스트() {
        // given
        String depAirportId = "NAARKJJ"; // 광주
        String arrAirportId = "NAARKPC"; // 제주
        String depPlandTime = "20250309"; // 오늘 날짜로 변경 필요

        // when
        List<FlightInfoResponse> flights = apiClientService.getFlightSchedule(
            depAirportId, arrAirportId, depPlandTime);

        // then
        assertThat(flights).isNotNull();
        flights.forEach(flight -> {
            System.out.println("항공편: " + flight.getFlightId());
            System.out.println("항공사: " + flight.getAirlineName());
            System.out.println("출발: " + flight.getDepartureTime());
            System.out.println("도착: " + flight.getArrivalTime());
            System.out.println("일반석: " + flight.getEconomyCharge() + "원");
            System.out.println("---");
        });
    }
}
```

---

## 🚀 실행 및 확인

### 1. 테스트 실행

```bash
./gradlew test --tests ApiClientServiceTest
```

IntelliJ: 테스트 클래스에서 우클릭 > Run

### 2. 예상 출력

```
항공편: OZ8141
항공사: 아시아나항공
출발: 202503090955
도착: 202503091045
일반석: 57900원
---
```

---

## 🔍 공항 코드 참고

### 주요 공항 코드

| 공항명 | 코드 | 공항명 | 코드 |
|--------|------|--------|------|
| 김포 | NAARKSS | 제주 | NAARKPC |
| 김해 | NAARKJB | 광주 | NAARKJJ |
| 대구 | NAARKTN | 청주 | NAARKNJ |
| 무안 | NAARKJJ | 울산 | NAARKNU |
| 여수 | NAARKJY | 양양 | NAARKNY |

---

## ❌ 자주 발생하는 에러

### 1. 인증키 에러

```
resultCode: "99"
resultMessage: "SERVICE KEY IS NOT REGISTERED ERROR"
```

**해결방법**:
- 인증키가 올바른지 확인
- 활용신청이 승인되었는지 확인
- Decoding 키를 사용하는지 확인

### 2. 파라미터 누락

```
resultCode: "99"
resultMessage: "INVALID PARAMETER"
```

**해결방법**:
- 필수 파라미터 모두 전달 확인
- 파라미터명 철자 확인

---

## ✅ 다음 단계

[서비스 구현](./05-서비스-구현.md)에서 비즈니스 로직을 추가합니다.
