# Step 1: Spring AI 환경 설정

## 학습 목표

이 단계를 마치면 다음을 할 수 있습니다:

- Spring AI 의존성을 `pom.xml`에 추가하고 BOM으로 버전을 관리할 수 있습니다
- `application.yml`을 통해 Ollama(로컬 LLM)와 Gemini(클라우드 LLM)를 설정할 수 있습니다
- API 키를 환경 변수로 안전하게 관리할 수 있습니다
- Spring AI 프로젝트를 실행하고 정상 동작을 확인할 수 있습니다

---

> **핵심 개념**: Spring AI는 Spring Boot 애플리케이션에서 AI 모델(LLM)을 쉽게 사용할 수 있게 해주는 프레임워크입니다. 쉽게 말해, 데이터베이스에 연결할 때 `application.yml`에 DB 설정을 하듯이, AI 모델에 연결할 때도 `application.yml`에 설정만 하면 된다고 생각하면 됩니다. Spring이 JDBC로 데이터베이스를 추상화하듯, Spring AI는 ChatClient로 서로 다른 AI 모델들을 통일된 방식으로 사용할 수 있게 해줍니다.

---

## 전체 흐름

이번 단계에서 구성할 환경의 전체 구조는 다음과 같습니다:

```text
[application.yml 설정]
       ↓
[Spring AI 자동 설정 (Auto-Configuration)]
       ↓
┌──────────────────────────────────┐
│     Spring 컨테이너 (IoC)        │
│  ┌────────────┐  ┌────────────┐  │
│  │ Ollama     │  │ Gemini     │  │
│  │ ChatModel  │  │ ChatModel  │  │
│  └────────────┘  └────────────┘  │
└──────────────────────────────────┘
       ↓
[ChatClientConfig에서 ChatClient 빈 생성] (Step 2에서 학습)
       ↓
[Controller → Service → ChatClient → AI 모델 응답] (Step 2~4에서 학습)
```

---

## LLM이란 무엇인가요?

**LLM**(Large Language Model, 대규모 언어 모델)은 방대한 텍스트 데이터로 학습한 인공지능 모델입니다.

### LLM의 기본 개념

**1. LLM이란 무엇인가?**

```text
일반적인 프로그램:  개발자가 규칙을 작성 → 프로그램이 규칙대로 동작
LLM:              방대한 데이터로 학습 → 모델이 스스로 패턴을 학습

예시:
- "스프링이 뭐야?" → LLM이 학습한 데이터에서 스프링 관련 내용을 찾아 답변
- "내일 광주에서 제주로 가는 항공편 알려줘" → LLM이 의도를 파악하고 응답
```

**2. LLM은 어떻게 동작하나요?**

```text
입력: "내일 광주에서 제주로 가는 항공편 알려줘"
  ↓
토큰화 (Tokenization): ["내일", "광주", "에서", "제주", "로", "가는", "항공편", ...]
  ↓
모델 처리: 각 토큰 사이의 관계를 분석하여 다음 토큰 예측
  ↓
출력: "네, 내일 광주에서 제주로 가는 항공편은..."
```

**3. 주요 용어 설명**

| 용어 | 설명 | 예시 |
|------|------|------|
| **토큰 (Token)** | LLM이 처리하는 최소 단위 | "안녕하세요" → ["안녕", "하", "세요"] (약 3-4토큰) |
| **프롬프트 (Prompt)** | LLM에게 전달하는 입력 | "스프링이 뭐야?" |
| **컨텍스트 (Context)** | 대화의 맥락 정보 | 이전 대화 기록, 시스템 지시사항 |
| **응답 (Completion)** | LLM이 생성하는 답변 | "스프링은 자바 프레임워크입니다..." |

### LLM의 파라미터 이해하기

**Temperature (온도)**

LLM의 창의성을 조절하는 파라미터입니다.

```text
Temperature = 0.0 (낮음)
→ 항상 비슷한 답변 (결정적)
→ 예: 질문에 대해 사실 위주의 답변
→ 사용: 검색, 분류, Function Calling

Temperature = 1.0 (높음)
→ 매번 다른 답변 (창의적)
→ 예: 창작, 아이디어 생성
→ 사용: 블로그 글 작성, 스토리 생성
```

**Max Tokens (최대 토큰 수)**

LLM이 생성할 수 있는 최대 토큰 수입니다.

```text
Max Tokens = 256
→ 약 200글자 정도의 답변
→ 사용: 간단한 질문-답변

Max Tokens = 4096
→ 약 3000글자 정도의 답변
→ 사용: 장문의 설명, 코드 생성
```

**Top-P, Top-K**

다음 토큰을 선택할 때 고려할 후보 수입니다.

```text
Top-K = 40: 상위 40개 후보 중에서 선택
Top-P = 0.9: 누적 확률 90%까지 후보 고려

일반적으로 기본값 사용 (Ollama 기본값: Top-K 40, Top-P 0.9)
```

### Spring에서 LLM 사용하기

**Spring AI는 무엇을 해주나요?**

```text
┌─────────────────────────────────────────┐
│  Spring 없이 LLM 사용                   │
│  - 각 LLM 회사의 SDK를 직접 학습        │
│  - OpenAI SDK, Gemini SDK, Ollama SDK   │
│  - 코드 중복, 모델 교체 어려움          │
└─────────────────────────────────────────┘

↓ Spring AI 도입

┌─────────────────────────────────────────┐
│  Spring AI로 LLM 사용                   │
│  - 통일된 API (ChatClient)              │
│  - Spring Bean으로 LLM 관리             │
│  - 설정 파일로 모델 전환                │
│  - 익숙한 Spring 방식 그대로 사용       │
└─────────────────────────────────────────┘
```

**Spring의 DI 컨테이너와 LLM**

```java
// 이미 알고 있는 Spring 방식
@Service
public class UserService {
    private final UserRepository repo;  // DI로 주입
}

// Spring AI도 동일한 방식
@Service
public class ChatService {
    private final ChatClient chatClient;  // DI로 주입
}
```

---

## Spring AI란?

**Spring AI**는 Spring 생태계에서 AI 모델(LLM)을 쉽게 사용할 수 있게 해주는 프레임워크입니다.

### 왜 Spring AI인가?

| 기존 방식 | Spring AI |
|----------|-----------|
| 각 LLM 제공자별 SDK를 직접 학습 | 통일된 API로 모든 LLM 사용 |
| OpenAI SDK, Gemini SDK 등 중복 코드 | ChatClient 하나로 모두 해결 |
| 모델 교체 시 코드 대폭 수정 | ChatModel만 교체하면 완료 |
| 비즈니스 로직과 LLM 호출 코드 혼재 | Spring의 DI와 Advisor로 깔끔하게 분리 |

### Spring AI의 기술적 배경

**1. 추상화 계층 (Abstraction Layer)**

```text
┌─────────────────────────────────────────┐
│         Spring AI Application           │
├─────────────────────────────────────────┤
│    ChatClient (통일된 인터페이스)       │
├─────────────────────────────────────────┤
│    ChatModel (추상화 계층)              │
│  ┌────────┐  ┌────────┐  ┌────────┐     │
│  │Ollama  │  │Gemini  │  │OpenAI  │     │
│  │구현체  │  │구현체  │  │구현체  │     │
│  └────────┘  └────────┘  └────────┘     │
└─────────────────────────────────────────┘
```

**2. 종속성 주입 (Dependency Injection)**

Spring AI는 Spring Framework의 DI 컨테이너를 활용하여 LLM 관련 빈(Bean)을 관리합니다. 이로 인해 다음과 같은 이점을 얻습니다:

- **느슨한 결합**: 구체적인 LLM 구현체에 의존하지 않음
- **쉬운 테스트**: Mock ChatModel로 쉽게 테스트 가능
- **환경별 설정**: 개발(로컬) vs 운영(클라우드) 쉽게 전환

**3. Advisor와 Observability**

Spring AI는 Spring AOP 프록시 대신 자체 메커니즘으로 공통 관심사를 처리합니다:

- **Advisor**: ChatClient 요청/응답 전후에 끼어드는 체인입니다. `SimpleLoggerAdvisor`로 요청/응답을 로깅하거나, 직접 만든 Advisor로 공통 처리를 추가합니다 (Step 4에서 학습)
- **Observability**: Micrometer Observation을 기반으로 ChatClient, ChatModel, Tool 호출의 실행 시간과 토큰 사용량을 메트릭·트레이스로 수집합니다 (Spring Boot Actuator와 함께 사용)

### 핵심 기능

| 기능 | 설명 |
|------|------|
| **ChatClient** | LLM과 대화하는 인터페이스 |
| **ChatModel** | 다양한 LLM 제공자(Ollama, Gemini, OpenAI 등) 통합 |
| **Function Calling** | LLM이 자바 메서드를 호출할 수 있게 함 |
| **Advisor** | ChatClient 요청/응답 전후에 로깅·모니터링 같은 공통 로직 적용 |

### Spring AI 1.1.4 특징

- Spring Boot 3.4.x / 3.5.x 지원 (이 프로젝트는 3.5.11)
- 공식 `@Tool`, `@ToolParam` 어노테이션 제공
- 다중 LLM 동시 사용 가능
- `CallAdvisor`/`StreamAdvisor` 기반 Advisor API로 요청/응답 가로채기 지원

---

## Step 1-1: pom.xml 설정

### 왜 이렇게 할까요?

Spring Boot 프로젝트에서 새로운 기능을 추가할 때 가장 먼저 하는 일이 무엇인지 떠올려 보세요. 바로 `pom.xml`에 의존성을 추가하는 것입니다. JPA를 쓸 때 `spring-boot-starter-data-jpa`를 추가하듯, AI 기능을 쓸 때도 `spring-ai-starter` 의존성을 추가해야 합니다.

Spring AI 1.1.4는 정식 릴리스(GA) 버전이므로 Maven 중앙 저장소에서 바로 내려받을 수 있습니다. 또한 Spring AI는 여러 모듈(Ollama, Gemini 등)로 구성되어 있어, BOM(Bill of Materials)으로 버전을 한 곳에서 관리하는 것이 좋습니다.

> **Spring Boot 복습**: BOM(Bill of Materials)은 여러 의존성의 버전을 한 번에 관리하는 방법입니다. Spring Boot에서도 `spring-boot-dependencies` BOM을 사용하여 수십 개의 의존성 버전을 통일합니다. Spring AI도 동일한 방식으로 버전을 관리합니다.

### Spring AI BOM(Bill of Materials) 추가

Spring AI는 BOM을 통해 모든 모듈의 버전을 관리합니다. `pom.xml`의 `<properties>`와 `<dependencyManagement>`에 추가:

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

### (선택) Spring Milestone Repository 추가

1.1.4 같은 정식 릴리스는 Maven 중앙 저장소에서 받으므로 이 설정이 필요 없습니다(실제 프로젝트의 `pom.xml`에도 없습니다). Milestone(M)이나 RC 버전을 사용할 때만 다음 저장소를 추가합니다:

```xml
<repositories>
    <repository>
        <id>spring-milestones</id>
        <name>Spring Milestones</name>
        <url>https://repo.spring.io/milestone</url>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
    </repository>
</repositories>
```

### Ollama 의존성 추가

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-ollama</artifactId>
</dependency>
```

### Google GenAI (Gemini) 의존성 추가

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-google-genai</artifactId>
</dependency>
```

---

## Step 1-2: Ollama 설정

### 왜 이렇게 할까요?

지금까지 `application.yml`에 데이터베이스 연결 정보를 설정해 본 적이 있을 것입니다. AI 모델도 데이터베이스와 마찬가지로 "어떤 서버에 연결할지"와 "어떤 설정으로 사용할지"를 `application.yml`에 적어야 합니다. Ollama는 무료로 로컬에서 실행할 수 있는 LLM이므로, 개발 단계에서 테스트하기에 적합합니다.

> **Spring Boot 복습**: Spring Boot의 자동 설정(Auto-Configuration)은 `application.yml`에 작성된 설정값을 읽어서 필요한 Bean을 자동으로 생성합니다. 예를 들어 `spring.datasource.url`을 적으면 `DataSource` Bean이 자동 생성되듯, Ollama 스타터가 클래스패스에 있으면 `spring.ai.ollama.*` 설정값(예: `base-url`, 기본값 `http://localhost:11434`)으로 `OllamaChatModel` Bean이 자동 생성됩니다.

### Ollama란?

**Ollama**는 로컬 컴퓨터에서 LLM을 실행할 수 있는 오픈소스 도구입니다.

| 특징 | 설명 |
|------|------|
| **비용** | 무료 |
| **속도** | 느림 (30~60초) |
| **용도** | 개발, 테스트 |
| **모델** | Llama, Qwen, Mistral 등 |

### application.yml 설정

Spring을 공부하신 분이라면 `application.yml`이 익숙하실 겁니다. LLM 설정도 동일한 방식으로 합니다.

```yaml
spring.ai.ollama:
  base-url: http://ollama.java21.net  # Ollama 서버 주소
  chat:
    options:
      model: qwen2.5:3b-instruct-q4_K_M  # 사용할 모델 (양자화된 Qwen 2.5 3B)
      temperature: 0.0                    # 응답 일관성
      max-tokens: 1024                    # 최대 출력 토큰 수 (Ollama의 num_predict)
      num-thread: 4                       # 스레드 수
      num-ctx: 4096                       # 컨텍스트 윈도우 크기 (Tool 7개 설명과 결과가 들어가도록)
      top-p: 0.9                          # Nucleus sampling
      top-k: 40                           # 상위 K 후보 고려
      keep-alive: 10m                     # 모델 유지 시간
```

### 설정 항목 상세 설명

**1. base-url (Ollama 서버 주소)**

```yaml
base-url: http://ollama.java21.net
```

- **의미**: Ollama LLM 서버의 주소
- **로컬 사용**: `http://localhost:11434`
- **원격 사용**: `http://ollama.java21.net` (프로젝트에서 사용)
- **Spring 설정과의 비교**:
  ```yaml
  # 데이터베이스 설정
  spring:
    datasource:
      url: jdbc:mysql://localhost:3306/mydb

  # Ollama 설정 (비슷한 구조!)
  spring:
    ai:
      ollama:
        base-url: http://localhost:11434
  ```

**2. model (사용할 모델)**

```yaml
model: qwen2.5:3b-instruct-q4_K_M
```

- **의미**: 사용할 LLM 모델 이름
- **Qwen 2.5**: 중국어, 한국어에 강한 오픈소스 모델
- **3b**: 3B(30억) 파라미터 모델
- **instruct**: Instruction 튜닝이 적용된 모델
- **q4_K_M**: 4비트 양자화(K-Quant Medium)로 메모리 사용량 최적화
- **다른 모델 예시**:
  - `llama3.2`: Meta의 Llama 모델
  - `mistral`: Mistral AI의 모델

**3. temperature (응답 창의성)**

```yaml
temperature: 0.0
```

- **범위**: 0.0 ~ 2.0
- **0.0에 가까울수록**: 항상 비슷한 답변 (결정적)
- **2.0에 가까울수록**: 매번 다른 답변 (창의적)
- **추천 설정**:
  - Function Calling: `0.0` (일관된 결과 필요)
  - 질문-답변: `0.7` (적절한 균형)
  - 창작: `1.0+` (창의적 응답)

### 파라미터 설명

| 파라미터 | 값 | 설명 |
|----------|-----|------|
| `base-url` | `http://ollama.java21.net` | Ollama 서버 주소 (로컬: `http://localhost:11434`) |
| `model` | `qwen2.5:3b-instruct-q4_K_M` | Qwen 2.5 3B 양자화 모델 (한국어 지원 우수) |
| `temperature` | `0.0` | 결정적 응답 (0.0~2.0, 낮을수록 일관적) |
| `max-tokens` | `1024` | 최대 출력 토큰 수 (`num-predict`와 같은 값) |
| `num-thread` | `4` | Ollama 실행 스레드 수 |
| `num-ctx` | `4096` | 컨텍스트 윈도우 크기 (토큰 수). Tool 7개의 설명과 Tool 결과가 함께 들어가야 하므로 넉넉하게 둠 |
| `top-p` | `0.9` | 누적 확률 분포 상위 90%만 고려 |
| `top-k` | `40` | 다음 토큰 후보 상위 40개만 고려 |
| `keep-alive` | `10m` | 모델 메모리 유지 시간 (10분) |

### 타임아웃과 재시도 설정

`spring.ai.ollama.chat.options`에는 `timeout` 항목이 없습니다. 작은 모델이라도 응답에 수십 초가 걸릴 수 있으므로, 이 프로젝트는 Spring Boot의 HTTP 클라이언트 설정으로 읽기 타임아웃을 늘립니다. 이 값은 Spring Boot가 자동 설정한 `RestClient.Builder`로 만든 클라이언트에 적용됩니다. Ollama 호출과, 공공데이터 포털을 부르는 `ApiClientService`가 모두 여기에 해당합니다. (`ApiClientService`가 `RestClient.create()` 대신 생성자로 `RestClient.Builder`를 주입받는 이유입니다. `RestClient.create()`로 직접 만들면 이 설정이 적용되지 않습니다. Step 6 참고)

```yaml
spring:
  # LLM 응답이 느릴 수 있으므로 HTTP 클라이언트 읽기 타임아웃을 5분으로 늘린다 (Ollama·RestClient 공통)
  http:
    client:
      read-timeout: 300s
```

Spring AI는 LLM 호출이 실패하면 자동으로 다시 시도합니다. 기본값은 최대 10회, 대기 간격을 5배씩 늘리는 방식이라 LLM 서버가 꺼져 있으면 응답이 수십 분 동안 멈출 수 있습니다. 실습에서는 실패를 빨리 확인하는 편이 낫기 때문에 재시도 횟수와 간격을 줄였습니다.

```yaml
# Spring AI 재시도 설정
spring.ai.retry:
  max-attempts: 2          # 최대 2회 시도
  backoff:
    initial-interval: 1s   # 첫 재시도 대기 시간
    max-interval: 5s       # 최대 대기 시간
```

---

## Step 1-3: Gemini 설정

### 왜 이렇게 할까요?

Ollama는 무료이지만 응답 속도가 느립니다(30~60초). 실제 서비스에서는 사용자가 1분을 기다릴 수 없습니다. Google Gemini는 클라우드 기반 LLM으로 3~5초 만에 응답하여 운영 환경에 적합합니다. 두 가지 LLM을 모두 설정해 두면, 개발할 때는 Ollama를, 실제 서비스에서는 Gemini를 선택적으로 사용할 수 있습니다.

### Google GenAI API란?

**Google Gemini**는 Google의 클라우드 기반 LLM 서비스입니다.

| 특징 | 설명 |
|------|------|
| **비용** | 유료 (저렴) |
| **속도** | 빠름 (3~5초) |
| **용도** | 실시간 검색, 운영 환경 |
| **모델** | gemini-2.5-flash, gemini-2.5-pro |

### API 키 발급

1. [Google AI Studio](https://aistudio.google.com/app/apikey) 접속
2. **Create API Key** 클릭
3. 프로젝트 선택 또는 생성
4. 발급된 키 복사

> **보안 주의**: API 키를 GitHub에 커밋하지 마세요!

### application.yml 설정

```yaml
spring:
  ai:
    google:
      genai:
        # 환경 변수에서 읽음. 없으면 "not-set"을 넣어 앱이 그대로 시작되게 한다
        api-key: ${GOOGLE_GENAI_API_KEY:not-set}
        chat:
          options:
            model: gemini-2.5-flash       # Gemini 2.5 Flash 모델
            temperature: 0.0              # 결정적 응답
            max-output-tokens: 1024       # 최대 출력 토큰 수 (계획 JSON과 답변이 잘리지 않도록)
            top-p: 0.9                    # Nucleus sampling
            top-k: 40                     # 상위 K 후보 고려
```

### 파라미터 상세 설명

| 파라미터 | 값 | 설명 | 범위 |
|----------|-----|------|------|
| `model` | `gemini-2.5-flash` | 빠르고 저렴한 모델 | `gemini-2.5-flash`, `gemini-2.5-pro` |
| `temperature` | `0.0` | 응답의 일관성 (0=결정적, 2=창의적) | `0.0` ~ `2.0` |
| `max-output-tokens` | `1024` | 최대 출력 토큰 수 (영어 기준 1토큰≈4글자, 한국어는 더 적음) | 모델별 상한 (2.5 Flash는 최대 65,536) |
| `top-p` | `0.9` | 누적 확률 분포 상위 90%만 고려 | `0.0` ~ `1.0` |
| `top-k` | `40` | 다음 토큰 후보 상위 40개만 고려 | 1 이상 (모델별 기본값 다름) |

> **기본값을 `not-set`으로 둔 이유**: API 키가 비어 있으면 Gemini 자동 설정이 빈을 만들지 못해 애플리케이션이 시작되지 않을 수 있습니다. 이 프로젝트는 Gemini 키가 없어도 Ollama만으로 실습할 수 있도록 `not-set`이라는 자리표시 값을 넣습니다. 화면에서 Gemini를 선택하면 `ChatClientProvider`가 이 값을 보고 "Gemini API 키가 설정되지 않았습니다. 환경 변수 GOOGLE_GENAI_API_KEY를 설정하거나 Ollama를 선택하세요."라는 안내를 돌려줍니다. (Step 2에서 다룹니다.)

### Temperature 값에 따른 동작

| 값 | 동작 | 사용 사례 |
|----|------|-----------|
| `0.0` ~ `0.3` | 같은 입력에 거의 같은 출력 (완전히 같다는 보장은 없음) | 검색, 분류, Function Calling |
| `0.4` ~ `0.7` | 균형 잡힌 창의성 | 대화, 질문-답변 |
| `0.8` ~ `2.0` | 창의적이고 무작위적 | 창작, 아이디어 생성 |

---

## Step 1-4: 환경 변수로 API 키 관리

### 왜 이렇게 할까요?

`application.yml`에 API 키를 직접 적으면 어떻게 될까요? GitHub에 코드를 올릴 때 API 키가 함께 공개되어 누구나 내 계정으로 Gemini를 사용할 수 있게 됩니다. 이를 방지하기 위해 API 키는 환경 변수로 관리합니다. `application.yml`에서 `${GOOGLE_GENAI_API_KEY:not-set}`처럼 작성하면, Spring이 실행될 때 환경 변수에서 값을 읽어 옵니다. 콜론(`:`) 뒤는 환경 변수가 없을 때의 기본값으로, 실제 키는 파일에 남지 않습니다.

이 프로젝트에서 쓰는 환경 변수는 두 가지입니다.

| 환경 변수 | 필수 여부 | 용도 | 없을 때 |
|-----------|-----------|------|---------|
| `DATA_GO_KR_SERVICE_KEY` | 필수 | 공공데이터 포털 국내 항공 운항 정보 API 키 | 앱은 정상 시작, 항공편 조회 시 "공공데이터 포털 서비스 키가 설정되지 않았습니다…" 안내 (`${DATA_GO_KR_SERVICE_KEY:}` → 빈 값) |
| `GOOGLE_GENAI_API_KEY` | 선택 | Gemini API 키 | 앱은 정상 시작, Gemini 선택 시 안내 메시지 표시 |

> **Spring Boot 복습**: `${...}` 문법은 Spring의 프로퍼티 플레이스홀더(placeholder)입니다. `spring.datasource.password`를 `${DB_PASSWORD}`로 설정해 본 적이 있을 것입니다. API 키도 동일한 방식으로 환경 변수에서 주입합니다.

### 방법 1: 터미널 (macOS/Linux)

```bash
export GOOGLE_GENAI_API_KEY=your-api-key
export DATA_GO_KR_SERVICE_KEY=your-service-key   # 공공데이터 포털 항공편 API 키
./mvnw spring-boot:run
```

### 방법 2: PowerShell (Windows)

```powershell
$env:GOOGLE_GENAI_API_KEY="your-api-key"
$env:DATA_GO_KR_SERVICE_KEY="your-service-key"
.\mvnw.cmd spring-boot:run
```

### 방법 3: IntelliJ IDEA

1. **Run** > **Edit Configurations**
2. **Environment Variables** 필드에:
   ```text
   GOOGLE_GENAI_API_KEY=your-api-key;DATA_GO_KR_SERVICE_KEY=your-service-key
   ```

### 방법 4: .env 파일 (권장)

프로젝트 루트에 `.env` 파일 생성:

```bash
GOOGLE_GENAI_API_KEY=your-api-key
DATA_GO_KR_SERVICE_KEY=your-service-key
```

> Spring Boot는 `.env` 파일을 자동으로 읽지 않습니다. `application.yml`에 `spring.config.import: optional:file:.env[.properties]`를 추가하거나, IDE의 EnvFile 플러그인 등을 함께 사용하세요.

**.gitignore에 추가:**

```text
.env
```

---

## Step 1-5: 설정 확인

### 전체 application.yml 예시

프로젝트의 `src/main/resources/application.yml` 전체입니다.

```yaml
spring:
  application:
    name: flyschedule
  output:
    ansi:
      enabled: always

  # LLM 응답이 느릴 수 있으므로 HTTP 클라이언트 읽기 타임아웃을 5분으로 늘린다 (Ollama·RestClient 공통)
  http:
    client:
      read-timeout: 300s

server:
  port: 8080
  # 서버 타임아웃 설정 (5분)
  tomcat:
    connection-timeout: 300000
    keep-alive-timeout: 300000

# 공공데이터 포털 API 설정
data-go-kr:
  api:
    url: https://apis.data.go.kr/1613000/DmstcFlightNvgInfo
    service-key: ${DATA_GO_KR_SERVICE_KEY:}

# Spring AI 재시도 설정
# 기본값(최대 10회, 대기 간격 5배씩 증가)은 LLM 서버가 꺼져 있을 때 응답이 수십 분 멈추므로 실습용으로 줄인다
spring.ai.retry:
  max-attempts: 2
  backoff:
    initial-interval: 1s
    max-interval: 5s

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

> **Ollama 서버 주소**: 기본값 `http://ollama.java21.net`은 실습용 원격 서버입니다. 내 컴퓨터에서 Ollama를 실행하려면 `base-url`을 `http://localhost:11434`로 바꾸고 `ollama pull qwen2.5:3b-instruct-q4_K_M`으로 모델을 내려받으세요.

### 프로젝트 실행 및 확인

```bash
./mvnw clean install
./mvnw spring-boot:run
```

**콘솔에서 다음 로그 확인:**
```text
Started FlyscheduleApplication in X.XXX seconds
Tomcat started on port(s): 8080 (http)
```

브라우저에서 `http://localhost:8080`을 열면 세 가지 검색 패턴을 비교하는 화면이 나타납니다. 아직 LLM을 호출하는 코드를 배우지 않았으므로, 이 단계에서는 애플리케이션이 오류 없이 시작되는 것까지만 확인하면 됩니다.

---

## Ollama vs Gemini 비교

| 항목 | Ollama (Qwen 2.5) | Gemini (2.5 Flash) |
|------|------------------|-------------------|
| **속도** | 느림 (30~60초) | 빠름 (3~5초) |
| **비용** | 무료 | 유료 (저렴) |
| **안정성** | 중간 | 높음 |
| **한국어** | 우수 | 우수 |
| **추천 용도** | 개발, 테스트 | 실시간 검색, 운영 |

---

## Spring AI 아키텍처 이해

### ChatClient와 ChatModel의 관계

```text
┌─────────────────────────────────────────┐
│         Spring AI Application           │
├─────────────────────────────────────────┤
│            ChatClient                   │
│  (LLM과 대화하는 인터페이스)            │
├─────────────────────────────────────────┤
│     ChatModel (구현체)                  │
│  ┌────────────┐    ┌────────────┐       │
│  │  Ollama    │    │  Gemini    │       │
│  │ChatModel   │    │ChatModel   │       │
│  └────────────┘    └────────────┘       │
├─────────────────────────────────────────┤
│         LLM Provider                    │
│  ┌────────────┐    ┌────────────┐       │
│  │  Ollama    │    │   Google   │       │
│  │  Server    │    │   GenAI    │       │
│  └────────────┘    └────────────┘       │
└─────────────────────────────────────────┘
```

### Spring AI의 핵심 컴포넌트

| 컴포넌트 | 역할 | 예시 |
|----------|------|------|
| **ChatClient** | LLM과 대화하는 빌더 패턴 API | `chatClient.prompt().user("...").call()` |
| **ChatModel** | 실제 LLM 제공자와 연결 | `OllamaChatModel`, `GoogleGenAiChatModel` |
| **@Tool** | LLM이 호출할 수 있는 메서드 정의 | `searchFlightsByAirline()`, `getAirportList()` |
| **@ToolParam** | Tool 파라미터 설명 | `departure`, `arrival`, `date` |

---

## 일반적인 문제 해결

### 문제 1: 의존성 다운로드 실패

**에러 메시지:**
```text
Could not resolve dependency for org.springframework.ai:...
```

**해결 방법:**
1. `spring-ai.version`과 BOM 버전 확인 (Milestone 버전이면 `spring-milestones` 저장소 필요)
2. `./mvnw clean install` 재실행
3. IntelliJ: Maven 탭 > Reload 클릭

### 문제 2: Ollama 연결 실패

**에러 메시지:**
```text
Connection refused: ollama.java21.net
```

화면에서는 `ChatClientProvider.describeError()`가 이 예외를 "LLM 서버에 연결할 수 없습니다. Ollama가 실행 중인지(spring.ai.ollama.base-url) 확인하거나 다른 모델을 선택하세요."라는 문장으로 바꿔 보여 줍니다.

**해결 방법:**
1. Ollama 서버 실행 중인지 확인
2. `base-url`이 올바른지 확인
3. 로컬 Ollama 사용 시: `http://localhost:11434`
4. 응답이 오래 멈춘다면 `spring.ai.retry` 설정이 빠지지 않았는지 확인 (기본값은 최대 10회 재시도)

### 문제 3: Gemini API 키 오류

**에러 메시지:**
```text
API key was reported as leaked
```

**해결 방법:**
1. [Google AI Studio](https://aistudio.google.com/app/apikey)에서 새 키 발급
2. 기존 키 삭제
3. 환경 변수(`GOOGLE_GENAI_API_KEY`) 업데이트

### 문제 4: Gemini를 선택하면 "API 키가 설정되지 않았습니다"라는 안내가 나옴

**원인:** `GOOGLE_GENAI_API_KEY` 환경 변수가 없어서 `api-key`가 기본값 `not-set`으로 설정되었습니다. 오류가 아니라 의도한 동작입니다.

**해결 방법:**
1. Ollama만으로 실습한다면 그대로 두어도 됩니다
2. Gemini를 쓰려면 환경 변수를 설정하고 애플리케이션을 다시 시작합니다

### 문제 5: Spring AI 버전 충돌

**에러 메시지:**
```text
NoSuchMethodError: org.springframework.ai...
```

**해결 방법:**
1. Spring Boot 버전이 3.5+인지 확인
2. `spring-ai.version`이 1.1.4인지 확인
3. `./mvnw clean install`로 의존성 재구축

---

## 실습 과제

1. **(기본)** `DATA_GO_KR_SERVICE_KEY`만 설정하고 `GOOGLE_GENAI_API_KEY`는 설정하지 않은 채 `./mvnw spring-boot:run`으로 애플리케이션을 실행해 보세요. 정상적으로 시작되는지, 화면에서 Gemini를 선택하면 어떤 안내가 나오는지 확인하세요.
2. **(기본)** 로컬에 Ollama를 설치하고 `ollama pull qwen2.5:3b-instruct-q4_K_M`으로 모델을 내려받은 뒤, `spring.ai.ollama.base-url`을 `http://localhost:11434`로 바꿔 실행해 보세요.
3. **(도전)** Ollama를 끈 상태에서 검색을 요청하고 실패 응답이 올 때까지 걸리는 시간을 재 보세요. 그다음 `spring.ai.retry` 블록을 주석 처리하고 다시 재 본 뒤, 이 프로젝트가 재시도 설정을 줄인 이유를 한두 문장으로 정리하세요.
4. **(도전)** `spring.config.import: optional:file:.env[.properties]`를 `application.yml`에 추가해 `.env` 파일에서 두 환경 변수를 읽도록 바꿔 보고, `.env`가 `.gitignore`에 들어 있는지 확인하세요.

---

## 요약

이번 단계에서는 Spring AI 프로젝트의 기반을 구축했습니다:

1. **의존성 추가**: `pom.xml`에 Spring AI BOM과 Ollama, Gemini 스타터 의존성을 추가했습니다
2. **LLM 설정**: `application.yml`을 통해 Ollama(로컬, 무료)와 Gemini(클라우드, 유료) 두 가지 LLM을 설정하고, 읽기 타임아웃(`spring.http.client.read-timeout`)과 재시도(`spring.ai.retry`)를 실습에 맞게 조정했습니다
3. **API 키 관리**: 환경 변수를 사용하여 API 키를 안전하게 관리하는 방법을 배웠습니다
4. **설정 확인**: 프로젝트를 실행하고 Spring AI가 정상적으로 초기화되는지 확인했습니다

다음 단계에서는 이 설정을 바탕으로 실제로 LLM과 대화하는 코드를 작성해 보겠습니다.

---

## 체크리스트

- [ ] Spring AI BOM이 pom.xml에 추가됨
- [ ] (Milestone 버전 사용 시) Spring Milestone Repository가 설정됨
- [ ] Ollama 의존성이 추가됨
- [ ] Gemini 의존성이 추가됨
- [ ] application.yml에 Ollama 설정이 완료됨
- [ ] application.yml에 Gemini 설정이 완료됨
- [ ] `DATA_GO_KR_SERVICE_KEY` 환경 변수가 설정됨 (필수, 항공편 검색 API용)
- [ ] `GOOGLE_GENAI_API_KEY` 환경 변수가 설정됨 (선택, 없으면 Ollama만 사용)
- [ ] `spring.http.client.read-timeout`과 `spring.ai.retry` 설정의 의미를 설명할 수 있음
- [ ] 프로젝트가 정상 실행됨
- [ ] 콘솔에서 Spring AI 로그 확인됨

---

## 다음 단계

[Step 2: ChatClient 기초와 LLM 연동](step-2-chatclient-basics.md)에서 실제로 LLM과 대화하는 방법을 학습합니다.

---

## 참고 자료

- [Spring AI 공식 문서](https://docs.spring.io/spring-ai/reference/)
- [Spring AI 1.1.4 릴리스 노트](https://github.com/spring-projects/spring-ai/releases/tag/v1.1.4)
- [Ollama 공식 사이트](https://ollama.com/)
- [Google AI Studio](https://aistudio.google.com/)
- [Gemini API 문서](https://ai.google.dev/gemini-api/docs)

---

## 심화 학습

### 왜 두 가지 LLM을 설정하나요?

1. **개발 단계**: Ollama (무료, 로컬)로 테스트
2. **운영 단계**: Gemini (빠름, 안정적)로 서비스

### Spring AI의 장점

- **통합 인터페이스**: ChatClient로 모든 LLM 사용 가능
- **쉬운 전환**: `ChatModel` 빈만 교체하면 LLM 변경 가능
- **Spring 생태계 통합**: Spring Boot, Spring Security와 쉽게 연동
