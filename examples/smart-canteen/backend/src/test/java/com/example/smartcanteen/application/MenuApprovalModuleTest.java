package com.example.smartcanteen.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.smartcanteen.application.port.MenuApproval;
import com.example.smartcanteen.application.port.MenuStore;
import com.example.smartcanteen.domain.Menu;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MenuApprovalModuleTest {

    @Test
    void menu_approval_module_owns_submission_and_decision_transitions() {
        InMemoryMenuStore store = new InMemoryMenuStore();
        store.menus.put("MENU-MODULE-001", new Menu("MENU-MODULE-001"));
        MenuApproval module = new MenuApprovalService(store);

        assertThat(module.submit("MENU-MODULE-001").status().name())
                .isEqualTo("PENDING_APPROVAL");
        assertThat(module.decide("MENU-MODULE-001", "APPROVE", "module test").status().name())
                .isEqualTo("APPROVED");
        assertThat(store.saveCount).isEqualTo(2);
    }

    private static final class InMemoryMenuStore implements MenuStore {

        private final Map<String, Menu> menus = new HashMap<>();
        private int saveCount;

        @Override
        public Optional<Menu> findMenu(String menuId) {
            return Optional.ofNullable(menus.get(menuId));
        }

        @Override
        public void saveMenu(Menu menu) {
            menus.put(menu.id(), menu);
            saveCount++;
        }
    }
}
