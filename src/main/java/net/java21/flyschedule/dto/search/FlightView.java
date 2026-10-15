package net.java21.flyschedule.dto.search;

import net.java21.flyschedule.dto.response.FlightInfoResponse;

/**
 * 화면에 보여 줄 항공편 한 건
 *
 * <p>공공데이터 포털 응답(FlightInfoResponse)의 "202603250955" 같은 시각을 "09:55"로 바꿔 담습니다.
 * API는 운임 정보가 없을 때 0을 돌려주므로, 0원은 "가격 정보 없음"(null)으로 바꿉니다.
 */
public record FlightView(
        String flightId,
        String airline,
        String departureAirport,
        String arrivalAirport,
        String departureTime,
        String arrivalTime,
        Integer price
) {

    public static FlightView from(FlightInfoResponse flight) {
        return new FlightView(
                flight.getFlightId(),
                flight.getAirlineName(),
                flight.getDepartureAirport(),
                flight.getArrivalAirport(),
                toClock(flight.getDepartureTime()),
                toClock(flight.getArrivalTime()),
                toPrice(flight.getEconomyCharge()));
    }

    private static Integer toPrice(Integer charge) {
        return charge != null && charge > 0 ? charge : null;
    }

    private static String toClock(String dateTime) {
        if (dateTime == null || dateTime.length() < 12) {
            return null;
        }
        return dateTime.substring(8, 10) + ":" + dateTime.substring(10, 12);
    }
}
