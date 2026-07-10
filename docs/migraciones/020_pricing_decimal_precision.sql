ALTER TABLE pricing_settings
    ALTER COLUMN eur_uyu_rate TYPE NUMERIC(14,8),
    ALTER COLUMN extra_cost_single_eur TYPE NUMERIC(14,6),
    ALTER COLUMN extra_cost_double_eur TYPE NUMERIC(14,6),
    ALTER COLUMN extra_cost_multi_eur TYPE NUMERIC(14,6),
    ALTER COLUMN markup_single TYPE NUMERIC(14,8),
    ALTER COLUMN markup_double TYPE NUMERIC(14,8),
    ALTER COLUMN markup_multi TYPE NUMERIC(14,8);

ALTER TABLE disco
    ALTER COLUMN costo TYPE NUMERIC(14,6),
    ALTER COLUMN precio_venta TYPE NUMERIC(14,6),
    ALTER COLUMN manual_markup TYPE NUMERIC(14,8);

ALTER TABLE pedido
    ALTER COLUMN franqueo TYPE NUMERIC(14,6),
    ALTER COLUMN tarifas TYPE NUMERIC(14,6),
    ALTER COLUMN tipo_cambio TYPE NUMERIC(14,8),
    ALTER COLUMN extra_costo_simple TYPE NUMERIC(14,6),
    ALTER COLUMN extra_costo_doble TYPE NUMERIC(14,6),
    ALTER COLUMN markup_simple TYPE NUMERIC(14,8),
    ALTER COLUMN markup_doble TYPE NUMERIC(14,8);

ALTER TABLE pedido_item
    ALTER COLUMN precio_unitario_eur TYPE NUMERIC(14,6),
    ALTER COLUMN total_linea_eur TYPE NUMERIC(14,6),
    ALTER COLUMN extra_costo_eur TYPE NUMERIC(14,6),
    ALTER COLUMN costo_real_eur TYPE NUMERIC(14,6),
    ALTER COLUMN costo_real_uyu TYPE NUMERIC(14,6),
    ALTER COLUMN markup TYPE NUMERIC(14,8),
    ALTER COLUMN precio_final_uyu TYPE NUMERIC(14,6);

UPDATE pricing_settings
SET rounding_rule = 'NONE'
WHERE rounding_rule IS DISTINCT FROM 'NONE';
