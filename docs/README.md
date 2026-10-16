# Spring AI로 배우는 Agent 설계 — 항공편 검색으로 비교하는 세 가지 패턴

"이번 주 금요일이나 토요일에 김포나 광주에서 제주 가는 제일 싼 항공편 찾아줘."

이 문장을 처리하려면 몇 번 검색해야 할까요? 날짜 둘, 출발 공항 둘이니 네 번입니다. 그 넷을 **누가** 정할까요? 개발자가 미리 짜 둔 코드일까요, 문장을 읽은 LLM일까요?

이 책은 처음부터 끝까지 질문 하나를 붙듭니다. **"다음 단계를 누가 결정하는가 — 코드인가, LLM인가?"** 공공데이터 포털의 국내항공운항정보 API를 Tool로 연결한 뒤, 같은 문장을 세 가지 구조로 풀어 봅니다.

| 패턴 | 다음 단계를 누가 정하나 | 장 |
|---|---|---|
| **Coordinator 패턴** | 코드가 순서를 정하고 전문 Agent를 차례로 호출 | [7장](./07-coordinator.md) |
| **Tool Calling Agent** | LLM이 어떤 Tool을 언제 부를지 결정 | [8장](./08-tool-calling-agent.md) |
| **Orchestrator-Workers 패턴** | LLM이 작업을 나누고 종합, 실행은 코드(Worker) | [9장](./09-orchestrator-workers.md) |

완성된 애플리케이션은 세 패턴을 동시에 실행하고, 단계마다 CODE·LLM·TOOL·WORKER로 색을 나눈 **실행 기록(trace)** 타임라인을 나란히 보여 줍니다. 데모 사이트 [https://fly.java21.net](https://fly.java21.net)에서 먼저 둘러볼 수 있습니다. 소스는 [GitHub 저장소](https://github.com/nhn-academy-marco/ai-fly-schedule)에 있습니다.

## 누구를 위한 책인가

- Java와 Spring Boot로 간단한 REST API를 만들어 본 부트캠프 수강생
- LLM과 Agent는 처음인 개발자

### 선수 지식

| 영역 | 필요한 수준 |
|---|---|
| Java | 클래스·인터페이스, 컬렉션, 람다와 `Supplier`·`Function`, `record` |
| Spring Boot | `@RestController`, `@Service`, 생성자 주입, `application.yml` |
| HTTP | JSON 요청·응답, `curl` 또는 IntelliJ IDEA HTTP Client |
| 테스트 | JUnit 5로 단위 테스트를 작성하고 `./mvnw test`로 실행 |

LLM, 프롬프트, Tool Calling, Agent 패턴은 책에서 처음부터 설명합니다. 가상 스레드는 9장에서 필요한 만큼만 다룹니다.

### 실습 환경

Java 21, Spring Boot 3.5.11, Spring AI 1.1.4를 씁니다. 공공데이터 포털 서비스 키(필수)와 Gemini API 키(선택)가 필요하고, Maven은 저장소의 `./mvnw`를 쓰면 따로 설치하지 않아도 됩니다. 준비 과정은 [들어가며](./00-introduction.md)에서 차례로 안내합니다.

## 목차

| 장 | 제목 | 이 장에서 하는 일 |
|---|---|---|
| [들어가며](./00-introduction.md) | 들어가며 | 책의 질문, 완성 앱 둘러보기, 개발 환경 준비 |
| [1장](./01-llm-and-spring-ai.md) | LLM과 Spring AI 시작하기 | 토큰·컨텍스트·temperature, Spring AI 구조, Ollama·Gemini 설정(모델 선택, thinking level) |
| [2장](./02-chatclient.md) | ChatClient로 LLM 부르기 | `prompt().call().content()`, Structured Output과 `LenientJsonCleaner`, 프롬프트 설계 기초(날짜표, `DateContext.annotate`) |
| [3장](./03-advisor.md) | Advisor로 LLM 호출 들여다보기 | `SimpleLoggerAdvisor`, 직접 만드는 Advisor, 관찰의 중요성 |
| [4장](./04-tool-calling.md) | Tool Calling의 원리 | LLM은 요청하고 Spring AI가 실행한다, `@Tool`·`@ToolParam` |
| [5장](./05-domain-tools.md) | 항공편 도메인 Tool 만들기 | 공공데이터 포털 API, `ApiClientService`, Tool 7개와 설계 원칙, Tool 결과 요약(`CompactFlightResultConverter`) |
| [6장](./06-specialized-agents.md) | 전문 Agent로 나누기 | LLM 없는 결정적 컴포넌트, 단일 책임, 단위 테스트 |
| [7장](./07-coordinator.md) | Coordinator 패턴 — 코드가 결정한다 | 정규식 우선과 LLM fallback, 실행 기록, 패턴의 한계 |
| [8장](./08-tool-calling-agent.md) | Tool Calling Agent — LLM이 결정한다 | system 프롬프트, Tool 호출 기록, 실패 사례, 측정하고 개선하기(99.4초 → 약 24초) |
| [9장](./09-orchestrator-workers.md) | Orchestrator-Workers 패턴 — LLM이 나누고 코드가 실행한다 | 계획과 검증(지어낸 조건 지우기), 가상 스레드 병렬 실행, 결과 종합 |
| [10장](./10-choosing-testing-operating.md) | 패턴 고르기, 테스트, 운영 | 패턴 선택 가이드, 측정하고 개선하기, 가짜 LLM 테스트, 작업 API, 배포, 확장 |
| [부록 A](./appendix-a-glossary.md) | 용어집 | 책에 나온 용어와 처음 나오는 장 |
| [부록 B](./appendix-b-troubleshooting.md) | 문제 해결 모음 | 증상 → 원인 → 해결 |

API를 직접 호출해 보는 `.http` 파일은 [API 테스트 가이드](./api-test/README.md)에 있습니다.

### 장마다 반복되는 흐름

각 장은 구체적인 상황이나 질문으로 시작해 **개념 → 실제 코드 → 직접 확인 → 생각해 보기** 순서로 진행합니다. 코드는 저장소의 실제 소스에서 핵심만 발췌하고 파일 경로를 붙였습니다. 장 끝에는 핵심 정리와 연습 문제(기본/도전, 힌트, 완료 기준)가 있습니다.

본문에서 쓰는 상자는 네 가지입니다.

- **주의**: 실수하기 쉬운 곳
- **팁**: 알아 두면 편한 방법
- **생각해 보기**: 정답보다 설계 판단을 묻는 질문
- **직접 확인하기**: 실행할 명령과 기대 결과

## 4주 수업 일정

| 주차 | 장 | 주제 |
|---|---|---|
| 1주 | 들어가며, 1~2장 | 환경 준비, LLM과 Spring AI, ChatClient와 Structured Output |
| 2주 | 3~5장 | Advisor로 관찰하기, Tool Calling의 원리, 항공편 도메인 Tool |
| 3주 | 6~7장 | 전문 Agent, Coordinator 패턴 |
| 4주 | 8~10장 | Tool Calling Agent, Orchestrator-Workers 패턴, 패턴 선택·테스트·운영 |

## 강사용 진행 팁

화면(`/`)은 4주 내내 쓰는 공통 교구입니다. 문장을 입력하고 **실행하기**를 누르면 체크한 패턴이 동시에 실행됩니다. 체크 상자로 일부 패턴만 고를 수 있고, 예시 칩(기본, 시간, 가격, 복합)과 LLM 전환 버튼(Ollama, Gemini)이 있습니다. 결과 패널의 숫자 칸(전체 시간, LLM 호출, Tool 호출, Worker)은 패턴 비교 표를 만들 때 그대로 옮겨 적으면 됩니다. 패턴 카드의 **7장 읽기**·**8장 읽기**·**9장 읽기** 링크와 상단의 **교재 보기** 버튼으로 해당 장을 바로 열 수 있습니다.

**수업 중 예시 링크 공유**: 주소에 검색 조건을 붙이면 문장과 옵션이 채워진 채로 바로 실행됩니다. 메신저나 슬라이드에 링크를 붙여 두면 수강생 모두가 같은 질문을 같은 설정으로 실행할 수 있습니다.

```text
https://fly.java21.net/?q=이번 주 금요일이나 토요일에 김포나 광주에서 제주 가는 제일 싼 항공편 찾아줘&model=ollama&patterns=coordinator,orchestrator&run=1
```

`q`는 검색 문장, `model`은 `ollama` 또는 `gemini`, `patterns`는 쉼표로 구분한 패턴 이름(`coordinator`, `tool-calling`, `orchestrator`)입니다. `run=1`을 빼면 채우기만 하고 실행은 수강생이 누르게 할 수 있습니다. 메신저에 따라 공백이 있는 주소가 잘리면 공백을 `%20`으로 바꿔 붙입니다.

아래 시점에 화면으로 시연하면 "지금 만드는 부품이 어디에 쓰이는지"를 수강생이 계속 확인할 수 있습니다.

| 시점 | 시연 | 짚어 줄 점 |
|---|---|---|
| 1주 첫 시간 (들어가며) | 데모 사이트에서 "복합" 예시로 세 패턴을 함께 실행 | 4주 뒤 완성할 결과입니다. 답이 비슷해도 실행 기록의 색이 다르다는 것만 보여 줍니다. 책의 질문을 여기서 처음 던집니다. |
| 1주 끝 (2장 후) | "기본" 예시를 Ollama와 Gemini로 한 번씩 실행 | 모델만 바꿔도 응답 시간과 답이 달라집니다. Gemini 키가 없으면 안내 문구가 나오는 것도 확인합니다. |
| 2주 끝 (5장 후) | Tool Calling만 체크하고 "시간" 예시 실행 | TOOL 단계에 LLM이 고른 Tool 이름과 인자가 보입니다. TOOL 단계는 Tool이 실행되는 즉시 나타나고 LLM 단계는 마지막에 붙습니다. 4~5장의 `@Tool`과 `ToolResultCapture`가 이 기록을 만듭니다. |
| 3주 끝 (7장 후) | Coordinator만 체크하고 "기본"·"가격" 예시 실행 | 모든 단계가 CODE이고 actor가 6장의 전문 Agent입니다. 정규식으로 조건을 뽑으면 LLM을 한 번도 부르지 않습니다. |
| 4주 8·9장 시작 | 해당 패턴만 체크하고 실행 | 그 장에서 만들 코드가 타임라인의 어느 줄이 되는지 먼저 보여 주고 시작합니다. |
| 4주 마지막 (10장) | "기본"과 "복합"으로 세 패턴을 함께 실행 | 기본 문장은 세 패턴 모두 잘 처리하지만, 복합 문장은 Orchestrator-Workers 패턴만 여러 작업으로 나눠 병렬 실행합니다. LLM 호출 수와 걸린 시간을 표로 정리하게 합니다. |

> **팁**
> 공용 Ollama 서버는 여러 명이 동시에 호출하면 응답이 수십 초 걸릴 수 있습니다. 시연은 강사 화면에서 한 번만 실행하고, 수강생 실습은 `./mvnw test`(가짜 LLM 사용)로 흐름을 먼저 확인하게 하면 대기 시간을 줄일 수 있습니다. 가짜 LLM 테스트 기법은 [10장](./10-choosing-testing-operating.md)에 정리되어 있습니다.

## 참고 자료

- [Anthropic, Building effective agents](https://www.anthropic.com/engineering/building-effective-agents) — workflow와 agent의 구분, orchestrator-workers
- [Microsoft Azure Architecture Center, AI agent orchestration patterns](https://learn.microsoft.com/en-us/azure/architecture/ai-ml/guide/ai-agent-design-patterns)
- [Spring 블로그, Building Effective Agents with Spring AI](https://spring.io/blog/2025/01/21/spring-ai-agentic-patterns)
- [Spring AI 공식 문서](https://docs.spring.io/spring-ai/reference/)
- [공공데이터 포털 - 국내항공운항정보](https://www.data.go.kr/data/15098526/openapi.do)
