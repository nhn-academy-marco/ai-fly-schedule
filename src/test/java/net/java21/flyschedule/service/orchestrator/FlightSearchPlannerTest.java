package net.java21.flyschedule.service.orchestrator;

import net.java21.flyschedule.dto.search.SearchCriteria;
import net.java21.flyschedule.service.agent.AirportCodeAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("FlightSearchPlanner 테스트")
class FlightSearchPlannerTest {

    private FlightSearchPlanner planner;

    @BeforeEach
    void setUp() {
        planner = new FlightSearchPlanner(new AirportCodeAgent());
    }

    private static SearchCriteria task(String dep, String arr, String date) {
        return new SearchCriteria(dep, arr, date, null, null, null, null);
    }

    private static SearchPlan plan(SearchCriteria... tasks) {
        return new SearchPlan("테스트", Arrays.asList(tasks));
    }

    @Test
    @DisplayName("LLM이 나눈 작업 수만큼 계획을 돌려준다 (2 × 2 = 4개)")
    void llmDecidesTaskCount() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        SearchPlan fromLlm = plan(
                task("김포", "제주", "2026-03-27"), task("김포", "제주", "2026-03-28"),
                task("광주", "제주", "2026-03-27"), task("광주", "제주", "2026-03-28"));
        when(chatClient.prompt().system(anyString()).user(anyString()).call().entity(org.mockito.ArgumentMatchers.<org.springframework.ai.converter.StructuredOutputConverter<SearchPlan>>any()))
                .thenReturn(fromLlm);

        SearchPlan result = planner.plan("금요일이나 토요일에 김포나 광주에서 제주", chatClient);

        assertThat(result.tasks()).hasSize(4);
    }

    @Test
    @DisplayName("알 수 없는 공항, 출발=도착, 경로 없는 작업은 버린다")
    void dropsInvalidTasks() {
        SearchPlan result = planner.validate(plan(
                task("광주", "제주", "2026-03-27"),
                task("서울", "제주", "2026-03-27"),
                task("제주", "제주", "2026-03-27"),
                task(null, "제주", "2026-03-27")));

        assertThat(result.tasks()).containsExactly(task("광주", "제주", "2026-03-27"));
    }

    @Test
    @DisplayName("중복 작업을 없애고 최대 4개로 제한한다")
    void dedupeAndLimit() {
        SearchPlan result = planner.validate(plan(
                task("광주", "제주", "2026-03-27"), task("광주", "제주", "2026-03-27"),
                task("김포", "제주", "2026-03-27"), task("김해", "제주", "2026-03-27"),
                task("대구", "제주", "2026-03-27"), task("청주", "제주", "2026-03-27")));

        assertThat(result.tasks()).hasSize(FlightSearchPlanner.MAX_TASKS).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("쓸 수 있는 작업이 없거나 계획이 비어 있으면 예외를 던진다")
    void emptyPlan() {
        assertThatThrownBy(() -> planner.validate(plan(task("서울", "도쿄", null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> planner.validate(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> planner.validate(new SearchPlan("x", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> planner.validate(new SearchPlan("x", List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("'광주'와 '광주공항'은 같은 공항으로 보고 중복·출발=도착을 걸러 낸다")
    void normalizesAirportNames() {
        SearchPlan result = planner.validate(plan(
                task("광주", "제주", "2026-03-27"),
                task("광주공항", "제주국제공항", "2026-03-27"),
                task("김해", "부산", "2026-03-27")));

        assertThat(result.tasks()).containsExactly(task("광주", "제주", "2026-03-27"));
    }

    @Test
    @DisplayName("문장에 시간·가격이 없으면 LLM이 지어낸 시간·가격 조건을 지운다 (운영에서 '제일 싼'에 1,000원 이상을 붙인 사례)")
    void removesInventedConditions() {
        SearchCriteria invented = new SearchCriteria("김포", "제주", "2026-10-16", null, "10:00", 1000, null);

        SearchPlan result = planner.validate(plan(invented), "이번 주 금요일 김포에서 제주 가는 제일 싼 항공편");

        assertThat(result.tasks()).containsExactly(task("김포", "제주", "2026-10-16"));
    }

    @Test
    @DisplayName("문장에 시간·가격이 있으면 조건을 그대로 둔다")
    void keepsRealConditions() {
        SearchCriteria real = new SearchCriteria("김포", "제주", "2026-10-16", "14:00", null, null, 60000);

        SearchPlan result = planner.validate(plan(real), "금요일 김포에서 제주 오후 2시 이후 6만원 이하");

        assertThat(result.tasks()).containsExactly(real);
    }

    @Test
    @DisplayName("LLM에게는 날짜를 붙인 문장을 보낸다")
    void sendsAnnotatedMessage() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call()
                .entity(org.mockito.ArgumentMatchers.<org.springframework.ai.converter.StructuredOutputConverter<SearchPlan>>any()))
                .thenReturn(plan(task("광주", "제주", "2026-10-17")));

        planner.plan("내일 광주에서 제주", chatClient);

        String tomorrow = java.time.LocalDate.now().plusDays(1).toString();
        org.mockito.Mockito.verify(chatClient.prompt().system(anyString())).user("내일(" + tomorrow + ") 광주에서 제주");
    }

    @Test
    @DisplayName("'광주공항'처럼 공항이 붙은 이름도 유효한 공항으로 본다")
    void acceptsAirportSuffix() {
        SearchPlan result = planner.validate(plan(task("광주공항", "제주국제공항", "2026-03-27")));

        assertThat(result.tasks()).hasSize(1);
    }
}
