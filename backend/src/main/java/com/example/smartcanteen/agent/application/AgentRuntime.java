package com.example.smartcanteen.agent.application;

import com.example.smartcanteen.agent.domain.AgentPlan;
import com.example.smartcanteen.agent.domain.AgentRun;
import com.example.smartcanteen.agent.domain.AgentStep;
import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.domain.StartRunCommand;
import com.example.smartcanteen.agent.domain.RunStatus;
import com.example.smartcanteen.agent.port.AgentRunStore;
import com.example.smartcanteen.agent.port.SkillRegistry;
import com.example.smartcanteen.agent.domain.SkillDefinition;
import com.example.smartcanteen.domain.CanteenScope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Creates durable, immutable Agent plans. Tool execution is intentionally a later seam. */
@Service
public class AgentRuntime {

    private final SkillRegistry skills;
    private final AgentRunStore runs;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public AgentRuntime(SkillRegistry skills, AgentRunStore runs, ObjectMapper objectMapper) {
        this(skills, runs, objectMapper, Clock.systemUTC());
    }

    public AgentRuntime(
            SkillRegistry skills,
            AgentRunStore runs,
            ObjectMapper objectMapper,
            Clock clock) {
        this.skills = skills;
        this.runs = runs;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public AgentRun start(StartRunCommand command, ExecutionContext context) {
        if (!command.requestId().equals(context.requestId())) {
            throw new IllegalArgumentException("requestId does not match execution context");
        }
        SkillDefinition skill = skills.findByIntent(command.intent())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No active Skill is registered for intent: " + command.intent()));
        String canonicalInput = canonicalize(command.inputJson());
        AgentPlan plan = plan(command, context, skill, canonicalInput);
        Optional<AgentRun> existing = runs.findByIdempotency(
                context.actorUserId(), context.scope(), command.idempotencyKey());
        if (existing.isPresent()) {
            AgentRun previous = existing.get();
            if (!previous.requestHash().equals(plan.inputDigest())) {
                throw new IllegalStateException(
                        "Idempotency key was already used for a different Agent request");
            }
            return previous;
        }

        Instant now = clock.instant();
        String runId = "RUN-" + UUID.randomUUID();
        AgentRun run = new AgentRun(
                runId,
                command.idempotencyKey(),
                plan.inputDigest(),
                context.actorUserId(),
                context.actorUsername(),
                context.scope(),
                command.intent(),
                skill.id(),
                skill.version(),
                skill.manifestDigest(),
                plan.planHash(),
                plan.planJson(),
                canonicalInput,
                RunStatus.PLANNED,
                null,
                null,
                null,
                null,
                0,
                now,
                now);
        List<AgentStep> steps = new ArrayList<>();
        for (int index = 0; index < skill.runtime().tools().size(); index++) {
            String stepId = "step-" + (index + 1);
            steps.add(new AgentStep(
                    runId,
                    stepId,
                    index,
                    skill.runtime().tools().get(index),
                    runId + ":" + stepId,
                    plan.inputDigest(),
                    "PENDING",
                    0,
                    null,
                    null,
                    null,
                    null,
                    null));
        }
        try {
            runs.insert(run, steps);
        } catch (DuplicateKeyException duplicate) {
            // The unique actor/scope/idempotency constraint is the concurrency
            // authority. A competing transaction may win between the lookup
            // above and the insert; convert that race into normal replay or a
            // deterministic same-key/different-payload conflict.
            AgentRun concurrent = runs.findByIdempotency(
                            context.actorUserId(), context.scope(), command.idempotencyKey())
                    .orElseThrow(() -> duplicate);
            if (!concurrent.requestHash().equals(plan.inputDigest())) {
                throw new IllegalStateException(
                        "Idempotency key was already used for a different Agent request",
                        duplicate);
            }
            return concurrent;
        }
        return run;
    }

    public Optional<AgentRun> find(String runId) {
        return runs.findById(runId);
    }

    private AgentPlan plan(
            StartRunCommand command,
            ExecutionContext context,
            SkillDefinition skill,
            String canonicalInput) {
        String inputDigest = digest(command.intent() + "\n" + canonicalInput);
        String planJson = writePlanJson(command, context.scope(), skill, inputDigest);
        return new AgentPlan(
                skill.id(),
                skill.version(),
                skill.manifestDigest(),
                command.intent(),
                context.scope(),
                inputDigest,
                skill.runtime().tools(),
                digest(planJson),
                planJson);
    }

    private String writePlanJson(
            StartRunCommand command,
            CanteenScope scope,
            SkillDefinition skill,
            String inputDigest) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("intent", command.intent());
        plan.put("skillId", skill.id());
        plan.put("skillVersion", skill.version());
        plan.put("manifestDigest", skill.manifestDigest());
        plan.put("inputDigest", inputDigest);
        plan.put("schoolId", scope.schoolId());
        plan.put("canteenId", scope.canteenId());
        plan.put("tools", skill.runtime().tools());
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize Agent plan", exception);
        }
    }

    private static String digest(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** Canonicalizes structured input so equivalent JSON cannot bypass idempotency checks. */
    private String canonicalize(String inputJson) {
        try {
            return objectMapper.writeValueAsString(canonicalNode(objectMapper.readTree(inputJson)));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Agent input must be valid JSON", exception);
        }
    }

    private JsonNode canonicalNode(JsonNode node) {
        if (node == null || node.isValueNode()) {
            return node;
        }
        if (node.isArray()) {
            ArrayNode array = objectMapper.createArrayNode();
            for (JsonNode item : node) {
                array.add(canonicalNode(item));
            }
            return array;
        }
        ObjectNode object = objectMapper.createObjectNode();
        List<String> names = new ArrayList<>();
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            names.add(fields.next());
        }
        Collections.sort(names);
        for (String name : names) {
            object.set(name, canonicalNode(node.get(name)));
        }
        return object;
    }
}
