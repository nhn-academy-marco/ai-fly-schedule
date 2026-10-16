package net.java21.flyschedule.service.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DateContext 테스트")
class DateContextTest {

    @Test
    @DisplayName("오늘이 금요일이면 '이번 주 금요일'은 오늘이다 (운영에서 작은 모델이 하루 늦게 계산한 사례)")
    void fridayIsToday() {
        String table = DateContext.describe(LocalDate.of(2026, 10, 16));

        assertThat(table).isEqualTo("""
                오늘: 2026-10-16(금)
                내일: 2026-10-17(토), 모레: 2026-10-18(일)
                이번 주: 월 2026-10-12, 화 2026-10-13, 수 2026-10-14, 목 2026-10-15, 금 2026-10-16, 토 2026-10-17, 일 2026-10-18
                다음 주: 월 2026-10-19, 화 2026-10-20, 수 2026-10-21, 목 2026-10-22, 금 2026-10-23, 토 2026-10-24, 일 2026-10-25""");
    }

    @Test
    @DisplayName("주는 월요일부터 시작한다 (일요일이면 그 주의 월요일은 6일 전)")
    void weekStartsOnMonday() {
        String table = DateContext.describe(LocalDate.of(2026, 10, 18));

        assertThat(table).contains("이번 주: 월 2026-10-12").contains("다음 주: 월 2026-10-19");
    }

    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 16);

    @Test
    @DisplayName("annotate: '이번 주 금요일이나 토요일'은 앞의 '이번 주'를 뒤 요일에도 적용한다 (운영에서 작은 모델이 틀린 문장)")
    void annotateCarriesWeekForward() {
        assertThat(DateContext.annotate("이번 주 금요일이나 토요일에 김포나 광주에서 제주", FRIDAY))
                .isEqualTo("이번 주 금요일(2026-10-16)이나 토요일(2026-10-17)에 김포나 광주에서 제주");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "'이번 주 토요일 김포에서 제주', '이번 주 토요일(2026-10-17) 김포에서 제주'",
            "'다음 주 월요일 광주에서 제주', '다음 주 월요일(2026-10-19) 광주에서 제주'",
            "'다다음 주 금요일', '다다음 주 금요일(2026-10-30)'",
            "'수요일 출발', '수요일(2026-10-21) 출발'",
            "'금요일 출발', '금요일(2026-10-16) 출발'",
            "'내일 광주에서 제주', '내일(2026-10-17) 광주에서 제주'",
            "'모레랑 글피', '모레(2026-10-18)랑 글피(2026-10-19)'",
            "'내일모레 출발', '내일모레(2026-10-18) 출발'"
    })
    @DisplayName("annotate: 주·요일·상대 날짜 표현 옆에 날짜를 붙인다 (요일만 있으면 오늘 포함 가장 가까운 날)")
    void annotateExpressions(String message, String expected) {
        assertThat(DateContext.annotate(message, FRIDAY)).isEqualTo(expected);
    }

    @Test
    @DisplayName("annotate: 이미 날짜가 붙은 표현과 날짜 표현이 없는 문장은 그대로 둔다")
    void annotateIsIdempotent() {
        String once = DateContext.annotate("이번 주 토요일 김포에서 제주", FRIDAY);

        assertThat(DateContext.annotate(once, FRIDAY)).isEqualTo(once);
        assertThat(DateContext.annotate("광주에서 제주", FRIDAY)).isEqualTo("광주에서 제주");
        assertThat(DateContext.annotate(null, FRIDAY)).isNull();
    }

    @Test
    @DisplayName("월이 바뀌는 주도 올바르게 만든다")
    void crossesMonth() {
        assertThat(DateContext.describe(LocalDate.of(2026, 10, 30)))
                .contains("모레: 2026-11-01(일)")
                .contains("다음 주: 월 2026-11-02");
    }
}
