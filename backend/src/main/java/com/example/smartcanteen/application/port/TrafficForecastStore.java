package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.TrafficForecast;
import java.time.LocalDate;
import java.util.Optional;

/** Persistence port for versioned traffic forecast facts. */
public interface TrafficForecastStore {

    Optional<TrafficForecast> find(
            CanteenScope scope, LocalDate forecastDate, String mealTime);
}
