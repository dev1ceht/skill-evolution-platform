package com.example.smartcanteen.domain;

import java.util.List;

public record PageResult<T>(List<T> records, int current, int size, long total) {

    public PageResult {
        records = records == null ? List.of() : List.copyOf(records);
        if (current < 1 || size < 1) {
            throw new IllegalArgumentException("Page current and size must be positive");
        }
        if (total < 0) {
            throw new IllegalArgumentException("Page total cannot be negative");
        }
    }

    public long pages() {
        return total == 0 ? 0 : (total + size - 1) / size;
    }
}
