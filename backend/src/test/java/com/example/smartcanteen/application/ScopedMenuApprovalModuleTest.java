package com.example.smartcanteen.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.smartcanteen.application.port.MenuApproval;
import com.example.smartcanteen.application.port.MenuStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.Menu;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScopedMenuApprovalModuleTest {

    @Test
    void same_menu_id_is_isolated_between_canteens() {
        CanteenScope north = new CanteenScope("SCHOOL-SCOPE", "CANTEEN-NORTH");
        CanteenScope south = new CanteenScope("SCHOOL-SCOPE", "CANTEEN-SOUTH");
        InMemoryMenuStore store = new InMemoryMenuStore();
        store.put(north, new Menu("MENU-SHARED"));
        store.put(south, new Menu("MENU-SHARED"));
        MenuApproval module = new MenuApprovalService(store);

        module.submit(north, "MENU-SHARED");
        module.decide(north, "MENU-SHARED", "APPROVE", "north approval");

        assertThat(store.findMenu(north, "MENU-SHARED").orElseThrow().status().name())
                .isEqualTo("APPROVED");
        assertThat(store.findMenu(south, "MENU-SHARED").orElseThrow().status().name())
                .isEqualTo("DRAFT");
    }

    private static final class InMemoryMenuStore implements MenuStore {

        private final Map<String, Menu> menus = new HashMap<>();

        void put(CanteenScope scope, Menu menu) {
            menus.put(key(scope, menu.id()), menu);
        }

        @Override
        public Optional<Menu> findMenu(String menuId) {
            throw new UnsupportedOperationException("scope is required");
        }

        @Override
        public Optional<Menu> findMenu(CanteenScope scope, String menuId) {
            return Optional.ofNullable(menus.get(key(scope, menuId)));
        }

        @Override
        public void saveMenu(Menu menu) {
            throw new UnsupportedOperationException("scope is required");
        }

        @Override
        public void saveMenu(CanteenScope scope, Menu menu) {
            put(scope, menu);
        }

        private static String key(CanteenScope scope, String menuId) {
            return scope.schoolId() + ":" + scope.canteenId() + ":" + menuId;
        }
    }
}
