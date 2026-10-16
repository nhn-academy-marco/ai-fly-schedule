package net.java21.flyschedule.service.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 날짜 파싱 전문 Agent
 *
 * 사용자의 자연어 날짜 표현을 API 요청 형식으로 변환합니다.
 * 지원하는 표현: "내일", "모레", "글피", "2026-03-25"
 */
@Slf4j
@Service
public class DateParserAgent {

    private static final DateTimeFormatter API_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter INPUT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 사용자의 날짜 표현을 API 요청 형식(YYYYMMDD)으로 변환합니다.
     *
     * @param dateInput 날짜 입력 (예: "내일", "2026-03-25", "20260325")
     * @return API 요청 형식의 날짜 (예: "20250309")
     */
    public String parseDate(String dateInput) {
        log.info("DateParserAgent: 날짜 파싱 요청 - {}", dateInput);

        // 자연어 표현 처리
        if (dateInput == null || dateInput.isBlank()) {
            return LocalDate.now().format(API_DATE_FORMATTER);
        }

        String normalized = dateInput.trim();

        // 이미 API 형식(YYYYMMDD)인 경우 그대로 반환
        if (normalized.matches("\\d{8}")) {
            log.info("DateParserAgent: 이미 API 형식의 날짜 - {}", normalized);
            return normalized;
        }

        normalized = normalized.toLowerCase();

        // 상대적 날짜 표현 처리
        return switch (normalized) {
            case "오늘" -> LocalDate.now().format(API_DATE_FORMATTER);
            case "내일" -> LocalDate.now().plusDays(1).format(API_DATE_FORMATTER);
            case "모레", "내일모레" -> LocalDate.now().plusDays(2).format(API_DATE_FORMATTER);
            case "글피" -> LocalDate.now().plusDays(3).format(API_DATE_FORMATTER);
            default -> parseSpecificDate(normalized);
        };
    }

    /**
     * 특정 날짜 형식(YYYY-MM-DD)을 파싱합니다.
     *
     * @param dateInput 날짜 문자열
     * @return API 요청 형식의 날짜
     * @throws IllegalArgumentException 잘못된 날짜 형식
     */
    private String parseSpecificDate(String dateInput) {
        try {
            LocalDate date = LocalDate.parse(dateInput, INPUT_DATE_FORMATTER);
            return date.format(API_DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            log.error("잘못된 날짜 형식: {}", dateInput);
            throw new IllegalArgumentException("날짜 형식이 올바르지 않습니다. (YYYY-MM-DD 또는 '내일', '모레' 등)");
        }
    }
}
