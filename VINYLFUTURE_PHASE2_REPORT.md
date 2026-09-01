# Vinyl Future Phase 2 Report

## Scope completed

Se completó la fase funcional de Vinyl Future previa a la prueba manual final:

- identidad explícita y conservadora por código de catálogo del proveedor;
- suma de stock físico sobre un producto existente, sin sobrescribir el stock previo;
- sincronización de QR según las copias físicas disponibles;
- idempotencia persistente por operación/factura;
- búsqueda, previsualización e importación manual por enlace;
- resolución manual de líneas de factura pendientes;
- descarga individual de portada y ZIP por producto;
- integración del mismo camino de catálogo/stock en factura PDF, pedido Vinyl Future, importación manual y Excel Vinyl Future.

No se trabajó sobre Discogs.

## Files modified

Archivos modificados o agregados específicamente para Fase 2:

- `frontend/src/api/sonograma.js`
- `frontend/src/api/sonograma.test.js`
- `frontend/src/pages/importar/VinylFutureTab.jsx`
- `frontend/src/pages/importar/VinylFutureTab.test.jsx`
- `sonograma-backend/src/main/java/com/sonograma/controller/ImportController.java`
- `sonograma-backend/src/main/java/com/sonograma/dto/VinylFutureManualConfirmRequestDTO.java`
- `sonograma-backend/src/main/java/com/sonograma/dto/VinylFutureManualImportResultDTO.java`
- `sonograma-backend/src/main/java/com/sonograma/dto/VinylFutureManualPreviewDTO.java`
- `sonograma-backend/src/main/java/com/sonograma/dto/VinylFutureManualSearchRequestDTO.java`
- `sonograma-backend/src/main/java/com/sonograma/dto/VinylFuturePendingItemDTO.java`
- `sonograma-backend/src/main/java/com/sonograma/entity/Disco.java`
- `sonograma-backend/src/main/java/com/sonograma/entity/Pedido.java`
- `sonograma-backend/src/main/java/com/sonograma/repository/DiscoRepository.java`
- `sonograma-backend/src/main/java/com/sonograma/repository/PedidoItemRepository.java`
- `sonograma-backend/src/main/java/com/sonograma/repository/PedidoRepository.java`
- `sonograma-backend/src/main/java/com/sonograma/service/PedidoService.java`
- `sonograma-backend/src/main/java/com/sonograma/service/VinylFutureCatalogStockService.java`
- `sonograma-backend/src/main/java/com/sonograma/service/VinylFutureIdentityNormalizer.java`
- `sonograma-backend/src/main/java/com/sonograma/service/VinylFutureManualImportService.java`
- `sonograma-backend/src/main/java/com/sonograma/service/importacion/VinylFutureImportService.java`
- `sonograma-backend/src/test/java/com/sonograma/controller/ImportControllerTest.java`
- `sonograma-backend/src/test/java/com/sonograma/service/PedidoServiceVinylFutureIdempotencyTest.java`
- `sonograma-backend/src/test/java/com/sonograma/service/VinylFutureCatalogStockServiceTest.java`
- `sonograma-backend/src/test/java/com/sonograma/service/VinylFutureManualImportServiceTest.java`
- `docs/migraciones/043_vinylfuture_identity_and_idempotency.sql`
- `VINYLFUTURE_PHASE2_REPORT.md`

El árbol ya contenía cambios de Fase 1 y del fallback del parser antes de iniciar esta fase. Se conservaron; `PdfInvoiceParser` no fue rediseñado ni modificado como parte de Fase 2.

## Database changes

Se agregó la migración manual `docs/migraciones/043_vinylfuture_identity_and_idempotency.sql`.

Agrega, sin borrar registros:

- `disco.vinylfuture_supplier_code_normalized`, nullable y único cuando tiene valor;
- `pedido.vinylfuture_operation_key`, nullable y único cuando tiene valor;
- índice para elementos `REVIEW_REQUIRED` todavía no resueltos.

La migración rellena identidades únicamente cuando la evidencia es determinística. Los códigos históricos duplicados permanecen en `NULL` y se reportan mediante consultas `SELECT`; no se fusionan. Las facturas históricas duplicadas también se reportan y no se fusionan. Además vincula filas de Fase 1 solamente cuando coinciden a la vez la factura y el código normalizado con un único producto Future, y marca como completadas únicamente las facturas cuyas filas quedaron todas vinculadas.

Antes de producción se debe ejecutar la migración sobre un respaldo o staging y revisar los dos resultados de duplicados que imprime el script.

## Vinyl Future identity strategy

La identidad es el código de catálogo del proveedor Vinyl Future, guardado en forma normalizada. La normalización centralizada aplica NFKC de Unicode, quita espacios externos, compacta espacios repetidos y usa mayúsculas. No elimina puntuación significativa: `GM-05` y `GM05` siguen siendo identidades distintas, igual que `MAO-V001` conserva sus guiones.

No existe fallback por similitud de artista/título. Si hay más de un producto Future histórico con la misma identidad normalizada, la operación se detiene con un mensaje de conflicto y no fusiona datos.

## Stock behavior

`VinylFutureCatalogStockService` separa producto de catálogo de copias físicas:

- identidad nueva: crea un producto y agrega N copias;
- identidad existente: reutiliza el producto y suma N al stock disponible;
- si existen QR persistidos, toma como base las copias QR disponibles;
- si es un registro legado sin inventario QR, toma `cantidadCopias` como base;
- sincroniza el objetivo de QR con el mecanismo existente;
- devuelve resultado estructurado `NEW/EXISTING`, copias agregadas y stock resultante.

El mismo servicio se usa desde factura PDF, pedido Vinyl Future, enlace manual y Excel Vinyl Future.

## Invoice idempotency

La clave persistente `VINYLFUTURE:<número normalizado>` representa la operación de factura, no el producto. Una compra posterior legítima del mismo código puede sumar stock mediante otra factura u otra búsqueda manual.

- factura completada: responde `Esta factura ya fue importada.`;
- factura en ejecución: bloquea doble submit;
- factura fallida: puede reanudarse;
- reintento parcial: omite las filas ya vinculadas al catálogo y procesa las restantes;
- Fase 1 con stock ya creado y estado legado `PARSED`: se detecta y bloquea para no duplicar;
- duplicados históricos de factura: se reportan y requieren revisión, sin fusión automática.

## Manual URL import

La pestaña conserva el flujo existente y muestra dos caminos claros:

- `A. Factura PDF`;
- `B. Importación manual` / `Importar producto por enlace`.

El usuario pega un enlace, pulsa `Buscar`, revisa el producto y confirma una cantidad entera positiva. Los hosts ajenos a `vinylfuture.com`, esquemas inválidos, enlaces con credenciales y URLs sin ruta se rechazan con mensajes en español. El scraper y la descarga de assets existentes se reutilizan.

Cada previsualización recibe un identificador temporal. Confirmar dos veces la misma previsualización devuelve el resultado previo y no duplica stock; una búsqueda futura nueva representa una nueva compra intencional.

## Manual preview behavior

La previsualización muestra, cuando están disponibles:

- código;
- artista y título;
- formato;
- sello;
- año;
- género;
- país;
- portada;
- cantidad de previews de audio;
- estado de metadatos;
- aviso de producto nuevo o existente.

Los datos ausentes aparecen como `Sin información disponible`. No se inventa metadata. Un producto sin código de catálogo confiable no puede confirmarse automáticamente.

## Pending invoice-item recovery

Los `PedidoItem` `REVIEW_REQUIRED` y sin disco vinculado se listan con factura, página, texto original, motivo y cantidad estimada. `Resolver manualmente` abre el flujo por enlace conservando el `pendingItemId` y preseleccionando la cantidad estimada cuando es válida.

Al confirmar:

- se bloquea la fila en base de datos;
- se agrega la cantidad una sola vez mediante el servicio compartido;
- se vincula el `PedidoItem` al `Disco`;
- cambia el estado de lectura a `RESUELTO`;
- se conserva la factura original y no se repiten sus filas ya importadas;
- el pedido pasa a `COMPLETED` solamente cuando no quedan filas sin vincular.

Cancelar en la interfaz no llama al endpoint de confirmación y deja la fila pendiente.

## Individual cover download

`Descargar portada` usa exclusivamente la portada ya descargada por `VinylFutureAssetService`. Si no existe archivo local válido, la UI muestra `Portada no disponible` y no genera placeholders.

## Individual ZIP generation

El ZIP individual usa `CsvExportService` y `ZipBundleService` con un mapa de un solo producto. Conserva el contenido aplicable del flujo existente: `import.csv`, portada local, previews MP3 locales y `missing_media.txt` cuando corresponda.

La confirmación de catálogo/stock y la generación del ZIP son requests separadas. Un error de ZIP muestra `No se pudo generar el archivo ZIP del producto.` y no revierte ni repite el alta de stock.

## Enrichment failure behavior

La identidad se resuelve antes de depender de portada o audio. Una falla de descarga conserva el producto y el stock válidos, reporta la media faltante y no reemplaza media existente con valores vacíos. Los previews de audio existentes tampoco se eliminan cuando la búsqueda nueva no devuelve tracks.

## Spanish UI verification

Todo el texto visible agregado en Fase 2 está en español, incluidos estados de espera, validaciones, avisos de producto existente, resolución, descargas y errores. ESLint pasó sin advertencias ni errores.

## Business rules preserved

No se modificaron:

- Libro de Ventas;
- persistencia de ventas;
- ingresos, estadísticas o dashboard;
- deuda, descuentos o impuestos;
- fórmulas de pricing;
- porcentajes de markup;
- costos fijos;
- reglas LP, 12", 2x12", 3x12", box sets o multidisco.

Los importadores siguen llamando a `CatalogPricingService` exactamente para obtener el precio inicial; ninguna fórmula se movió ni reinterpretó.

## Discogs untouched

No se modificaron servicios, parser, jobs, metadata, normalización de URL ni UI de Discogs. `git diff` no contiene cambios en `DiscogsImportService` ni `DiscogsTab.jsx`. El archivo API compartido solo recibió métodos nuevos bajo el namespace Vinyl Future.

## Parser regression status

El test con el PDF real `0036-188471.pdf` sigue pasando:

- cantidad declarada: 32;
- cantidad física interpretada: 32;
- cantidad pendiente: 0;
- filas/productos fuente: 23;
- filas de revisión: 0;
- `OYSTER80`: 4;
- `RCM101120LP`: 3;
- `TOKO6`: 2.

La advertencia de PDFBox sobre una tabla de fuente truncada continúa apareciendo en logs, pero no afecta la extracción ni el resultado 32/32/0.

## Tests added

Se agregaron pruebas para:

- normalización conservadora y puntuación significativa;
- producto nuevo, producto existente, stock acumulativo y QR;
- inventario QR como base del stock disponible;
- compra posterior legítima;
- identidades distintas y duplicados históricos ambiguos;
- factura completada, doble submit, reanudación fallida y legado de Fase 1;
- URL válida e inválida;
- preview nuevo/existente;
- cantidad manual mayor que uno;
- reintento idempotente de confirmación manual;
- portada disponible/no disponible;
- ZIP individual y falla de ZIP posterior al guardado;
- listado, confirmación y reintento de resolución pendiente;
- interfaz manual y preselección de una fila pendiente;
- cliente API para búsqueda, confirmación, portada y ZIP.

## Test results

- Suite enfocada Vinyl Future (parser, controller, catálogo/stock, manual, idempotencia, recuperación, assets, scraper y ZIP): pasó.
- Frontend completo: 92 tests, 92 pasaron.
- Frontend lint: pasó.
- Frontend build de producción: pasó.
- Backend completo: 278 tests ejecutados, 276 pasaron, 1 omitido y 1 fallo.
- Único fallo backend: `EstadisticasServiceTest.serieMensualConservaCentavosYCoincideConElLibroEnLosIdsYMontosIncluidos`, esperado `36955`, obtenido `30000`. Es el fallo preexistente indicado para esta tarea y no fue modificado.
- `git diff --check`: pasó.

## Known remaining limitations

- La búsqueda manual y sus descargas usan una sesión temporal en memoria de dos horas; después debe repetirse `Buscar`.
- Los ZIP de importación siguen usando el almacenamiento temporal existente.
- Los duplicados históricos ambiguos no se corrigen automáticamente: la migración los reporta y el servicio bloquea una fusión insegura.
- Las pruebas automatizadas mockean Vinyl Future; la disponibilidad real del proveedor, portada y MP3 debe verificarse en la prueba manual final.
- La migración 043 debe probarse sobre un respaldo/staging PostgreSQL y sus reportes de duplicados deben revisarse antes de la prueba productiva. No se pudo ejecutar un PostgreSQL efímero local porque el entorno contiene el cliente `libpq` pero no el binario del servidor.

## Manual end-to-end test procedure

Usar una base staging o una copia respaldada. Aplicar primero `docs/migraciones/043_vinylfuture_identity_and_idempotency.sql` y guardar los resultados de las consultas de duplicados. Si reportan identidades, no fusionarlas: revisarlas manualmente antes de continuar.

1. Abrir `Importar → Vinyl Future` y ubicar `A. Factura PDF`.
2. Cargar el PDF real `0036-188471.pdf`, pulsar `Subir factura PDF` y verificar antes de confirmar: cantidad declarada 32, copias válidas 32, copias pendientes 0 y ninguna fila `REVIEW_REQUIRED`.
3. Confirmar la importación y esperar el estado final. Verificar: 32 copias importadas, 23 productos fuente/procesados y 0 pendientes. Si esa factura ya estaba importada en la base usada, el resultado correcto es `Esta factura ya fue importada.` y no debe cambiar ningún stock; para probar el alta completa usar una copia staging anterior a esa importación.
4. Comparar el catálogo antes/después. Debe existir un solo producto por código Vinyl Future. Comprobar especialmente las cantidades de la factura: `OYSTER80 = 4`, `RCM101120LP = 3` y `TOKO6 = 2`. Si uno ya tenía stock anterior, el resultado debe ser stock anterior más esa cantidad.
5. Abrir esos productos en catálogo y verificar que el número de QR disponibles coincide con el stock disponible resultante. Confirmar que se crearon solamente las copias necesarias y que los QR previos no cambiaron.
6. Revisar una muestra de productos con datos completos: artista, título, formato, sello, año, género, país y costo. Abrir portadas y reproducir al menos un preview de audio local. Registrar por separado cualquier media ausente; no debe existir un segundo producto por reintento de enriquecimiento.
7. Desde el resultado de factura pulsar `Descargar ZIP`. Abrirlo y verificar raíz de factura, `import.csv`, carpetas de producto, portadas y MP3 disponibles, más `missing_media.txt` únicamente donde corresponda.
8. En `B. Importación manual`, elegir un enlace Vinyl Future cuyo código no exista en staging. Pegar el enlace, pulsar `Buscar`, verificar la preview, seleccionar cantidad 2 y pulsar `Agregar al catálogo`. Confirmar un producto nuevo, stock 2 y dos QR disponibles.
9. Repetir `Buscar` con el mismo enlace como una nueva compra intencional. La UI debe indicar `Producto ya existente`. Seleccionar cantidad 1 y pulsar `Agregar copia al stock`. Confirmar el mismo `idDisco`, sin segundo producto, stock 3 y un QR disponible adicional.
10. En la preview, pulsar `Descargar portada`. Verificar una imagen no vacía con la portada mostrada. Para un producto sin portada, verificar texto y botón `Portada no disponible`, sin archivo falso.
11. Pulsar `Descargar ZIP del producto`. Abrirlo y verificar `import.csv` y los assets locales aplicables. Opcionalmente simular una falla temporal del ZIP después de guardar; el stock del paso 9 debe permanecer 3 y un reintento de la misma confirmación no debe sumar otra copia.
12. Para probar recuperación sin tocar producción, crear una base local/staging desde un respaldo y usar una copia de una factura de prueba donde una sola cantidad de producto haya sido reemplazada por un carácter ambiguo, por ejemplo `X`. Validar, confirmar explícitamente la importación parcial y comprobar que la fila aparece en `Elementos pendientes de facturas`.
13. Pulsar `Resolver manualmente` sobre esa fila. Verificar factura, página, texto original y cantidad estimada. Pegar el enlace correcto, buscar, revisar metadata, confirmar la cantidad y comprobar: stock agregado una vez, fila `RESUELTO`, pedido sin pendientes cuando todas las filas están vinculadas y ninguna fila válida anterior repetida. Repetir el mismo submit o refrescar: no debe volver a sumar stock. Cancelar otra resolución de prueba antes de confirmar y comprobar que sigue pendiente.
14. Revisar Libro de Ventas, dashboard, estadísticas, deuda y configuración de pricing antes/después. No debe aparecer ninguna venta nueva ni variar fórmulas, porcentajes, markups o configuración financiera por esta importación; solamente cambian catálogo, stock/QR, pedido y assets de Vinyl Future.
