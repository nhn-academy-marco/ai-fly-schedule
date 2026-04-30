# Step 8: A2A 개념과 단일 에이전트 구현

## 학습 목표
이 단계에서는 **A2A (Agent-to-Agent)**오케스트레이션 패턴을 이해하고 전문화된 단일 에이전트를 구현합니다.

**학습 완료 후 할 수 있는 것:**
- A2A 패턴과 MCP Tool의 차이 이해
- 전문화된 단일 에이전트 구현
- 에이전트 간 통신 구현
- 단일 책임 원칙을 적용한 에이전트 설계

---

## A2A (Agent-to-Agent)란?

### Agent의 기본 개념

**Agent**는 자율적으로 행동하고 목표를 달성하기 위해 환경과 상호작용하는 소프트웨어 개체입니다.

**Agent의 핵심 특징:**

| 특징 | 설명 | 예시 |
|------|------|------|
| **자율성 (Autonomy)**| 스스로 결정하고 행동 | DateParser가 날짜를 스스로 파싱 |
| **반응성 (Reactivity)**| 환경 변화에 즉시 대응 | 공항 코드 요청 시 즉시 변환 |
| **능동성 (Proactivity)**| 목표 달성을 위해 주도적 행동 | 필요한 데이터를 스스로 조회 |
| **사회성 (Social Ability)**| 다른 Agent와 협력 | Coordinator와 통신하며 작업 수행 |

### A2A 정의

**A2A (Agent-to-Agent)**는 여러 **전문화된 에이전트**가 협력하여 복잡한 작업을 해결하는 패턴입니다.

### A2A의 기술적 배경

**단일 Agent의 한계:**

```
┌─────────────────────────────────┐
│     하나의 거대한 Agent          │
│                                 │
│  - 모든 로직을 하나에서 처리    │
│  - 코드 복잡도 높음             │
│  - 테스트/유지보수 어려움       │
│  - 재사용 불가능                │
└─────────────────────────────────┘
```

**A2A의 해결책:**

```
┌─────────────────────────────────────────────────────────┐
│  Coordinator (조율자)                                    │
│  - 전체 흐름 제어                                        │
│  - Agent 간 통신 관리                                    │
└─────────────────────────────────────────────────────────┘
           ↓          ↓          ↓          ↓
    ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
    │DateParser│ │AirportCode│ │FlightSrch│ │ Grouping │
    │  Agent   │ │  Agent   │ │  Agent   │ │  Agent   │
    └──────────┘ └──────────┘ └──────────┘ └──────────┘

    각 Agent는:
    - 단일 책임만 수행
    - 독립적으로 테스트 가능
    - 다른 컨텍스트에서 재사용
```

### A2A vs 다른 아키텍처 패턴

| 항목 | Monolithic | Microservices | A2A |
|------|-----------|---------------|-----|
| **단위**| 하나의 큰 서비스 | 독립된 서비스 | 전문화된 Agent |
| **통신**| 함수 호출 | HTTP/RPC | 메서드 호출 |
| **배포**| 전체 재배포 | 독립 배포 | 같은 앱 내 |
| **목적**| 단순함 | 확장성 | 지능적 협업 |
| **복잡도**| 낮음 | 높음 | 중간 |

**A2A의 독특한 가치:**

- **전문화**: 각 Agent가 특정 도메인에 집중
- **협업**: 여러 Agent가 지식을 합쳐 문제 해결
- **재사용**: Agent를 다른 시나리오에서도 활용
- **확장**: 새로운 전문가 Agent를 쉽게 추가

```
┌─────────────────────────────────────────────────────────┐
│  A2A 오케스트레이션                                       │
│                                                         │
│  사용자 → Coordinator → 전문 에이전트들 → 결과 통합      │
│                                                         │
│  [DateParserAgent]  "내일" → "20260310"                 │
│  [AirportCodeAgent] "광주" → "NAARKJJ"                  │
│  [FlightSearchAgent] → 항공편 목록                       │
│  [GroupingAgent] → 항공사별 그룹핑                       │
└─────────────────────────────────────────────────────────┘
```

### A2A vs MCP Tool

| 항목 | MCP Tool | A2A Agent |
|------|----------|-----------|
| **호출자**| LLM이 직접 호출 | Coordinator가 호출 |
| **제어권**| LLM이 가짐 | Coordinator가 가짐 |
| **복잡도**| 단일 기능 | 복합 기능 |
| **통신 방식**| LLM → Tool | Agent → Agent |
| **용도**| 간단한 함수 호출 | 복잡한 작업 흐름 |

### A2A의 장점

| 장점 | 설명 | 예시 |
|------|------|------|
| **전문화**| 각 에이전트가 특정 역할에 집중 | DateParser는 날짜만 처리 |
| **재사용성**| 에이전트를 다른 컨텍스트에서 재사용 | DateParser는 다른 Service에서도 활용 |
| **테스트 용이성**| 각 에이전트를 독립적으로 테스트 | DateParser 단위 테스트 |
| **확장성**| 새로운 에이전트를 쉽게 추가 | PriceFilterAgent 추가 |
| **디버깅**| 문제가 발생한 에이전트를 쉽게 찾음 | 파싱 실패 시 DateParser 확인 |

---

## Step 8-1: A2A 에이전트 구조

### 에이전트 계층 구조

```
┌───────────────────────────────────────────────────────────┐
│  Coordinator (오케스트레이터)                              │
│  - 전체 작업 조율                                          │
│  - 에이전트 간 통신 관리                                   │
│  - 결과 통합                                               │
└───────────────────────────────────────────────────────────┘
                          ↓
┌───────────────────────────────────────────────────────────┐
│  전문 에이전트들                                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │DateParser    │  │AirportCode   │  │FlightSearch  │  │
│  │Agent         │  │Agent         │  │Agent         │  │
│  │(날짜 파싱)   │  │(공항 코드)   │  │(항공편 검색) │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└───────────────────────────────────────────────────────────┘
```

---

## Step 8-2: DateParserAgent 구현

### 역할

사용자의 자연어 날짜 표현을 API 요청 형식으로 변환합니다.

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
 * Spring Service로 구현된 Agent입니다.
 * Agent = 전문화된 역할을 가진 Service 컴포넌트
 *
 * A2A에서 Agent란:
 * - 특정 도메인(날짜, 공항 코드 등)에 특화된 컴포넌트
 * - Spring의 @Service, @Component로 구현
 * - 다른 Agent/Service에서 주입받아 사용
 * - 단일 책임 원칙 (SRP)을 따름
 */
@Slf4j  // Lombok 로그 어노테이션 (자동으로 Logger 생성)
@Service  // Spring Service Bean (익숙하죠?)
public class DateParserAgent {

    // API 요청 형식: YYYYMMDD (예: 20260310)
    private static final DateTimeFormatter API_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    // 입력 형식: YYYY-MM-DD (예: 2026-03-10)
    private static final DateTimeFormatter INPUT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 사용자의 날짜 표현을 API 요청 형식(YYYYMMDD)으로 변환합니다.
     *
     * Spring Service의 일반 메서드와 동일하게 구현됩니다.
     * A2A Agent = 전문화된 역할을 가진 Spring Service
     *
     * @param dateInput 날짜 입력 (예: "내일", "2026-03-10", "20260310")
     * @return API 요청 형식의 날짜 (예: "20260310")
     *
     * @throws IllegalArgumentException 잘못된 날짜 형식
     */
    public String parseDate(String dateInput) {
        log.info("DateParserAgent: 날짜 파싱 요청 - {}", dateInput);

        // null이거나 빈 문자열이면 오늘 날짜 반환
        // (예: 방어적 프로그래밍)
        if (dateInput == null || dateInput.isBlank()) {
            return LocalDate.now().format(API_DATE_FORMATTER);
        }

        String normalized = dateInput.trim();

        // 이미 API 형식(YYYYMMDD)인지 확인
        // 정규표현식: 숫자 8개 (예: 20260310)
        if (normalized.matches("\\d{8}")) {
            log.info("DateParserParser: 이미 API 형식의 날짜 - {}", normalized);
            return normalized;
        }

        normalized = normalized.toLowerCase();

        // 자연어 표현 처리 (Java 17의 switch expression)
        // "내일", "모레" 등의 상대적 날짜를 실제 날짜로 변환
        return switch (normalized) {
            case "오늘" -> LocalDate.now().format(API_DATE_FORMATTER);
            case "내일" -> LocalDate.now().plusDays(1).format(API_DATE_FORMATTER);
            case "모레", "내일모레" -> LocalDate.now().plusDays(2).format(API_DATE_FORMATTER);
            case "글피" -> LocalDate.now().plusDays(3).format(API_DATE_FORMATTER);
            default -> parseSpecificDate(dateInput);  // 구체적인 날짜 (예: 2026-03-10)
        };
    }

    /**
     * 특정 날짜 형식(YYYY-MM-DD)을 파싱합니다.
     *
     * @param dateInput "2026-03-10" 형식의 날짜 문자열
     * @return "20260310" 형식으로 변환된 날짜
     * @throws IllegalArgumentException 잘못된 날짜 형식
     */
    private String parseSpecificDate(String dateInput) {
        try {
            // String → LocalDate 변환
            LocalDate date = LocalDate.parse(dateInput, INPUT_DATE_FORMATTER);

            // LocalDate → String (YYYYMMDD) 변환
            return date.format(API_DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            log.error("잘못된 날짜 형식: {}", dateInput);

            // 명확한 에러 메시지로 사용자에게 피드백
            throw new IllegalArgumentException(
                "날짜 형식이 올바르지 않습니다. (YYYY-MM-DD 또는 '내일', '모레' 등)"
            );
        }
    }
}
```

### Agent vs Service (차이점)

Spring을 이미 공부하신 분이라면 "Service와 무엇이 다르지?"라고 물으실 수 있습니다.

| 항목 | 일반적인 Service | A2A Agent |
|------|-----------------|-----------|
| **어노테이션**| `@Service` | `@Service` (동일!) |
| **역할**| 비즈니스 로직 처리 | 특정 도메인 전문 처리 |
| **책임**| 여러 기능을 포함 가능 | 단일 책임 (SRP) |
| **사용 예**| UserService, OrderService | DateParserAgent, AirportCodeAgent |
| **호출 방식**| Controller → Service | Coordinator → Agent |

**결론: A2A Agent는 특정 도메인에 특화된 Spring Service입니다!**

### 사용 예시

```java
DateParserAgent dateParser = new DateParserAgent();

dateParser.parseDate("내일");      // "20260310"
dateParser.parseDate("모레");      // "20260311"
dateParser.parseDate("2026-03-10"); // "20260310"
dateParser.parseDate("20260310");   // "20260310" (이미 형식 맞음)
```

---

## Step 8-3: AirportCodeAgent 구현

### 역할

공항 이름을 공항 코드로 변환합니다.

```java
package com.nhnacademy.flyschedule.service.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 공항 코드 매핑 A2A 에이전트
 *
 * 공항 이름을 공항 코드로 변환합니다.
 */
@Slf4j
@Service
public class AirportCodeAgent {

    private static final Map<String, String> AIRPORT_CODE_MAP = new HashMap<>();

    static {
        // 수도권
        AIRPORT_CODE_MAP.put("김포", "NAARKSS");
        AIRPORT_CODE_MAP.put("인천", "NAARKSI");

        // 부산/경남권
        AIRPORT_CODE_MAP.put("김해", "NAARKJB");
        AIRPORT_CODE_MAP.put("부산", "NAARKJB");
        AIRPORT_CODE_MAP.put("울산", "NAARKNU");

        // 호남권
        AIRPORT_CODE_MAP.put("광주", "NAARKJJ");
        AIRPORT_CODE_MAP.put("여수", "NAARKJY");
        AIRPORT_CODE_MAP.put("무안", "NAARKJJ");

        // 영남권
        AIRPORT_CODE_MAP.put("대구", "NAARKTN");
        AIRPORT_CODE_MAP.put("포항", "NAARKPK");

        // 충청/강원권
        AIRPORT_CODE_MAP.put("청주", "NAARKNJ");
        AIRPORT_CODE_MAP.put("양양", "NAARKNY");

        // 제주권
        AIRPORT_CODE_MAP.put("제주", "NAARKPC");
    }

    /**
     * 공항 이름을 공항 코드로 변환합니다.
     *
     * @param airportName 공항 이름 (예: "광주", "김포", "제주") 또는 공항 코드 (예: "NAARKJJ")
     * @return 공항 코드 (예: "NAARKJJ", "NAARKSS", "NAARKPC")
     * @throws IllegalArgumentException 알 수 없는 공항
     */
    public String getAirportCode(String airportName) {
        if (airportName == null || airportName.isBlank()) {
            throw new IllegalArgumentException("공항 이름을 입력해주세요.");
        }

        String normalized = airportName.trim();

        // 이미 공항 코드 형식인 경우
        if (normalized.matches("NAARK[A-Z]{2}")) {
            log.info("공항 코드 입력됨: {}", normalized);
            return normalized;
        }

        String code = AIRPORT_CODE_MAP.get(normalized);

        if (code == null) {
            log.warn("알 수 없는 공항: {}", airportName);
            throw new IllegalArgumentException("알 수 없는 공항입니다: " + airportName);
        }

        log.info("공항 코드 변환: {} → {}", airportName, code);
        return code;
    }

    /**
     * 공항 코드가 유효한지 확인합니다.
     */
    public boolean isValidAirport(String airportName) {
        if (airportName == null || airportName.isBlank()) {
            return false;
        }
        return AIRPORT_CODE_MAP.containsKey(airportName.trim());
    }
}
```

### 사용 예시

```java
AirportCodeAgent airportAgent = new AirportCodeAgent();

airportAgent.getAirportCode("광주");    // "NAARKJJ"
airportAgent.getAirportCode("제주");    // "NAARKPC"
airportAgent.getAirportCode("NAARKJJ"); // "NAARKJJ" (이미 코드)
airportAgent.isValidAirport("광주");    // true
airportAgent.isValidAirport("화성");    // false
```

---

## Step 8-4: 에이전트 테스트

### AgentTestController.java

```java
package com.nhnacademy.flyschedule.controller;

import com.nhnacademy.flyschedule.service.agent.AirportCodeAgent;
import com.nhnacademy.flyschedule.service.agent.DateParserAgent;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
public class AgentTestController {

    private final DateParserAgent dateParser;
    private final AirportCodeAgent airportAgent;

    public AgentTestController(DateParserAgent dateParser, AirportCodeAgent airportAgent) {
        this.dateParser = dateParser;
        this.airportAgent = airportAgent;
    }

    /**
     * 날짜 파싱 테스트
     * GET /api/agent/date-parse?input=내일
     */
    @GetMapping("/date-parse")
    public String testDateParser(@RequestParam String input) {
        try {
            String result = dateParser.parseDate(input);
            return "날짜 파싱 결과: " + input + " → " + result;
        } catch (Exception e) {
            return "파싱 실패: " + e.getMessage();
        }
    }

    /**
     * 공항 코드 변환 테스트
     * GET /api/agent/airport-code?input=광주
     */
    @GetMapping("/airport-code")
    public String testAirportCode(@RequestParam String input) {
        try {
            String result = airportAgent.getAirportCode(input);
            return "공항 코드 변환: " + input + " → " + result;
        } catch (Exception e) {
            return "변환 실패: " + e.getMessage();
        }
    }

    /**
     * 에이전트 체이닝 테스트
     * GET /api/agent/chain?departure=광주&arrival=제주&date=내일
     */
    @GetMapping("/chain")
    public String testAgentChaining(
            @RequestParam String departure,
            @RequestParam String arrival,
            @RequestParam String date) {

        try {
            // 에이전트 체이닝
            String depCode = airportAgent.getAirportCode(departure);
            String arrCode = airportAgent.getAirportCode(arrival);
            String formattedDate = dateParser.parseDate(date);

            return String.format(
                    "에이전트 체이닝 결과:\n" +
                    "  출발: %s → %s\n" +
                    "  도착: %s → %s\n" +
                    "  날짜: %s → %s",
                    departure, depCode,
                    arrival, arrCode,
                    date, formattedDate
            );
        } catch (Exception e) {
            return "처리 실패: " + e.getMessage();
        }
    }
}
```

### 테스트 실행

```bash
# 날짜 파싱
curl "http://localhost:8080/api/agent/date-parse?input=내일"

# 공항 코드 변환
curl "http://localhost:8080/api/agent/airport-code?input=광주"

# 에이전트 체이닝
curl "http://localhost:8080/api/agent/chain?departure=광주&arrival=제주&date=내일"
```

---

## Step 8-5: 에이전트 간 통신 패턴

### 패턴 1: 순차적 통신 (Sequential)

```
DateParser → "20260310"
    ↓
AirportCode → "NAARKJJ"
    ↓
FlightSearch → 항공편 목록
```

### 패턴 2: 병렬 통신 (Parallel)

```
DateParser → "20260310" ─┐
AirportCode (dep) → "NAARKJJ" ┤→ FlightSearch
AirportCode (arr) → "NAARKPC" ─┘
```

### 패턴 3: 파이프라인 (Pipeline)

```
입력 → DateParser → AirportCode → FlightSearch → Grouping → 출력
```

---

## Step 8-6: 단일 책임 원칙 적용

### 원칙

각 에이전트는 **하나의 명확한 책임**만 가져야 합니다.

| 에이전트 | 단일 책임 | 하지 않는 것 |
|---------|-----------|-------------|
| **DateParserAgent**| 날짜 파싱 | 공항 코드 변환, API 호출 |
| **AirportCodeAgent**| 공항 코드 매핑 | 날짜 파싱, 항공편 검색 |
| **FlightSearchAgent**| 항공편 검색 | 날짜 파싱, 공항 코드 변환 |

### 잘못된 예시: 복합 에이전트

```java
// 나쁜 예시: 하나의 에이전트가 너무 많은 일을 함
@Service
public class FlightSearchAgent {

    public Map<String, List<Flight>> search(String departure, String arrival, String date) {
        // 1. 날짜 파싱 (DateParser의 책임)
        String formattedDate = parseDate(date);

        // 2. 공항 코드 변환 (AirportCode의 책임)
        String depCode = getAirportCode(departure);
        String arrCode = getAirportCode(arrival);

        // 3. 항공편 검색 (본연의 책임)
        return apiClient.search(depCode, arrCode, formattedDate);
    }
}
```

### 올바른 예시: 단일 책임 에이전트

```java
// 좋은 예시: 각 에이전트가 자신의 책임만 수행
@Service
public class FlightSearchAgent {

    private final DateParserAgent dateParser;
    private final AirportCodeAgent airportAgent;

    public Map<String, List<Flight>> search(String departure, String arrival, String date) {
        // 다른 에이전트에 위임
        String formattedDate = dateParser.parseDate(date);
        String depCode = airportAgent.getAirportCode(departure);
        String arrCode = airportAgent.getAirportCode(arrival);

        // 본연의 책임만 수행
        return apiClient.search(depCode, arrCode, formattedDate);
    }
}
```

---

## A2A vs 단일 Service 비교

| 항목 | A2A (에이전트) | 단일 Service |
|------|----------------|--------------|
| **코드 재사용성**| 높음 (각 에이전트를 독립적으로 사용) | 낮음 (Service에 결합) |
| **테스트 용이성**| 높음 (각 에이전트를 단위 테스트) | 낮음 (통합 테스트만 가능) |
| **확장성**| 높음 (새로운 에이전트를 쉽게 추가) | 낮음 (Service를 수정해야 함) |
| **복잡도**| 높음 (에이전트 간 통신 관리) | 낮음 (단일 Service) |
| **적합한 경우**| 복잡한 비즈니스 로직 | 단순한 CRUD |

---

## 체크리스트

- [ ] `DateParserAgent`가 구현됨
- [ ] `AirportCodeAgent`가 구현됨
- [ ] 각 에이전트가 단일 책임을 가짐
- [ ] 에이전트 간 통신이 동작함
- [ ] 에이전트 체이닝 테스트 통과

---

## 다음 단계

**Step 9: Coordinator 패턴과 에이전트 통신**에서 여러 에이전트를 조율하는 Coordinator를 구현합니다.

---

## 참고 자료

- [Agent-oriented Programming](https://en.wikipedia.org/wiki/Agent-oriented_programming)
- [Multi-agent Systems](https://en.wikipedia.org/wiki/Multi-agent_system)
- [Single Responsibility Principle](https://en.wikipedia.org/wiki/Single-responsibility_principle)
