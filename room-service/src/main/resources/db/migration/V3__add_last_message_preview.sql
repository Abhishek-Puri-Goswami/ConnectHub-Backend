ALTER TABLE rooms
    ADD COLUMN last_message_preview VARCHAR(200) NULL,
    ADD COLUMN last_message_sender_id INT NULL;
