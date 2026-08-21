-- SC-007: versioned forecast facts are read by the assistant; forecast generation stays outside the Agent.

CREATE TABLE traffic_forecasts (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    forecast_date DATE NOT NULL,
    meal_time VARCHAR(16) NOT NULL,
    expected_diner_count BIGINT NOT NULL,
    lower_bound BIGINT NOT NULL,
    upper_bound BIGINT NOT NULL,
    model_version VARCHAR(64) NOT NULL,
    source VARCHAR(64) NOT NULL,
    generated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (school_id, canteen_id, forecast_date, meal_time),
    CONSTRAINT ck_traffic_forecasts_meal_time CHECK (
        meal_time IN ('BREAKFAST', 'LUNCH', 'DINNER', 'SNACK')
    ),
    CONSTRAINT ck_traffic_forecasts_expected CHECK (expected_diner_count > 0),
    CONSTRAINT ck_traffic_forecasts_lower CHECK (
        lower_bound >= 0 AND lower_bound <= expected_diner_count
    ),
    CONSTRAINT ck_traffic_forecasts_upper CHECK (upper_bound >= expected_diner_count)
);

CREATE INDEX idx_traffic_forecasts_scope_date
    ON traffic_forecasts(school_id, canteen_id, forecast_date, meal_time);
