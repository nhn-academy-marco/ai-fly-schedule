# Spring AI 에이전트 패턴 실습: 항공편 검색 — 교재 개요

**Spring AI 1.1.4**로 LLM 애플리케이션의 대표적인 세 가지 구조를 만들어 보는 Spring Boot 실습 교재입니다.
공공데이터 포털의 국내 항공편 정보를 Tool로 연결한 뒤, 같은 자연어 질문을 **Coordinator 패턴**, **Tool Calling 에이전트**, **Orchestrator-Workers 패턴**으로 풀어 보고, 실행 기록 화면에서 "다음 단계를 누가 결정하는가"를 비교합니다.

---

## 핵심 용어

| 용어 | 뜻 | 이 프로젝트 |
|------|----|-------------|
| Tool (MCP Tool) | LLM이 호출할 수 있는 자바 메서드. 이 프로젝트의 "MCP Tool"은 MCP 서버가 아니라 Spring AI Tool Calling(`@Tool`)으로 만든 Tool이며, MCP 서버로 노출할 수 있는 형태로 설계했다는 뜻 | `mcp/*` |
| 전문 에이전트 | 한 가지 일만 하는 결정적 클래스. 교재에서 "에이전트"라고 부르지만 **LLM을 쓰지 않음** | `service/agent/*` |
| Coordinator 패턴 | 코드가 처리 순서를 정하고 전문 에이전트를 차례로 호출하는 워크플로 (sequential workflow, prompt chaining) | `FlightSearchCoordinator` |
| Tool Calling 에이전트 | LLM 하나가 Tool 목록을 받아 어떤 Tool을 언제 부를지 스스로 결정 | `ToolCallingSearchService` |
| Orchestrator-Workers 패턴 | LLM(Orchestrator)이 요청을 하위 작업으로 나누고, Worker가 처리하고, LLM이 종합 | `FlightSearchOrchestrator` |
| A2A (Agent2Agent) 프로토콜 | 독립 실행되는 에이전트끼리 HTTP(JSON-RPC)로 통신하는 표준. MCP가 에이전트와 Tool을 잇는다면 A2A는 에이전트와 에이전트를 잇는다 | **구현 없음**. 개념과 비교로만 소개 |

---

## 패턴 한눈에 보기

| 패턴 | 다음 단계를 누가 정하나 | 하위 작업이 미리 정해져 있나 | 한 요청의 LLM 호출 | 엔드포인트 |
|------|------------------------|------------------------------|--------------------|------------|
| Coordinator | 코드 | 예 (7단계 고정) | 0~1회 (정규식 실패 시 검색 조건 추출) | `POST /api/search/coordinator` |
| Tool Calling 에이전트 | LLM | 아니요 (LLM이 Tool 선택) | `call()` 한 번 안에서 여러 차례 왕복 | `POST /api/search/tool-calling` |
| Orchestrator-Workers | LLM(계획·종합) + 코드(실행) | 아니요 (LLM이 작업 수 결정, 최대 4개) | 2회 (계획 + 종합) | `POST /api/search/orchestrator` |

---

## 데모 사이트

**[https://fly.java21.net](https://fly.java21.net)**

- 한 문장을 입력하고 세 패턴을 각각 실행해 결과 비교
- CODE, LLM, TOOL, WORKER로 구분한 실행 기록 타임라인
- Worker가 가상 스레드에서 병렬 실행되는 모습 확인 (Orchestrator-Workers)

---

## 4주 완성 학습 경로

이 교재는 **11개의 Step**으로 구성되어 있습니다.

| 주차 | Step | 주제 | 예상 학습 시간 |
|------|------|------|----------------|
| **1주차** | Step 1~2 | Spring AI 기초 | 7~9시간 |
| **2주차** | Step 3~5 | Function Calling, Advisor, MCP Tool 패턴 | 13~16시간 |
| **3주차** | Step 6~8 | 공공데이터 포털 연동 Tool과 전문 에이전트 | 13~17시간 |
| **4주차** | Step 9~11 | 세 가지 에이전트 패턴 구현과 비교 | 15~18시간 |

모든 Step에는 실제 코드와 연결된 **실습 과제**(기본/도전)가 있습니다.

---

## Step별 문서

### Step 1: Spring AI 환경 설정
**[step-1-spring-ai-settings.md](./step-1-spring-ai-settings.md)**

- Spring AI 1.1.4 의존성 설정 (BOM 관리)
- Ollama(Qwen 2.5)와 Google Gemini(2.5 Flash) 연동
- 읽기 타임아웃(`spring.http.client.read-timeout`)과 재시도(`spring.ai.retry`) 설정
- 환경 변수로 키 관리 (`DATA_GO_KR_SERVICE_KEY` 필수, `GOOGLE_GENAI_API_KEY` 선택)

---

### Step 2: ChatClient 기초와 LLM 연동
**[step-2-chatclient-basics.md](./step-2-chatclient-basics.md)**

- `ChatClientConfig`에서 모델별 `ChatClient` 빈 등록
- `ChatClientProvider`로 요청마다 모델 선택과 오류 안내
- `.content()`와 `.entity(SearchCriteria.class)`(Structured Output)
- `SearchController`와 `POST /api/search/*` 엔드포인트

---

### Step 3: Function Calling 기초
**[step-3-function-calling.md](./step-3-function-calling.md)**

- **@Tool**, **@ToolParam** 어노테이션
- LLM이 자바 메서드 호출을 요청하는 과정
- Tool 전달 방법 비교: 빌더의 `defaultTools` vs 요청마다 `.tools(...)`
- 이 프로젝트가 요청마다 `.tools(...)`를 쓰는 이유

---

### Step 4: ChatClient Advisor와 Tool 실행 과정 이해
**[step-4-tool-callback.md](./step-4-tool-callback.md)**

- ChatClient Advisor 개념과 `SimpleLoggerAdvisor`
- 사용자 정의 Advisor로 실행 시간 측정
- 로그 대신 데이터로 남기기: `ToolResultCapture`
- 캐싱, 재시도, 에러 처리 Advisor 패턴

---

### Step 5: 다중 Function Calling과 MCP Tool 패턴
**[step-5-mcp-tool-pattern.md](./step-5-mcp-tool-pattern.md)**

- Tool 카테고리 분류와 에이전트 위임 패턴
- `ToolResultCapture`: `capture(toolName, arguments, () -> 작업)`(작업 실행·시간 측정·실패 기록)과 `getAndClear()`
- Tool 등록 패턴과 Description 최적화
- `McpController`로 Tool 직접 호출

---

### Step 6: 항공편 검색 Tool 구현
**[step-6-flight-search-tool.md](./step-6-flight-search-tool.md)**

- RestClient(Spring Boot가 만든 `RestClient.Builder`)로 공공데이터 포털 API 호출, 실패는 `ExternalApiException`으로 알리기
- 날짜 변환, 공항 코드 변환, 그룹핑, 시간·가격 필터 전문 에이전트
- `FlightSearchTool`의 세 가지 `@Tool` 메서드 (항공사별 최대 3편으로 결과 제한)

---

### Step 7: 공항/항공사 정보 Tool 구현
**[step-7-airport-info-tool.md](./step-7-airport-info-tool.md)**

- 공항 목록·공항 코드 조회 Tool
- 항공사 목록·항공사 ID 조회 Tool
- 여러 Tool을 한 요청에 전달해 LLM이 고르게 하기

---

### Step 8: 전문 에이전트 설계와 구현
**[step-8-specialized-agents.md](./step-8-specialized-agents.md)**

- 한 가지 일만 하는 결정적 컴포넌트로서의 전문 에이전트
- 단일 책임과 단위 테스트
- 세 가지 패턴이 전문 에이전트를 재사용하는 방식

---

### Step 9: Coordinator 패턴 — 코드가 제어하는 워크플로
**[step-9-coordinator-pattern.md](./step-9-coordinator-pattern.md)**

- `FlightSearchCoordinator`의 7단계 순차 워크플로
- `SearchCriteriaExtractor`: 정규식 우선, 실패 시 LLM
- `ExecutionTrace`로 단계별 실행 기록 남기기

---

### Step 10: Tool Calling 에이전트 — LLM이 제어하는 검색
**[step-10-tool-calling-agent.md](./step-10-tool-calling-agent.md)**

- `ToolCallingSearchService`: LLM이 Tool을 고르고 Spring AI가 실행
- `ToolResultCapture`로 Tool 호출을 실행 기록에 남기기
- Coordinator 패턴과의 비교

---

### Step 11: Orchestrator-Workers 패턴 — LLM이 작업을 나누고 종합하기
**[step-11-orchestrator-workers.md](./step-11-orchestrator-workers.md)**

- `FlightSearchPlanner`: LLM이 요청을 검색 작업으로 나누기 (`.entity(SearchPlan.class)`)
- `FlightSearchWorker`: 가상 스레드로 병렬 실행
- `ResultSynthesizer`: 작업별 요약을 LLM이 종합

---

## 학습 목표

### 기본 기능
- **ChatClient와 ChatModel**: LLM과 대화하는 인터페이스
- **Function Calling**: LLM이 자바 메서드 호출을 요청하고 Spring AI가 실행
- **Structured Output**: LLM 응답을 자바 record로 받기
- **MCP Tool**: `@Tool`로 LLM이 사용할 수 있는 Tool 구현
- **RestClient**: Spring 6.1부터 제공하는 HTTP 클라이언트

### 에이전트 패턴
- **Coordinator**: 코드가 정한 순서로 전문 에이전트 호출
- **Tool Calling 에이전트**: LLM이 Tool 선택과 호출 순서를 결정
- **Orchestrator-Workers**: LLM이 작업을 나누고, Worker가 병렬 처리하고, LLM이 종합
- **비교의 축**: 다음 단계를 누가 결정하는가(코드 vs LLM), 하위 작업이 미리 정해져 있는가

---

## 시나리오 예시

### 예시 1: Coordinator 패턴으로 기본 검색

**요청**

```bash
curl -X POST http://localhost:8080/api/search/coordinator \
  -H "Content-Type: application/json" \
  -d '{"message": "내일 광주에서 제주로 가는 항공편 알려줘", "model": "ollama"}'
```

**처리 과정** (공항 이름이 두 개이므로 정규식으로 처리되어 LLM을 부르지 않습니다)

```text
[SearchCriteriaExtractor]  정규식으로 검색 조건 추출 → 광주 → 제주 · 2026-03-25
    ↓
[DateParserAgent]          2026-03-25 → 20260325
    ↓
[AirportCodeAgent]         광주 → NAARKJJ, 제주 → NAARKPC
    ↓
[ApiClientService]         공공데이터 포털 API 호출
    ↓
[GroupingAgent]            항공사별 그룹핑
```

**응답** (`SearchResponse`, 일부 생략)

```json
{
  "pattern": "coordinator",
  "success": true,
  "message": "광주 → 제주 · 2026-03-25: 9편, 최저가 진에어 LJ0571 07:10 출발 45,900원",
  "results": [
    {
      "label": "광주 → 제주 · 2026-03-25",
      "criteria": { "departure": "광주", "arrival": "제주", "date": "2026-03-25",
                    "afterTime": null, "beforeTime": null, "minPrice": null, "maxPrice": null },
      "flightsByAirline": {
        "아시아나항공": [
          { "flightId": "OZ8141", "airline": "아시아나항공", "departureAirport": "광주",
            "arrivalAirport": "제주", "departureTime": "09:55", "arrivalTime": "11:00", "price": 57900 }
        ],
        "진에어": [ "..." ]
      },
      "totalCount": 9,
      "cheapest": { "flightId": "LJ0571", "airline": "진에어", "departureTime": "07:10", "price": 45900, "...": "..." }
    }
  ],
  "trace": [
    { "order": 1, "kind": "CODE", "actor": "SearchCriteriaExtractor", "title": "정규식으로 검색 조건 추출",
      "detail": "광주 → 제주 · 2026-03-25", "startMs": 0, "durationMs": 1, "thread": "http-nio-8080-exec-1" },
    { "order": 2, "kind": "CODE", "actor": "DateParserAgent", "title": "날짜 변환",
      "detail": "2026-03-25 → 20260325", "startMs": 1, "durationMs": 0, "thread": "http-nio-8080-exec-1" },
    { "order": 5, "kind": "CODE", "actor": "ApiClientService", "title": "공공데이터 포털 API 호출",
      "detail": "9편 조회", "startMs": 2, "durationMs": 412, "thread": "http-nio-8080-exec-1" },
    { "order": 6, "kind": "CODE", "actor": "GroupingAgent", "title": "항공사별 그룹핑",
      "detail": "4개 항공사", "startMs": 414, "durationMs": 1, "thread": "http-nio-8080-exec-1" }
  ],
  "elapsedMs": 416
}
```

실행 기록의 모든 단계가 `CODE`입니다. 다음 단계를 코드가 정하는 Coordinator 패턴의 특징이 그대로 드러납니다. (항공편 번호와 가격은 예시이며, 실행하는 날의 운항 정보에 따라 달라집니다.)

### 예시 2: Orchestrator-Workers 패턴으로 여러 조건 비교

```text
이번 주 금요일이나 토요일에 김포나 광주에서 제주 가는 제일 싼 항공편 찾아줘
```

날짜 2개 × 출발 공항 2개라는 조합을 미리 정해 둘 수 없으므로, LLM(`FlightSearchPlanner`)이 이 문장을 "김포 → 제주 · 금요일", "광주 → 제주 · 토요일"처럼 최대 4개의 검색 작업으로 나눕니다. Worker가 작업마다 가상 스레드에서 병렬로 검색하고, `ResultSynthesizer`가 작업별 요약을 LLM에게 넘겨 가장 저렴한 조합을 추천합니다. 실행 기록에는 `LLM`(계획) → `WORKER`×N(서로 다른 `virtual-*` 스레드) → `LLM`(종합) 순서로 나타납니다. 자세한 내용은 [Step 11](./step-11-orchestrator-workers.md)에서 다룹니다.

---

## API 정보

### 이 프로젝트의 API

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `POST` | `/api/search/coordinator` | Coordinator 패턴 검색 |
| `POST` | `/api/search/tool-calling` | Tool Calling 에이전트 검색 |
| `POST` | `/api/search/orchestrator` | Orchestrator-Workers 패턴 검색 |
| `GET` | `/api/mcp/**` | Tool을 LLM 없이 직접 호출 (`McpController`) |
| `GET` | `/` | 세 패턴 비교와 실행 기록 화면 |

요청 예시는 [api-test](./api-test/README.md) 디렉터리의 `.http` 파일을 참고하세요. 세 패턴을 한 번에 호출하는 예시는 `04-패턴-비교.http`에 있습니다.

### 공공데이터 포털 API

| 항목 | 내용 |
|------|------|
| 제공 기관 | 국토교통부 |
| API 유형 | REST |
| 기본 URL | `https://apis.data.go.kr/1613000/DmstcFlightNvgInfo` |
| 데이터 형식 | JSON, XML |
| 비용 | 무료 |

---

## 기술 스택

### 핵심 기술
- **Java 21** (가상 스레드)
- **Spring Boot 3.5.11**, **Spring Web MVC**
- **RestClient** (Spring 6.1부터 제공하는 HTTP 클라이언트)
- **Lombok**, **Maven**

### AI/LLM
- **Spring AI 1.1.4** - ChatClient, Tool Calling, Structured Output, Advisor
- **Ollama** - `qwen2.5:3b-instruct-q4_K_M`
- **Google Gemini** - `gemini-2.5-flash`

### 화면
- 단일 페이지 `templates/index.html` (일반 HTML/CSS/JavaScript, Fetch API, 외부 UI 프레임워크 없음)

### 테스트
- JUnit 5, 테스트 197개 중 196개 실행·1개(`contextLoads`) 비활성 (`./mvnw test`). LLM과 공공데이터 포털 호출을 가짜 구현으로 바꿔 끼워 세 패턴의 흐름을 검증

---

## 사전 요구사항

1. **Java 21** 설치
2. **IntelliJ IDEA** (또는 Eclipse)
3. **Maven** 빌드 도구 (프로젝트에 포함된 `./mvnw` 사용 가능)
4. **공공데이터 포털 서비스 키** (필수, 무료 발급) → 환경 변수 `DATA_GO_KR_SERVICE_KEY`
5. **Google Gemini API 키** (선택) → 환경 변수 `GOOGLE_GENAI_API_KEY`. 없으면 Ollama만 사용

실행 방법은 [프로젝트 README](../README.md#시작하기)를 참고하세요.

---

## 관련 링크

### 공식 문서
- [공공데이터 포털](https://www.data.go.kr/)
- [국내항공운항정보 API](https://www.data.go.kr/data/15098526/openapi.do)
- [Spring Boot 공식 문서](https://spring.io/projects/spring-boot)
- [Spring AI 공식 문서](https://docs.spring.io/spring-ai/reference/)
- [Anthropic, Building effective agents](https://www.anthropic.com/engineering/building-effective-agents)
- [Model Context Protocol](https://modelcontextprotocol.io/)

### 커뮤니티 자료
- [Awesome Spring AI](https://github.com/spring-ai-community/awesome-spring-ai)

---

## 라이선스

이 프로젝트는 학습 목적으로 제작되었습니다.
