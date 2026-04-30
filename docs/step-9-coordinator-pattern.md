# Step 9: Multi-Agent Orchestration 패턴

## 학습 목표
이 단계에서는 여러 전문 에이전트를 **조율하는 Orchestrator**를 구현하고 에이전트 간 통신 패턴을 학습합니다.

**학습 완료 후 할 수 있는 것:**
- Multi-Agent Orchestration 패턴 구현
- 에이전트 간 통신 구현
- 작업 흐름 제어
- 에러 처리 및 재시도

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
         ↓           ↓           ↓           ↓
    ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐
    │ Date   │  │Airport │  │Flight  │  │Grouping│
    │ Parser │  │ Code   │  │ Search│  │ Agent  │
    └────────┘  └────────┘  └────────┘  └────────┘
```

### Orchestrator의 책임

| 책임 | 설명 | 예시 |
|------|------|------|
| **작업 분해**| 복잡한 작업을 작은 단위로 분해 | "광주-제주 항공편" → 3개의 하위 작업 |
| **에이전트 선택**| 적절한 에이전트 선택 및 호출 | 날짜 파싱 필요 → DateParserAgent |
| **데이터 전달**| 에이전트 간 데이터 전달 | DateParser 결과 → AirportCode |
| **결과 통합**| 각 에이전트의 결과를 통합 | 항공사별 그룹핑 |
| **에러 처리**| 실패 시 재시도 또는fallback | 파싱 실패 → 기본값 사용 |

---

## Step 9-1: FlightSearchAgent 구현

### 역할

항공편 검색을 담당하며 다른 에이전트를 활용하여 파라미터를 준비합니다.

```java
package com.nhnacademy.flyschedule.service.agent;

import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import com.nhnacademy.flyschedule.service.api.ApiClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 항공편 검색 A2A 에이전트
 *
 * DateParserAgent, AirportCodeAgent 에이전트와 협력하여 항공편을 검색합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlightSearchAgent {

    private final ApiClientService apiClientService;
    private final DateParserAgent dateParserAgent;
    private final AirportCodeAgent airportCodeAgent;
    private final GroupingAgent groupingAgent;

    /**
     * 항공편을 검색하여 항공사별로 그룹핑합니다.
     *
     * @param departure 출발 공항 이름
     * @param arrival   도착 공항 이름
     * @param date      날짜
     * @return 항공사별로 그룹핑된 항공편
     */
    public Map<String, List<FlightInfoResponse>> searchAndGroupByAirline(
            String departure, String arrival, String date) {

        log.info("FlightSearchAgent: 항공편 검색 시작");

        // 단계 1: 날짜 파싱 (DateParserAgent)
        log.info("  단계 1: 날짜 파싱");
        String formattedDate = dateParser.parseDate(date);
        log.info("  → 날짜: {} → {}", date, formattedDate);

        // 단계 2: 공항 코드 변환 (AirportCodeAgent)
        log.info("  단계 2: 공항 코드 변환");
        String depCode = airportCodeAgent.getAirportCode(departure);
        String arrCode = airportCodeAgent.getAirportCode(arrival);
        log.info("  → 출발: {} → {}", departure, depCode);
        log.info("  → 도착: {} → {}", arrival, arrCode);

        // 단계 3: API 호출
        log.info("  단계 3: 항공편 API 호출");
        List<FlightInfoResponse> flights = apiClientService.getFlightSchedule(
                depCode, arrCode, formattedDate);
        log.info("  → {}편 조회 완료", flights.size());

        // 단계 4: 항공사별 그룹핑 (GroupingAgent)
        log.info("  단계 4: 항공사별 그룹핑");
        Map<String, List<FlightInfoResponse>> grouped = groupingAgent.groupByAirline(flights);

        log.info("FlightSearchAgent: 항공편 검색 완료 ({}개 항공사)", grouped.size());
        return grouped;
    }
}
```

---

## Step 9-2: GroupingAgent 구현

### 역할

항공편을 항공사별로 그룹핑합니다.

```java
package com.nhnacademy.flyschedule.service.agent;

import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 그룹핑 A2A 에이전트
 *
 * 항공편을 다양한 기준으로 그룹핑합니다.
 */
@Slf4j
@Service
public class GroupingAgent {

    /**
     * 항공사별로 항공편을 그룹핑합니다.
     *
     * @param flights 항공편 목록
     * @return 항공사별로 그룹핑된 항공편
     */
    public Map<String, List<FlightInfoResponse>> groupByAirline(List<FlightInfoResponse> flights) {
        log.info("GroupingAgent: 항공사별 그룹핑 시작 ({}편)", flights.size());

        Map<String, List<FlightInfoResponse>> grouped = flights.stream()
                .collect(Collectors.groupingBy(FlightInfoResponse::getAirlineName));

        grouped.forEach((airline, airlineFlights) -> {
            log.info("  {}: {}편", airline, airlineFlights.size());
        });

        log.info("GroupingAgent: 그룹핑 완료 ({}개 항공사)", grouped.size());
        return grouped;
    }

    /**
     * 시간대별로 항공편을 그룹핑합니다.
     */
    public Map<String, List<FlightInfoResponse>> groupByTimeSlot(List<FlightInfoResponse> flights) {
        log.info("GroupingAgent: 시간대별 그룹핑 시작");

        return flights.stream()
                .collect(Collectors.groupingBy(flight -> {
                    int hour = Integer.parseInt(flight.getDepartureTime().substring(0, 2));

                    if (hour < 12) {
                        return "오전";
                    } else if (hour < 18) {
                        return "오후";
                    } else {
                        return "저녁";
                    }
                }));
    }
}
```

---

## Step 9-3: MultiAgentOrchestrator 구현

### MultiAgentOrchestrator 구현

```java
package com.nhnacademy.flyschedule.service.orchestration;

import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import com.nhnacademy.flyschedule.service.agent.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 멀티 에이전트 오케스트레이터
 *
 * 여러 에이전트를 조율하여 항공편 검색 및 추천 작업을 수행합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiAgentOrchestrator {

    private final FlightSearchAgent flightSearchAgent;
    private final TimeFilterAgent timeFilterAgent;
    private final PriceFilterAgent priceFilterAgent;

    /**
     * 기본 항공편 검색을 조율합니다.
     */
    public Map<String, List<FlightInfoResponse>> coordinateBasicSearch(
            String departure, String arrival, String date) {

        log.info("========================================");
        log.info("MultiAgentOrchestrator: 기본 검색 조율 시작");
        log.info("========================================");

        // FlightSearchAgent에 위임 (내부적으로 다른 에이전트 활용)
        Map<String, List<FlightInfoResponse>> result = flightSearchAgent.searchAndGroupByAirline(
                departure, arrival, date);

        log.info("========================================");
        log.info("MultiAgentOrchestrator: 기본 검색 조율 완료");
        log.info("========================================");

        return result;
    }

    /**
     * 시간 필터 검색을 조율합니다.
     */
    public Map<String, List<FlightInfoResponse>> coordinateTimeFilterSearch(
            String departure, String arrival, String date, String afterTime) {

        log.info("========================================");
        log.info("MultiAgentOrchestrator: 시간 필터 검색 조율 시작");
        log.info("시간 조건: {} 이후", afterTime);
        log.info("========================================");

        // 단계 1: 기본 검색
        Map<String, List<FlightInfoResponse>> allFlights = flightSearchAgent.searchAndGroupByAirline(
                departure, arrival, date);

        // 단계 2: 시간 필터링
        log.info("시간 필터링 적용");
        Map<String, List<FlightInfoResponse>> filtered = flightSearchAgent.searchWithTimeFilter(
                departure, arrival, date, afterTime);

        log.info("========================================");
        log.info("MultiAgentOrchestrator: 시간 필터 검색 조율 완료");
        log.info("========================================");

        return filtered;
    }

    /**
     * 가격 필터 검색을 조율합니다.
     */
    public Map<String, List<FlightInfoResponse>> coordinatePriceFilterSearch(
            String departure, String arrival, String date, Integer minPrice, Integer maxPrice) {

        log.info("========================================");
        log.info("MultiAgentOrchestrator: 가격 필터 검색 조율 시작");
        log.info("가격 조건: {} ~ {}원", minPrice, maxPrice);
        log.info("========================================");

        // 단계 1: 기본 검색
        Map<String, List<FlightInfoResponse>> allFlights = flightSearchAgent.searchAndGroupByAirline(
                departure, arrival, date);

        // 단계 2: 가격 필터링
        log.info("가격 필터링 적용");
        Map<String, List<FlightInfoResponse>> filtered = flightSearchAgent.searchWithPriceFilter(
                departure, arrival, date, minPrice, maxPrice);

        log.info("========================================");
        log.info("MultiAgentOrchestrator: 가격 필터 검색 조율 완료");
        log.info("========================================");

        return filtered;
    }
}
```

---

## Step 9-4: Coordinator 테스트

### CoordinatorTestController.java

```java
package com.nhnacademy.flyschedule.controller;

import com.nhnacademy.flyschedule.service.orchestration.MultiAgentOrchestrator;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coordinator")
public class CoordinatorTestController {

    private final MultiAgentOrchestrator coordinator;

    public CoordinatorTestController(MultiAgentOrchestrator coordinator) {
        this.coordinator = coordinator;
    }

    /**
     * 기본 검색 테스트
     * GET /api/coordinator/search?departure=광주&arrival=제주&date=내일
     */
    @GetMapping("/search")
    public String search(
            @RequestParam String departure,
            @RequestParam String arrival,
            @RequestParam String date) {

        var result = coordinator.coordinateBasicSearch(departure, arrival, date);

        StringBuilder sb = new StringBuilder();
        sb.append("조율된 검색 결과:\n\n");

        result.forEach((airline, flights) -> {
            sb.append("[").append(airline).append("] - ").append(flights.size()).append("편\n");
            flights.forEach(f -> {
                sb.append("  ").append(f.getFlightId())
                  .append(" (").append(f.getDepartureTime())
                  .append(" → ").append(f.getArrivalTime())
                  .append(") ").append(f.getEconomyCharge()).append("원\n");
            });
            sb.append("\n");
        });

        return sb.toString();
    }

    /**
     * 시간 필터 테스트
     * GET /api/coordinator/search/time?departure=광주&arrival=제주&date=내일&afterTime=14:00
     */
    @GetMapping("/search/time")
    public String searchWithTimeFilter(
            @RequestParam String departure,
            @RequestParam String arrival,
            @RequestParam String date,
            @RequestParam String afterTime) {

        var result = coordinator.coordinateTimeFilterSearch(departure, arrival, date, afterTime);

        return "시간 필터 결과 (" + afterTime + " 이후):\n\n" +
                result.values().stream()
                        .mapToInt(List::size)
                        .sum() + "편의 항공편이 있습니다.";
    }

    /**
     * 가격 필터 테스트
     * GET /api/coordinator/search/price?departure=광주&arrival=제주&date=내일&minPrice=30000&maxPrice=70000
     */
    @GetMapping("/search/price")
    public String searchWithPriceFilter(
            @RequestParam String departure,
            @RequestParam String arrival,
            @RequestParam String date,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice) {

        var result = coordinator.coordinatePriceFilterSearch(departure, arrival, date, minPrice, maxPrice);

        return "가격 필터 결과 (" + minPrice + "~" + maxPrice + "원):\n\n" +
                result.values().stream()
                        .mapToInt(List::size)
                        .sum() + "편의 항공편이 있습니다.";
    }
}
```

### 테스트 실행

```bash
# 기본 검색
curl "http://localhost:8080/api/coordinator/search?departure=광주&arrival=제주&date=내일"

# 시간 필터
curl "http://localhost:8080/api/coordinator/search/time?departure=광주&arrival=제주&date=내일&afterTime=14:00"

# 가격 필터
curl "http://localhost:8080/api/coordinator/search/price?departure=광주&arrival=제주&date=내일&minPrice=30000&maxPrice=70000"
```

---

## Step 9-5: 에러 처리 및 재시도

### 패턴 1: 예외 전파

```java
public Map<String, List<FlightInfoResponse>> coordinateBasicSearch(
        String departure, String arrival, String date) {

    try {
        // 에이전트 호출
        return flightSearchAgent.searchAndGroupByAirline(departure, arrival, date);
    } catch (IllegalArgumentException e) {
        // 파라미터 에러 → 즉시 전파
        log.error("파라미터 에러: {}", e.getMessage());
        throw e;
    } catch (Exception e) {
        // 시스템 에러 → 래핑하여 전파
        log.error("시스템 에러", e);
        throw new RuntimeException("항공편 검색 중 오류가 발생했습니다.", e);
    }
}
```

### 패턴 2: 재시도

```java
public Map<String, List<FlightInfoResponse>> coordinateBasicSearchWithRetry(
        String departure, String arrival, String date) {

    int maxRetries = 3;
    int retryCount = 0;

    while (retryCount < maxRetries) {
        try {
            return flightSearchAgent.searchAndGroupByAirline(departure, arrival, date);
        } catch (Exception e) {
            retryCount++;
            if (retryCount >= maxRetries) {
                log.error("최대 재시도 횟수 초과: {}", maxRetries);
                throw new RuntimeException("항공편 검색 실패", e);
            }
            log.warn("재시도 {}/{}: {}", retryCount, maxRetries, e.getMessage());

            // 대기 후 재시도
            try {
                Thread.sleep(1000L * retryCount);  // 점진적 지연
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

```java
public Map<String, List<FlightInfoResponse>> coordinateBasicSearchWithFallback(
        String departure, String arrival, String date) {

    try {
        return flightSearchAgent.searchAndGroupByAirline(departure, arrival, date);
    } catch (Exception e) {
        log.warn("기본 검색 실패, 캐시된 데이터 사용: {}", e.getMessage());

        // Fallback: 캐시된 데이터 반환
        return getCachedFlights(departure, arrival, date);
    }
}

private Map<String, List<FlightInfoResponse>> getCachedFlights(
        String departure, String arrival, String date) {

    // 캐시 로직 구현
    log.info("캐시된 데이터 반환: {} → {} ({})", departure, arrival, date);
    return Map.of();  // 빈 맵 반환
}
```

---

## Step 9-6: Coordinator 패턴의 장단점

### 장점

| 장점 | 설명 | 예시 |
|------|------|------|
| **중앙 집중식 제어**| 작업 흐름을 한 곳에서 관리 | Coordinator에서 전체 흐름 확인 가능 |
| **재사용성**| 여러 곳에서 Coordinator 활용 | 다른 컨트롤러에서도 사용 가능 |
| **테스트 용이성**| Coordinator만 테스트하면 됨 | 각 에이전트는 Mock으로 대체 |
| **유연성**| 에이전트 교체가 쉬움 | DateParser → NewDateParser로 교체 |

### 단점

| 단점 | 설명 | 해결 방법 |
|------|------|----------|
| **복잡도 증가**| 중간 계층이 추가됨 | 간단한 경우는 직접 호출 |
| **병목 가능성**| 모든 호출이 Coordinator를 거침 | 비동기 처리로 해결 |
| **단일 실패점**| Coordinator 실패 시 전체 실패 | 재시도, Fallback 추가 |

---

## 체크리스트

- [ ] `FlightCoordinator`가 구현됨
- [ ] 에이전트 간 통신이 동작함
- [ ] 기본 검색이 조율됨
- [ ] 시간 필터 검색이 조율됨
- [ ] 가격 필터 검색이 조율됨
- [ ] 에러 처리가 구현됨

---

## 다음 단계

**Step 10: 복합 A2A 시스템과 자연어 검색**에서 완전한 자연어 검색 시스템을 구현합니다.

---

## 참고 자료

- [Mediator Pattern](https://en.wikipedia.org/wiki/Mediator_pattern)
- [Orchestration vs Choreography](https://martinfowler.com/bliki/Orchestration.html)
- [Enterprise Integration Patterns](https://www.enterpriseintegrationpatterns.com/)
