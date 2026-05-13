-- Add razorpay_subscription_id column for recurring subscription support.
-- Existing rows keep razorpay_order_id for backward compatibility with
-- one-time payment records.

ALTER TABLE subscriptions
    ADD COLUMN razorpay_subscription_id VARCHAR(100) NULL AFTER user_email,
    ADD INDEX idx_subscription_rzp_sub_id (razorpay_subscription_id);
