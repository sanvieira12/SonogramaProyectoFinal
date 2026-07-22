-- Keep debt rows as sale/manual movements and make their balance source-derived.
-- This migration is idempotent and deliberately does not merge or delete rows.

ALTER TABLE deuda
    ADD COLUMN IF NOT EXISTS monto_pagado_inicial NUMERIC(10,2) NOT NULL DEFAULT 0;

-- Recover the original payment from the existing cached total. For sales,
-- venta.monto_pagado contains the initial payment plus later debt payments;
-- manual debts used deuda.monto_pagado in the same way.
UPDATE deuda d
SET monto_pagado_inicial = GREATEST(
    0,
    LEAST(
        COALESCE(CASE WHEN d.id_venta IS NOT NULL THEN v.monto_pagado ELSE d.monto_pagado END, 0)
        - COALESCE((
            SELECT SUM(p.monto)
            FROM pago_deuda p
            WHERE p.id_deuda = d.id_deuda
              AND p.monto > 0
        ), 0),
        COALESCE(d.monto_total, 0)
    )
)
FROM venta v
WHERE d.id_venta = v.id_venta;

UPDATE deuda d
SET monto_pagado_inicial = GREATEST(
    0,
    LEAST(
        COALESCE(d.monto_pagado, 0)
        - COALESCE((
            SELECT SUM(p.monto)
            FROM pago_deuda p
            WHERE p.id_deuda = d.id_deuda
              AND p.monto > 0
        ), 0),
        COALESCE(d.monto_total, 0)
    )
)
WHERE d.id_venta IS NULL;

-- Normalize cached columns for compatibility with existing reports and APIs.
WITH pagos AS (
    SELECT d.id_deuda,
           LEAST(
               COALESCE(d.monto_total, 0),
               COALESCE(d.monto_pagado_inicial, 0) + COALESCE(SUM(p.monto) FILTER (WHERE p.monto > 0), 0)
           ) AS pagado
    FROM deuda d
    LEFT JOIN pago_deuda p ON p.id_deuda = d.id_deuda
    GROUP BY d.id_deuda, d.monto_total, d.monto_pagado_inicial
)
UPDATE deuda d
SET monto_pagado = pagos.pagado,
    monto_pendiente = GREATEST(COALESCE(d.monto_total, 0) - pagos.pagado, 0),
    estado_pago = CASE
        WHEN pagos.pagado >= COALESCE(d.monto_total, 0) THEN 'PAGADO'
        WHEN pagos.pagado > 0 THEN 'PARCIAL'
        ELSE 'PENDIENTE'
    END,
    updated_at = COALESCE(d.updated_at, NOW())
FROM pagos
WHERE d.id_deuda = pagos.id_deuda;

CREATE INDEX IF NOT EXISTS idx_deuda_cliente_movimientos
    ON deuda (id_cliente, activa, estado_pago, fecha_deuda);
