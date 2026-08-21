package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.MealPayment;
import java.util.Optional;

public interface MealPaymentStore {

    Optional<MealPayment> findByIdempotency(
            CanteenScope scope, String actorUserId, String idempotencyKey);

    Optional<MealPayment> findByOrder(CanteenScope scope, String orderId);

    MealPayment create(CanteenScope scope, MealPayment payment);
}
