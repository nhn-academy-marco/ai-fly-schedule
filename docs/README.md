# Spring AI 에이전트 패턴 실습: 항공편 검색 — 수업 안내

Spring AI 1.1.4로 LLM 애플리케이션의 대표적인 세 가지 구조를 직접 만들어 보는 4주 과정 교재입니다.
공공데이터 포털의 국내 항공편 정보를 Tool로 연결한 뒤, 같은 자연어 질문을 Coordinator 패턴, Tool Calling 에이전트, Orchestrator-Workers 패턴으로 풀어 봅니다. 마지막에는 실행 기록(타임라인) 화면에서 "다음 단계를 누가 결정하는가(코드인가 LLM인가)"를 나란히 비교합니다.

---

## 이 과정에서 만들 것

화면(`/`)에 "이번 주 금요일이나 토요일에 김포나 광주에서 제주 가는 제일 싼 항공편 찾아줘" 같은 문장을 입력하고 "세 패턴 모두 실행"을 누르면, 세 패턴이 동시에 실행되어 결과와 실행 기록을 나란히 보여 주는 애플리케이션입니다.

- 실행 기록의 단계는 `CODE`(개발자가 순서 결정), `LLM`(판단·계획·요약), `TOOL`(LLM이 호출 요청), `WORKER`(Orchestrator가 맡긴 작업)로 구분됩니다.
- Orchestrator-Workers 패턴에서는 Worker가 서로 다른 가상 스레드(`virtual-*`)에서 병렬로 실행되는 모습을 확인할 수 있습니다.
- 완성본은 데모 사이트 [https://fly.java21.net](https://fly.java21.net)에서 미리 볼 수 있습니다.

---

## 대상과 선수 지식

### 대상

- Java와 Spring Boot로 간단한 REST API를 만들어 본 부트캠프 수강생
- LLM과 에이전트는 처음인 개발자

### 선수 지식

| 영역 | 필요한 수준 |
|------|-------------|
| Java | 클래스·인터페이스, 컬렉션, 람다와 `Supplier`·`Function` 같은 함수형 인터페이스, `record` |
| Spring Boot | `@RestController`, `@Service`, 생성자 주입, `application.yml` 설정 |
| HTTP | JSON 요청·응답, `curl` 또는 IntelliJ IDEA HTTP Client 사용 |
| 테스트 | JUnit 5로 단위 테스트를 작성하고 `./mvnw test`로 실행 |

LLM, 프롬프트, Function Calling, 에이전트 패턴은 교재에서 처음부터 설명합니다. 가상 스레드는 Step 11에서 필요한 만큼만 다룹니다.

### 준비물

1. Java 21
2. IntelliJ IDEA (HTTP Client로 `api-test/*.http`를 실행할 때 편리합니다)
3. 공공데이터 포털 서비스 키(필수, 무료) → 환경 변수 `DATA_GO_KR_SERVICE_KEY`
4. Google Gemini API 키(선택) → 환경 변수 `GOOGLE_GENAI_API_KEY`. 없으면 Ollama만 씁니다.

Maven은 프로젝트에 들어 있는 `./mvnw`를 쓰면 따로 설치하지 않아도 됩니다. 실행 방법은 [프로젝트 README](../README.md#시작하기)를 참고하세요.

---

## 핵심 용어

교재 전체에서 아래 용어를 같은 뜻으로 씁니다.

| 용어 | 뜻 | 이 프로젝트 |
|------|----|-------------|
| Tool (MCP Tool) | LLM이 호출을 요청할 수 있는 자바 메서드. 이 프로젝트의 "MCP Tool"은 MCP 서버가 아니라 Spring AI Tool Calling(`@Tool`)으로 만든 Tool이며, MCP 서버로 노출할 수 있는 형태로 설계했다는 뜻입니다 | `mcp/*` |
| 전문 에이전트 | 한 가지 일만 하는 결정적(deterministic) 클래스. 교재에서 "에이전트"라고 부르지만 LLM을 쓰지 않습니다 | `service/agent/*` |
| Coordinator 패턴 | 코드가 처리 순서를 정하고 전문 에이전트를 차례로 호출하는 워크플로(sequential workflow, prompt chaining) | `FlightSearchCoordinator` |
| Tool Calling 에이전트 | LLM 하나가 Tool 목록을 받아 어떤 Tool을 언제 부를지 스스로 결정하는 에이전트 | `ToolCallingSearchService` |
| Orchestrator-Workers 패턴 | LLM(Orchestrator)이 요청을 하위 작업으로 나누고, Worker가 처리하고, LLM이 종합하는 구조 | `FlightSearchOrchestrator` |
| A2A (Agent2Agent) 프로토콜 | 독립 실행되는 에이전트끼리 HTTP(JSON-RPC)로 통신하는 표준. MCP가 에이전트와 Tool을 잇는다면 A2A는 에이전트와 에이전트를 잇습니다 | 구현 없음. 개념과 비교로만 소개 |

---

## 패턴 한눈에 보기

| 패턴 | 다음 단계를 누가 정하나 | 하위 작업이 미리 정해져 있나 | 한 요청의 LLM 호출 | 엔드포인트 |
|------|------------------------|------------------------------|--------------------|------------|
| Coordinator | 코드 | 예 (7단계 고정) | 0~1회 (정규식으로 검색 조건을 못 뽑을 때만) | `POST /api/search/coordinator` |
| Tool Calling 에이전트 | LLM | 아니요 (LLM이 Tool 선택) | `call()` 한 번 안에서 여러 차례 왕복 | `POST /api/search/tool-calling` |
| Orchestrator-Workers | LLM(계획·종합) + 코드(실행) | 아니요 (LLM이 작업 수 결정, 최대 4개) | 2회 (계획 + 종합) | `POST /api/search/orchestrator` |

---

## 4주 일정표

| 주차 | Step | 주제 | 예상 학습 시간 |
|------|------|------|----------------|
| 1주차 | Step 1~2 | Spring AI 기초: 환경 설정, ChatClient | 7~9시간 |
| 2주차 | Step 3~5 | Function Calling, Advisor, MCP Tool 패턴 | 13~16시간 |
| 3주차 | Step 6~8 | 공공데이터 포털 연동 Tool, 전문 에이전트 | 13~17시간 |
| 4주차 | Step 9~11 | 세 가지 에이전트 패턴 구현과 비교 | 15~18시간 |

모든 Step에는 실제 코드와 연결된 실습 과제(기본/도전)가 있고, 과제마다 힌트와 완료 기준이 붙어 있습니다.

### Step별 학습 목표

| Step | 문서 | 이 Step을 마치면 |
|------|------|------------------|
| 1 | [Spring AI 환경 설정](./step-1-spring-ai-settings.md) | BOM으로 Spring AI 의존성을 관리하고, Ollama·Gemini 연결과 타임아웃·재시도를 `application.yml`에 설정할 수 있습니다. |
| 2 | [ChatClient 기초와 LLM 연동](./step-2-chatclient-basics.md) | 모델별 `ChatClient` 빈을 등록하고 `.content()`와 `.entity(SearchCriteria.class)`로 LLM 응답을 받을 수 있습니다. |
| 3 | [Function Calling 기초](./step-3-function-calling.md) | `@Tool`·`@ToolParam`으로 Tool을 정의하고, 요청마다 `.tools(...)`로 넘기는 이유를 설명할 수 있습니다. |
| 4 | [ChatClient Advisor와 Tool 실행 과정 이해](./step-4-tool-callback.md) | `SimpleLoggerAdvisor`와 사용자 정의 Advisor로 LLM 요청·응답과 Tool 실행 과정을 관찰할 수 있습니다. |
| 5 | [다중 Function Calling과 MCP Tool 패턴](./step-5-mcp-tool-pattern.md) | Tool이 전문 에이전트에게 일을 맡기게 하고, `ToolResultCapture`로 Tool 호출을 기록할 수 있습니다. |
| 6 | [항공편 검색 Tool 구현](./step-6-flight-search-tool.md) | `RestClient`로 공공데이터 포털 API를 호출하는 `FlightSearchTool`을 만들고 실패를 `ExternalApiException`으로 알릴 수 있습니다. |
| 7 | [공항/항공사 정보 Tool 구현](./step-7-airport-info-tool.md) | 공항·항공사 정보 Tool을 추가하고, 여러 Tool을 한 요청에 넘겨 LLM이 고르게 할 수 있습니다. |
| 8 | [전문 에이전트 설계와 구현](./step-8-specialized-agents.md) | LLM을 쓰지 않는 전문 에이전트를 단일 책임으로 설계하고 Spring 없이 단위 테스트할 수 있습니다. |
| 9 | [Coordinator 패턴](./step-9-coordinator-pattern.md) | 코드가 순서를 정하는 7단계 워크플로를 만들고 `ExecutionTrace`로 단계마다 실행 기록을 남길 수 있습니다. |
| 10 | [Tool Calling 에이전트](./step-10-tool-calling-agent.md) | LLM이 Tool을 골라 부르는 검색을 만들고, Tool 호출을 실행 기록의 `TOOL` 단계로 보여 줄 수 있습니다. |
| 11 | [Orchestrator-Workers 패턴](./step-11-orchestrator-workers.md) | LLM이 요청을 하위 작업으로 나누게 하고, Worker를 가상 스레드로 병렬 실행한 뒤 LLM이 결과를 종합하게 할 수 있습니다. |

---

## 수업 진행 팁 (강사용)

화면(`/`)은 4주 내내 쓰는 공통 교구입니다. 한 문장을 입력하고 "세 패턴 모두 실행"을 누르면 세 패턴이 동시에 실행되고, 체크박스로 일부 패턴만 고를 수 있습니다. 예시 버튼(기본, 시간, 가격, 복합)과 LLM 선택(Ollama, Gemini)도 화면에 있습니다. 주차마다 아래 시점에 화면으로 세 패턴을 비교 시연하면 "지금 만드는 부품이 어디에 쓰이는지"를 수강생이 계속 확인할 수 있습니다.

| 시점 | 시연 내용 | 짚어 줄 점 |
|------|-----------|------------|
| 1주차 첫 시간 (Step 1 시작 전) | 데모 사이트나 완성 코드에서 "복합" 예시로 세 패턴 모두 실행 | 4주 뒤 완성할 결과. 세 패턴의 답이 같아도 실행 기록의 색(`CODE`/`LLM`/`TOOL`/`WORKER`)이 다르다는 것만 보여 줍니다. |
| 1주차 끝 (Step 2 후) | "기본" 예시를 Ollama와 Gemini로 한 번씩 실행 | 같은 코드에서 모델만 바꿨을 때 응답 시간과 답변이 달라집니다. Gemini 키가 없으면 안내 문구가 나오는 것도 확인합니다. |
| 2주차 끝 (Step 5 후) | Tool Calling만 체크하고 "시간" 예시 실행 | `TOOL` 단계에서 LLM이 고른 Tool 이름과 인자. Step 3~5에서 만든 `@Tool`과 `ToolResultCapture`가 이 기록을 만듭니다. |
| 3주차 끝 (Step 8 후) | Coordinator만 체크하고 "기본"·"가격" 예시 실행 | 모든 단계가 `CODE`이고, 각 단계의 `actor`가 Step 8의 전문 에이전트입니다. 정규식으로 조건을 뽑으면 LLM을 한 번도 부르지 않습니다. |
| 4주차 Step 9~11 각 시작 | 해당 패턴만 체크하고 실행한 뒤 코드와 실행 기록을 한 줄씩 대응 | 그 Step에서 만들 코드가 타임라인의 어느 줄이 되는지 먼저 보여 주고 시작합니다. |
| 4주차 마지막 시간 | "기본"과 "복합" 예시로 세 패턴 모두 실행 | 기본 문장은 세 패턴 모두 잘 처리하지만, 복합 문장은 Orchestrator-Workers만 여러 작업으로 나눠 병렬 실행합니다. "다음 단계를 누가 결정하는가", "하위 작업이 미리 정해져 있는가"로 정리합니다. |

> **팁**
> 수업 중 공용 Ollama 서버가 느리거나 여러 명이 동시에 호출하면 응답이 수십 초 걸릴 수 있습니다. 시연은 강사 화면에서 한 번만 실행하고, 수강생 실습은 `./mvnw test`(가짜 LLM 사용)로 흐름을 먼저 확인하게 하면 대기 시간을 줄일 수 있습니다.

---

## 시나리오 예시

### 예시 1: Coordinator 패턴으로 기본 검색

요청은 다음과 같습니다.

```bash
curl -X POST http://localhost:8080/api/search/coordinator \
  -H "Content-Type: application/json" \
  -d '{"message": "내일 광주에서 제주로 가는 항공편 알려줘", "model": "ollama"}'
```

공항 이름이 두 개이므로 정규식으로 처리되어 LLM을 부르지 않습니다. 시간·가격 조건이 없으므로 `TimeFilterAgent`와 `PriceFilterAgent` 단계는 건너뜁니다.

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

응답(`SearchResponse`)은 다음과 같습니다(일부 생략).

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

생략한 3·4번 단계는 출발·도착 공항 코드 변환(`AirportCodeAgent`)입니다. 실행 기록의 모든 단계가 `CODE`이므로, 다음 단계를 코드가 정하는 Coordinator 패턴의 특징이 그대로 드러납니다. 항공편 번호와 가격은 예시이며, 실행하는 날의 운항 정보에 따라 달라집니다.

### 예시 2: Orchestrator-Workers 패턴으로 여러 조건 비교

```text
이번 주 금요일이나 토요일에 김포나 광주에서 제주 가는 제일 싼 항공편 찾아줘
```

날짜 2개와 출발 공항 2개의 조합을 코드에 미리 정해 둘 수 없으므로, LLM(`FlightSearchPlanner`)이 이 문장을 "김포 → 제주 · 금요일 날짜", "광주 → 제주 · 토요일 날짜"처럼 최대 4개의 검색 작업으로 나눕니다. Worker가 작업마다 가상 스레드에서 병렬로 검색하고, `ResultSynthesizer`가 작업별 요약을 LLM에게 넘겨 결과를 비교·추천하게 합니다. 실행 기록에는 `LLM`(계획) → `WORKER`×N(서로 다른 `virtual-*` 스레드) → `LLM`(종합) 순서로 나타납니다. 자세한 내용은 [Step 11](./step-11-orchestrator-workers.md)에서 다룹니다.

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

세 검색 엔드포인트는 모두 `{"message": "...", "model": "ollama" | "gemini"}`를 받고 같은 형식의 `SearchResponse`를 돌려줍니다. 요청 예시는 [API 테스트 가이드](./api-test/README.md)의 `.http` 파일을 참고하세요. 세 패턴을 차례로 호출하는 예시는 `04-패턴-비교.http`에 있습니다.

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

| 구분 | 기술 |
|------|------|
| 언어·프레임워크 | Java 21(가상 스레드), Spring Boot 3.5.11, Spring Web MVC, Lombok, Maven |
| AI | Spring AI 1.1.4 (ChatClient, Tool Calling, Structured Output, Advisor) |
| LLM | Ollama `qwen2.5:3b-instruct-q4_K_M`, Google Gemini `gemini-2.5-flash` |
| 외부 API | 공공데이터 포털 국내항공운항정보 (`RestClient`) |
| 화면 | 단일 페이지 `templates/index.html` (일반 HTML/CSS/JavaScript, Fetch API) |
| 테스트 | JUnit 5. LLM과 공공데이터 포털 호출을 가짜 구현(또는 `MockRestServiceServer`)으로 바꿔 끼워, 키 없이 세 패턴의 흐름을 검증합니다. `./mvnw test`로 실행하고 결과의 테스트 수를 확인하세요. |

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
