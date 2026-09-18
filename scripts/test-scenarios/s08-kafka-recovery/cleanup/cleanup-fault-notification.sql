-- S08 Kafka 장애·복구 테스트 - 공통 Notification 실패 주입 cleanup
-- 영구 실패와 재시도 후 성공 시나리오가 만든 DB 객체를 함께 제거합니다.
\set ON_ERROR_STOP on
BEGIN;

DROP TRIGGER IF EXISTS trg_s08_retry_failure_notification_inbox ON p_notification_inbox;
DROP FUNCTION IF EXISTS raise_s08_retry_failure_notification_inbox();

DROP TRIGGER IF EXISTS trg_s08_retry_success_notification_inbox ON p_notification_inbox;
DROP FUNCTION IF EXISTS raise_s08_retry_success_notification_inbox();
DROP SEQUENCE IF EXISTS s08_retry_success_notification_attempt_seq;

COMMIT;
