package com.example.smartcanteen.agent.port;

import com.example.smartcanteen.agent.domain.SkillDefinition;
import java.util.List;
import java.util.Optional;

/** Read-only access to validated, immutable runtime Skill definitions. */
public interface SkillRegistry {

    Optional<SkillDefinition> findByIntent(String intent);

    Optional<SkillDefinition> find(String skillId, String version);

    List<SkillDefinition> list();
}
