package com.example.smartcanteen.agent.domain;

public enum RunStatus {
    RECEIVED,
    PLANNED,
    WAITING_CLARIFICATION,
    WAITING_CONFIRMATION,
    EXECUTING,
    SUCCEEDED,
    FAILED,
    REJECTED,
    CANCELLED,
    TIMED_OUT,
    RECONCILIATION_REQUIRED;

    /**
     * Returns whether a run may move directly from this state to {@code next}.
     * Terminal states deliberately have no outgoing transitions; recovery and
     * compensation will be introduced as explicit commands in a later phase.
     */
    public boolean canTransitionTo(RunStatus next) {
        if (next == null) {
            return false;
        }
        return switch (this) {
            case RECEIVED -> next == PLANNED
                    || next == WAITING_CLARIFICATION
                    || next == WAITING_CONFIRMATION
                    || next == REJECTED
                    || next == CANCELLED;
            case PLANNED -> next == EXECUTING
                    || next == WAITING_CLARIFICATION
                    || next == WAITING_CONFIRMATION
                    || next == REJECTED
                    || next == CANCELLED;
            case WAITING_CLARIFICATION -> next == PLANNED
                    || next == REJECTED
                    || next == CANCELLED;
            case WAITING_CONFIRMATION -> next == EXECUTING
                    || next == PLANNED
                    || next == REJECTED
                    || next == CANCELLED;
            case EXECUTING -> next == SUCCEEDED
                    || next == FAILED
                    || next == TIMED_OUT
                    || next == RECONCILIATION_REQUIRED;
            case SUCCEEDED, FAILED, REJECTED, CANCELLED, TIMED_OUT, RECONCILIATION_REQUIRED -> false;
        };
    }
}
