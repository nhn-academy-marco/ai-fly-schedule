package net.java21.flyschedule.service.ai;

import org.springframework.ai.converter.ResponseTextCleaner;

/**
 * LLM 응답에서 JSON 부분만 꺼냅니다. (Structured Output 변환 전에 실행)
 *
 * <p>왜 필요한가: Spring AI의 기본 정리기는 응답 전체가 ```로 시작하고 ```로 끝날 때만 코드 블록 표시를 벗깁니다.
 * 작은 모델(Ollama qwen2.5:3b 등)은 "다음은 계획입니다:" 같은 문장을 앞뒤에 붙이거나 코드 블록 뒤에 설명을 덧붙이기도 해서,
 * 그대로 JSON으로 읽으면 파싱에 실패합니다. 실제로 운영 중에 이 때문에 Orchestrator의 계획 단계가 실패했습니다.
 *
 * <ol>
 *   <li>응답 안에 ``` 코드 블록이 있으면 첫 번째 블록의 내용</li>
 *   <li>없으면 처음 나온 '{'부터 마지막 '}'까지</li>
 *   <li>둘 다 없으면 앞뒤 공백만 제거한 원래 응답</li>
 * </ol>
 */
public class LenientJsonCleaner implements ResponseTextCleaner {

    @Override
    public String clean(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();

        int fence = trimmed.indexOf("```");
        if (fence >= 0) {
            int bodyStart = trimmed.indexOf('\n', fence);
            int closing = bodyStart < 0 ? -1 : trimmed.indexOf("```", bodyStart);
            if (closing > bodyStart) {
                return trimmed.substring(bodyStart + 1, closing).trim();
            }
        }

        int open = trimmed.indexOf('{');
        int close = trimmed.lastIndexOf('}');
        if (open >= 0 && close > open) {
            return trimmed.substring(open, close + 1);
        }
        return trimmed;
    }
}
