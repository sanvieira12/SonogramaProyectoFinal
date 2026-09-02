\set ON_ERROR_STOP on

BEGIN;

DO $$
DECLARE
    target_count integer;
    target_qr_count integer;
    target_sale_count integer;
    target_debt_count integer;
BEGIN
    SELECT COUNT(*) INTO target_count
      FROM disco
     WHERE id_disco = 1210
       AND codigo_interno = 'MXLP4300'
       AND artista = 'Various'
       AND album = 'PACHA IBIZA CLASSICS LP 3x12"'
       AND condicion = 'NUEVO'
       AND estado = 'VENDIDO'
       AND cantidad_copias = 0
       AND numero_factura_compra = '0036-188471'
       AND fecha_ingreso = TIMESTAMP '2026-08-31 14:53:34.164182'
       AND catalog_deleted_at = TIMESTAMP '2026-09-01 22:52:21.571016';

    SELECT COUNT(*) INTO target_qr_count
      FROM disco_qr_copy
     WHERE id = 15791
       AND id_disco = 1210
       AND copy_number = 1
       AND estado = 'VENDIDO';

    SELECT COUNT(*) INTO target_sale_count
      FROM detalle_venta dv
      JOIN venta v ON v.id_venta = dv.id_venta
     WHERE dv.id_detalle = 280
       AND dv.id_venta = 111
       AND dv.id_disco = 1210
       AND dv.cantidad = 1
       AND dv.copy_ids_snapshot = '15791'
       AND v.estado = 'COMPLETADA';

    SELECT COUNT(*) INTO target_debt_count
      FROM deuda
     WHERE id_deuda = 120
       AND id_venta = 111
       AND estado_pago = 'PARCIAL'
       AND monto_total = 2800.00
       AND monto_pagado = 2500.00
       AND monto_pendiente = 300.00
       AND activa = true;

    IF target_count <> 1 OR target_qr_count <> 1 OR target_sale_count <> 1 OR target_debt_count <> 1 THEN
        RAISE EXCEPTION 'MXLP4300 restoration preconditions failed (target %, qr %, sale %, debt %)',
            target_count, target_qr_count, target_sale_count, target_debt_count;
    END IF;

    IF (SELECT COUNT(*) FROM pedido WHERE id_pedido = 9
            AND numero_factura = '0036-188471'
            AND vinylfuture_operation_key = 'VINYLFUTURE:0036-188471'
            AND import_status = 'COMPLETED'
            AND cantidad_total_pdf = 32) <> 1
       OR (SELECT COUNT(*) FROM pedido_item WHERE id_pedido = 9) <> 23
       OR (SELECT COUNT(*) FROM pedido_item WHERE id_pedido = 9 AND id_disco IS NOT NULL) <> 23
       OR (SELECT COALESCE(SUM(cantidad), 0) FROM pedido_item WHERE id_pedido = 9) <> 32 THEN
        RAISE EXCEPTION 'Pedido 9 no longer matches the verified 0036 import';
    END IF;
END $$;

UPDATE disco
   SET catalog_deleted_at = NULL,
       catalog_deleted_by = NULL,
       fecha_actualizacion = (
           SELECT MAX(COALESCE(other.fecha_actualizacion, other.fecha_ingreso))
             FROM pedido_item pi
             JOIN disco other ON other.id_disco = pi.id_disco
            WHERE pi.id_pedido = 9
              AND other.id_disco <> 1210
       )
 WHERE id_disco = 1210
   AND catalog_deleted_at IS NOT NULL;

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM disco WHERE id_disco = 1210
            AND catalog_deleted_at IS NULL
            AND catalog_deleted_by IS NULL
            AND condicion = 'NUEVO'
            AND estado = 'VENDIDO'
            AND cantidad_copias = 0) <> 1
       OR (SELECT COUNT(*) FROM disco_qr_copy WHERE id_disco = 1210) <> 1
       OR (SELECT COUNT(*) FROM disco_qr_copy WHERE id = 15791 AND id_disco = 1210 AND estado = 'VENDIDO') <> 1
       OR (SELECT COUNT(*) FROM disco_qr_copy WHERE id_disco = 1210 AND estado = 'DISPONIBLE') <> 0 THEN
        RAISE EXCEPTION 'MXLP4300 restoration postconditions failed';
    END IF;
END $$;

COMMIT;
