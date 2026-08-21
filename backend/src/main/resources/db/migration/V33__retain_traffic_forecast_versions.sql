-- SC-007: keep each generated forecast fact so the read path can select the latest version.

ALTER TABLE traffic_forecasts DROP PRIMARY KEY;

ALTER TABLE traffic_forecasts
    ADD PRIMARY KEY (school_id, canteen_id, forecast_date, meal_time, generated_at);
