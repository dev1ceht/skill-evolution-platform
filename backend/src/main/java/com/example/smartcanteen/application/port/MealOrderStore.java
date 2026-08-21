package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.MealOrder;
import com.example.smartcanteen.domain.PageResult;
import java.util.Optional;

public interface MealOrderStore {

    PageResult<MealOrder> listMine(
            CanteenScope scope, String actorUserId, String status, int page, int size);

    Optional<MealOrder> find(CanteenScope scope, String orderId);

    MealOrder create(
            CanteenScope scope,
            MealOrder order,
            String idempotencyKey,
            String requestHash);

    MealOrder cancel(
            CanteenScope scope,
            String orderId,
            String actorUserId,
            long expectedVersion);
}
