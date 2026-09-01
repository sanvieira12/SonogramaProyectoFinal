import { useCallback, useEffect, useRef, useState } from 'react'
import { api, resolveApiUrl } from '../../api/sonograma'
import { downloadBlob } from '../../utils/downloadBlob'

function Spinner({ text }) {
  return (
    <div className="flex items-center justify-center gap-3 py-4">
      <svg className="animate-spin w-5 h-5 text-[#7E9FA8]" viewBox="0 0 24 24" fill="none">
        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
      </svg>
      <span className="text-slate-500 dark:text-stone-400 text-sm">{text}</span>
    </div>
  )
}

function UploadZone({ onFile, accept, label }) {
  const [dragging, setDragging] = useState(false)
  const [archivo, setArchivo] = useState(null)
  const inputRef = useRef(null)

  function handleFile(file) {
    if (!file) return
    setArchivo(file)
    onFile(file)
  }

  return (
    <div
      className={`relative rounded-2xl border-2 border-dashed transition-colors cursor-pointer
        ${dragging ? 'border-[#7E9FA8] bg-[#7E9FA8]/5' :
          archivo ? 'border-[#7E9FA8]/50 bg-[#7E9FA8]/5' :
          'border-slate-200 dark:border-stone-700 hover:border-[#7E9FA8]/50 hover:bg-slate-50 dark:hover:bg-stone-900/50'}`}
      onClick={() => inputRef.current?.click()}
      onDragOver={e => { e.preventDefault(); setDragging(true) }}
      onDragLeave={() => setDragging(false)}
      onDrop={e => { e.preventDefault(); setDragging(false); handleFile(e.dataTransfer.files[0]) }}
    >
      <input ref={inputRef} type="file" accept={accept} className="hidden"
        onChange={e => handleFile(e.target.files[0])} />
      <div className="flex flex-col items-center justify-center gap-3 py-10 px-6 text-center pointer-events-none">
        {archivo ? (
          <>
            <span className="text-[#5C7D87] dark:text-[#7E9FA8] font-medium text-sm">{archivo.name}</span>
            <span className="text-xs text-slate-400 dark:text-stone-500">{(archivo.size / 1024).toFixed(0)} KB · clic para cambiar</span>
          </>
        ) : (
          <>
            <svg className="w-9 h-9 text-slate-300 dark:text-stone-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 16.5V9.75m0 0 3 3m-3-3-3 3M6.75 19.5a4.5 4.5 0 0 1-1.41-8.775 5.25 5.25 0 0 1 10.233-2.33 3 3 0 0 1 3.758 3.848A3.752 3.752 0 0 1 18 19.5H6.75Z" />
            </svg>
            <div>
              <p className="font-medium text-slate-700 dark:text-stone-300 text-sm">{label}</p>
              <p className="text-xs text-slate-400 dark:text-stone-500 mt-1">{accept.replace(/\./g, '').toUpperCase()}</p>
            </div>
          </>
        )}
      </div>
    </div>
  )
}

// ── Sub-section A: Excel import → preview → confirm ──────────────────────────

// Conservado para reactivar la previsualización Excel sin perder el flujo existente.
// eslint-disable-next-line no-unused-vars
function ExcelImport() {
  const [archivo, setArchivo] = useState(null)
  const [estado, setEstado] = useState('idle') // idle | loading | preview | saving | done | error
  const [previews, setPreviews] = useState([])
  const [seleccionados, setSeleccionados] = useState(new Set())
  const [errorMsg, setErrorMsg] = useState('')
  const [resultado, setResultado] = useState(null)

  async function parsear() {
    if (!archivo) return
    setEstado('loading')
    setErrorMsg('')
    try {
      const data = await api.importaciones.vinylfuturePreview(archivo)
      setPreviews(data)
      setSeleccionados(new Set(data.map((_, i) => i)))
      setEstado('preview')
    } catch (err) {
      setErrorMsg(err.message || 'Error al parsear el archivo')
      setEstado('error')
    }
  }

  async function confirmar() {
    const lista = previews.filter((_, i) => seleccionados.has(i))
    setEstado('saving')
    try {
      const data = await api.importaciones.vinylfutureConfirmar(lista)
      setResultado(data)
      setEstado('done')
    } catch (err) {
      setErrorMsg(err.message || 'Error al guardar')
      setEstado('error')
    }
  }

  function toggleRow(i) {
    setSeleccionados(prev => {
      const next = new Set(prev)
      next.has(i) ? next.delete(i) : next.add(i)
      return next
    })
  }

  function reset() {
    setArchivo(null); setPreviews([]); setSeleccionados(new Set())
    setEstado('idle'); setErrorMsg(''); setResultado(null)
  }

  return (
    <div className="space-y-4">
      <div>
        <h3 className="font-semibold text-slate-800 dark:text-stone-200 text-sm mb-1">Importar desde Excel</h3>
        <p className="text-xs text-slate-500 dark:text-stone-400">
          Subí un Excel con columnas: Artista, Álbum, Año, Precio, Condición, Género, Sello, Catálogo.
        </p>
      </div>

      {estado === 'idle' && (
        <>
          <UploadZone
            accept=".xlsx,.xls"
            label="Arrastrá el Excel aquí o hacé clic"
            onFile={setArchivo}
          />
          <button
            onClick={parsear}
            disabled={!archivo}
            className="px-5 py-2.5 rounded-lg bg-[#5C7D87] hover:bg-[#4a6a74] disabled:opacity-40 disabled:cursor-not-allowed text-white text-sm font-medium transition-colors"
          >
            Previsualizar
          </button>
        </>
      )}

      {estado === 'loading' && <Spinner text="Parseando el archivo Excel…" />}

      {estado === 'preview' && previews.length > 0 && (
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <p className="text-sm text-slate-600 dark:text-stone-400">
              {previews.length} filas encontradas — {seleccionados.size} seleccionadas
            </p>
            <div className="flex gap-2">
              <button onClick={() => setSeleccionados(new Set(previews.map((_, i) => i)))}
                className="text-xs text-[#5C7D87] dark:text-[#7E9FA8] hover:underline">Seleccionar todo</button>
              <span className="text-slate-300 dark:text-stone-700">|</span>
              <button onClick={() => setSeleccionados(new Set())}
                className="text-xs text-slate-500 dark:text-stone-400 hover:underline">Deseleccionar todo</button>
            </div>
          </div>

          <div className="overflow-x-auto rounded-xl border border-slate-200 dark:border-stone-800">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b border-slate-200 dark:border-stone-800 bg-slate-50 dark:bg-stone-950">
                  <th className="w-8 px-3 py-2"></th>
                  {['Artista', 'Álbum', 'Año', 'Sello', 'Condición', 'Precio', 'Estado'].map(h => (
                    <th key={h} className="text-left px-3 py-2 font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider">{h}</th>
                  ))}
                  <th className="px-3 py-2"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-stone-800">
                {previews.map((p, i) => (
                  <tr key={i} className={`transition-colors ${seleccionados.has(i) ? '' : 'opacity-40'}`}>
                    <td className="px-3 py-2">
                      <input type="checkbox" checked={seleccionados.has(i)}
                        onChange={() => toggleRow(i)}
                        className="rounded border-slate-300 dark:border-stone-600 text-[#5C7D87]" />
                    </td>
                    <td className="px-3 py-2 font-medium text-slate-800 dark:text-stone-200">{p.artista || <span className="text-red-400">—</span>}</td>
                    <td className="px-3 py-2 text-slate-600 dark:text-stone-400">{p.album || <span className="text-red-400">—</span>}</td>
                    <td className="px-3 py-2 text-slate-500 dark:text-stone-500">{p.anio || '—'}</td>
                    <td className="px-3 py-2 text-slate-500 dark:text-stone-500 max-w-[100px] truncate">{p.sello || '—'}</td>
                    <td className="px-3 py-2 text-slate-500 dark:text-stone-500">{p.condicion || '—'}</td>
                    <td className="px-3 py-2 text-slate-700 dark:text-stone-300 tabular-nums">
                      {p.precioVenta ? `$${Number(p.precioVenta).toLocaleString('es-AR')}` : '—'}
                    </td>
                    <td className="px-3 py-2 text-slate-500 dark:text-stone-500">{p.estado || 'DISPONIBLE'}</td>
                    <td className="px-3 py-2">
                      {p.errores?.length > 0 && (
                        <span className="text-red-500 text-xs" title={p.errores.join(', ')}>⚠ {p.errores.length}</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="flex gap-3">
            <button
              onClick={confirmar}
              disabled={seleccionados.size === 0}
              className="px-5 py-2.5 rounded-lg bg-[#5C7D87] hover:bg-[#4a6a74] disabled:opacity-40 disabled:cursor-not-allowed text-white text-sm font-medium transition-colors"
            >
              Confirmar importación ({seleccionados.size})
            </button>
            <button onClick={reset}
              className="px-5 py-2.5 rounded-lg border border-slate-200 dark:border-stone-700 text-slate-600 dark:text-stone-300 text-sm font-medium hover:bg-slate-50 dark:hover:bg-stone-900 transition-colors">
              Cancelar
            </button>
          </div>
        </div>
      )}

      {estado === 'saving' && <Spinner text="Guardando discos en el catálogo…" />}

      {estado === 'done' && resultado && (
        <div className="p-4 rounded-xl bg-emerald-50 dark:bg-emerald-900/20 border border-emerald-200 dark:border-emerald-800">
          <p className="font-medium text-emerald-700 dark:text-emerald-400 text-sm">
            ✓ {resultado.length} discos importados correctamente al catálogo
          </p>
          <button onClick={reset} className="mt-2 text-xs text-emerald-600 dark:text-emerald-400 underline">
            Nueva importación
          </button>
        </div>
      )}

      {estado === 'error' && (
        <div className="p-4 rounded-xl bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800">
          <p className="text-sm text-red-700 dark:text-red-400 font-medium">Error</p>
          {errorMsg && <p className="text-xs text-red-600 dark:text-red-300 mt-1">{errorMsg}</p>}
          <button onClick={reset} className="mt-2 text-xs underline text-red-600 dark:text-red-400">Reintentar</button>
        </div>
      )}
    </div>
  )
}

function dato(value) {
  return value == null || value === '' ? 'Sin información disponible' : value
}

function ManualUrlImport({ pendingItem, onResolved, onCancelPending }) {
  const [url, setUrl] = useState('')
  const [estado, setEstado] = useState('idle')
  const [preview, setPreview] = useState(null)
  const [cantidad, setCantidad] = useState(() => pendingItem?.estimatedQuantity > 0 ? pendingItem.estimatedQuantity : 1)
  const [resultado, setResultado] = useState(null)
  const [errorMsg, setErrorMsg] = useState('')
  const [descargando, setDescargando] = useState('')

  async function buscar() {
    if (!url.trim()) return
    setEstado('searching')
    setErrorMsg('')
    try {
      const data = await api.importar.vinylfutureManualBuscar(url.trim(), pendingItem?.pendingItemId)
      setPreview(data)
      setCantidad(data.suggestedQuantity > 0 ? data.suggestedQuantity : 1)
      setEstado('preview')
    } catch (err) {
      setErrorMsg(err.message || 'No se pudo buscar el producto')
      setEstado('error')
    }
  }

  async function confirmar() {
    if (!preview || !Number.isInteger(Number(cantidad)) || Number(cantidad) < 1) {
      setErrorMsg('La cantidad debe ser un número entero mayor que cero.')
      return
    }
    setEstado('saving')
    setErrorMsg('')
    try {
      const data = await api.importar.vinylfutureManualConfirmar(preview.previewId, Number(cantidad))
      setResultado(data)
      setEstado('done')
      if (data.pendingItemResolved) onResolved?.()
    } catch (err) {
      setErrorMsg(err.message || 'No se pudo agregar el producto al catálogo')
      setEstado('preview')
    }
  }

  async function descargarPortada() {
    setDescargando('cover')
    setErrorMsg('')
    try {
      const data = await api.importar.vinylfuturePortada(preview.previewId)
      downloadBlob(data.blob, `${preview.catalogueCode || 'vinylfuture'}-portada.jpg`, data.contentDisposition)
    } catch (err) {
      setErrorMsg(err.message || 'Portada no disponible')
    } finally {
      setDescargando('')
    }
  }

  async function descargarZip() {
    setDescargando('zip')
    setErrorMsg('')
    try {
      const data = await api.importar.vinylfutureProductoZip(preview.previewId)
      downloadBlob(data.blob, data.filename, data.contentDisposition)
    } catch (err) {
      setErrorMsg(err.message || 'No se pudo generar el archivo ZIP del producto.')
    } finally {
      setDescargando('')
    }
  }

  function reiniciar() {
    setUrl('')
    setPreview(null)
    setResultado(null)
    setErrorMsg('')
    setEstado('idle')
    setCantidad(1)
    onCancelPending?.()
  }

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 dark:border-stone-800 dark:bg-stone-950/40 space-y-4">
      <div>
        <p className="text-[10px] font-semibold uppercase tracking-[0.18em] text-[#5C7D87] dark:text-[#7E9FA8]">B. Importación manual</p>
        <h3 className="mt-1 font-semibold text-slate-800 dark:text-stone-200">Importar producto por enlace</h3>
        <p className="mt-1 text-xs text-slate-500 dark:text-stone-400">
          Pegá un enlace de producto de Vinyl Future, revisá la información y elegí cuántas copias agregar.
        </p>
      </div>

      {pendingItem && (
        <div className="rounded-xl border border-amber-300 bg-amber-50 p-3 text-xs text-amber-800 dark:border-amber-800 dark:bg-amber-950/20 dark:text-amber-300">
          <p className="font-semibold">Resolviendo un elemento de la factura {pendingItem.invoiceNumber || 'sin número'}</p>
          <p className="mt-1">Página {pendingItem.pageNumber || 'sin información'}: {pendingItem.sourceText}</p>
          <button type="button" onClick={reiniciar} className="mt-2 underline">Cancelar resolución</button>
        </div>
      )}

      <div className="flex flex-col gap-3 sm:flex-row">
        <label className="flex-1 text-xs font-medium text-slate-600 dark:text-stone-300">
          Enlace de Vinyl Future
          <input
            type="url"
            value={url}
            onChange={event => setUrl(event.target.value)}
            placeholder="https://www.vinylfuture.com/..."
            className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-800 outline-none focus:border-[#7E9FA8] dark:border-stone-700 dark:bg-stone-900 dark:text-stone-100"
          />
        </label>
        <button type="button" onClick={buscar} disabled={!url.trim() || estado === 'searching'}
          className="self-end rounded-lg bg-[#5C7D87] px-5 py-2.5 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-40">
          {estado === 'searching' ? 'Buscando información…' : 'Buscar'}
        </button>
      </div>

      {estado === 'searching' && <Spinner text="Buscando información del producto…" />}

      {preview && (
        <div className="rounded-xl border border-slate-200 p-4 dark:border-stone-800 space-y-4">
          <div className="flex flex-col gap-4 sm:flex-row">
            <div className="h-36 w-36 shrink-0 overflow-hidden rounded-xl border border-slate-200 bg-slate-50 dark:border-stone-800 dark:bg-stone-900">
              {preview.coverAvailable ? (
                <img src={resolveApiUrl(preview.coverUrl)} alt={`Portada de ${preview.title || 'producto Vinyl Future'}`} className="h-full w-full object-cover" />
              ) : (
                <div className="flex h-full items-center justify-center p-3 text-center text-xs text-slate-400">Portada no disponible</div>
              )}
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-xs font-semibold uppercase tracking-wider text-emerald-600">Producto encontrado</p>
              <h4 className="mt-1 text-lg font-bold text-slate-900 dark:text-white">{dato(preview.artist)} — {dato(preview.title)}</h4>
              <div className="mt-3 grid grid-cols-2 gap-2 text-xs sm:grid-cols-3">
                {[
                  ['Código', preview.catalogueCode], ['Formato', preview.format], ['Sello', preview.label],
                  ['Año', preview.year], ['Género', preview.genre], ['País', preview.country],
                ].map(([label, value]) => (
                  <div key={label}><span className="text-slate-400">{label}: </span><span className="text-slate-700 dark:text-stone-300">{dato(value)}</span></div>
                ))}
              </div>
              <p className="mt-3 text-xs text-slate-500 dark:text-stone-400">Estado de metadatos: {preview.metadataStatus}</p>
              <div className={`mt-3 rounded-lg px-3 py-2 text-xs ${preview.existingProduct
                ? 'bg-amber-50 text-amber-800 dark:bg-amber-950/30 dark:text-amber-300'
                : 'bg-emerald-50 text-emerald-800 dark:bg-emerald-950/30 dark:text-emerald-300'}`}>
                {preview.existingProduct
                  ? 'Producto ya existente. Se reutilizará el producto y se agregarán las copias seleccionadas al stock.'
                  : 'Producto nuevo. Se creará en el catálogo con las copias seleccionadas.'}
              </div>
            </div>
          </div>

          {preview.tracks?.length > 0 ? (
            <p className="text-xs text-slate-500 dark:text-stone-400">Previsualizaciones de audio disponibles: {preview.tracks.length}</p>
          ) : (
            <p className="text-xs text-slate-400">Audio: Sin información disponible</p>
          )}

          <div className="flex flex-wrap items-end gap-3">
            <label className="text-xs font-medium text-slate-600 dark:text-stone-300">
              Copias físicas a agregar
              <input type="number" min="1" step="1" value={cantidad}
                onChange={event => setCantidad(event.target.value)}
                className="mt-1 block w-28 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm dark:border-stone-700 dark:bg-stone-900" />
            </label>
            <button type="button" onClick={confirmar} disabled={estado === 'saving' || estado === 'done'}
              className="rounded-lg bg-[#5C7D87] px-4 py-2 text-sm font-medium text-white disabled:opacity-50">
              {estado === 'saving' ? 'Agregando al catálogo…' : preview.existingProduct ? 'Agregar copia al stock' : 'Agregar al catálogo'}
            </button>
            <button type="button" onClick={descargarPortada} disabled={!preview.coverAvailable || Boolean(descargando)}
              className="rounded-lg border border-slate-300 px-4 py-2 text-sm text-slate-700 disabled:opacity-40 dark:border-stone-700 dark:text-stone-300">
              {descargando === 'cover' ? 'Descargando…' : preview.coverAvailable ? 'Descargar portada' : 'Portada no disponible'}
            </button>
            <button type="button" onClick={descargarZip} disabled={Boolean(descargando)}
              className="rounded-lg border border-slate-300 px-4 py-2 text-sm text-slate-700 disabled:opacity-40 dark:border-stone-700 dark:text-stone-300">
              {descargando === 'zip' ? 'Generando ZIP…' : 'Descargar ZIP del producto'}
            </button>
          </div>
        </div>
      )}

      {resultado && (
        <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800 dark:border-emerald-800 dark:bg-emerald-950/20 dark:text-emerald-300">
          {resultado.alreadyProcessed
            ? 'Esta operación ya había sido procesada; no se agregaron copias duplicadas.'
            : `${resultado.catalogueStatus === 'NUEVO' ? 'Producto creado' : 'Producto existente reutilizado'}: se agregaron ${resultado.addedCopies} copia(s). Stock resultante: ${resultado.resultingStock}.`}
        </div>
      )}
      {errorMsg && <p className="text-xs text-red-600 dark:text-red-400">{errorMsg}</p>}
    </section>
  )
}

// ── Sub-section B: existing PDF → ZIP export ─────────────────────────────────

function PdfExport({ onImportFinished }) {
  const [archivo, setArchivo] = useState(null)
  const [estado, setEstado] = useState('idle')
  const [job, setJob] = useState(null)
  const [validacion, setValidacion] = useState(null)
  const [exportando, setExportando] = useState(false)
  const [errorMsg, setErrorMsg] = useState('')
  const [segundos, setSegundos] = useState(0)
  const [exportSegundos, setExportSegundos] = useState(0)

  useEffect(() => {
    if (estado !== 'loading') return undefined
    const timer = window.setInterval(() => setSegundos(s => s + 1), 1000)
    return () => window.clearInterval(timer)
  }, [estado])

  useEffect(() => {
    if (!exportando) return undefined
    const timer = window.setInterval(() => setExportSegundos(s => s + 1), 1000)
    return () => window.clearInterval(timer)
  }, [exportando])

  const etapaZip = exportSegundos < 4
    ? 'Validando archivos descargados…'
    : exportSegundos < 18
      ? 'Armando el ZIP desde la importación reciente…'
      : exportSegundos < 45
        ? 'Comprimiendo CSV, portadas y MP3 locales…'
        : 'La descarga sigue en curso. Se mantiene abierta hasta completar el ZIP…'

  async function procesar() {
    if (!archivo) return
    setSegundos(0)
    setEstado('validating')
    setErrorMsg('')
    try {
      const validation = await api.importar.vinylfutureValidar(archivo)
      setValidacion(validation)
      if (!validation.consistent || validation.unparsedRows > 0 || validation.errors?.length > 0) {
        setEstado('review')
        return
      }
      await iniciarImportacion(validation.validationId, false)
    } catch (err) {
      setErrorMsg(err.message || 'Error al validar el PDF')
      setEstado('error')
    }
  }

  async function iniciarImportacion(validationId, continuarParcial) {
    setSegundos(0)
    setEstado('loading')
    setErrorMsg('')
    try {
      const start = await api.importar.vinylfutureConfirmar(validationId, continuarParcial)
      const firstJob = await api.importar.vinylfutureJob(start.jobId)
      setJob(firstJob)
    } catch (err) {
      setErrorMsg(err.message || 'Error al procesar el PDF')
      setEstado('error')
    }
  }

  async function cancelarImportacion() {
    const validationId = validacion?.validationId
    if (validationId) {
      try {
        await api.importar.vinylfutureCancelar(validationId)
      } catch {
        // La validación no produce cambios; el reinicio local sigue siendo seguro.
      }
    }
    reset()
  }

  useEffect(() => {
    if (estado !== 'loading' || !job?.jobId) return undefined
    let cancelled = false
    const timer = window.setInterval(async () => {
      try {
        const next = await api.importar.vinylfutureJob(job.jobId)
        if (cancelled) return
        setJob(next)
        if (['COMPLETED', 'COMPLETED_WITH_ERRORS'].includes(next.status)) {
          setEstado('done')
        }
        if (['FAILED', 'CANCELLED'].includes(next.status)) {
          setErrorMsg(next.errors?.[0] || 'Error al procesar el PDF')
          setEstado('error')
        }
      } catch (err) {
        if (!cancelled) {
          setErrorMsg(err.message || 'No se pudo consultar el estado de la importación')
          setEstado('error')
        }
      }
    }, 1500)
    return () => {
      cancelled = true
      window.clearInterval(timer)
    }
  }, [estado, job?.jobId])

  async function exportarZip() {
    setExportSegundos(0)
    setExportando(true)
    setErrorMsg('')
    try {
      const importId = job?.importId || job?.summary?.importId
      if (!importId) {
        throw new Error('No hay un identificador válido de importación para descargar el ZIP. Volvé a procesar el PDF.')
      }
      const result = await api.importar.vinylfutureZip(importId)
      const ts = new Date().toISOString().replace(/[:T]/g, '-').slice(0, 16)
      downloadBlob(result.blob, result.filename || `vinylfuture-export-${ts}.zip`, result.contentDisposition)
    } catch (err) {
      setErrorMsg(err.message || 'Error al exportar el ZIP')
    } finally {
      setExportando(false)
    }
  }

  function reset() {
    setArchivo(null); setJob(null); setValidacion(null)
    setEstado('idle'); setErrorMsg(''); setSegundos(0); setExportSegundos(0)
  }

  const resumen = job?.summary
  const progress = Math.max(0, Math.min(job?.progressPercent || 0, 100))
  const activeStep = job?.currentStep || 'Factura recibida'
  const canDownloadZip = estado === 'done' && Boolean(job?.importId || resumen?.importId)

  useEffect(() => {
    if (estado === 'done') onImportFinished?.()
  }, [estado, onImportFinished])

  return (
    <div className="space-y-4">
      <div>
        <h3 className="font-semibold text-slate-800 dark:text-stone-200 text-sm mb-1">Importar desde factura de Vinyl Future</h3>
        <p className="text-xs text-slate-500 dark:text-stone-400">
          Subí una factura PDF de Vinyl Future para buscar cada ítem, descargar portadas y guardar los discos en el catálogo.
        </p>
      </div>
      {estado === 'idle' && (
        <>
          <UploadZone accept="application/pdf" label="Arrastrá el PDF aquí o hacé clic" onFile={setArchivo} />
          <button onClick={procesar} disabled={!archivo}
            className="px-5 py-2.5 rounded-lg bg-[#5C7D87] hover:bg-[#4a6a74] disabled:opacity-40 disabled:cursor-not-allowed text-white text-sm font-medium transition-colors">
            Subir factura PDF
          </button>
        </>
      )}
      {estado === 'validating' && (
        <div className="rounded-xl border border-[#7E9FA8]/20 bg-[#7E9FA8]/5 px-4 py-3">
          <Spinner text="Validando factura…" />
        </div>
      )}
      {estado === 'review' && validacion && (
        <div className="space-y-4 rounded-xl border border-amber-300 bg-amber-50 p-4 dark:border-amber-800 dark:bg-amber-950/20">
          <div>
            <p className="font-semibold text-amber-800 dark:text-amber-300">Se detectaron elementos que requieren revisión</p>
            <p className="mt-1 text-xs text-amber-700 dark:text-amber-400">
              Ningún cambio fue realizado todavía en el catálogo ni en el stock.
            </p>
          </div>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            {[
              ['Cantidad declarada', validacion.declaredQuantity],
              ['Filas detectadas', validacion.detectedSourceRows],
              ['Copias válidas', validacion.parsedPhysicalQuantity],
              ['Copias pendientes', validacion.unparsedRows > 0 && validacion.pendingPhysicalQuantity === 0
                ? 'Sin determinar'
                : validacion.pendingPhysicalQuantity],
            ].map(([label, value]) => (
              <div key={label} className="rounded-lg border border-amber-200 px-3 py-2 dark:border-amber-800">
                <p className="text-[10px] uppercase tracking-wider text-amber-700/70 dark:text-amber-500">{label}</p>
                <p className="text-lg font-bold text-amber-900 dark:text-amber-200">{value ?? 'No disponible'}</p>
              </div>
            ))}
          </div>
          {validacion.sourceRows?.filter(row => row.status === 'REVIEW_REQUIRED').map(row => (
            <div key={row.sourceRowNumber} className="rounded-lg border border-red-200 bg-white/70 p-3 text-xs dark:border-red-900 dark:bg-stone-950/40">
              <p className="font-semibold text-red-700 dark:text-red-300">No se pudo interpretar una línea de producto.</p>
              <p className="mt-1 text-slate-600 dark:text-stone-300">Página: {row.pageNumber || 'No disponible'}</p>
              <p className="mt-1 break-words text-slate-600 dark:text-stone-300">Texto detectado: {row.sourceText}</p>
              <p className="mt-1 text-red-600 dark:text-red-400">Motivo: {row.reason}</p>
              {row.estimatedQuantity != null && <p className="mt-1 text-slate-600 dark:text-stone-300">Cantidad estimada: {row.estimatedQuantity}</p>}
            </div>
          ))}
          {validacion.errors?.length > 0 && (
            <ul className="list-disc space-y-1 pl-5 text-xs text-red-700 dark:text-red-300">
              {validacion.errors.map((error, index) => <li key={`${error}-${index}`}>{error}</li>)}
            </ul>
          )}
          <div className="flex flex-wrap gap-3">
            <button onClick={() => iniciarImportacion(validacion.validationId, true)}
              disabled={validacion.parsedPhysicalQuantity < 1}
              className="rounded-lg bg-[#5C7D87] px-4 py-2.5 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-40">
              Continuar con {validacion.parsedPhysicalQuantity} copias válidas
            </button>
            <button onClick={cancelarImportacion}
              className="rounded-lg border border-amber-400 px-4 py-2.5 text-sm font-medium text-amber-800 dark:text-amber-300">
              Cancelar importación
            </button>
          </div>
        </div>
      )}
      {estado === 'loading' && (
        <div className="rounded-xl border border-[#7E9FA8]/20 bg-[#7E9FA8]/5 px-4 py-3">
          <Spinner text={`Procesando factura · ${activeStep}`} />
          <div className="h-1.5 overflow-hidden rounded-full bg-slate-200 dark:bg-stone-800">
            <div className="h-full rounded-full bg-[#7E9FA8] transition-all" style={{ width: `${progress}%` }} />
          </div>
          <p className="mt-2 text-center text-xs text-slate-500 dark:text-stone-400">
            {segundos}s · {progress}% · productos detectados {job?.totalItems || 0} · unidades totales {job?.totalQuantity || 0}
          </p>
        </div>
      )}
      {estado === 'done' && (
        <div className="p-4 rounded-xl bg-emerald-50 dark:bg-emerald-900/20 border border-emerald-200 dark:border-emerald-800">
          <p className="font-medium text-emerald-700 dark:text-emerald-400 text-sm mb-3">
            {resumen?.partialImport
              ? 'Importación completada con elementos pendientes'
              : job?.status === 'COMPLETED_WITH_ERRORS'
                ? 'Importación completada con advertencias'
                : 'Factura validada e importada correctamente'}
          </p>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 mb-4">
            {[
              ['Factura', resumen?.invoiceNumber || job?.invoiceNumber],
              ['Cantidad declarada', resumen?.declaredCopies ?? job?.totalQuantity],
              ['Copias importadas', resumen?.importedCopies],
              ['Copias pendientes', resumen?.pendingCopies],
              ['Productos procesados', job?.successCount ?? resumen?.recordsImported],
              ['Omitidos', job?.skippedCount],
              ['Errores', job?.failedCount],
              ['Portadas encontradas', resumen?.coversFound],
              ['Portadas descargadas', resumen?.coversDownloaded],
              ['MP3 encontrados', resumen?.mp3PreviewsFound],
              ['MP3 descargados', resumen?.mp3Downloaded],
              ['Audios/portadas fallidos', resumen?.failedMediaDownloads],
              ['YouTube', resumen?.youtubeLinksFound],
              ['QR creados', resumen?.qrEntriesCreated],
              ['Productos nuevos', Math.max(0, (resumen?.recordsImported || 0) - (resumen?.skippedDuplicates || 0))],
              ['Productos ya existentes', resumen?.skippedDuplicates],
              ['Enlaces sin datos', resumen?.failedLinks],
              ['Límites del proveedor', resumen?.rateLimitFailures],
              ['Estado del ZIP', resumen?.zipStatus === 'DISPONIBLE' ? 'Disponible' : 'No disponible'],
            ].map(([label, value]) => (
              <div key={label} className="rounded-lg border border-emerald-200 dark:border-emerald-800 px-3 py-2">
                <p className="text-[10px] uppercase tracking-wider text-emerald-600/70 dark:text-emerald-500">{label}</p>
                <p className="text-lg font-bold text-emerald-800 dark:text-emerald-300">{value ?? 0}</p>
              </div>
            ))}
          </div>
          {job?.warnings?.length > 0 && (
            <details className="mb-3 text-xs text-amber-700 dark:text-amber-300">
              <summary className="cursor-pointer font-medium">Ver advertencias ({job.warnings.length})</summary>
              <ul className="mt-2 list-disc pl-5 space-y-1">
                {job.warnings.map((warning, index) => <li key={`${warning}-${index}`}>{warning}</li>)}
              </ul>
            </details>
          )}
          {job?.errors?.length > 0 && (
            <details className="mb-3 text-xs text-red-700 dark:text-red-300">
              <summary className="cursor-pointer font-medium">Ver errores ({job.errors.length})</summary>
              <ul className="mt-2 list-disc pl-5 space-y-1">
                {job.errors.map((error, index) => <li key={`${error}-${index}`}>{error}</li>)}
              </ul>
            </details>
          )}
          {resumen?.failedLinkDetails?.length > 0 && (
            <p className="mb-3 text-xs text-amber-700 dark:text-amber-300">
              Información adicional pendiente: {resumen.failedLinkDetails.join(', ')}
            </p>
          )}
          {job?.sourceRows?.some(row => row.status === 'REVIEW_REQUIRED') && (
            <details className="mb-3 text-xs text-amber-700 dark:text-amber-300">
              <summary className="cursor-pointer font-medium">Ver elementos pendientes</summary>
              <ul className="mt-2 space-y-2">
                {job.sourceRows.filter(row => row.status === 'REVIEW_REQUIRED').map(row => (
                  <li key={row.sourceRowNumber}>Página {row.pageNumber}: {row.sourceText} — {row.reason}</li>
                ))}
              </ul>
            </details>
          )}
          <button onClick={exportarZip} disabled={exportando || !canDownloadZip}
            className="inline-flex items-center gap-2 px-4 py-2 rounded-lg border border-emerald-500 text-emerald-700 dark:text-emerald-300 text-sm font-medium disabled:opacity-50">
            {exportando ? 'Preparando ZIP…' : 'Descargar ZIP'}
          </button>
          <button onClick={reset} disabled={exportando} className="ml-3 text-xs underline text-emerald-600 dark:text-emerald-400 disabled:opacity-50">Nueva importación</button>
          {exportando && (
            <div className="mt-4 rounded-xl border border-emerald-200 dark:border-emerald-800 bg-emerald-100/60 dark:bg-emerald-950/30 px-4 py-3">
              <Spinner text={etapaZip} />
              <div className="h-1.5 overflow-hidden rounded-full bg-emerald-200/70 dark:bg-emerald-900">
                <div className="h-full w-1/3 animate-pulse rounded-full bg-emerald-500" />
              </div>
              <p className="mt-2 text-center text-xs text-emerald-700/80 dark:text-emerald-300/80">
                {exportSegundos}s · se usa la media local ya descargada en esta importación
              </p>
            </div>
          )}
          {errorMsg && <p className="mt-2 text-xs text-red-600 dark:text-red-400">{errorMsg}</p>}
        </div>
      )}
      {estado === 'error' && (
        <div className="p-4 rounded-xl bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800">
          <p className="text-sm text-red-700 dark:text-red-400 font-medium">Error al procesar</p>
          {errorMsg && <p className="text-xs text-red-600 dark:text-red-300 mt-1">{errorMsg}</p>}
          <button onClick={reset} className="mt-1 text-xs underline text-red-600 dark:text-red-400">Reintentar</button>
        </div>
      )}
    </div>
  )
}

export default function VinylFutureTab() {
  const [pendientes, setPendientes] = useState([])
  const [pendienteSeleccionado, setPendienteSeleccionado] = useState(null)
  const [errorPendientes, setErrorPendientes] = useState('')

  const cargarPendientes = useCallback(async () => {
    try {
      setPendientes(await api.importar.vinylfuturePendientes())
      setErrorPendientes('')
    } catch (err) {
      setErrorPendientes(err.message || 'No se pudieron cargar los elementos pendientes')
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    api.importar.vinylfuturePendientes()
      .then(data => {
        if (!cancelled) setPendientes(data)
      })
      .catch(err => {
        if (!cancelled) setErrorPendientes(err.message || 'No se pudieron cargar los elementos pendientes')
      })
    return () => { cancelled = true }
  }, [])

  function handleResolved() {
    setPendienteSeleccionado(null)
    cargarPendientes()
  }

  return (
    <div className="space-y-8">
      <section className="rounded-2xl border border-slate-200 bg-white p-5 dark:border-stone-800 dark:bg-stone-950/40">
        <p className="mb-1 text-[10px] font-semibold uppercase tracking-[0.18em] text-[#5C7D87] dark:text-[#7E9FA8]">A. Factura PDF</p>
        <PdfExport onImportFinished={cargarPendientes} />
      </section>

      {pendientes.length > 0 && (
        <section className="rounded-2xl border border-amber-300 bg-amber-50 p-5 dark:border-amber-800 dark:bg-amber-950/20 space-y-3">
          <div>
            <h3 className="font-semibold text-amber-900 dark:text-amber-200">Elementos pendientes de facturas</h3>
            <p className="mt-1 text-xs text-amber-700 dark:text-amber-400">Elegí un elemento y vinculalo explícitamente con el producto correcto.</p>
          </div>
          {pendientes.map(item => (
            <div key={item.pendingItemId} className="flex flex-col justify-between gap-3 rounded-xl border border-amber-200 bg-white/70 p-3 text-xs dark:border-amber-900 dark:bg-stone-950/40 sm:flex-row sm:items-center">
              <div>
                <p className="font-semibold text-slate-800 dark:text-stone-200">Factura {item.invoiceNumber || 'sin número'} · página {item.pageNumber || 'sin información'}</p>
                <p className="mt-1 text-slate-600 dark:text-stone-400">{item.sourceText}</p>
                <p className="mt-1 text-amber-700 dark:text-amber-400">{item.reviewReason}</p>
              </div>
              <button type="button" onClick={() => setPendienteSeleccionado(item)}
                className="shrink-0 rounded-lg bg-amber-700 px-4 py-2 font-medium text-white">
                Resolver manualmente
              </button>
            </div>
          ))}
        </section>
      )}
      {errorPendientes && <p className="text-xs text-red-600 dark:text-red-400">{errorPendientes}</p>}

      <ManualUrlImport
        key={pendienteSeleccionado?.pendingItemId || 'importacion-manual'}
        pendingItem={pendienteSeleccionado}
        onResolved={handleResolved}
        onCancelPending={() => setPendienteSeleccionado(null)}
      />
    </div>
  )
}
