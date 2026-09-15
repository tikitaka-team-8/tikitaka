-- S08 Retry 성공 보충 테스트 - Notification 일시적 실패 주입 제거

\set ON_ERROR_STOP on
BEGIN;

DROP TRIGGER IF EXISTS trg_s08_retry_success_notification_inbox ON p_notification_inbox;
DROP FUNCTION IF EXISTS raise_s08_retry_success_notification_inbox();
DROP SEQUENCE IF EXISTS s08_retry_success_notification_attempt_seq;

COMMIT;
