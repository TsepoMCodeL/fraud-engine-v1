package fraudengine.decision;

/**
 * Configuration thresholds for translating risk scores into engine actions.
 */
public record DecisionThresholds(
        int reviewScoreThreshold,
        int declineScoreThreshold,
        boolean honorHardOverrides
) {
    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 100;
    private static final int DEFAULT_REVIEW_THRESHOLD = 40;
    private static final int DEFAULT_DECLINE_THRESHOLD = 75;

    public DecisionThresholds {
        if (reviewScoreThreshold < MIN_SCORE || reviewScoreThreshold > MAX_SCORE) {
            throw new IllegalArgumentException(
                    "reviewScoreThreshold must be between " + MIN_SCORE + " and " + MAX_SCORE);
        }
        if (declineScoreThreshold < reviewScoreThreshold || declineScoreThreshold > MAX_SCORE) {
            throw new IllegalArgumentException(
                    "declineScoreThreshold must be >= reviewScoreThreshold and <= " + MAX_SCORE);
        }
    }

    public static DecisionThresholds defaultThresholds() {
        return new DecisionThresholds(DEFAULT_REVIEW_THRESHOLD, DEFAULT_DECLINE_THRESHOLD, true);
    }
}
