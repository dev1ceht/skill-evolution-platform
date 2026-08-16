package com.example.smartcanteen.agent.domain;

import com.example.smartcanteen.domain.CanteenScope;
import java.time.Instant;
import java.util.Objects;

/** Durable snapshot of one Agent plan and its execution lifecycle. */
public record AgentRun(
        String runId,
        String idempotencyKey,
        String requestHash,
        String actorUserId,
        String actorUsername,
        CanteenScope scope,
        String intent,
        String skillId,
        String skillVersion,
        String manifestDigest,
        String planHash,
        String planJson,
        String inputJson,
        RunStatus status,
        String currentStep,
        String resultJson,
        String errorCode,
        String errorMessage,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public AgentRun {
        requireText("runId", runId);
        requireText("idempotencyKey", idempotencyKey);
        requireText("requestHash", requestHash);
        requireText("actorUserId", actorUserId);
        requireText("actorUsername", actorUsername);
        Objects.requireNonNull(scope, "scope");
        requireText("intent", intent);
        requireText("skillId", skillId);
        requireText("skillVersion", skillVersion);
        requireText("manifestDigest", manifestDigest);
        requireText("planHash", planHash);
        requireText("planJson", planJson);
        requireText("inputJson", inputJson);
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }

    public AgentRun withStatus(RunStatus nextStatus, String nextStep, Instant now) {
        if (!status.canTransitionTo(nextStatus)) {
            throw new IllegalStateException(
                    "Invalid Agent Run transition: " + status + " -> " + nextStatus);
        }
        return new AgentRun(
                runId,
                idempotencyKey,
                requestHash,
                actorUserId,
                actorUsername,
                scope,
                intent,
                skillId,
                skillVersion,
                manifestDigest,
                planHash,
                planJson,
                inputJson,
                nextStatus,
                nextStep,
                resultJson,
                errorCode,
                errorMessage,
                version + 1,
                createdAt,
                now);
    }

    public AgentRun withSuccess(String result, Instant now) {
        if (!status.canTransitionTo(RunStatus.SUCCEEDED)) {
            throw new IllegalStateException(
                    "Invalid Agent Run transition: " + status + " -> SUCCEEDED");
        }
        return new AgentRun(
                runId, idempotencyKey, requestHash, actorUserId, actorUsername, scope, intent,
                skillId, skillVersion, manifestDigest, planHash, planJson, inputJson,
                RunStatus.SUCCEEDED, null, result, null, null, version + 1, createdAt, now);
    }

    public AgentRun withFailure(String code, String message, RunStatus failureStatus, Instant now) {
        if (failureStatus != RunStatus.FAILED
                && failureStatus != RunStatus.TIMED_OUT
                && failureStatus != RunStatus.RECONCILIATION_REQUIRED) {
            throw new IllegalArgumentException(
                    "Failure status must be FAILED, TIMED_OUT, or RECONCILIATION_REQUIRED");
        }
        if (!status.canTransitionTo(failureStatus)) {
            throw new IllegalStateException(
                    "Invalid Agent Run transition: " + status + " -> " + failureStatus);
        }
        return new AgentRun(
                runId, idempotencyKey, requestHash, actorUserId, actorUsername, scope, intent,
                skillId, skillVersion, manifestDigest, planHash, planJson, inputJson,
                failureStatus, currentStep, resultJson, code, message, version + 1, createdAt, now);
    }

    private static void requireText(String name, String value) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
