-- =========================================================
-- Seat hold: idempotency key support
-- =========================================================

ALTER TABLE p_seat_hold
    ADD COLUMN idempotency_key VARCHAR(100);

-- 기존에 적재된 row(있다면)에 대한 백필: seat_hold_id 기반으로 유일한 값을 채워준다.
UPDATE p_seat_hold
SET idempotency_key = 'legacy-' || seat_hold_id
WHERE idempotency_key IS NULL;

ALTER TABLE p_seat_hold
    ALTER COLUMN idempotency_key SET NOT NULL;

-- 동일 사용자가 동일 Idempotency-Key로 재요청하면 같은 선점 건을 반환하기 위한 유니크 제약
ALTER TABLE p_seat_hold
    ADD CONSTRAINT uq_seat_hold_user_idempotency UNIQUE (user_id, idempotency_key);
