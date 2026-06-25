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

// ── Sub-section B: existing PDF → ZIP export ─────────────────────────────────

function PdfExport() {
  const [archivo, setArchivo] = useState(null)
  const [estado, setEstado] = useState('idle')
  const [resumen, setResumen] = useState(null)
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

  const etapa = segundos < 3
    ? 'Leyendo factura y precios…'
    : segundos < 12
      ? 'Buscando portadas y metadata en paralelo…'
      : 'Preparando archivos y guardando el pedido…'

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
    setEstado('loading')
    setErrorMsg('')
    try {
      setResumen(await api.importar.vinylfutureCatalogo(archivo))
      setEstado('done')
    } catch (err) {
      setErrorMsg(err.message || 'Error al procesar el PDF')
      setEstado('error')
    }
  }

  async function exportarZip() {
    setExportSegundos(0)
    setExportando(true)
    setErrorMsg('')
    try {
      const blob = resumen?.importId
        ? await api.importar.vinylfutureZip(resumen.importId)
        : await api.importar.vinylfutureCsv(archivo)
      const url = URL.createObjectURL(blob)
      const ts = new Date().toISOString().replace(/[:T]/g, '-').slice(0, 16)
      const link = document.createElement('a')
      link.href = url
      link.download = `vinylfuture-export-${ts}.zip`
      link.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      setErrorMsg(err.message || 'Error al exportar el ZIP')
    } finally {
      setExportando(false)
    }
  }

  function reset() {
    setArchivo(null); setResumen(null)
    setEstado('idle'); setErrorMsg(''); setSegundos(0); setExportSegundos(0)
  }

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
            Procesar PDF
          </button>
        </>
      )}
      {estado === 'loading' && (
        <div className="rounded-xl border border-[#7E9FA8]/20 bg-[#7E9FA8]/5 px-4 py-3">
          <Spinner text={etapa} />
          <div className="h-1.5 overflow-hidden rounded-full bg-slate-200 dark:bg-stone-800">
            <div className="h-full w-1/3 animate-pulse rounded-full bg-[#7E9FA8]" />
          </div>
          <p className="mt-2 text-center text-xs text-slate-400 dark:text-stone-500">
            {segundos}s · podés dejar esta pantalla abierta mientras termina
          </p>
        </div>
      )}
      {estado === 'done' && (
        <div className="p-4 rounded-xl bg-emerald-50 dark:bg-emerald-900/20 border border-emerald-200 dark:border-emerald-800">
          <p className="font-medium text-emerald-700 dark:text-emerald-400 text-sm mb-3">
            Importación completada: {resumen?.recordsImported || 0} discos agregados al catálogo
          </p>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 mb-4">
            {[
              ['Detectados', resumen?.recordsDetected],
              ['Importados', resumen?.recordsImported],
              ['Portadas encontradas', resumen?.coversFound],
              ['Portadas descargadas', resumen?.coversDownloaded],
              ['MP3 encontrados', resumen?.mp3PreviewsFound],
              ['MP3 descargados', resumen?.mp3Downloaded],
              ['Media fallida', resumen?.failedMediaDownloads],
              ['YouTube', resumen?.youtubeLinksFound],
              ['QR creados', resumen?.qrEntriesCreated],
              ['Duplicados', resumen?.skippedDuplicates],
              ['Links fallidos', resumen?.failedLinks],
              ['Rate limit', resumen?.rateLimitFailures],
            ].map(([label, value]) => (
              <div key={label} className="rounded-lg border border-emerald-200 dark:border-emerald-800 px-3 py-2">
                <p className="text-[10px] uppercase tracking-wider text-emerald-600/70 dark:text-emerald-500">{label}</p>
                <p className="text-lg font-bold text-emerald-800 dark:text-emerald-300">{value || 0}</p>
              </div>
            ))}
          </div>
          {resumen?.failedLinkDetails?.length > 0 && (
            <p className="mb-3 text-xs text-amber-700 dark:text-amber-300">
              Sin metadata externa: {resumen.failedLinkDetails.join(', ')}
            </p>
          )}
          <button onClick={exportarZip} disabled={exportando}
            className="inline-flex items-center gap-2 px-4 py-2 rounded-lg border border-emerald-500 text-emerald-700 dark:text-emerald-300 text-sm font-medium disabled:opacity-50">
            {exportando ? 'Preparando ZIP…' : 'Exportar CSV, portadas y audio (opcional)'}
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
  return (
    <div className="space-y-8">
      <PdfExport />
    </div>
  )
}
