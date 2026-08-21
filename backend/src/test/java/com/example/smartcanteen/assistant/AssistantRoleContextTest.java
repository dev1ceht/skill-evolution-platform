package com.example.smartcanteen.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.assistant.domain.AssistantPersona;
import com.example.smartcanteen.assistant.domain.AssistantRoleContext;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.Role;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AssistantRoleContextTest {

    @Test
    void maps_diner_to_employee_student_persona() {
        AssistantRoleContext context = AssistantRoleContext.from(executionContext(Role.DINER));

        assertThat(context.persona()).isEqualTo(AssistantPersona.EMPLOYEE_STUDENT);
        assertThat(context.scope()).isEqualTo(new CanteenScope("SCHOOL-001", "CANTEEN-001"));
        assertThat(context.roles()).containsExactly(Role.DINER);
    }

    @Test
    void maps_staff_and_school_admin_to_operational_personas() {
        assertThat(AssistantRoleContext.from(executionContext(Role.CANTEEN_STAFF)).persona())
                .isEqualTo(AssistantPersona.CANTEEN_OPERATOR);
        assertThat(AssistantRoleContext.from(executionContext(Role.SCHOOL_ADMIN)).persona())
                .isEqualTo(AssistantPersona.CANTEEN_MANAGER);
    }

    @Test
    void exposes_only_server_derived_context_values() {
        AssistantRoleContext context = AssistantRoleContext.from(executionContext(Role.DINER));

        assertThat(context.actorUserId()).isEqualTo("USER-001");
        assertThat(context.permissions()).containsExactly("MENU_READ");
        assertThat(context.promptSummary())
                .contains("EMPLOYEE_STUDENT")
                .contains("SCHOOL-001/CANTEEN-001")
                .doesNotContain("password");
    }

    private static ExecutionContext executionContext(Role role) {
        AuthPrincipal principal = new AuthPrincipal(
                "USER-001",
                "study-user",
                "Study User",
                role,
                "SCHOOL-001",
                "CANTEEN-001");
        return ExecutionContext.fromTrustedPrincipal(
                "request-001",
                principal,
                new CanteenScope("SCHOOL-001", "CANTEEN-001"),
                Set.of(role),
                Set.of("MENU_READ"));
    }
}
