-- 좌석 동시 선점 경쟁 테스트(scripts/test-scenarios/s02-seat-hold-concurrency/seat-concurrency.js)를 다음 동시성 단계(10 -> 50 -> 100 -> 300)로
-- 넘어가기 전에, 해당 좌석과 이번 테스트가 만든 선점/예매 흔적을 원래 상태(AVAILABLE)로 되돌린다.
--
-- 사용법 (ticketing DB, docker exec로 이미 떠 있는 tikitaka-ticketing-postgres 컨테이너 안에서 실행 -
-- 로컬에 psql 설치 불필요, SQL 파일은 호스트에만 있으므로 -f 대신 표준입력으로 흘려보낸다):
--   set -a; source .env; set +a
--   docker exec -i -e "PGPASSWORD=${TICKETING_DB_PASSWORD}" tikitaka-ticketing-postgres \
--     psql -U "${TICKETING_DB_USERNAME:-ticketing}" -d "${TICKETING_DB_NAME:-tikitaka_ticketing}" -v ON_ERROR_STOP=1 \
--     -v seat_id="40000000-0000-0000-0000-000000000001" \
--     -v session_id="31000000-0000-0000-0000-000000000001" \
--   < scripts/test-scenarios/s02-seat-hold-concurrency/reset-seat-hold-race.sql


\set ON_ERROR_STOP on

BEGIN;

-- 1) 이 좌석에 연결된 예매-좌석 매핑 제거 (FK RESTRICT라서 seat_hold보다 먼저 지워야 함)
DELETE FROM p_reservation_seats
WHERE schedule_seat_id = :'seat_id'::uuid;

-- 2) 이번 좌석 테스트(runTag가 'race-'로 시작하는 idempotency_key)가 만든, 이제 좌석 매핑이
--    하나도 안 남은(=이 테스트 때문에 생긴) 예매만 정리. 다른 좌석과 묶여 있던 예매는 건드리지 않는다.
DELETE FROM p_reservation
WHERE event_session_id = :'session_id'::uuid
  AND idempotency_key LIKE 'race-rsv-%'
  AND reservation_id NOT IN (SELECT DISTINCT reservation_id FROM p_reservation_seats);

-- 3) 이 좌석에 남아있는 SeatHold 전부 제거 (성공자의 CONFIRMED 건 포함, 다음 라운드를 위해 초기화)
DELETE FROM p_seat_hold
WHERE schedule_seat_id = :'seat_id'::uuid;

-- 4) 좌석 상태를 다시 판매 가능으로
UPDATE p_schedule_seat
SET seat_status = 'AVAILABLE',
    updated_at = CURRENT_TIMESTAMP
WHERE schedule_seat_id = :'seat_id'::uuid;

COMMIT;

-- 리셋 결과 확인
SELECT schedule_seat_id, seat_status
FROM p_schedule_seat
WHERE schedule_seat_id = :'seat_id'::uuid;
