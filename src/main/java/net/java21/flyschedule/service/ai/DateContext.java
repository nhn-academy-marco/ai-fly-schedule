package net.java21.flyschedule.service.ai;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 프롬프트에 넣을 날짜표를 만듭니다.
 *
 * <p>왜 필요한가: 작은 모델은 "이번 주 금요일"을 오늘 날짜에서 계산하다 자주 틀립니다.
 * 운영에서 qwen2.5:3b는 오늘이 2026-10-16(금)일 때 "이번 주 금요일이나 토요일"을 10-17, 10-18로 계산했습니다.
 * 계산을 시키지 말고 이번 주·다음 주 날짜표를 주면, LLM은 표에서 찾기만 하면 됩니다.
 *
 * <pre>
 * 오늘: 2026-10-16(금)
 * 내일: 2026-10-17(토), 모레: 2026-10-18(일)
 * 이번 주: 월 2026-10-12, 화 2026-10-13, 수 2026-10-14, 목 2026-10-15, 금 2026-10-16, 토 2026-10-17, 일 2026-10-18
 * 다음 주: 월 2026-10-19, … 일 2026-10-25
 * </pre>
 */
public final class DateContext {

    private DateContext() {
    }

    public static String describe() {
        return describe(LocalDate.now());
    }

    public static String describe(LocalDate today) {
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return """
                오늘: %s
                내일: %s, 모레: %s
                이번 주: %s
                다음 주: %s""".formatted(
                withDay(today), withDay(today.plusDays(1)), withDay(today.plusDays(2)),
                week(monday), week(monday.plusWeeks(1)));
    }

    private static String week(LocalDate monday) {
        return IntStream.range(0, 7)
                .mapToObj(monday::plusDays)
                .map(day -> dayName(day) + " " + day)
                .collect(Collectors.joining(", "));
    }

    private static String withDay(LocalDate date) {
        return date + "(" + dayName(date) + ")";
    }

    private static String dayName(LocalDate date) {
        return date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN);
    }
}
