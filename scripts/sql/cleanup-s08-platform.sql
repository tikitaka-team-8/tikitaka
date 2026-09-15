-- S08 Kafka 장애·복구 부하 테스트 - Platform DB cleanup
\set ON_ERROR_STOP on
BEGIN;

DELETE FROM p_user
WHERE id BETWEEN 9100001 AND 9100100
  AND email LIKE 's08-user-%@test.tikitaka.local'
  AND nickname LIKE 's08-kafka-user-%';

COMMIT;
