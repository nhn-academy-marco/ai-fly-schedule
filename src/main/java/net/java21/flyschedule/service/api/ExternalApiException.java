package net.java21.flyschedule.service.api;

/**
 * 공공데이터 포털 API 호출 실패
 *
 * <p>"항공편이 없다"와 "API를 호출하지 못했다"는 다른 상황입니다.
 * 호출 실패를 빈 목록으로 숨기면 사용자는 항공편이 없다고 오해하므로, 별도 예외로 알립니다.
 */
public class ExternalApiException extends RuntimeException {

    public ExternalApiException(String message) {
        super(message);
    }

    public ExternalApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
