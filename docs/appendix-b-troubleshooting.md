# 부록 B 문제 해결 모음

실습과 배포에서 자주 만나는 문제를 영역별로 모았습니다. 증상을 찾아 원인과 해결을 확인하고, 자세한 설명은 오른쪽의 장을 봅니다. **(실제 사례)** 로 표시한 항목은 이 프로젝트를 만들고 운영하면서 실제로 겪은 문제입니다.

문제를 좁힐 때는 먼저 화면이나 응답의 **실행 기록(trace)** 을 봅니다. 어느 단계에서 `실패:`가 찍혔는지가 가장 빠른 단서입니다. 그다음 애플리케이션 로그를 봅니다.

## 빌드와 실행

| 증상 | 원인 | 해결 | 관련 장 |
|---|---|---|---|
| `zsh: no such file or directory: ./mvnw` 또는 `./mvnw: No such file or directory` **(실제 사례)** | Maven Wrapper(`mvnw`, `.mvn/wrapper`)가 저장소에 없던 시점의 사본 | `git pull`로 최신 코드를 받습니다. 지금 저장소에는 `mvnw`, `mvnw.cmd`, `.mvn/wrapper`가 들어 있습니다 | [들어가며](./00-introduction.md) |
| `./mvnw: Permission denied` | 실행 권한이 빠진 채 복사됨 | `chmod +x mvnw` | [들어가며](./00-introduction.md) |
| `Could not resolve dependency for org.springframework.ai:...` | BOM 버전·스타터 이름 오타, 네트워크 문제 | `pom.xml`의 `spring-ai-bom` 버전(1.1.4)과 `artifactId`를 확인하고 `./mvnw clean package -U` | [1장](./01-llm-and-spring-ai.md) |
| `NoSuchMethodError: org.springframework.ai...` | Spring AI 모듈 버전이 서로 다름 | 스타터에 따로 적은 `<version>`을 지우고 BOM을 따르게 합니다 | [1장](./01-llm-and-spring-ai.md) |
| `UnsupportedClassVersionError ... class file version 65.0` | Java 21로 빌드한 jar를 더 낮은 버전의 Java로 실행 | `java -version`이 21인지 확인합니다. 서버에서는 `JAVA_BIN`을 지정하거나 `/usr/local/java/java21/bin/java`를 설치합니다 | [10장](./10-choosing-testing-operating.md) |
| `Web server failed to start. Port 8080 was already in use.` | 이전에 띄운 앱이나 다른 프로그램이 8080을 쓰는 중 | `lsof -i :8080`으로 프로세스를 찾아 종료하거나 `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081`로 다른 포트를 씁니다 | [들어가며](./00-introduction.md) |
| `No qualifying bean of type 'org.springframework.ai.chat.client.ChatClient'` + `expected single matching bean but found 2` | ChatClient 빈이 둘(Ollama, Gemini)인데 이름을 지정하지 않음 | 주입받는 쪽에 `@Qualifier("ollamaChatClient")`처럼 빈 이름을 적거나 `ChatClientProvider`를 씁니다 | [2장](./02-chatclient.md) |
| Docker 빌드에서 `COPY failed: ... flyschedule-0.0.1-SNAPSHOT.jar: not found` | `pom.xml`의 `<finalName>`이 `fly`라서 jar 이름이 `target/fly.jar`임 | `Dockerfile`의 `COPY` 줄을 `target/fly.jar`로 고칩니다 | [10장](./10-choosing-testing-operating.md) |

## LLM 연결과 모델

| 증상 | 원인 | 해결 | 관련 장 |
|---|---|---|---|
| "LLM 서버에 연결할 수 없습니다. Ollama가 실행 중인지(spring.ai.ollama.base-url) 확인하거나 다른 모델을 선택하세요." 로그에는 `Connection refused` | Ollama 서버가 꺼져 있거나 `spring.ai.ollama.base-url`이 틀림 | `curl <base-url>/api/tags`로 응답을 확인합니다. 로컬 Ollama라면 `base-url`을 `http://localhost:11434`로 바꾸고 `ollama pull qwen2.5:3b-instruct-q4_K_M` | [1장](./01-llm-and-spring-ai.md) |
| 연결 실패 응답이 몇 분씩 늦게 옴 | `spring.ai.retry` 설정이 빠져 기본값(최대 10회, 대기 간격 증가)으로 재시도 | `application.yml`의 `spring.ai.retry.max-attempts: 2`와 `backoff` 설정을 확인합니다 | [1장](./01-llm-and-spring-ai.md) |
| "Gemini API 키가 설정되지 않았습니다. 환경 변수 GOOGLE_GENAI_API_KEY를 설정하거나 Ollama를 선택하세요." | `GOOGLE_GENAI_API_KEY`가 없어 `api-key`가 `not-set` | 오류가 아니라 의도한 안내입니다. Gemini를 쓰려면 환경 변수를 설정하고 다시 시작합니다 | [1장](./01-llm-and-spring-ai.md) |
| "Gemini API 키가 올바르지 않습니다. GOOGLE_GENAI_API_KEY를 확인하세요." **(실제 사례)** | 키 값이 틀렸거나, 키가 공개 저장소에 올라가 Google이 차단함(`API key not valid`, `API key was reported as leaked`) | [Google AI Studio](https://aistudio.google.com/app/apikey)에서 기존 키를 지우고 새로 발급합니다. 키는 코드와 `application.yml`에 적지 않습니다 | [1장](./01-llm-and-spring-ai.md) |
| 응답이 수십 초 이상 걸림 | 원격 Ollama의 3B 모델 + Tool 왕복 여러 번 | Gemini로 바꿔 비교합니다. 정규식으로 처리되는 문장은 LLM을 부르지 않아 빠릅니다 | [8장](./08-tool-calling-agent.md) |
| `I/O error ... Read timed out` | LLM 응답이 `spring.http.client.read-timeout`(300s)을 넘김 | 모델·서버 상태를 확인합니다. 값을 더 늘리기보다 작업 API를 씁니다 | [10장](./10-choosing-testing-operating.md) |

## 공공데이터 포털 API

| 증상 | 원인 | 해결 | 관련 장 |
|---|---|---|---|
| "공공데이터 포털 서비스 키가 설정되지 않았습니다. 환경 변수 DATA_GO_KR_SERVICE_KEY를 설정하세요." **(실제 사례)** | `DATA_GO_KR_SERVICE_KEY` 환경 변수 없음. IDE에서 실행하면 터미널의 `export`가 전달되지 않기도 함 | 환경 변수를 설정하고 다시 시작합니다. IntelliJ에서는 Run Configuration의 Environment variables에 넣습니다 | [5장](./05-domain-tools.md) |
| "공공데이터 포털 API 오류: SERVICE_KEY_IS_NOT_REGISTERED_ERROR" | 활용 신청이 승인되지 않았거나 인코딩된(Encoding) 키를 씀 | 공공데이터 포털에서 승인 상태를 확인하고 Decoding 키를 씁니다 | [5장](./05-domain-tools.md) |
| "공공데이터 포털 API를 호출하지 못했습니다. 잠시 후 다시 시도하세요." | 네트워크 오류나 API 서버의 HTTP 오류 | 로그의 `공공데이터 포털 API 호출 실패:` 줄에서 원인을 확인합니다 | [5장](./05-domain-tools.md) |
| 김포→제주처럼 운항편이 많은 노선에서 결과가 정확히 100편에서 끊기고 저녁 항공편이 없음 **(실제 사례)** | 한 번에 받는 건수(`numOfRows`)가 작아 하루 운항편이 잘림 (김포→제주는 하루 118편, 2026년 10월 실측) | `ApiClientService.MAX_ROWS`(500)로 요청합니다. `ApiClientServiceTest.requestsEnoughRows()`가 이 값을 지킵니다 | [5장](./05-domain-tools.md) |
| 검색은 성공했는데 항공편이 0편 | 공항 코드가 틀림(API는 잘못된 코드에도 빈 목록을 줌), 과거·먼 날짜 | `GET /api/mcp/airports` 결과와 `AirportCodeAgent`의 코드를 비교합니다 | [6장](./06-specialized-agents.md) |
| 최저가가 0원으로 나옴 | 운임 0원(정보 없음)을 최저가 계산에 넣음 | `PriceFilterAgent.findCheapest()`를 씁니다 | [6장](./06-specialized-agents.md) |
| "알 수 없는 공항입니다: 서울" | 공항이 여러 개인 도시 이름은 일부러 등록하지 않음 | "김포"나 "인천"처럼 공항 이름으로 입력합니다 | [6장](./06-specialized-agents.md) |
| "날짜 형식이 올바르지 않습니다" | `2026/03/25`처럼 `DateParserAgent`가 받지 않는 형식 | `YYYY-MM-DD`, `YYYYMMDD`, 오늘·내일·모레 등을 씁니다 | [6장](./06-specialized-agents.md) |

## 패턴별 동작

| 증상 | 원인 | 해결 | 관련 장 |
|---|---|---|---|
| Coordinator 패턴에서 "출발 공항과 도착 공항을 찾지 못했습니다" | 정규식이 포기한 문장을 LLM이 처리했는데 출발·도착을 비움(작은 모델에서 잦음) | 공항 이름 두 개를 넣어 정규식 경로로 보내거나 Gemini를 씁니다 | [7장](./07-coordinator.md) |
| 시간·가격 조건을 넣었는데 필터 단계가 trace에 없음 | 정규식이 그 표현을 인식하지 못함(예: "10시 넘어서") | 1단계 detail의 검색 조건을 확인하고 "10시 이후"처럼 씁니다 | [7장](./07-coordinator.md) |
| Tool Calling Agent가 `success: false`이고 message가 LLM의 질문 | LLM이 Tool을 하나도 부르지 않음 | 출발지·도착지·날짜를 모두 넣어 다시 질문하거나 Gemini로 비교합니다. Tool 설명과 system 프롬프트를 보완합니다 | [8장](./08-tool-calling-agent.md) |
| TOOL 단계가 "Tool 실행 실패" | LLM이 엉뚱한 인자(없는 공항 등)를 넣었거나 Tool 안에서 `ExternalApiException` 발생 | detail의 실패 메시지를 확인합니다. LLM은 오류를 받고 다른 인자로 다시 시도할 수 있습니다 | [8장](./08-tool-calling-agent.md) |
| 다른 요청의 Tool 기록이 섞임 | `stream()`이나 다른 스레드에서 Tool이 실행되어 `ThreadLocal`이 비워지지 않음 | `call()`을 쓰고 `ToolResultCaptureCleanupFilter`가 빈으로 등록되어 있는지 확인합니다 | [5장](./05-domain-tools.md) |
| Advisor 로그에 Tool 호출이 보이지 않음 | 정상. Tool 루프는 ChatModel 안에서 돌아 Advisor에는 최종 응답만 보임 | Tool 호출은 trace의 TOOL 단계로 확인합니다 | [3장](./03-advisor.md) |
| "검색 계획을 세우지 못했습니다..." | 작은 모델이 JSON 형식을 지키지 못해 `entity()` 결과가 비어 있음 | Gemini를 쓰거나 문장에 출발지·도착지를 분명히 넣습니다 | [9장](./09-orchestrator-workers.md) |
| "검색할 수 있는 작업이 없습니다..." | LLM의 계획이 모두 `validate()`에서 걸러짐(없는 공항, 출발=도착) | 지원하는 공항 이름으로 입력하고 로그의 "계획 검증" 경고를 확인합니다 | [9장](./09-orchestrator-workers.md) |
| WORKER 막대가 겹치지 않고 계단처럼 이어짐 | Worker를 순차로 실행함(제출과 기다리기를 한 스트림에서 섞음) | "모두 제출한 뒤 모두 기다리기" 구조를 유지합니다 | [9장](./09-orchestrator-workers.md) |
| 답변에 결과에 없는 항공편·가격이 나옴 | 종합 LLM의 환각 | system 프롬프트의 근거 제한 규칙을 확인하고 `results`의 실제 데이터와 대조합니다 | [9장](./09-orchestrator-workers.md) |

## 배포와 운영

| 증상 | 원인 | 해결 | 관련 장 |
|---|---|---|---|
| 데모 사이트가 `502 Bad Gateway` **(실제 사례)** | nginx는 10600번 포트로 넘기는데 앱은 8080으로 기동. 원래 스크립트의 `PORT=10600` 형식을 감지하지 못했음 | 배포 로그의 `[startup] 포트 ... (근거: ...)` 줄과 `[startup][nginx]` 줄을 비교합니다. 지금 `startup.sh`는 `SERVER_PORT` → `startup.sh.orig` → nginx 설정 → 10600 순서로 포트를 정합니다 | [10장](./10-choosing-testing-operating.md) |
| 화면에 `HTTP 504 - 서버가 응답하지 않습니다...`, 또는 동기 API가 1분쯤 뒤 `504 Gateway Time-out` **(실제 사례)** | LLM 호출이 nginx 기본 `proxy_read_timeout`(60초)을 넘김 | 화면처럼 작업 API(`POST /api/search/{pattern}/jobs` + `GET /api/search/jobs/{jobId}`)를 씁니다. nginx에는 `proxy_read_timeout 300s;`를 권장합니다 | [10장](./10-choosing-testing-operating.md) |
| GitHub Actions의 `Check secrets` 단계가 실패하며 "GitHub Secrets에 DATA_GO_KR_SERVICE_KEY가 없습니다." | 저장소 Secrets에 서비스 키가 없음 | Settings > Secrets and variables > Actions에 `DATA_GO_KR_SERVICE_KEY`를 등록합니다 | [10장](./10-choosing-testing-operating.md) |
| `run-script` 단계가 "[startup] 기동 실패. 최근 로그:"로 끝남 | 90초 안에 `http://localhost:{포트}/`가 응답하지 않음(설정 오류, 포트 충돌, Java 버전) | 함께 출력된 로그 50줄과 `~/fly-schedule/app.log`를 확인합니다 | [10장](./10-choosing-testing-operating.md) |
| 배포된 사이트에서 Gemini만 "키가 설정되지 않았습니다" | 서버의 `~/fly-schedule/.env`에 `GOOGLE_GENAI_API_KEY`가 없음 | `.env`에 키를 적고 다시 배포하거나 `startup.sh`를 다시 실행합니다 | [10장](./10-choosing-testing-operating.md) |
| 서버를 재시작한 뒤 조회하던 작업이 `404` | 작업은 메모리에만 있음. 끝난 작업도 10분 뒤 지워짐 | 화면에서 다시 검색합니다 | [10장](./10-choosing-testing-operating.md) |
| "실행 중인 검색이 너무 많습니다. 잠시 후 다시 시도하세요." | 보관 중인 작업이 `MAX_JOBS`(200)개에 도달 | 잠시 기다리면 끝난 작업이 정리됩니다 | [10장](./10-choosing-testing-operating.md) |

## 테스트

| 증상 | 원인 | 해결 | 관련 장 |
|---|---|---|---|
| 테스트가 Ollama나 공공데이터 포털에 접속하려 함 | 단위 테스트에서 진짜 ChatClient·RestClient를 씀 | ChatClient는 Mockito mock(`RETURNS_DEEP_STUBS`, `RETURNS_SELF`)으로, 외부 API는 `MockRestServiceServer`로 바꿉니다 | [10장](./10-choosing-testing-operating.md) |
| 비동기 테스트가 가끔 실패함 | `Thread.sleep`으로 시간을 짐작해 기다림 | Awaitility의 `await().atMost(...).until(...)`로 조건을 기다립니다 | [10장](./10-choosing-testing-operating.md) |
| `contextLoads` 테스트가 skipped로 나옴 | 애플리케이션 전체를 띄우는 `FlyscheduleApplicationTests` 하나를 `@Disabled`로 꺼 둠(Ollama 등 외부 의존성 때문) | 정상입니다 | [들어가며](./00-introduction.md) |
