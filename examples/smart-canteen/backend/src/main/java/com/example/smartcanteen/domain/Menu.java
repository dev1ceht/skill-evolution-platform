package com.example.smartcanteen.domain;

import java.util.Objects;

public final class Menu {

    private final String id;
    private final long version;
    private MenuStatus status = MenuStatus.DRAFT;
    private String decisionComment;

    public Menu(String id) {
        this(id, MenuStatus.DRAFT, null, 0);
    }

    private Menu(String id, MenuStatus status, String decisionComment, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.status = Objects.requireNonNull(status, "status");
        this.decisionComment = decisionComment;
        this.version = version;
    }

    public static Menu restore(
            String id, MenuStatus status, String decisionComment, long version) {
        return new Menu(id, status, decisionComment, version);
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

    public long version() {
        return version;
    }
}
