package net.java21.flyschedule.mcp;

import net.java21.flyschedule.dto.response.AirportInfoResponse;
import net.java21.flyschedule.service.agent.AirportListAgent;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 공항 정보 MCP Tool
 *
 * Spring AI의 @Tool annotation을 사용하여 공항 관련 도구를 정의합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AirportInfoTool {

    private final AirportListAgent airportListAgent;

    /**
     * 전체 공항 목록을 조회합니다.
     *
     * <p>예시:
     * <pre>
     * 사용자: "공항 리스트 보여줘"
     * AI 모델: getAirportList() 호출
     * </pre>
     *
     * @return 공항 정보 목록
     */
    @Tool(
            description = "전체 공항 목록을 조회합니다. " +
                    "국내 모든 공항의 코드와 이름을 반환합니다."
    )
    public List<AirportInfoResponse> getAirportList() {
        log.info("MCP Tool 호출: getAirportList()");

        return ToolResultCapture.capture("getAirportList", "전체", airportListAgent::getAirportList);
    }

    /**
     * 공항 이름으로 공항 코드를 조회합니다.
     *
     * <p>예시:
     * <pre>
     * 사용자: "광주 공항 코드가 뭐야?"
     * AI 모델: getAirportCode(airportName="광주") 호출
     * </pre>
     *
     * @param airportName 공항 이름 (예: "광주", "김포", "제주")
     * @return 공항 코드 (예: "NAARKJJ", "NAARKSS", "NAARKPC")
     */
    @Tool(
            description = "공항 이름으로 공항 코드를 조회합니다. " +
                    "공항 이름을 입력하면 공항 코드(예: NAARKJJ)를 반환합니다. " +
                    "지원하는 공항: 김포, 인천, 김해, 광주, 제주, 대구, 청주, 양양, 울산, 여수, 사천, 포항, 무안, 군산, 원주"
    )
    public String getAirportCode(
            @ToolParam(description = "공항 이름 (예: 광주, 김포, 제주)") String airportName) {

        log.info("MCP Tool 호출: getAirportCode(airportName={})", airportName);

        return ToolResultCapture.capture("getAirportCode", airportName, () -> airportListAgent.getAirportCode(airportName));
    }
}
