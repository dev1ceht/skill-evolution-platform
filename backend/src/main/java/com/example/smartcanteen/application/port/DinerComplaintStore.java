package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.DinerComplaint;
import com.example.smartcanteen.domain.PageResult;
import java.util.Optional;

public interface DinerComplaintStore {

    PageResult<DinerComplaint> listMine(
            CanteenScope scope, String actorUserId, String status, int page, int size);

    DinerComplaint create(
            CanteenScope scope,
            DinerComplaint complaint,
            String idempotencyKey,
            String requestHash);

    Optional<DinerComplaint> findByIdempotency(
            CanteenScope scope, String actorUserId, String idempotencyKey);
}
