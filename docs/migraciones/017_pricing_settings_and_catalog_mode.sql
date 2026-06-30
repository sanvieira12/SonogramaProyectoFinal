CREATE TABLE IF NOT EXISTS pricing_settings (
    id BIGINT PRIMARY KEY,
    eur_uyu_rate NUMERIC(10,4) NOT NULL,
    extra_cost_single_eur NUMERIC(10,2) NOT NULL,
    extra_cost_double_eur NUMERIC(10,2) NOT NULL,
    extra_cost_multi_eur NUMERIC(10,2) NOT NULL,
    markup_single NUMERIC(10,4) NOT NULL,
    markup_double NUMERIC(10,4) NOT NULL,
    markup_multi NUMERIC(10,4) NOT NULL,
    rounding_rule VARCHAR(32) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO pricing_settings (
    id,
    eur_uyu_rate,
    extra_cost_single_eur,
    extra_cost_double_eur,
    extra_cost_multi_eur,
    markup_single,
    markup_double,
    markup_multi,
    rounding_rule,
    updated_at
)
SELECT
    1,
    49.5,
    5,
    8,
    9,
    1.7,
    1.5,
    1.4,
    'NEAREST_10',
    NOW()
WHERE NOT EXISTS (SELECT 1 FROM pricing_settings WHERE id = 1);

ALTER TABLE disco
    ADD COLUMN IF NOT EXISTS formato VARCHAR(120),
    ADD COLUMN IF NOT EXISTS pricing_mode VARCHAR(20) NOT NULL DEFAULT 'AUTO';

UPDATE disco
SET pricing_mode = 'AUTO'
WHERE pricing_mode IS NULL OR TRIM(pricing_mode) = '';
