-- S05 응답 유실 멱등성 테스트 - Platform cleanup
-- 회원가입한 테스트 사용자의 userId와 email을 psql 변수로 전달합니다.
\set ON_ERROR_STOP on
BEGIN;

DELETE FROM p_session_section_price
WHERE id = '50050000-0000-0000-0000-000000000007';

DELETE FROM p_event_session
WHERE id = '50050000-0000-0000-0000-000000000006';

DELETE FROM p_event
WHERE id = '50050000-0000-0000-0000-000000000005';

DELETE FROM p_venue_seat
WHERE id = '50050000-0000-0000-0000-000000000004';

DELETE FROM p_venue_section
WHERE id = '50050000-0000-0000-0000-000000000003';

DELETE FROM p_venue
WHERE id = '50050000-0000-0000-0000-000000000002';

DELETE FROM p_organizer
WHERE id = '50050000-0000-0000-0000-000000000001';

DELETE FROM p_user
WHERE id = 9500000
  AND email = 's05-organizer@test.tikitaka.local';

DELETE FROM p_user
WHERE id = :'test_user_id'::bigint
  AND LOWER(email) = LOWER(:'test_user_email')
  AND LOWER(email) LIKE 's05-%@test.tikitaka.local';

COMMIT;
