import { useEffect, useMemo, useRef, useState } from 'react'
import { api } from '../../api/sonograma'
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

function PreviewCard({ preview, onChange, onGuardar, onCover, onZip, saving, mediaBusy }) {
  if (!preview) return null
  const tieneErrores = preview.errores?.length > 0
  const copySalePrice = preview.copySalePrice ?? preview.precioVenta ?? ''

  return (
    <div className="rounded-xl border border-slate-200 dark:border-stone-800 p-5 space-y-4">
      <div className="flex gap-4">
        {preview.imagenUrl && (
          <img src={preview.imagenUrl} alt={preview.album}
            className="w-24 h-24 object-cover rounded-lg bg-slate-100 dark:bg-stone-800 flex-shrink-0" />
        )}
        <div className="flex-1 min-w-0">
          <input
            className="input text-sm font-semibold mb-1 w-full"
            value={preview.artista || ''}
            onChange={e => onChange({ ...preview, artista: e.target.value })}
            placeholder="Artista"
          />
          <input
            className="input text-sm w-full"
            value={preview.album || ''}
            onChange={e => onChange({ ...preview, album: e.target.value })}
            placeholder="Álbum"
          />
        </div>
      </div>

      {tieneErrores && (
        <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg px-3 py-2">
          {preview.errores.map((e, i) => (
            <p key={i} className="text-xs text-red-600 dark:text-red-400">{e}</p>
          ))}
        </div>
      )}

      <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 text-xs">
        {[
          ['Sello', 'sello'], ['Año', 'anio'], ['País', 'pais'],
          ['Género', 'genero'], ['Estilo', 'estilo'], ['Formato', 'formato'],
        ].map(([label, field]) => (
          <div key={field}>
            <p className="text-slate-400 dark:text-stone-500 mb-1">{label}</p>
            <input
              className="input text-xs w-full"
              value={preview[field] || ''}
              onChange={e => onChange({ ...preview, [field]: e.target.value })}
              placeholder="—"
            />
          </div>
        ))}
      </div>

      <div>
        <p className="text-xs text-slate-400 dark:text-stone-500 mb-1">Tracklist</p>
        <textarea
          rows={4}
          className="input text-xs w-full resize-none"
          value={preview.tracklist || ''}
          onChange={e => onChange({ ...preview, tracklist: e.target.value })}
          placeholder="Tracklist"
        />
      </div>

      <div>
        <p className="text-xs text-slate-400 dark:text-stone-500 mb-1">Código de cliente</p>
        <input
          type="text"
          className="input text-sm w-full"
          value={preview.customerCode || ''}
          onChange={e => onChange({ ...preview, customerCode: e.target.value })}
          placeholder="JPH"
          required
        />
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div>
          <p className="text-xs text-slate-400 dark:text-stone-500 mb-1">Precio venta</p>
          <input
            type="number"
            className="input text-sm w-full"
            value={copySalePrice}
            onChange={e => {
              const value = e.target.value ? Number(e.target.value) : null
              onChange({ ...preview, precioVenta: value, copySalePrice: value })
            }}
            placeholder="$"
            min="0"
            step="0.01"
            required
          />
        </div>
        <div>
          <p className="text-xs text-slate-400 dark:text-stone-500 mb-1">Condición física</p>
          <input
            type="text"
            className="input text-sm w-full"
            value={preview.physicalCondition || ''}
            onChange={e => onChange({ ...preview, physicalCondition: e.target.value })}
            placeholder="VG+, NM, M, G..."
          />
        </div>
      </div>

      {preview.previewUrl && (
        <div>
          <p className="text-xs text-slate-400 dark:text-stone-500 mb-1">Preview de audio</p>
          <audio controls src={preview.previewUrl} className="w-full h-9" />
        </div>
      )}

      {preview.productoExistente ? (
        <div className="rounded-lg border border-sky-200 bg-sky-50 px-3 py-2 text-xs text-sky-800 dark:border-sky-800 dark:bg-sky-950/30 dark:text-sky-200">
          <p className="font-medium">Producto ya existente</p>
          <p>Actualmente tiene {preview.copiasDisponibles ?? 0} copias disponibles. Se agregará {preview.cantidadCopias || 1} copia nueva.</p>
        </div>
      ) : !tieneErrores && (
        <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs text-emerald-800 dark:border-emerald-800 dark:bg-emerald-950/30 dark:text-emerald-200">
          <p className="font-medium">Producto nuevo</p>
          <p>Se agregará {preview.cantidadCopias || 1} copia al catálogo.</p>
        </div>
      )}

      <div className="grid grid-cols-2 gap-3">
        <button
          type="button"
          onClick={() => onCover(preview)}
          disabled={!preview.operationId || mediaBusy}
          className="px-3 py-2 rounded-lg border border-slate-300 dark:border-stone-700 text-slate-700 dark:text-stone-200 text-sm disabled:opacity-40"
        >
          {mediaBusy === 'cover' ? 'Descargando…' : 'Descargar portada'}
        </button>
        <button
          type="button"
          onClick={() => onZip(preview)}
          disabled={!preview.operationId || mediaBusy}
          className="px-3 py-2 rounded-lg border border-slate-300 dark:border-stone-700 text-slate-700 dark:text-stone-200 text-sm disabled:opacity-40"
        >
          {mediaBusy === 'zip' ? 'Preparando…' : 'Descargar ZIP'}
        </button>
      </div>

      <button
        onClick={() => onGuardar(preview)}
        disabled={saving || tieneErrores || !preview.artista || !preview.album
          || !preview.customerCode?.trim() || copySalePrice === '' || copySalePrice == null}
        className="w-full px-5 py-2.5 rounded-lg bg-[#5C7D87] hover:bg-[#4a6a74] disabled:opacity-40 disabled:cursor-not-allowed text-white text-sm font-medium transition-colors"
      >
        {saving ? 'Guardando…' : preview.productoExistente ? 'Agregar copia' : 'Guardar producto'}
      </button>
    </div>
  )
}

// ── Sub-section A: single link ────────────────────────────────────────────────

function LinkSingle() {
  const [url, setUrl] = useState('')
  const [estado, setEstado] = useState('idle') // idle | loading | preview | saving | done | error
  const [preview, setPreview] = useState(null)
  const [errorMsg, setErrorMsg] = useState('')
  const [result, setResult] = useState(null)
  const [mediaBusy, setMediaBusy] = useState('')

  async function fetchLink() {
    if (!url.trim()) return
    setEstado('loading')
    setErrorMsg('')
    try {
      const data = await api.importaciones.discogsDesdeLink(url.trim())
      setPreview({ ...data, customerCode: preview?.customerCode || data.customerCode || '' })
      setEstado('preview')
    } catch (err) {
      setErrorMsg(err.message || 'Error al consultar Discogs')
      setEstado('error')
    }
  }

  async function guardar(p) {
    setEstado('saving')
    setErrorMsg('')
    try {
      const response = await api.importaciones.discogsGuardar(p)
      setResult(response)
      setEstado('done')
    } catch (err) {
      setErrorMsg(err.message || 'No se pudo guardar la importación. Podés volver a intentarlo sin duplicar el stock.')
      setEstado('error')
    }
  }

  async function downloadCover(p) {
    setMediaBusy('cover')
    setErrorMsg('')
    try {
      const response = await api.importaciones.discogsManualCover(p)
      if (response.imagenUrl) setPreview(current => ({ ...current, imagenUrl: response.imagenUrl }))
      if (response.warning) setErrorMsg(`Portada: ${response.warning}`)
    } catch (err) {
      setErrorMsg(err.message || 'No se pudo descargar la portada.')
    } finally {
      setMediaBusy('')
    }
  }

  async function downloadZip(p) {
    setMediaBusy('zip')
    setErrorMsg('')
    try {
      const file = await api.importaciones.discogsManualZip(p)
      downloadBlob(file.blob, file.filename, file.contentDisposition)
    } catch (err) {
      setErrorMsg(err.message || 'No se pudo generar el ZIP.')
    } finally {
      setMediaBusy('')
    }
  }

  function reset() {
    const retainedCustomerCode = preview?.customerCode?.trim() || ''
    setUrl('')
    setPreview(retainedCustomerCode ? { customerCode: retainedCustomerCode } : null)
    setResult(null)
    setEstado('idle')
    setErrorMsg('')
  }

  return (
    <div className="space-y-4">
      <div>
        <h3 className="font-semibold text-slate-800 dark:text-stone-200 text-sm mb-1">Buscar por link de Discogs</h3>
        <p className="text-xs text-slate-500 dark:text-stone-400">
          Ingresá la URL de un release en discogs.com para obtener todos los datos.
        </p>
      </div>

      <div className="flex gap-2">
        <input
          type="url"
          value={url}
          onChange={e => setUrl(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && fetchLink()}
          placeholder="https://www.discogs.com/release/12345"
          className="input flex-1 text-sm"
          disabled={estado === 'loading' || estado === 'saving'}
        />
        <button
          onClick={fetchLink}
          disabled={!url.trim() || estado === 'loading' || estado === 'saving'}
          className="px-4 py-2 rounded-lg bg-[#5C7D87] hover:bg-[#4a6a74] disabled:opacity-40 disabled:cursor-not-allowed text-white text-sm font-medium transition-colors whitespace-nowrap"
        >
          {estado === 'loading' ? 'Buscando…' : 'Buscar'}
        </button>
      </div>

      {estado === 'loading' && <Spinner text="Consultando Discogs API…" />}

      {(estado === 'preview' || estado === 'saving' || estado === 'error') && preview && (
        <PreviewCard
          preview={preview}
          onChange={setPreview}
          onGuardar={guardar}
          onCover={downloadCover}
          onZip={downloadZip}
          saving={estado === 'saving'}
          mediaBusy={mediaBusy}
        />
      )}

      {estado === 'done' && (
        <div className="p-4 rounded-xl bg-emerald-50 dark:bg-emerald-900/20 border border-emerald-200 dark:border-emerald-800">
          <p className="text-sm font-medium text-emerald-700 dark:text-emerald-400">
            ✓ {result?.alreadyProcessed
              ? 'La importación ya había sido confirmada correctamente.'
              : result?.resultType === 'EXISTING_PRODUCT'
                ? `Producto ya existente: se agregó ${result?.copiesAdded || 1} copia al stock.`
                : 'Producto nuevo importado correctamente.'}
          </p>
          {result?.availableCopies !== undefined && <p className="mt-1 text-xs text-emerald-700 dark:text-emerald-300">Copias disponibles: {result.availableCopies}</p>}
          {preview && <div className="mt-3 grid grid-cols-2 gap-3">
            <button onClick={() => downloadCover(preview)} disabled={mediaBusy} className="text-xs underline text-emerald-700 dark:text-emerald-300 disabled:opacity-40">Descargar portada</button>
            <button onClick={() => downloadZip(preview)} disabled={mediaBusy} className="text-xs underline text-emerald-700 dark:text-emerald-300 disabled:opacity-40">Descargar ZIP</button>
          </div>}
          <button onClick={reset} className="mt-1 text-xs underline text-emerald-600 dark:text-emerald-400">Buscar otro</button>
        </div>
      )}

      {estado === 'error' && (
        <div className="p-4 rounded-xl bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800">
          <p className="text-sm font-medium text-red-700 dark:text-red-400">Error</p>
          {errorMsg && <p className="text-xs text-red-600 dark:text-red-300 mt-1">{errorMsg}</p>}
          {!preview && <button onClick={reset} className="mt-1 text-xs underline text-red-600 dark:text-red-400">Reintentar</button>}
        </div>
      )}
    </div>
  )
}

// ── Sub-section B: Excel with Discogs links ───────────────────────────────────

const ACTIVE_DISCOGS_JOB_KEY = 'sonograma:discogs-excel-job:v1'

function rememberDiscogsJob(jobId) {
  try {
    window.localStorage.setItem(ACTIVE_DISCOGS_JOB_KEY, String(jobId))
  } catch {
    // The import remains recoverable through the API even if browser storage is unavailable.
  }
}

function rememberedDiscogsJob() {
  try {
    const value = window.localStorage.getItem(ACTIVE_DISCOGS_JOB_KEY)
    return value && /^\d+$/.test(value) ? Number(value) : null
  } catch {
    return null
  }
}

function forgetDiscogsJob() {
  try {
    window.localStorage.removeItem(ACTIVE_DISCOGS_JOB_KEY)
  } catch {
    // Nothing else is required when storage is unavailable.
  }
}

function stepLabel(job, estado, processing) {
  if (estado === 'loading') return 'Leyendo Excel'
  if (estado === 'saving') return 'Importando al catálogo'
  if (!job) return 'Validando filas'
  const labels = {
    reading_excel: 'Leyendo Excel',
    parsing_rows: 'Analizando filas',
    resolving_discogs: 'Resolviendo masters de Discogs',
    fetching_metadata: 'Obteniendo metadata de Discogs',
    downloading_covers: 'Descargando portadas',
    fetching_youtube: 'Procesando links de YouTube',
    ready_for_catalog_import: 'Listo para importar al catálogo',
    importing_catalog: 'Importando al catálogo',
    preparing_zip: 'Preparando ZIP de portadas',
    completed: job.status === 'completed_with_warnings' ? 'Completado con advertencias' : 'Completado',
  }
  if (processing && job.rateLimited > 0) return 'Discogs agotó los reintentos automáticos'
  return labels[job.stage] || 'Procesando importación'
}

function jobStatusLabel(status) {
  const labels = {
    pending: 'pendiente',
    processing: 'en curso',
    completed: 'completado',
    completed_with_warnings: 'completado con elementos pendientes',
    completed_with_errors: 'completado con errores',
    failed: 'fallido — revisable',
  }
  return labels[status] || status || '—'
}

function statusLabel(row) {
  const previewLabels = {
    NEW_COPY: 'NUEVA COPIA',
    ALREADY_RECEIVED: 'YA RECIBIDA',
    MANUAL_REVIEW: 'REVISIÓN MANUAL',
    BLOCKED_ERROR: 'BLOQUEADA / ERROR',
  }
  if (previewLabels[row.previewOutcome]) return previewLabels[row.previewOutcome]
  if (row.catalogImportStatus === 'already_imported') return 'Ya importada'
  if (row.catalogImportStatus === 'imported') return 'Importada'
  if (row.status === 'ignored') return 'Ignorada — no es un producto'
  if (row.catalogImportStatus === 'manual_review') return 'Importación técnicamente imposible — revisión manual'
  if (row.metadataStatus === 'missing_link') return 'Sin link — lista para importar con revisión'
  if (row.metadataStatus === 'rate_limited') return 'Reintentos automáticos agotados'
  if (row.metadataStatus === 'failed_retryable') return 'Fallo transitorio — pendiente de reintento'
  if (row.metadataStatus === 'processing') return 'Consultando Discogs'
  if (row.metadataStatus === 'failed') return row.metadataErrorCode === 'MASTER_RESOLUTION_FAILED'
    ? 'No se pudo resolver el master'
    : 'Metadata fallida'
  if (row.metadataStatus === 'success' && row.coverStatus !== 'success') return 'Metadata OK · portada no disponible'
  if (row.metadataStatus === 'success') return 'Metadata OK'
  const labels = {
    pending: 'Pendiente',
    parsed: 'Lista para importar',
    fetching_discogs: 'Consultando Discogs',
    fetching_metadata: 'Obteniendo metadata',
    failed: 'Falló la importación',
  }
  return labels[row.status] || row.status || '—'
}

function ImportConfirmationModal({ job, onConfirm, onCancel, saving, error }) {
  const newCopies = job.newCopiesToReceive ?? job.physicalCopiesToReceive ?? job.readyToImport ?? 0
  const available = job.availableCopiesToReceive ?? 0
  const sold = job.soldCopiesToReceive ?? 0
  const noPrice = job.noPriceReceivableRows ?? 0
  const alreadyReceived = job.alreadyReceivedRows ?? job.alreadyImported ?? 0
  const manualReview = job.manualReviewRows ?? 0

  return (
    <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center px-4" role="presentation">
      <div className="bg-white dark:bg-stone-900 border border-slate-200 dark:border-stone-700 rounded-2xl shadow-2xl w-full max-w-md p-6" role="dialog" aria-modal="true" aria-labelledby="discogs-confirm-title">
        <h3 id="discogs-confirm-title" className="text-slate-900 dark:text-white font-bold text-base mb-3">Confirmar recepción de inventario</h3>
        <p className="text-slate-600 dark:text-stone-300 text-sm mb-4">Vas a recibir {newCopies} copias físicas nuevas: {available} disponibles y {sold} vendidas.</p>
        <div className="rounded-lg bg-slate-50 dark:bg-stone-950 border border-slate-200 dark:border-stone-800 px-3 py-2 text-xs text-slate-600 dark:text-stone-300 space-y-1 mb-5">
          <p>{noPrice} sin precio — se conservarán sin precio.</p>
          <p>{manualReview} fila(s) requieren revisión manual y no se recibirán.</p>
          {alreadyReceived > 0 && <p>{alreadyReceived} fila(s) ya recibidas — no crearán stock adicional.</p>}
        </div>
        {error && <p role="alert" className="text-red-600 dark:text-red-300 text-sm mb-4">{error}</p>}
        <div className="flex gap-3">
          <button type="button" onClick={onCancel} disabled={saving} className="btn-secondary flex-1">Cancelar</button>
          <button type="button" onClick={onConfirm} disabled={saving} className="flex-1 bg-[#5C7D87] hover:bg-[#4a6a74] disabled:opacity-50 text-white font-semibold rounded-lg px-4 py-2 text-sm transition-all">
            {saving ? 'Recibiendo…' : 'Confirmar recepción'}
          </button>
        </div>
      </div>
    </div>
  )
}

function urlSourceLabel(source) {
  const labels = {
    hyperlink: 'hipervínculo',
    hyperlink_formula: 'fórmula de hipervínculo',
    fallback_hyperlink: 'hipervínculo en otra columna',
    fallback_hyperlink_formula: 'fórmula en otra columna',
    visible: 'texto visible',
    visible_r_id: 'texto visible (release)',
    visible_m_id: 'texto visible (master)',
    visible_discogs_text: 'texto Discogs',
  }
  return labels[source] || source || '—'
}

function ExcelLinks() {
  const [archivo, setArchivo] = useState(null)
  const [estado, setEstado] = useState('idle')
  const [job, setJob] = useState(null)
  const [errorMsg, setErrorMsg] = useState('')
  const [filter, setFilter] = useState('all')
  const [downloadingZip, setDownloadingZip] = useState(false)
  const [confirmingImport, setConfirmingImport] = useState(false)
  const [confirmationError, setConfirmationError] = useState('')
  const inputRef = useRef(null)

  const jobProcessing = job && ['pending', 'processing'].includes(job.status)
  const zipPreparing = job?.zipStatus === 'preparing'
  const processing = jobProcessing || zipPreparing
  const newCopiesToReceive = job?.newCopiesToReceive ?? job?.physicalCopiesToReceive ?? job?.readyToImport ?? 0
  const alreadyReceivedRows = job?.alreadyReceivedRows ?? job?.alreadyImported ?? 0
  const availableCopiesToReceive = job?.availableCopiesToReceive ?? 0
  const soldCopiesToReceive = job?.soldCopiesToReceive ?? 0
  const noPriceRows = job?.noPriceRows ?? 0
  const noPriceReceivableRows = job?.noPriceReceivableRows ?? 0
  const meaningfulRows = job?.meaningfulRows ?? 0
  const identityBearingRows = job?.identityBearingRows ?? 0
  const uniqueConcreteReleases = job?.resolvedConcreteReleases ?? 0
  const manualReviewRows = job?.manualReviewRows ?? 0
  const canConfirm = job?.canConfirm ?? (newCopiesToReceive > 0 && !processing)
  const filteredRows = useMemo(() => {
    const rows = job?.rows || []
    return rows.filter(row => {
      if (filter === 'all') return true
      if (filter === 'available') return row.sourceStatus === 'DISPONIBLE'
      if (filter === 'sold') return row.sourceStatus === 'VENDIDO'
      if (filter === 'reserved') return row.sourceStatus === 'RESERVADO'
      if (filter === 'invalid') return row.metadataStatus === 'missing_link'
      if (filter === 'ignored') return row.status === 'ignored'
      if (filter === 'pending') return ['pending', 'processing', 'rate_limited', 'failed_retryable'].includes(row.metadataStatus)
      if (filter === 'failed') return row.metadataStatus === 'failed' || ['failed', 'failed_retryable', 'missing_local_file'].includes(row.coverStatus)
      if (filter === 'imported') return ['imported', 'already_imported'].includes(row.catalogImportStatus)
      return true
    })
  }, [filter, job?.rows])

  const linkedRows = job?.rows?.filter(row => row.discogsId) || []
  const parsedRows = job?.realRowsRead || job?.totalRowsRead || 0
  const metadataDone = linkedRows.filter(row => !['pending', 'processing', 'rate_limited', 'failed_retryable'].includes(row.metadataStatus)).length
  const coverDone = linkedRows.filter(row => ['success', 'unavailable', 'missing_local_file', 'failed_retryable', 'failed', 'not_applicable'].includes(row.coverStatus)).length
  const youtubeDone = linkedRows.filter(row => ['success', 'partial', 'not_found', 'failed', 'not_applicable'].includes(row.youtubeStatus)).length
  const progressTotal = Math.max(parsedRows + linkedRows.length * 3, 1)
  const progressDone = Math.min(progressTotal, parsedRows + metadataDone + coverDone + youtubeDone)
  const progressPct = Math.round((progressDone / progressTotal) * 100)
  const currentStep = stepLabel(job, estado, processing)

  useEffect(() => {
    const savedJobId = rememberedDiscogsJob()
    if (!savedJobId) return undefined
    let cancelled = false
    api.importaciones.discogsJob(savedJobId).then(data => {
      if (cancelled) return
      setJob(data)
      setEstado('preview')
    }).catch(() => forgetDiscogsJob())
    return () => { cancelled = true }
  }, [])

  useEffect(() => {
    const jobId = job?.id
    if (!jobId || !processing) return undefined
    let cancelled = false
    let timer
    const poll = async () => {
      try {
        const updated = await api.importaciones.discogsJob(jobId)
        if (!cancelled) setJob(updated)
      } catch (err) {
        if (!cancelled) setErrorMsg(err.message || 'No se pudo actualizar el progreso')
      }
      if (!cancelled) timer = window.setTimeout(poll, 1500)
    }
    timer = window.setTimeout(poll, 1500)
    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [job?.id, processing])

  async function fetchExcel() {
    if (!archivo) return
    setEstado('loading')
    setErrorMsg('')
    try {
      const data = await api.importaciones.discogsDesdeExcel(archivo)
      setJob(data)
      rememberDiscogsJob(data.id)
      setEstado('preview')
    } catch (err) {
      setErrorMsg(err.message || 'Error al procesar Excel')
      setEstado('error')
    }
  }

  async function importarFilasIdentificables() {
    setEstado('saving')
    setConfirmationError('')
    try {
      setJob(await api.importaciones.discogsImportarJob(job.id))
      setEstado('preview')
    } catch (err) {
      setConfirmationError(err.message || 'Error al guardar')
      setErrorMsg(err.message || 'Error al guardar')
      setEstado('preview')
    } finally {
      setConfirmingImport(false)
    }
  }

  function solicitarImportacion() {
    if (!canConfirm) return
    setConfirmationError('')
    setConfirmingImport(true)
  }

  async function retryRow(rowId) {
    try {
      setJob(await api.importaciones.discogsRetryRow(job.id, rowId))
      setEstado('preview')
    } catch (err) {
      setErrorMsg(err.message || 'No se pudo reintentar la fila')
    }
  }

  async function retryPending() {
    try {
      setJob(await api.importaciones.discogsRetryPending(job.id))
      setEstado('preview')
    } catch (err) {
      setErrorMsg(err.message || 'No se pudieron reintentar las filas pendientes')
    }
  }

  async function descargarPortadas() {
    try {
      if (!job.zipReady) {
        const zip = await api.importaciones.discogsPrepareCoversZip(job.id)
        setJob(current => ({ ...current, ...zip }))
        return
      }
      setDownloadingZip(true)
      const result = await api.importaciones.discogsCoversZip(job.id)
      downloadBlob(result.blob, result.filename || `discogs-covers-${job.id}.zip`, result.contentDisposition)
    } catch (err) {
      setErrorMsg(err.message || 'No se pudo descargar el ZIP')
    } finally {
      setDownloadingZip(false)
    }
  }

  function reset() {
    setArchivo(null); setJob(null)
    setEstado('idle'); setErrorMsg('')
    forgetDiscogsJob()
    if (inputRef.current) inputRef.current.value = ''
  }

  return (
    <div className="space-y-4">
      <div>
        <h3 className="font-semibold text-slate-800 dark:text-stone-200 text-sm mb-1">Importar desde Excel con links de Discogs</h3>
        <p className="text-xs text-slate-500 dark:text-stone-400">
          Lee todas las filas, intenta enriquecerlas y permite importar cada registro identificable como USADO. Los estados del Excel se conservan solo para revisión.
        </p>
      </div>

      {estado === 'idle' && (
        <>
          <div
            className={`rounded-2xl border-2 border-dashed transition-colors cursor-pointer
              ${archivo ? 'border-[#7E9FA8]/50 bg-[#7E9FA8]/5' :
              'border-slate-200 dark:border-stone-700 hover:border-[#7E9FA8]/50 hover:bg-slate-50 dark:hover:bg-stone-900/50'}`}
            onClick={() => inputRef.current?.click()}
          >
            <input ref={inputRef} type="file" accept=".xlsx,.xls" className="hidden"
              onChange={e => setArchivo(e.target.files[0])} />
            <div className="flex flex-col items-center justify-center gap-2 py-8 text-center pointer-events-none">
              {archivo ? (
                <span className="text-[#5C7D87] dark:text-[#7E9FA8] text-sm font-medium">{archivo.name}</span>
              ) : (
                <span className="text-slate-500 dark:text-stone-400 text-sm">Seleccionar Excel (.xlsx, .xls)</span>
              )}
            </div>
          </div>
          <button onClick={fetchExcel} disabled={!archivo}
            className="px-5 py-2.5 rounded-lg bg-[#5C7D87] hover:bg-[#4a6a74] disabled:opacity-40 disabled:cursor-not-allowed text-white text-sm font-medium transition-colors">
            Buscar en Discogs
          </button>
        </>
      )}

      {estado === 'loading' && <Spinner text="Leyendo todas las filas y creando la importación…" />}

      {(estado === 'preview' || estado === 'saving') && job && (
        <div className="space-y-4">
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-sm font-medium text-slate-800 dark:text-stone-200">{job.nombreArchivo}</p>
              <p className="text-xs text-slate-500 dark:text-stone-500">
                Hoja {job.nombreHoja} · Estado: {jobStatusLabel(job.status)}
              </p>
            </div>
            {processing && <Spinner text="Enriqueciendo…" />}
          </div>

          <div className="space-y-2">
            <div className="flex items-center justify-between text-xs text-slate-500 dark:text-stone-400">
              <span>{currentStep}</span>
              <span>{progressPct}%</span>
            </div>
            <div className="h-2 rounded-full bg-slate-100 dark:bg-stone-800 overflow-hidden">
              <div className="h-full bg-[#5C7D87] transition-all" style={{ width: `${progressPct}%` }} />
            </div>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2">
            {[
              ['Nuevas copias', newCopiesToReceive],
              ['Disponibles', availableCopiesToReceive],
              ['Vendidas', soldCopiesToReceive],
              ['Ya recibidas', alreadyReceivedRows],
              ['Sin precio', noPriceRows],
              ['Revisión manual', manualReviewRows],
            ].map(([label, value]) => (
              <div key={label} className="rounded-lg border border-[#7E9FA8]/40 bg-[#7E9FA8]/5 px-3 py-2">
                <p className="text-[10px] uppercase text-slate-500 dark:text-stone-400">{label}</p>
                <p className="text-lg font-bold text-slate-900 dark:text-white">{value}</p>
              </div>
            ))}
          </div>

          {noPriceRows > 0 && noPriceReceivableRows !== noPriceRows && (
            <p className="text-xs text-slate-500 dark:text-stone-400">
              Sin precio recepcionables: {noPriceReceivableRows}; las restantes quedan fuera por revisión manual.
            </p>
          )}

          {newCopiesToReceive === 0 && alreadyReceivedRows > 0 && (
            <div className="rounded-xl border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-900/20 dark:text-amber-200">
              Este archivo exacto ya recibió sus copias físicas. No se crearán copias nuevas.
            </div>
          )}

          <div className="grid grid-cols-2 sm:grid-cols-4 xl:grid-cols-5 gap-2">
            {[
              ['Filas fuente', job.realRowsRead ?? job.totalRowsRead],
              ['Filas detectadas', job.rowsDetected ?? job.realRowsRead ?? job.totalRowsRead],
              ['Filas Discogs válidas', job.linksDetected],
              ['Ignoradas / no producto', job.ignored],
              ['Filas asociadas al catálogo', job.rowsImported],
              ['Copias importadas en esta carga', job.imported],
              ['Copias físicas importadas', job.physicalCopiesImported ?? job.imported],
              ['Productos de catálogo afectados', job.catalogProductsAffected],
              ['Productos nuevos', job.newProducts],
              ['Productos existentes reutilizados', job.existingProducts],
              ['Releases concretos resueltos', job.resolvedConcreteReleases],
              ['Copias físicas a recibir', newCopiesToReceive],
              ['Alertas técnicas históricas', job.rowsRequiringReview],
              ['Filas pendientes', job.pendingRows ?? job.pending],
              ['Filas con error', job.errorRows ?? job.failed],
              ['Metadata completa', job.rowsWithFullMetadata],
              ['Con advertencias', job.rowsWithWarnings],
              ['Imposibles de importar', job.rowsTechnicallyImpossible],
              ['Links detectados', job.linksDetected],
              ['Sin link Discogs', job.missingDiscogsLinks],
              ['Filas vacías ignoradas', job.blankRowsIgnored],
              ['Release IDs', job.validReleaseUrls],
              ['Master IDs', job.validMasterUrls],
              ['Estado Excel: vendidos', job.soldRows],
              ['Estado Excel: reservados', job.reservedRows],
              ['Metadata obtenida', `${job.metadataFetched || 0}/${job.linksDetected || 0}`],
              ['Metadata pendiente', job.metadataPending],
              ['Metadata fallida', job.metadataFailed],
              ['Portadas descargadas', `${job.coversDownloaded || 0}/${job.metadataFetched || 0}`],
              ['Portadas faltantes', job.coversMissing],
              ['YouTube encontrados', job.youtubeLinksFound],
              ['Tracks sin YouTube', job.youtubeTracksMissing],
              ['Identificables por importar', newCopiesToReceive],
              ['Ya importados', job.alreadyImported],
              ['QR creados', job.qrEntriesCreated],
              ['Advertencias', job.warnings],
            ].map(([label, value]) => (
              <div key={label} className="rounded-lg border border-slate-200 dark:border-stone-800 px-3 py-2">
                <p className="text-[10px] uppercase text-slate-400 dark:text-stone-500">{label}</p>
                <p className="text-lg font-bold text-slate-900 dark:text-white">{value || 0}</p>
              </div>
            ))}
          </div>

          {job.extraColumns?.length > 0 && (
            <p className="text-xs text-slate-500 dark:text-stone-400">
              Columnas no reconocidas (sus valores se conservaron en observaciones): {job.extraColumns.join(', ')}
            </p>
          )}

          {errorMsg && <p className="text-xs text-red-600 dark:text-red-400">{errorMsg}</p>}
          {job.errorMessage && (
            <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700 dark:border-red-800 dark:bg-red-900/20 dark:text-red-300">
              {job.errorMessage}
            </p>
          )}
          {job.rateLimited > 0 && (
            <div className="rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-800 dark:border-amber-800 dark:bg-amber-900/20 dark:text-amber-300">
              Discogs siguió limitando solicitudes después de los reintentos automáticos. Las filas afectadas pueden importarse con la información disponible y reintentarse después.
            </div>
          )}

          {job.zipStatus && job.zipStatus !== 'not_started' && (
            <div className="rounded-xl border border-slate-200 dark:border-stone-800 p-4 space-y-2">
              <div className="flex items-center justify-between text-xs">
                <span className="font-medium text-slate-700 dark:text-stone-200">
                  {job.zipReady ? 'ZIP listo' : job.zipStatus === 'failed' ? 'Falló la preparación del ZIP' : 'Preparando portadas ZIP…'}
                </span>
                <span>{job.zipProgressPercentage || 0}%</span>
              </div>
              <div className="h-2 rounded-full bg-slate-100 dark:bg-stone-800 overflow-hidden">
                <div className="h-full bg-[#5C7D87] transition-all" style={{ width: `${job.zipProgressPercentage || 0}%` }} />
              </div>
              <p className="text-xs text-slate-500 dark:text-stone-400">
                {job.zipProcessedCovers || 0} / {job.zipTotalCovers || 0} procesadas · {job.zipAddedCovers || 0} incluidas · {job.zipFailedCovers || 0} no disponibles
              </p>
              {job.zipCurrentRelease && <p className="text-xs text-slate-500 dark:text-stone-400">Actual: {job.zipCurrentRelease}</p>}
              {job.zipError && <p className="text-xs text-red-600 dark:text-red-400">{job.zipError}</p>}
            </div>
          )}

          <div className="flex flex-wrap gap-2">
            {[
              ['all', 'Todas'],
              ['available', 'Estado Excel: disponible'],
              ['sold', 'Estado Excel: vendido'],
              ['reserved', 'Estado Excel: reservado'],
              ['invalid', 'Sin link'],
              ['ignored', 'No producto'],
              ['pending', 'Metadata pendiente'],
              ['failed', 'Con fallas de enriquecimiento'],
              ['imported', 'Importadas'],
            ].map(([key, label]) => (
              <button key={key} onClick={() => setFilter(key)}
                className={`px-3 py-1.5 rounded-md border text-xs transition-colors
                  ${filter === key
                    ? 'border-[#5C7D87] bg-[#5C7D87] text-white'
                    : 'border-slate-200 dark:border-stone-700 text-slate-600 dark:text-stone-300 hover:bg-slate-50 dark:hover:bg-stone-900'}`}>
                {label}
              </button>
            ))}
          </div>

          <div className="overflow-x-auto rounded-xl border border-slate-200 dark:border-stone-800">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b border-slate-200 dark:border-stone-800 bg-slate-50 dark:bg-stone-950">
                  {['Fila', 'Portada', 'URL extraída', 'Fuente', 'Tipo / ID', 'Discogs', 'Metadata', 'Excel', 'Estado', 'Detalle', ''].map(h => (
                    <th key={h} className="text-left px-3 py-2 font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-stone-800">
                {filteredRows.map(row => (
                  <tr key={row.id} className={row.metadataStatus === 'failed' || ['failed', 'missing_local_file'].includes(row.coverStatus) ? 'bg-red-50 dark:bg-red-900/10' : ''}>
                    <td className="px-3 py-2 font-mono">{row.sourceExcelRowNumber}</td>
                    <td className="px-3 py-2">
                      {row.imageUrl ? (
                        <img src={row.imageUrl} alt="" loading="lazy"
                          className="w-10 h-10 rounded object-cover bg-slate-100 dark:bg-stone-800" />
                      ) : '—'}
                    </td>
                    <td className="px-3 py-2 max-w-[240px]">
                      {row.normalizedDiscogsUrl ? (
                        <a href={row.normalizedDiscogsUrl} target="_blank" rel="noreferrer"
                          className="text-[#5C7D87] dark:text-[#7E9FA8] hover:underline break-all">
                          {row.normalizedDiscogsUrl}
                        </a>
                      ) : '—'}
                    </td>
                    <td className="px-3 py-2">{urlSourceLabel(row.urlSource)}</td>
                    <td className="px-3 py-2 font-mono">
                      {row.discogsType ? `${row.discogsType}/${row.discogsId}` : '—'}
                      {row.masterId && row.resolvedReleaseId && (
                        <div className="text-[10px] text-slate-400">release/{row.resolvedReleaseId}</div>
                      )}
                    </td>
                    <td className="px-3 py-2">
                      <div className="font-medium text-slate-800 dark:text-stone-200">{row.artist || '—'}</div>
                      <div className="text-slate-500 dark:text-stone-500">{row.title || '—'}</div>
                    </td>
                    <td className="px-3 py-2 text-slate-500 dark:text-stone-500">
                      <div>{row.genre || row.manualGenre || '—'}</div>
                      <div>{row.style || '—'} · {row.format || '—'}</div>
                      <div>{row.country || '—'} · {row.label || '—'} · {row.year || '—'}</div>
                      <div className={row.youtubeLinksFound > 0 ? 'text-emerald-700 dark:text-emerald-300' : 'text-amber-700 dark:text-amber-300'}>
                        YouTube: {row.youtubeLinksFound > 0 ? `${row.youtubeLinksFound} link(s)` : 'sin link'}
                      </div>
                    </td>
                    <td className="px-3 py-2 text-slate-500 dark:text-stone-500">
                      <div>{row.manualCondition || '—'} · {row.manualPriceUyu != null ? `$${row.manualPriceUyu}` : `sin precio${row.rawPrice ? ` (${row.rawPrice})` : ''}`}</div>
                      <div className={row.normalizedPriceStatus === 'UNDEFINED' ? 'font-medium text-amber-700 dark:text-amber-300' : ''}>
                        Precio normalizado: {row.normalizedPriceStatus === 'UNDEFINED' ? 'SIN PRECIO' : row.normalizedPriceStatus || (row.manualPriceUyu != null ? 'DEFINED' : 'REQUIRES_REVIEW')}
                      </div>
                      <div>{row.manualGenre || '—'} · {row.sourceStatus || '—'}</div>
                      <div className="font-medium text-emerald-700 dark:text-emerald-300">Catálogo: USADO</div>
                      {row.internalCode && <div className="font-mono">{row.internalCode}</div>}
                      {row.observation && <div className="text-amber-700 dark:text-amber-300">Obs: {row.observation}</div>}
                    </td>
                    <td className="px-3 py-2">
                      <div className="font-medium">{statusLabel(row)}</div>
                      {row.resultingCopyState && <div>Copia resultante: {row.resultingCopyState}</div>}
                      <div className="text-[10px] text-slate-400">Metadata: {row.metadataStatus || '—'}</div>
                      <div className="text-[10px] text-slate-400">Portada: {row.coverStatus || '—'}</div>
                      <div className="text-[10px] text-slate-400">YouTube: {row.youtubeStatus || '—'}</div>
                      <div className="text-[10px] text-slate-400">Catálogo: {row.catalogImportStatus || '—'}</div>
                    </td>
                    <td className="px-3 py-2 text-slate-500 dark:text-stone-500 max-w-[240px]">
                      {row.previewReason || row.errorMessage || row.warningMessage || '—'}
                    </td>
                    <td className="px-3 py-2">
                      {(['failed', 'rate_limited', 'failed_retryable'].includes(row.metadataStatus)
                        || ['failed_retryable', 'missing_local_file'].includes(row.coverStatus)) && (
                        <button onClick={() => retryRow(row.id)}
                          className="text-[#5C7D87] dark:text-[#7E9FA8] hover:underline">
                          Reintentar
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {['completed', 'completed_with_warnings', 'completed_with_errors'].includes(job.status) && (
            <div className={`rounded-xl border p-4 text-xs ${job.warnings > 0
              ? 'border-amber-300 bg-amber-50 text-amber-900 dark:border-amber-800 dark:bg-amber-900/20 dark:text-amber-200'
              : 'border-emerald-300 bg-emerald-50 text-emerald-900 dark:border-emerald-800 dark:bg-emerald-900/20 dark:text-emerald-200'}`}>
              <p className="font-semibold">
                {(job.warnings > 0 || manualReviewRows > 0 || job.pendingRows > 0 || job.errorRows > 0)
                  ? 'Importación completada con elementos pendientes'
                  : 'Importación completada'}
              </p>
              <div className="mt-2 grid sm:grid-cols-2 gap-1">
                <span>✓ Filas significativas: {meaningfulRows}</span>
                <span>✓ Filas con identidad Discogs: {identityBearingRows}</span>
                <span>✓ Releases concretos únicos: {uniqueConcreteReleases}</span>
                <span>✓ Copias nuevas a recibir: {newCopiesToReceive}</span>
                <span>✓ Filas ya recibidas: {alreadyReceivedRows}</span>
                <span>✓ Copias disponibles: {availableCopiesToReceive}</span>
                <span>✓ Copias vendidas: {soldCopiesToReceive}</span>
                <span>✓ Filas SIN PRECIO: {noPriceRows}</span>
                <span>✓ SIN PRECIO recepcionables: {noPriceReceivableRows}</span>
                <span>✓ Revisión manual: {manualReviewRows}</span>
              </div>
              {(job.rows || []).some(row => row.errorMessage || row.warningMessage) && (
                <div className="mt-3 space-y-1">
                  <p className="font-semibold">Advertencias:</p>
                  {(job.rows || []).filter(row => row.errorMessage || row.warningMessage).slice(0, 10).map(row => (
                    <p key={row.id}>- Fila {row.sourceExcelRowNumber}: {row.errorMessage || row.warningMessage}</p>
                  ))}
                  {(job.rows || []).filter(row => row.errorMessage || row.warningMessage).length > 10 && (
                    <p>- Hay más advertencias disponibles en la tabla.</p>
                  )}
                </div>
              )}
            </div>
          )}

          <div className="flex flex-wrap gap-3">
            <button onClick={solicitarImportacion} disabled={!canConfirm || estado === 'saving'}
              className="px-5 py-2.5 rounded-lg bg-[#5C7D87] hover:bg-[#4a6a74] disabled:opacity-40 disabled:cursor-not-allowed text-white text-sm font-medium transition-colors">
              {estado === 'saving' ? 'Recibiendo…' : `Confirmar recepción (${newCopiesToReceive})`}
            </button>
            <button onClick={retryPending} disabled={processing || !(job.metadataPending || job.rateLimited || job.failed)}
              className="px-5 py-2.5 rounded-lg border border-[#7E9FA8]/50 text-[#5C7D87] dark:text-[#7E9FA8] text-sm font-medium disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
              Reintentar metadata pendiente
            </button>
            <button onClick={descargarPortadas}
              disabled={!job.metadataFetched || zipPreparing || downloadingZip}
              className="px-5 py-2.5 rounded-lg border border-[#7E9FA8]/50 text-[#5C7D87] dark:text-[#7E9FA8] text-sm font-medium disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
              {downloadingZip
                ? 'Descargando ZIP…'
                : zipPreparing
                  ? `Preparando ZIP (${job.zipProcessedCovers || 0}/${job.zipTotalCovers || 0})`
                  : job.zipReady
                    ? `Descargar ZIP (${job.zipAddedCovers || 0})`
                    : job.zipStatus === 'failed'
                      ? 'Reintentar preparación del ZIP'
                      : `Preparar portadas ZIP (${job.coversDownloaded || 0})`}
            </button>
            <button onClick={reset}
              className="px-5 py-2.5 rounded-lg border border-slate-200 dark:border-stone-700 text-slate-600 dark:text-stone-300 text-sm font-medium hover:bg-slate-50 dark:hover:bg-stone-900 transition-colors">
              Cancelar
            </button>
          </div>

          {job.status?.startsWith('completed') && (job.imported > 0 || job.alreadyImported > 0) && estado !== 'saving' && (
            <div className="rounded-xl border border-emerald-300 bg-emerald-50 px-4 py-3 text-sm text-emerald-900 dark:border-emerald-800 dark:bg-emerald-900/20 dark:text-emerald-200">
              Recepción completada: {job.imported || 0} copias nuevas; {job.alreadyImported || 0} ya recibidas.
            </div>
          )}
        </div>
      )}

      {estado === 'error' && (
        <div className="p-4 rounded-xl bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800">
          <p className="text-sm font-medium text-red-700 dark:text-red-400">Error</p>
          {errorMsg && <p className="text-xs text-red-600 dark:text-red-300 mt-1">{errorMsg}</p>}
          <button onClick={reset} className="mt-1 text-xs underline text-red-600 dark:text-red-400">Reintentar</button>
        </div>
      )}

      {confirmingImport && job && (
        <ImportConfirmationModal
          job={job}
          onConfirm={importarFilasIdentificables}
          onCancel={() => { if (estado !== 'saving') setConfirmingImport(false) }}
          saving={estado === 'saving'}
          error={confirmationError}
        />
      )}
    </div>
  )
}

export default function DiscogsTab() {
  return (
    <div className="space-y-8">
      <LinkSingle />
      <hr className="border-slate-200 dark:border-stone-800" />
      <ExcelLinks />
    </div>
  )
}
