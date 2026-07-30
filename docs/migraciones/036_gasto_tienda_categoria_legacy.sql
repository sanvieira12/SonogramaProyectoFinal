-- Normaliza las categorías de gastos creadas con nombres anteriores.
-- Es idempotente y conserva exactamente los mismos registros e importes.
UPDATE gasto_tienda
SET categoria = 'STORE_EXPENSES'
WHERE categoria IS NOT NULL
  AND LOWER(BTRIM(categoria)) IN (
      'gastos del local',
      'gasto local',
      'gastos de tienda'
  );

