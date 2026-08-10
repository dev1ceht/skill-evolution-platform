package com.example.smartcanteen.domain;

import java.util.Objects;

public final class Menu {

    private final String id;
    private MenuStatus status = MenuStatus.DRAFT;
    private String decisionComment;

    public Menu(String id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public void submit() {
        requireStatus(MenuStatus.DRAFT);
        status = MenuStatus.PENDING_APPROVAL;
    }

    public void approve(String comment) {
        requireStatus(MenuStatus.PENDING_APPROVAL);
        status = MenuStatus.APPROVED;
        decisionComment = comment;
    }

    public void reject(String comment) {
        requireStatus(MenuStatus.PENDING_APPROVAL);
        status = MenuStatus.REJECTED;
        decisionComment = comment;
    }

    private void requireStatus(MenuStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Menu %s cannot transition from %s; expected %s".formatted(id, status, expected));
        }
    }

    public String id() {
        return id;
    }

    public MenuStatus status() {
        return status;
    }

    public String decisionComment() {
        return decisionComment;
    }
}
