package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.Menu;
import com.example.smartcanteen.domain.CanteenScope;

/** Public use-case interface for menu submission and approval decisions. */
public interface MenuApproval {

    Menu submit(String menuId);

    Menu decide(String menuId, String decision, String comment);

    default Menu submit(CanteenScope scope, String menuId) {
        return submit(menuId);
    }

    default Menu decide(CanteenScope scope, String menuId, String decision, String comment) {
        return decide(menuId, decision, comment);
    }
}
