# Vinyl Future Phase 1 Report

## Changes implemented

- Se agregó una validación previa obligatoria para el flujo principal de facturas PDF de Vinyl Future.
- El parser ahora devuelve, además de los productos válidos, un diagnóstico por cada línea candidata: página, texto original, posición lógica, estado, cantidad estimada y motivo de revisión.
- Se agregó reconciliación entre la cantidad oficial de la factura y la suma de cantidades interpretadas.
- Las filas repetidas se conservan como filas fuente y se exponen también como consolidaciones con sus cantidades de origen y total acumulado.
- La interfaz valida antes de modificar catálogo o stock. Las facturas consistentes continúan automáticamente; las problemáticas muestran una revisión previa.
- Ante problemas, el usuario puede cancelar sin cambios o confirmar expresamente una importación parcial de los elementos válidos.
- Las filas no interpretadas se persisten en `pedido_item` y permanecen en el resultado del trabajo de importación.
- Los fallos de búsqueda/enriquecimiento permanecen separados de los errores de lectura del PDF.
- Un fallo al crear el ZIP ya no convierte una importación de catálogo exitosa en un supuesto fallo de lectura: queda informado como estado de ZIP independiente.
- Se limitaron las validaciones temporales a 20 sesiones y 30 minutos para evitar retener PDFs abandonados indefinidamente en memoria.

## Files modified

- `frontend/src/api/sonograma.js`
- `frontend/src/api/sonograma.test.js`
- `frontend/src/pages/importar/VinylFutureTab.jsx`
- `frontend/src/pages/importar/VinylFutureTab.test.jsx`
- `sonograma-backend/src/main/java/com/sonograma/controller/ImportController.java`
- `sonograma-backend/src/main/java/com/sonograma/dto/InvoiceParseResult.java`
- `sonograma-backend/src/main/java/com/sonograma/dto/InvoiceProductConsolidationDTO.java`
- `sonograma-backend/src/main/java/com/sonograma/dto/InvoiceSourceRowDTO.java`
- `sonograma-backend/src/main/java/com/sonograma/dto/PedidoItemResponseDTO.java`
- `sonograma-backend/src/main/java/com/sonograma/dto/VinylFutureImportJobDTO.java`
- `sonograma-backend/src/main/java/com/sonograma/dto/VinylFutureImportSummaryDTO.java`
- `sonograma-backend/src/main/java/com/sonograma/dto/VinylFutureInvoiceValidationDTO.java`
- `sonograma-backend/src/main/java/com/sonograma/entity/PedidoItem.java`
- `sonograma-backend/src/main/java/com/sonograma/service/PdfInvoiceParser.java`
- `sonograma-backend/src/main/java/com/sonograma/service/PedidoService.java`
- `sonograma-backend/src/test/java/com/sonograma/controller/ImportControllerTest.java`
- `sonograma-backend/src/test/java/com/sonograma/service/PdfInvoiceParserTest.java`
- `docs/migraciones/042_vinylfuture_pdf_validation.sql`
- `VINYLFUTURE_PHASE1_REPORT.md`

`IMPORT_SYSTEM_AUDIT.md` ya estaba presente como archivo no versionado y no fue modificado por esta fase.

## Previous behavior

El endpoint principal iniciaba inmediatamente un trabajo asíncrono. `PdfInvoiceParser` devolvía únicamente las líneas que coincidían con sus expresiones regulares; una línea candidata no interpretable podía desaparecer. El `Pedido` y sus productos válidos se persistían antes de cualquier control de cantidad, y luego se ejecutaban enriquecimiento, catálogo, QR, orden de envío y ZIP. La discrepancia entre cantidad oficial y suma interpretada no bloqueaba ni requería confirmación.

## New behavior

El frontend primero llama a `POST /importar/vinylfuture/validar`. El backend conserva temporalmente el PDF y su resultado estructurado sin escribir catálogo, stock ni pedido. Si la factura es consistente, la interfaz confirma automáticamente y conserva el flujo ágil anterior. Si existe una discrepancia, una fila no interpretable o falta el total oficial, se muestra una revisión en español y el backend exige `continuarParcial=true` para iniciar el trabajo.

El endpoint histórico `POST /importar/vinylfuture-catalogo` se conserva, pero solo inicia importaciones consistentes. Una factura problemática recibe un conflicto y debe pasar por la validación previa. El flujo secundario de CSV/ZIP también rechaza facturas problemáticas en lugar de importarlas silenciosamente.

## PDF validation flow

1. PDFBox extrae cada página por separado, conservando el número de página.
2. Se mantienen las expresiones regulares existentes para las líneas que ya funcionaban.
3. Cada línea parseada se registra con estado interno `PARSED` y su `InvoiceItem`.
4. Una línea con estructura de producto y al menos dos importes, pero no parseable, se registra como `REVIEW_REQUIRED` con texto y motivo en español.
5. Se extraen cabecera, cantidad oficial y totales con la lógica existente.
6. Se calculan filas detectadas, filas válidas, filas pendientes y copias válidas.
7. El resultado incluye consolidaciones por identidad exacta actual, con números de fila, cantidades originales y cantidad consolidada.

## Quantity reconciliation

La validación calcula `SUM(InvoiceItem.cantidad)` y la compara con `cantidadTotalPdf`. Una coincidencia requiere además que no existan filas pendientes ni errores de lectura. La diferencia nunca se rellena, redistribuye ni transforma en productos ficticios.

Para filas repetidas, la validación mantiene cada línea fuente. La importación reutiliza `mergeExactRepeatedRows`, que suma cantidades antes de pasar por búsqueda, scraping y catálogo. Por tanto, una secuencia `1 + 2 + 1` produce cuatro copias entrantes sin crear tres productos de catálogo.

## Partial import behavior

La pantalla ofrece dos acciones ante una factura problemática:

- `Cancelar importación`: elimina la validación temporal y no inicia ninguna escritura.
- `Continuar con N copias válidas`: requiere confirmación explícita y envía únicamente los `InvoiceItem` válidos al pipeline existente.

El resumen parcial informa factura, cantidad declarada, copias importadas, copias pendientes, productos nuevos, productos ya existentes, advertencias y estado del ZIP. El estado visible es `Importación completada con elementos pendientes`.

## Pending/review behavior

Al aceptar una importación parcial, `PedidoService` persiste tanto filas válidas como filas pendientes. Los nuevos campos de `pedido_item` son:

- `pagina_fuente`
- `texto_fuente`
- `estado_lectura`
- `motivo_revision`
- `cantidad_estimada`

Los mismos datos quedan en `VinylFutureImportJobDTO.sourceRows`. Los errores de lectura se identifican por `REVIEW_REQUIRED`; una búsqueda o descarga externa fallida no cambia una fila parseada a error de lectura y se informa como `Información adicional pendiente`.

## ZIP behavior

No se modificó `ZipBundleService`. En una importación parcial recibe únicamente el mapa de productos válidos que atravesaron el pipeline existente. Las filas no resueltas no generan carpetas ni archivos ficticios.

Si la construcción inicial falla, el catálogo permanece importado, el resumen indica `FALLIDO` para el ZIP y el lote conserva CSV y datos válidos para que el endpoint de descarga pueda intentar reconstruirlo. El error aparece separado de lectura y reconciliación.

## Existing functionality intentionally preserved

- Búsqueda de Vinyl Future.
- Scraping y validación de coincidencia fuerte.
- Descarga de portadas y MP3.
- Extracción y persistencia de previews de audio y YouTube.
- Cálculo de precios.
- Upsert actual de catálogo y suma de stock existente.
- Sincronización de copias QR.
- Creación de `ShippingOrder`.
- Generación de CSV y contenido ZIP.
- Endpoints existentes.
- Todo el comportamiento de Discogs.

## Tests added

- Diagnóstico de una línea candidata no interpretable con página, texto y motivo.
- Parsing multipágina y conservación de filas repetidas (ampliación de cobertura existente).
- Filas repetidas con cantidades diferentes.
- Reconciliación correcta e incorrecta.
- Cancelación previa sin persistencia ni búsqueda.
- Confirmación explícita de importación parcial.
- Procesamiento exclusivo de productos válidos.
- Conservación de filas pendientes en el resultado.
- Separación entre falta de metadata externa y error de lectura.
- ZIP parcial formado solo con productos procesados.
- Fallo de ZIP separado de una importación de catálogo exitosa.
- Flujo exitoso anterior de catálogo, enriquecimiento, QR y ZIP.
- Pruebas de API y pantalla de revisión en React.

La representación disponible de `0036-188471` usa exclusivamente la información suministrada: OYSTER80 `1 + 2 + 1 = 4`, RCM101120LP `1 + 1 + 1 = 3` y TOKO6 `1 + 1 = 2`. Estas filas conocidas suman 9. El test conserva el total oficial 32 y verifica correctamente una discrepancia de 23; no se inventaron las otras líneas de la factura.

## Test results

- Backend focalizado: `PdfInvoiceParserTest,ImportControllerTest` — 16 tests, 16 aprobados.
- Backend completo: 246 ejecutados, 244 aprobados, 1 omitido, 1 fallo no relacionado en `EstadisticasServiceTest.serieMensualConservaCentavosYCoincideConElLibroEnLosIdsYMontosIncluidos` (`esperado 36955`, `obtenido 30000`). El fallo también se reproduce aislado y no toca archivos modificados en esta fase.
- Frontend completo: 17 archivos, 88 tests, todos aprobados.
- Frontend lint: aprobado.
- Frontend build de producción: aprobado.
- `git diff --check`: aprobado.

## Known remaining issues

- El PDF real `0036-188471` no está en el repositorio ni en los adjuntos. Por eso no es posible afirmar todavía, sin fabricar datos, que sus 32 copias reales son interpretadas por el parser actual.
- Las líneas de producto físicamente partidas en varias líneas de extracción PDF podrían aparecer como más de un fragmento; la nueva reconciliación impedirá el éxito silencioso, pero una unión automática general de líneas queda pendiente de contar con un PDF real que permita implementarla sin regresiones.
- Los trabajos asíncronos y validaciones temporales siguen en memoria; las filas aceptadas, incluidas las pendientes, sí quedan persistidas en `Pedido`/`PedidoItem`.
- La migración SQL se administra manualmente en este proyecto y debe aplicarse antes de desplegar con `ddl-auto=validate`.
- El fallo preexistente de `EstadisticasServiceTest` permanece fuera del alcance de esta fase.

## Items intentionally deferred to Phase 2

- Recuperación manual mediante URL de Vinyl Future.
- Identidad canónica de producto/edición.
- Rediseño general de stock o deduplicación concurrente.
- Persistencia durable de la cola y estado completo de trabajos asíncronos.
- Cambios en Discogs.
- Reescritura de scraping, búsqueda, pricing o ZIP.

## Manual verification procedure

Antes de probar en producción, aplicar `docs/migraciones/042_vinylfuture_pdf_validation.sql` y desplegar backend y frontend juntos.

1. Abrir Sonograma e ir a `Importar` → `Vinyl Future`.
2. Seleccionar el PDF real de la factura `0036-188471` y pulsar `Subir factura PDF`.
3. Durante la primera etapa debe verse `Validando factura…`. En este momento todavía no debe existir ningún cambio de catálogo o stock.
4. Si el PDF completo es interpretado correctamente, la importación continuará automáticamente. Al finalizar se debe ver:
   - Factura: `0036-188471`.
   - Cantidad declarada: `32`.
   - Copias importadas: `32`.
   - Copias pendientes: `0`.
   - Estado: factura validada e importada correctamente.
   - ZIP: disponible.
5. Consultar la respuesta de validación en las herramientas de red del navegador si se desea auditar las consolidaciones. Debe mostrar:
   - OYSTER80: filas con cantidades `1, 2, 1`; total `4`.
   - RCM101120LP: tres filas de cantidad `1`; total `3`.
   - TOKO6: dos filas de cantidad `1`; total `2`.
6. Verificar en catálogo que cada código corresponda a un producto y que el aumento de copias coincida con el total consolidado; revisar también los QR generados.
7. Descargar el ZIP y verificar que corresponda a los productos procesados y contenga el CSV/media esperados.
8. Si aparece una revisión en vez de continuar automáticamente, confirmar que muestre `Cantidad declarada: 32`, la cantidad válida realmente interpretada, la diferencia pendiente y cada línea problemática con página, texto y motivo.
9. Primero pulsar `Cancelar importación` y comprobar que no cambien catálogo ni stock.
10. Volver a subir el PDF y, solo para probar el flujo parcial, pulsar `Continuar con N copias válidas`. El resumen final debe decir `Importación completada con elementos pendientes`, mantener visibles las filas pendientes y generar el ZIP únicamente con la parte procesada.

El criterio de aceptación final para el PDF real es que la validación muestre 32 copias declaradas, 32 interpretadas y 0 pendientes, además de las consolidaciones 4/3/2 indicadas. Si no ocurre, no debe considerarse validado: la nueva pantalla debe detener el flujo y exponer exactamente dónde está la diferencia.
