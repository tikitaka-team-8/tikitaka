-- S08 Retry 성공 보충 테스트 - Notification 단일 Fixture와 일시적 DB 실패 준비
-- 1번 예매의 Reservation 이벤트 최초 두 처리는 실패시키고 세 번째 처리는 통과시킵니다.

\set ON_ERROR_STOP on
BEGIN;

-- 앞선 혼합 실패 테스트의 영구 실패 트리거를 제거합니다.
DROP TRIGGER IF EXISTS trg_s08_retry_failure_notification_inbox ON p_notification_inbox;
DROP FUNCTION IF EXISTS raise_s08_retry_failure_notification_inbox();

DROP TRIGGER IF EXISTS trg_s08_retry_success_notification_inbox ON p_notification_inbox;
DROP FUNCTION IF EXISTS raise_s08_retry_success_notification_inbox();
DROP SEQUENCE IF EXISTS s08_retry_success_notification_attempt_seq;

-- 공식 테스트에서 생성된 1번 예매의 알림 결과만 제거합니다.
DELETE FROM p_notification_inbox
WHERE reservation_id = '81000000-0000-0000-0000-000000000001'::uuid;

DELETE FROM p_notification
WHERE reservation_id = '81000000-0000-0000-0000-000000000001'::uuid;

-- SEQUENCE 증가는 트랜잭션 롤백의 영향을 받지 않아 재시도 횟수를 유지합니다.
CREATE SEQUENCE s08_retry_success_notification_attempt_seq START WITH 1;

CREATE FUNCTION raise_s08_retry_success_notification_inbox()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    attempt BIGINT;
BEGIN
    IF NEW.reservation_id = '81000000-0000-0000-0000-000000000001'::uuid
            AND NEW.event_type = 'RESERVATION_CONFIRMED' THEN
        attempt := nextval('s08_retry_success_notification_attempt_seq');

        IF attempt <= 2 THEN
            RAISE EXCEPTION 'S08 transient Notification DB failure: event_id=%, attempt=%', NEW.event_id, attempt
                USING ERRCODE = '40001';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_s08_retry_success_notification_inbox
    BEFORE INSERT ON p_notification_inbox
    FOR EACH ROW
    EXECUTE FUNCTION raise_s08_retry_success_notification_inbox();

COMMIT;
