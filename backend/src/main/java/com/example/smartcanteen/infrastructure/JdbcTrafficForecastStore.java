package com.example.smartcanteen.infrastructure;

import com.example.smartcanteen.application.port.TrafficForecastStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.TrafficForecast;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC adapter for versioned traffic forecast facts. */
@Repository
public class JdbcTrafficForecastStore implements TrafficForecastStore {

    private final JdbcTemplate jdbc;

    public JdbcTrafficForecastStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<TrafficForecast> find(
            CanteenScope scope, LocalDate forecastDate, String mealTime) {
        return jdbc.query(
                        "SELECT forecast_date, meal_time, expected_diner_count, lower_bound, "
                                + "upper_bound, model_version, source, generated_at "
                                + "FROM traffic_forecasts WHERE school_id = ? AND canteen_id = ? "
                                + "AND forecast_date = ? AND meal_time = ? "
                                + "ORDER BY generated_at DESC, model_version DESC LIMIT 1",
                        (result, row) -> new TrafficForecast(
                                result.getDate("forecast_date").toLocalDate(),
                                result.getString("meal_time"),
                                result.getLong("expected_diner_count"),
                                result.getLong("lower_bound"),
                                result.getLong("upper_bound"),
                                result.getString("model_version"),
                                result.getString("source"),
                                timestamp(result.getTimestamp("generated_at"))),
                        scope.schoolId(),
                        scope.canteenId(),
                        forecastDate,
                        TrafficForecast.normalizeMealTime(mealTime))
                .stream()
                .findFirst();
    }

    private static java.time.Instant timestamp(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
