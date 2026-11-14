ALTER TABLE venta
    DROP CONSTRAINT IF EXISTS venta_canal_venta_check,
    ADD CONSTRAINT venta_canal_venta_check
        CHECK (canal_venta IN ('LOCAL', 'INSTAGRAM', 'WEB', 'ENCARGUE', 'OTROS'));
