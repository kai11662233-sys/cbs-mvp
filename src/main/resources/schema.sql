-- schema.sql (PostgreSQL) : Freeze仕様・完全版（開発用）
-- ※ CREATE TABLE IF NOT EXISTS で冪等。初期INSERTはON CONFLICTで冪等。

-- 0) app_meta（動作確認用）
CREATE TABLE IF NOT EXISTS app_meta (
  id BIGSERIAL PRIMARY KEY,
  app_name TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO app_meta(app_name)
SELECT 'cbs-mvp'
WHERE NOT EXISTS (SELECT 1 FROM app_meta WHERE app_name = 'cbs-mvp');

-- 1) system_flags
CREATE TABLE IF NOT EXISTS system_flags (
  key VARCHAR(50) PRIMARY KEY,
  value TEXT,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 初期フラグ（dev用。必要に応じて変更）
INSERT INTO system_flags(key, value) VALUES ('OPS_KEY', 'dev-ops-key')
ON CONFLICT (key) DO NOTHING;

INSERT INTO system_flags(key, value) VALUES ('PAUSED', 'false')
ON CONFLICT (key) DO NOTHING;

INSERT INTO system_flags(key, value) VALUES ('PAUSE_REASON', '')
ON CONFLICT (key) DO NOTHING;

-- Pricing/Gate用の初期Params（MVPはsystem_flagsで管理）
INSERT INTO system_flags(key, value) VALUES ('FX_BUFFER', '0.03') ON CONFLICT (key) DO NOTHING;
INSERT INTO system_flags(key, value) VALUES ('DOMESTIC_SHIP', '800') ON CONFLICT (key) DO NOTHING;
INSERT INTO system_flags(key, value) VALUES ('PACKING_MISC', '300') ON CONFLICT (key) DO NOTHING;
INSERT INTO system_flags(key, value) VALUES ('PL_INBOUND', '200') ON CONFLICT (key) DO NOTHING;
INSERT INTO system_flags(key, value) VALUES ('PL_PICKPACK', '500') ON CONFLICT (key) DO NOTHING;
INSERT INTO system_flags(key, value) VALUES ('EBAY_FEE_RATE', '0.15') ON CONFLICT (key) DO NOTHING;
INSERT INTO system_flags(key, value) VALUES ('REFUND_RES_RATE', '0.05') ON CONFLICT (key) DO NOTHING;
INSERT INTO system_flags(key, value) VALUES ('PROFIT_MIN_YEN', '3000') ON CONFLICT (key) DO NOTHING;
INSERT INTO system_flags(key, value) VALUES ('PROFIT_MIN_RATE', '0.20') ON CONFLICT (key) DO NOTHING;
INSERT INTO system_flags(key, value) VALUES ('DEFAULT_WEIGHT_KG', '1.500') ON CONFLICT (key) DO NOTHING;
INSERT INTO system_flags(key, value) VALUES ('DEFAULT_SIZE_TIER', 'XL') ON CONFLICT (key) DO NOTHING;

-- Gate用（必要なら後で更新）
INSERT INTO system_flags(key, value) VALUES ('CURRENT_CASH', '0') ON CONFLICT (key) DO NOTHING;
INSERT INTO system_flags(key, value) VALUES ('CREDIT_LIMIT', '0') ON CONFLICT (key) DO NOTHING;
INSERT INTO system_flags(key, value) VALUES ('CREDIT_USED', '0') ON CONFLICT (key) DO NOTHING;
INSERT INTO system_flags(key, value) VALUES ('UNCONFIRMED_COST', '0') ON CONFLICT (key) DO NOTHING;
INSERT INTO system_flags(key, value) VALUES ('REFUND_FIX_RES', '0') ON CONFLICT (key) DO NOTHING;
INSERT INTO system_flags(key, value) VALUES ('RECENT_SALES_30D', '0') ON CONFLICT (key) DO NOTHING;
INSERT INTO system_flags(key, value) VALUES ('REFUND_RES_RATIO', '0.10') ON CONFLICT (key) DO NOTHING;
INSERT INTO system_flags(key, value) VALUES ('WC_CAP_RATIO', '0.30') ON CONFLICT (key) DO NOTHING;
INSERT INTO system_flags(key, value) VALUES ('EBAY_TRACKING_RETRY_MAX_ATTEMPTS', '5') ON CONFLICT (key) DO NOTHING;
INSERT INTO system_flags(key, value) VALUES ('EBAY_TRACKING_RETRY_MAX_AGE_MINUTES', '60') ON CONFLICT (key) DO NOTHING;
INSERT INTO system_flags(key, value) VALUES ('EBAY_TRACKING_RETRY_BASE_DELAY_SECONDS', '60') ON CONFLICT (key) DO NOTHING;
INSERT INTO system_flags(key, value) VALUES ('EBAY_TRACKING_RETRY_MAX_DELAY_SECONDS', '900') ON CONFLICT (key) DO NOTHING;
INSERT INTO system_flags(key, value) VALUES ('EBAY_TRACKING_RETRY_BATCH_LIMIT', '20') ON CONFLICT (key) DO NOTHING;

-- 2) candidates
CREATE TABLE IF NOT EXISTS candidates (
  candidate_id BIGSERIAL PRIMARY KEY,
  source_url TEXT NOT NULL,
  source_price_yen NUMERIC(12,2) NOT NULL,
  weight_kg NUMERIC(6,3),
  size_tier VARCHAR(5) NOT NULL DEFAULT 'XL' CHECK (size_tier IN ('S','M','L','XL')),
  memo TEXT,
  state VARCHAR(30) NOT NULL CHECK (state IN (
    'CANDIDATE','DRAFT_READY','REJECTED','EBAY_DRAFT_CREATED','EBAY_DRAFT_FAILED','PUBLISHED','DISCARDED','PAUSED'
  )),
  reject_reason_code VARCHAR(50),
  reject_reason_detail TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3) pricing_results（候補1件=最新1件）
CREATE TABLE IF NOT EXISTS pricing_results (
  pricing_id BIGSERIAL PRIMARY KEY,
  candidate_id BIGINT NOT NULL UNIQUE REFERENCES candidates(candidate_id),
  fx_rate NUMERIC(10,4) NOT NULL,
  fx_safe NUMERIC(10,4) NOT NULL,
  sell_price_usd NUMERIC(12,2) NOT NULL,
  sell_price_yen NUMERIC(12,2) NOT NULL,
  total_cost_yen NUMERIC(12,2) NOT NULL,          -- M列相当
  ebay_fee_yen NUMERIC(12,2) NOT NULL,
  refund_reserve_yen NUMERIC(12,2) NOT NULL,
  profit_yen NUMERIC(12,2) NOT NULL,
  profit_rate NUMERIC(6,4) NOT NULL,
  gate_profit_ok BOOLEAN NOT NULL,
  gate_cash_ok BOOLEAN NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE pricing_results ADD COLUMN IF NOT EXISTS calc_source_price_yen NUMERIC(12,2);
ALTER TABLE pricing_results ADD COLUMN IF NOT EXISTS calc_weight_kg NUMERIC(6,3);
ALTER TABLE pricing_results ADD COLUMN IF NOT EXISTS calc_intl_ship_yen NUMERIC(12,2);
ALTER TABLE pricing_results ADD COLUMN IF NOT EXISTS used_fee_rate NUMERIC(6,4);
ALTER TABLE pricing_results ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

ALTER TABLE candidates ADD COLUMN IF NOT EXISTS last_calculated_at TIMESTAMP;

-- 3b) pricing_results_history (履歴保持用)
CREATE TABLE IF NOT EXISTS pricing_results_history (
  history_id BIGSERIAL PRIMARY KEY,
  candidate_id BIGINT NOT NULL, -- FKなしでもよいが、分析用に候補IDは必須
  pricing_id BIGINT,            -- 元のPricingID（あれば）
  fx_rate NUMERIC(10,4),
  sell_price_usd NUMERIC(12,2),
  total_cost_yen NUMERIC(12,2),
  profit_yen NUMERIC(12,2),
  profit_rate NUMERIC(6,4),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_pricing_history_candidate ON pricing_results_history(candidate_id);

-- 4) ebay_drafts
CREATE TABLE IF NOT EXISTS ebay_drafts (
  draft_id BIGSERIAL PRIMARY KEY,
  candidate_id BIGINT NOT NULL REFERENCES candidates(candidate_id),
  sku VARCHAR(64) NOT NULL UNIQUE,
  inventory_item_id VARCHAR(64),
  offer_id VARCHAR(64),
  marketplace VARCHAR(16) NOT NULL DEFAULT 'EBAY_US',
  title_en VARCHAR(200) NOT NULL,
  description_html TEXT NOT NULL,
  list_price_usd NUMERIC(12,2) NOT NULL,
  quantity INT NOT NULL DEFAULT 1,
  state VARCHAR(30) NOT NULL CHECK (state IN ('EBAY_DRAFT_CREATED','EBAY_DRAFT_FAILED')),
  last_error TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 5) orders
CREATE TABLE IF NOT EXISTS orders (
  order_id BIGSERIAL PRIMARY KEY,
  ebay_order_key VARCHAR(64) NOT NULL UNIQUE,
  draft_id BIGINT REFERENCES ebay_drafts(draft_id),
  sold_price_usd NUMERIC(12,2) NOT NULL,
  sold_price_yen NUMERIC(12,2) NOT NULL,
  state VARCHAR(30) NOT NULL CHECK (state IN (
    'SOLD','PROCUREMENT_REQUESTED','PROCUREMENT_SHIPPED_TO_3PL','PROCUREMENT_FAILED',
    '3PL_RECEIVED','3PL_SHIPPED_INTL','EBAY_TRACKING_UPLOADED','DELIVERED','CLAIM','REFUND','PAUSED'
  )),
  tracking_retry_count INT NOT NULL DEFAULT 0,
  tracking_retry_started_at TIMESTAMP,
  tracking_next_retry_at TIMESTAMP,
  tracking_retry_last_error TEXT,
  tracking_retry_terminal_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE orders ADD COLUMN IF NOT EXISTS tracking_retry_count INT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tracking_retry_started_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tracking_next_retry_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tracking_retry_last_error TEXT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tracking_retry_terminal_at TIMESTAMP;

-- 6) purchase_orders（expected_total_cost_yen=M列相当の総コスト見込み）
CREATE TABLE IF NOT EXISTS purchase_orders (
  po_id BIGSERIAL PRIMARY KEY,
  order_id BIGINT NOT NULL REFERENCES orders(order_id),
  supplier_name VARCHAR(100),
  supplier_order_ref VARCHAR(100),
  ship_to_3pl_address TEXT NOT NULL,
  inbound_tracking VARCHAR(64),
  expected_total_cost_yen NUMERIC(12,2) NOT NULL,
  state VARCHAR(30) NOT NULL CHECK (state IN ('REQUESTED','SHIPPED_TO_3PL','PROCUREMENT_FAILED','PAUSED')),
  fail_reason TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 7) fulfillment
CREATE TABLE IF NOT EXISTS fulfillment (
  fulfill_id BIGSERIAL PRIMARY KEY,
  order_id BIGINT NOT NULL REFERENCES orders(order_id),
  inbound_received_at TIMESTAMP,
  outbound_carrier VARCHAR(50),
  outbound_tracking VARCHAR(64),
  state VARCHAR(30) NOT NULL CHECK (state IN ('3PL_RECEIVED','3PL_SHIPPED_INTL')),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 8) cash_ledger
CREATE TABLE IF NOT EXISTS cash_ledger (
  cash_id BIGSERIAL PRIMARY KEY,
  event_type VARCHAR(30) NOT NULL CHECK (event_type IN ('SALE','PROCUREMENT','REFUND','FEE')),
  ref_table VARCHAR(30) NOT NULL,
  ref_id BIGINT NOT NULL,
  amount_yen NUMERIC(12,2) NOT NULL, -- 入金(+) / 出金(-)
  expected_date DATE,
  actual_date DATE, -- NULL=未確定(Commitment), NotNull=確定
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cash_ledger_ref
  ON cash_ledger(ref_table, ref_id, event_type);

-- POに対してPROCUREMENTは1回のみ（二重計上防止）
CREATE UNIQUE INDEX IF NOT EXISTS uq_cash_ledger_po_procurement
  ON cash_ledger(ref_table, ref_id, event_type)
  WHERE ref_table = 'purchase_orders' AND event_type = 'PROCUREMENT';

-- 9) state_transitions
CREATE TABLE IF NOT EXISTS state_transitions (
  log_id BIGSERIAL PRIMARY KEY,
  entity_type VARCHAR(30) NOT NULL, -- 'CANDIDATE','ORDER','PO','SYSTEM'
  entity_id BIGINT NOT NULL,
  from_state VARCHAR(50),
  to_state VARCHAR(50) NOT NULL,
  reason_code VARCHAR(50),
  reason_detail TEXT,
  actor VARCHAR(50) NOT NULL DEFAULT 'SYSTEM', -- 'SYSTEM','USER','BATCH'
  correlation_id VARCHAR(64),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_state_transitions_entity
  ON state_transitions(entity_type, entity_id);

-- 10) fx_rate_history
CREATE TABLE IF NOT EXISTS fx_rate_history (
  history_id BIGSERIAL PRIMARY KEY,
  base_currency VARCHAR(3) NOT NULL,
  target_currency VARCHAR(3) NOT NULL,
  rate NUMERIC(10, 4) NOT NULL,
  source VARCHAR(50) NOT NULL,
  change_percent NUMERIC(6, 2),
  is_anomaly BOOLEAN NOT NULL DEFAULT FALSE,
  fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 11) pricing_rules
CREATE TABLE IF NOT EXISTS pricing_rules (
  rule_id BIGSERIAL PRIMARY KEY,
  condition_type VARCHAR(20) NOT NULL CHECK (condition_type IN ('SOURCE_PRICE', 'WEIGHT')),
  condition_min NUMERIC(12,2),
  condition_max NUMERIC(12,2),
  target_field VARCHAR(20) NOT NULL CHECK (target_field IN ('PROFIT_MIN_YEN', 'PROFIT_MIN_RATE')),
  adjustment_value NUMERIC(12,2) NOT NULL,
  priority INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed Data (Rules)
-- Rule 1: High Price items (> 10000) -> Lower Profit Rate (15%) OK
INSERT INTO pricing_rules (condition_type, condition_min, condition_max, target_field, adjustment_value, priority)
VALUES ('SOURCE_PRICE', 10000, NULL, 'PROFIT_MIN_RATE', 0.15, 10);

-- Rule 2: Low Price items (< 3000) -> Higher Profit Rate (30%) Required
INSERT INTO pricing_rules (condition_type, condition_min, condition_max, target_field, adjustment_value, priority)
VALUES ('SOURCE_PRICE', 0, 3000, 'PROFIT_MIN_RATE', 0.30, 20);

-- 12) discovery_items（Discovery機能用）
CREATE TABLE IF NOT EXISTS discovery_items (
  id BIGSERIAL PRIMARY KEY,
  source_url TEXT NOT NULL,
  source_domain TEXT,
  source_type TEXT DEFAULT 'OTHER',  -- OFFICIAL/RETAIL/MALL/AMAZON/C2C/OTHER
  title TEXT,
  condition TEXT DEFAULT 'UNKNOWN',  -- NEW/USED/UNKNOWN
  category_hint TEXT,
  price_yen NUMERIC(12,2) NOT NULL,
  shipping_yen NUMERIC(12,2),
  weight_kg NUMERIC(6,3),
  safety_score INT NOT NULL DEFAULT 100,
  profit_score INT NOT NULL DEFAULT 0,
  freshness_score INT NOT NULL DEFAULT 0,
  overall_score INT NOT NULL DEFAULT 0,
  risk_flags JSONB NOT NULL DEFAULT '[]'::jsonb,
  safety_breakdown JSONB NOT NULL DEFAULT '[]'::jsonb,
  last_checked_at TIMESTAMPTZ,
  snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
  status TEXT NOT NULL DEFAULT 'NEW',  -- NEW/CHECKED/OK/NG/DRAFTED/ARCHIVED
  linked_candidate_id BIGINT,
  linked_draft_id BIGINT,
  notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_discovery_items_overall_score ON discovery_items(overall_score DESC);
CREATE INDEX IF NOT EXISTS idx_discovery_items_condition ON discovery_items(condition);
CREATE INDEX IF NOT EXISTS idx_discovery_items_status ON discovery_items(status);
CREATE INDEX IF NOT EXISTS idx_discovery_items_last_checked ON discovery_items(last_checked_at);

-- Discovery用の初期閾値設定
INSERT INTO system_flags(key, value) VALUES ('DISCOVERY_MIN_SAFETY', '50') ON CONFLICT (key) DO NOTHING;
INSERT INTO system_flags(key, value) VALUES ('DISCOVERY_FRESHNESS_REQUIRED_HOURS', '24') ON CONFLICT (key) DO NOTHING;
