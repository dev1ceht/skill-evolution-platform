package com.example.smartcanteen.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MenuIdTest {

    @Test
    void normalizes_and_accepts_the_two_short_id_forms() {
        assertThat(MenuId.normalize(" m001 ")).isEqualTo("M001");
        assertThat(MenuId.normalize("mabc123")).isEqualTo("MABC123");
        assertThat(MenuId.isValid(MenuId.generate())).isTrue();
    }

    @Test
    void rejects_legacy_and_ambiguous_menu_ids() {
        assertThat(MenuId.isValid("MENU-001")).isFalse();
        assertThat(MenuId.isValid("M1234")).isFalse();
        assertThatThrownBy(() -> MenuId.normalize("MENU-001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("short format");
    }

    @Test
    void finds_only_a_standalone_short_id_in_text() {
        assertThat(MenuId.findIn("请查询 M001 的午餐菜单")).contains("M001");
        assertThat(MenuId.findIn("请查询 MENU-001 的午餐菜单")).isEmpty();
    }
}
