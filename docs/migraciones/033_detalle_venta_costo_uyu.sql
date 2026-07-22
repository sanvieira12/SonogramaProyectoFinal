-- Keep the original acquisition amount for audit, and add an explicit
-- normalized historical unit cost for profit calculations.
ALTER TABLE detalle_venta
    ADD COLUMN IF NOT EXISTS costo_adquisicion_unitario_uyu NUMERIC(14,6),
    ADD COLUMN IF NOT EXISTS costo_adquisicion_moneda_original VARCHAR(10),
    ADD COLUMN IF NOT EXISTS tipo_cambio_adquisicion NUMERIC(14,8),
    ADD COLUMN IF NOT EXISTS costo_adquisicion_fuente VARCHAR(80);

-- Existing VinylFuture sales may have copied the EUR catalog cost into the
-- old snapshot column. Prefer the immutable landed UYU cost stored on the
-- purchase line. If that is unavailable, use only the purchase's persisted
-- historical exchange rate; never use a live/current rate.
UPDATE detalle_venta dv
SET costo_adquisicion_unitario_uyu = COALESCE(
        pi.costo_real_uyu,
        CASE
            WHEN UPPER(COALESCE(d.costo_moneda,
                    CASE WHEN UPPER(COALESCE(d.procedencia, '')) IN ('FUTURE', 'VINYL_FUTURE', 'VINYLFUTURE')
                         THEN 'EUR' ELSE 'UYU' END)) = 'UYU'
                THEN dv.costo_adquisicion_unitario
            WHEN p.tipo_cambio IS NOT NULL AND p.tipo_cambio > 0
                THEN dv.costo_adquisicion_unitario * p.tipo_cambio
            ELSE NULL
        END
    ),
    costo_adquisicion_moneda_original = UPPER(COALESCE(d.costo_moneda,
        CASE WHEN UPPER(COALESCE(d.procedencia, '')) IN ('FUTURE', 'VINYL_FUTURE', 'VINYLFUTURE')
             THEN 'EUR' ELSE 'UYU' END)),
    tipo_cambio_adquisicion = CASE
        WHEN UPPER(COALESCE(d.costo_moneda,
                CASE WHEN UPPER(COALESCE(d.procedencia, '')) IN ('FUTURE', 'VINYL_FUTURE', 'VINYLFUTURE')
                     THEN 'EUR' ELSE 'UYU' END)) = 'UYU' THEN NULL
        ELSE p.tipo_cambio
    END,
    costo_adquisicion_fuente = CASE
        WHEN pi.costo_real_uyu IS NOT NULL THEN 'HISTORICAL_PURCHASE_LANDED_COST'
        WHEN p.tipo_cambio IS NOT NULL AND p.tipo_cambio > 0 THEN 'HISTORICAL_PURCHASE_CONVERSION'
        WHEN UPPER(COALESCE(d.costo_moneda, 'UYU')) = 'UYU' THEN 'HISTORICAL_CATALOG_UYU'
        ELSE NULL
    END
FROM disco d
LEFT JOIN pedido_item pi ON pi.id_disco = d.id_disco
LEFT JOIN pedido p ON p.id_pedido = pi.id_pedido
WHERE dv.id_disco = d.id_disco
  AND dv.costo_adquisicion_unitario_uyu IS NULL;
