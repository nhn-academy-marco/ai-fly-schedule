package com.nhnacademy.flyschedule.service.agent;

import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 시간 필터링 A2A 에이전트
 *
 * 출발 시간을 기준으로 항공편을 필터링합니다.
 */
@Slf4j
@Service
public class TimeFilterAgent {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmm");

    /**
     * 시간 문자열을 파싱합니다.
     *
     * @param timeInput 시간 문자열 (예: "14:00", "오후 2시", "1400")
     * @return LocalTime 객체
     */
    public LocalTime parseTime(String timeInput) {
        if (timeInput == null || timeInput.isBlank()) {
            throw new IllegalArgumentException("시간을 입력해주세요.");
        }

        String normalized = timeInput.trim().toLowerCase();

        // "오후 2시" 또는 "오후2시" 형식 처리
        if (normalized.contains("오후")) {
            // 숫자만 추출 (공백/문자 제거)
            String numbersOnly = normalized.replaceAll("[^0-9]", "");
            if (!numbersOnly.isEmpty()) {
                int hour = Integer.parseInt(numbersOnly);
                if (hour < 12) {
                    hour += 12; // 오후 시간 변환
                }
                if (hour >= 24) {
                    hour = 12; // 오후 12시는 12시
                }
                return LocalTime.of(hour, 0);
            }
        }

        // "오전 9시" 또는 "오전9시" 형식 처리
        if (normalized.contains("오전")) {
            // 숫자만 추출 (공백/문자 제거)
            String numbersOnly = normalized.replaceAll("[^0-9]", "");
            if (!numbersOnly.isEmpty()) {
                int hour = Integer.parseInt(numbersOnly);
                if (hour == 12) {
                    hour = 0; // 자정
                }
                return LocalTime.of(hour, 0);
            }
        }

        // "HH:mm" 또는 "HHmm" 형식 처리
        try {
            String cleaned = normalized.replace(":", "");
            return LocalTime.parse(cleaned, TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("시간 형식이 올바르지 않습니다. (HH:mm 또는 '오후 2시' 등)");
        }
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
        if (departureTime == null || departureTime.length() < 8) {
            throw new IllegalArgumentException("잘못된 출발 시간 형식");
        }

        // "202503090955" 형식에서 시간 부분(0955) 추출
        String timePart = departureTime.substring(8, 12);
        return LocalTime.parse(timePart, TIME_FORMATTER);
    }
}
