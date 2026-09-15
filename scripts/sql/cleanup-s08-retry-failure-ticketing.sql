-- S08 Retry·DLT 적용 후 테스트 - Ticketing DB 재시도 실패 주입 제거

\set ON_ERROR_STOP on
BEGIN;

DROP TRIGGER IF EXISTS trg_s08_retry_failure_reservation_inbox ON p_reservation_inbox;
DROP FUNCTION IF EXISTS raise_s08_retry_failure_reservation_inbox();

COMMIT;
