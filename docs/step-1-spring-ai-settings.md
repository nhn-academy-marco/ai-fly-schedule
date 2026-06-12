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

```
[application.yml 설정]
       ↓
[Spring AI 자동 설정 (Auto-Configuration)]
       ↓
┌──────────────────────────────────┐
│     Spring 컨테이너 (IoC)        │
│  ┌────────────┐  ┌────────────┐ │
│  │ Ollama     │  │ Gemini     │ │
│  │ ChatModel  │  │ ChatModel  │ │
│  └────────────┘  └────────────┘ │
└──────────────────────────────────┘
       ↓
[ChatClient.Builder로 ChatClient 생성] (Step 2에서 학습)
       ↓
[Controller → Service → ChatClient → AI 모델 응답] (Step 2~4에서 학습)
```

---

## LLM이란 무엇인가요?

**LLM (Large Language Model, 대규모 언어 모델)** 은 방대한 텍스트 데이터로 학습한 인공지능 모델입니다.

### LLM의 기본 개념

**1. LLM이란 무엇인가?**

```
일반적인 프로그램:  개발자가 규칙을 작성 → 프로그램이 규칙대로 동작
LLM:              방대한 데이터로 학습 → 모델이 스스로 패턴을 학습

예시:
- "스프링이 뭐야?" → LLM이 학습한 데이터에서 스프링 관련 내용을 찾아 답변
- "내일 광주에서 제주로 가는 항공편 알려줘" → LLM이 의도를 파악하고 응답
```

**2. LLM은 어떻게 동작하나요?**

```
입력: "내일 광주에서 제주로 가는 항공편 알려줘"
  ↓
토큰화 (Tokenization): ["내일", "광주", "에서", "제주", "로", "가는", "항공편", ...]
  ↓
모델 처리: 각 토큰 간의 관계를 분석하여 다음 토큰 예측
  ↓
출력: "네, 내일 광주에서 제주로 가는 항공편은..."
```

**3. 주요 용어 설명**

| 용어 | 설명 | 예시 |
|------|------|------|
| **토큰 (Token)**| LLM이 처리하는 최소 단위 | "안녕하세요" → ["안녕", "하", "세요"] (약 3-4토큰) |
| **프롬프트 (Prompt)**| LLM에게 전달하는 입력 | "스프링이 뭐야?" |
| **컨텍스트 (Context)**| 대화의 맥락 정보 | 이전 대화 기록, 시스템 지시사항 |
| **응답 (Completion)**| LLM이 생성하는 답변 | "스프링은 자바 프레임워크입니다..." |

### LLM의 파라미터 이해하기

**Temperature (온도)**

LLM의 창의성을 조절하는 파라미터입니다.

```
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

```
Max Tokens = 256
→ 약 200글자 정도의 답변
→ 사용: 간단한 질문-답변

Max Tokens = 4096
→ 약 3000글자 정도의 답변
→ 사용: 장문의 설명, 코드 생성
```

**Top-P, Top-K**

다음 토큰을 선택할 때 고려할 후보 수입니다.

```
Top-K = 40: 상위 40개 후보 중에서 선택
Top-P = 0.9: 누적 확률 90%까지 후보 고려

일반적으로 기본값 사용 (Top-K: 40, Top-P: 0.9)
```

### Spring에서 LLM 사용하기

**Spring AI는 무엇을 해주나요?**

```
┌─────────────────────────────────────────┐
│  Spring 없이 LLM 사용                    │
│  - 각 LLM 회사의 SDK를 직접 학습         │
│  - OpenAI SDK, Gemini SDK, Ollama SDK   │
│  - 코드 중복, 모델 교체 어려움           │
└─────────────────────────────────────────┘

↓ Spring AI 도입

┌─────────────────────────────────────────┐
│  Spring AI로 LLM 사용                    │
│  - 통일된 API (ChatClient)               │
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
| 비즈니스 로직과 LLM 호출 코드 혼재 | Spring의 DI/AOP로 깔끔하게 분리 |

### Spring AI의 기술적 배경

**1. 추상화 계층 (Abstraction Layer)**

```
┌─────────────────────────────────────────┐
│         Spring AI Application           │
├─────────────────────────────────────────┤
│    ChatClient (통일된 인터페이스)       │
├─────────────────────────────────────────┤
│    ChatModel (추상화 계층)              │
│  ┌────────┐  ┌────────┐  ┌────────┐  │
│  │Ollama  │  │Gemini  │  │OpenAI  │  │
│  │구현체  │  │구현체  │  │구현체  │  │
│  └────────┘  └────────┘  └────────┘  │
└─────────────────────────────────────────┘
```

**2. 종속성 주입 (Dependency Injection)**

Spring AI는 Spring Framework의 DI 컨테이너를 활용하여 LLM 관련 빈(Bean)을 관리합니다. 이로 인해 다음과 같은 이점을 얻습니다:

- **느슨한 결합**: 구체적인 LLM 구현체에 의존하지 않음
- **쉬운 테스트**: Mock ChatModel로 쉽게 테스트 가능
- **환경별 설정**: 개발(로컬) vs 운영(클라우드) 쉽게 전환

**3. AOP (Aspect-Oriented Programming)**

Spring AI는 AOP를 활용하여 다음을 구현합니다:

- **Logging**: LLM 호출 전후 자동 로깅
- **Monitoring**: 실행 시간, 토큰 사용량 추적
- **Error Handling**: 일관된 예외 처리

### 핵심 기능

| 기능 | 설명 |
|------|------|
| **ChatClient**| LLM과 대화하는 인터페이스 |
| **ChatModel**| 다양한 LLM 제공자(Ollama, Gemini, OpenAI 등) 통합 |
| **Function Calling**| LLM이 자바 메서드를 호출할 수 있게 함 |
| **Tool Calling Callback**| Function Calling 실행 과정을 모니터링 |

### Spring AI 1.1.4 특징

- Spring Boot 3.5+ 지원
- 공식 `@Tool`, `@ToolParam` annotation 제공
- 다중 LLM 동시 사용 가능
- Tool Calling Callback으로 실행 추적 지원

---

## Step 1-1: pom.xml 설정

### 왜 이렇게 할까요?

Spring Boot 프로젝트에서 새로운 기능을 추가할 때 가장 먼저 하는 일이 무엇인지 떠올려 보세요. 바로 `pom.xml`에 의존성을 추가하는 것입니다. JPA를 쓸 때 `spring-boot-starter-data-jpa`를 추가하듯, AI 기능을 쓸 때도 `spring-ai-starter` 의존성을 추가해야 합니다.

Spring AI는 아직 정식 릴리스가 아니라 Milestone 버전이기 때문에, 일반 Maven 중앙 저장소에서 다운로드할 수 없습니다. 그래서 Spring 전용 Milestone 저장소를 추가로 등록해야 합니다. 또한 Spring AI는 여러 모듈(Ollama, Gemini 등)로 구성되어 있어, BOM(Bill of Materials)으로 버전을 한 곳에서 관리하는 것이 좋습니다.

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

### Spring Milestone Repository 추가

Spring AI는 현재 Milestone 버전이므로 Maven repository를 추가해야 합니다:

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

> **Spring Boot 복습**: Spring Boot의 자동 설정(Auto-Configuration)은 `application.yml`에 작성된 설정값을 읽어서 필요한 Bean을 자동으로 생성합니다. 예를 들어 `spring.datasource.url`을 적으면 `DataSource` Bean이 자동 생성되듯, `spring.ai.ollama.base-url`을 적으면 `OllamaChatModel` Bean이 자동 생성됩니다.

### Ollama란?

**Ollama**는 로컬 컴퓨터에서 LLM을 실행할 수 있는 오픈소스 도구입니다.

| 특징 | 설명 |
|------|------|
| **비용**| 무료 |
| **속도**| 느림 (30~60초) |
| **용도**| 개발, 테스트 |
| **모델**| Llama, Qwen, Mistral 등 |

### application.yml 설정

Spring을 공부하신 분이라면 `application.yml`이 익숙하실 겁니다. LLM 설정도 동일한 방식으로 합니다.

```yaml
spring.ai.ollama:
  base-url: http://ollama.java21.net  # Ollama 서버 주소
  chat:
    options:
      model: qwen2.5:3b-instruct-q4_K_M  # 사용할 모델 (양자화된 Qwen 2.5 3B)
      temperature: 0.0                    # 응답 일관성
      timeout: 300s                       # 타임아웃 5분 (300초)
      max-tokens: 1024                    # 최대 출력 토큰 수
      num-thread: 4                       # 스레드 수
      num-ctx: 2048                       # 컨텍스트 윈도우 크기
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
| `temperature` | `0.0` | 결정적 응답 (0.0~1.0, 낮을수록 일관적) |
| `timeout` | `300s` | 요청 타임아웃 (5분) |
| `max-tokens` | `1024` | 최대 출력 토큰 수 |
| `num-thread` | `4` | Ollama 실행 스레드 수 |
| `num-ctx` | `2048` | 컨텍스트 윈도우 크기 (토큰 수) |
| `top-p` | `0.9` | 누적 확률 분포 상위 90%만 고려 |
| `top-k` | `40` | 다음 토큰 후보 상위 40개만 고려 |
| `keep-alive` | `10m` | 모델 메모리 유지 시간 (10분) |

---

## Step 1-3: Gemini 설정

### 왜 이렇게 할까요?

Ollama는 무료이지만 응답 속도가 느립니다(30~60초). 실제 서비스에서는 사용자가 1분을 기다릴 수 없습니다. Google Gemini는 클라우드 기반 LLM으로 3~5초 만에 응답하여 운영 환경에 적합합니다. 두 가지 LLM을 모두 설정해 두면, 개발할 때는 Ollama를, 실제 서비스에서는 Gemini를 선택적으로 사용할 수 있습니다.

### Google GenAI API란?

**Google Gemini**는 Google의 클라우드 기반 LLM 서비스입니다.

| 특징 | 설명 |
|------|------|
| **비용**| 유료 (저렴) |
| **속도**| 빠름 (3~5초) |
| **용도**| 실시간 검색, 운영 환경 |
| **모델**| gemini-2.5-flash, gemini-2.5-pro |

### API 키 발급

1. [Google AI Studio](https://makersuite.google.com/app/apikey) 접속
2. **Create API Key**클릭
3. 프로젝트 선택 또는 생성
4. 발급된 키 복사

 **보안 주의**: API 키를 GitHub에 커밋하지 마세요!

### application.yml 설정

```yaml
spring:
  ai:
    google:
      genai:
        api-key: ${GEMINI_API_KEY}        # 환경 변수 사용 권장
        chat:
          options:
            model: gemini-2.5-flash       # Gemini 2.5 Flash 모델
            temperature: 0.0              # 결정적 응답
            max-output-tokens: 256        # 최대 출력 토큰 수
            top-p: 0.9                    # Nucleus sampling
            top-k: 40                     # 상위 K 후보 고려
```

### 파라미터 상세 설명

| 파라미터 | 값 | 설명 | 범위 |
|----------|-----|------|------|
| `model` | `gemini-2.5-flash` | 빠르고 저렴한 모델 | `gemini-2.5-flash`, `gemini-2.5-pro` |
| `temperature` | `0.0` | 응답의 일관성 (0=결정적, 2=창의적) | `0.0` ~ `2.0` |
| `max-output-tokens` | `256` | 최대 출력 토큰 수 (1토큰≈4글자) | `1` ~ `8192` |
| `top-p` | `0.9` | 누적 확률 분포 상위 90%만 고려 | `0.0` ~ `1.0` |
| `top-k` | `40` | 다음 토큰 후보 상위 40개만 고려 | `1` ~ `100` |

### Temperature 값에 따른 동작

| 값 | 동작 | 사용 사례 |
|----|------|-----------|
| `0.0` ~ `0.3` | 항상 같은 입력에 같은 출력 | 검색, 분류, Function Calling |
| `0.4` ~ `0.7` | 균형 잡힌 창의성 | 대화, 질문-답변 |
| `0.8` ~ `2.0` | 창의적이고 무작위적 | 창작, 아이디어 생성 |

---

## Step 1-4: 환경 변수로 API 키 관리

### 왜 이렇게 할까요?

`application.yml`에 API 키를 직접 적으면 어떻게 될까요? GitHub에 코드를 올릴 때 API 키가 함께 공개되어 누구나 내 계정으로 Gemini를 사용할 수 있게 됩니다. 이를 방지하기 위해 API 키는 환경 변수로 관리합니다. `application.yml`에서 `${GEMINI_API_KEY}`처럼 작성하면, Spring이 실행될 때 환경 변수에서 값을 읽어옵니다.

> **Spring Boot 복습**: `${...}` 문법은 Spring의 프로퍼티 placeholder입니다. `spring.datasource.password`를 `${DB_PASSWORD}`로 설정해 본 적이 있을 것입니다. API 키도 동일한 방식으로 환경 변수에서 주입합니다.

### 방법 1: 터미널 (macOS/Linux)

```bash
export GEMINI_API_KEY=AIzaSyA6XO9NuGKguis8ckJ17FnteAKfqiT_XA0
mvn spring-boot:run
```

### 방법 2: PowerShell (Windows)

```powershell
$env:GEMINI_API_KEY="AIzaSyA6XO9NuGKguis8ckJ17FnteAKfqiT_XA0"
mvn spring-boot:run
```

### 방법 3: IntelliJ IDEA

1. **Run**> **Edit Configurations**
2. **Environment Variables**필드에:
   ```
   GEMINI_API_KEY=AIzaSyA6XO9NuGKguis8ckJ17FnteAKfqiT_XA0
   ```

### 방법 4: .env 파일 (권장)

프로젝트 루트에 `.env` 파일 생성:

```env
GEMINI_API_KEY=AIzaSyA6XO9NuGKguis8ckJ17FnteAKfqiT_XA0
```

 **.gitignore에 추가:**
```
.env
application.yml
```

---

## Step 1-5: 설정 확인

### 전체 application.yml 예시

```yaml
spring:
  application:
    name: flyschedule
  output:
    ansi:
      enabled: always

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
    service-key: ${DATA_GO_KR_SERVICE_KEY}

# Spring AI Ollama 설정
spring.ai.ollama:
  base-url: http://ollama.java21.net
  chat:
    options:
      model: qwen2.5:3b-instruct-q4_K_M
      temperature: 0.0
      # 타임아웃 5분 (300초)
      timeout: 300s
      max-tokens: 1024
      num-thread: 4
      num-ctx: 2048
      top-p: 0.9
      top-k: 40
      keep-alive: 10m

# Spring AI Google GenAI 설정 (Gemini)
spring.ai.google:
  genai:
    api-key: ${GEMINI_API_KEY}
    chat:
      options:
        model: gemini-2.5-flash
        temperature: 0.0
        max-output-tokens: 256
        top-p: 0.9
        top-k: 40
```

### 프로젝트 실행 및 확인

```bash
mvn clean install
mvn spring-boot:run
```

**콘솔에서 다음 로그 확인:**
```
Started FlyScheduleApplication in X.XXX seconds
Tomcat started on port(s): 8080 (http)
```

---

## 일반적인 문제 해결

### 문제 1: 의존성 다운로드 실패

**에러 메시지:**
```
Could not resolve dependency for org.springframework.ai:...
```

**해결 방법:**
1. Maven repository 설정 확인 (`spring-milestones`)
2. `mvn clean install` 재실행
3. IntelliJ: Maven 탭 > Reload 클릭

### 문제 2: Ollama 연결 실패

**에러 메시지:**
```
Connection refused: ollama.java21.net
```

**해결 방법:**
1. Ollama 서버 실행 중인지 확인
2. `base-url`이 올바른지 확인
3. 로컬 Ollama 사용 시: `http://localhost:11434`

### 문제 3: Gemini API 키 오류

**에러 메시지:**
```
API key was reported as leaked
```

**해결 방법:**
1. [Google AI Studio](https://makersuite.google.com/app/apikey)에서 새 키 발급
2. 기존 키 삭제
3. `application.yml` 또는 환경 변수 업데이트

### 문제 4: Spring AI 버전 충돌

**에러 메시지:**
```
NoSuchMethodError: org.springframework.ai...
```

**해결 방법:**
1. Spring Boot 버전이 3.5+인지 확인
2. `spring-ai.version`이 1.1.4인지 확인
3. `mvn clean install`로 의존성 재구축

---

## Ollama vs Gemini 비교

| 항목 | Ollama (Qwen 2.5) | Gemini (2.5 Flash) |
|------|------------------|-------------------|
| **속도**| 느림 느림 (30~60초) | 빠름 빠름 (3~5초) |
| **비용**| 무료 무료 | 유료 유료 (저렴) |
| **안정성**|  중간 |  높음 |
| **한국어**|  우수 |  우수 |
| **추천 용도**| 개발, 테스트 | 실시간 검색, 운영 |

---

## Spring AI 아키텍처 이해

### ChatClient와 ChatModel의 관계

```
┌─────────────────────────────────────────┐
│         Spring AI Application           │
├─────────────────────────────────────────┤
│            ChatClient                   │
│  (LLM과 대화하는 인터페이스)            │
├─────────────────────────────────────────┤
│     ChatModel (구현체)                  │
│  ┌────────────┐    ┌────────────┐     │
│  │  Ollama    │    │  Gemini    │     │
│  │ChatModel   │    │ChatModel   │     │
│  └────────────┘    └────────────┘     │
├─────────────────────────────────────────┤
│         LLM Provider                    │
│  ┌────────────┐    ┌────────────┐     │
│  │  Ollama    │    │   Google   │     │
│  │  Server    │    │   GenAI    │     │
│  └────────────┘    └────────────┘     │
└─────────────────────────────────────────┘
```

### Spring AI의 핵심 컴포넌트

| 컴포넌트 | 역할 | 예시 |
|----------|------|------|
| **ChatClient**| LLM과 대화하는 빌더 패턴 API | `chatClient.prompt().user("...").call()` |
| **ChatModel**| 실제 LLM 제공자와 연결 | `OllamaChatModel`, `GoogleGenAiChatModel` |
| **@Tool**| LLM이 호출할 수 있는 함수 정의 | `searchFlights()`, `getAirports()` |
| **@ToolParam**| Tool 파라미터 설명 | `departure`, `arrival`, `date` |

---

## 체크리스트

- [ ] Spring AI BOM이 pom.xml에 추가됨
- [ ] Spring Milestone Repository가 설정됨
- [ ] Ollama 의존성이 추가됨
- [ ] Gemini 의존성이 추가됨
- [ ] application.yml에 Ollama 설정이 완료됨
- [ ] application.yml에 Gemini 설정이 완료됨
- [ ] GEMINI_API_KEY 환경 변수가 설정됨
- [ ] 프로젝트가 정상 실행됨
- [ ] 콘솔에서 Spring AI 로그 확인됨

---

## 다음 단계

**Step 2: ChatClient 기초와 LLM 연동**에서 실제로 LLM과 대화하는 방법을 학습합니다.

---

## 요약

이번 단계에서는 Spring AI 프로젝트의 기반을 구축했습니다:

1. **의존성 추가**: `pom.xml`에 Spring AI BOM과 Ollama, Gemini 스타터 의존성을 추가했습니다
2. **LLM 설정**: `application.yml`을 통해 Ollama(로컬, 무료)와 Gemini(클라우드, 유료) 두 가지 LLM을 설정했습니다
3. **API 키 관리**: 환경 변수를 사용하여 API 키를 안전하게 관리하는 방법을 배웠습니다
4. **설정 확인**: 프로젝트를 실행하고 Spring AI가 정상적으로 초기화되는지 확인했습니다

다음 단계에서는 이 설정을 바탕으로 실제로 LLM과 대화하는 코드를 작성해 보겠습니다.

---

## 참고 자료

- [Spring AI 공식 문서](https://docs.spring.io/spring-ai/reference/)
- [Spring AI 1.1.4 릴리스 노트](https://github.com/spring-projects/spring-ai/wiki/v1.1.4-Release-Notes)
- [Ollama 공식 사이트](https://ollama.com/)
- [Google AI Studio](https://makersuite.google.com/)
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
