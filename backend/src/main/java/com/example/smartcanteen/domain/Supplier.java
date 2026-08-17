package com.example.smartcanteen.domain;

public record Supplier(
        String id,
        String name,
        String contactName,
        String contactPhone,
        String licenseNo,
        boolean active) {

    public Supplier {
        id = required(id, "supplierId", 64);
        name = required(name, "name", 200);
        contactName = optional(contactName, "contactName", 100);
        contactPhone = optional(contactPhone, "contactPhone", 32);
        licenseNo = optional(licenseNo, "licenseNo", 100);
    }

    private static String required(String value, String name, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new IllegalArgumentException(name + " exceeds " + max + " characters");
        }
        return normalized;
    }

    private static String optional(String value, String name, int max) {
        return value == null || value.isBlank() ? null : required(value, name, max);
    }
}
