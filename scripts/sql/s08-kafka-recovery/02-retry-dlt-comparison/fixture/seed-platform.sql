-- S08 Kafka 실패 격리 테스트 - Platform DB
-- 실행 예:
--   psql -v ON_ERROR_STOP=1 -v password_hash='테스트용_해시값' -f seed-s08-failure-platform.sql
-- 실제 비밀번호/JWT/Service Key는 포함하지 않습니다.

\set ON_ERROR_STOP on
BEGIN;

INSERT INTO p_user (
    id, email, password_hash, name, nickname, phone, role, status
)
SELECT
    9100000 + gs,
    format('s08-user-%s@test.tikitaka.local', lpad(gs::text, 3, '0')),
    :'password_hash',
    format('[S08] Kafka 실패 격리 테스트 사용자 %s', lpad(gs::text, 3, '0')),
    format('s08-kafka-user-%s', lpad(gs::text, 3, '0')),
    '010-89' || lpad((((gs - 1) / 100)::int)::text, 2, '0') || '-' || lpad((7000 + gs)::text, 4, '0'),
    'USER',
    'ACTIVE'
FROM generate_series(1, 600) AS gs;

COMMIT;
