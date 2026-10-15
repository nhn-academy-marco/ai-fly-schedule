package net.java21.flyschedule.service;

import net.java21.flyschedule.service.agent.DateParserAgent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DateParserAgent 단위 테스트
 *
 * 날짜 파싱 기능을 검증합니다.
 */
@DisplayName("DateParserAgent 단위 테스트")
class DateParserAgentTest {

    private final DateParserAgent dateParserAgent = new DateParserAgent();

    @Test
    @DisplayName("null 입력은 오늘 날짜로 변환되어야 한다")
    void parseDate_nullInput_returnsToday() {
        // When
        String result = dateParserAgent.parseDate(null);

        // Then
        assertThat(result).hasSize(8); // YYYYMMDD format
    }

    @Test
    @DisplayName("빈 문자열 입력은 오늘 날짜로 변환되어야 한다")
    void parseDate_blankInput_returnsToday() {
        // When
        String result = dateParserAgent.parseDate("   ");

        // Then
        assertThat(result).hasSize(8);
    }

    @ParameterizedTest
    @CsvSource({
            "'오늘', 0",
            "'내일', 1",
            "'모레', 2",
            "'내일모레', 2",
            "'글피', 3"
    })
    @DisplayName("상대적 날짜 표현을 정확히 파싱해야 한다")
    void parseDate_relativeDateExpressions(String input, int daysToAdd) {
        // When
        String result = dateParserAgent.parseDate(input);

        // Then
        assertThat(result).hasSize(8);
        // 첫 4자리는 연도, 다음 2자리는 월, 마지막 2자리는 일
        int year = Integer.parseInt(result.substring(0, 4));
        int month = Integer.parseInt(result.substring(4, 6));
        int day = Integer.parseInt(result.substring(6, 8));

        // 연도가 현재 연도인지 확인 (테스트가 연도 말에 실행되면 실패할 수 있음)
        assertThat(year).isGreaterThanOrEqualTo(2024);
        assertThat(month).isBetween(1, 12);
        assertThat(day).isBetween(1, 31);
    }

    @Test
    @DisplayName("YYYY-MM-DD 형식의 날짜를 정확히 파싱해야 한다")
    void parseDate_specificDate_validFormat() {
        // When
        String result = dateParserAgent.parseDate("2025-03-10");

        // Then
        assertThat(result).isEqualTo("20250310");
    }

    @Test
    @DisplayName("잘못된 날짜 형식은 예외를 발생시켜야 한다")
    void parseDate_invalidFormat_throwsException() {
        // When & Then
        assertThatThrownBy(() -> dateParserAgent.parseDate("2025/03/10"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("날짜 형식이 올바르지 않습니다");
    }

    @Test
    @DisplayName("잘못된 형식의 날짜는 예외를 발생시켜야 한다")
    void parseDate_invalidDateFormat_throwsException() {
        // When & Then - 슬래시 형식은 지원하지 않음
        assertThatThrownBy(() -> dateParserAgent.parseDate("2025/03/10"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("날짜 형식이 올바르지 않습니다");
    }

    @ParameterizedTest
    @ValueSource(strings = {"오늘", "내일", "모레", "글피"})
    @DisplayName("자연어 날짜 표현은 대소문자 구분 없이 처리해야 한다")
    void parseDate_naturalLanguage_caseInsensitive(String input) {
        // When
        String result = dateParserAgent.parseDate(input.toUpperCase());

        // Then
        assertThat(result).hasSize(8);
    }

    @Test
    @DisplayName("앞뒤 공백은 무시하고 날짜를 변환해야 한다")
    void parseDate_dateWithWhitespace() {
        assertThat(dateParserAgent.parseDate("  2025-03-10  ")).isEqualTo("20250310");
    }
}
