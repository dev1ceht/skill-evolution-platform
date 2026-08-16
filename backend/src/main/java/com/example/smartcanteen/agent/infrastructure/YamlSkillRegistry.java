package com.example.smartcanteen.agent.infrastructure;

import com.example.smartcanteen.agent.domain.SkillDefinition;
import com.example.smartcanteen.agent.domain.SkillDefinition.RuntimePolicy;
import com.example.smartcanteen.agent.port.SkillRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/** Loads and validates the immutable Skill snapshot packaged with the backend. */
@Component
public class YamlSkillRegistry implements SkillRegistry {

    private static final Set<String> ALLOWED_STATUSES = Set.of(
            "implemented", "port-only", "deferred", "environment-gated");

    private final Map<String, SkillDefinition> definitions;
    private final Map<String, SkillDefinition> activeByIntent;

    @Autowired
    public YamlSkillRegistry(
            @Value("${smart-canteen.agent.skill-manifest:classpath:agent/skills/sop-manifests.yaml}")
                    Resource manifest) {
        this(new ObjectMapper(new YAMLFactory()), manifest);
    }

    public YamlSkillRegistry(ObjectMapper yamlMapper, Resource manifest) {
        if (yamlMapper == null || manifest == null) {
            throw new NullPointerException("yamlMapper and manifest are required");
        }
        Loaded loaded = load(yamlMapper, manifest);
        this.definitions = Map.copyOf(loaded.definitions());
        this.activeByIntent = Map.copyOf(loaded.activeByIntent());
    }

    @Override
    public Optional<SkillDefinition> findByIntent(String intent) {
        if (intent == null || intent.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(activeByIntent.get(intent));
    }

    @Override
    public Optional<SkillDefinition> find(String skillId, String version) {
        if (skillId == null || version == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(key(skillId, version)));
    }

    @Override
    public List<SkillDefinition> list() {
        return definitions.values().stream()
                .sorted(Comparator.comparing(SkillDefinition::id)
                        .thenComparing(SkillDefinition::version))
                .toList();
    }

    private static Loaded load(ObjectMapper mapper, Resource manifest) {
        try (InputStream input = manifest.getInputStream()) {
            JsonNode root = mapper.readTree(input);
            JsonNode sops = requiredArray(root, "sops");
            Map<String, SkillDefinition> definitions = new LinkedHashMap<>();
            Map<String, SkillDefinition> activeByIntent = new LinkedHashMap<>();
            for (JsonNode sop : sops) {
                SkillDefinition definition = parseDefinition(mapper, sop);
                String key = key(definition.id(), definition.version());
                if (definitions.putIfAbsent(key, definition) != null) {
                    throw new IllegalStateException("Duplicate Skill version: " + key);
                }
                if (!definition.isAvailable()) {
                    continue;
                }
                for (String intent : definition.runtime().intents()) {
                    SkillDefinition previous = activeByIntent.putIfAbsent(intent, definition);
                    if (previous != null) {
                        throw new IllegalStateException(
                                "Multiple active Skills claim intent: " + intent);
                    }
                }
            }
            if (definitions.isEmpty()) {
                throw new IllegalStateException("Skill manifest contains no SOP definitions");
            }
            return new Loaded(definitions, activeByIntent);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load Skill manifest: " + manifest, exception);
        }
    }

    private static SkillDefinition parseDefinition(ObjectMapper mapper, JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("Each SOP definition must be an object");
        }
        String id = requiredText(node, "id");
        String version = requiredText(node, "version");
        String status = requiredText(node, "status");
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Unsupported Skill status: " + status);
        }
        RuntimePolicy runtime = parseRuntime(node.get("runtime"));
        return new SkillDefinition(
                id,
                version,
                status,
                requiredText(node, "risk_level"),
                requiredText(node, "approval"),
                requiredText(node, "scope"),
                requiredStrings(node, "trigger"),
                requiredStrings(node, "permissions"),
                requiredStrings(node, "steps"),
                runtime,
                digest(mapper, node));
    }

    private static RuntimePolicy parseRuntime(JsonNode runtime) {
        if (runtime == null || runtime.isNull()) {
            return null;
        }
        if (!runtime.isObject()) {
            throw new IllegalArgumentException("runtime must be an object");
        }
        return new RuntimePolicy(
                requiredStrings(runtime, "intents"),
                requiredText(runtime, "input_schema"),
                requiredText(runtime, "output_schema"),
                requiredStrings(runtime, "tools"),
                requiredText(runtime, "side_effect"),
                requiredText(runtime, "run_confirmation"),
                requiredText(runtime, "domain_approval"),
                requiredText(runtime, "activation"),
                requiredPositiveLong(runtime, "deadline_ms"),
                requiredText(runtime, "retry_policy"),
                requiredText(runtime, "evidence"));
    }

    private static JsonNode requiredArray(JsonNode parent, String field) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || !value.isArray() || value.isEmpty()) {
            throw new IllegalArgumentException(field + " must be a non-empty array");
        }
        return value;
    }

    private static List<String> requiredStrings(JsonNode parent, String field) {
        JsonNode value = requiredArray(parent, field);
        List<String> result = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw new IllegalArgumentException(field + " must contain non-blank strings");
            }
            result.add(item.asText());
        }
        return result;
    }

    private static String requiredText(JsonNode parent, String field) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string");
        }
        return value.asText();
    }

    private static long requiredPositiveLong(JsonNode parent, String field) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || !value.canConvertToLong() || value.asLong() <= 0) {
            throw new IllegalArgumentException(field + " must be a positive integer");
        }
        return value.asLong();
    }

    private static String digest(ObjectMapper mapper, JsonNode node) {
        try {
            byte[] canonical = mapper.writeValueAsBytes(node);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to fingerprint Skill definition", exception);
        }
    }

    private static String key(String id, String version) {
        return id + "@" + version;
    }

    private record Loaded(
            Map<String, SkillDefinition> definitions,
            Map<String, SkillDefinition> activeByIntent) {
    }
}
