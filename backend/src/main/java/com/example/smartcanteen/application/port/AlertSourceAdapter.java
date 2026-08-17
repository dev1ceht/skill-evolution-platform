package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.AlertReport;
import java.util.List;

/** Normalizes an external source into the alert-center report contract. */
public interface AlertSourceAdapter {

    List<AlertReport> pullAlerts();
}
