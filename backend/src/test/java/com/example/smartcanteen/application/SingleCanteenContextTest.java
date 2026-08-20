package com.example.smartcanteen.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.ForbiddenException;
import org.junit.jupiter.api.Test;

class SingleCanteenContextTest {

    private final CanteenScope fixedScope = new CanteenScope("SCHOOL-001", "CANTEEN-001");

    @Test
    void resolves_the_configured_scope_and_rejects_switching_in_single_mode() {
        SingleCanteenContext context = new SingleCanteenContext(
                true, fixedScope.schoolId(), fixedScope.canteenId());

        assertThat(context.resolve(null, null)).isEqualTo(fixedScope);
        assertThat(context.resolve(fixedScope)).isEqualTo(fixedScope);
        assertThatThrownBy(() -> context.resolve("OTHER-SCHOOL", "OTHER-CANTEEN"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("This deployment operates one fixed canteen only");
    }

    @Test
    void can_resolve_arbitrary_scopes_when_fixed_mode_is_disabled_for_tests() {
        SingleCanteenContext context = new SingleCanteenContext(
                false, fixedScope.schoolId(), fixedScope.canteenId());

        assertThat(context.resolve("OTHER-SCHOOL", "OTHER-CANTEEN"))
                .isEqualTo(new CanteenScope("OTHER-SCHOOL", "OTHER-CANTEEN"));
    }
}
