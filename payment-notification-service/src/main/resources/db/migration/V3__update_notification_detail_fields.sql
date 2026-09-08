ALTER TABLE p_notification
    DROP CONSTRAINT ck_notification_read_consistency;

ALTER TABLE p_notification
    RENAME COLUMN read_at TO last_viewed_at;

ALTER TABLE p_notification
    ADD COLUMN reservation_number VARCHAR(30) NOT NULL;

ALTER TABLE p_notification
    ADD CONSTRAINT ck_notification_read_consistency
        CHECK (
            (read_status = 'UNREAD' AND last_viewed_at IS NULL)
            OR (read_status = 'READ' AND last_viewed_at IS NOT NULL)
        );
