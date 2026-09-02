-- =========================================================
-- Payment schema status and column naming update
-- =========================================================

-- 기존 CHECK 제약조건 제거
ALTER TABLE p_payment
DROP CONSTRAINT ck_payment_status;

-- 기존 데이터 상태값 변환
UPDATE p_payment
SET status = 'APPROVED'
WHERE status = 'PAID';

UPDATE p_payment
SET status = 'CANCELED'
WHERE status = 'CANCELLED';

-- 변경된 상태값 기준으로 CHECK 제약조건 재생성
ALTER TABLE p_payment
    ADD CONSTRAINT ck_payment_status
        CHECK (status IN ('READY','PROCESSING','APPROVED','FAILED','UNKNOWN','CANCELED'));

-- 컬럼명 변경
ALTER TABLE p_payment
    RENAME COLUMN cancelled_at TO canceled_at;