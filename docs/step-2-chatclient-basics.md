# Step 2: ChatClient 기초와 LLM 연동

Step 1에서 설정한 Ollama·Gemini 연결은 Spring 컨테이너에 `ChatModel` 빈으로 들어 있습니다. 이번에는 그 `ChatModel`을 `ChatClient`로 감싸, 서비스 코드에서 실제로 LLM을 호출해 봅니다.

**이 Step에서 만들 것**: 모델별 `ChatClient` 빈과, 요청의 `model` 값으로 `ChatClient`를 고르는 `ChatClientProvider`를 이해하고, `POST /api/search/coordinator`로 LLM이 문장에서 검색 조건을 뽑아내는 것을 확인합니다. 영어 문장을 보내면 응답의 실행 기록 첫 단계가 다음처럼 `LLM`으로 나옵니다.

```json
{ "order": 1, "kind": "LLM", "actor": "SearchCriteriaExtractor", "title": "LLM으로 검색 조건 추출", "detail": "광주 → 제주 · 2026-10-17", ... }
```

## 학습 목표

이 Step을 마치면 다음을 할 수 있습니다.

1. `ChatClient`와 `ChatModel`의 역할 차이를 설명할 수 있다.
2. `@Configuration` 클래스에서 모델별 `ChatClient` 빈을 등록하고, `@Qualifier`로 원하는 빈을 주입할 수 있다.
3. `ChatClientProvider`가 요청의 `model` 값으로 `ChatClient`를 고르고, 키 누락·연결 실패를 안내 문장으로 바꾸는 흐름을 설명할 수 있다.
4. `prompt().system().user().call()`로 LLM을 호출하고, `.content()`와 `.entity(Class)`로 결과를 문자열이나 자바 객체로 받을 수 있다.
5. curl로 `/api/search/coordinator`를 호출하고, 응답의 `trace`로 정규식 경로와 LLM 경로를 구분할 수 있다.

---

## 전체 흐름

```text
[사용자]  POST /api/search/coordinator  {"message": "...", "model": "ollama"}
  ↓
[SearchController.coordinator()]
  ↓
[FlightSearchCoordinator.coordinate(message, model)]
  ↓
[SearchCriteriaExtractor.extract(message, () -> chatClientProvider.get(model))]
  ↓  정규식으로 안 되면 model 값에 따라 ollamaChatClient 또는 geminiChatClient
[ChatClient.prompt().system(...).user(message).call().entity(SearchCriteria.class)]
  ↓
[ChatModel]  OllamaChatModel 또는 GoogleGenAiChatModel
  ↓
[SearchResponse(JSON)]
```

이번 Step은 이 흐름 가운데 `ChatClient`를 만들고, 고르고, 호출하는 부분만 다룹니다. `FlightSearchCoordinator`가 검색 조건을 받은 뒤 하는 일은 Step 9에서 배웁니다.

익숙한 Spring 구조와 비교하면 Repository 자리에 `ChatClient`가 들어간 모양입니다.

```text
기존:  [컨트롤러] → [서비스] → [Repository] → [데이터베이스]
AI:    [컨트롤러] → [서비스] → [ChatClient] → [LLM]
```

---

## ChatClient란?

**ChatClient**는 Spring AI에서 LLM에 프롬프트를 보내고 응답을 받는 API입니다. `RestClient`로 외부 HTTP API를 부르듯, `ChatClient`로 LLM을 부릅니다.

### 왜 필요한가

LLM 제공자마다 SDK와 요청 형식이 다릅니다. 제공자의 SDK를 직접 쓰면 모델을 바꿀 때 호출 코드를 모두 고쳐야 합니다. `ChatClient`는 어떤 모델이든 같은 코드로 호출하게 해 줍니다.

```java
String answer = chatClient.prompt()
        .system("당신은 국내선 항공편 검색 도우미입니다.")   // 역할과 규칙
        .user("내일 광주에서 제주 가는 항공편")              // 사용자 질문
        .call()                                              // LLM 호출 (동기)
        .content();                                          // 응답 텍스트
```

메서드를 이어 붙여 요청을 조립하는 이런 방식을 **유창한 API**(Fluent API)라고 합니다.

| 메서드 | 반환 타입 | 하는 일 |
|--------|-----------|---------|
| `prompt()` | `ChatClient.ChatClientRequestSpec` | 요청 조립 시작 |
| `system(String)` | `ChatClientRequestSpec` | 시스템 프롬프트(System Prompt, LLM의 역할과 규칙을 정하는 지시) 설정 |
| `user(String)` | `ChatClientRequestSpec` | 사용자 메시지 설정 |
| `call()` | `ChatClient.CallResponseSpec` | LLM을 동기 호출 |
| `content()` | `String` | 응답 텍스트 |
| `entity(Class)` | 지정한 타입 | 응답 JSON을 자바 객체로 변환 |
| `chatResponse()` | `ChatResponse` | 토큰 사용량 같은 메타데이터를 포함한 전체 응답 |

### ChatClient와 ChatModel

| 구분 | ChatModel | ChatClient |
|------|-----------|------------|
| 역할 | 특정 제공자와 실제로 통신 | 프롬프트 조립, 호출, 결과 변환 |
| 만드는 쪽 | 스타터의 자동 설정 | 개발자 (`ChatClient.builder(chatModel)`) |
| 이 프로젝트의 빈 | `ollamaChatModel`, `googleGenAiChatModel` | `ollamaChatClient`, `geminiChatClient` |

`prompt()`는 호출할 때마다 새 요청 스펙을 만들므로, 한 요청에 넣은 `system()`, `user()`는 다른 요청에 영향을 주지 않습니다. 한 번 만든 `ChatClient`는 여러 스레드에서 함께 써도 되므로 싱글톤 빈으로 둡니다.

---

## Step 2-1: ChatClientConfig로 ChatClient 빈 등록

### 왜 필요한가

Spring AI는 `ChatModel` 빈은 자동으로 만들어 주지만, 서비스가 쓸 `ChatClient` 빈은 만들어 주지 않습니다. 이 프로젝트는 `ChatModel`이 두 개이므로 `ChatClient`도 모델마다 하나씩 직접 만듭니다. 외부 라이브러리 객체는 클래스에 `@Component`를 붙일 수 없으므로, `@Configuration` 클래스의 `@Bean` 메서드로 등록합니다.

### 어떻게 하는가

파일: `src/main/java/net/java21/flyschedule/config/ChatClientConfig.java`

```java
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient ollamaChatClient(@Qualifier("ollamaChatModel") ChatModel ollamaChatModel) {
        return ChatClient.builder(ollamaChatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    @Bean
    public ChatClient geminiChatClient(@Qualifier("googleGenAiChatModel") ChatModel geminiChatModel) {
        return ChatClient.builder(geminiChatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}
```

**코드 읽기**

1. `@Bean` 메서드 이름이 빈 이름이 됩니다. 같은 `ChatClient` 타입 빈이 `ollamaChatClient`, `geminiChatClient` 두 개 생깁니다.
2. `@Qualifier("ollamaChatModel")`: `ChatModel` 타입 빈이 두 개이므로 이름으로 골라 주입합니다.
3. `ChatClient.builder(chatModel)`로 빌더를 만들고, 모든 요청에 붙일 기본값을 지정한 뒤 `build()`합니다.
4. `defaultAdvisors(new SimpleLoggerAdvisor())`: 모든 요청과 응답을 DEBUG 로그로 남기는 Advisor를 기본 등록합니다(Step 4).
5. `defaultTools(...)`는 쓰지 않습니다. 기본으로 Tool을 등록하면 검색 조건 추출(`SearchCriteriaExtractor`), 작업 계획(`FlightSearchPlanner`), 결과 요약(`ResultSynthesizer`)처럼 Tool이 필요 없는 호출에도 Tool 목록이 붙어, LLM이 엉뚱하게 Tool을 부를 수 있습니다. Tool은 필요한 곳(`ToolCallingSearchService`)에서만 요청마다 `.tools(...)`로 넘깁니다(Step 3).

| 빈 이름 | 타입 | 만드는 쪽 |
|---------|------|-----------|
| `ollamaChatModel` | `OllamaChatModel` | Ollama 스타터 자동 설정 |
| `googleGenAiChatModel` | `GoogleGenAiChatModel` | Google GenAI 스타터 자동 설정 |
| `ollamaChatClient` | `ChatClient` | `ChatClientConfig` |
| `geminiChatClient` | `ChatClient` | `ChatClientConfig` |

> **생각해 보기**: `ChatClient.Builder`를 빈으로 두고 서비스마다 `build()`하는 방법도 있습니다. 이 프로젝트가 완성된 `ChatClient`를 빈으로 둔 까닭은 무엇일까요? 심화 학습에서 두 방식을 비교합니다.

### 확인하기

애플리케이션이 오류 없이 시작되면 네 빈이 모두 만들어지고 주입된 것입니다. 일부러 `ChatClientProvider` 생성자의 `@Qualifier("ollamaChatClient")`를 지우고 실행해 보면, `ChatClient` 빈이 두 개라서 어느 것을 넣을지 모른다는 오류(`expected single matching bean but found 2`)를 볼 수 있습니다. 확인한 뒤 되돌리세요.

---

## Step 2-2: ChatClientProvider로 모델 고르기

### 왜 필요한가

화면과 API에서 사용자는 요청마다 Ollama와 Gemini 중 하나를 고릅니다. 서비스마다 `"gemini".equals(model) ? ... : ...`를 반복하면 키 검사나 오류 문구가 서비스마다 달라지기 쉽습니다. 모델 선택 규칙을 한 클래스에 모으면 세 가지 검색 패턴(Step 9~11)이 모두 같은 방식으로 모델을 고르고 같은 문장으로 오류를 안내합니다.

### 어떻게 하는가

파일: `src/main/java/net/java21/flyschedule/config/ChatClientProvider.java`

```java
@Component
public class ChatClientProvider {

    public static final String OLLAMA = "ollama";
    public static final String GEMINI = "gemini";

    /** application.yml에서 GOOGLE_GENAI_API_KEY가 없을 때 쓰는 기본값 */
    static final String GEMINI_KEY_NOT_SET = "not-set";

    private final ChatClient ollamaChatClient;
    private final ChatClient geminiChatClient;
    private final boolean geminiConfigured;

    public ChatClientProvider(@Qualifier("ollamaChatClient") ChatClient ollamaChatClient,
                              @Qualifier("geminiChatClient") ChatClient geminiChatClient,
                              @Value("${spring.ai.google.genai.api-key:" + GEMINI_KEY_NOT_SET + "}") String geminiApiKey) {
        this.ollamaChatClient = ollamaChatClient;
        this.geminiChatClient = geminiChatClient;
        this.geminiConfigured = !geminiApiKey.isBlank() && !GEMINI_KEY_NOT_SET.equals(geminiApiKey);
    }

    public ChatClient get(String model) {
        if (!GEMINI.equalsIgnoreCase(model)) {
            return ollamaChatClient;
        }
        if (!geminiConfigured) {
            throw new IllegalArgumentException(
                    "Gemini API 키가 설정되지 않았습니다. 환경 변수 GOOGLE_GENAI_API_KEY를 설정하거나 Ollama를 선택하세요.");
        }
        return geminiChatClient;
    }

    public static String describeError(Exception e) {
        // ... 생략 (ExternalApiException은 메시지 그대로, 연결 실패·잘못된 키는 안내 문장으로)
    }

    public static String normalize(String model) {
        return GEMINI.equalsIgnoreCase(model) ? GEMINI : OLLAMA;
    }
}
```

**코드 읽기**

1. `get(model)`: `"gemini"`(대소문자 무관)면 Gemini, 그 밖의 값과 `null`은 Ollama를 돌려줍니다.
2. 생성자에서 키가 `not-set`인지 미리 판단해 두고, Gemini를 고른 요청에서만 `IllegalArgumentException`을 던집니다. 각 패턴 서비스가 이 예외를 잡아 `success: false` 응답의 `message`로 보여 줍니다.
3. `describeError(e)`: 예외를 사용자에게 보여 줄 문장으로 바꿉니다. 공공데이터 포털 오류(`ExternalApiException`, Step 6)는 메시지를 그대로 쓰고, 원인을 따라가며 연결 실패면 "LLM 서버에 연결할 수 없습니다...", `API key not valid`면 "Gemini API 키가 올바르지 않습니다..."로 바꿉니다.
4. `normalize(model)`: 실행 기록에 남길 모델 이름을 `"ollama"`나 `"gemini"`로 맞춥니다.

### 확인하기

```bash
./mvnw test -Dtest=ChatClientProviderTest
```

`selectsByModelName`, `geminiWithoutKey`, `describeError`, `normalize` 네 테스트가 통과하면 됩니다. `src/test/java/net/java21/flyschedule/config/ChatClientProviderTest.java`를 열어, 가짜 `ChatClient`(Mockito `mock`)로 선택 규칙만 검증하는 방식을 살펴보세요.

---

## Step 2-3: 첫 LLM 호출 — SearchCriteriaExtractor

### 왜 필요한가

"내일 광주에서 제주로 가는 항공편"이라는 문장으로 항공편을 검색하려면 먼저 출발 공항, 도착 공항, 날짜 같은 검색 조건을 뽑아야 합니다. 정해진 형식의 문장은 정규식으로 충분하지만, "flights from Gwangju to Jeju tomorrow"나 "다음 주 금요일"처럼 형식이 다양한 문장은 LLM이 더 잘 처리합니다. `SearchCriteriaExtractor`는 정규식을 먼저 시도하고, 실패할 때만 LLM을 부릅니다.

### 어떻게 하는가

파일: `src/main/java/net/java21/flyschedule/service/ai/SearchCriteriaExtractor.java`

```java
public Extraction extract(String message, Supplier<ChatClient> chatClient) {
    Optional<SearchCriteria> parsed = parseWithRegex(message);
    if (parsed.isPresent()) {
        log.info("정규식으로 검색 조건 추출: {}", parsed.get());
        return new Extraction(parsed.get(), false);
    }

    log.info("정규식으로 공항을 찾지 못해 LLM으로 검색 조건 추출: {}", message);
    SearchCriteria criteria = chatClient.get().prompt()
            .system("""
                    항공편 검색 문장에서 검색 조건을 추출합니다.
                    오늘 날짜는 %s입니다. "내일", "다음 주 금요일" 같은 표현은 이 날짜를 기준으로 YYYY-MM-DD로 바꿉니다.
                    공항은 공항 코드가 아니라 한글 이름(광주, 김포, 제주 등)으로 적습니다.
                    문장에 없는 조건은 null로 둡니다.
                    """.formatted(LocalDate.now()))
            .user(message)
            .call()
            .entity(SearchCriteria.class);

    if (criteria == null || !criteria.hasRoute()) {
        throw new IllegalArgumentException("출발 공항과 도착 공항을 찾지 못했습니다. 예: \"내일 광주에서 제주\"");
    }
    return new Extraction(criteria, true);
}
```

**코드 읽기**

1. `parseWithRegex(message)`: 공항 이름이 정확히 두 개이고 계산이 필요한 날짜 표현("다음 주", "요일" 등)이 없을 때만 결과를 돌려줍니다. 이때는 LLM을 부르지 않으므로 빠르고 결과가 항상 같습니다.
2. 시스템 프롬프트에 오늘 날짜를 넣습니다. LLM은 오늘이 며칠인지 모르기 때문입니다.
3. `.entity(SearchCriteria.class)`: 응답을 문자열이 아니라 자바 record로 받습니다. 이렇게 LLM에게 정해진 JSON 형식으로 답하게 하고 객체로 변환받는 기능을 **Structured Output**이라고 합니다. Spring AI가 `SearchCriteria`의 JSON 스키마를 프롬프트에 덧붙이고, 응답 JSON을 객체로 바꿉니다.
4. `Supplier<ChatClient>`로 받는 이유: 정규식으로 끝나는 요청에서는 `chatClientProvider.get(model)`이 아예 불리지 않습니다. 그래서 Gemini 키가 없어도 Gemini를 고른 한국어 요청은 정규식 경로로 성공합니다.
5. `Extraction(criteria, usedLlm)`: LLM을 썼는지 함께 돌려줘, `FlightSearchCoordinator`가 실행 기록에 `LLM` 또는 `CODE` 단계로 남깁니다.

Structured Output의 대상인 `SearchCriteria`는 필드마다 `@JsonPropertyDescription`을 붙여 둡니다. 이 설명이 LLM에게 전달되는 JSON 스키마의 `description`이 됩니다.

파일: `src/main/java/net/java21/flyschedule/dto/search/SearchCriteria.java`

```java
public record SearchCriteria(
        @JsonPropertyDescription("출발 공항 한글 이름 (예: 광주, 김포, 제주)")
        String departure,

        @JsonPropertyDescription("도착 공항 한글 이름 (예: 제주, 김포, 김해)")
        String arrival,

        @JsonPropertyDescription("출발 날짜, YYYY-MM-DD 형식")
        String date,

        // ... 생략 (afterTime, beforeTime, minPrice, maxPrice)
) {
    // ... 생략 (hasRoute(), label())
}
```

> **주의**: `.entity()`는 LLM이 형식에 맞는 JSON을 돌려줄 때만 성공합니다. 작은 모델은 공항 이름을 영어로 적거나 필드를 빠뜨리기도 합니다. 그래서 `hasRoute()`로 결과를 다시 검사하고, 실패하면 이해할 수 있는 예외 메시지를 던집니다. LLM의 출력은 항상 코드로 검증한다고 기억하세요.

### 확인하기

```bash
./mvnw test -Dtest=SearchCriteriaExtractorTest
```

`regexSkipsLlm`(정규식으로 충분하면 LLM을 호출하지 않는다)과 `usesLlmWhenRegexFails`(정규식이 실패하면 Structured Output을 쓴다)가 이 절의 두 경로를 검증합니다. 실제 LLM 없이 가짜 `ChatClient`로 테스트하므로 Ollama가 꺼져 있어도 통과합니다.

---

## Step 2-4: SearchController

### 왜 필요한가

서비스를 만들었으니 HTTP 요청을 받아 서비스에 넘길 컨트롤러가 필요합니다. 이 프로젝트는 같은 문장을 세 가지 패턴으로 처리해 비교할 수 있도록 엔드포인트 세 개를 둡니다.

### 어떻게 하는가

파일: `src/main/java/net/java21/flyschedule/controller/SearchController.java`

```java
@Slf4j
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final FlightSearchCoordinator coordinator;
    private final ToolCallingSearchService toolCallingService;
    private final FlightSearchOrchestrator orchestrator;

    @PostMapping("/coordinator")
    public SearchResponse coordinator(@RequestBody SearchRequest request) {
        return handle(FlightSearchCoordinator.PATTERN, request, coordinator::coordinate);
    }

    // ... 생략 (/tool-calling, /orchestrator도 같은 모양)

    private SearchResponse handle(String pattern, SearchRequest request,
                                  BiFunction<String, String, SearchResponse> searcher) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            return SearchResponse.fail(pattern, "검색할 문장을 입력해 주세요.", new ExecutionTrace());
        }
        log.info("[{}] 검색 요청 (model={}): {}", pattern, request.model(), request.message());
        return searcher.apply(request.message().trim(), request.model());
    }
}
```

요청 본문은 `SearchRequest` record로 받습니다(`src/main/java/net/java21/flyschedule/dto/request/SearchRequest.java`).

```java
public record SearchRequest(String message, String model) {
}
```

**코드 읽기**

1. 세 엔드포인트가 모두 `handle()`을 거칩니다. 빈 문장 검사와 요청 로그를 한곳에서 처리하고, 실제 검색은 `BiFunction<message, model, SearchResponse>`로 받은 서비스 메서드에 맡깁니다.
2. `model`을 생략하면 `null`이 넘어가고, `ChatClientProvider.get()`이 Ollama를 고릅니다.
3. 세 패턴은 같은 `SearchResponse` 형식으로 응답하므로 화면이 결과를 나란히 비교할 수 있습니다.

| 엔드포인트 | 패턴 | 다음 단계를 누가 정하는가 | 학습 Step |
|------------|------|---------------------------|-----------|
| `POST /api/search/coordinator` | Coordinator 패턴 | 코드 | Step 9 |
| `POST /api/search/tool-calling` | Tool Calling 에이전트 | LLM | Step 10 |
| `POST /api/search/orchestrator` | Orchestrator-Workers 패턴 | LLM(계획·종합) + 코드(실행) | Step 11 |

### 확인하기

```bash
./mvnw test -Dtest=SearchControllerTest
```

---

## Step 2-5: 실행 및 테스트

### 어떻게 하는가

Step 1처럼 `DATA_GO_KR_SERVICE_KEY`를 설정하고 애플리케이션을 실행합니다.

```bash
./mvnw spring-boot:run
```

**1) 정규식으로 처리되는 문장 (LLM 호출 없음)**

```bash
curl -X POST http://localhost:8080/api/search/coordinator \
  -H "Content-Type: application/json" \
  -d '{"message": "내일 광주에서 제주로 가는 항공편 알려줘"}'
```

**2) LLM이 처리하는 문장 (Ollama)**

한국어 공항 이름이 없으면 정규식이 실패하므로 LLM의 `.entity()`가 쓰입니다.

```bash
curl -X POST http://localhost:8080/api/search/coordinator \
  -H "Content-Type: application/json" \
  -d '{"message": "flights from Gwangju to Jeju tomorrow", "model": "ollama"}' \
  -w "\n응답 시간: %{time_total}초\n"
```

**3) 같은 문장을 Gemini로**

```bash
curl -X POST http://localhost:8080/api/search/coordinator \
  -H "Content-Type: application/json" \
  -d '{"message": "flights from Gwangju to Jeju tomorrow", "model": "gemini"}' \
  -w "\n응답 시간: %{time_total}초\n"
```

`GOOGLE_GENAI_API_KEY`를 설정하지 않았다면 `success: false`와 함께 "Gemini API 키가 설정되지 않았습니다..." 안내가 돌아옵니다.

### 확인하기

응답은 다음과 같은 모양입니다(일부 생략). 항공편 번호와 가격은 실행하는 날의 실제 운항 정보에 따라 달라집니다.

```json
{
  "pattern": "coordinator",
  "success": true,
  "message": "광주 → 제주 · 2026-10-17: 12편, 최저가 진에어 LJ0571 07:10 출발 45,900원",
  "results": [
    {
      "label": "광주 → 제주 · 2026-10-17",
      "flightsByAirline": { "아시아나항공": [ ... ], "진에어": [ ... ] },
      "totalCount": 12,
      "cheapest": { "flightId": "LJ0571", "airline": "진에어", "departureTime": "07:10", "price": 45900, ... }
    }
  ],
  "trace": [
    { "order": 1, "kind": "LLM", "actor": "SearchCriteriaExtractor", "title": "LLM으로 검색 조건 추출", ... },
    { "order": 2, "kind": "CODE", "actor": "DateParserAgent", "title": "날짜 변환", ... }
  ],
  "elapsedMs": 4210
}
```

- 1)의 첫 단계는 `"kind": "CODE"`, `"title": "정규식으로 검색 조건 추출"`입니다.
- 2)와 3)의 첫 단계는 `"kind": "LLM"`, `"title": "LLM으로 검색 조건 추출"`입니다.
- `elapsedMs`를 비교하면 LLM 호출이 전체 시간의 대부분을 차지한다는 것을 알 수 있습니다.

브라우저에서는 `http://localhost:8080`에서 문장과 모델을 고르고 세 패턴을 실행해, 결과와 실행 기록(타임라인)을 비교할 수 있습니다.

---

## Step 2-6: ChatClient의 다른 기능

이 프로젝트에서 직접 쓰지는 않지만 알아 두면 좋은 기능입니다. 아래 코드는 설명용 예시이며 프로젝트에 들어 있지 않습니다.

```java
// 대화 기록 넘기기: 이전 메시지 목록(List<Message>)을 함께 보낸다
String answer = chatClient.prompt()
        .messages(history)
        .user(userMessage)
        .call()
        .content();

// 스트리밍: 응답을 토큰 묶음 단위로 받는다 (Flux<String>)
chatClient.prompt()
        .user(question)
        .stream()
        .content()
        .doOnNext(System.out::print)
        .blockLast();   // 예제에서만 끝날 때까지 기다린다
```

**코드 읽기**

- `.messages(history)`: LLM은 이전 대화를 기억하지 못하므로, 대화를 이어 가려면 기록을 매번 함께 보내야 합니다. Spring AI의 `MessageChatMemoryAdvisor`가 이 일을 대신해 줍니다(Step 4 심화 학습).
- `.stream()`: 긴 답변을 화면에 조금씩 보여 줄 때 씁니다.

> **주의**: 이 프로젝트의 Tool 호출 기록(`ToolResultCapture`, Step 4)은 `call()`에서 Tool이 같은 스레드에서 실행된다는 점에 기대고 있습니다. `stream()`에서는 Tool이 다른 스레드에서 실행되므로 이 방식이 동작하지 않습니다. 그래서 이 프로젝트는 `call()`만 씁니다.

---

## Step 2-7: 로그로 프롬프트 확인하기

### 왜 필요한가

`.entity()`가 실패하거나 LLM이 이상한 답을 하면, 실제로 어떤 프롬프트가 나갔는지 봐야 원인을 찾을 수 있습니다. `ChatClientConfig`에서 등록한 `SimpleLoggerAdvisor`가 요청과 응답을 DEBUG 레벨로 남기므로, 그 로거의 레벨만 올리면 됩니다.

### 어떻게 하는가

파일: `src/main/resources/application.yml`에 추가

```yaml
logging:
  level:
    org.springframework.ai.chat.client.advisor: DEBUG
```

### 확인하기

영어 문장으로 다시 검색하면 콘솔에 다음과 같은 로그가 나옵니다(줄여서 표시).

```text
DEBUG o.s.a.c.c.a.SimpleLoggerAdvisor - request: ChatClientRequest[prompt=Prompt{messages=[SystemMessage{textContent='항공편 검색 문장에서 검색 조건을 추출합니다. ...'}, UserMessage{content='flights from Gwangju to Jeju tomorrow', ...}], ...}, context={...}]
DEBUG o.s.a.c.c.a.SimpleLoggerAdvisor - response: { "result" : { "output" : { "text" : "{\"departure\":\"광주\",\"arrival\":\"제주\",\"date\":\"2026-10-17\", ...}", ... } } }
```

시스템 프롬프트 뒤에 Spring AI가 덧붙인 JSON 스키마 지시와, LLM이 돌려준 JSON 문자열을 찾아보세요. Advisor는 Step 4에서 자세히 다룹니다.

---

## 일반적인 문제 해결

### `No qualifying bean of type 'org.springframework.ai.chat.client.ChatClient'`

- `expected single matching bean but found 2`가 함께 나오면, 주입받는 쪽에 `@Qualifier("ollamaChatClient")`처럼 빈 이름을 적지 않은 것입니다.
- 빈이 아예 없다고 나오면 `ChatClientConfig`가 `@Configuration`이고 `net.java21.flyschedule` 패키지 아래에 있는지 확인합니다.

### "LLM 서버에 연결할 수 없습니다"

`ChatClientProvider.describeError()`가 연결 실패를 바꾼 문장입니다. `spring.ai.ollama.base-url`과 Ollama 서버 상태를 확인합니다(Step 1).

### "Gemini API 키가 올바르지 않습니다"

Gemini가 `API key not valid` 오류를 돌려준 경우입니다. `GOOGLE_GENAI_API_KEY` 값과 할당량(Quota)을 확인합니다. 키를 아예 설정하지 않았다면 LLM을 호출하기 전에 `ChatClientProvider.get()`이 "Gemini API 키가 설정되지 않았습니다..."라고 안내합니다.

### "출발 공항과 도착 공항을 찾지 못했습니다"

LLM이 `departure`나 `arrival`을 비워 돌려준 경우입니다. 작은 Ollama 모델에서 자주 생깁니다. Gemini로 바꾸거나, 문장에 한글 공항 이름을 넣어 정규식 경로로 처리되게 합니다.

### 응답이 너무 느림

Gemini로 바꾸거나, `max-tokens`(Ollama)·`max-output-tokens`(Gemini)를 줄여 봅니다. 정규식으로 처리되는 문장은 LLM을 부르지 않으므로 빠릅니다.

---

## 실습 과제

### 과제 1 (기본): 정규식 경로와 LLM 경로 비교

한국어 문장 `내일 광주에서 제주로 가는 항공편 알려줘`와 영어 문장 `flights from Gwangju to Jeju tomorrow`를 각각 `POST /api/search/coordinator`로 보냅니다.

- 힌트: Step 2-5의 curl 명령, 응답의 `trace[0]`
- 완료 기준: 두 응답의 `trace[0].kind`(`CODE`/`LLM`)와 `elapsedMs`를 표로 정리하고, 차이가 나는 이유를 한 문장으로 설명한다.

### 과제 2 (기본): Supplier<ChatClient>의 효과 확인

`GOOGLE_GENAI_API_KEY` 없이 `"model": "gemini"`로 과제 1의 두 문장을 보냅니다.

- 힌트: `SearchCriteriaExtractor.extract()`에서 `chatClient.get()`이 불리는 위치
- 완료 기준: 한국어 문장은 `success: true`, 영어 문장은 키 안내 메시지가 나오는 것을 확인하고, 그 이유를 `Supplier`와 연결해 설명한다.

### 과제 3 (도전): ChatClientProvider 테스트 추가

`ChatClientProviderTest`에 알 수 없는 모델 이름(`"gpt"`, 빈 문자열 `""`)을 넘기는 테스트를 추가합니다.

- 힌트: 기존 `selectsByModelName()`과 `normalize()` 테스트를 본뜹니다.
- 완료 기준: `get("gpt")`와 `get("")`이 Ollama 클라이언트를, `normalize("gpt")`가 `"ollama"`를 돌려준다는 단언이 들어간 테스트가 `./mvnw test -Dtest=ChatClientProviderTest`로 통과한다.

### 과제 4 (도전): @JsonPropertyDescription의 역할 관찰

`SearchCriteria.date`의 설명을 `"출발 날짜"`로 바꾸고(형식 안내 제거), Step 2-7의 로그를 켠 채 영어 문장을 Ollama로 여러 번 보냅니다.

- 힌트: 로그의 request에 포함된 JSON 스키마, response의 `date` 값
- 완료 기준: 설명을 바꾸기 전과 후의 `date` 형식을 비교해 적고, Structured Output에서 필드 설명이 하는 역할을 두세 문장으로 정리한다. 실험 뒤 원래대로 되돌린다.

---

## 요약

- `ChatModel`은 제공자와 통신하고, `ChatClient`는 프롬프트를 조립·호출하는 공통 API입니다.
- `ChatClientConfig`가 모델별 `ChatClient` 빈 두 개를 만들고, Advisor는 기본 등록하되 Tool은 등록하지 않습니다.
- `ChatClientProvider`가 요청의 `model` 값으로 `ChatClient`를 고르고, 키 누락·연결 실패를 안내 문장으로 바꿉니다.
- `SearchCriteriaExtractor`는 정규식을 먼저 쓰고, 실패하면 `.entity(SearchCriteria.class)`로 LLM의 Structured Output을 받습니다.
- 세 엔드포인트 `POST /api/search/{coordinator|tool-calling|orchestrator}`는 같은 `SearchResponse` 형식으로 응답합니다.

지금까지는 LLM이 글(또는 JSON)만 돌려줬습니다. Step 3에서는 LLM이 우리가 만든 자바 메서드를 호출하게 해, 실시간 데이터를 다루게 합니다.

---

## 체크리스트

- [ ] `ChatClient`와 `ChatModel`이 각각 무엇을 하는지 설명할 수 있다. (목표 1)
- [ ] `ChatClientConfig`의 두 빈과 `@Qualifier`의 쓰임, `defaultTools`를 쓰지 않는 이유를 설명할 수 있다. (목표 2)
- [ ] `ChatClientProvider.get()`의 선택 규칙과 `describeError()`가 바꾸는 오류를 설명하고, `ChatClientProviderTest`를 실행했다. (목표 3)
- [ ] `.content()`와 `.entity(Class)`의 차이를 설명하고, `SearchCriteriaExtractorTest`를 실행했다. (목표 4)
- [ ] curl로 `/api/search/coordinator`를 호출해 `trace[0].kind`로 정규식 경로와 LLM 경로를 구분했다. (목표 5)

---

## 다음 단계

[Step 3: Function Calling 기초](step-3-function-calling.md)에서 `@Tool`로 LLM이 호출할 수 있는 자바 메서드를 만들고, 요청마다 `.tools(...)`로 넘기는 방법을 배웁니다.

---

## 참고 자료

- [Spring AI ChatClient 공식 문서](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [ChatClient 생성하기](https://docs.spring.io/spring-ai/reference/api/chatclient.html#_creating_a_chatclient)
- [Ollama 모델 목록](https://ollama.com/models)
- [Gemini 모델 비교](https://ai.google.dev/gemini-api/docs/models)

---

## 심화 학습

### ChatClient 빈과 ChatClient.Builder 빈

`ChatClient.Builder`를 빈으로 등록하고 서비스마다 `build()`하는 방법도 있습니다(설명용 예시).

```java
@Bean
public ChatClient.Builder ollamaChatClientBuilder(@Qualifier("ollamaChatModel") ChatModel model) {
    return ChatClient.builder(model);
}

// 서비스마다 필요한 기본값을 더해 직접 만든다
public MyService(@Qualifier("ollamaChatClientBuilder") ChatClient.Builder builder) {
    this.chatClient = builder.defaultSystem("서비스별 역할").build();
}
```

| 방식 | 장점 | 주의할 점 |
|------|------|-----------|
| `ChatClient` 빈 (이 프로젝트) | 기본 설정이 `ChatClientConfig` 한곳에 모이고, 서비스는 바로 사용 | 서비스별 기본값이 필요하면 `chatClient.mutate()`로 복사한 빌더에서 새로 만듦 |
| `ChatClient.Builder` 빈 | 서비스마다 다른 기본값을 붙이기 쉬움 | Builder는 변경 가능한(mutable) 객체라, 같은 Builder를 여러 곳에서 고치면 설정이 섞일 수 있음 |

이 프로젝트에서 서비스마다 다른 것은 시스템 프롬프트와 Tool 정도이고, 둘 다 요청마다 `.system(...)`, `.tools(...)`로 넘기므로 `ChatClient` 빈 방식을 택했습니다.
