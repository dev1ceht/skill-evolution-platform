package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.Menu;
import com.example.smartcanteen.domain.CanteenScope;
import java.util.Optional;

/** Persistence seam for menu approval. */
public interface MenuStore {

    Optional<Menu> findMenu(String menuId);

    void saveMenu(Menu menu);

    default Optional<Menu> findMenu(CanteenScope scope, String menuId) {
        return findMenu(menuId);
    }

    default void saveMenu(CanteenScope scope, Menu menu) {
        saveMenu(menu);
    }
}
