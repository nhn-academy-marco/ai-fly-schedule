package net.java21.flyschedule.service.job;

import net.java21.flyschedule.dto.search.ExecutionTrace;
import net.java21.flyschedule.dto.search.SearchResponse;

import java.time.Instant;

/**
 * 실행 중이거나 끝난 검색 작업 하나
 *
 * <p>trace는 실행 중에도 계속 늘어나므로, 화면은 작업이 끝나기 전에도 지금까지의 단계를 보여 줄 수 있습니다.
 */
public class SearchJob {

    public enum Status { RUNNING, DONE }

    private final String id;
    private final String pattern;
    private final ExecutionTrace trace = new ExecutionTrace();
    private final Instant createdAt = Instant.now();
    private volatile SearchResponse result;

    SearchJob(String id, String pattern) {
        this.id = id;
        this.pattern = pattern;
    }

    public String id() {
        return id;
    }

    public String pattern() {
        return pattern;
    }

    public ExecutionTrace trace() {
        return trace;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Status status() {
        return result == null ? Status.RUNNING : Status.DONE;
    }

    public SearchResponse result() {
        return result;
    }

    void complete(SearchResponse response) {
        this.result = response;
    }
}
