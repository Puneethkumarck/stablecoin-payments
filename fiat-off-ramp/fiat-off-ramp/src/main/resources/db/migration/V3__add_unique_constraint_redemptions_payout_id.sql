-- Add unique constraint on stablecoin_redemptions.payout_id
-- Domain model assumes 1:1 relationship between payout and redemption
ALTER TABLE stablecoin_redemptions
    ADD CONSTRAINT stablecoin_redemptions_payout_id_unique UNIQUE (payout_id);
