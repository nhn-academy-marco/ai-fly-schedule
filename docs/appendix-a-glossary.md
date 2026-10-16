# 부록 A 용어집

이 책에서 쓴 용어를 한 줄로 정리했습니다. 영문 용어는 알파벳순, 한글 용어는 가나다순입니다. "처음 나오는 장"은 그 용어를 처음 자세히 설명하는 장입니다. 코드 이름(클래스·메서드)은 이 프로젝트에서의 역할로 설명합니다.

## 영문 용어

| 용어 | 뜻 | 처음 나오는 장 |
|---|---|---|
| 202 Accepted | 요청을 받았지만 처리가 아직 끝나지 않았다는 HTTP 상태 코드. 작업 API의 시작 응답 | [10장](./10-choosing-testing-operating.md) |
| 502 Bad Gateway | 프록시가 뒤쪽 서버에 연결하지 못했을 때의 오류. 이 프로젝트에서는 nginx와 앱의 포트가 달라서 났음 | [10장](./10-choosing-testing-operating.md) |
| 504 Gateway Time-out | 프록시가 정해진 시간 안에 뒤쪽 서버의 응답을 받지 못했을 때의 오류. nginx 기본 60초 | [10장](./10-choosing-testing-operating.md) |
| A2A (Agent2Agent) 프로토콜 | Google이 2025년 발표한 Agent 간 통신 프로토콜(현재 Linux Foundation). **이 프로젝트에는 구현되어 있지 않음** | [10장](./10-choosing-testing-operating.md) |
| Advisor | ChatClient 요청과 응답 사이에 끼어들어 로그·변환 같은 공통 처리를 하는 Spring AI 부품 | [3장](./03-advisor.md) |
| Agent | LLM이 다음 단계(어떤 Tool을 언제 부를지)를 스스로 정하는 구조. 이 책에서는 영어로 씀 | [들어가며](./00-introduction.md) |
| Awaitility | 비동기 코드의 결과를 "조건이 참이 될 때까지, 최대 몇 초" 기다리는 테스트 라이브러리 | [10장](./10-choosing-testing-operating.md) |
| BOM (Bill of Materials) | 관련 라이브러리 버전을 한곳에서 맞추는 Maven 설정. `spring-ai-bom` | [1장](./01-llm-and-spring-ai.md) |
| `CallAdvisor` | 동기 호출(`call()`)에 끼어드는 Advisor 인터페이스. `getOrder()` 값으로 순서가 정해짐 | [3장](./03-advisor.md) |
| ChatClient | Spring AI에서 LLM을 부르는 기본 API. `prompt().system().user().call()` 형태의 체인 | [2장](./02-chatclient.md) |
| `ChatClientProvider` | 요청의 모델 이름(`ollama`, `gemini`)으로 ChatClient를 골라 주고, LLM 오류를 사용자용 문장으로 바꾸는 컴포넌트 | [2장](./02-chatclient.md) |
| `content()` | LLM 응답을 문자열로 받는 메서드. Tool Calling에서는 이 호출 안에서 Tool이 실행됨 | [2장](./02-chatclient.md) |
| Coordinator 패턴 | 코드가 처리 순서를 정하고 전문 Agent를 차례로 호출하는 구조. 이 책의 workflow 예 | [7장](./07-coordinator.md) |
| `defaultTools` | ChatClient를 만들 때 모든 요청에 붙는 Tool. 이 프로젝트는 쓰지 않고 요청마다 `.tools(...)`로 넘김 | [4장](./04-tool-calling.md) |
| `entity()` | LLM 응답 JSON을 자바 객체로 바로 받는 메서드. Structured Output의 입구 | [2장](./02-chatclient.md) |
| Evaluator-Optimizer 패턴 | 한쪽이 답을 만들고 다른 쪽(LLM 또는 코드)이 평가해 고쳐 쓰게 하는 반복 구조. 이 프로젝트에는 없음 | [10장](./10-choosing-testing-operating.md) |
| `ExecutionTrace` | 한 요청의 실행 단계를 시간·스레드와 함께 쌓는 기록. 화면 타임라인의 원본 | [7장](./07-coordinator.md) |
| `ExternalApiException` | 공공데이터 포털 호출 실패(키 없음, HTTP 오류, 오류 코드)를 "항공편 없음"과 구분해 알리는 예외 | [5장](./05-domain-tools.md) |
| Function Calling | Tool Calling의 다른 이름. 모델 제공사에 따라 이렇게 부름 | [4장](./04-tool-calling.md) |
| Gemini | Google의 LLM. 이 프로젝트는 `gemini-2.5-flash`를 쓰며 키(`GOOGLE_GENAI_API_KEY`)는 선택 | [1장](./01-llm-and-spring-ai.md) |
| GitHub Actions | 저장소 이벤트(push 등)에 맞춰 빌드·배포를 실행하는 GitHub 기능. `.github/workflows/maven.yml` | [10장](./10-choosing-testing-operating.md) |
| GitHub Secrets | workflow에서만 읽을 수 있게 저장하는 비밀 값. 서버 접속 정보와 서비스 키를 둠 | [10장](./10-choosing-testing-operating.md) |
| grounding (근거 제한) | "Tool 결과에 없는 내용은 지어내지 마라"처럼 답의 근거를 주어진 자료로 묶는 프롬프트 규칙 | [2장](./02-chatclient.md) |
| `@JsonPropertyDescription` | Structured Output에서 각 필드의 뜻과 형식을 LLM에게 알려 주는 Jackson 어노테이션 | [2장](./02-chatclient.md) |
| LLM (Large Language Model) | 대량의 글로 학습해 다음 토큰을 예측하며 문장을 만드는 모델 | [1장](./01-llm-and-spring-ai.md) |
| LLM fallback | 정규식으로 검색 조건을 뽑지 못할 때만 LLM을 부르는 방식. `SearchCriteriaExtractor` | [7장](./07-coordinator.md) |
| `MAX_ROWS` | 공공데이터 포털에 한 번에 요청하는 최대 건수(500). 하루 100편이 넘는 노선이 잘리지 않게 함 | [5장](./05-domain-tools.md) |
| MCP (Model Context Protocol) | Agent와 Tool·데이터를 잇는 표준 프로토콜 | [5장](./05-domain-tools.md) |
| MCP Tool | 이 프로젝트 `mcp` 패키지의 Tool. MCP 서버가 아니라 `@Tool`로 만든 Tool이며 MCP 서버로 옮기기 쉬운 형태라는 뜻 | [5장](./05-domain-tools.md) |
| `McpController` | LLM 없이 Tool을 HTTP로 직접 호출해 보는 컨트롤러(`/api/mcp/**`) | [5장](./05-domain-tools.md) |
| Mockito | 테스트에서 가짜 객체(mock)를 만드는 라이브러리. 가짜 LLM을 만들 때 씀 | [6장](./06-specialized-agents.md) |
| `MockRestServiceServer` | `RestClient`의 HTTP 요청을 가로채 정해 둔 응답을 돌려주는 Spring 테스트 도구 | [5장](./05-domain-tools.md) |
| Multi-Agent | 여러 Agent(또는 Agent와 Worker)가 일을 나눠 맡는 구조의 통칭 | [9장](./09-orchestrator-workers.md) |
| nginx | 데모 서버 앞단의 웹 서버이자 프록시. `fly.java21.net` 요청을 앱 포트로 넘김 | [10장](./10-choosing-testing-operating.md) |
| Ollama | 내 컴퓨터나 서버에서 오픈 모델을 실행하는 도구. 이 프로젝트는 `qwen2.5:3b-instruct-q4_K_M` | [1장](./01-llm-and-spring-ai.md) |
| Orchestration | 여러 Agent·Worker의 실행 순서와 역할을 조율하는 일. 이 책에서는 영어로 씀 | [9장](./09-orchestrator-workers.md) |
| Orchestrator | Orchestrator-Workers 패턴에서 요청을 나누고 결과를 모으는 쪽. `FlightSearchOrchestrator` | [9장](./09-orchestrator-workers.md) |
| Orchestrator-Workers 패턴 | LLM이 요청을 하위 작업으로 나누고, Worker(코드)가 실행하고, LLM이 종합하는 구조 | [9장](./09-orchestrator-workers.md) |
| Planner | Orchestrator의 계획 단계. `FlightSearchPlanner`가 LLM으로 `SearchPlan`을 만들고 `validate()`로 검증 | [9장](./09-orchestrator-workers.md) |
| `proxy_read_timeout` | nginx가 뒤쪽 서버의 응답을 기다리는 최대 시간. 기본 60초, 이 책은 300초를 권장 | [10장](./10-choosing-testing-operating.md) |
| RestClient | Spring의 동기 HTTP 클라이언트. 공공데이터 포털 호출에 씀 | [5장](./05-domain-tools.md) |
| `RETURNS_DEEP_STUBS` | 메서드 체인의 중간 객체를 자동으로 만드는 Mockito 설정. ChatClient 체인을 한 줄로 흉내 냄 | [7장](./07-coordinator.md) |
| `RETURNS_SELF` | 빌더 메서드가 자기 자신을 돌려주게 하는 Mockito 설정. `ChatClientRequestSpec` 흉내에 씀 | [8장](./08-tool-calling-agent.md) |
| `SearchCriteria` | 출발·도착·날짜·시간·가격 조건을 담는 record. Structured Output의 대상 | [2장](./02-chatclient.md) |
| `SearchResponse` | 세 패턴이 공통으로 돌려주는 응답(결과 + 실행 기록 + 걸린 시간) | [7장](./07-coordinator.md) |
| `SimpleLoggerAdvisor` | LLM 요청과 응답을 로그로 남기는 Spring AI 기본 Advisor | [3장](./03-advisor.md) |
| Spring AI | Spring에서 여러 LLM을 같은 API로 다루게 해 주는 프레임워크. 이 책은 1.1.4 | [1장](./01-llm-and-spring-ai.md) |
| `startup.sh` | 서버에서 앱을 다시 시작하는 배포 스크립트(`deploy/startup.sh`). 포트 감지, Java 21 선택, 기동 확인 | [10장](./10-choosing-testing-operating.md) |
| Structured Output | LLM이 정해진 JSON 형식으로 답하게 하고 자바 객체로 받는 기능 | [2장](./02-chatclient.md) |
| Synthesizer | Orchestrator의 종합 단계. `ResultSynthesizer`가 작업별 요약을 LLM에 넘겨 추천을 만들고, 실패하면 코드 요약을 씀 | [9장](./09-orchestrator-workers.md) |
| system 프롬프트 | LLM에게 역할과 규칙을 주는 메시지. 사용자 메시지(user)와 구분됨 | [2장](./02-chatclient.md) |
| temperature | LLM 출력의 무작위성을 정하는 값. 낮을수록 같은 입력에 비슷한 답 | [1장](./01-llm-and-spring-ai.md) |
| Tool | LLM이 호출을 요청할 수 있는 자바 메서드. 실행은 Spring AI가 함 | [4장](./04-tool-calling.md) |
| `@Tool` / `@ToolParam` | 메서드와 파라미터를 Tool로 등록하고 LLM이 읽을 설명을 붙이는 Spring AI 어노테이션 | [4장](./04-tool-calling.md) |
| Tool Calling | LLM이 "이 Tool을 이 인자로 불러 달라"고 요청하고 애플리케이션이 실행해 결과를 돌려주는 방식 | [4장](./04-tool-calling.md) |
| Tool Calling Agent | LLM 하나가 Tool 목록을 받아 어떤 Tool을 언제 부를지 정하는 구조. `ToolCallingSearchService` | [8장](./08-tool-calling-agent.md) |
| `ToolResultCapture` | Tool 호출 이름·인자·결과·시간을 `ThreadLocal`에 모으고, `bind()`로 연결한 실행 기록에 Tool 실행 즉시 TOOL 단계를 남기는 도구 | [5장](./05-domain-tools.md) |
| Trace (실행 기록) | 한 요청이 거친 단계를 CODE·LLM·TOOL·WORKER로 구분해 남긴 기록 | [들어가며](./00-introduction.md) |
| `TraceStep` | 실행 기록 한 줄. 종류(kind), 실행 주체(actor), 시작 시각, 걸린 시간, 스레드 이름을 가짐 | [7장](./07-coordinator.md) |
| Worker | Orchestrator가 맡긴 하위 작업 하나를 실행하는 쪽. `FlightSearchWorker`는 Coordinator의 검색 단계를 재사용 | [9장](./09-orchestrator-workers.md) |
| workflow | 코드가 실행 경로를 정하는 구조(Anthropic의 구분). agent와 대비됨 | [7장](./07-coordinator.md) |

## 한글 용어

| 용어 | 뜻 | 처음 나오는 장 |
|---|---|---|
| 가상 스레드 | Java 21의 가벼운 스레드. 기다리는 시간이 긴 작업을 많이 동시에 돌릴 때 유리. Worker와 작업 API가 씀 | [9장](./09-orchestrator-workers.md) |
| 가짜 LLM | 테스트에서 ChatClient를 mock으로 바꿔, 정해 둔 응답을 돌려주거나 Tool을 직접 실행하게 한 것 | [10장](./10-choosing-testing-operating.md) |
| 결정성 | 같은 입력에 항상 같은 결과가 나오는 성질. 코드는 결정적, LLM은 비결정적 | [1장](./01-llm-and-spring-ai.md) |
| 공공데이터 포털 | 정부 기관의 공개 API를 제공하는 사이트. 이 프로젝트는 국내항공운항정보 API를 씀 | [들어가며](./00-introduction.md) |
| 공항 코드 | 공공데이터 포털이 쓰는 공항 ID(예: 김포 `NAARKSS`, 제주 `NAARKPC`). `AirportCodeAgent`가 이름을 코드로 바꿈 | [5장](./05-domain-tools.md) |
| 단일 책임 원칙 (SRP) | 클래스 하나가 한 가지 이유로만 바뀌게 하는 설계 원칙. 전문 Agent를 나누는 기준 | [6장](./06-specialized-agents.md) |
| 비결정성 | 같은 입력에도 LLM 출력이 달라질 수 있는 성질 | [1장](./01-llm-and-spring-ai.md) |
| 서비스 키 | 공공데이터 포털 API를 부를 때 필요한 인증 키. 환경 변수 `DATA_GO_KR_SERVICE_KEY`(필수) | [들어가며](./00-introduction.md) |
| 작업(job) API | 검색을 백그라운드에서 시작하고(`POST /api/search/{pattern}/jobs`) 상태를 따로 조회하는(`GET /api/search/jobs/{jobId}`) API | [10장](./10-choosing-testing-operating.md) |
| 재시도 (retry) | LLM 호출 실패 시 다시 시도하는 Spring AI 설정(`spring.ai.retry`). 이 프로젝트는 최대 2회로 줄임 | [1장](./01-llm-and-spring-ai.md) |
| 전문 Agent | LLM 없이 한 가지 일만 하는 결정적 컴포넌트(`service/agent`). 예: `DateParserAgent`, `AirportCodeAgent` | [6장](./06-specialized-agents.md) |
| 정규식 우선 | 검색 조건을 먼저 정규식으로 뽑고 실패할 때만 LLM을 부르는 전략. LLM 호출 0회 경로를 만듦 | [7장](./07-coordinator.md) |
| 컨텍스트 (컨텍스트 창) | LLM이 한 번에 읽을 수 있는 토큰의 범위. Ollama는 `num-ctx`로 정함 | [1장](./01-llm-and-spring-ai.md) |
| 토큰 | LLM이 글을 나눠 처리하는 단위. 비용과 컨텍스트 길이의 기준 | [1장](./01-llm-and-spring-ai.md) |
| 프롬프트 | LLM에게 보내는 입력 전체(system 메시지 + user 메시지) | [1장](./01-llm-and-spring-ai.md) |
| 환각 (hallucination) | LLM이 근거 없는 내용을 사실처럼 만들어 내는 현상. grounding 규칙과 검증으로 줄임 | [2장](./02-chatclient.md) |
