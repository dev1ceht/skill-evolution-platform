package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.DinerComplaintStore;
import com.example.smartcanteen.application.port.MealOrderStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.DinerComplaint;
import com.example.smartcanteen.domain.MealOrder;
import com.example.smartcanteen.domain.PageResult;
import com.example.smartcanteen.security.ForbiddenException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DinerComplaintService {

    private final DinerComplaintStore complaints;
    private final MealOrderStore orders;
    private final Clock clock;

    @Autowired
    public DinerComplaintService(DinerComplaintStore complaints, MealOrderStore orders) {
        this(complaints, orders, Clock.systemUTC());
    }

    public DinerComplaintService(
            DinerComplaintStore complaints, MealOrderStore orders, Clock clock) {
        this.complaints = Objects.requireNonNull(complaints, "complaints");
        this.orders = Objects.requireNonNull(orders, "orders");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(readOnly = true)
    public PageResult<DinerComplaint> listMine(
            CanteenScope scope, String actorUserId, String status, int page, int size) {
        requireText(actorUserId, "actorUserId", 64);
        String normalizedStatus = status == null || status.isBlank()
                ? null : status.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalizedStatus != null && !"SUBMITTED".equals(normalizedStatus)) {
            throw new IllegalArgumentException("Unsupported diner complaint status: " + status);
        }
        return complaints.listMine(scope, actorUserId, normalizedStatus, page, size);
    }

    @Transactional
    public DinerComplaint create(
            CanteenScope scope,
            String actorUserId,
            String category,
            String subject,
            String description,
            String relatedOrderId,
            String idempotencyKey) {
        String normalizedActor = requireText(actorUserId, "actorUserId", 64);
        String normalizedCategory = requireText(category, "category", 32)
                .toUpperCase(java.util.Locale.ROOT);
        if (!DinerComplaint.isSupportedCategory(normalizedCategory)) {
            throw new IllegalArgumentException("Unsupported complaint category: " + category);
        }
        String normalizedSubject = requireText(subject, "subject", 120);
        String normalizedDescription = requireText(description, "description", 2000);
        String normalizedOrderId = relatedOrderId == null || relatedOrderId.isBlank()
                ? null : requireText(relatedOrderId, "relatedOrderId", 64);
        String normalizedKey = requireText(idempotencyKey, "idempotencyKey", 128);
        if (normalizedOrderId != null) {
            MealOrder order = orders.find(scope, normalizedOrderId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Related meal order not found: " + normalizedOrderId));
            if (!normalizedActor.equals(order.actorUserId())) {
                throw new ForbiddenException("Only the order owner can reference a meal order");
            }
        }
        Instant now = clock.instant();
        DinerComplaint complaint = new DinerComplaint(
                "COMPLAINT-" + UUID.randomUUID(),
                normalizedActor,
                normalizedCategory,
                normalizedSubject,
                normalizedDescription,
                normalizedOrderId,
                "SUBMITTED",
                null,
                0,
                now,
                now);
        return complaints.create(scope, complaint, normalizedKey, requestHash(complaint));
    }

    private static String requestHash(DinerComplaint complaint) {
        String canonical = complaint.category() + "|" + complaint.subject() + "|"
                + complaint.description() + "|"
                + (complaint.relatedOrderId() == null ? "" : complaint.relatedOrderId());
        return sha256(canonical);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " must be non-blank and at most " + maxLength + " characters");
        }
        return value.trim();
    }
}
