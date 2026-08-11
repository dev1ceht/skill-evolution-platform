package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.Menu;
import java.util.Optional;

/** Persistence seam for menu approval. */
public interface MenuStore {

    Optional<Menu> findMenu(String menuId);

    void saveMenu(Menu menu);
}
