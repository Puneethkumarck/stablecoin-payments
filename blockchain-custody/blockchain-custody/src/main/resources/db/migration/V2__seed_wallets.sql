-- V2: Seed wallets for local dev (Base Sepolia testnet)

INSERT INTO wallets (wallet_id, chain_id, address, address_checksum, tier, purpose, custodian, vault_account_id, stablecoin, is_active)
VALUES
  (gen_random_uuid(), 'base', '0x1111222233334444555566667777888899990000', '0x1111222233334444555566667777888899990000', 'HOT', 'ON_RAMP', 'dev', 'dev-vault-onramp', 'USDC', true),
  (gen_random_uuid(), 'base', '0xaaaa222233334444555566667777888899990000', '0xaaaa222233334444555566667777888899990000', 'HOT', 'OFF_RAMP', 'dev', 'dev-vault-offramp', 'USDC', true);

-- Seed initial balances
INSERT INTO wallet_balances (balance_id, wallet_id, chain_id, stablecoin, available_balance, reserved_balance, blockchain_balance, last_indexed_block)
SELECT gen_random_uuid(), w.wallet_id, w.chain_id, w.stablecoin, 500000.00000000, 0.00000000, 500000.00000000, 0
FROM wallets w;

-- Seed nonces
INSERT INTO wallet_nonces (wallet_id, chain_id, current_nonce)
SELECT w.wallet_id, w.chain_id, 0
FROM wallets w;
