import { useEffect, useRef, useState } from 'react'
import { api } from '../../api/sonograma'

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

function PreviewCard({ preview, onChange, onGuardar, saving }) {
  if (!preview) return null
  const tieneErrores = preview.errores?.length > 0

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

      <div className="grid grid-cols-2 gap-3">
        <div>
          <p className="text-xs text-slate-400 dark:text-stone-500 mb-1">Precio venta</p>
          <input
            type="number"
            className="input text-sm w-full"
            value={preview.precioVenta || ''}
            onChange={e => onChange({ ...preview, precioVenta: e.target.value ? Number(e.target.value) : null })}
            placeholder="$"
          />
        </div>
        <div>
          <p className="text-xs text-slate-400 dark:text-stone-500 mb-1">Condición</p>
          <select
            className="input text-sm w-full"
            value={preview.condicion || 'USADO'}
            onChange={e => onChange({ ...preview, condicion: e.target.value })}
          >
            {['NUEVO', 'USADO', 'CONSIGNACION', 'CATALOGO'].map(c => (
              <option key={c} value={c}>{c}</option>
            ))}
          </select>
        </div>
      </div>

      {preview.previewUrl && (
        <div>
          <p className="text-xs text-slate-400 dark:text-stone-500 mb-1">Preview de audio</p>
          <audio controls src={preview.previewUrl} className="w-full h-9" />
        </div>
      )}

      <button
        onClick={() => onGuardar(preview)}
        disabled={saving || tieneErrores || !preview.artista || !preview.album}
        className="w-full px-5 py-2.5 rounded-lg bg-[#5C7D87] hover:bg-[#4a6a74] disabled:opacity-40 disabled:cursor-not-allowed text-white text-sm font-medium transition-colors"
      >
        {saving ? 'Guardando…' : 'Guardar en catálogo'}
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

  async function fetchLink() {
    if (!url.trim()) return
    setEstado('loading')
    setErrorMsg('')
    try {
      const data = await api.importaciones.discogsDesdeLink(url.trim())
      setPreview(data)
      setEstado('preview')
    } catch (err) {
      setErrorMsg(err.message || 'Error al consultar Discogs')
      setEstado('error')
    }
  }

  async function guardar(p) {
    setEstado('saving')
    try {
      await api.importaciones.discogsGuardar(p)
      setEstado('done')
    } catch (err) {
      setErrorMsg(err.message || 'Error al guardar')
      setEstado('error')
    }
  }

  function reset() { setUrl(''); setPreview(null); setEstado('idle'); setErrorMsg('') }

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

      {(estado === 'preview' || estado === 'saving') && preview && (
        <PreviewCard
          preview={preview}
          onChange={setPreview}
          onGuardar={guardar}
          saving={estado === 'saving'}
        />
      )}

      {estado === 'done' && (
        <div className="p-4 rounded-xl bg-emerald-50 dark:bg-emerald-900/20 border border-emerald-200 dark:border-emerald-800">
          <p className="text-sm font-medium text-emerald-700 dark:text-emerald-400">✓ Disco guardado en el catálogo</p>
          <button onClick={reset} className="mt-1 text-xs underline text-emerald-600 dark:text-emerald-400">Buscar otro</button>
        </div>
      )}

      {estado === 'error' && (
        <div className="p-4 rounded-xl bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800">
          <p className="text-sm font-medium text-red-700 dark:text-red-400">Error</p>
          {errorMsg && <p className="text-xs text-red-600 dark:text-red-300 mt-1">{errorMsg}</p>}
          <button onClick={reset} className="mt-1 text-xs underline text-red-600 dark:text-red-400">Reintentar</button>
        </div>
      )}
    </div>
  )
}

// ── Sub-section B: Excel with Discogs links ───────────────────────────────────

function ExcelLinks() {
  const [archivo, setArchivo] = useState(null)
  const [estado, setEstado] = useState('idle')
  const [job, setJob] = useState(null)
  const [errorMsg, setErrorMsg] = useState('')
  const inputRef = useRef(null)

  const processing = job && ['pending', 'processing'].includes(job.status)
  const readyCount = job?.rows?.filter(row =>
    ['parsed', 'rate_limited'].includes(row.status) && !row.importedCatalogProductId
  ).length || 0

  useEffect(() => {
    if (!processing) return undefined
    const timer = window.setInterval(async () => {
      try {
        setJob(await api.importaciones.discogsJob(job.id))
      } catch (err) {
        setErrorMsg(err.message || 'No se pudo actualizar el progreso')
      }
    }, 1500)
    return () => window.clearInterval(timer)
  }, [job?.id, processing])

  async function fetchExcel() {
    if (!archivo) return
    setEstado('loading')
    setErrorMsg('')
    try {
      const data = await api.importaciones.discogsDesdeExcel(archivo)
      setJob(data)
      setEstado('preview')
    } catch (err) {
      setErrorMsg(err.message || 'Error al procesar Excel')
      setEstado('error')
    }
  }

  async function importarDisponibles() {
    setEstado('saving')
    try {
      setJob(await api.importaciones.discogsImportarJob(job.id))
      setEstado('preview')
    } catch (err) {
      setErrorMsg(err.message || 'Error al guardar')
      setEstado('error')
    }
  }

  async function retryRow(rowId) {
    try {
      setJob(await api.importaciones.discogsRetryRow(job.id, rowId))
      setEstado('preview')
    } catch (err) {
      setErrorMsg(err.message || 'No se pudo reintentar la fila')
    }
  }

  async function descargarPortadas() {
    try {
      const blob = await api.importaciones.discogsCoversZip(job.id)
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `discogs-covers-${job.id}.zip`
      link.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      setErrorMsg(err.message || 'No se pudo descargar el ZIP')
    }
  }

  function reset() {
    setArchivo(null); setJob(null)
    setEstado('idle'); setErrorMsg('')
    if (inputRef.current) inputRef.current.value = ''
  }

  return (
    <div className="space-y-4">
      <div>
        <h3 className="font-semibold text-slate-800 dark:text-stone-200 text-sm mb-1">Importar desde Excel con links de Discogs</h3>
        <p className="text-xs text-slate-500 dark:text-stone-400">
          Lee links visibles e hipervínculos embebidos de releases y masters. El procesamiento continúa en segundo plano.
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
                Hoja {job.nombreHoja} · Estado: {job.status}
              </p>
            </div>
            {processing && <Spinner text="Enriqueciendo…" />}
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
            {[
              ['Filas leídas', job.totalRowsRead],
              ['Release URLs', job.validReleaseUrls],
              ['Master URLs', job.validMasterUrls],
              ['Hipervínculos', job.embeddedHyperlinkRows],
              ['Match manual', job.needsManualMatch],
              ['Ignoradas', job.ignored],
              ['Fallidas', job.failed],
              ['Rate limited', job.rateLimited],
              ['Importadas', job.imported],
              ['Portadas', job.coversDownloaded],
              ['Pendientes', job.pending],
            ].map(([label, value]) => (
              <div key={label} className="rounded-lg border border-slate-200 dark:border-stone-800 px-3 py-2">
                <p className="text-[10px] uppercase tracking-wider text-slate-400 dark:text-stone-500">{label}</p>
                <p className="text-lg font-bold text-slate-900 dark:text-white">{value || 0}</p>
              </div>
            ))}
          </div>

          {errorMsg && <p className="text-xs text-red-600 dark:text-red-400">{errorMsg}</p>}
          {job.rateLimited > 0 && (
            <div className="rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-800 dark:border-amber-800 dark:bg-amber-900/20 dark:text-amber-300">
              Discogs limitó temporalmente algunas solicitudes. Esos ítems pueden importarse parcialmente y quedan pendientes de reintentar metadata.
            </div>
          )}

          <div className="overflow-x-auto rounded-xl border border-slate-200 dark:border-stone-800">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b border-slate-200 dark:border-stone-800 bg-slate-50 dark:bg-stone-950">
                  {['Fila', 'URL extraída', 'Fuente', 'Tipo / ID', 'Artista / Título', 'Estado', 'Detalle', ''].map(h => (
                    <th key={h} className="text-left px-3 py-2 font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-stone-800">
                {job.rows?.map(row => (
                  <tr key={row.id} className={['failed', 'rate_limited'].includes(row.status) ? 'bg-red-50 dark:bg-red-900/10' : ''}>
                    <td className="px-3 py-2 font-mono">{row.sourceExcelRowNumber}</td>
                    <td className="px-3 py-2 max-w-[240px]">
                      {row.normalizedDiscogsUrl ? (
                        <a href={row.normalizedDiscogsUrl} target="_blank" rel="noreferrer"
                          className="text-[#5C7D87] dark:text-[#7E9FA8] hover:underline break-all">
                          {row.normalizedDiscogsUrl}
                        </a>
                      ) : '—'}
                    </td>
                    <td className="px-3 py-2">{row.urlSource || '—'}</td>
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
                    <td className="px-3 py-2">{row.status}</td>
                    <td className="px-3 py-2 text-slate-500 dark:text-stone-500 max-w-[240px]">{row.errorMessage || '—'}</td>
                    <td className="px-3 py-2">
                      {['failed', 'rate_limited'].includes(row.status) && (
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
          <div className="flex gap-3">
            <button onClick={importarDisponibles} disabled={readyCount === 0 || processing || estado === 'saving'}
              className="px-5 py-2.5 rounded-lg bg-[#5C7D87] hover:bg-[#4a6a74] disabled:opacity-40 disabled:cursor-not-allowed text-white text-sm font-medium transition-colors">
              {estado === 'saving' ? 'Importando…' : `Importar al catálogo (${readyCount})`}
            </button>
            <button onClick={descargarPortadas} disabled={!job.coversDownloaded}
              className="px-5 py-2.5 rounded-lg border border-[#7E9FA8]/50 text-[#5C7D87] dark:text-[#7E9FA8] text-sm font-medium disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
              Descargar portadas ZIP ({job.coversDownloaded || 0})
            </button>
            <button onClick={reset}
              className="px-5 py-2.5 rounded-lg border border-slate-200 dark:border-stone-700 text-slate-600 dark:text-stone-300 text-sm font-medium hover:bg-slate-50 dark:hover:bg-stone-900 transition-colors">
              Cancelar
            </button>
          </div>
        </div>
      )}

      {estado === 'error' && (
        <div className="p-4 rounded-xl bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800">
          <p className="text-sm font-medium text-red-700 dark:text-red-400">Error</p>
          {errorMsg && <p className="text-xs text-red-600 dark:text-red-300 mt-1">{errorMsg}</p>}
          <button onClick={reset} className="mt-1 text-xs underline text-red-600 dark:text-red-400">Reintentar</button>
        </div>
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
