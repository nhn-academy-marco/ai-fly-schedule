package com.nhnacademy.flyschedule.service.agent;

import com.nhnacademy.flyschedule.dto.response.FlightInfoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 가격 필터링 A2A 에이전트
 *
 * 운임을 기준으로 항공편을 필터링합니다.
 */
@Slf4j
@Service
public class PriceFilterAgent {

    /**
     * 가격 범위 내의 항공편만 필터링합니다.
     *
     * @param flights  전체 항공편 목록
     * @param minPrice 최소 가격 (null인 경우 제한 없음)
     * @param maxPrice 최대 가격 (null인 경우 제한 없음)
     * @return 필터링된 항공편 목록
     */
    public List<FlightInfoResponse> filterByPriceRange(
            List<FlightInfoResponse> flights,
            Integer minPrice,
            Integer maxPrice) {

        if (flights == null || flights.isEmpty()) {
            return List.of();
        }

        return flights.stream()
                .filter(flight -> {
                    Integer price = flight.getEconomyCharge();

                    // 가격 정보가 없는 경우 제외
                    if (price == null || price == 0) {
                        return false;
                    }

                    // 최소 가격 필터
                    if (minPrice != null && price < minPrice) {
                        return false;
                    }

                    // 최대 가격 필터
                    return maxPrice == null || price <= maxPrice;
                })
                .collect(Collectors.toList());
    }

    /**
     * 최저가 항공편을 찾습니다.
     *
     * @param flights 항공편 목록
     * @return 최저가 항공편 (없으면 null)
     */
    public FlightInfoResponse findCheapest(List<FlightInfoResponse> flights) {
        return flights.stream()
                .filter(f -> f.getEconomyCharge() != null && f.getEconomyCharge() > 0)
                .min((f1, f2) -> f1.getEconomyCharge().compareTo(f2.getEconomyCharge()))
                .orElse(null);
    }

    /**
     * 최고가 항공편을 찾습니다.
     *
     * @param flights 항공편 목록
     * @return 최고가 항공편 (없으면 null)
     */
    public FlightInfoResponse findMostExpensive(List<FlightInfoResponse> flights) {
        return flights.stream()
                .filter(f -> f.getEconomyCharge() != null && f.getEconomyCharge() > 0)
                .max((f1, f2) -> f1.getEconomyCharge().compareTo(f2.getEconomyCharge()))
                .orElse(null);
    }

    /**
     * 평균 가격을 계산합니다.
     *
     * @param flights 항공편 목록
     * @return 평균 가격
     */
    public double calculateAveragePrice(List<FlightInfoResponse> flights) {
        return flights.stream()
                .filter(f -> f.getEconomyCharge() != null && f.getEconomyCharge() > 0)
                .mapToInt(FlightInfoResponse::getEconomyCharge)
                .average()
                .orElse(0.0);
    }
}
