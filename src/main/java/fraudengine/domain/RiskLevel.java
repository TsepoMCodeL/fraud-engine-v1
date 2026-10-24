package fraudengine.domain;

/**
 * Standard risk tier derived from composite scoring.
 */
public enum RiskLevel {
    LOW(0, 29),
    MEDIUM(30, 59),
    HIGH(60, 84),
    CRITICAL(85, 100);

    private final int minScore;
    private final int maxScore;

    RiskLevel(int minScore, int maxScore) {
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    public int minScore() {
        return minScore;
    }

    public int maxScore() {
        return maxScore;
    }

    public static RiskLevel fromScore(int score) {
        if (score >= CRITICAL.minScore) return CRITICAL;
        if (score >= HIGH.minScore) return HIGH;
        if (score >= MEDIUM.minScore) return MEDIUM;
        return LOW;
    }
}
