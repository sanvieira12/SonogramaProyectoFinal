-- Persist the customer's preferred DAC agency as a stable catalog id plus a
-- name/address snapshot. Keep sucursal_dac for backwards compatibility with
-- existing exports and old records.
ALTER TABLE cliente
    ADD COLUMN IF NOT EXISTS dac_branch_id VARCHAR(80),
    ADD COLUMN IF NOT EXISTS dac_branch_name VARCHAR(180),
    ADD COLUMN IF NOT EXISTS dac_branch_address VARCHAR(255);

-- Keep the selected agency snapshot with each shipment as well.
ALTER TABLE envio
    ADD COLUMN IF NOT EXISTS dac_branch_id VARCHAR(80),
    ADD COLUMN IF NOT EXISTS sucursal_dac_direccion VARCHAR(255);
