package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.ProcurementItem;
import com.example.smartcanteen.domain.CanteenScope;
import java.util.List;

/** Public use-case interface for calculating procurement shortages. */
public interface ProcurementPlanning {

    List<ProcurementItem> generate(String menuId);

    default List<ProcurementItem> generate(CanteenScope scope, String menuId) {
        return generate(menuId);
    }
}
