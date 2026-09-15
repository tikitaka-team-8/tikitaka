-- S08 Retry 성공 보충 테스트 - Ticketing 단일 Fixture와 일시적 DB 실패 준비
-- 결제 성공 이벤트 1건의 최초 두 처리는 실패시키고 세 번째 처리는 통과시킵니다.

\set ON_ERROR_STOP on
BEGIN;

-- 앞선 혼합 실패 테스트의 영구 실패 트리거를 제거합니다.
DROP TRIGGER IF EXISTS trg_s08_retry_failure_reservation_inbox ON p_reservation_inbox;
DROP FUNCTION IF EXISTS raise_s08_retry_failure_reservation_inbox();

DROP TRIGGER IF EXISTS trg_s08_retry_success_reservation_inbox ON p_reservation_inbox;
DROP FUNCTION IF EXISTS raise_s08_retry_success_reservation_inbox();
DROP SEQUENCE IF EXISTS s08_retry_success_ticketing_attempt_seq;

-- 공식 테스트에서 확정된 1번 예매만 결제 처리 직전 상태로 되돌립니다.
DELETE FROM p_reservation_inbox
WHERE reservation_id = '81000000-0000-0000-0000-000000000001'::uuid;

DELETE FROM p_reservation_outbox
WHERE reservation_id = '81000000-0000-0000-0000-000000000001'::uuid;

UPDATE p_reservation
SET reservation_status = 'PAYMENT_PROCESSING',
    failure_reason = NULL,
    payment_completed_at = NULL,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 9100001,
    version = 0
WHERE reservation_id = '81000000-0000-0000-0000-000000000001'::uuid;

UPDATE p_seat_hold
SET hold_status = 'RESERVED',
    released_at = NULL,
    release_reason = NULL,
    reserved_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 9100001
WHERE seat_hold_id = '84000000-0000-0000-0000-000000000001'::uuid;

UPDATE p_schedule_seat
SET seat_status = 'HELD',
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 9100001
WHERE schedule_seat_id = '83000000-0000-0000-0000-000000000001'::uuid;

-- SEQUENCE 증가는 트랜잭션 롤백의 영향을 받지 않아 재시도 횟수를 유지합니다.
CREATE SEQUENCE s08_retry_success_ticketing_attempt_seq START WITH 1;

CREATE FUNCTION raise_s08_retry_success_reservation_inbox()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    attempt BIGINT;
BEGIN
    IF NEW.event_id = 'f3000000-0000-0000-0000-000000000001'::uuid THEN
        attempt := nextval('s08_retry_success_ticketing_attempt_seq');

        IF attempt <= 2 THEN
            RAISE EXCEPTION 'S08 transient Ticketing DB failure: event_id=%, attempt=%', NEW.event_id, attempt
                USING ERRCODE = '40001';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_s08_retry_success_reservation_inbox
    BEFORE INSERT ON p_reservation_inbox
    FOR EACH ROW
    EXECUTE FUNCTION raise_s08_retry_success_reservation_inbox();

COMMIT;
