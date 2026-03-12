# Gemini (Google GenAI) 설정 가이드

## 개요

이 문서는 Spring AI와 Google GenAI API(Gemini)를 사용한 자연어 항공편 검색 시스템의 설정을 설명합니다.

## 설정 파일 위치

- **소스**: `src/main/resources/application.yml`
- **빌드**: `target/classes/application.yml`

## Gemini 설정 파라미터

### 1. API 키

```yaml
spring.ai.google:
  genai:
    api-key: AIzaSyA6XO9NuGKguis8ckJ17FnteAKfqiT_XA0
```

| 항목 | 설명 |
|------|------|
| `api-key` | Google AI Studio에서 발급받은 API 키 |
| **발급처** | [https://makersuite.google.com/app/apikey](https://makersuite.google.com/app/apikey) |
| **비고** | 유출되지 않도록 주의 필요 (`.gitignore`에 추가 권장) |

---

### 2. 모델 설정

```yaml
chat:
  options:
    model: gemini-2.5-flash
```

| 파라미터 | 값 | 설명 |
|----------|-----|------|
| `model` | `gemini-2.5-flash` | Google의 Gemini 2.5 Flash 모델 |
| **특징** | **속도** | 빠른 응답 속도 (실시간 검색에 적합) |
| | **비용** | 저렴한 가격 |
| | **용도** | Function Calling, MCP Tool 호출 |

**사용 가능한 다른 모델:**
- `gemini-2.5-flash` - 빠르고 경량 (현재 사용)
- `gemini-2.5-pro` - 더 강력한 성능
- `gemini-2.0-flash` - 이전 버전

---

### 3. Temperature (온도)

```yaml
temperature: 0.0
```

| 값 | 범위 | 설명 |
|----|------|------|
| `0.0` | 0.0 ~ 2.0 | **결정적(Deterministic)** 응답 |
| | | 항상 같은 입력에 같은 출력 |
| | | → **검색 시스템에 적합** |

**Temperature 값에 따른 동작:**

| 값 | 동작 | 사용 사례 |
|----|------|-----------|
| `0.0` ~ `0.3` | 결정적, 일관된 응답 | 검색, 분류, 요약 |
| `0.4` ~ `0.7` | 균형 잡힌 창의성 | 대화, 질문-답변 |
| `0.8` ~ `2.0` | 창의적, 무작위적 | 창작, 아이디어 생성 |

---

### 4. Max Output Tokens (최대 출력 토큰)

```yaml
max-output-tokens: 256
```

| 파라미터 | 값 | 설명 |
|----------|-----|------|
| `max-output-tokens` | `256` | LLM이 생성할 수 있는 **최대 토큰 수** |
| **1 토큰 ≈** | 약 4글자 (영문 기준) | 256 토큰 ≈ 1024글자 |
| **현재 설정 의도** | **간단한 응답**만 생성 | Tool 호출 위주 |

**권장 설정:**

| 용도 | `max-output-tokens` | 설명 |
|------|---------------------|------|
| **Tool 호출 (현재)** | 256 ~ 512 | 간단한 확인 메시지만 필요 |
| **요약** | 512 ~ 1024 | 중간 길이의 요약 |
| **상세 설명** | 1024 ~ 2048 | 긴 분석, 보고서 |

---

### 5. Top-P (Nucleus Sampling)

```yaml
top-p: 0.9
```

| 파라미터 | 값 | 설명 |
|----------|-----|------|
| `top-p` | `0.9` | **누적 확률 분포**에서 상위 90%만 고려 |
| **의미** | 가능성 있는 상위 토큰만 샘플링 | |
| **현재 설정** | `0.9` = 약간의 다양성 허용 | |

**Top-P 값에 따른 동작:**

| 값 | 동작 | 사용 사례 |
|----|------|-----------|
| `0.1` ~ `0.5` | 매우 보수적, 일관된 응답 | 검색, 분류 |
| `0.6` ~ `0.9` | 균형 잡힌 다양성 | 일반적인 대화 |
| `0.95` ~ `1.0` | 높은 다양성 | 창의적 작업 |

**참고**: `Top-P`와 `Temperature`는 함께 사용되지만, 보통 둘 중 하나만 조정합니다.

---

### 6. Top-K

```yaml
top-k: 40
```

| 파라미터 | 값 | 설명 |
|----------|-----|------|
| `top-k` | `40` | 각 단계에서 **상위 K개 후보**만 고려 |
| **의미** | 다음 토큰을 선택할 때 확률이 높은 40개만 고려 | |
| **현재 설정** | `40` = 적절한 다양성과 일관성 | |

**Top-K 값에 따른 동작:**

| 값 | 동작 | 사용 사례 |
|----|------|-----------|
| `1` ~ `10` | 매우 보수적, 일관된 응답 | 검색, 분류 |
| `20` ~ `50` | 균형 잡힌 다양성 | 일반적인 작업 |
| `50` ~ `100` | 높은 다양성 | 창의적 작업 |

---

## 현재 설정의 최적화

```yaml
spring.ai.google:
  genai:
    api-key: ${GEMINI_API_KEY}
    chat:
      options:
        model: gemini-2.5-flash
        temperature: 0.0          # 결정적 응답
        max-output-tokens: 256    # 간단한 응답
        top-p: 0.9                # 균형 잡힌 다양성
        top-k: 40                 # 균형 잡힌 일관성
```

### 이 설정이 적합한 이유

1. **검색 시스템 특성**
   - Temperature 0.0: 항상 일관된 파라미터 추출
   - Function Calling에 적합

2. **속도 최적화**
   - gemini-2.5-flash: 빠른 응답 속도
   - max-output-tokens 256: 불필요한 긴 응답 방지

3. **안정성**
   - Top-K, Top-P 중간값: 일관성과 다양성의 균형

---

## 다른 설정 비교

### Ollama 설정 (비교)

```yaml
spring.ai.ollama:
  chat:
    options:
      model: qwen2.5:latest
      temperature: 0.0
      timeout: 300s
      max-tokens: 256
      top-p: 0.9
      top-k: 40
```

| 항목 | Gemini | Ollama |
|------|--------|--------|
| **속도** | ⚡ 빠름 (3~5초) | 🐢 느림 (30~60초) |
| **비용** | 💰 유료 (저렴) | 🆓 무료 |
| **안정성** | ✅ 높음 | ⚠️ 중간 |
| **추천用途** | 실시간 검색 | 테스트, 개발 |

---

## 환경 변수로 분리 (권장)

API 키를 코드에서 분리하여 보안을 강화하세요:

### application.yml
```yaml
spring.ai.google:
  genai:
    api-key: ${GEMINI_API_KEY}
```

### 터미널
```bash
export GEMINI_API_KEY=AIzaSyA6XO9NuGKguis8ckJ17FnteAKfqiT_XA0
mvn spring-boot:run
```

### IntelliJ IDEA
Run Configuration → Environment Variables:
```
GEMINI_API_KEY=AIzaSyA6XO9NuGKguis8ckJ17FnteAKfqiT_XA0
```

---

## 문제 해결

### 1. "API key was reported as leaked" 에러

**원인**: API 키가 GitHub 등에 노출되어 Google이 차단

**해결**:
1. [Google AI Studio](https://makersuite.google.com/app/apikey)에서 새 API 키 발급
2. 기존 키 삭제
3. `application.yml` 업데이트
4. `.gitignore`에 추가:
   ```
   application.yml
   ```

### 2. "No value present" 에러

**원인**: LLM 응답 생성 실패 (복잡한 프롬프트)

**해결**:
- 간단한 시스템 프롬프트 사용
- `max-output-tokens` 제한
- try-catch로 예외 처리

### 3. 응답 속도가 느린 경우

**해결**:
- `gemini-2.5-flash` 사용 (현재)
- `max-output-tokens` 줄이기
- `temperature` 0.0 ~ 0.3으로 낮추기

---

## 참고 자료

- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [Google GenAI API](https://ai.google.dev/gemini-api/docs)
- [Gemini Models](https://ai.google.dev/gemini-api/docs/models/gemini)

---

## 변경 이력

| 날짜 | 버전 | 변경 내용 |
|------|------|-----------|
| 2026-03-12 | 1.0 | 초기 문서 작성 |
