package com.nhnacademy.flyschedule.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM을 활용한 자연어 분석 서비스
 *
 * 사용자의 자연어 메시지에서 항공편 검색 파라미터를 추출합니다.
 * Spring AI ChatClient를 사용하여 구조화된 데이터를 추출합니다.
 */
@Slf4j
@Service
public class LlmAnalysisService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatClient chatClient;

    public LlmAnalysisService(@Qualifier("ollamaChatClientBuilder") ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 자연어 메시지에서 항공편 검색 파라미터 추출
     *
     * @param message 사용어 자연어 메시지 (예: "2026년3월25일 광주에서 제주로 가는 10시 이후 항공편 조회 해줘")
     * @return 추출된 파라미터 Map
     */
    public Map<String, Object> extractFlightSearchParams(String message) {
        log.info("LLM 분석 요청: {}", message);

        // 먼저 정규식 기반 파싱 시도 (LLM 실패 시 fallback)
        try {
            Map<String, Object> parsed = parseWithRegex(message);
            if (parsed != null && !parsed.isEmpty()) {
                log.info("정규식 파싱 성공: {}", parsed);
                return parsed;
            }
        } catch (Exception e) {
            log.warn("정규식 파싱 실패, LLM 시도: {}", e.getMessage());
        }

        String prompt = buildAnalysisPrompt(message);

        try {
            // ChatClient를 사용하여 JSON 응답 수신
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (response != null && !response.isBlank()) {
                log.info("LLM 분석 결과: {}", response);
                return parseResponse(response);
            } else {
                throw new RuntimeException("LLM 응답이 비어있습니다.");
            }

        } catch (Exception e) {
            log.error("LLM 호출 실패, 정규식 fallback 사용", e);
            // LLM 실패 시 정규식 기반 파싱 사용
            Map<String, Object> fallbackResult = parseWithRegex(message);
            if (fallbackResult != null && !fallbackResult.isEmpty()) {
                return fallbackResult;
            }
            throw new RuntimeException("자연어 분석에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * LLM 분석 프롬프트 구성
     */
    private String buildAnalysisPrompt(String message) {
        return String.format("""
            당신은 항공편 검색 시스템의 파라미터 추출 전문가입니다.

            사용자의 자연어 메시지를 분석하여 항공편 검색에 필요한 파라미터를 JSON 형식으로 추출하세요.

            ## 추출할 파라미터
            - departure: 출발 공항 **이름** (예: 광주, 김포, 제주, 인천, 부산) - **반드시 한글 공항 이름만 사용**
            - arrival: 도착 공항 **이름** (예: 광주, 김포, 제주, 인천, 부산) - **반드시 한글 공항 이름만 사용**
            - date: 날짜 (YYYY-MM-DD 형식, 예: 2026-03-25)
            - afterTime: 기준 시간 (HH:MM 형식, 예: 10:00) - "이후" 조건만
            - beforeTime: 기준 시간 (HH:MM 형식, 예: 18:00) - "이전" 조건만
            - minPrice: 최소 가격 (숫자만)
            - maxPrice: 최대 가격 (숫자만)

            ## 규칙
            1. 파라미터가 없으면 null로 설정
            2. 날짜는 반드시 YYYY-MM-DD 형식으로 변환
            3. 시간은 24시간제 HH:MM 형식
            4. "내일", "모레" 등 상대적 날짜 표현은 오늘 날짜를 기준으로 계산하여 YYYY-MM-DD로 변환
            5. 오늘 날짜: 2026-03-10
            6. "오후 3시", "14시" 등의 표현은 HH:MM으로 변환
            7. **중요: 공항은 반드시 한글 이름(광주, 김포, 제주 등)만 사용하고, 공항 코드(NAARKJJ 등)는 절대 사용하지 마세요**

            ## 출력 형식
            반드시 아래 JSON 형식만 출력하고, 다른 설명은 하지 마세요:

            ```json
            {
              "departure": "광주",
              "arrival": "제주",
              "date": "2026-03-25",
              "afterTime": "10:00",
              "beforeTime": null,
              "minPrice": null,
              "maxPrice": null
            }
            ```

            ## 사용자 메시지
            %s
            """, message);
    }

    /**
     * LLM 응답을 파싱하여 Map으로 변환
     */
    private Map<String, Object> parseResponse(String response) {
        try {
            // JSON 블록 추출 (```json ... ```)
            String jsonContent = response;
            if (response.contains("```json")) {
                jsonContent = response.substring(response.indexOf("```json") + 7);
                jsonContent = jsonContent.substring(0, jsonContent.indexOf("```"));
            } else if (response.contains("```")) {
                jsonContent = response.substring(response.indexOf("```") + 3);
                jsonContent = jsonContent.substring(0, jsonContent.indexOf("```"));
            }

            jsonContent = jsonContent.trim();

            @SuppressWarnings("unchecked")
            Map<String, Object> params = objectMapper.readValue(jsonContent, Map.class);

            // null 값 정리
            params.entrySet().removeIf(entry -> entry.getValue() == null ||
                    (entry.getValue() instanceof String && ((String) entry.getValue()).isBlank()));

            log.info("파싱된 파라미터: {}", params);
            return params;

        } catch (Exception e) {
            log.error("LLM 응답 파싱 실패: {}", response, e);
            throw new RuntimeException("자연어 분석에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * 정규식 기반 파싱 (LLM 실패 시 fallback)
     */
    private Map<String, Object> parseWithRegex(String message) {
        Map<String, Object> params = new HashMap<>();

        try {
            // 날짜 파싱 (2026년3월25일, 2026-03-25 등)
            Pattern datePattern = Pattern.compile("(\\d{4})[년\\-](\\d{1,2})[월\\-](\\d{1,2})일?");
            Matcher dateMatcher = datePattern.matcher(message);
            if (dateMatcher.find()) {
                String year = dateMatcher.group(1);
                String month = String.format("%02d", Integer.parseInt(dateMatcher.group(2)));
                String day = String.format("%02d", Integer.parseInt(dateMatcher.group(3)));
                params.put("date", year + "-" + month + "-" + day);
            } else if (message.contains("내일")) {
                LocalDate tomorrow = LocalDate.now().plusDays(1);
                params.put("date", tomorrow.format(DateTimeFormatter.ISO_LOCAL_DATE));
            } else if (message.contains("모레")) {
                LocalDate dayAfterTomorrow = LocalDate.now().plusDays(2);
                params.put("date", dayAfterTomorrow.format(DateTimeFormatter.ISO_LOCAL_DATE));
            }

            // 공항 이름 파싱
            String[] airports = {"광주", "김포", "제주", "인천", "부산", "대구", "울산", "청주", "양양"};
            for (String airport : airports) {
                if (message.contains(airport)) {
                    if (!params.containsKey("departure")) {
                        params.put("departure", airport);
                    } else if (!params.containsKey("arrival")) {
                        params.put("arrival", airport);
                        break;
                    }
                }
            }

            // 시간 파싱 (10시 이후, 9시 이전 등)
            Pattern timePattern = Pattern.compile("(\\d{1,2})시\\s*(이후|이전|전후)?");
            Matcher timeMatcher = timePattern.matcher(message);
            while (timeMatcher.find()) {
                String hour = String.format("%02d", Integer.parseInt(timeMatcher.group(1)));
                String time = hour + ":00";
                String condition = timeMatcher.group(2);

                if ("이후".equals(condition)) {
                    params.put("afterTime", time);
                } else if ("이전".equals(condition) || "전".equals(condition)) {
                    params.put("beforeTime", time);
                }
            }

            // 가격 파싱 (3만원에서 5만원 사이 등)
            Pattern pricePattern = Pattern.compile("(\\d+)만원");
            Matcher priceMatcher = pricePattern.matcher(message);
            if (priceMatcher.find()) {
                params.put("minPrice", String.valueOf(Integer.parseInt(priceMatcher.group(1)) * 10000));
            }
            if (priceMatcher.find()) {
                params.put("maxPrice", String.valueOf(Integer.parseInt(priceMatcher.group(1)) * 10000));
            }

            // null 값 정리
            params.entrySet().removeIf(entry -> entry.getValue() == null ||
                    (entry.getValue() instanceof String && ((String) entry.getValue()).isBlank()));

            return params;

        } catch (Exception e) {
            log.warn("정규식 파싱 실패: {}", e.getMessage());
            return new HashMap<>();
        }
    }
}
