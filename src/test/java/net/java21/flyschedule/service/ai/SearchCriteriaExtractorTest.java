package net.java21.flyschedule.service.ai;

import net.java21.flyschedule.dto.search.SearchCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.client.ChatClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("SearchCriteriaExtractor 단위 테스트")
class SearchCriteriaExtractorTest {

    private SearchCriteriaExtractor extractor;
    private ChatClient chatClient;

    @BeforeEach
    void setUp() {
        extractor = new SearchCriteriaExtractor();
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    }

    private SearchCriteria regex(String message) {
        return extractor.parseWithRegex(message).orElseThrow();
    }

    // ===== 정규식 파싱 =====

    @Test
    @DisplayName("출발·도착 공항과 상대 날짜(내일)를 추출한다")
    void basic() {
        SearchCriteria criteria = regex("내일 광주에서 제주로 가는 항공편 알려줘");

        assertThat(criteria.departure()).isEqualTo("광주");
        assertThat(criteria.arrival()).isEqualTo("제주");
        assertThat(criteria.date()).isEqualTo(LocalDate.now().plusDays(1).toString());
    }

    @Test
    @DisplayName("날짜가 없으면 오늘, '모레'는 이틀 뒤")
    void relativeDates() {
        assertThat(regex("광주에서 제주").date()).isEqualTo(LocalDate.now().toString());
        assertThat(regex("모레 광주에서 제주").date()).isEqualTo(LocalDate.now().plusDays(2).toString());
    }

    @ParameterizedTest
    @CsvSource({
            "'2026년 3월 25일 김포에서 제주', '2026-03-25'",
            "'2026-3-5 김포에서 제주', '2026-03-05'",
            "'2026.12.01 김포에서 제주', '2026-12-01'"
    })
    @DisplayName("절대 날짜를 YYYY-MM-DD로 바꾼다")
    void absoluteDates(String message, String expected) {
        assertThat(regex(message).date()).isEqualTo(expected);
    }

    @Test
    @DisplayName("공항 목록 순서가 아니라 '~에서'가 붙은 공항을 출발지로 본다")
    void departureByEseo() {
        SearchCriteria criteria = regex("광주로 가는 제주에서 출발 항공편");

        assertThat(criteria.departure()).isEqualTo("제주");
        assertThat(criteria.arrival()).isEqualTo("광주");
    }

    @Test
    @DisplayName("'~에서'가 없으면 먼저 나온 공항을 출발지로 본다")
    void departureByOrder() {
        SearchCriteria criteria = regex("제주 광주 항공편");

        assertThat(criteria.departure()).isEqualTo("제주");
        assertThat(criteria.arrival()).isEqualTo("광주");
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "'광주에서 제주 10시 이후', '10:00', null",
            "'광주에서 제주 오후 2시 이후', '14:00', null",
            "'광주에서 제주 오후 2시 30분 이후', '14:30', null",
            "'광주에서 제주 오전 9시부터', '09:00', null",
            "'광주에서 제주 18시 이전', null, '18:00'",
            "'광주에서 제주 오후 6시까지', null, '18:00'",
            "'광주에서 제주 오전 12시 이후', '00:00', null"
    })
    @DisplayName("시간 조건을 24시간 HH:mm으로 바꾼다")
    void times(String message, String after, String before) {
        SearchCriteria criteria = regex(message);

        assertThat(criteria.afterTime()).isEqualTo(after);
        assertThat(criteria.beforeTime()).isEqualTo(before);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "'광주에서 제주 6만원 이하', null, 60000",
            "'광주에서 제주 6만원', null, 60000",
            "'광주에서 제주 4만원 이상', 40000, null",
            "'광주에서 제주 3만원에서 5만원 사이', 30000, 50000",
            "'광주에서 제주 5만원에서 3만원 사이', 30000, 50000"
    })
    @DisplayName("가격 조건: 하나면 이하/이상으로 판단하고, 둘이면 범위로 본다")
    void prices(String message, Integer min, Integer max) {
        SearchCriteria criteria = regex(message);

        assertThat(criteria.minPrice()).isEqualTo(min);
        assertThat(criteria.maxPrice()).isEqualTo(max);
    }

    @ParameterizedTest
    @CsvSource({
            "'다음 주 금요일 광주에서 제주'",
            "'이번 주 토요일 김포에서 제주'",
            "'3일 뒤 광주에서 제주'",
            "'3월 25일 광주에서 제주'"
    })
    @DisplayName("계산이 필요한 날짜 표현이 있으면 정규식은 포기한다 (오늘 날짜로 잘못 검색하지 않음)")
    void giveUpOnComplexDates(String message) {
        assertThat(extractor.parseWithRegex(message)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "'제주 가는 항공편'",
            "'서울에서 부산'",
            "'김포나 광주에서 제주'"
    })
    @DisplayName("공항이 정확히 두 개가 아니면 정규식은 포기한다 (LLM에 맡김)")
    void giveUpWhenNotExactlyTwoAirports(String message) {
        assertThat(extractor.parseWithRegex(message)).isEmpty();
    }

    // ===== LLM 사용 여부 =====

    @Test
    @DisplayName("정규식으로 충분하면 LLM을 호출하지 않는다")
    void regexSkipsLlm() {
        SearchCriteriaExtractor.Extraction extraction = extractor.extract("내일 광주에서 제주", () -> chatClient);

        assertThat(extraction.usedLlm()).isFalse();
        verifyNoInteractions(chatClient);
    }

    @Test
    @DisplayName("정규식이 실패하면 LLM의 Structured Output을 사용한다")
    void usesLlmWhenRegexFails() {
        SearchCriteria fromLlm = new SearchCriteria("김포", "제주", "2026-03-27", null, null, null, null);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().entity(org.mockito.ArgumentMatchers.<org.springframework.ai.converter.StructuredOutputConverter<SearchCriteria>>any()))
                .thenReturn(fromLlm);

        SearchCriteriaExtractor.Extraction extraction =
                extractor.extract("이번 주 금요일 서울에서 제주도 가는 비행기", () -> chatClient);

        assertThat(extraction.usedLlm()).isTrue();
        assertThat(extraction.criteria()).isEqualTo(fromLlm);
    }

    @Test
    @DisplayName("LLM도 출발·도착을 찾지 못하면 예외를 던진다")
    void llmWithoutRoute() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().entity(org.mockito.ArgumentMatchers.<org.springframework.ai.converter.StructuredOutputConverter<SearchCriteria>>any()))
                .thenReturn(new SearchCriteria(null, "제주", null, null, null, null, null));

        assertThatThrownBy(() -> extractor.extract("제주 가고 싶어", () -> chatClient))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("출발 공항과 도착 공항");
    }
}
