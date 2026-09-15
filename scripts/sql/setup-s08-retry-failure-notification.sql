-- S08 Retry·DLT 적용 후 테스트 - Notification DB 재시도 실패 주입
-- 짝수 번호의 테스트 Reservation 이벤트 5건만 트랜잭션 직렬화 실패로 처리합니다.

\set ON_ERROR_STOP on
BEGIN;

DROP TRIGGER IF EXISTS trg_s08_retry_failure_notification_inbox ON p_notification_inbox;
DROP FUNCTION IF EXISTS raise_s08_retry_failure_notification_inbox();

CREATE FUNCTION raise_s08_retry_failure_notification_inbox()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.event_id IN (
        'f2000000-0000-0000-0000-000000000002'::uuid,
        'f2000000-0000-0000-0000-000000000004'::uuid,
        'f2000000-0000-0000-0000-000000000006'::uuid,
        'f2000000-0000-0000-0000-000000000008'::uuid,
        'f2000000-0000-0000-0000-000000000010'::uuid
    ) THEN
        RAISE EXCEPTION 'S08 retryable Notification DB failure: event_id=%', NEW.event_id
            USING ERRCODE = '40001';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_s08_retry_failure_notification_inbox
    BEFORE INSERT ON p_notification_inbox
    FOR EACH ROW
    EXECUTE FUNCTION raise_s08_retry_failure_notification_inbox();

COMMIT;
