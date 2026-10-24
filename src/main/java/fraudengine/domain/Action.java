package fraudengine.domain;

/**
 * Recommended or final action resulting from fraud evaluation.
 */
public enum Action {
    /**
     * Transaction is safe to proceed without friction.
     */
    APPROVE,

    /**
     * Suspicious signals detected; transaction requires step-up authentication
     * (e.g. 3D Secure, OTP) or routing to a manual analyst queue.
     */
    REVIEW,

    /**
     * Critical risk or hard violation detected; transaction is immediately blocked.
     */
    DECLINE
}

