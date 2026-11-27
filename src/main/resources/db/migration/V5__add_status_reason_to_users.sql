-- V5__add_status_reason_to_users.sql
-- Adds nullable moderation reason column for account lock/delete actions

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS status_reason TEXT NULL;
