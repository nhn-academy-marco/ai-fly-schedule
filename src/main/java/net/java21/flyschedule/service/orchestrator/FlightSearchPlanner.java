package net.java21.flyschedule.service.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.java21.flyschedule.dto.search.SearchCriteria;
import net.java21.flyschedule.service.agent.AirportCodeAgent;
import net.java21.flyschedule.service.ai.DateContext;
import net.java21.flyschedule.service.ai.LenientJsonCleaner;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Orchestrator의 계획 단계: LLM이 요청을 하위 검색 작업으로 나눕니다.
 *
 * <p>"금요일이나 토요일에 김포나 광주에서 제주"처럼 조건이 여러 개인 요청은 2 × 2 = 4개 작업이 됩니다.
 * 몇 개로 나눌지는 코드가 아니라 LLM이 요청을 보고 정합니다. 이것이 Coordinator 패턴과의 핵심 차이입니다.
 * 다만 LLM이 만든 계획은 그대로 믿지 않고, 알 수 없는 공항을 걸러 내고 작업 수를 제한합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlightSearchPlanner {

    /** 한 요청에서 실행할 최대 작업 수 (API 호출 수 제한) */
    static final int MAX_TASKS = 4;

    private final AirportCodeAgent airportCodeAgent;

    public SearchPlan plan(String message, ChatClient chatClient) {
        SearchPlan plan = chatClient.prompt()
                .system("""
                        당신은 국내선 항공편 검색 작업을 나누는 Orchestrator입니다.
                        사용자 요청에 출발지, 도착지, 날짜 후보가 여러 개 있으면 조합마다 검색 작업을 하나씩 만드세요.
                        조건이 하나뿐이면 작업도 하나입니다. 작업은 최대 %d개까지만 만드세요.
                        "이번 주 금요일" 같은 날짜는 계산하지 말고 아래 날짜표에서 찾아 YYYY-MM-DD로 적으세요.
                        공항은 한글 이름(광주, 김포, 제주 등)으로 적고, 없는 조건은 null로 둡니다.

                        %s
                        """.formatted(MAX_TASKS, DateContext.describe()))
                .user(DateContext.annotate(message)) // 날짜 표현 옆에 코드가 계산한 날짜를 붙여 보낸다
                .call()
                // 작은 모델이 JSON 앞뒤에 문장을 붙여도 JSON 부분만 읽는다
                .entity(new BeanOutputConverter<>(SearchPlan.class, null, new LenientJsonCleaner()));

        return validate(plan, message);
    }

    /**
     * LLM이 만든 계획을 검증합니다. 공항을 알 수 없는 작업은 버리고, 중복을 없애고, 개수를 제한합니다.
     */
    SearchPlan validate(SearchPlan plan) {
        return validate(plan, null);
    }

    /**
     * @param message 사용자 문장. 주어지면 문장에 없는 시간·가격 조건(LLM이 지어낸 조건)을 지운다
     */
    SearchPlan validate(SearchPlan plan, String message) {
        if (plan == null || plan.tasks() == null) {
            throw new IllegalArgumentException("검색 계획을 세우지 못했습니다. 출발지와 도착지를 포함해 다시 입력해 주세요.");
        }

        // "광주"와 "광주공항"은 같은 공항이므로, 출발=도착 검사와 중복 제거는 공항 코드로 비교한다
        Set<List<Object>> seen = new HashSet<>();
        List<SearchCriteria> tasks = plan.tasks().stream()
                .filter(SearchCriteria::hasRoute)
                .map(task -> removeInventedConditions(task, message))
                .filter(task -> airportCodeAgent.isValidAirport(task.departure())
                        && airportCodeAgent.isValidAirport(task.arrival()))
                .filter(task -> !departureCode(task).equals(arrivalCode(task)))
                .filter(task -> seen.add(Arrays.asList(departureCode(task), arrivalCode(task), task.date(),
                        task.afterTime(), task.beforeTime(), task.minPrice(), task.maxPrice())))
                .limit(MAX_TASKS)
                .toList();

        if (tasks.size() < plan.tasks().size()) {
            log.warn("계획 검증: 작업 {}개 중 {}개만 사용", plan.tasks().size(), tasks.size());
        }
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("검색할 수 있는 작업이 없습니다. 지원하는 공항 이름으로 다시 입력해 주세요.");
        }
        return new SearchPlan(plan.reasoning(), tasks);
    }

    private static final Pattern TIME_IN_MESSAGE = Pattern.compile("\\d{1,2}\\s*시|\\d{1,2}:\\d{2}");
    private static final Pattern PRICE_IN_MESSAGE = Pattern.compile("\\d+\\s*(만\\s*)?원");

    /**
     * 문장에 시간·가격이 없는데 LLM이 붙인 조건을 지웁니다.
     * 운영에서 작은 모델이 "제일 싼 항공편"이라는 말에 "10:00 이전, 1,000원 이상"을 지어낸 사례가 있었습니다.
     * ("제일 싼"은 가격 범위가 아니라 결과를 비교하라는 뜻이므로 범위 조건이 필요 없습니다.)
     */
    static SearchCriteria removeInventedConditions(SearchCriteria task, String message) {
        if (message == null) {
            return task;
        }
        boolean hasTime = TIME_IN_MESSAGE.matcher(message).find();
        boolean hasPrice = PRICE_IN_MESSAGE.matcher(message).find();
        return new SearchCriteria(task.departure(), task.arrival(), task.date(),
                hasTime ? task.afterTime() : null,
                hasTime ? task.beforeTime() : null,
                hasPrice ? task.minPrice() : null,
                hasPrice ? task.maxPrice() : null);
    }

    private String departureCode(SearchCriteria task) {
        return airportCodeAgent.getAirportCode(task.departure());
    }

    private String arrivalCode(SearchCriteria task) {
        return airportCodeAgent.getAirportCode(task.arrival());
    }
}
