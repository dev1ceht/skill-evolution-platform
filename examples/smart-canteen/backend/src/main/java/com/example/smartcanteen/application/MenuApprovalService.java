package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.MenuApproval;
import com.example.smartcanteen.application.port.MenuStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.Menu;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MenuApprovalService implements MenuApproval {

    private final MenuStore menus;

    public MenuApprovalService(MenuStore menus) {
        this.menus = menus;
    }

    @Override
    @Transactional
    public Menu submit(String menuId) {
        return submit(CanteenScope.DEFAULT, menuId);
    }

    @Override
    @Transactional
    public Menu submit(CanteenScope scope, String menuId) {
        requireIdentifier("menuId", menuId, 64);
        Menu menu = requireMenu(scope, menuId);
        menu.submit();
        menus.saveMenu(scope, menu);
        return menu;
    }

    @Override
    @Transactional
    public Menu decide(String menuId, String decision, String comment) {
        return decide(CanteenScope.DEFAULT, menuId, decision, comment);
    }

    @Override
    @Transactional
    public Menu decide(CanteenScope scope, String menuId, String decision, String comment) {
        requireIdentifier("menuId", menuId, 64);
        Menu menu = requireMenu(scope, menuId);
        if ("APPROVE".equalsIgnoreCase(decision)) {
            menu.approve(comment);
        } else if ("REJECT".equalsIgnoreCase(decision)) {
            menu.reject(comment);
        } else {
            throw new IllegalArgumentException("Unsupported approval decision: " + decision);
        }
        menus.saveMenu(scope, menu);
        return menu;
    }

    private Menu requireMenu(CanteenScope scope, String menuId) {
        return menus.findMenu(scope, menuId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown menu: " + menuId));
    }

    private static void requireIdentifier(String label, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(label + " exceeds " + maxLength + " characters");
        }
    }
}
