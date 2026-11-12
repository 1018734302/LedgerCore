-- LedgerCore DDL (H2, MODE=MySQL)
-- 金额一律 BIGINT，单位：分
-- 使用 IF NOT EXISTS，应用每次启动可安全重放

CREATE TABLE IF NOT EXISTS account (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  account_no VARCHAR(64) NOT NULL,
  account_name VARCHAR(128) NOT NULL,
  account_type VARCHAR(32) NOT NULL,
  balance_cent BIGINT NOT NULL DEFAULT 0,
  opening_balance_cent BIGINT NOT NULL DEFAULT 0,
  frozen_cent BIGINT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_account_no UNIQUE (account_no)
);

CREATE TABLE IF NOT EXISTS voucher (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  voucher_no VARCHAR(64) NOT NULL,
  biz_type VARCHAR(32) NOT NULL,
  biz_no VARCHAR(64) NOT NULL,
  total_cent BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'POSTED',
  original_voucher_no VARCHAR(64),
  idempotency_key VARCHAR(128) NOT NULL,
  remark VARCHAR(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_voucher_no UNIQUE (voucher_no),
  CONSTRAINT uk_voucher_idem UNIQUE (biz_no, idempotency_key)
);

CREATE TABLE IF NOT EXISTS account_entry (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  voucher_no VARCHAR(64) NOT NULL,
  account_no VARCHAR(64) NOT NULL,
  direction VARCHAR(4) NOT NULL,
  amount_cent BIGINT NOT NULL,
  biz_no VARCHAR(64) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  before_balance_cent BIGINT NOT NULL,
  after_balance_cent BIGINT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_entry_idem UNIQUE (biz_no, idempotency_key, account_no, direction)
);

CREATE TABLE IF NOT EXISTS payment_order (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no VARCHAR(64) NOT NULL,
  buyer_no VARCHAR(64) NOT NULL,
  merchant_no VARCHAR(64) NOT NULL,
  amount_cent BIGINT NOT NULL,
  fee_rate DECIMAL(10, 6) NOT NULL,
  merchant_cent BIGINT NOT NULL,
  commission_cent BIGINT NOT NULL,
  channel VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  channel_txn_no VARCHAR(64),
  refunded_cent BIGINT NOT NULL DEFAULT 0,
  idempotency_key VARCHAR(128) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_order_no UNIQUE (order_no),
  CONSTRAINT uk_order_idem UNIQUE (idempotency_key)
);

CREATE TABLE IF NOT EXISTS channel_txn (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  channel_txn_no VARCHAR(64) NOT NULL,
  channel VARCHAR(32) NOT NULL,
  order_no VARCHAR(64) NOT NULL,
  amount_cent BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL,
  notify_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_channel_txn_no UNIQUE (channel_txn_no)
);

CREATE TABLE IF NOT EXISTS channel_bill (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  bill_date VARCHAR(16) NOT NULL,
  channel_txn_no VARCHAR(64) NOT NULL,
  channel VARCHAR(32) NOT NULL,
  amount_cent BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL,
  tampered INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_bill_txn UNIQUE (channel_txn_no)
);

CREATE TABLE IF NOT EXISTS split_detail (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no VARCHAR(64) NOT NULL,
  merchant_no VARCHAR(64) NOT NULL,
  account_no VARCHAR(64) NOT NULL,
  amount_cent BIGINT NOT NULL,
  voucher_no VARCHAR(64) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS refund_order (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  refund_no VARCHAR(64) NOT NULL,
  original_order_no VARCHAR(64) NOT NULL,
  amount_cent BIGINT NOT NULL,
  merchant_cent BIGINT NOT NULL,
  commission_cent BIGINT NOT NULL,
  reversal_voucher_no VARCHAR(64),
  status VARCHAR(16) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_refund_no UNIQUE (refund_no)
);

CREATE TABLE IF NOT EXISTS settlement_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  batch_no VARCHAR(64) NOT NULL,
  merchant_no VARCHAR(64) NOT NULL,
  settle_date VARCHAR(16) NOT NULL,
  gross_cent BIGINT NOT NULL,
  refund_cent BIGINT NOT NULL DEFAULT 0,
  fee_cent BIGINT NOT NULL DEFAULT 0,
  net_cent BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL,
  voucher_no VARCHAR(64),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS recon_diff (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  recon_no VARCHAR(64) NOT NULL,
  recon_date VARCHAR(16) NOT NULL,
  diff_type VARCHAR(32) NOT NULL,
  diff_scope VARCHAR(32) NOT NULL,
  biz_no VARCHAR(64),
  expected_cent BIGINT NOT NULL DEFAULT 0,
  actual_cent BIGINT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  handler VARCHAR(64),
  remark VARCHAR(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS event_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_no VARCHAR(64) NOT NULL,
  topic VARCHAR(32) NOT NULL,
  biz_no VARCHAR(64),
  action VARCHAR(64) NOT NULL,
  result VARCHAR(16) NOT NULL,
  detail VARCHAR(1000),
  trace_no VARCHAR(64),
  status VARCHAR(16) NOT NULL DEFAULT 'DONE',
  retry_count INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS risk_rule (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  rule_code VARCHAR(64) NOT NULL,
  rule_name VARCHAR(128) NOT NULL,
  rule_value VARCHAR(128) NOT NULL,
  enabled INT NOT NULL DEFAULT 1,
  remark VARCHAR(255),
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_rule_code UNIQUE (rule_code)
);

CREATE INDEX IF NOT EXISTS idx_entry_account ON account_entry(account_no, id);
CREATE INDEX IF NOT EXISTS idx_entry_voucher ON account_entry(voucher_no);
CREATE INDEX IF NOT EXISTS idx_event_time ON event_log(id);
CREATE INDEX IF NOT EXISTS idx_order_created ON payment_order(created_at);
CREATE INDEX IF NOT EXISTS idx_bill_date ON channel_bill(bill_date);
CREATE INDEX IF NOT EXISTS idx_diff_status ON recon_diff(status);
CREATE INDEX IF NOT EXISTS idx_settle_date ON settlement_item(settle_date);
