# Spring AI Agent 패턴 실습: 항공편 검색

Spring AI 1.1.4로 LLM 애플리케이션의 대표적인 세 가지 구조를 직접 만들어 보는 교육용 프로젝트입니다.
"이번 주 금요일이나 토요일에 김포나 광주에서 제주 가는 제일 싼 항공편 찾아줘" 같은 문장을 **Coordinator 패턴**, **Tool Calling Agent**, **Orchestrator-Workers 패턴**으로 풀고, 실행 기록(trace) 타임라인에서 **다음 단계를 누가 결정하는지(코드인가, LLM인가)** 나란히 비교합니다.

- 데모: **[https://fly.java21.net](https://fly.java21.net)**
- 저장소: [https://github.com/nhn-academy-marco/ai-fly-schedule](https://github.com/nhn-academy-marco/ai-fly-schedule)
- 교재: [『Spring AI로 배우는 Agent 설계 — 항공편 검색으로 비교하는 세 가지 패턴』](./docs/README.md)

## 무엇을 볼 수 있나

- 한 문장을 입력하고 "세 패턴 모두 실행"으로 결과를 비교합니다.
- 단계마다 CODE, LLM, TOOL, WORKER로 구분한 실행 기록 타임라인이 실시간으로 쌓입니다.
- Orchestrator-Workers 패턴에서는 Worker가 가상 스레드로 병렬 실행되는 모습을 볼 수 있습니다.
- LLM은 Ollama(`qwen2.5:3b-instruct-q4_K_M`)와 Gemini(`gemini-2.5-flash`) 중에서 고릅니다.

| 패턴 | 다음 단계를 누가 정하나 | 한 요청의 LLM 호출 | 엔드포인트 | 교재 |
|---|---|---|---|---|
| Coordinator 패턴 | 코드 (순서 고정) | 0회(정규식 경로) 또는 1회(LLM fallback) | `POST /api/search/coordinator` | [7장](./docs/07-coordinator.md) |
| Tool Calling Agent | LLM (어떤 Tool을 언제 부를지) | `call()` 최소 1회, 내부 모델 왕복은 2회 이상 | `POST /api/search/tool-calling` | [8장](./docs/08-tool-calling-agent.md) |
| Orchestrator-Workers 패턴 | LLM이 나누고 종합, 실행은 코드(Worker) | 계획 1회 + 종합 1회 | `POST /api/search/orchestrator` | [9장](./docs/09-orchestrator-workers.md) |

세 엔드포인트는 모두 `{"message": "...", "model": "ollama" | "gemini"}`를 받고 같은 형식의 `SearchResponse`(결과 + 실행 기록)를 돌려줍니다. 오래 걸리는 검색은 작업 API로 실행합니다. 화면도 이 방식을 씁니다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| `POST` | `/api/search/{pattern}/jobs` | 검색을 백그라운드에서 시작하고 `202`와 `jobId`를 바로 반환 |
| `GET` | `/api/search/jobs/{jobId}` | 진행 상황(지금까지의 실행 기록)과 결과 조회. 화면은 1.5초 간격으로 조회 |
| `GET` | `/api/mcp/**` | LLM 없이 Tool을 직접 호출 |

> **주의**
> - 이 프로젝트는 A2A(Agent2Agent) 프로토콜을 **구현하지 않습니다**. A2A는 [10장](./docs/10-choosing-testing-operating.md)에서 개념으로만 소개합니다.
> - `mcp` 패키지의 "MCP Tool"은 MCP 서버가 아니라 Spring AI `@Tool`로 만든 Tool입니다. MCP 서버로 노출하기 쉬운 형태라는 뜻입니다.
> - `service/agent` 패키지의 전문 Agent는 LLM을 쓰지 않는, 한 가지 일만 하는 결정적 컴포넌트입니다.

## 빠르게 시작하기

### 1. 준비물

- Java 21
- 공공데이터 포털 서비스 키(필수): [국내항공운항정보](https://www.data.go.kr/data/15098526/openapi.do) 활용 신청 후 발급
- Google Gemini API 키(선택): [Google AI Studio](https://aistudio.google.com/app/apikey)
- Ollama(선택): 내 컴퓨터에서 모델을 실행할 때만 필요

Maven은 저장소에 들어 있는 `./mvnw`를 쓰면 따로 설치하지 않아도 됩니다.

### 2. 환경 변수

| 환경 변수 | 필수 여부 | 설명 |
|---|---|---|
| `DATA_GO_KR_SERVICE_KEY` | 필수 | 공공데이터 포털 서비스 키(항공편·공항·항공사 조회) |
| `GOOGLE_GENAI_API_KEY` | 선택 | Gemini API 키. 없어도 앱은 정상으로 시작하며, 화면에서 Gemini를 고르면 키를 설정하라는 안내가 나옵니다 |

```bash
export DATA_GO_KR_SERVICE_KEY="발급받은_공공데이터_포털_서비스_키"
export GOOGLE_GENAI_API_KEY="발급받은_Gemini_API_키"   # 선택
```

### 3. Ollama 주소

`src/main/resources/application.yml`의 `spring.ai.ollama.base-url`에서 정합니다. 기본값은 실습용 원격 서버 `http://ollama.java21.net`입니다. 내 컴퓨터에서 실행하려면 `http://localhost:11434`로 바꾸고 모델을 내려받습니다.

```bash
ollama pull qwen2.5:3b-instruct-q4_K_M
```

### 4. 실행과 테스트

```bash
./mvnw spring-boot:run   # http://localhost:8080
./mvnw test              # 키와 LLM 없이 실행 (가짜 LLM, MockRestServiceServer)
```

테스트는 LLM과 공공데이터 포털을 가짜로 바꿔 끼우므로 키 없이도 세 패턴의 흐름을 검증합니다. 애플리케이션 전체를 띄우는 테스트 하나만 `@Disabled`로 꺼 두었습니다. 테스트 수는 실행 결과의 `Tests run` 줄로 확인하세요. IntelliJ IDEA HTTP Client용 요청 파일은 [API 테스트 가이드](./docs/api-test/README.md)에 있습니다.

## 교재

[docs/README.md](./docs/README.md)에 책 소개, 4주 수업 일정, 강사용 진행 팁이 있습니다.

| 장 | 제목 |
|---|---|
| [들어가며](./docs/00-introduction.md) | 책의 질문, 완성 앱 둘러보기, 개발 환경 준비 |
| [1장](./docs/01-llm-and-spring-ai.md) | LLM과 Spring AI 시작하기 |
| [2장](./docs/02-chatclient.md) | ChatClient로 LLM 부르기 |
| [3장](./docs/03-advisor.md) | Advisor로 LLM 호출 들여다보기 |
| [4장](./docs/04-tool-calling.md) | Tool Calling의 원리 |
| [5장](./docs/05-domain-tools.md) | 항공편 도메인 Tool 만들기 |
| [6장](./docs/06-specialized-agents.md) | 전문 Agent로 나누기 |
| [7장](./docs/07-coordinator.md) | Coordinator 패턴 — 코드가 결정한다 |
| [8장](./docs/08-tool-calling-agent.md) | Tool Calling Agent — LLM이 결정한다 |
| [9장](./docs/09-orchestrator-workers.md) | Orchestrator-Workers 패턴 — LLM이 나누고 코드가 실행한다 |
| [10장](./docs/10-choosing-testing-operating.md) | 패턴 고르기, 테스트, 운영 |
| [부록 A](./docs/appendix-a-glossary.md) | 용어집 |
| [부록 B](./docs/appendix-b-troubleshooting.md) | 문제 해결 모음 |

## 배포 요약

`main` 브랜치에 push하면 GitHub Actions([.github/workflows/maven.yml](./.github/workflows/maven.yml))가 서비스 키 확인 → JDK 21 빌드(`-DskipTests`) → `deploy/startup.sh`와 `target/fly.jar` 업로드 → 서버에서 `startup.sh` 실행까지 진행합니다. Pull request에서는 빌드만 합니다.

- GitHub Secrets: `SSH_IP`, `SSH_ID`, `SSH_PASSWORD`, `SSH_PORT`, `DATA_GO_KR_SERVICE_KEY`
- Gemini 키(선택): 서버의 `~/fly-schedule/.env`에 `GOOGLE_GENAI_API_KEY=...`
- `startup.sh`: 포트를 `SERVER_PORT` → `startup.sh.orig` → nginx `proxy_pass` → `10600` 순서로 정하고, Java 21과 `prod` 프로필로 기동한 뒤 최대 90초 동안 응답을 확인합니다.
- nginx는 `proxy_read_timeout 300s`를 권장합니다. 기본 60초에서는 LLM 호출이 긴 동기 API가 504로 끊깁니다.

빌드 단계는 테스트를 건너뛰므로 push 전에 로컬에서 `./mvnw test`를 실행하세요. 파이프라인 그림, 포트 감지 규칙, 실제로 겪은 502·504 사례는 [10장](./docs/10-choosing-testing-operating.md)에 자세히 정리했습니다.

## 기술 스택

| 구분 | 기술 |
|---|---|
| 언어·프레임워크 | Java 21, Spring Boot 3.5.11, Spring Web MVC, Lombok, Maven |
| AI | Spring AI 1.1.4 (ChatClient, Tool Calling, Structured Output, Advisor) |
| LLM | Ollama `qwen2.5:3b-instruct-q4_K_M`, Google Gemini `gemini-2.5-flash` |
| 외부 API | 공공데이터 포털 국내항공운항정보 (`RestClient`) |
| 동시성 | Java 21 가상 스레드 (Worker 병렬 실행, 작업 API) |
| 화면 | 단일 페이지 `templates/index.html` (HTML/CSS/JavaScript, Fetch API) |
| 테스트 | JUnit 5, Mockito, `MockRestServiceServer`, Awaitility |

## 라이선스

이 프로젝트는 학습 및 교육 목적으로 제작되었습니다.
