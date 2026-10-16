# Step 1: Spring AI 환경 설정

이 교재는 국내선 항공편 검색 서비스(flyschedule)를 만들면서 Spring AI로 LLM을 다루는 방법과, LLM을 쓰는 세 가지 검색 패턴(Coordinator 패턴, Tool Calling 에이전트, Orchestrator-Workers 패턴)을 배웁니다. Step 1은 그 출발점입니다.

**이 Step에서 만들 것**: Ollama와 Gemini 두 LLM에 연결할 수 있도록 설정한 Spring Boot 애플리케이션을 실행하고, 브라우저에서 `http://localhost:8080` 화면이 열리는 것까지 확인합니다. 정상이라면 콘솔에 다음과 같은 로그가 보입니다.

```text
... Tomcat started on port 8080 (http) with context path '/'
... Started FlyscheduleApplication in 3.512 seconds (process running for 3.9)
```

## 학습 목표

이 Step을 마치면 다음을 할 수 있습니다.

1. LLM, 토큰, 프롬프트, temperature 같은 기본 용어를 설명할 수 있다.
2. `pom.xml`에 Spring AI BOM과 Ollama·Gemini 스타터를 추가할 수 있다.
3. `application.yml`에 Ollama·Gemini 연결 설정과 타임아웃·재시도 설정을 작성하고, 각 값의 의미를 설명할 수 있다.
4. API 키를 환경 변수로 주입하고, 키가 없을 때 애플리케이션이 어떻게 동작하는지 확인할 수 있다.
5. 애플리케이션을 실행하고 로그와 화면으로 정상 기동을 확인할 수 있다.

---

## 전체 흐름

`application.yml`에 적은 설정을 Spring AI의 자동 설정(Auto-Configuration)이 읽어 LLM 연결 객체(`ChatModel`)를 만듭니다. 이번 Step은 그림의 앞부분, 설정과 자동 설정까지를 다룹니다.

```text
[pom.xml]  Spring AI BOM + 스타터 2개 (Ollama, Gemini)
  ↓
[application.yml]  spring.ai.ollama.* / spring.ai.google.genai.* / 환경 변수
  ↓
[Spring AI 자동 설정]
  ↓
[Spring 컨테이너]  ollamaChatModel, googleGenAiChatModel 빈
  ↓
[ChatClientConfig]  ChatClient 빈 생성                (Step 2)
  ↓
[컨트롤러 → 서비스 → ChatClient → LLM]              (Step 2~4)
```

---

## LLM 기초 개념

### LLM이란?

**LLM**(Large Language Model, 대규모 언어 모델)은 방대한 텍스트로 학습해, 주어진 입력 다음에 올 내용을 예측하며 문장을 만들어 내는 모델입니다. 일반 프로그램은 개발자가 작성한 규칙대로 동작하지만, LLM은 학습한 패턴으로 답을 만듭니다. 그래서 자연어를 유연하게 이해하는 대신, 같은 질문에도 답이 조금씩 달라지거나 사실이 아닌 내용을 지어낼 수 있습니다.

```text
입력: "내일 광주에서 제주로 가는 항공편 알려줘"
  ↓
토큰화: ["내일", "광주", "에서", "제주", "로", ...]
  ↓
모델: 앞의 토큰들을 보고 다음 토큰을 하나씩 예측
  ↓
출력: "내일 광주에서 제주로 가는 항공편은 ..."
```

LLM은 실시간 항공편 데이터를 알지 못합니다. 이 프로젝트가 공공데이터 포털 API를 부르는 자바 코드(Tool)를 LLM에게 쥐여 주는 이유가 여기에 있습니다(Step 3).

### 주요 용어

| 용어 | 뜻 | 예 |
|------|------|------|
| 토큰(Token) | LLM이 처리하는 최소 단위. 비용과 길이 제한의 기준 | "안녕하세요" → 3~4토큰 |
| 프롬프트(Prompt) | LLM에게 보내는 입력 전체 | 시스템 지시 + 사용자 질문 |
| 컨텍스트 윈도우(Context Window) | 한 번에 넣을 수 있는 입력+출력 토큰의 최대 길이 | Ollama `num-ctx: 4096` |
| 응답(Completion) | LLM이 만든 출력 | "내일 광주에서 ..." |

### 생성 옵션

| 옵션 | 뜻 | 이 프로젝트의 값 |
|------|------|------|
| temperature | 답의 무작위성. 낮을수록 같은 입력에 비슷한 출력 | `0.0` (검색 조건 추출과 Tool 선택은 일관성이 중요) |
| max tokens | 출력 토큰의 상한. 넘으면 답이 잘림 | `1024` |
| top-k | 다음 토큰 후보를 확률 상위 k개로 제한 | `40` |
| top-p | 누적 확률 p까지의 후보만 고려 | `0.9` |

temperature를 0으로 두어도 출력이 완전히 같다는 보장은 없습니다. 같은 입력에 "거의" 같은 출력이 나온다고 이해하세요.

---

## Spring AI란?

**Spring AI**는 Spring 애플리케이션에서 여러 LLM을 같은 API로 쓰게 해 주는 프레임워크입니다. JDBC가 데이터베이스 종류를 감추듯, Spring AI는 LLM 제공자의 차이를 감춥니다.

```text
[서비스 코드]
  ↓ 사용
[ChatClient]   LLM과 대화하는 API (Step 2)
  ↓ 위임
[ChatModel]    제공자별 구현: OllamaChatModel, GoogleGenAiChatModel
  ↓ HTTP
[LLM 서버]     Ollama 서버, Google Gemini API
```

| 구성 요소 | 역할 | 처음 다루는 Step |
|-----------|------|------------------|
| ChatModel | 특정 LLM 제공자와 실제로 통신하는 객체. 스타터가 자동으로 빈을 만듦 | Step 1 |
| ChatClient | 프롬프트를 조립하고 호출하는 API. 서비스는 이것만 사용 | Step 2 |
| Tool / Function Calling | LLM이 자바 메서드 호출을 요청하게 하는 기능(`@Tool`) | Step 3 |
| Advisor | ChatClient 요청·응답 앞뒤에 끼어드는 공통 처리(로깅 등) | Step 4 |

모델을 바꿔도 서비스 코드는 `ChatClient`만 보므로 거의 바뀌지 않습니다. 이 프로젝트는 Spring Boot 3.5.11과 Spring AI 1.1.4, Java 21을 사용합니다.

---

## Step 1-1: pom.xml 설정

### 왜 필요한가

JPA를 쓰려면 `spring-boot-starter-data-jpa`를 추가하듯, LLM을 쓰려면 Spring AI 스타터를 추가해야 합니다. Spring AI는 모듈이 여러 개이므로 **BOM**(Bill of Materials, 여러 의존성의 버전을 한곳에서 정하는 목록)으로 버전을 맞춥니다.

### 어떻게 하는가

파일: `pom.xml`

```xml
<properties>
    <java.version>21</java.version>
    <spring-ai.version>1.1.4</spring-ai.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.1.4</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

`<dependencies>`에는 두 스타터를 추가합니다. BOM이 버전을 정하므로 `<version>`은 적지 않습니다.

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-ollama</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-google-genai</artifactId>
</dependency>
```

**코드 읽기**

- `spring-ai-bom`을 `import` 범위로 넣으면 Spring AI 모듈 전체가 1.1.4로 맞춰집니다.
- `spring-ai-starter-model-ollama`는 `OllamaChatModel`을, `spring-ai-starter-model-google-genai`는 `GoogleGenAiChatModel`을 자동으로 빈으로 만듭니다.
- `<spring-ai.version>` 속성은 현재 `pom.xml`에서 직접 참조하지 않습니다. BOM의 `<version>`에 `${spring-ai.version}`을 쓰면 버전을 한 곳에서 바꿀 수 있습니다.

> **팁**: 1.1.4 같은 정식(GA) 버전은 Maven 중앙 저장소에서 받으므로 별도 저장소 설정이 필요 없습니다. Milestone(M)이나 RC 버전을 쓸 때만 `https://repo.spring.io/milestone` 저장소를 `<repositories>`에 추가합니다.

### 확인하기

```bash
./mvnw dependency:tree | grep spring-ai-starter
```

`spring-ai-starter-model-ollama:jar:1.1.4`와 `spring-ai-starter-model-google-genai:jar:1.1.4`가 보이면 성공입니다.

> **주의**: 저장소에 Maven Wrapper(`mvnw`, `.mvn/`)가 포함되어 있지 않으면 `./mvnw`가 없다는 오류가 납니다. 이때는 설치된 Maven의 `mvn` 명령으로 바꿔 실행하거나, `mvn wrapper:wrapper`로 Wrapper를 만든 뒤 진행하세요. 이 교재의 `./mvnw` 명령은 모두 `mvn`으로 바꿔 써도 됩니다.

---

## Step 1-2: Ollama 설정

### 왜 필요한가

**Ollama**는 LLM을 내 컴퓨터나 사내 서버에서 실행하는 오픈소스 도구입니다. 사용료가 없어 실습과 개발에 알맞습니다. 데이터베이스를 쓸 때 `spring.datasource.url`로 접속 주소를 알려 주듯, Ollama도 "어느 서버에, 어떤 모델로, 어떤 옵션으로" 접속할지 `application.yml`에 적어야 합니다.

### 어떻게 하는가

파일: `src/main/resources/application.yml`

```yaml
# Spring AI Ollama 설정
spring.ai.ollama:
  base-url: http://ollama.java21.net
  chat:
    options:
      model: qwen2.5:3b-instruct-q4_K_M
      temperature: 0.0
      max-tokens: 1024
      num-thread: 4
      # Tool 7개의 설명과 Tool 결과가 들어가도록 컨텍스트를 늘린다
      num-ctx: 4096
      top-p: 0.9
      top-k: 40
      keep-alive: 10m
```

**코드 읽기**

| 설정 | 값 | 의미 |
|------|-----|------|
| `base-url` | `http://ollama.java21.net` | 실습용 원격 Ollama 서버. 내 컴퓨터에서 실행하면 `http://localhost:11434`(기본값) |
| `model` | `qwen2.5:3b-instruct-q4_K_M` | Qwen 2.5, 30억(3B) 파라미터, 지시 따르기 학습(instruct), 4비트 양자화(q4_K_M)로 메모리를 줄인 모델 |
| `temperature` | `0.0` | 같은 입력에 최대한 같은 출력 |
| `max-tokens` | `1024` | 출력 토큰 상한 (Ollama의 `num_predict`) |
| `num-thread` | `4` | Ollama가 쓸 CPU 스레드 수 |
| `num-ctx` | `4096` | 컨텍스트 윈도우. Tool 7개의 설명과 Tool 결과가 함께 들어가야 해서 늘렸음 |
| `top-p`, `top-k` | `0.9`, `40` | 다음 토큰 후보 제한 |
| `keep-alive` | `10m` | 마지막 요청 뒤 모델을 메모리에 유지하는 시간 |

`spring.ai.ollama:`처럼 점으로 이은 키는 `spring:` → `ai:` → `ollama:`로 중첩해 쓴 것과 같습니다.

### 타임아웃과 재시도

작은 모델이라도 응답에 수십 초가 걸릴 수 있습니다. `spring.ai.ollama.chat.options`에는 타임아웃 항목이 없으므로, Spring Boot의 HTTP 클라이언트 설정으로 읽기 타임아웃을 늘립니다.

```yaml
spring:
  # LLM 응답이 느릴 수 있으므로 HTTP 클라이언트 읽기 타임아웃을 5분으로 늘린다 (Ollama·RestClient 공통)
  http:
    client:
      read-timeout: 300s
```

이 값은 Spring Boot가 자동 설정한 `RestClient.Builder`로 만든 클라이언트에만 적용됩니다. Ollama 호출과, 공공데이터 포털을 부르는 `ApiClientService`가 여기에 해당합니다. `ApiClientService`가 `RestClient.create()` 대신 생성자로 `RestClient.Builder`를 주입받는 이유입니다(Step 6).

Spring AI는 LLM 호출이 실패하면 자동으로 다시 시도합니다. 기본값(최대 10회, 대기 간격이 점점 늘어남)으로는 LLM 서버가 꺼져 있을 때 응답이 수십 분 멈출 수 있어, 실습에 맞게 줄였습니다.

```yaml
# Spring AI 재시도 설정
# 기본값(최대 10회, 대기 간격 5배씩 증가)은 LLM 서버가 꺼져 있을 때 응답이 수십 분 멈추므로 실습용으로 줄인다
spring.ai.retry:
  max-attempts: 2
  backoff:
    initial-interval: 1s
    max-interval: 5s
```

### 확인하기

Ollama 서버가 응답하는지, 모델이 준비되어 있는지 확인합니다.

```bash
curl http://ollama.java21.net/api/tags
```

응답 JSON의 `models` 목록에 `qwen2.5:3b-instruct-q4_K_M`이 있으면 됩니다. 내 컴퓨터에서 Ollama를 쓴다면 `ollama pull qwen2.5:3b-instruct-q4_K_M`으로 모델을 내려받고 `base-url`을 `http://localhost:11434`로 바꿉니다.

---

## Step 1-3: Gemini 설정

### 왜 필요한가

작은 Ollama 모델은 무료지만 느리고, 여러 단계를 계획하거나 Tool을 고르는 판단이 약합니다. **Gemini**는 Google의 클라우드 LLM으로, 빠르고 판단이 안정적입니다. 두 모델을 모두 설정해 두면 화면에서 요청마다 모델을 골라 결과와 속도를 비교할 수 있습니다.

### API 키 발급

1. [Google AI Studio](https://aistudio.google.com/app/apikey)에 접속합니다.
2. **Create API Key**를 누르고 프로젝트를 고릅니다.
3. 발급된 키를 복사해 둡니다. 키는 다음 절에서 환경 변수로 넘깁니다.

### 어떻게 하는가

파일: `src/main/resources/application.yml`

```yaml
# Spring AI Google GenAI 설정 (Gemini)
spring.ai.google:
  genai:
    # 키가 없어도 앱이 시작되도록 기본값을 둔다. 이 값이면 Gemini를 고를 때 안내 메시지를 보여 준다. (ChatClientProvider)
    api-key: ${GOOGLE_GENAI_API_KEY:not-set}
    chat:
      options:
        model: gemini-2.5-flash
        temperature: 0.0
        # 계획(JSON)과 추천 답변이 잘리지 않도록 넉넉하게 둔다
        max-output-tokens: 1024
        top-p: 0.9
        top-k: 40
```

**코드 읽기**

- `api-key: ${GOOGLE_GENAI_API_KEY:not-set}`: 환경 변수 `GOOGLE_GENAI_API_KEY`를 읽고, 없으면 `not-set`을 넣습니다.
- `model: gemini-2.5-flash`: 빠르고 저렴한 Gemini 모델입니다. 더 정확한 `gemini-2.5-pro`도 있습니다.
- `max-output-tokens: 1024`: Gemini는 출력 상한 옵션 이름이 Ollama(`max-tokens`)와 다릅니다. Orchestrator-Workers 패턴의 작업 계획(JSON)이 잘리지 않을 만큼 둡니다(Step 11).

키가 비어 있으면 Gemini 자동 설정이 빈을 만들지 못해 애플리케이션이 시작되지 않을 수 있습니다. 그래서 `not-set`이라는 자리표시 값을 넣어 키 없이도 Ollama만으로 실습할 수 있게 했습니다. 화면에서 Gemini를 고르면 `ChatClientProvider`(Step 2)가 이 값을 보고 다음 안내를 돌려줍니다.

```text
Gemini API 키가 설정되지 않았습니다. 환경 변수 GOOGLE_GENAI_API_KEY를 설정하거나 Ollama를 선택하세요.
```

### 확인하기

키를 설정했다면 Google API가 키를 받아 주는지 직접 확인할 수 있습니다.

```bash
curl "https://generativelanguage.googleapis.com/v1beta/models?key=$GOOGLE_GENAI_API_KEY" | head -20
```

모델 목록이 나오면 키가 유효합니다. `API key not valid` 오류가 나오면 키를 다시 확인하세요.

---

## Step 1-4: 환경 변수로 API 키 관리

### 왜 필요한가

`application.yml`에 키를 직접 적으면 GitHub에 코드를 올릴 때 키도 함께 공개됩니다. 그러면 누구나 내 계정으로 API를 쓸 수 있습니다. 그래서 키는 파일 대신 환경 변수로 넘깁니다. `${이름:기본값}`은 Spring의 프로퍼티 플레이스홀더(placeholder)로, 실행할 때 환경 변수 값으로 바뀌고 콜론 뒤는 값이 없을 때의 기본값입니다.

이 프로젝트가 쓰는 환경 변수는 두 개입니다.

| 환경 변수 | 필수 여부 | 용도 | 없을 때 |
|-----------|-----------|------|---------|
| `DATA_GO_KR_SERVICE_KEY` | 필수 | 공공데이터 포털 국내 항공 운항 정보 API 서비스 키 | 앱은 시작됨. 항공편을 조회할 때 "공공데이터 포털 서비스 키가 설정되지 않았습니다. 환경 변수 DATA_GO_KR_SERVICE_KEY를 설정하세요." |
| `GOOGLE_GENAI_API_KEY` | 선택 | Gemini API 키 | 앱은 시작됨. Gemini를 고르면 안내 메시지 |

`DATA_GO_KR_SERVICE_KEY`는 `application.yml`의 `data-go-kr.api.service-key: ${DATA_GO_KR_SERVICE_KEY:}`로 읽습니다. 키 발급 방법은 Step 6에서 다룹니다.

### 어떻게 하는가

**macOS/Linux 터미널**

```bash
export DATA_GO_KR_SERVICE_KEY=your-service-key
export GOOGLE_GENAI_API_KEY=your-api-key   # 선택
./mvnw spring-boot:run
```

**Windows PowerShell**

```powershell
$env:DATA_GO_KR_SERVICE_KEY="your-service-key"
$env:GOOGLE_GENAI_API_KEY="your-api-key"
.\mvnw.cmd spring-boot:run
```

**IntelliJ IDEA**: **Run > Edit Configurations**의 **Environment variables**에 다음처럼 적습니다.

```text
DATA_GO_KR_SERVICE_KEY=your-service-key;GOOGLE_GENAI_API_KEY=your-api-key
```

**.env 파일**: 프로젝트 루트에 `.env`를 만들고 `이름=값` 형식으로 적습니다. Spring Boot는 `.env`를 자동으로 읽지 않으므로 `application.yml`에 `spring.config.import: optional:file:.env[.properties]`를 추가해야 합니다.

```properties
DATA_GO_KR_SERVICE_KEY=your-service-key
GOOGLE_GENAI_API_KEY=your-api-key
```

> **주의**: 현재 `.gitignore`에는 `.env`가 들어 있지 않습니다. `.env`를 쓴다면 먼저 `.gitignore`에 `.env` 한 줄을 추가한 뒤 파일을 만드세요.

배포 서버에서도 같은 방식을 씁니다. GitHub Actions 워크플로(`.github/workflows/maven.yml`)가 GitHub Secrets의 `DATA_GO_KR_SERVICE_KEY`를 서버에 환경 변수로 넘기고, `deploy/startup.sh`가 서버의 `~/fly-schedule/.env`에서 선택 값(`GOOGLE_GENAI_API_KEY` 등)을 읽은 뒤 애플리케이션을 실행합니다. 키는 어디에도 파일로 커밋되지 않습니다.

### 확인하기

```bash
echo $DATA_GO_KR_SERVICE_KEY
```

값이 출력되면 같은 터미널에서 실행한 애플리케이션이 그 값을 받습니다. 환경 변수는 설정한 터미널(또는 실행 구성)에만 적용된다는 점에 주의하세요.

---

## Step 1-5: 실행과 확인

### 어떻게 하는가

전체 설정은 `src/main/resources/application.yml`에 있습니다. 앞에서 본 블록 외에 서버 포트(`server.port: 8080`), Tomcat 타임아웃, 공공데이터 포털 주소(`data-go-kr.api.url`)가 들어 있습니다. 파일을 열어 각 블록이 어느 절의 내용인지 짚어 보세요.

```bash
./mvnw clean package -DskipTests
./mvnw spring-boot:run
```

### 확인하기

1. 콘솔에 `Tomcat started on port 8080`과 `Started FlyscheduleApplication`이 차례로 나오는지 확인합니다.
2. 브라우저에서 `http://localhost:8080`을 엽니다. 세 가지 검색 패턴을 비교하는 화면이 나오면 성공입니다.
3. Gemini 키 없이 실행했다면, Gemini를 골랐을 때의 안내를 API로 확인해 봅니다. 영어 문장은 정규식으로 처리되지 않아 LLM이 필요하므로 키 검사가 바로 일어납니다(Step 2에서 자세히 다룹니다).

```bash
curl -X POST http://localhost:8080/api/search/coordinator \
  -H "Content-Type: application/json" \
  -d '{"message": "flights from Gwangju to Jeju tomorrow", "model": "gemini"}'
```

```json
{
  "pattern": "coordinator",
  "success": false,
  "message": "Gemini API 키가 설정되지 않았습니다. 환경 변수 GOOGLE_GENAI_API_KEY를 설정하거나 Ollama를 선택하세요.",
  "results": [],
  "trace": [
    { "order": 1, "kind": "CODE", "actor": "SearchCriteriaExtractor", "title": "검색 조건 추출", "detail": "실패: Gemini API 키가 설정되지 않았습니다. ...", ... }
  ],
  "elapsedMs": 3
}
```

---

## Ollama와 Gemini 비교

| 항목 | Ollama (qwen2.5 3B) | Gemini (2.5 Flash) |
|------|---------------------|--------------------|
| 실행 위치 | 내 컴퓨터 또는 실습 서버 | Google 클라우드 |
| 비용 | 무료 | 무료 사용량을 넘으면 유료 |
| 응답 속도 | 서버 사양에 따라 수 초~수십 초 | 대개 수 초 |
| Tool 선택·계획 능력 | 약함 (Tool을 건너뛰거나 인자를 틀리기도 함) | 안정적 |
| 이 교재에서의 용도 | 기본 실습 | 세 패턴의 결과 비교, 복잡한 요청 |

> **생각해 보기**: 같은 문장을 두 모델로 처리했을 때 결과가 다르다면, 그 차이는 코드 때문일까요, 모델 때문일까요? Step 9~11에서 세 패턴을 비교하면서 다시 확인합니다.

---

## 일반적인 문제 해결

### 의존성을 내려받지 못함

```text
Could not resolve dependency for org.springframework.ai:...
```

1. BOM 버전(1.1.4)과 스타터 `artifactId` 철자를 확인합니다.
2. Milestone·RC 버전을 쓰고 있다면 `spring-milestones` 저장소가 필요합니다.
3. `./mvnw clean package -U`로 다시 받습니다. IntelliJ에서는 Maven 탭의 Reload를 누릅니다.

### Ollama에 연결할 수 없음

```text
I/O error on POST request for "http://ollama.java21.net/api/chat": Connection refused
```

화면과 API 응답에는 `ChatClientProvider.describeError()`가 바꾼 문장 "LLM 서버에 연결할 수 없습니다. Ollama가 실행 중인지(spring.ai.ollama.base-url) 확인하거나 다른 모델을 선택하세요."가 나옵니다.

1. `curl <base-url>/api/tags`로 서버가 응답하는지 확인합니다.
2. 로컬 Ollama라면 `base-url`이 `http://localhost:11434`인지 확인합니다.
3. 실패 응답이 아주 늦게 온다면 `spring.ai.retry` 설정이 빠지지 않았는지 확인합니다.

### Gemini를 고르면 "API 키가 설정되지 않았습니다"가 나옴

오류가 아니라 의도한 동작입니다. `GOOGLE_GENAI_API_KEY`가 없어 `api-key`가 `not-set`이 되었습니다. Ollama만 쓴다면 그대로 두고, Gemini를 쓰려면 환경 변수를 설정한 뒤 애플리케이션을 다시 시작합니다.

### Gemini 키가 거부됨

`API key not valid` 또는 `API key was reported as leaked` 오류가 나오면 [Google AI Studio](https://aistudio.google.com/app/apikey)에서 기존 키를 지우고 새로 발급한 뒤 환경 변수를 바꿉니다. 키가 공개 저장소에 올라가면 Google이 자동으로 차단하기도 합니다.

### NoSuchMethodError 등 버전 충돌

```text
NoSuchMethodError: org.springframework.ai...
```

Spring Boot가 3.5.x인지, Spring AI 모듈이 모두 BOM(1.1.4)을 따르는지 확인합니다. 스타터에 `<version>`을 따로 적었다면 지웁니다.

---

## 실습 과제

### 과제 1 (기본): 키 없이 실행해 보기

`DATA_GO_KR_SERVICE_KEY`만 설정하고 `GOOGLE_GENAI_API_KEY`는 설정하지 않은 채 애플리케이션을 실행합니다. 화면에서 Gemini를 고르고 영어 문장(`flights from Gwangju to Jeju tomorrow`)으로 검색해 봅니다.

- 힌트: `application.yml`의 `spring.ai.google.genai.api-key`, `ChatClientProvider.get()`
- 완료 기준: 애플리케이션이 정상 시작되고, 화면에 "Gemini API 키가 설정되지 않았습니다..." 안내가 나온다.

### 과제 2 (기본): 로컬 Ollama로 바꾸기

[Ollama](https://ollama.com/)를 설치하고 `ollama pull qwen2.5:3b-instruct-q4_K_M`으로 모델을 받은 뒤, `spring.ai.ollama.base-url`을 `http://localhost:11434`로 바꿔 실행합니다.

- 힌트: `curl http://localhost:11434/api/tags`로 모델이 보이는지 먼저 확인합니다.
- 완료 기준: 영어 문장으로 Coordinator 검색을 했을 때 응답 `trace`의 첫 단계 `kind`가 `LLM`이고 `success`가 `true`다.

### 과제 3 (도전): 재시도 설정의 효과 재기

`base-url`을 존재하지 않는 주소(예: `http://localhost:9999`)로 바꾸고 영어 문장으로 검색해, 실패 응답까지 걸린 시간을 잽니다. 다음으로 `spring.ai.retry` 블록을 주석 처리하고 다시 잽니다.

- 힌트: `curl ... -w "\n응답 시간: %{time_total}초\n"`
- 완료 기준: 두 경우의 시간을 표로 적고, 이 프로젝트가 재시도를 줄인 이유를 한두 문장으로 설명한다. 실험 뒤 설정을 원래대로 되돌린다.

### 과제 4 (도전): .env 파일로 키 읽기

`.gitignore`에 `.env`를 추가하고, `application.yml`에 `spring.config.import: optional:file:.env[.properties]`를 넣어 `.env`에서 두 키를 읽도록 바꿉니다.

- 힌트: 터미널에서 `unset DATA_GO_KR_SERVICE_KEY`로 환경 변수를 지운 뒤 실행해야 `.env`에서 읽는지 확인할 수 있습니다.
- 완료 기준: 환경 변수 없이 실행해도 한국어 문장 검색(`내일 광주에서 제주`)이 성공하고, `git status`에 `.env`가 나오지 않는다.

---

## 요약

- Spring AI는 `ChatModel`(제공자별 구현)과 `ChatClient`(공통 API)로 LLM 제공자의 차이를 감춥니다.
- `pom.xml`에 Spring AI BOM(1.1.4)과 Ollama·Gemini 스타터를 추가하면 `ChatModel` 빈이 자동으로 만들어집니다.
- `application.yml`에 모델·옵션을 적고, 읽기 타임아웃(300초)과 재시도(최대 2회)를 실습에 맞게 조정했습니다.
- API 키는 환경 변수로 넘기며, 키가 없어도 앱은 시작되고 해당 기능에서 안내 메시지를 보여 줍니다.

이제 설정된 LLM에 실제로 말을 걸 차례입니다. Step 2에서 `ChatClient`를 만들어 첫 LLM 호출을 해 봅니다.

---

## 체크리스트

- [ ] LLM, 토큰, 프롬프트, 컨텍스트 윈도우, temperature의 뜻을 한 문장씩 설명할 수 있다. (목표 1)
- [ ] `pom.xml`에 BOM과 스타터 두 개를 추가하고 `dependency:tree`로 확인했다. (목표 2)
- [ ] `application.yml`의 Ollama·Gemini 설정과 `spring.http.client.read-timeout`, `spring.ai.retry`의 의미를 설명할 수 있다. (목표 3)
- [ ] 환경 변수로 `DATA_GO_KR_SERVICE_KEY`를 넘겼고, `GOOGLE_GENAI_API_KEY`가 없을 때의 안내를 확인했다. (목표 4)
- [ ] 애플리케이션을 실행해 기동 로그와 `http://localhost:8080` 화면을 확인했다. (목표 5)

---

## 다음 단계

[Step 2: ChatClient 기초와 LLM 연동](step-2-chatclient-basics.md)에서 `ChatClient` 빈을 만들고, 요청마다 모델을 골라 LLM을 호출합니다.

---

## 참고 자료

- [Spring AI 공식 문서](https://docs.spring.io/spring-ai/reference/)
- [Spring AI 1.1.4 릴리스 노트](https://github.com/spring-projects/spring-ai/releases/tag/v1.1.4)
- [Ollama 공식 사이트](https://ollama.com/)
- [Google AI Studio](https://aistudio.google.com/)
- [Gemini API 문서](https://ai.google.dev/gemini-api/docs)

---

## 심화 학습

### 관찰 가능성(Observability)

Spring AI는 Micrometer Observation으로 `ChatClient`, `ChatModel`, Tool 호출의 실행 시간과 토큰 사용량을 메트릭·트레이스로 남깁니다. Spring Boot Actuator를 추가하면 `/actuator/metrics`에서 `gen_ai.client.operation` 같은 지표를 볼 수 있습니다. 이 프로젝트는 Actuator 대신 응답의 `trace`(실행 기록)로 단계별 시간을 보여 줍니다(Step 9~11).

### 모델을 하나만 쓰고 싶다면

스타터를 하나만 남기면 `ChatModel` 빈도 하나만 생깁니다. 다만 이 프로젝트의 `ChatClientConfig`와 `ChatClientProvider`는 두 빈을 모두 주입받으므로 함께 고쳐야 합니다. 두 모델을 동시에 두는 구성이 어떤 점에서 편한지 Step 2를 읽으며 생각해 보세요.
