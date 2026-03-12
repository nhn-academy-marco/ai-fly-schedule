package com.nhnacademy.flyschedule.mcp;

import com.nhnacademy.flyschedule.dto.response.AirlineInfoResponse;
import com.nhnacademy.flyschedule.service.AirlineListAgent;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 항공사 정보 MCP Tool
 *
 * Spring AI의 @Tool annotation을 사용하여 항공사 관련 도구를 정의합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AirlineInfoTool {

    private final AirlineListAgent airlineListAgent;

    /**
     * 전체 항공사 목록을 조회합니다.
     *
     * <p>Spring AI MCP Tool 예시:
     * <pre>
     * 사용자: "항공사 리스트 보여줘"
     * AI 모델: getAirlineList() 호출
     * </pre>
     *
     * @return 항공사 정보 목록
     */
    @Tool(
            description = "전체 항공사 목록을 조회합니다. " +
                    "국내 모든 항공사의 코드와 이름을 반환합니다."
    )
    public List<AirlineInfoResponse> getAirlineList() {
        log.info("MCP Tool 호출: getAirlineList()");

        // A2A: Coordinator → AirlineListAgent
        return airlineListAgent.getAirlineList();
    }

    /**
     * 항공사 이름으로 항공사 ID를 조회합니다.
     *
     * <p>Spring AI MCP Tool 예시:
     * <pre>
     * 사용자: "아시아나항공 코드가 뭐야?"
     * AI 모델: getAirlineId(airlineName="아시아나항공") 호출
     * </pre>
     *
     * @param airlineName 항공사 이름 (예: "아시아나항공", "대한항공", "제주항공")
     * @return 항공사 ID (예: "AAR", "KAL", "7C")
     */
    @Tool(
            description = "항공사 이름으로 항공사 ID를 조회합니다. " +
                    "항공사 이름을 입력하면 해당 항공사의 IATA 코드를 반환합니다. " +
                    "지원하는 항공사: 대한항공, 아시아나항공, 제주항공, 에어부산, 에어서울, 진에어, 티웨이항공 등"
    )
    public String getAirlineId(
            @ToolParam(description = "항공사 이름 (예: 아시아나항공, 대한항공, 제주항공)") String airlineName) {

        log.info("MCP Tool 호출: getAirlineId(airlineName={})", airlineName);

        // A2A: Coordinator → AirlineCodeAgent
        return airlineListAgent.getAirlineId(airlineName);
    }
}
