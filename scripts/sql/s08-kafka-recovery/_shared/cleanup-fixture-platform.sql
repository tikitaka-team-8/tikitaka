-- S08 Kafka 장애·복구 테스트 - 공통 Platform Fixture cleanup
-- 01의 사용자 100명과 02의 사용자 600명을 모두 포함하는 S08 전용 범위만 정리합니다.
\set ON_ERROR_STOP on
BEGIN;

DELETE FROM p_user
WHERE id BETWEEN 9100001 AND 9100600
  AND email LIKE 's08-user-%@test.tikitaka.local'
  AND nickname LIKE 's08-kafka-user-%';

COMMIT;
