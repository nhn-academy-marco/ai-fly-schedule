package net.java21.flyschedule.service.ai;

import lombok.extern.slf4j.Slf4j;
import net.java21.flyschedule.dto.search.SearchCriteria;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 자연어 메시지에서 검색 조건(SearchCriteria)을 뽑는 서비스
 *
 * <p>1차로 정규식을 시도하고, 출발·도착 공항을 찾지 못했을 때만 LLM의 Structured Output을 사용합니다.
 * 정규식으로 충분한 요청에 LLM을 쓰지 않으면 응답이 빠르고 결과도 항상 같습니다.
 */
@Slf4j
@Service
public class SearchCriteriaExtractor {

    /**
     * 정규식 파싱에서 인식하는 공항 이름 (AirportCodeAgent가 변환할 수 있는 이름)
     */
    private static final List<String> AIRPORT_NAMES = List.of(
            "김포", "인천", "김해", "부산", "사천", "울산", "광주", "여수", "무안", "군산",
            "대구", "포항", "청주", "양양", "원주", "제주");

    private static final Pattern DATE_PATTERN =
            Pattern.compile("(\\d{4})[년\\-.]\\s*(\\d{1,2})[월\\-.]\\s*(\\d{1,2})일?");
    private static final Pattern TIME_PATTERN =
            Pattern.compile("(오전|오후)?\\s*(\\d{1,2})\\s*시(?:\\s*(\\d{1,2})\\s*분)?\\s*(이후|부터|이전|까지|전)?");
    private static final Pattern COMPLEX_DATE_PATTERN =
            Pattern.compile("요일|주말|이번\\s*주|다음\\s*주|다다음|글피|\\d+\\s*일\\s*(뒤|후)|\\d{1,2}\\s*월\\s*\\d{1,2}\\s*일");
    private static final Pattern PRICE_PATTERN =
            Pattern.compile("(\\d+)\\s*만\\s*원\\s*(이하|미만|까지|이상|초과|부터)?");

    /**
     * 추출 결과
     *
     * @param criteria 검색 조건
     * @param usedLlm  LLM을 사용했는지 여부 (false면 정규식으로 처리)
     */
    public record Extraction(SearchCriteria criteria, boolean usedLlm) {
    }

    /**
     * @param chatClient LLM이 필요할 때만 꺼내 쓴다 (정규식으로 끝나면 모델 설정을 확인하지 않음)
     */
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
                // 작은 모델이 JSON 앞뒤에 문장을 붙여도 JSON 부분만 읽는다
                .entity(new BeanOutputConverter<>(SearchCriteria.class, null, new LenientJsonCleaner()));

        if (criteria == null || !criteria.hasRoute()) {
            throw new IllegalArgumentException("출발 공항과 도착 공항을 찾지 못했습니다. 예: \"내일 광주에서 제주\"");
        }
        return new Extraction(criteria, true);
    }

    /**
     * 정규식 기반 파싱
     *
     * <p>공항 이름이 정확히 두 개일 때만 결과를 돌려주고, 그렇지 않으면 빈 값을 돌려줘 LLM 분석으로 넘깁니다.
     */
    Optional<SearchCriteria> parseWithRegex(String message) {
        // 공항: 메시지에 나온 순서대로 정렬한 뒤, "~에서"가 붙은 공항을 출발지로 본다
        List<String> found = AIRPORT_NAMES.stream()
                .filter(message::contains)
                .sorted(Comparator.comparingInt(message::indexOf))
                .toList();
        if (found.size() != 2) {
            // 공항이 0~1개면 정보 부족, 3개 이상이면 "김포나 광주에서 제주"처럼 해석이 필요하므로 LLM에 맡긴다
            return Optional.empty();
        }
        String first = found.get(0);
        String second = found.get(1);
        boolean secondIsDeparture = message.contains(second + "에서") && !message.contains(first + "에서");
        String departure = secondIsDeparture ? second : first;
        String arrival = secondIsDeparture ? first : second;

        // 날짜: 2026년 3월 25일, 2026-03-25, 내일, 모레 (없으면 오늘)
        String date = LocalDate.now().toString();
        Matcher dateMatcher = DATE_PATTERN.matcher(message);
        boolean hasAbsoluteDate = dateMatcher.find();
        if (!hasAbsoluteDate && COMPLEX_DATE_PATTERN.matcher(message).find()) {
            // "다음 주 금요일", "3일 뒤"처럼 계산이 필요한 날짜는 정규식으로 처리하지 않고 LLM에 맡긴다
            return Optional.empty();
        }
        if (hasAbsoluteDate) {
            date = "%s-%02d-%02d".formatted(dateMatcher.group(1),
                    Integer.parseInt(dateMatcher.group(2)), Integer.parseInt(dateMatcher.group(3)));
        } else if (message.contains("모레")) {
            date = LocalDate.now().plusDays(2).toString();
        } else if (message.contains("내일")) {
            date = LocalDate.now().plusDays(1).toString();
        }

        // 시간: 10시 이후, 오후 2시 30분 이전
        String afterTime = null;
        String beforeTime = null;
        Matcher timeMatcher = TIME_PATTERN.matcher(message);
        while (timeMatcher.find()) {
            int hour = Integer.parseInt(timeMatcher.group(2));
            int minute = timeMatcher.group(3) != null ? Integer.parseInt(timeMatcher.group(3)) : 0;
            if ("오후".equals(timeMatcher.group(1)) && hour < 12) {
                hour += 12;
            } else if ("오전".equals(timeMatcher.group(1)) && hour == 12) {
                hour = 0;
            }
            String time = "%02d:%02d".formatted(hour, minute);
            String condition = timeMatcher.group(4);
            if ("이후".equals(condition) || "부터".equals(condition)) {
                afterTime = time;
            } else if ("이전".equals(condition) || "까지".equals(condition) || "전".equals(condition)) {
                beforeTime = time;
            }
        }

        // 가격: 3만원에서 5만원 사이, 6만원 이하, 4만원 이상
        List<Integer> prices = new ArrayList<>();
        String priceCondition = null;
        Matcher priceMatcher = PRICE_PATTERN.matcher(message);
        while (priceMatcher.find()) {
            prices.add(Integer.parseInt(priceMatcher.group(1)) * 10000);
            priceCondition = priceMatcher.group(2);
        }
        Integer minPrice = null;
        Integer maxPrice = null;
        if (prices.size() >= 2) {
            minPrice = Math.min(prices.get(0), prices.get(1));
            maxPrice = Math.max(prices.get(0), prices.get(1));
        } else if (prices.size() == 1) {
            boolean isMin = "이상".equals(priceCondition) || "초과".equals(priceCondition) || "부터".equals(priceCondition);
            if (isMin) {
                minPrice = prices.get(0);
            } else {
                maxPrice = prices.get(0);
            }
        }

        return Optional.of(new SearchCriteria(departure, arrival, date, afterTime, beforeTime, minPrice, maxPrice));
    }
}
