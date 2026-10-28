package fraudengine.persistence;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class FraudEvaluationOutboxSql {

    private final String insert;
    private final String claimPending;
    private final String markClaimed;
    private final String markPublished;
    private final String markFailed;
    private final String deletePublished;

    public FraudEvaluationOutboxSql() {
        insert = load("insert-outbox-event.sql");
        claimPending = load("claim-outbox-events.sql");
        markClaimed = load("mark-outbox-claimed.sql");
        markPublished = load("mark-outbox-published.sql");
        markFailed = load("mark-outbox-failed.sql");
        deletePublished = load("delete-published-outbox-events.sql");
    }

    public String insert() {
        return insert;
    }

    public String claimPending() {
        return claimPending;
    }

    public String markClaimed() {
        return markClaimed;
    }

    public String markPublished() {
        return markPublished;
    }

    public String markFailed() {
        return markFailed;
    }

    public String deletePublished() {
        return deletePublished;
    }

    private String load(String filename) {
        try {
            return new ClassPathResource("db/queries/" + filename)
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load SQL query: " + filename, ex);
        }
    }
}
