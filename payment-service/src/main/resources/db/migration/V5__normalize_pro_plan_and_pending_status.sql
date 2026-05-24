-- Normalize legacy "PRO" plan name to "PREMIUM" for all existing rows.
-- "PRO" was the original plan name before PREMIUM/PLATINUM tiers were introduced.
UPDATE subscriptions SET plan = 'PREMIUM' WHERE plan = 'PRO';
