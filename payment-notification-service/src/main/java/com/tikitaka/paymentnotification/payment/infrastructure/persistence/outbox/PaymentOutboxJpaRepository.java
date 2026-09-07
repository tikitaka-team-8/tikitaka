package com.tikitaka.paymentnotification.payment.infrastructure.persistence.outbox;

import com.tikitaka.paymentnotification.payment.domain.outbox.PaymentOutbox;
import com.tikitaka.paymentnotification.payment.domain.outbox.PaymentOutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PaymentOutboxJpaRepository extends JpaRepository<PaymentOutbox, UUID> {

    @Query(
            value = """
                    SELECT *
                    FROM p_payment_outbox
                    WHERE status = 'PENDING'
                    ORDER BY created_at ASC
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true
    )
    List<PaymentOutbox> findPendingOutboxesForUpdate(
            @Param("limit") int limit
    );


}
