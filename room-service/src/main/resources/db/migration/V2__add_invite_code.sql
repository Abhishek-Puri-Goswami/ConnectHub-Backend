-- P2-13: Add invite_code column to rooms table for shareable room invite links
ALTER TABLE rooms ADD COLUMN invite_code VARCHAR(20) DEFAULT NULL UNIQUE;
