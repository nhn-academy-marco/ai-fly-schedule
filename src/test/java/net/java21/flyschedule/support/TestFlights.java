package net.java21.flyschedule.support;

import net.java21.flyschedule.dto.response.FlightInfoResponse;

/**
 * 테스트용 항공편 데이터
 */
public final class TestFlights {

    private TestFlights() {
    }

    /**
     * @param flightId 항공편명 (예: "OZ8141")
     * @param airline  항공사명
     * @param depTime  출발 시각 "HHmm"
     * @param arrTime  도착 시각 "HHmm"
     * @param price    일반석 운임 (null 가능)
     */
    public static FlightInfoResponse flight(String flightId, String airline, String depTime, String arrTime, Integer price) {
        return FlightInfoResponse.builder()
                .flightId(flightId)
                .airlineName(airline)
                .departureTime("20260325" + depTime)
                .arrivalTime("20260325" + arrTime)
                .departureAirport("광주")
                .arrivalAirport("제주")
                .economyCharge(price)
                .build();
    }
}
