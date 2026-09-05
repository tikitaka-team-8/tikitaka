-- 결제 정보 생성 단계에서 Seat Hold 만료 시각을 연장한 시각을 기록한다.
-- NULL이면 아직 한 번도 연장되지 않은 선점이다. (동일 요청 재호출 시 중복 연장을 막는 데 사용)
ALTER TABLE p_seat_hold
    ADD COLUMN extended_at TIMESTAMP;
