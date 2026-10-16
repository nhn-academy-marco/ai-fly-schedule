package net.java21.flyschedule.service.agent;

import net.java21.flyschedule.dto.response.FlightInfoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 시간 필터링 전문 에이전트
 *
 * 출발 시간을 기준으로 항공편을 필터링합니다.
 */
@Slf4j
@Service
public class TimeFilterAgent {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmm");

    /**
     * 한글 시간 표현: "오후 2시", "오전9시 30분", "14시", "14시 30분"
     */
    private static final Pattern KOREAN_TIME_PATTERN =
            Pattern.compile("(오전|오후)?\\s*(\\d{1,2})\\s*시(?:\\s*(\\d{1,2})\\s*분)?");

    /**
     * 숫자 시간 표현: "14:00", "9:30", "1400", "930"
     */
    private static final Pattern NUMERIC_TIME_PATTERN =
            Pattern.compile("(\\d{1,2}):?(\\d{2})");

    /**
     * 시간 문자열을 파싱합니다.
     *
     * @param timeInput 시간 문자열 (예: "14:00", "9:30", "1400", "오후 2시", "오전 9시 30분")
     * @return LocalTime 객체
     */
    public LocalTime parseTime(String timeInput) {
        if (timeInput == null || timeInput.isBlank()) {
            throw new IllegalArgumentException("시간을 입력해주세요.");
        }

        String normalized = timeInput.trim();

        try {
            Matcher korean = KOREAN_TIME_PATTERN.matcher(normalized);
            if (korean.find()) {
                int hour = Integer.parseInt(korean.group(2));
                int minute = korean.group(3) != null ? Integer.parseInt(korean.group(3)) : 0;
                String meridiem = korean.group(1);
                if ("오후".equals(meridiem) && hour < 12) {
                    hour += 12; // 오후 2시 → 14시
                } else if ("오전".equals(meridiem) && hour == 12) {
                    hour = 0;   // 오전 12시 → 0시
                }
                return LocalTime.of(hour, minute);
            }

            Matcher numeric = NUMERIC_TIME_PATTERN.matcher(normalized);
            if (numeric.matches()) {
                return LocalTime.of(Integer.parseInt(numeric.group(1)), Integer.parseInt(numeric.group(2)));
            }
        } catch (DateTimeException e) {
            // 25시, 70분처럼 범위를 벗어난 값은 아래에서 함께 처리
        }

        throw new IllegalArgumentException("시간 형식이 올바르지 않습니다. (HH:mm 또는 '오후 2시' 등)");
    }

    /**
     * 기준 시간 이후에 출발하는 항공편만 필터링합니다.
     *
     * @param flights   전체 항공편 목록
     * @param afterTime 기준 시간
     * @return 필터링된 항공편 목록
     */
    public List<FlightInfoResponse> filterAfterTime(List<FlightInfoResponse> flights, LocalTime afterTime) {
        if (flights == null || flights.isEmpty()) {
            return List.of();
        }

        return flights.stream()
                .filter(flight -> {
                    try {
                        LocalTime departureTime = parseDepartureTime(flight.getDepartureTime());
                        return !departureTime.isBefore(afterTime);
                    } catch (Exception e) {
                        log.warn("시간 파싱 실패: {}", flight.getDepartureTime());
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * 기준 시간 이전에 출발하는 항공편만 필터링합니다.
     *
     * @param flights    전체 항공편 목록
     * @param beforeTime 기준 시간
     * @return 필터링된 항공편 목록
     */
    public List<FlightInfoResponse> filterBeforeTime(List<FlightInfoResponse> flights, LocalTime beforeTime) {
        if (flights == null || flights.isEmpty()) {
            return List.of();
        }

        return flights.stream()
                .filter(flight -> {
                    try {
                        LocalTime departureTime = parseDepartureTime(flight.getDepartureTime());
                        return !departureTime.isAfter(beforeTime);
                    } catch (Exception e) {
                        log.warn("시간 파싱 실패: {}", flight.getDepartureTime());
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * 항공편의 출발 시간 문자열을 파싱합니다.
     *
     * @param departureTime 출발 시간 문자열 (예: "202503090955")
     * @return LocalTime 객체
     */
    private LocalTime parseDepartureTime(String departureTime) {
        if (departureTime == null || departureTime.length() < 12) {
            throw new IllegalArgumentException("잘못된 출발 시간 형식");
        }

        // "202503090955" 형식에서 시간 부분(0955) 추출
        String timePart = departureTime.substring(8, 12);
        return LocalTime.parse(timePart, TIME_FORMATTER);
    }
}
