package com.example.smartcanteen.agent.application;

import com.example.smartcanteen.agent.domain.AgentRun;
import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.domain.SkillDefinition;
import com.example.smartcanteen.agent.port.AgentRunStore;
import com.example.smartcanteen.agent.port.SkillRegistry;
import com.example.smartcanteen.application.AuthService;
import com.example.smartcanteen.application.AgentSchedulerRolloutPolicy;
import com.example.smartcanteen.application.BusinessAuthorizationPolicy;
import com.example.smartcanteen.security.AuthPrincipal;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Single-instance polling adapter for the claim-aware worker seam.
 *
 * <p>The feature is disabled by default. Multiple instances may be enabled only after assigning
 * distinct owner IDs; the database claim remains the concurrency authority.
 */
@Service
@ConditionalOnProperty(
        prefix = "agent.runtime.scheduler", name = "enabled", havingValue = "true")
public class AgentRunScheduler {

    private static final Logger log = LoggerFactory.getLogger(AgentRunScheduler.class);
    private static final int MAX_BATCH_SIZE = 100;

    private final AgentRunStore runs;
    private final AgentRunWorker worker;
    private final SkillRegistry skills;
    private final AuthService authentication;
    private final AgentSchedulerRolloutPolicy rollout;
    private final BusinessAuthorizationPolicy policy;
    private final String ownerId;
    private final int batchSize;

    @Autowired
    public AgentRunScheduler(
            AgentRunStore runs,
            AgentRunWorker worker,
            SkillRegistry skills,
            AuthService authentication,
            AgentSchedulerRolloutPolicy rollout,
            BusinessAuthorizationPolicy policy,
            @Value("${agent.runtime.scheduler.owner-id:}") String ownerId,
            @Value("${agent.runtime.scheduler.batch-size:10}") int batchSize) {
        this.runs = Objects.requireNonNull(runs, "runs");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.skills = Objects.requireNonNull(skills, "skills");
        this.authentication = Objects.requireNonNull(authentication, "authentication");
        this.rollout = Objects.requireNonNull(rollout, "rollout");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.ownerId = requireOwnerId(ownerId);
        this.batchSize = requireBatchSize(batchSize);
    }

    /** Polls once; the scheduled adapter delegates here so tests can exercise the public seam. */
    @Scheduled(fixedDelayString = "${agent.runtime.scheduler.poll-interval-ms:5000}")
    public void poll() {
        pollOnce();
    }

    public int pollOnce() {
        if (!runs.supportsExecutionClaims()) {
            throw new IllegalStateException(
                    "Agent scheduler requires a store with durable execution claims");
        }
        List<AgentRun> planned = runs.findPlanned(batchSize, rollout.allowedScopes());
        int processed = 0;
        for (AgentRun run : planned) {
            try {
                execute(run);
                processed++;
            } catch (AgentRunClaimUnavailableException expected) {
                log.debug("Agent Run was claimed by another worker runId={}", run.runId());
            } catch (RuntimeException exception) {
                // A single disabled actor, revoked Skill, or malformed snapshot must not stop
                // later Runs in the same poll. The worker remains the state-transition authority.
                log.warn("Agent scheduler could not process runId={}", run.runId(), exception);
            }
        }
        return processed;
    }

    private void execute(AgentRun run) {
        rollout.requireEnabled(run.scope());
        SkillDefinition skill = skills.find(run.skillId(), run.skillVersion())
                .filter(SkillDefinition::isAvailable)
                .orElseThrow(() -> new IllegalStateException(
                        "Skill snapshot is no longer executable: "
                                + run.skillId() + "@" + run.skillVersion()));
        AuthPrincipal principal = authentication.principalForUser(run.actorUserId());
        ExecutionContext context = policy.establishContext(
                principal,
                schedulerRequestId(run),
                run.scope(),
                "write".equals(skill.runtime().sideEffect()));
        worker.claimAndExecute(run.runId(), ownerId, context);
    }

    private String schedulerRequestId(AgentRun run) {
        String requestId = "agent-scheduler-" + ownerId + "-" + run.runId();
        if (requestId.length() <= 128) {
            return requestId;
        }
        String runSuffix = "-" + run.runId();
        int ownerPrefixLength = 128 - runSuffix.length();
        return requestId.substring(0, ownerPrefixLength) + runSuffix;
    }

    private static String requireOwnerId(String value) {
        Objects.requireNonNull(value, "ownerId");
        if (value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException("ownerId must be 1-128 characters");
        }
        return value;
    }

    private static int requireBatchSize(int value) {
        if (value < 1 || value > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("scheduler batch size must be between 1 and 100");
        }
        return value;
    }
}
