# Step 5: 다중 Function Calling과 MCP Tool 패턴

## 학습 목표
이 단계에서는 여러 개의 **Function Calling Tool**을 효과적으로 관리하고 **MCP (Model Context Protocol) Tool 패턴**을 설계하는 방법을 학습합니다.

**학습 완료 후 할 수 있는 것:**
- 여러 Tool을 효과적으로 관리
- MCP Tool 설계 패턴 적용
- Tool 간의 관계와 책임 분리
- 재사용 가능한 Tool 구조 설계

---

## MCP Tool 패턴이란?

**MCP (Model Context Protocol) Tool 패턴**은 LLM이 사용할 수 있는 **도구(Tool)**를 체계적으로 설계하고 구현하는 패턴입니다.

### MCP (Model Context Protocol)의 기원

**MCP**는 Anthropic이 제안한 프로토콜로, AI 모델이 외부 시스템과 상호작용할 수 있는 표준 방식을 정의합니다.

**핵심 목표:**
- AI 모델이 도구(Tools)를 통해 실제 행동을 수행
- 모델과 도구 간의 표준화된 인터페이스 제공
- 확장 가능한 도구 생태계 조성

### MCP Tool의 설계 철학

**1. 선언적 (Declarative)**

```java
// 나쁜 예시: 절차적 코드
if (userInput.contains("항공편") && userInput.contains("검색")) {
    if (userInput.contains("광주")) {
        // 광주 관련 로직
    }
}

// 좋은 예시: 선언적 Tool
@Tool(description = "항공편을 검색합니다")
public Map<String, List<Flight>> searchFlights(
    @ToolParam(description = "출발 공항") String departure,
    @ToolParam(description = "도착 공항") String arrival
) { ... }
```

**2. 자기 설명적 (Self-Descriptive)**

Tool은 스스로를 설명할 수 있어야 합니다:

```java
@Tool(
    description = """
    항공편을 검색하여 항공사별로 그룹핑합니다.

    언제 사용:
    - 사용자가 특정 노선의 항공편을 물을 때
    - 특정 날짜의 스케줄을 조회할 때

    파라미터:
    - departure: 출발 공항 이름
    - arrival: 도착 공항 이름
    - date: 날짜 (YYYY-MM-DD 형식)

    반환값:
    - 항공사별 항공편 목록 (Map 형태)
    """
)
```

**3. 상태 비저장 (Stateless)**

각 Tool 호출은 독립적이어야 합니다:

```java
// 나쁜 예시: 상태 의존
public class BadTool {
    private String cachedDeparture;  // 상태 저장

    public void setDeparture(String dep) { ... }
    public Map<String, List<Flight>> search(String arr) { ... }
}

// 좋은 예시: 상태 비저장
public class GoodTool {
    public Map<String, List<Flight>> search(
        String departure,
        String arrival
    ) { ... }
}
```

### MCP Tool의 핵심 원칙

| 원칙 | 설명 | 예시 |
|------|------|------|
| **단일 책임**| 하나의 Tool은 하나의 명확한 기능만 | `searchFlights`, `getAirports` |
| **명확한 인터페이스**| 파라미터와 반환값이 명확해야 함 | `String departure`, `Map<String, List<Flight>>` |
| **독립성**| 각 Tool은 독립적으로 동작 가능 | `searchFlights`는 `getAirports` 없이도 동작 |
| **재사용성**| 다른 Tool에서도 사용 가능 | `getAirportCode`는 여러 Tool에서 활용 |

---

## Step 5-1: Tool 카테고리 분류

### Tool 카테고리 구조

```
MCP Tools
├── 데이터 조회 Tool (Data Retrieval)
│   ├── getAirportList()      # 전체 공항 목록
│   ├── getAirlineList()      # 전체 항공사 목록
│   └── getFlightSchedule()   # 항공편 스케줄
│
├── 데이터 변환 Tool (Data Transformation)
│   ├── parseDate()           # 날짜 파싱 ("내일" → "2026-03-10")
│   ├── parseTime()           # 시간 파싱 ("오후 2시" → "14:00")
│   └── parsePrice()          # 가격 파싱 ("3만원" → 30000)
│
├── 필터링 Tool (Filtering)
│   ├── filterByTime()        # 시간 필터링
│   ├── filterByPrice()       # 가격 필터링
│   └── filterByAirline()     # 항공사 필터링
│
└── 그룹핑 Tool (Grouping)
    ├── groupByAirline()      # 항공사별 그룹핑
    └── groupByTime()         # 시간대별 그룹핑
```

---

## Step 5-2: Tool 관리 전략

### 전략 1: 기능별 Tool 분리

```java
@Component
public class AirportInfoTool {
    @Tool
    public List<Airport> getAirportList() { ... }

    @Tool
    public String getAirportCode(String name) { ... }
}

@Component
public class AirlineInfoTool {
    @Tool
    public List<Airline> getAirlineList() { ... }

    @Tool
    public String getAirlineId(String name) { ... }
}

@Component
public class FlightSearchTool {
    @Tool
    public Map<String, List<Flight>> searchFlightsByAirline(...) { ... }

    @Tool
    public Map<String, List<Flight>> searchFlightsAfterTime(...) { ... }

    @Tool
    public Map<String, List<Flight>> searchFlightsByPriceRange(...) { ... }
}
```

**장점:**
- 관련 Tool들이 같은 클래스에 모여 있음
- 유지보수가 쉬움
- 기능 확장이 용이

### 전략 2: 단일 Tool 클래스

```java
@Component
public class AllInOneTool {
    @Tool
    public List<Airport> getAirportList() { ... }

    @Tool
    public String getAirportCode(String name) { ... }

    @Tool
    public List<Airline> getAirlineList() { ... }

    @Tool
    public String getAirlineId(String name) { ... }

    @Tool
    public Map<String, List<Flight>> searchFlightsByAirline(...) { ... }
}
```

**장점:**
- 모든 Tool이 한 곳에 있어서 찾기 쉬움
- 설정이 간단

**단점:**
- 클래스가 커질 수 있음
- 관련 없는 Tool들이 섞임

**권장:**전략 1 (기능별 분리)

---

## Step 5-3: Tool 등록 패턴

### 패턴 1: 명시적 등록 (권장)

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

**장점:**
- 어떤 Tool이 등록되었는지 명확
- 순서 제어 가능
- 선택적 Tool 등록 가능

### 패턴 2: 자동 스캔

```java
@Configuration
public class ChatClientConfig {

    @Bean
    @Primary
    public ChatClient.Builder ollamaChatClientBuilder(
            @Qualifier("ollamaChatModel") ChatModel ollamaChatModel,
            ApplicationContext context) {

        // 모든 @Tool 빈 자동 수집
        Map<String, Object> tools = context.getBeansWithAnnotation(Component.class);
        List<Object> toolList = tools.values().stream()
                .filter(bean -> hasToolMethods(bean.getClass()))
                .toList();

        return ChatClient.builder(ollamaChatModel)
                .defaultTools(toolList.toArray(new Object[0]));
    }

    private boolean hasToolMethods(Class<?> clazz) {
        return Arrays.stream(clazz.getMethods())
                .anyMatch(method -> method.isAnnotationPresent(Tool.class));
    }
}
```

**장점:**
- 새 Tool 추가 시 설정 수정 불필요
- 자동으로 모든 Tool 등록

**단점:**
- 등록된 Tool을 확인하기 어려움
- 순서 제어 어려움

---

## Step 5-4: Tool 조합 예시

### 예시 1: 단일 Tool 호출

```
사용자: "공항 리스트 보여줘"
  ↓
LLM: getAirportList() 호출
  ↓
Tool: 공항 목록 반환
  ↓
LLM: "전국 공항 목록입니다..." 응답
```

### 예시 2: 다중 Tool 호출 (순차적)

```
사용자: "광주 공항 코드가 뭐야? 그리고 내일 광주에서 제주 항공편 알려줘"
  ↓
LLM: 1) getAirportCode("광주") 호출
  ↓     "NAARKJJ"
LLM: 2) searchFlightsByAirline("광주", "제주", "내일") 호출
  ↓     항공편 목록
LLM: "광주 공항 코드는 NAARKJJ입니다. 내일 항공편은..." 응답
```

### 예시 3: 다중 Tool 호출 (병렬)

```
사용자: "공항 리스트와 항공사 리스트 알려줘"
  ↓
LLM: 1) getAirportList() 호출 ┐
     2) getAirlineList() 호출┤ (병렬)
  ↓                          ┘
LLM: 결과 통합
LLM: "전국 공항은 15개이고, 항공사는 8개입니다..." 응답
```

---

## Step 5-5: Tool Description 최적화

### 좋은 Description의 5가지 원칙

| 원칙 | 설명 | 나쁜 예시 | 좋은 예시 |
|------|------|----------|----------|
| **1. 무엇을 하는지**| 기능 명확히 설명 | "검색" | "항공편을 검색하여 항공사별로 정리합니다" |
| **2. 언제 사용하는지**| 사용 상황 명시 | (없음) | "사용자가 특정 날짜의 항공편을 물어볼 때 사용" |
| **3. 파라미터 설명**| 각 파라미터 역할 명시 | "날짜" | "날짜 (예: 내일, 모레, 2026-03-10)" |
| **4. 반환값 설명**| 무엇을 반환하는지 명시 | (없음) | "항공사별 항공편 목록을 Map 형태로 반환" |
| **5. 제한 사항**| 제한이 있으면 명시 | (없음) | "항공사별 최대 3편만 반환 (응답 속도 최적화)" |

### Description 예시

```java
@Tool(
    description = """
    항공편을 검색하여 항공사별로 그룹핑하여 반환합니다.

    언제 사용:
    - 사용자가 "A에서 B로 가는 항공편"을 물어볼 때
    - 특정 날짜의 항공편 스케줄을 조회할 때

    파라미터:
    - departure: 출발 공항 이름 (예: 광주, 김포, 제주)
    - arrival: 도착 공항 이름 (예: 제주, 김포, 부산)
    - date: 날짜 표현 (예: 내일, 모레, 2026-03-10)

    반환값:
    - Map<항공사명, List<항공편>> 형태
    - 항공사별로 정리된 항공편 목록
    - 빠른 응답을 위해 항공사별 최대 3편만 반환

    예시:
    - searchFlightsByAirline("광주", "제주", "내일")
    - → {아시아나항공: [펀1, 펀2], 제주항공: [펀1, 펀2, 펀3]}
    """
)
public Map<String, List<FlightInfoResponse>> searchFlightsByAirline(
    @ToolParam(description = "출발 공항 이름 (예: 광주, 김포, 제주)") String departure,
    @ToolParam(description = "도착 공항 이름 (예: 제주, 김포, 부산)") String arrival,
    @ToolParam(description = "날짜 (예: 내일, 모레, 2026-03-10)") String date
) {
    // 구현
}
```

---

## Step 5-6: Tool 설계 패턴

### 패턴 1: 조회 Tool (Read-Only)

```java
@Component
public class ReadOnlyTool {

    @Tool(description = "데이터 조회 (변경 없음)")
    public DataType getData(String id) {
        return repository.findById(id);
    }
}
```

**특징:**
- 부작용(Side Effect) 없음
- 여러 번 호출해도 안전
- 캐싱 가능

### 패턴 2: 명령 Tool (Command)

```java
@Component
public class CommandTool {

    @Tool(description = "데이터 변경 (주의 필요)")
    public Result updateData(String id, Data data) {
        log.warn("데이터 변경: id={}", id);
        return repository.update(id, data);
    }
}
```

**특징:**
- 부작용 있음
- 한 번만 호출해야 함
- 권한 체크 필요

### 패턴 3: 필터링 Tool

```java
@Component
public class FilterTool {

    @Tool(description = "조건에 맞는 데이터만 필터링")
    public List<DataType> filterData(
            List<DataType> data,
            @ToolParam(description = "최소값") Integer min,
            @ToolParam(description = "최대값") Integer max) {

        return data.stream()
                .filter(item -> item.getValue() >= min && item.getValue() <= max)
                .toList();
    }
}
```

**특징:**
- 다른 Tool의 결과를 입력으로 받음
- 순수 함수 형태
- 재사용 가능

---

## Tool 성능 최적화

### 최적화 1: 결과 제한

```java
@Tool(description = "항공편 검색 (최대 100편)")
public List<Flight> searchFlights(...) {
    List<Flight> allFlights = apiClient.search(...)
    return allFlights.stream()
            .limit(100)  // 결과 제한
            .toList();
}
```

### 최적화 2: 비동기 실행

```java
@Tool(description = "항공편 검색 (비동기)")
public CompletableFuture<List<Flight>> searchFlightsAsync(...) {
    return CompletableFuture.supplyAsync(() -> {
        return apiClient.search(...)
    });
}
```

### 최적화 3: 캐싱

```java
@Component
public class CachedTool {

    private final Cache<String, List<Flight>> cache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    @Tool(description = "항공편 검색 (캐싱)")
    public List<Flight> searchFlights(String departure, String arrival, String date) {
        String key = departure + "-" + arrival + "-" + date;

        return cache.get(key, k -> {
            return apiClient.search(departure, arrival, date);
        });
    }
}
```

---

## 일반적인 문제 해결

### 문제 1: LLM이 적절한 Tool을 선택하지 못함

**증상:**다른 Tool이 호출되거나 Tool이 호출되지 않음

**해결 방법:**
1. Description 더 구체화
2. Tool 이름 명확하게
3. 파라미터 설명 상세화

```java
// 나쁜 예시
@Tool(description = "검색")
public List<Flight> search(String from, String to) { ... }

// 좋은 예시
@Tool(description = "항공편을 검색하여 항공사별로 그룹핑합니다")
public Map<String, List<Flight>> searchFlightsByAirline(
    @ToolParam(description = "출발 공항 이름 (예: 광주, 김포)") String departure,
    @ToolParam(description = "도착 공항 이름 (예: 제주, 부산)") String arrival
) { ... }
```

### 문제 2: Tool이 너무 많아서 LLM이 혼란스러워함

**증상:**엉뚱한 Tool이 호출됨

**해결 방법:**
1. 관련 Tool끼리 그룹화
2. `defaultTools()` 대신 특정 상황별로 다른 Tool 세트 사용
3. Tool 수 줄이기 (비슷한 Tool 통합)

```java
// 기본 Tool 세트
@Bean
public ChatClient.Builder defaultBuilder(...) {
    return ChatClient.builder(model)
            .defaultTools(basicTools);
}

// 항공편 검색 전용 Tool 세트
@Bean
public ChatClient.Builder flightSearchBuilder(...) {
    return ChatClient.builder(model)
            .defaultTools(flightSearchTools);
}
```

### 문제 3: Tool 호출 순서 문제

**증상:**선행 Tool의 결과가 필요한데 먼저 호출되지 않음

**해결 방법:**
1. Description에 "먼저 X를 호출해야 함" 명시
2. 필요한 데이터를 파라미터로 받도록 설계
3. Tool 내부에서 다른 Tool 호출

```java
@Tool(
    description = """
    항공편을 검색합니다.

    주의: 먼저 getAirportCode()로 공항 코드를 확인한 후 호출하세요.
    """
)
public List<Flight> searchFlights(
    @ToolParam(description = "출발 공항 코드 (예: NAARKJJ)") String depCode,
    @ToolParam(description = "도착 공항 코드 (예: NAARKPC)") String arrCode
) {
    return apiClient.search(depCode, arrCode);
}
```

---

## 체크리스트

- [ ] Tool이 기능별로 분리됨
- [ ] 각 Tool이 단일 책임을 가짐
- [ ] Tool Description이 명확함
- [ ] Tool이 ChatClient에 등록됨
- [ ] 다중 Tool이 정상적으로 동작함
- [ ] Tool 간의 관계가 명확함

---

## 다음 단계

**Step 6: 항공편 검색 Tool 구현**에서 실제 공공데이터 API와 연동하는 MCP Tool을 구현합니다.

---

## 참고 자료

- [Spring AI Function Calling 가이드](https://docs.spring.io/spring-ai/reference/api/functioncalling.html)
- [MCP (Model Context Protocol) 사양](https://modelcontextprotocol.io/)
- [Tool Design Best Practices](https://docs.spring.io/spring-ai/reference/api/functioncalling.html#_best_practices_for_tool_design)

---

## 심화 학습

### Tool 네이밍 컨벤션

| 패턴 | 예시 | 설명 |
|------|------|------|
| **동사 + 명사**| `searchFlights`, `getAirports` | 무엇을 하는지 명확 |
| **구체적 이름**| `searchFlightsByAirline` | `search`보다 명확 |
| **질문 형태**| `hasFlights`, `canBook` | Boolean 반환일 때 |
| **복수형 사용**| `getAirports`, `listFlights` | 컬렉션 반환일 때 |

### Tool 버전 관리

```java
@Component
public class VersionedTool {

    @Tool(description = "항공편 검색 v2 (빠름)")
    public Map<String, List<Flight>> searchFlightsV2(...) {
        // 새로운 구현
    }

    @Tool(description = "항공편 검색 v1 (호환성)")
    public List<Flight> searchFlightsV1(...) {
        // 레거시 구현
    }
}
```
