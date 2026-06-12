# Step 8: A2A 개념과 단일 에이전트 구현

## 학습 목표

이 단계를 마치면 다음을 할 수 있습니다:

1. **A2A (Agent-to-Agent) 패턴**이 무엇인지, 왜 필요한지 설명할 수 있다
2. **DateParserAgent**, **AirportCodeAgent** 같은 단일 책임 에이전트를 직접 구현할 수 있다
3. 여러 에이전트를 **체인(Chain) 방식**으로 연결하여 복합 작업을 수행할 수 있다
4. Spring Boot의 **단일 책임 원칙(SRP)**이 A2A 패턴에서 어떻게 적용되는지 이해한다

---

> 💡 **핵심 개념**: A2A (Agent-to-Agent)는 여러 **전문화된 에이전트**가 협력하여 복잡한 작업을 해결하는 패턴입니다.
>
> 일상생활에 비유하면: **병원**에서 환자가 접수원에게 증상을 말하고,
> 접수원이 환자를 적절한 전문의 (안과, 이비인후과, 정형외과 등)에게 안내하는 것과 비슷합니다.
> 각 의사는 자기 전문 분야만 진료하고, 접수원이 이를 조율합니다.

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

### 왜 에이전트를 분리할까요?

하나의 Service 클래스에 모든 로직을 넣으면 코드가 길어지고 수정하기 어렵습니다.
Spring Boot에서 Service를 도메인별로 나누는 것과 같은 원리입니다.

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

> 📖 **Spring Boot 복습**: `UserService`에 로그인, 회원가입, 비밀번호 변경, 이메일 발송까지
> 모두 넣은 경험이 있으신가요? 코드가 수백 줄이 되고, 하나를 수정하면 다른 기능이 고장 나기 쉽습니다.
> Spring Boot에서는 이를 `AuthService`, `EmailService`, `PasswordService` 등으로 분리합니다.
> A2A도 같은 원리입니다. 각 에이전트는 하나의 **명확한 책임**만 가집니다.

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

> ### 왜 계층 구조가 필요할까요?
> 회사를 생각해보세요. 사장이 모든 직원에게 직접 일을 지시하면 혼란스럽습니다.
> 팀장(Coordinator)이 있고, 각 팀원(Agent)이 자기 역할만 담당하는 구조가 효율적입니다.

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

> ### 왜 DateParserAgent가 필요할까요?
> 사용자는 "내일", "모레"라고 말하지만, 항공편 API는 `"20260613"` 같은 **YYYYMMDD 형식**만 이해합니다.
> 이 변환을 담당하는 전담 에이전트가 있으면, 다른 곳에서도 재사용할 수 있고
> 날짜 처리 규칙이 바뀌면 이 에이전트만 수정하면 됩니다.

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
 * 사용자의 자연어 날짜 표현을 API 요청 형식으로 변환합니다.
 * 지원하는 표현: "내일", "모레", "글피", "2026-03-25"
 */
@Slf4j
@Service                          // ① @Service: Spring Boot의 Bean으로 등록합니다.
public class DateParserAgent {    //    일반 Service와 완전히 동일합니다!

    // ② API가 요구하는 날짜 형식 (예: "20260613")
    private static final DateTimeFormatter API_DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyyMMdd");

    // ③ 사용자가 입력할 수 있는 날짜 형식 (예: "2026-06-13")
    private static final DateTimeFormatter INPUT_DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 사용자의 날짜 표현을 API 요청 형식(YYYYMMDD)으로 변환합니다.
     *
     * @param dateInput 날짜 입력 (예: "내일", "2026-03-25", "20260325")
     * @return API 요청 형식의 날짜 (예: "20250309")
     */
    public String parseDate(String dateInput) {
        log.info("DateParserAgent: 날짜 파싱 요청 - {}", dateInput);

        // ④ null이나 빈 값이면 오늘 날짜를 기본값으로 사용
        if (dateInput == null || dateInput.isBlank()) {
            return LocalDate.now().format(API_DATE_FORMATTER);
        }

        String normalized = dateInput.trim();

        // ⑤ 이미 API 형식(YYYYMMDD, 숫자 8자리)인 경우 변환 없이 그대로 반환
        if (normalized.matches("\\d{8}")) {
            log.info("DateParserAgent: 이미 API 형식의 날짜 - {}", normalized);
            return normalized;
        }

        normalized = normalized.toLowerCase();

        // ⑥ switch 표현식으로 자연어 날짜를 실제 날짜로 변환
        return switch (normalized) {
            case "오늘" -> LocalDate.now().format(API_DATE_FORMATTER);
            case "내일" -> LocalDate.now().plusDays(1).format(API_DATE_FORMATTER);
            case "모레", "내일모레" -> LocalDate.now().plusDays(2).format(API_DATE_FORMATTER);
            case "글피" -> LocalDate.now().plusDays(3).format(API_DATE_FORMATTER);
            default -> parseSpecificDate(dateInput);  // ⑦ 그 외는 "YYYY-MM-DD" 형식으로 시도
        };
    }

    /**
     * 특정 날짜 형식(YYYY-MM-DD)을 파싱합니다.
     *
     * @param dateInput 날짜 문자열
     * @return API 요청 형식의 날짜
     * @throws IllegalArgumentException 잘못된 날짜 형식
     */
    private String parseSpecificDate(String dateInput) {
        try {
            // ⑧ "2026-03-25" 문자열을 LocalDate 객체로 파싱한 뒤 "20260325"로 변환
            LocalDate date = LocalDate.parse(dateInput, INPUT_DATE_FORMATTER);
            return date.format(API_DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            // ⑨ 파싱 실패 시 명확한 에러 메시지와 함께 예외 발생
            log.error("잘못된 날짜 형식: {}", dateInput);
            throw new IllegalArgumentException(
                "날짜 형식이 올바르지 않습니다. (YYYY-MM-DD 또는 '내일', '모레' 등)");
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

> 📖 **Spring Boot 복습**: `@Service` 어노테이션은 Spring 컨테이너에 Bean으로 등록하라는 뜻입니다.
> A2A Agent도 똑같이 `@Service`를 사용합니다. 코드 레벨에서는 일반 Service와 동일하지만,
> **설계 철학**이 다릅니다. Agent는 "하나의 책임만" 갖는다는 원칙을 엄격히 지킵니다.

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

> ### 왜 AirportCodeAgent가 필요할까요?
> 사용자는 "광주에서 제주"라고 말하지만, 항공편 API는 `NAARKJJ`, `NAARKPC` 같은
> **공항 코드**만 이해합니다. 이 변환을 전담하는 에이전트가 있으면:
> - 공항이 새로 추가되면 이 에이전트만 수정하면 됩니다
> - 다른 Service에서도 공항 코드 변환이 필요할 때 재사용할 수 있습니다
> - 변환 로직에 버그가 있으면 이곳만 확인하면 됩니다

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
@Service                           // ① @Service로 Spring Bean 등록
public class AirportCodeAgent {

    // ② static 블록으로 공항 이름→코드 매핑 테이블 초기화
    //    애플리케이션 실행 시 한 번만 생성됩니다.
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

    /**
     * 공항 이름을 공항 코드로 변환합니다.
     *
     * @param airportName 공항 이름 (예: "광주", "김포", "제주") 또는 공항 코드 (예: "NAARKJJ")
     * @return 공항 코드 (예: "NAARKJJ", "NAARKSS", "NAARKPC")
     * @throws IllegalArgumentException 알 수 없는 공항
     */
    public String getAirportCode(String airportName) {
        // ③ null/빈 값 검증
        if (airportName == null || airportName.isBlank()) {
            throw new IllegalArgumentException("공항 이름을 입력해주세요.");
        }

        String normalized = airportName.trim();

        // ④ 이미 공항 코드 형식(예: "NAARKJJ")이면 변환 없이 그대로 반환
        if (normalized.matches("NAARK[A-Z]{2}")) {
            log.info("공항 코드 입력됨: {}", normalized);
            return normalized;
        }

        // ⑤ Map에서 공항 이름으로 코드를 조회
        String code = AIRPORT_CODE_MAP.get(normalized);

        if (code == null) {
            // ⑥ 등록되지 않은 공항이면 예외 발생
            log.warn("알 수 없는 공항: {}", airportName);
            throw new IllegalArgumentException("알 수 없는 공항입니다: " + airportName);
        }

        return code;
    }

    /**
     * 공항 코드가 유효한지 확인합니다.
     *
     * @param airportName 공항 이름
     * @return 유효 여부
     */
    public boolean isValidAirport(String airportName) {
        if (airportName == null || airportName.isBlank()) {
            return false;
        }
        return AIRPORT_CODE_MAP.containsKey(airportName.trim());
    }
}
```

> 📖 **Spring Boot 복습**: `AIRPORT_CODE_MAP`은 `static` 블록에서 초기화됩니다.
> 이것은 Spring의 Bean과는 별개로, **클래스가 로딩될 때 한 번만** 실행되는 Java의 기본 기능입니다.
> `@PostConstruct`를 사용해도 되지만, 변경되지 않는 데이터이므로 `static`이 더 간단합니다.

### 공항 코드 매핑 표

| 공항명 | 코드 | "공항" 접미사 | 코드 |
|--------|------|---------------|------|
| 김포 | NAARKSS | 김포공항 | NAARKSS |
| 인천 | NAARKSI | 인천공항 | NAARKSI |
| 김해 | NAARKJB | 김해공항 | NAARKJB |
| 부산 | NAARKJB | - | - |
| 사천 | NAARKPS | 사천공항 | NAARKPS |
| 울산 | NAARKNU | 울산공항 | NAARKNU |
| 광주 | NAARKJJ | 광주공항 | NAARKJJ |
| 여수 | NAARKJY | 여수공항 | NAARKJY |
| 무안 | NAARKJB | 무안공항 | NAARKJB |
| 대구 | NAARKTN | 대구공항 | NAARKTN |
| 포항 | NAARKPK | 포항공항 | NAARKPK |
| 청주 | NAARKNJ | 청주공항 | NAARKNJ |
| 양양 | NAARKNY | 양양공항 | NAARKNY |
| 제주 | NAARKPC | 제주공항 | NAARKPC |

### 사용 예시

```java
AirportCodeAgent airportAgent = new AirportCodeAgent();

airportAgent.getAirportCode("광주");    // "NAARKJJ"
airportAgent.getAirportCode("제주");    // "NAARKPC"
airportAgent.getAirportCode("무안");    // "NAARKJB"
airportAgent.getAirportCode("청주");    // "NAARKNJ"
airportAgent.getAirportCode("인천");    // "NAARKSI"
airportAgent.getAirportCode("김포공항"); // "NAARKSS" ("공항" 접미사 지원)
airportAgent.getAirportCode("NAARKJJ"); // "NAARKJJ" (이미 코드)
airportAgent.isValidAirport("광주");    // true
airportAgent.isValidAirport("화성");    // false
```

---

## Step 8-4: 에이전트 테스트

> **참고:** 아래의 `AgentTestController`는 학습 예제입니다. 실제 프로젝트에는 존재하지 않으며,
> 에이전트 테스트 방법을 설명하기 위한 가상 코드입니다.

### AgentTestController.java (학습용 예제)

> ### 왜 테스트 컨트롤러를 만들까요?
> 각 에이전트가 올바르게 동작하는지 **개별적으로 확인**하려면,
> 에이전트를 직접 호출할 수 있는 테스트용 컨트롤러가 편리합니다.
> Spring Boot에서 `@RestController`를 만들어 Service를 테스트해본 경험이 있으실 겁니다.

```java
package com.nhnacademy.flyschedule.controller;

import com.nhnacademy.flyschedule.service.agent.AirportCodeAgent;
import com.nhnacademy.flyschedule.service.agent.DateParserAgent;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
public class AgentTestController {

    // ① 생성자 주입으로 에이전트를 주입받습니다.
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
            // ② DateParserAgent만 단독으로 테스트
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
            // ③ AirportCodeAgent만 단독으로 테스트
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
            // ④ 두 에이전트를 순차적으로 호출 (체이닝)
            //    AirportCodeAgent → AirportCodeAgent → DateParserAgent 순서
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

> 📖 **Spring Boot 복습**: 생성자 주입(`new AgentTestController(dateParser, airportAgent)`)은
> Spring Boot에서 권장하는 DI 방식입니다. `@Autowired`를 생략해도 Spring 4.3+에서는
> 생성자가 하나뿐이면 자동으로 주입합니다.

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

> ### 왜 통신 패턴을 알아야 할까요?
> 에이전트들이 어떻게 협력하느냐에 따라 성능과 복잡도가 달라집니다.
> 마치 팀 프로젝트에서 순차적으로 일을 전달할지, 동시에 일을 시작할지 정하는 것과 같습니다.

### 패턴 1: 순차적 통신 (Sequential)

한 에이전트의 결과를 다음 에이전트의 입력으로 전달합니다.

```
DateParser → "20260310"
    ↓
AirportCode → "NAARKJJ"
    ↓
FlightSearch → 항공편 목록
```

> 📖 **Spring Boot 복습**: 이 방식은 Spring Boot의 메서드 체이닝과 같습니다.
> `userService.getUser(id).getName()` 처럼, 앞의 결과를 뒤에서 사용합니다.

### 패턴 2: 병렬 통신 (Parallel)

여러 에이전트가 동시에 작업하고, 결과를 모아서 다음 단계로 전달합니다.

```
DateParser → "20260310" ─┐
AirportCode (dep) → "NAARKJJ" ┤→ FlightSearch
AirportCode (arr) → "NAARKPC" ─┘
```

### 패턴 3: 파이프라인 (Pipeline)

데이터가 여러 에이전트를 거치며 점진적으로 처리됩니다.

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
| **FlightSearchAgent**| 항공편 검색 (하위 에이전트 조율) | 직접 날짜 파싱, 직접 공항 코드 변환 |
| **TimeFilterAgent**| 시간 필터링 | 가격 필터링, 항공편 검색 |
| **PriceFilterAgent**| 가격 필터링 | 시간 필터링, 항공편 검색 |
| **GroupingAgent**| 항공사별 그룹핑 | 필터링, 검색 |

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
@RequiredArgsConstructor
public class FlightSearchAgent {

    // ① DI로 다른 에이전트를 주입받아 사용
    private final DateParserAgent dateParserAgent;
    private final AirportCodeAgent airportCodeAgent;
    private final TimeFilterAgent timeFilterAgent;
    private final PriceFilterAgent priceFilterAgent;
    private final GroupingAgent groupingAgent;

    public Map<String, List<Flight>> search(String departure, String arrival, String date) {
        // ② 다른 에이전트에 위임 — 직접 구현하지 않음
        String formattedDate = dateParserAgent.parseDate(date);
        String depCode = airportCodeAgent.getAirportCode(departure);
        String arrCode = airportCodeAgent.getAirportCode(arrival);

        // ③ 본연의 책임만 수행 — 항공편 검색
        return apiClient.search(depCode, arrCode, formattedDate);
    }
}
```

> 📖 **Spring Boot 복습**: `@RequiredArgsConstructor`는 Lombok 어노테이션으로,
> `final` 필드만 포함하는 생성자를 자동 생성합니다. 즉, 아래 코드와 동일합니다:
> ```java
> public FlightSearchAgent(DateParserAgent dateParserAgent, AirportCodeAgent airportCodeAgent, ...) {
>     this.dateParserAgent = dateParserAgent;
>     this.airportCodeAgent = airportCodeAgent;
>     ...
> }
> ```
> Spring이 이 생성자를 통해 Bean을 주입합니다.

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

## 전체 흐름 요약

지금까지 구현한 에이전트들이 어떻게 협력하는지 전체 흐름을 살펴보세요:

```
[사용자: "내일 광주에서 제주 항공편 알려줘"]
       ↓
[AgentTestController (에이전트 체이닝)]
       ↓
① AirportCodeAgent → "광주" → "NAARKJJ"
② AirportCodeAgent → "제주" → "NAARKPC"
③ DateParserAgent  → "내일" → "20260614"
       ↓
[결과: 출발 NAARKJJ, 도착 NAARKPC, 날짜 20260614]
(이 데이터로 항공편 API를 호출할 수 있습니다!)
```

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
