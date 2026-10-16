# API 테스트 가이드

IntelliJ IDEA의 HTTP Client로 공공데이터 포털 API와 이 애플리케이션의 검색 API를 직접 호출해 보는 파일 모음입니다. 01~03은 Step 6~7에서 공공데이터 포털의 응답 구조를 살펴볼 때, 04는 Step 9~11에서 세 패턴의 응답(`SearchResponse`)을 비교할 때 씁니다.

## 파일 목록

| 파일 | 설명 |
|------|------|
| [http-client.env.json](./http-client.env.json) | 공개해도 되는 환경 변수 (`baseUrl`, `appUrl`) |
| `http-client.private.env.json` | 서비스 키 (직접 만들며, git에 올리지 않음) |
| [01-항공편-조회.http](./01-항공편-조회.http) | 전국 공항 → 제주 노선 항공편 조회 (공공데이터 포털) |
| [02-공항-목록.http](./02-공항-목록.http) | 전체 공항 목록 조회 (공공데이터 포털) |
| [03-항공사-목록.http](./03-항공사-목록.http) | 전체 항공사 목록 조회 (공공데이터 포털) |
| [04-패턴-비교.http](./04-패턴-비교.http) | 로컬 애플리케이션의 세 가지 검색 패턴 호출 |

## 사용 방법

### 1. 서비스 키 설정

[공공데이터 포털](https://www.data.go.kr/data/15098526/openapi.do)에서 발급받은 서비스 키를 이 디렉터리의 `http-client.private.env.json`에 적습니다. 이 파일은 `.gitignore`에 등록되어 있어 커밋되지 않습니다.

```json
{
  "dev": {
    "serviceKey": "발급받은_서비스_키"
  }
}
```

### 2. 요청 실행

1. `.http` 파일을 열고 에디터 오른쪽 위에서 환경을 `dev`로 고릅니다.
2. 요청 왼쪽의 ▶ 버튼을 누르면 응답이 아래에 표시됩니다.
3. 파일 위쪽의 ▶▶(Run All)로 모든 요청을 차례로 실행할 수 있습니다.

`04-패턴-비교.http`는 애플리케이션을 먼저 실행해야 합니다(`./mvnw spring-boot:run`). 요청 본문의 `model`은 `"ollama"` 또는 `"gemini"`입니다.

### 3. 확인하기

- 01~03: 응답의 `response.header.resultCode`가 `"00"`이면 성공입니다. 서비스 키가 틀리면 다른 코드와 오류 메시지가 옵니다.
- 04: 세 응답 모두 `"success": true`이고, `trace` 배열의 `kind`가 패턴마다 다르게 나오면 성공입니다. Coordinator는 `CODE`(정규식 추출에 실패하면 첫 단계만 `LLM`), Tool Calling은 `LLM`과 `TOOL`, Orchestrator-Workers는 `LLM`, `WORKER`, `CODE`가 함께 나옵니다.

> **팁**
> `01-항공편-조회.http`는 응답 구조를 보기 위한 예제라 `numOfRows=10`으로 10건만 받습니다. 응답의 `totalCount`와 비교해 보세요. 애플리케이션의 `ApiClientService`는 김포→제주처럼 하루 100편이 넘는 노선도 잘리지 않도록 `numOfRows=500`(`MAX_ROWS`)으로 요청합니다.

## 공항 코드

| 공항 | 코드 | 공항 | 코드 |
|------|------|------|------|
| 김포 | NAARKSS | 인천 | NAARKSI |
| 김해 | NAARKPK | 제주 | NAARKPC |
| 광주 | NAARKJJ | 대구 | NAARKTN |
| 청주 | NAARKTU | 여수 | NAARKJY |
| 울산 | NAARKPU | 포항경주 | NAARKTH |
| 무안 | NAARKJB | 사천 | NAARKPS |
| 양양 | NAARKNY | 군산 | NAARKJK |
| 원주 | NAARKNW | | |

공항 목록 API(`GetArprtList`) 기준입니다.
