package net.java21.flyschedule.mcp;

import net.java21.flyschedule.dto.response.FlightInfoResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.execution.DefaultToolCallResultConverter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.java21.flyschedule.support.TestFlights.flight;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CompactFlightResultConverter 테스트")
class CompactFlightResultConverterTest {

    private final CompactFlightResultConverter converter = new CompactFlightResultConverter();

    @Test
    @DisplayName("항공사별로 한 줄씩, 항공편명·시각·가격만 남기고 마지막에 총 편수와 최저가를 붙인다")
    void compactLines() {
        Map<String, List<FlightInfoResponse>> grouped = new LinkedHashMap<>();
        grouped.put("제주항공", List.of(
                flight("7C125", "제주항공", "1425", "1535", 51400),
                flight("7C127", "제주항공", "1640", "1750", 0)));
        grouped.put("대한항공", List.of(flight("KE1235", "대한항공", "1500", "1610", 89000)));

        String text = converter.summarize(grouped);

        assertThat(text).isEqualTo("""
                제주항공 2편: 7C125 14:25→15:35 51,400원 / 7C127 16:40→17:50 가격 없음
                대한항공 1편: KE1235 15:00→16:10 89,000원
                총 3편, 최저가 제주항공 7C125 51,400원""");
    }

    @Test
    @DisplayName("결과는 {\"result\": ...} 형태의 올바른 JSON이다 (Gemini는 Tool 결과를 JSON으로 파싱한다)")
    void wrapsAsJson() throws Exception {
        Map<String, List<FlightInfoResponse>> grouped = Map.of("제주항공", List.of(flight("7C125", "제주항공", "1425", "1535", 51400)));

        String json = converter.convert(grouped, Map.class);

        Map<String, Object> parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        assertThat(parsed).containsOnlyKeys("result");
        assertThat((String) parsed.get("result")).contains("7C125 14:25→15:35 51,400원");
    }

    @Test
    @DisplayName("같은 결과를 기본 JSON 변환보다 훨씬 짧게 만든다 (LLM 입력 토큰 절약)")
    void muchShorterThanJson() {
        Map<String, List<FlightInfoResponse>> grouped = Map.of("제주항공", List.of(
                flight("7C125", "제주항공", "1425", "1535", 51400),
                flight("7C129", "제주항공", "1840", "1950", 61400),
                flight("7C131", "제주항공", "2040", "2150", 48400)));

        String compact = converter.convert(grouped, Map.class);
        String json = new DefaultToolCallResultConverter().convert(grouped, Map.class);

        assertThat(compact.length()).isLessThan(json.length() / 3);
    }

    @Test
    @DisplayName("결과가 없으면 '항공편 없음'을, 항공편 Map이 아니면 기본 JSON 변환을 쓴다")
    void emptyAndFallback() {
        assertThat(converter.convert(Map.of(), Map.class)).isEqualTo("{\"result\":\"조건에 맞는 항공편이 없습니다.\"}");
        assertThat(converter.convert("NAARKJJ", String.class)).isEqualTo("\"NAARKJJ\"");
    }
}
