# API 테스트 가이드

이 디렉토리는 IntelliJ IDEA의 HTTP Client 기능을 사용하여 공공데이터 포털 API를 테스트하기 위한 파일들입니다.

---

## 📁 파일 목록

| 파일 | 설명 |
|------|------|
| [http-client.env.json](../../http-client.env.json) | 환경 변수 설정 (dev/prod) |
| [01-항공편-조회.http](./01-항공편-조회.http) | 전국 공항 → 제주 노선 항공편 조회 |
| [02-공항-목록.http](./02-공항-목록.http) | 전체 공항 목록 조회 |
| [03-항공사-목록.http](./03-항공사-목록.http) | 전체 항공사 목록 조회 |

---

## 🚀 사용 방법

### 1. 환경 선택

IntelliJ IDEA 에디터 우측 상단의 드롭다운에서 환경 선택:
- **dev**- 개발 환경 (테스트용 인증키)
- **prod**- 운영 환경 (운영 인증키)

### 2. 요청 실행

1. `.http` 파일 열기
2. 요청 왼쪽의 ▶ 버튼 클릭
3. 응답이 하단에 표시됨

### 3. 전체 요청 실행

1. 파일 상단의 ▶▶ (Run All) 버튼 클릭
2. 모든 요청 순차 실행

---

## 🔧 환경 변수 설정

### http-client.env.json

```json
{
  "dev": {
    "baseUrl": "http://apis.data.go.kr/1613000/DmstcFlightNvgInfoService",
    "baseUrlShort": "https://apis.data.go.kr/1613000/DmstcFlightNvgInfo",
    "serviceKey": "개발_인증키"
  },
  "prod": {
    "baseUrl": "http://apis.data.go.kr/1613000/DmstcFlightNvgInfoService",
    "baseUrlShort": "https://apis.data.go.kr/1613000/DmstcFlightNvgInfo",
    "serviceKey": "운영_인증키"
  }
}
```

### 사용 가능한 변수

| 변수 | 설명 |
|------|------|
| `{{baseUrl}}` | 항공편/공항 API 기본 URL |
| `{{baseUrlShort}}` | 항공사 API 기본 URL |
| `{{serviceKey}}` | URL 인코딩된 인증키 |
| `{{serviceKeyPlain}}` | 일반 인증키 |

---

## 🔑 인증키 변경 방법

### dev 환경 인증키 변경

1. `http-client.env.json` 파일 열기
2. `dev.serviceKey` 값 변경
3. 저장 후 다시 요청 실행

### prod 환경 설정

1. `http-client.env.json` 파일 열기
2. `prod.serviceKey`에 운영 인증키 입력
3. IntelliJ 에디터 우측 상단에서 `prod` 선택

---

## 📋 공항 코드 참고

| 공항명 | 코드 | 지역 |
|--------|------|------|
| 김포 | NAARKSS | 서울 |
| 제주 | NAARKPC | 제주 |
| 김해 | NAARKJB | 부산 |
| 광주 | NAARKJJ | 광주 |
| 대구 | NAARKTN | 대구 |
| 청주 | NAARKNJ | 충북 |
| 무안 | NAARKJJ | 전남 |
| 울산 | NAARKNU | 울산 |
| 여수 | NAARKJY | 전남 |
| 양양 | NAARKNY | 강원 |
| 사천 | NAARKPS | 경남 |

---

## ✅ 응답 예시

### 항공편 조회 응답

```json
{
  "response": {
    "header": {
      "resultCode": "00",
      "resultMsg": "NORMAL SERVICE."
    },
    "body": {
      "items": {
        "item": [
          {
            "vihicleId": "OZ8141",
            "airlineNm": "아시아나항공",
            "depPlandTime": "202503090955",
            "arrPlandTime": "202503091045",
            "economyCharge": "57900",
            "prestigeCharge": "0",
            "depAirportNm": "광주",
            "arrAirportNm": "제주"
          }
        ]
      },
      "numOfRows": 10,
      "pageNo": 1,
      "totalCount": 5
    }
  }
}
```

### 공항 목록 응답

```json
{
  "response": {
    "header": {
      "resultCode": "00",
      "resultMsg": "NORMAL SERVICE."
    },
    "body": {
      "items": {
        "item": [
          {
            "airportId": "NAARKSS",
            "airportNm": "김포"
          },
          {
            "airportId": "NAARKPC",
            "airportNm": "제주"
          }
        ]
      }
    }
  }
}
```

### 항공사 목록 응답

```json
{
  "response": {
    "header": {
      "resultCode": "00",
      "resultMsg": "NORMAL SERVICE."
    },
    "body": {
      "items": {
        "item": [
          {
            "airlineId": "AAR",
            "airlineNm": "아시아나항공"
          },
          {
            "airlineId": "ABL",
            "airlineNm": "에어부산"
          }
        ]
      }
    }
  }
}
```

---

## 🐛 에러 코드

| 코드 | 메시지 | 설명 |
|------|--------|------|
| 00 | NORMAL SERVICE | 정상 |
| 99 | INVALID_REQUEST_PARAMETER_ERROR | 잘못된 요청 파라미터 |
| 30 | SERVICE_KEY_IS_NOT_REGISTERED_ERROR | 등록되지 않은 서비스키 |

---

## 📝 참고사항

1. **날짜 형식**: `YYYYMMDD` (예: 20250309)
2. **페이지네이션**: `pageNo`, `numOfRows`로 조절 가능
3. **데이터 포맷**: JSON 또는 XML 선택 가능
4. **일일 호출 제한**: 개발계정 10,000회/일

---

## 🔗 관련 링크

- [공공데이터포털](https://www.data.go.kr/)
- [국내항공운항정보 API](https://www.data.go.kr/data/15098526/openapi.do#/)
