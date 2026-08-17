package com.example.smartcanteen.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MenuTest {

    @Test
    void approval_must_follow_the_documented_state_machine() {
        Menu menu = new Menu("MENU-001");

        menu.submit();
        menu.approve("营养与成本均符合要求");

        assertThat(menu.status()).isEqualTo(MenuStatus.APPROVED);
        assertThat(menu.decisionComment()).isEqualTo("营养与成本均符合要求");
        assertThatThrownBy(() -> menu.reject("不能重复审批"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APPROVED");
    }

    @Test
    void draft_menu_cannot_be_approved_directly() {
        Menu menu = new Menu("MENU-001");

        assertThatThrownBy(() -> menu.approve("跳过提交"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");
    }
}
