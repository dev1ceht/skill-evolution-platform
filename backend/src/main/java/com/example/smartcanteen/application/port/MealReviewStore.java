package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.MealReview;
import com.example.smartcanteen.domain.PageResult;
import java.util.Optional;

public interface MealReviewStore {

    PageResult<MealReview> listMine(
            CanteenScope scope, String actorUserId, int page, int size);

    Optional<MealReview> findByOrder(
            CanteenScope scope, String actorUserId, String orderId);

    MealReview create(
            CanteenScope scope,
            MealReview review,
            String idempotencyKey,
            String requestHash);
}
