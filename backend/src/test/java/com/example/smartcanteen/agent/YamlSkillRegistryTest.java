package com.example.smartcanteen.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.smartcanteen.agent.domain.SkillDefinition;
import com.example.smartcanteen.agent.infrastructure.YamlSkillRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class YamlSkillRegistryTest {

    private final YamlSkillRegistry registry = new YamlSkillRegistry(
            new ObjectMapper(new YAMLFactory()),
            new ClassPathResource("agent/skills/sop-manifests.yaml"));

    @Test
    void loads_all_manifest_entries_and_only_exposes_active_runtime_intents() {
        assertThat(registry.list()).hasSize(18);
        SkillDefinition traceability = registry.findByIntent("traceability.query").orElseThrow();

        assertThat(traceability.id()).isEqualTo("smart-canteen.traceability");
        assertThat(traceability.version()).isEqualTo("1.0.0");
        assertThat(traceability.isAvailable()).isTrue();
        assertThat(traceability.runtime().tools()).containsExactly("traceability.query");
        assertThat(traceability.manifestDigest()).hasSize(64);
        assertThat(registry.findByIntent("menu.publish")).isPresent();
        assertThat(registry.findByIntent("menu.validate-for-submit")).isPresent();
        assertThat(registry.findByIntent("menu.query")).isPresent();
        assertThat(registry.findByIntent("meal_order.query")).isPresent();
        assertThat(registry.findByIntent("meal_order.create")).isPresent();
        assertThat(registry.findByIntent("meal_order.cancel")).isPresent();
        assertThat(registry.findByIntent("inventory.query")).isPresent();
        assertThat(registry.findByIntent("procurement.plan.generate")).isPresent();
        assertThat(registry.findByIntent("procurement.order.create")).isPresent();
        assertThat(registry.findByIntent("procurement.order.receive")).isPresent();
        assertThat(registry.findByIntent("inventory.receive")).isPresent();
        assertThat(registry.findByIntent("inventory.stock-out")).isPresent();
        assertThat(registry.findByIntent("alert.dispose")).isPresent();
    }

    @Test
    void keeps_blocked_menu_definition_queryable_without_making_it_executable() {
        SkillDefinition menu = registry
                .find("smart-canteen.menu-approval", "1.0.0")
                .orElseThrow();

        assertThat(menu.runtime().activation()).isEqualTo("active");
        assertThat(menu.isAvailable()).isTrue();
        assertThat(menu.runtime().sideEffect()).isEqualTo("write");
    }
}
