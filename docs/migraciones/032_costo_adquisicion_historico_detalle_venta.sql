-- Preserve the acquisition cost used for each sold line independently from
-- the mutable catalog record.
ALTER TABLE detalle_venta
    ADD COLUMN IF NOT EXISTS costo_adquisicion_unitario NUMERIC(14,6);

-- A single historical detail can be recovered without guessing: the existing
-- sale-level cost is the aggregate cost captured at sale time. Do not backfill
-- multi-item sales because their per-line allocation is not historically known.
UPDATE detalle_venta dv
SET costo_adquisicion_unitario = v.costo_disco / NULLIF(dv.cantidad, 0)
FROM venta v
WHERE dv.id_venta = v.id_venta
  AND dv.id_disco IS NOT NULL
  AND dv.cantidad > 0
  AND v.costo_disco IS NOT NULL
  AND v.costo_disco > 0
  AND (
      SELECT COUNT(*)
      FROM detalle_venta same_sale
      WHERE same_sale.id_venta = dv.id_venta
  ) = 1
  AND dv.costo_adquisicion_unitario IS NULL;
