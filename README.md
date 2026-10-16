# Spring AI 에이전트 패턴 실습: 항공편 검색

Spring AI 1.1.4로 LLM 애플리케이션의 대표적인 세 가지 구조를 직접 만들어 보는 교육용 프로젝트입니다.
"이번 주 금요일이나 토요일에 김포나 광주에서 제주 가는 제일 싼 항공편 찾아줘" 같은 질문을 Coordinator 패턴, Tool Calling 에이전트, Orchestrator-Workers 패턴의 세 방식으로 풀고, 실행 기록(타임라인) 화면에서 **다음 단계를 누가 결정하는지(코드인가 LLM인가)** 나란히 비교합니다.

---

## 데모 사이트

**[https://fly.java21.net](https://fly.java21.net)**

- 한 문장을 입력하고 "세 패턴 모두 실행"으로 결과 비교
- 단계마다 CODE, LLM, TOOL, WORKER로 구분한 실행 기록 타임라인
- Orchestrator-Workers 패턴에서 Worker가 가상 스레드로 병렬 실행되는 모습 확인
- Ollama(qwen2.5:3b)와 Gemini(2.5 Flash) 중 모델 선택

---

## 패턴 한눈에 보기

| 패턴 | 다음 단계를 누가 정하나 | 한 요청의 LLM 호출 | 엔드포인트 | 교재 |
|------|------------------------|--------------------|------------|------|
| Coordinator | 코드 (순서가 고정된 워크플로) | 0~1회 (정규식으로 검색 조건을 못 뽑을 때만) | `POST /api/search/coordinator` | Step 9 |
| Tool Calling 에이전트 | LLM (어떤 Tool을 언제 부를지) | `call()` 한 번 안에서 여러 차례 왕복 (Tool을 부를 때마다 결과를 들고 다시 LLM에게) | `POST /api/search/tool-calling` | Step 10 |
| Orchestrator-Workers | LLM이 작업을 나누고 종합, 실행은 코드(Worker) | 2회 (계획 + 종합, 종합이 실패하면 코드로 요약) | `POST /api/search/orchestrator` | Step 11 |

세 엔드포인트 모두 요청 `{"message": "...", "model": "ollama" | "gemini"}`를 받고, 같은 형식의 `SearchResponse`(결과 + 실행 기록)를 돌려줍니다.

> **주의**
> 다음 용어를 혼동하지 않도록 합니다.
>
> - 이 프로젝트는 A2A(Agent2Agent) 프로토콜을 **구현하지 않습니다**. A2A는 독립 실행되는 에이전트끼리 HTTP(JSON-RPC)로 통신하는 표준이며, 교재에서는 개념과 비교로만 다룹니다.
> - 여기서 말하는 MCP Tool은 MCP 서버가 아니라 Spring AI Tool Calling(`@Tool`)으로 만든 Tool입니다. MCP 서버로 노출할 수 있는 형태로 설계했다는 뜻입니다.
> - `service/agent` 패키지의 전문 에이전트는 LLM을 쓰지 않는, 한 가지 일만 하는 결정적 컴포넌트입니다.

---

## 학습 경로 (4주 완성)

[docs](./docs) 디렉터리에 단계별 교재가 있습니다. 대상, 선수 지식, Step별 학습 목표, 강사용 수업 진행 팁은 [수업 안내](./docs/README.md)를 참고하세요.

| 주차 | Step | 주제 | 예상 학습 시간 |
|------|------|------|----------------|
| 1주차 | Step 1~2 | Spring AI 기초: 환경 설정, ChatClient | 7~9시간 |
| 2주차 | Step 3~5 | Function Calling, Advisor, MCP Tool 패턴 | 13~16시간 |
| 3주차 | Step 6~8 | 공공데이터 포털 연동 Tool, 전문 에이전트 | 13~17시간 |
| 4주차 | Step 9~11 | 세 가지 에이전트 패턴 구현과 비교 | 15~18시간 |

1. [Step 1: Spring AI 환경 설정](./docs/step-1-spring-ai-settings.md)
2. [Step 2: ChatClient 기초와 LLM 연동](./docs/step-2-chatclient-basics.md)
3. [Step 3: Function Calling 기초](./docs/step-3-function-calling.md)
4. [Step 4: ChatClient Advisor와 Tool 실행 과정 이해](./docs/step-4-tool-callback.md)
5. [Step 5: 다중 Function Calling과 MCP Tool 패턴](./docs/step-5-mcp-tool-pattern.md)
6. [Step 6: 항공편 검색 Tool 구현](./docs/step-6-flight-search-tool.md)
7. [Step 7: 공항/항공사 정보 Tool 구현](./docs/step-7-airport-info-tool.md)
8. [Step 8: 전문 에이전트 설계와 구현](./docs/step-8-specialized-agents.md)
9. [Step 9: Coordinator 패턴 — 코드가 제어하는 워크플로](./docs/step-9-coordinator-pattern.md)
10. [Step 10: Tool Calling 에이전트 — LLM이 제어하는 검색](./docs/step-10-tool-calling-agent.md)
11. [Step 11: Orchestrator-Workers 패턴 — LLM이 작업을 나누고 종합하기](./docs/step-11-orchestrator-workers.md)

---

## 시작하기

### 1. 사전 요구사항

- Java 21
- 공공데이터 포털 서비스 키 (필수): [국내항공운항정보](https://www.data.go.kr/data/15098526/openapi.do) 활용 신청 후 발급
- Google Gemini API 키 (선택): [Google AI Studio](https://aistudio.google.com/app/apikey)에서 발급
- Ollama (선택): 내 컴퓨터에서 모델을 실행할 때만 필요

### 2. 환경 변수 설정

| 환경 변수 | 필수 여부 | 설명 |
|-----------|-----------|------|
| `DATA_GO_KR_SERVICE_KEY` | 필수 | 공공데이터 포털 서비스 키 (항공편·공항·항공사 조회) |
| `GOOGLE_GENAI_API_KEY` | 선택 | Gemini API 키. 없어도 앱은 정상 시작하며, 화면에서 Gemini를 고르면 키를 설정하라는 안내가 나옵니다 |

```bash
export DATA_GO_KR_SERVICE_KEY="발급받은_공공데이터_포털_서비스_키"
export GOOGLE_GENAI_API_KEY="발급받은_Gemini_API_키"   # 선택
```

### 3. Ollama 서버 주소

Ollama 주소는 `src/main/resources/application.yml`의 `spring.ai.ollama.base-url`에서 정합니다. 기본값은 실습용 원격 서버(`http://ollama.java21.net`)입니다.
내 컴퓨터에서 실행하려면 주소를 `http://localhost:11434`로 바꾸고 모델을 내려받으세요.

```bash
ollama pull qwen2.5:3b-instruct-q4_K_M
```

### 4. 실행

```bash
./mvnw spring-boot:run
```

브라우저에서 [http://localhost:8080](http://localhost:8080)을 엽니다. IntelliJ IDEA HTTP Client용 `docs/api-test/*.http` 파일로 API를 직접 호출해 볼 수도 있습니다. 서비스 키는 `docs/api-test/http-client.private.env.json`(git에 올리지 않음)에 적습니다. 자세한 내용은 [API 테스트 가이드](./docs/api-test/README.md)를 참고하세요.

### 5. 테스트

```bash
./mvnw test
```

LLM과 공공데이터 포털 호출은 가짜 구현(또는 `MockRestServiceServer`)으로 바꿔 끼우므로, Ollama·Gemini·서비스 키 없이도 세 패턴의 흐름을 검증할 수 있습니다. 애플리케이션 전체를 띄우는 `contextLoads` 테스트 1개만 `@Disabled`로 꺼 두었습니다. 테스트 수는 계속 바뀔 수 있으니 실행 결과의 `Tests run` 줄로 확인하세요.

---

## 배포

`main` 브랜치에 push하면 GitHub Actions([.github/workflows/maven.yml](./.github/workflows/maven.yml))가 빌드부터 서버 재시작까지 처리합니다.

```text
[GitHub Actions]  DATA_GO_KR_SERVICE_KEY Secret 확인 (없으면 중단)
    ↓
[GitHub Actions]  JDK 21 설치 → mvn -B package -DskipTests → target/fly.jar
    ↓
[SSH]             서버의 기존 ~/fly-schedule/startup.sh를 startup.sh.orig로 한 번만 백업
    ↓
[SCP]             deploy/startup.sh → ~/fly-schedule/startup.sh
                  target/fly.jar   → ~/fly-schedule/target/fly.jar
    ↓
[SSH]             ~/fly-schedule/startup.sh 실행 (서비스 키를 환경 변수로 전달)
```

빌드 단계는 `-DskipTests`로 테스트를 건너뛰므로, push하기 전에 로컬에서 `./mvnw test`를 실행하세요.

> **주의**
> 지금 workflow는 `main` 대상 pull request에서도 실행되며, 배포 단계에 조건이 없어 PR을 열기만 해도 서버에 배포됩니다. PR에서는 빌드만 하려면 배포 단계에 `if: github.event_name == 'push'`를 붙여야 합니다.

### GitHub Secrets

저장소의 Settings > Secrets and variables > Actions에 다음 값을 등록합니다.

| Secret | 용도 |
|--------|------|
| `SSH_IP` | 배포 서버 주소 |
| `SSH_ID` | SSH 사용자 이름 |
| `SSH_PASSWORD` | SSH 비밀번호 |
| `SSH_PORT` | SSH 포트 |
| `DATA_GO_KR_SERVICE_KEY` | 공공데이터 포털 서비스 키. workflow가 원격 셸의 환경 변수로 넘기고, 애플리케이션이 이어받습니다 |

Gemini 키는 Secret이 아니라 서버의 `~/fly-schedule/.env`에 적어 둡니다(선택). `startup.sh`가 이 파일이 있으면 읽어 환경 변수로 내보냅니다.

```bash
# ~/fly-schedule/.env
GOOGLE_GENAI_API_KEY=발급받은_Gemini_API_키
```

### deploy/startup.sh가 하는 일

1. jar 파일(`~/fly-schedule/target/fly.jar`, 없으면 `~/fly-schedule/fly.jar`)과 `.env`를 읽고, `DATA_GO_KR_SERVICE_KEY`가 없으면 멈춥니다.
2. 포트를 정합니다. 우선순위는 `SERVER_PORT` 환경 변수 → `startup.sh.orig`에 적힌 포트(`server.port=` 또는 `SERVER_PORT=`) → nginx 설정에서 `fly.java21.net`(`NGINX_SERVER_NAME`으로 변경 가능)을 넘기는 `proxy_pass` 포트(upstream 이름이면 그 upstream의 `server` 포트) → `8080`입니다. 고른 포트와 근거, 502 진단용 nginx 설정 일부를 로그에 출력합니다.
3. 실행 중인 애플리케이션을 종료합니다. `app.pid`의 프로세스와 `fly.jar`를 실행 중인 java 프로세스에 종료 신호를 보내고, 30초 안에 끝나지 않으면 강제 종료합니다.
4. `nohup`(Linux에서는 `setsid`도)으로 새로 시작합니다. `JAVA_OPTS` 기본값은 `-Xms512m -Xmx1024m`이고, 로그는 `~/fly-schedule/app.log`에 쌓입니다.
5. 최대 90초 동안 `http://localhost:{포트}/`를 확인합니다. 응답이 오면 성공, 아니면 최근 로그 50줄을 출력하고 실패로 끝납니다.

> **주의**
> nginx가 바라보는 포트와 애플리케이션 포트가 다르면 502 Bad Gateway가 납니다. 배포 로그의 `[startup] 포트 ... (근거: ...)` 줄과 `[startup][nginx]` 줄을 먼저 확인하세요.

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| 언어·프레임워크 | Java 21, Spring Boot 3.5.11, Spring Web MVC, Lombok, Maven |
| AI | Spring AI 1.1.4 (ChatClient, Tool Calling, Structured Output, Advisor) |
| LLM | Ollama `qwen2.5:3b-instruct-q4_K_M`, Google Gemini `gemini-2.5-flash` |
| 외부 API | 공공데이터 포털 국내항공운항정보 (RestClient) |
| 동시성 | Java 21 가상 스레드 (Orchestrator-Workers의 Worker 병렬 실행) |
| 화면 | 단일 페이지 `templates/index.html` (일반 HTML/CSS/JavaScript, Fetch API, 외부 UI 프레임워크 없음) |

---

## 관련 문서

- [수업 안내 (docs)](./docs/README.md)
- [공공데이터 포털 - 국내항공운항정보](https://www.data.go.kr/data/15098526/openapi.do)
- [Spring AI 공식 문서](https://docs.spring.io/spring-ai/reference/)
- [Anthropic, Building effective agents](https://www.anthropic.com/engineering/building-effective-agents)

---

## 라이선스

이 프로젝트는 학습 및 교육 목적으로 제작되었습니다.
