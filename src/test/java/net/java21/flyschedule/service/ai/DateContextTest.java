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

    @Test
    @DisplayName("월이 바뀌는 주도 올바르게 만든다")
    void crossesMonth() {
        assertThat(DateContext.describe(LocalDate.of(2026, 10, 30)))
                .contains("모레: 2026-11-01(일)")
                .contains("다음 주: 월 2026-11-02");
    }
}
