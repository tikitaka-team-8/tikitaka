-- seed-seat-list-load.sql로 생성한 더미 좌석만 삭제합니다.
-- (created_by=900099 로 표시된 행만 지우므로 seat-concurrency 픽스처는 영향받지 않습니다.)
--
-- 사용 예:
--   psql "host=localhost port=5434 dbname=tikitaka_ticketing user=ticketing" \
--     -v session_id="'31000000-0000-0000-0000-000000000001'" \
--     -f scripts/test-scenarios/s11-seat-list-load/cleanup-seat-list-load.sql

BEGIN;

DELETE FROM p_schedule_seat
WHERE event_session_id = :session_id::uuid
  AND created_by = 900099;

COMMIT;
