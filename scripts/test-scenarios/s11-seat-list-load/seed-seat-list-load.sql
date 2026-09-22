-- =========================================================
-- 좌석 목록 반복조회 부하테스트용 더미 좌석 생성
-- 기존 seat-concurrency 테스트용 8개 좌석(40000000-...-0001~0008, created_by=900001)은
-- 건드리지 않습니다. 이 스크립트가 만드는 행은 created_by=900099로 표시해서
-- cleanup-seat-list-load.sql로만 골라 지울 수 있게 구분합니다.
--
-- 사용 예 (중형 규모, 매진임박 상태 분포로 3,000석 생성):
--   psql "host=localhost port=5434 dbname=tikitaka_ticketing user=ticketing" \
--     -v session_id="'31000000-0000-0000-0000-000000000001'" \
--     -v seat_count=3000 \
--     -v available_ratio=0.05 \
--     -v held_ratio=0.05 \
--     -f scripts/test-scenarios/s11-seat-list-load/seed-seat-list-load.sql
-- =========================================================

BEGIN;

INSERT INTO p_schedule_seat (
    schedule_seat_id, event_session_id, venue_seat_id,
    section, row_label, seat_number, seat_grade, price, seat_status,
    created_by, updated_by
)
SELECT
    gen_random_uuid(),
    :session_id::uuid,
    gen_random_uuid(),
    section, row_label, seat_number, seat_grade, price,
    CASE
        WHEN roll < :available_ratio THEN 'AVAILABLE'
        WHEN roll < (:available_ratio + :held_ratio) THEN 'HELD'
        ELSE 'SOLD'
    END AS seat_status,
    900099,
    900099
FROM (
    SELECT
        gs,
        random() AS roll,
        CASE
            WHEN gs <= :seat_count * 0.1 THEN 'VIP'
            WHEN gs <= :seat_count * 0.4 THEN 'R'
            ELSE 'S'
        END AS section,
        chr(65 + ((gs / 20) % 26)) AS row_label,
        ((gs % 20) + 1)::text AS seat_number,
        CASE
            WHEN gs <= :seat_count * 0.1 THEN 'VIP'
            WHEN gs <= :seat_count * 0.4 THEN 'R'
            ELSE 'S'
        END AS seat_grade,
        CASE
            WHEN gs <= :seat_count * 0.1 THEN 150000
            WHEN gs <= :seat_count * 0.4 THEN 100000
            ELSE 70000
        END AS price
    FROM generate_series(1, :seat_count) AS gs
) t;

COMMIT;

-- 생성 후 상태 분포 확인:
-- SELECT seat_status, count(*) FROM p_schedule_seat
-- WHERE event_session_id = '31000000-0000-0000-0000-000000000001' GROUP BY seat_status;
