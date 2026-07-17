-- Align parent catalog status with the persisted QR-copy inventory.
-- Sold copies must remain distinguishable from a catalog item that was never stocked.
UPDATE disco d
SET cantidad_copias = (
        SELECT COUNT(*)
        FROM disco_qr_copy c
        WHERE c.id_disco = d.id_disco
          AND c.estado = 'DISPONIBLE'
    ),
    estado = CASE
        WHEN EXISTS (
            SELECT 1 FROM disco_qr_copy c
            WHERE c.id_disco = d.id_disco
              AND c.estado = 'DISPONIBLE'
        ) THEN CASE WHEN d.estado = 'RESERVADO' THEN 'RESERVADO' ELSE 'DISPONIBLE' END
        WHEN EXISTS (
            SELECT 1 FROM disco_qr_copy c
            WHERE c.id_disco = d.id_disco
              AND c.estado = 'VENDIDO'
        ) THEN 'VENDIDO'
        ELSE 'SIN_STOCK'
    END
WHERE EXISTS (
    SELECT 1 FROM disco_qr_copy c WHERE c.id_disco = d.id_disco
);
