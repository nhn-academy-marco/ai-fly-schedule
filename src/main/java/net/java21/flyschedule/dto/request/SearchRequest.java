package net.java21.flyschedule.dto.request;

/**
 * 자연어 검색 요청
 *
 * @param message 사용자 문장 (예: "내일 광주에서 제주로 가는 항공편")
 * @param model   사용할 LLM ("ollama" 또는 "gemini", 생략하면 ollama)
 */
public record SearchRequest(String message, String model) {
}
