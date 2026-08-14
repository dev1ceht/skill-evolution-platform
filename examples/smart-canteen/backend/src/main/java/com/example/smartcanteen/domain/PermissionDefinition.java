package com.example.smartcanteen.domain;

public record PermissionDefinition(
        String code,
        String name,
        String resource,
        String action,
        String description) {
}
