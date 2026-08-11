package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.Menu;

/** Public use-case interface for menu submission and approval decisions. */
public interface MenuApproval {

    Menu submit(String menuId);

    Menu decide(String menuId, String decision, String comment);
}
