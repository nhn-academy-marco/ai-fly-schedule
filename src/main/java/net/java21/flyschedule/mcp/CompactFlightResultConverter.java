package net.java21.flyschedule.mcp;

import net.java21.flyschedule.dto.response.FlightInfoResponse;
import net.java21.flyschedule.dto.search.FlightView;
import org.springframework.ai.tool.execution.DefaultToolCallResultConverter;
import org.springframework.ai.tool.execution.ToolCallResultConverter;

import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 항공편 검색 Tool의 결과를 LLM에게 넘길 짧은 문장으로 바꿉니다.
 *
 * <p>왜 필요한가: 기본 변환기는 결과 객체를 JSON으로 바꿔 그대로 LLM에게 넘깁니다. 항공편 한 건이 필드 9개짜리 JSON이라
 * 14편이면 입력 토큰이 크게 늘고, CPU에서 돌리는 작은 모델은 이 입력을 읽는 데만 수십 초가 걸립니다.
 * LLM이 답변에 쓰는 정보(항공편명·시각·가격)만 한 줄씩 남기면 같은 답을 훨씬 빨리 만들 수 있습니다.
 *
 * <pre>
 * 제주항공 2편: 7C125 14:25→15:35 51,400원 / 7C127 16:40→17:50 가격 없음
 * 대한항공 1편: KE1235 15:00→16:10 89,000원
 * 총 3편, 최저가 제주항공 7C125 51,400원
 * </pre>
 *
 * <p>화면에 보여 줄 결과는 ToolResultCapture가 원래 객체로 따로 보관하므로 영향을 받지 않습니다.
 */
public class CompactFlightResultConverter implements ToolCallResultConverter {

    private final ToolCallResultConverter fallback = new DefaultToolCallResultConverter();

    @Override
    @SuppressWarnings("unchecked")
    public String convert(Object result, Type returnType) {
        if (!(result instanceof Map<?, ?> map)) {
            return fallback.convert(result, returnType);
        }
        Map<String, List<FlightInfoResponse>> grouped = (Map<String, List<FlightInfoResponse>>) map;
        List<FlightView> all = grouped.values().stream().flatMap(List::stream).map(FlightView::from).toList();
        if (all.isEmpty()) {
            return "조건에 맞는 항공편이 없습니다.";
        }

        String lines = grouped.entrySet().stream()
                .map(entry -> entry.getKey() + " " + entry.getValue().size() + "편: " + entry.getValue().stream()
                        .map(FlightView::from)
                        .map(CompactFlightResultConverter::line)
                        .collect(Collectors.joining(" / ")))
                .collect(Collectors.joining("\n"));

        String cheapest = all.stream()
                .filter(flight -> flight.price() != null)
                .min(Comparator.comparingInt(FlightView::price))
                .map(flight -> ", 최저가 %s %s %,d원".formatted(flight.airline(), flight.flightId(), flight.price()))
                .orElse("");
        return lines + "\n총 " + all.size() + "편" + cheapest;
    }

    private static String line(FlightView flight) {
        String price = Objects.isNull(flight.price()) ? "가격 없음" : "%,d원".formatted(flight.price());
        return "%s %s→%s %s".formatted(flight.flightId(), flight.departureTime(), flight.arrivalTime(), price);
    }
}
