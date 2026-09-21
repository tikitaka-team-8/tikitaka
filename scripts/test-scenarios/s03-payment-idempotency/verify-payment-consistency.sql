-- S03 Payment 멱등성 / 동시성 정합성 검증
-- 사용 전 아래 payment_id를 테스트 대상 값으로 변경한다.

-- 1. Payment 최종 상태 확인
SELECT
    payment_id,
    reservation_id,
    status,
    payment_provider,
    pg_payment_key,
    approved_at
FROM p_payment
WHERE payment_id = '17fef89c-b3e8-47ff-860a-8161dc6b8b36';


-- 2. 승인 Transaction 중복 생성 여부 확인
SELECT
    payment_id,
    transaction_type,
    provider,
    status,
    attempt_no,
    pg_transaction_id,
    completed_at
FROM p_payment_transaction
WHERE payment_id = '17fef89c-b3e8-47ff-860a-8161dc6b8b36'
ORDER BY created_at;


-- 3. Payment 성공 Outbox 중복 생성 여부 확인
SELECT
    payment_id,
    event_type,
    status,
    retry_count,
    published_at
FROM p_payment_outbox
WHERE payment_id = '17fef89c-b3e8-47ff-860a-8161dc6b8b36'
ORDER BY created_at;


-- 4. 건수 검증
SELECT
    (SELECT COUNT(*)
     FROM p_payment_transaction
     WHERE payment_id = '17fef89c-b3e8-47ff-860a-8161dc6b8b36'
       AND transaction_type = 'APPROVE') AS approve_transaction_count,

    (SELECT COUNT(*)
     FROM p_payment_outbox
     WHERE payment_id = '17fef89c-b3e8-47ff-860a-8161dc6b8b36'
       AND event_type = 'PAYMENT_SUCCEEDED') AS payment_succeeded_outbox_count;