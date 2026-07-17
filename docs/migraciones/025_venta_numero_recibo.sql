-- Receipt number entered manually in the New Sale workflow.
ALTER TABLE venta ADD COLUMN IF NOT EXISTS numero_recibo VARCHAR(255);
