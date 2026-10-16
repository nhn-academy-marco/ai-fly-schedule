package net.java21.flyschedule.service.ai;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 *
 * <p>날짜표를 줘도 작은 모델은 여전히 틀릴 수 있습니다(운영에서 "이번 주 토요일"을 하루 늦게 고름).
 * 그래서 {@link #annotate}로 문장 속 날짜 표현 옆에 코드가 계산한 날짜를 붙여서 보냅니다.
 * 코드가 확실하게 할 수 있는 일은 LLM에게 맡기지 않는다는 원칙입니다.
 */
public final class DateContext {

    /** 오늘, 내일, 모레, 글피 (내일모레는 모레) */
    private static final Pattern RELATIVE_DAY = Pattern.compile("(내일모레|오늘|내일|모레|글피)(?!\\s*\\()");
    /** [이번 주|다음 주|다다음 주] X요일 */
    private static final Pattern WEEKDAY =
            Pattern.compile("(?:(이번\\s*주|다음\\s*주|다다음\\s*주)\\s*)?([월화수목금토일])요일(?!\\s*\\()");
    private static final String WEEKDAYS = "월화수목금토일";

    private DateContext() {
    }

    public static String annotate(String message) {
        return annotate(message, LocalDate.now());
    }

    /**
     * 문장 속 날짜 표현 옆에 날짜를 붙입니다.
     *
     * <pre>
     * "이번 주 금요일이나 토요일에 김포에서 제주" (오늘 2026-10-16 금)
     *   → "이번 주 금요일(2026-10-16)이나 토요일(2026-10-17)에 김포에서 제주"
     * </pre>
     *
     * <p>"금요일이나 토요일"처럼 앞에 나온 "이번 주/다음 주"는 뒤의 요일에도 적용합니다.
     * 주 표현 없이 요일만 있으면 오늘 이후 가장 가까운 그 요일(오늘 포함)로 봅니다.
     */
    public static String annotate(String message, LocalDate today) {
        if (message == null || message.isBlank()) {
            return message;
        }
        String withRelative = RELATIVE_DAY.matcher(message).replaceAll(match -> {
            int days = switch (match.group(1)) {
                case "오늘" -> 0;
                case "내일" -> 1;
                case "모레", "내일모레" -> 2;
                default -> 3; // 글피
            };
            return Matcher.quoteReplacement(match.group() + "(" + today.plusDays(days) + ")");
        });

        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Integer[] weekOffset = {null}; // 앞에서 나온 주 표현을 뒤의 요일에도 적용
        return WEEKDAY.matcher(withRelative).replaceAll(match -> {
            if (match.group(1) != null) {
                String week = match.group(1).replaceAll("\\s", "");
                weekOffset[0] = switch (week) {
                    case "이번주" -> 0;
                    case "다음주" -> 1;
                    default -> 2; // 다다음주
                };
            }
            int dayIndex = WEEKDAYS.indexOf(match.group(2));
            LocalDate date = weekOffset[0] != null
                    ? monday.plusWeeks(weekOffset[0]).plusDays(dayIndex)
                    : today.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(dayIndex + 1)));
            return Matcher.quoteReplacement(match.group() + "(" + date + ")");
        });
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
