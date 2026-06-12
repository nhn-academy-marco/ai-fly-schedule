# Step 9: Multi-Agent Orchestration 패턴

## 학습 목표

이 단계를 마치면 다음을 할 수 있습니다:

1. **Orchestrator(지휘자)** 패턴이 무엇인지, 왜 필요한지 설명할 수 있다
2. 여러 에이전트를 조율하는 **NaturalLanguageOrchestrationService**를 이해할 수 있다
3. **10단계 오케스트레이션 흐름**을 따라갈 수 있다
4. 에러 처리의 3가지 패턴 (**예외 전파, 재시도, Fallback**)을 이해한다

---

> 💡 **핵심 개념**: **Orchestrator(오케스트레이터)**는 교향乐团에서 지휘자가 각 악기 파트를
> 언제 연주할지 지시하는 것처럼, **여러 에이전트의 실행 순서를 제어**하는 중앙 관리자입니다.
>
> 일상생활에 비유하면: **여행사 직원**이 고객의 요청을 듣고,
> "날짜 담당자 → 공항 코드 담당자 → 항공편 조회 담당자 → 필터링 담당자" 순서로
> 일을 분배하고 결과를 모아 고객에게 전달하는 것과 비슷합니다.

---

## Multi-Agent Orchestration 패턴이란?

### Orchestrator 정의

**Orchestrator**는 여러 에이전트를 **조율(Orchestrate)**하고 작업 흐름을 제어하는 중앙 관리자입니다.

```
┌─────────────────────────────────────────────────────────────┐
│  Orchestrator                                               │
│  - 작업 계획 수립                                           │
│  - 에이전트 간 통신 관리                                    │
│  - 결과 통합                                                │
│  - 에러 처리                                                │
└─────────────────────────────────────────────────────────────┘
         ↓           ↓           ↓           ↓           ↓
    ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐
    │ Date   │  │Airport │  │Flight  │  │ Time   │  │Grouping│
    │ Parser │  │ Code   │  │ Search │  │ Filter │  │ Agent  │
    └────────┘  └────────┘  └────────┘  └────────┘  └────────┘
```

### Orchestrator의 책임

| 책임 | 설명 | 예시 |
|------|------|------|
| **작업 분해**| 복잡한 작업을 작은 단위로 분해 | "광주-제주 항공편" → 여러 하위 작업 |
| **에이전트 선택**| 적절한 에이전트 선택 및 호출 | 날짜 파싱 필요 → DateParserAgent |
| **데이터 전달**| 에이전트 간 데이터 전달 | DateParser 결과 → AirportCode |
| **결과 통합**| 각 에이전트의 결과를 통합 | 항공사별 그룹핑 |
| **에러 처리**| 실패 시 재시도 또는 fallback | 파싱 실패 → 기본값 사용 |

---

## 전체 흐름

다음은 사용자가 자연어로 항공편을 검색할 때의 **전체 처리 흐름**입니다:

```
[사용자: "내일 광주에서 제주 오전 10시 이후 항공편"]
       ↓
[NaturalLanguageOrchestrationService (지휘자)]
       ↓
① LlmAnalysisService     → "출발: 광주, 도착: 제주, 날짜: 내일, 시간: 10:00 이후"
② (파라미터 검증)        → departure, arrival 존재 확인
③ DateParserAgent        → "내일" → "20260614"
④ AirportCodeAgent       → "광주" → "NAARKJJ", "제주" → "NAARKPC"
⑤ FlightSearchAgent      → API 호출 → 118건 조회
⑥ TimeFilterAgent        → 10:00 이후 필터링
⑦ TimeFilterAgent        → (beforeTime이 있으면 이전 시간 필터링)
⑧ PriceFilterAgent       → (가격 조건이 있으면 가격 필터링)
⑨ GroupingAgent          → 항공사별 그룹핑
⑩ convertToMap()         → 결과를 Map으로 변환
       ↓
[결과: {"대한항공": [...], "아시아나항공": [...], ...}]
```

---

## Step 9-1: FlightSearchAgent 구현

### 역할

항공편 검색을 담당하며 **5개의 하위 에이전트**를 활용하여 파라미터를 준비하고 검색 결과를 처리합니다.

- **DateParserAgent**: 날짜 파싱
- **AirportCodeAgent**: 공항 코드 변환
- **TimeFilterAgent**: 시간 필터링
- **PriceFilterAgent**: 가격 필터링
- **GroupingAgent**: 항공사별 그룹핑

> ### 왜 FlightSearchAgent가 다른 에이전트들을 사용할까요?
> FlightSearchAgent는 항공편을 "검색"하는 것이 주 책임입니다.
> 날짜 파싱이나 공항 코드 변환은 직접 하지 않고, 전문 에이전트에게 **위임**합니다.
> Spring Boot에서 `OrderService`가 `ProductService`, `MemberService`를 사용하는 것과 같습니다.

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
 * 여러 하위 에이전트를 조율하여 항공편 검색 작업을 수행합니다.
 * - DateParserAgent: 날짜 파싱
 * - AirportCodeAgent: 공항 코드 변환
 * - TimeFilterAgent: 시간 필터링
 * - PriceFilterAgent: 가격 필터링
 * - GroupingAgent: 항공사별 그룹핑
 */
@Slf4j
@Service
@RequiredArgsConstructor  // ① final 필드의 생성자를 자동 생성 (Lombok)
public class FlightSearchAgent {

    // ② DI(의존성 주입)로 필요한 에이전트와 서비스를 주입받습니다.
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
            String departure,
            String arrival,
            String date) {

        log.info("FlightSearchAgent: 항공편 검색 시작");

        // ③ DateParserAgent: "내일" → "20260614" 로 변환
        String parsedDate = dateParserAgent.parseDate(date);
        log.info("DateParserAgent: {} → {}", date, parsedDate);

        // ④ AirportCodeAgent: "광주" → "NAARKJJ" 로 변환
        String depAirportId = airportCodeAgent.getAirportCode(departure);
        String arrAirportId = airportCodeAgent.getAirportCode(arrival);
        log.info("AirportCodeAgent: {} → {}, {} → {}",
            departure, depAirportId, arrival, arrAirportId);

        // ⑤ 외부 API 호출 (공공데이터 포털)
        List<FlightInfoResponse> flights = apiClientService.getFlightSchedule(
                depAirportId, arrAirportId, parsedDate);

        // ⑥ GroupingAgent: 항공사별로 그룹핑
        Map<String, List<FlightInfoResponse>> grouped = groupingAgent.groupByAirline(flights);
        log.info("GroupingAgent: {}개 항공사로 그룹핑 완료", grouped.size());

        return grouped;
    }

    /**
     * 시간 필터를 적용하여 항공편을 검색합니다.
     */
    public Map<String, List<FlightInfoResponse>> searchWithTimeFilter(
            String departure,
            String arrival,
            String date,
            String afterTime) {

        log.info("FlightSearchAgent: 시간 필터링 검색 시작");

        // ⑦ 기본 검색 수행 (위의 searchAndGroupByAirline 재사용)
        Map<String, List<FlightInfoResponse>> grouped =
            searchAndGroupByAirline(departure, arrival, date);

        // ⑧ TimeFilterAgent: 시간 필터링
        LocalTime filterTime = timeFilterAgent.parseTime(afterTime);
        log.info("TimeFilterAgent: {} 이후 출발 항공편 필터링", filterTime);

        // ⑨ 각 항공사별로 시간 필터링 적용 (Stream API 사용)
        Map<String, List<FlightInfoResponse>> filtered = grouped.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> timeFilterAgent.filterAfterTime(entry.getValue(), filterTime)
                ));

        return filtered;
    }

    /**
     * 가격 필터를 적용하여 항공편을 검색합니다.
     */
    public Map<String, List<FlightInfoResponse>> searchWithPriceFilter(
            String departure,
            String arrival,
            String date,
            Integer minPrice,
            Integer maxPrice) {

        log.info("FlightSearchAgent: 가격 필터링 검색 시작");

        // 기본 검색 수행
        Map<String, List<FlightInfoResponse>> grouped =
            searchAndGroupByAirline(departure, arrival, date);

        // PriceFilterAgent: 가격 필터링
        log.info("PriceFilterAgent: {}원 ~ {}원 범위 필터링", minPrice, maxPrice);

        // 각 항공사별로 가격 필터링 적용
        Map<String, List<FlightInfoResponse>> filtered = grouped.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> priceFilterAgent.filterByPriceRange(
                            entry.getValue(), minPrice, maxPrice)
                ));

        return filtered;
    }
}
```

> 📖 **Spring Boot 복습**: 여러 Service를 조합하는 것은 Spring Boot에서 흔히 하는 일입니다.
> `OrderService`가 `ProductService`, `MemberService`, `PaymentService`를 사용하는 것과 같습니다.
> `FlightSearchAgent`도 여러 에이전트를 조합합니다. Spring의 **DI(의존성 주입)** 덕분에
> `new` 키워드로 직접 생성하지 않고, 생성자 주입으로 에이전트를 받아 사용합니다.

---

## Step 9-2: TimeFilterAgent 및 PriceFilterAgent 구현

### TimeFilterAgent

출발 시간을 기준으로 항공편을 필터링합니다.

> ### 왜 필터링 에이전트를 분리할까요?
> "오후 2시 이후", "5만원 이하" 같은 조건은 검색마다 다를 수 있습니다.
> 시간 필터와 가격 필터를 각각 독립된 에이전트로 만들면,
> 필요한 필터만 선택적으로 적용할 수 있고, 새로운 필터(예: 좌석 등급)도 쉽게 추가할 수 있습니다.

```java
package com.nhnacademy.flyschedule.service.agent;

import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 시간 필터링 A2A 에이전트
 *
 * 출발 시간을 기준으로 항공편을 필터링합니다.
 */
@Slf4j
@Service
public class TimeFilterAgent {

    private static final DateTimeFormatter TIME_FORMATTER =
        DateTimeFormatter.ofPattern("HHmm");

    /**
     * 시간 문자열을 파싱합니다.
     *
     * @param timeInput 시간 문자열 (예: "14:00", "오후 2시", "1400")
     * @return LocalTime 객체
     */
    public LocalTime parseTime(String timeInput) {
        // ... (생략: "오후 2시" → 14:00, "오전 9시" → 09:00, "14:00" → 14:00 등 지원)
    }

    /**
     * 기준 시간 이후에 출발하는 항공편만 필터링합니다.
     */
    public List<FlightInfoResponse> filterAfterTime(
            List<FlightInfoResponse> flights, LocalTime afterTime) {
        // ... (생략)
    }

    /**
     * 기준 시간 이전에 출발하는 항공편만 필터링합니다.
     */
    public List<FlightInfoResponse> filterBeforeTime(
            List<FlightInfoResponse> flights, LocalTime beforeTime) {
        // ... (생략)
    }
}
```

### PriceFilterAgent

운임을 기준으로 항공편을 필터링합니다.

```java
package com.nhnacademy.flyschedule.service.agent;

import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 가격 필터링 A2A 에이전트
 *
 * 운임을 기준으로 항공편을 필터링합니다.
 */
@Slf4j
@Service
public class PriceFilterAgent {

    /**
     * 가격 범위 내의 항공편만 필터링합니다.
     */
    public List<FlightInfoResponse> filterByPriceRange(
            List<FlightInfoResponse> flights,
            Integer minPrice,
            Integer maxPrice) {
        // ... (생략)
    }

    /**
     * 최저가 항공편을 찾습니다.
     */
    public FlightInfoResponse findCheapest(List<FlightInfoResponse> flights) {
        // ... (생략)
    }

    /**
     * 평균 가격을 계산합니다.
     */
    public double calculateAveragePrice(List<FlightInfoResponse> flights) {
        // ... (생략)
    }
}
```

---

## Step 9-3: GroupingAgent 구현

### 역할

항공편을 항공사별로 그룹핑합니다. 항공사명이 없는 경우 항공편 ID에서 항공사 코드를 추출합니다.

> ### 왜 그룹핑 에이전트가 필요할까요?
> API에서 가져온 항공편 데이터는 **하나의 평평한 리스트**입니다.
> 사용자에게 보여줄 때는 "대한항공 3편, 아시아나항공 2편, 제주항공 5편"처럼
> **항공사별로 묶어서** 보여주는 것이 훨씬 보기 좋습니다.
> 이 정렬·그룹핑 작업을 전담하는 것이 GroupingAgent입니다.

```java
package com.nhnacademy.flyschedule.service.agent;

import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 그룹핑 A2A 에이전트
 *
 * 항공편을 항공사별로 그룹핑합니다.
 */
@Slf4j
@Service
public class GroupingAgent {

    /**
     * 항공편을 항공사별로 그룹핑합니다.
     *
     * @param flights 전체 항공편 목록
     * @return 항공사명을 키로, 항공편 목록을 값으로 하는 Map (TreeMap으로 정렬 순서 유지)
     */
    public Map<String, List<FlightInfoResponse>> groupByAirline(
            List<FlightInfoResponse> flights) {

        // ① null/빈 목록이면 빈 TreeMap 반환
        if (flights == null || flights.isEmpty()) {
            return new TreeMap<>();
        }

        // ② Stream API의 groupingBy로 항공사별 그룹핑
        Map<String, List<FlightInfoResponse>> grouped = flights.stream()
                .collect(Collectors.groupingBy(
                        flight -> {
                            String airlineName = flight.getAirlineName();
                            // ③ 항공사명이 null이거나 빈 값이면 항공편 ID에서 코드 추출
                            if (airlineName == null || airlineName.trim().isEmpty()) {
                                String flightId = flight.getFlightId();
                                if (flightId != null && flightId.length() >= 2) {
                                    String airlineCode = flightId.substring(0, 2);
                                    log.warn("항공사명 없음, 항공편 ID에서 추출: {} -> {}",
                                        flightId, airlineCode);
                                    return airlineCode;
                                }
                                return "알 수 없는 항공사";
                            }
                            return airlineName;
                        },
                        TreeMap::new,      // ④ TreeMap 사용으로 항공사명 알파벳순 정렬
                        Collectors.toList()
                ));

        log.info("그룹핑 완료: {}개 항공사, {}개 항공편",
            grouped.size(), flights.size());
        return grouped;
    }

    /**
     * 항공편을 출발 시간대별로 그룹핑합니다.
     */
    public Map<String, List<FlightInfoResponse>> groupByTimeRange(
            List<FlightInfoResponse> flights) {
        // 아침 (06:00-12:00), 점심 (12:00-14:00), 오후 (14:00-18:00),
        // 저녁 (18:00-22:00), 밤 (22:00-06:00) 으로 그룹핑
    }
}
```

> 📖 **Spring Boot 복습**: `Collectors.groupingBy()`는 Java Stream API의 강력한 기능입니다.
> SQL의 `GROUP BY`와 같은 역할을 합니다. `TreeMap::new`를 사용하면
> 결과가 **키(항공사명)의 알파벳 순서**로 정렬됩니다.

---

## Step 9-4: NaturalLanguageOrchestrationService 구현

### 실제 오케스트레이션 서비스

> ### 왜 Orchestrator가 필요할까요?
> Step 8에서 만든 에이전트들을 **누가, 어떤 순서로** 호출할지 결정해야 합니다.
> 각 에이전트는 자기 일만 알고, 전체 흐름은 모릅니다.
> **Orchestrator**가 "이 순서로 일을 처리해라"라고 지시하는 역할을 합니다.
>
> Spring Boot에서 `OrderService`가 여러 하위 Service를 순서대로 호출하는 것과 같은 원리입니다.

이 프로젝트에서는 `MultiAgentOrchestrator` 대신 **`NaturalLanguageOrchestrationService`**가 오케스트레이터 역할을 수행합니다. LLM 파라미터 추출부터 결과 반환까지 전체 흐름을 10단계로 조율합니다.

```java
package com.nhnacademy.flyschedule.service.orchestration;

import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import com.nhnacademy.flyschedule.service.agent.*;
import com.nhnacademy.flyschedule.service.ai.LlmAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 자연어 항공편 검색 오케스트레이션 서비스
 *
 * Coordinator 패턴으로 여러 A2A 에이전트들을 조율합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NaturalLanguageOrchestrationService {

    // ① 오케스트레이터가 조율할 에이전트들 (DI로 주입)
    private final LlmAnalysisService llmAnalysisService;
    private final DateParserAgent dateParserAgent;
    private final AirportCodeAgent airportCodeAgent;
    private final FlightSearchAgent flightSearchAgent;
    private final TimeFilterAgent timeFilterAgent;
    private final PriceFilterAgent priceFilterAgent;
    private final GroupingAgent groupingAgent;

    /**
     * 자연어 메시지를 분석하여 항공편을 검색하고 결과를 반환합니다.
     *
     * 전체 흐름 (10단계):
     * 1. LLM 파라미터 추출
     * 2. 파라미터 검증
     * 3. 날짜 처리 (DateParserAgent)
     * 4. 공항 코드 변환 (AirportCodeAgent)
     * 5. 항공편 검색 (FlightSearchAgent)
     * 6. 시간 필터링 - 이후 (TimeFilterAgent)
     * 7. 시간 필터링 - 이전 (TimeFilterAgent)
     * 8. 가격 필터링 (PriceFilterAgent)
     * 9. 항공사별 그룹핑 (GroupingAgent)
     * 10. 결과 변환
     */
    public OrchestrationResult orchestrateFlightSearch(String message) {
        log.info("자연어 항공편 검색 오케스트레이션 시작: {}", message);

        // ========== 1단계: LLM으로 파라미터 추출 ==========
        // ② "내일 광주에서 제주 오전 10시 이후" →
        //    {departure:"광주", arrival:"제주", date:"내일", afterTime:"10:00"}
        log.info("단계 1: LLM 파라미터 추출");
        Map<String, Object> params = llmAnalysisService.extractFlightSearchParams(message);

        // ========== 2단계: 파라미터 검증 ==========
        // ③ 필수 파라미터(출발/도착 공항)가 없으면 에러 반환
        log.info("단계 2: 파라미터 검증");
        if (!params.containsKey("departure") || !params.containsKey("arrival")) {
            return OrchestrationResult.error("출발 공항과 도착 공항을 명확히 입력해주세요.");
        }

        // ========== 3단계: 날짜 처리 (DateParserAgent) ==========
        // ④ "내일" → "20260614" 로 변환. date가 없으면 오늘 날짜 사용
        log.info("단계 3: 날짜 처리");
        String dateStr = (String) params.getOrDefault("date", LocalDate.now().toString());
        String parsedDate = dateParserAgent.parseDate(dateStr);
        log.info("날짜: {} -> {}", dateStr, parsedDate);

        // ========== 4단계: 공항 코드 변환 (AirportCodeAgent) ==========
        // ⑤ "광주" → "NAARKJJ", "제주" → "NAARKPC" 로 변환
        log.info("단계 4: 공항 코드 변환");
        String departure = (String) params.get("departure");
        String arrival = (String) params.get("arrival");
        String depAirportId = airportCodeAgent.getAirportCode(departure);
        String arrAirportId = airportCodeAgent.getAirportCode(arrival);
        log.info("공항 코드: {} -> {}, {} -> {}",
            departure, depAirportId, arrival, arrAirportId);

        // ========== 5단계: 항공편 검색 (FlightSearchAgent) ==========
        // ⑥ 외부 API를 호출하여 항공편 목록을 가져옵니다.
        log.info("단계 5: 항공편 검색");
        Map<String, List<FlightInfoResponse>> flightsMap =
            flightSearchAgent.searchAndGroupByAirline(depAirportId, arrAirportId, parsedDate);
        List<FlightInfoResponse> flights = flightsMap.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
        log.info("검색된 항공편: {}편", flights.size());

        // ========== 6단계: 시간 필터링 - 이후 (TimeFilterAgent) ==========
        // ⑦ afterTime 파라미터가 있으면 해당 시간 이후 항공편만 필터링
        if (params.containsKey("afterTime")) {
            log.info("단계 6a: 시간 필터링 (이후)");
            String afterTime = (String) params.get("afterTime");
            LocalTime time = LocalTime.parse(afterTime);
            flights = timeFilterAgent.filterAfterTime(flights, time);
            log.info("{} 이후 필터링: {}편", afterTime, flights.size());
        }

        // ========== 7단계: 시간 필터링 - 이전 (TimeFilterAgent) ==========
        // ⑧ beforeTime 파라미터가 있으면 해당 시간 이전 항공편만 필터링
        if (params.containsKey("beforeTime")) {
            log.info("단계 6b: 시간 필터링 (이전)");
            String beforeTime = (String) params.get("beforeTime");
            LocalTime time = LocalTime.parse(beforeTime);
            flights = timeFilterAgent.filterBeforeTime(flights, time);
            log.info("{} 이전 필터링: {}편", beforeTime, flights.size());
        }

        // ========== 8단계: 가격 필터링 (PriceFilterAgent) ==========
        // ⑨ 가격 조건이 있으면 해당 범위의 항공편만 필터링
        if (params.containsKey("minPrice") || params.containsKey("maxPrice")) {
            log.info("단계 7: 가격 필터링");
            Integer minPrice = params.containsKey("minPrice") ?
                    Integer.parseInt((String) params.get("minPrice")) : null;
            Integer maxPrice = params.containsKey("maxPrice") ?
                    Integer.parseInt((String) params.get("maxPrice")) : null;
            flights = priceFilterAgent.filterByPriceRange(flights, minPrice, maxPrice);
            log.info("가격 필터링 ({}~{}): {}편", minPrice, maxPrice, flights.size());
        }

        // ========== 9단계: 항공사별 그룹핑 (GroupingAgent) ==========
        // ⑩ 필터링된 결과를 항공사별로 다시 그룹핑
        log.info("단계 8: 항공사별 그룹핑");
        Map<String, List<FlightInfoResponse>> groupedFlights =
            groupingAgent.groupByAirline(flights);
        log.info("그룹핑 완료: {}개 항공사", groupedFlights.size());

        // ========== 10단계: 결과 변환 ==========
        // ⑪ FlightInfoResponse 객체를 JSON 응답용 Map으로 변환
        log.info("단계 9: 결과 변환");
        Map<String, List<Map<String, Object>>> resultData = groupedFlights.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue().stream()
                            .map(this::convertToMap)
                            .collect(Collectors.toList())
                ));

        log.info("오케스트레이션 완료");
        return OrchestrationResult.success(params, resultData);
    }

    /**
     * FlightInfoResponse를 Map으로 변환
     */
    private Map<String, Object> convertToMap(FlightInfoResponse flight) {
        return Map.of(
            "vihicleId", flight.getFlightId(),
            "airlineNm", flight.getAirlineName(),
            "depPlandTime", flight.getDepartureTime(),
            "arrPlandTime", flight.getArrivalTime(),
            "economyCharge", flight.getEconomyCharge(),
            "prestigeCharge", flight.getPrestigeCharge(),
            "depAirportNm", flight.getDepartureAirport(),
            "arrAirportNm", flight.getArrivalAirport()
        );
    }

    /**
     * 오케스트레이션 결과
     */
    public static class OrchestrationResult {
        // ... success(), error(), toResponse() 등 제공
    }
}
```

> 📖 **Spring Boot 복습**: 여러 Service를 조합하는 것은 Spring Boot에서 흔히 하는 일입니다.
> `OrderService`가 `ProductService`, `MemberService`, `PaymentService`를 사용하는 것과 같습니다.
> `NaturalLanguageOrchestrationService`도 여러 Agent를 조합합니다.
> `@RequiredArgsConstructor` + `private final` 필드 조합으로 생성자 주입을 간결하게 표현합니다.

### 오케스트레이션 흐름도

```
사용자: "내일 오후 2시 이후 광주에서 제주로 가는 항공편 알려줘"
                        ↓
┌─────────────────────────────────────────────────────────┐
│  NaturalLanguageOrchestrationService (10단계 조율)       │
│                                                         │
│  1. LlmAnalysisService → {departure:광주, arrival:제주, │
│     date:내일, afterTime:14:00}                          │
│                                                         │
│  2. 파라미터 검증 (departure, arrival 존재 확인)        │
│                                                         │
│  3. DateParserAgent   "내일" → "20260310"               │
│                                                         │
│  4. AirportCodeAgent  "광주" → "NAARKJJ"                │
│                      "제주" → "NAARKPC"                  │
│                                                         │
│  5. FlightSearchAgent → 항공편 목록 (항공사별 그룹핑)    │
│                                                         │
│  6. TimeFilterAgent   14:00 이후 필터링                  │
│                                                         │
│  7. (beforeTime가 있으면 TimeFilterAgent 이전 필터링)    │
│                                                         │
│  8. (가격 조건이 있으면 PriceFilterAgent 가격 필터링)    │
│                                                         │
│  9. GroupingAgent     항공사별 재그룹핑                  │
│                                                         │
│  10. convertToMap()   FlightInfoResponse → Map 변환      │
│                                                         │
└─────────────────────────────────────────────────────────┘
                        ↓
               OrchestrationResult 반환
```

---

## Step 9-5: 에러 처리 및 재시도

> ### 왜 에러 처리가 중요할까요?
> 외부 API 호출, LLM 응답 등은 언제든 실패할 수 있습니다.
> 사용자에게 에러 메시지만 보여주는 것보다, **재시도**하거나 **대안(Fallback)**을
> 제공하는 것이 더 나은 경험을 줍니다.

### 패턴 1: 예외 전파

```java
public Map<String, List<FlightInfoResponse>> coordinateBasicSearch(
        String departure, String arrival, String date) {

    try {
        // 에이전트 호출
        return flightSearchAgent.searchAndGroupByAirline(departure, arrival, date);
    } catch (IllegalArgumentException e) {
        // ① 파라미터 에러 → 사용자 입력이 잘못됨. 즉시 전파
        log.error("파라미터 에러: {}", e.getMessage());
        throw e;
    } catch (Exception e) {
        // ② 시스템 에러 → 외부 API 장애 등. 에러를 감싸서 전파
        log.error("시스템 에러", e);
        throw new RuntimeException("항공편 검색 중 오류가 발생했습니다.", e);
    }
}
```

> 📖 **Spring Boot 복습**: `@RestController`에서 예외가 발생하면 500 에러가 반환됩니다.
> `@ControllerAdvice` + `@ExceptionHandler`를 사용하면 에러 응답을 깔끔하게 처리할 수 있습니다.

### 패턴 2: 재시도

```java
public Map<String, List<FlightInfoResponse>> coordinateBasicSearchWithRetry(
        String departure, String arrival, String date) {

    int maxRetries = 3;
    int retryCount = 0;

    while (retryCount < maxRetries) {
        try {
            // ① 정상 응답을 받으면 바로 반환
            return flightSearchAgent.searchAndGroupByAirline(departure, arrival, date);
        } catch (Exception e) {
            retryCount++;
            if (retryCount >= maxRetries) {
                // ② 최대 재시도 횟수 초과 시 예외 발생
                log.error("최대 재시도 횟수 초과: {}", maxRetries);
                throw new RuntimeException("항공편 검색 실패", e);
            }
            log.warn("재시도 {}/{}: {}", retryCount, maxRetries, e.getMessage());

            // ③ 점진적 지연 (Exponential Backoff)
            //    1초 → 2초 → 3초 로 점점 길게 대기
            try {
                Thread.sleep(1000L * retryCount);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("재시도 중단", ie);
            }
        }
    }

    throw new RuntimeException("알 수 없는 에러");
}
```

### 패턴 3: Fallback

NaturalLanguageOrchestrationService에서 사용하는 LlmAnalysisService도 fallback 패턴을 적용합니다. LLM 호출이 실패하면 정규식 기반 파싱으로 대체합니다.

```java
// LlmAnalysisService의 fallback 패턴
public Map<String, Object> extractFlightSearchParams(String message) {
    // ① 먼저 정규식 기반 파싱 시도 (빠르고 확실)
    try {
        Map<String, Object> parsed = parseWithRegex(message);
        if (parsed != null && !parsed.isEmpty()) {
            return parsed;
        }
    } catch (Exception e) {
        log.warn("정규식 파싱 실패, LLM 시도: {}", e.getMessage());
    }

    // ② 정규식으로 못 잡으면 LLM 호출 시도
    try {
        String response = chatClient.prompt().user(prompt).call().content();
        return parseResponse(response);
    } catch (Exception e) {
        log.error("LLM 호출 실패, 정규식 fallback 사용", e);
        // ③ LLM도 실패하면 다시 정규식 기반 파싱 사용 (마지막 수단)
        Map<String, Object> fallbackResult = parseWithRegex(message);
        if (fallbackResult != null && !fallbackResult.isEmpty()) {
            return fallbackResult;
        }
        throw new RuntimeException("자연어 분석에 실패했습니다.");
    }
}
```

---

## Step 9-6: Coordinator 패턴의 장단점

### 장점

| 장점 | 설명 | 예시 |
|------|------|------|
| **중앙 집중식 제어**| 작업 흐름을 한 곳에서 관리 | NaturalLanguageOrchestrationService에서 전체 흐름 확인 가능 |
| **재사용성**| 여러 곳에서 Orchestrator 활용 | 다른 컨트롤러에서도 사용 가능 |
| **테스트 용이성**| Orchestrator만 테스트하면 됨 | 각 에이전트는 Mock으로 대체 |
| **유연성**| 에이전트 교체가 쉬움 | DateParser → NewDateParser로 교체 |

### 단점

| 단점 | 설명 | 해결 방법 |
|------|------|----------|
| **복잡도 증가**| 중간 계층이 추가됨 | 간단한 경우는 직접 호출 |
| **병목 가능성**| 모든 호출이 Orchestrator를 거침 | 비동기 처리로 해결 |
| **단일 실패점**| Orchestrator 실패 시 전체 실패 | 재시도, Fallback 추가 |

---

## 요약

이 단계에서 학습한 핵심 내용:

1. **Orchestrator**는 여러 에이전트를 순서대로 호출하는 "지휘자" 역할을 합니다
2. **NaturalLanguageOrchestrationService**는 10단계로 자연어 항공편 검색을 조율합니다
3. **에러 처리**에는 예외 전파, 재시도(Exponential Backoff), Fallback 3가지 패턴이 있습니다
4. Coordinator 패턴은 **제어권이 코드에 있어** 흐름을 예측하기 쉽지만, 유연성은 낮습니다

> 다음 Step 10에서는 LLM이 **직접** 어떤 도구를 호출할지 결정하는 A2A 방식을 배우고,
> Coordinator 방식과 비교해 보겠습니다.

---

## 체크리스트

- [ ] `FlightSearchAgent`가 5개 하위 에이전트와 함께 구현됨
- [ ] `TimeFilterAgent`가 시간 필터링을 수행함
- [ ] `PriceFilterAgent`가 가격 필터링을 수행함
- [ ] `GroupingAgent`가 항공사별 그룹핑을 수행함
- [ ] `NaturalLanguageOrchestrationService`가 10단계 오케스트레이션을 수행함
- [ ] 에러 처리가 구현됨

---

## 다음 단계

**Step 10: 복합 A2A 시스템과 자연어 검색**에서 완전한 자연어 검색 시스템을 구현합니다.

---

## 참고 자료

- [Mediator Pattern](https://en.wikipedia.org/wiki/Mediator_pattern)
- [Orchestration vs Choreography](https://martinfowler.com/bliki/Orchestration.html)
- [Enterprise Integration Patterns](https://www.enterpriseintegrationpatterns.com/)
