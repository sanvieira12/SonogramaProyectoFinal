-- Metadata completa obtenida desde la pagina del proveedor antes de importar al catalogo.
ALTER TABLE pedido_item ADD COLUMN IF NOT EXISTS page_data_json TEXT;
