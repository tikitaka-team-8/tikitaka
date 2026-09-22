-- 좌석 동시 선점 경쟁 테스트 후 최종 데이터 정합성을 확인한다.
-- seat-concurrency.js는 좌석 락 동시성만 검증하고 예매·결제로는 진행하지 않으므로(범위 밖),
-- 기대하는 최종 상태는 "승자 1명이 좌석을 HOLDING 상태로 선점한 채 멈춰있다"이다:
--   1) 활성 Seat Hold와 좌석 선점 성공자는 정확히 한 명이다.
--   2) 나머지는 동일하고 설명 가능한 충돌 응답을 받는다 (-> 애플리케이션/HTTP 레벨 확인 대상, 여기선 DB 상태만)
--   3) 패배자에게는 애초에 Seat Hold가 생성되지 않는다(관대한 락 순서상 RELEASED조차 남지 않음).
--   4) 예매(Reservation)는 이 테스트 범위 밖이라 생성되지 않는다 - 아래 4번 쿼리가 0행이면 정상.
--
-- 사용법 (ticketing DB, docker exec로 이미 떠 있는 tikitaka-ticketing-postgres 컨테이너 안에서 실행 -
-- 로컬에 psql 설치 불필요, SQL 파일은 호스트에만 있으므로 -f 대신 표준입력으로 흘려보낸다):
--   set -a; source .env; set +a
--   docker exec -i -e "PGPASSWORD=${TICKETING_DB_PASSWORD}" tikitaka-ticketing-postgres \
--     psql -U "${TICKETING_DB_USERNAME:-ticketing}" -d "${TICKETING_DB_NAME:-tikitaka_ticketing}" -v ON_ERROR_STOP=1 \
--     -v seat_id="40000000-0000-0000-0000-000000000001" \
--   < scripts/test-scenarios/s02-seat-hold-concurrency/verify-seat-hold-race.sql

\set ON_ERROR_STOP on

-- 1) 최종 좌석 상태 - HELD 1건이어야 한다(승자가 선점한 상태 그대로 - 이 테스트는 SOLD까지 진행하지 않는다).
SELECT '1) seat_status' AS check_name, seat_status::text AS value, NULL AS extra
FROM p_schedule_seat
WHERE schedule_seat_id = :'seat_id'::uuid

UNION ALL

-- 2) SeatHold 상태별 건수 - HOLDING 1건만 있어야 정상(승자의 선점, 예매로 이어지지 않아 CONFIRMED로
--    전이되지 않음). 패배자는 애초에 SeatHold가 생성되지 않으므로 RELEASED 건수는 항상 0이다.
SELECT '2) seat_hold_status_count', hold_status::text, count(*)::text
FROM p_seat_hold
WHERE schedule_seat_id = :'seat_id'::uuid
GROUP BY hold_status

UNION ALL

-- 3) 활성(HOLDING/RESERVED) 선점 건수 - 승자 1건이어야 한다(예매로 진행하지 않으므로 계속 HOLDING으로 남아있는 것이 정상).
SELECT '3) active_seat_hold_count', count(*)::text, NULL
FROM p_seat_hold
WHERE schedule_seat_id = :'seat_id'::uuid
  AND hold_status IN ('HOLDING', 'RESERVED')

UNION ALL

-- 4) 이 좌석에 걸린 예매 상태별 건수 - 이 테스트는 예매를 진행하지 않으므로 0행이 정상이다(행이 하나라도 있으면 테스트 범위 밖의 예매가 섞여 들어온 것).
SELECT '4) reservation_status_count', r.reservation_status::text, count(*)::text
FROM p_reservation r
JOIN p_reservation_seats rs ON rs.reservation_id = r.reservation_id
WHERE rs.schedule_seat_id = :'seat_id'::uuid
GROUP BY r.reservation_status

ORDER BY 1;

-- ── 참고: 데드락/락 대기/커넥션 풀은 DB 통계라 위 쿼리 하나로는 안 보인다.
-- 테스트 "동안" 아래 두 개를 별도로 관찰하세요 (README 참고).
--   데드락 누적 건수:
--     SELECT deadlocks FROM pg_stat_database WHERE datname = current_database();
--   현재 락을 기다리는 세션:
--     SELECT pid, wait_event_type, wait_event, query, query_start
--     FROM pg_stat_activity
--     WHERE datname = current_database() AND wait_event_type = 'Lock';
