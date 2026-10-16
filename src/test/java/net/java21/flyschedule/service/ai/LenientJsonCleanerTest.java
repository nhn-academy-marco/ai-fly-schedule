package net.java21.flyschedule.service.ai;

import net.java21.flyschedule.dto.search.SearchCriteria;
import net.java21.flyschedule.service.orchestrator.SearchPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 작은 모델이 실제로 돌려주는 모양의 응답으로 Structured Output 변환을 검증합니다.
 */
@DisplayName("LenientJsonCleaner 테스트")
class LenientJsonCleanerTest {

    private final LenientJsonCleaner cleaner = new LenientJsonCleaner();

    @Test
    @DisplayName("코드 블록 앞뒤에 문장이 붙어 있어도 블록 안의 JSON만 꺼낸다 (운영에서 Orchestrator 계획이 실패한 사례)")
    void fencedWithSurroundingText() {
        String reply = """
                다음은 검색 계획입니다:
                ```json
                {"reasoning": "2 × 2", "tasks": []}
                ```
                각 작업을 실행하세요.""";

        assertThat(cleaner.clean(reply)).isEqualTo("{\"reasoning\": \"2 × 2\", \"tasks\": []}");
    }

    @Test
    @DisplayName("코드 블록이 없으면 처음 '{'부터 마지막 '}'까지 꺼낸다")
    void bracesWithSurroundingText() {
        assertThat(cleaner.clean("결과: {\"departure\": \"광주\"} 입니다"))
                .isEqualTo("{\"departure\": \"광주\"}");
    }

    @Test
    @DisplayName("이미 깨끗한 JSON과 null은 그대로 둔다")
    void cleanInput() {
        assertThat(cleaner.clean("  {\"a\":1}  ")).isEqualTo("{\"a\":1}");
        assertThat(cleaner.clean(null)).isNull();
    }

    @Test
    @DisplayName("BeanOutputConverter에 연결하면 문장이 섞인 응답도 SearchPlan으로 변환된다")
    void worksWithBeanOutputConverter() {
        BeanOutputConverter<SearchPlan> converter = new BeanOutputConverter<>(SearchPlan.class, null, cleaner);
        String reply = """
                ```json
                {"reasoning": "금·토 두 날짜", "tasks": [
                  {"departure": "광주", "arrival": "제주", "date": "2026-10-16"},
                  {"departure": "광주", "arrival": "제주", "date": "2026-10-17"}
                ]}
                ```
                위 계획대로 검색합니다.""";

        SearchPlan plan = converter.convert(reply);

        assertThat(plan.tasks()).extracting(SearchCriteria::date).containsExactly("2026-10-16", "2026-10-17");
    }

    @Test
    @DisplayName("기본 정리기만 쓰면 같은 응답이 실패한다 (왜 이 클래스가 필요한지 보여 주는 테스트)")
    void defaultCleanerFails() {
        BeanOutputConverter<SearchCriteria> converter = new BeanOutputConverter<>(SearchCriteria.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> converter.convert("""
                ```json
                {"departure": "광주", "arrival": "제주"}
                ```
                참고하세요."""))
                .isInstanceOf(RuntimeException.class);
    }
}
