import { useRef, useState } from 'react'
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
  const [previews, setPreviews] = useState([])
  const [seleccionados, setSeleccionados] = useState(new Set())
  const [errorMsg, setErrorMsg] = useState('')
  const [resultado, setResultado] = useState(null)
  const inputRef = useRef(null)

  async function fetchExcel() {
    if (!archivo) return
    setEstado('loading')
    setErrorMsg('')
    try {
      const data = await api.importaciones.discogsDesdeExcel(archivo)
      setPreviews(data)
      setSeleccionados(new Set(
        data.map((_, i) => i).filter(i => !data[i].errores?.length)
      ))
      setEstado('preview')
    } catch (err) {
      setErrorMsg(err.message || 'Error al procesar Excel')
      setEstado('error')
    }
  }

  async function guardarLote() {
    const lista = previews.filter((_, i) => seleccionados.has(i))
    setEstado('saving')
    try {
      const data = await api.importaciones.discogsGuardarLote(lista)
      setResultado(data)
      setEstado('done')
    } catch (err) {
      setErrorMsg(err.message || 'Error al guardar')
      setEstado('error')
    }
  }

  function toggleRow(i) {
    setSeleccionados(prev => { const s = new Set(prev); s.has(i) ? s.delete(i) : s.add(i); return s })
  }

  function reset() {
    setArchivo(null); setPreviews([]); setSeleccionados(new Set())
    setEstado('idle'); setErrorMsg(''); setResultado(null)
    if (inputRef.current) inputRef.current.value = ''
  }

  return (
    <div className="space-y-4">
      <div>
        <h3 className="font-semibold text-slate-800 dark:text-stone-200 text-sm mb-1">Importar desde Excel con links de Discogs</h3>
        <p className="text-xs text-slate-500 dark:text-stone-400">
          El Excel debe tener una columna llamada <code className="font-mono bg-slate-100 dark:bg-stone-800 px-1 rounded">Link</code>, <code className="font-mono bg-slate-100 dark:bg-stone-800 px-1 rounded">URL</code> o <code className="font-mono bg-slate-100 dark:bg-stone-800 px-1 rounded">Discogs</code> con las URLs de los releases.
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

      {estado === 'loading' && <Spinner text="Consultando Discogs API por cada link… esto puede tardar." />}

      {estado === 'preview' && previews.length > 0 && (
        <div className="space-y-3">
          <p className="text-sm text-slate-600 dark:text-stone-400">
            {previews.length} resultados — {seleccionados.size} sin errores seleccionados
          </p>
          <div className="overflow-x-auto rounded-xl border border-slate-200 dark:border-stone-800">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b border-slate-200 dark:border-stone-800 bg-slate-50 dark:bg-stone-950">
                  <th className="w-8 px-3 py-2"></th>
                  {['Artista', 'Álbum', 'Año', 'Género', 'Sello', 'Estado'].map(h => (
                    <th key={h} className="text-left px-3 py-2 font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-stone-800">
                {previews.map((p, i) => (
                  <tr key={i} className={p.errores?.length ? 'bg-red-50 dark:bg-red-900/10' : ''}>
                    <td className="px-3 py-2">
                      <input type="checkbox" checked={seleccionados.has(i)} disabled={!!p.errores?.length}
                        onChange={() => toggleRow(i)}
                        className="rounded border-slate-300 text-[#5C7D87] disabled:opacity-30" />
                    </td>
                    <td className="px-3 py-2 font-medium text-slate-800 dark:text-stone-200">
                      {p.artista || <span className="text-red-400 text-xs">{p.errores?.[0] || 'Error'}</span>}
                    </td>
                    <td className="px-3 py-2 text-slate-600 dark:text-stone-400">{p.album || '—'}</td>
                    <td className="px-3 py-2 text-slate-500 dark:text-stone-500">{p.anio || '—'}</td>
                    <td className="px-3 py-2 text-slate-500 dark:text-stone-500">{p.genero || '—'}</td>
                    <td className="px-3 py-2 text-slate-500 dark:text-stone-500 max-w-[120px] truncate">{p.sello || '—'}</td>
                    <td className="px-3 py-2 text-slate-500 dark:text-stone-500">{p.estado || 'DISPONIBLE'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="flex gap-3">
            <button onClick={guardarLote} disabled={seleccionados.size === 0}
              className="px-5 py-2.5 rounded-lg bg-[#5C7D87] hover:bg-[#4a6a74] disabled:opacity-40 disabled:cursor-not-allowed text-white text-sm font-medium transition-colors">
              Guardar seleccionados ({seleccionados.size})
            </button>
            <button onClick={reset}
              className="px-5 py-2.5 rounded-lg border border-slate-200 dark:border-stone-700 text-slate-600 dark:text-stone-300 text-sm font-medium hover:bg-slate-50 dark:hover:bg-stone-900 transition-colors">
              Cancelar
            </button>
          </div>
        </div>
      )}

      {estado === 'saving' && <Spinner text="Guardando en el catálogo…" />}

      {estado === 'done' && resultado && (
        <div className="p-4 rounded-xl bg-emerald-50 dark:bg-emerald-900/20 border border-emerald-200 dark:border-emerald-800">
          <p className="text-sm font-medium text-emerald-700 dark:text-emerald-400">✓ {resultado.length} discos importados</p>
          <button onClick={reset} className="mt-1 text-xs underline text-emerald-600 dark:text-emerald-400">Nueva importación</button>
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
