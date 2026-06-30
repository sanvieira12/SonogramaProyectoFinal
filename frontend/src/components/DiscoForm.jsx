import { useEffect, useRef, useState } from 'react'
import { api, resolveApiUrl } from '../api/sonograma'
import CompactPlayer from './CompactPlayer'
import { stopAllPreviews } from './audioPreviewPlayback'

const CONDICIONES = ['NUEVO', 'USADO', 'CONSIGNACION', 'CATALOGO']
const TIPOS = ['VINILO', 'CD', 'DIGITAL', 'CASSETTE', 'OTRO']
const ESTADOS = ['DISPONIBLE', 'RESERVADO', 'VENDIDO', 'FUERA_STOCK', 'DESCONTINUADO']

const VACIO = {
  codigoInterno: '', artista: '', album: '', genero: '', selloDiscografico: '', descripcion: '', anio: '',
  condicion: 'NUEVO', tipoDisco: 'VINILO', formato: '', costo: '', precioVenta: '', pricingMode: 'AUTO', estado: 'DISPONIBLE', imagenUrl: '',
}

function resizarBase64(file, maxPx = 400) {
  return new Promise((resolve) => {
    const reader = new FileReader()
    reader.onload = (ev) => {
      const img = new Image()
      img.onload = () => {
        const scale = Math.min(1, maxPx / Math.max(img.width, img.height))
        const canvas = document.createElement('canvas')
        canvas.width = Math.round(img.width * scale)
        canvas.height = Math.round(img.height * scale)
        canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height)
        resolve(canvas.toDataURL('image/jpeg', 0.85))
      }
      img.src = ev.target.result
    }
    reader.readAsDataURL(file)
  })
}

function CoverUpload({ value, onChange }) {
  const fileRef = useRef(null)
  const isBase64 = value?.startsWith('data:')

  async function handleFile(e) {
    const file = e.target.files[0]
    if (!file) return
    const b64 = await resizarBase64(file)
    onChange(b64)
    e.target.value = ''
  }

  return (
    <div className="space-y-2">
      <div className="flex gap-2">
        <input
          type="url"
          className="input flex-1"
          value={isBase64 ? '' : (value || '')}
          onChange={e => onChange(e.target.value)}
          placeholder="https://... o subí un archivo →"
        />
        <button
          type="button"
          onClick={() => fileRef.current?.click()}
          className="px-3 py-2 rounded-lg text-xs bg-slate-100 dark:bg-stone-900 text-slate-600 dark:text-stone-400 hover:bg-slate-200 dark:hover:bg-stone-700 transition-colors whitespace-nowrap"
        >
          {isBase64 ? 'Cambiar' : 'Subir'}
        </button>
        {value && (
          <button
            type="button"
            onClick={() => onChange('')}
            className="px-2 py-2 rounded-lg text-xs text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors"
          >
            ✕
          </button>
        )}
        <input ref={fileRef} type="file" accept="image/*" className="hidden" onChange={handleFile} />
      </div>
      {value && (
        <img src={resolveApiUrl(value)} alt="Portada" className="h-16 w-16 object-cover rounded-lg border border-slate-200 dark:border-stone-700" />
      )}
    </div>
  )
}

function AudioPreviewsSection({ discoId, initialPreviews = [] }) {
  const [previews, setPreviews] = useState(initialPreviews)
  const [newUrl, setNewUrl] = useState('')
  const [newYoutubeUrl, setNewYoutubeUrl] = useState('')
  const [newName, setNewName] = useState('')
  const [newPos, setNewPos] = useState('')
  const [adding, setAdding] = useState(false)
  const [err, setErr] = useState('')

  useEffect(() => {
    let cancelled = false
    api.discos.previews.listar(discoId)
      .then(d => { if (!cancelled) setPreviews(d) })
      .catch(ex => { if (!cancelled) setErr(ex.message) })
    return () => {
      cancelled = true
      stopAllPreviews()
    }
  }, [discoId])

  async function handleAdd(e) {
    e.preventDefault()
    if (!newUrl.trim() && !newYoutubeUrl.trim()) return
    setAdding(true)
    setErr('')
    try {
      const added = await api.discos.previews.agregar(discoId, {
        audioUrl: newUrl.trim(),
        youtubeUrl: newYoutubeUrl.trim(),
        trackName: newName.trim() || null,
        trackPosition: newPos.trim() || null,
        durationSeconds: null,
      })
      setPreviews(p => [...p, added])
      setNewUrl(''); setNewYoutubeUrl(''); setNewName(''); setNewPos('')
    } catch (ex) {
      setErr(ex.message)
    } finally {
      setAdding(false)
    }
  }

  async function handleDelete(previewId) {
    stopAllPreviews()
    try {
      await api.discos.previews.eliminar(discoId, previewId)
      setPreviews(p => p.filter(x => x.id !== previewId))
    } catch (ex) {
      setErr(ex.message)
    }
  }

  return (
    <div className="border-t border-slate-100 dark:border-stone-800 pt-4 space-y-3">
      <p className="text-xs font-semibold text-slate-600 dark:text-stone-400 uppercase tracking-wide">Audio previews</p>

      {previews.length === 0 && (
        <p className="text-xs text-slate-400 dark:text-stone-600">Sin previews de audio.</p>
      )}

      <div className="space-y-1.5 max-h-56 overflow-y-auto pr-1">
        {previews.map(p => (
          <div key={p.id} className="flex items-center gap-2">
            <div className="flex-1 min-w-0">
              <CompactPlayer audioUrl={p.audioUrl} youtubeUrl={p.youtubeUrl} trackName={p.trackName} trackPosition={p.trackPosition} />
            </div>
            <button
              type="button"
              onClick={() => handleDelete(p.id)}
              className="w-7 h-7 rounded-lg flex items-center justify-center text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors flex-shrink-0"
              title="Eliminar preview"
            >
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18 18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        ))}
      </div>

      <form onSubmit={handleAdd} className="space-y-2">
        <input
          type="url"
          className="input text-xs"
          value={newUrl}
          onChange={e => setNewUrl(e.target.value)}
          placeholder="https://... URL del MP3"
        />
        <input
          type="url"
          className="input text-xs"
          value={newYoutubeUrl}
          onChange={e => setNewYoutubeUrl(e.target.value)}
          placeholder="https://youtube.com/... (si no hay MP3)"
        />
        <div className="flex gap-2">
          <input
            className="input text-xs flex-1"
            value={newPos}
            onChange={e => setNewPos(e.target.value)}
            placeholder="Posición (ej. A1)"
          />
          <input
            className="input text-xs flex-1"
            value={newName}
            onChange={e => setNewName(e.target.value)}
            placeholder="Nombre del track"
          />
          <button
            type="submit"
            disabled={adding || (!newUrl.trim() && !newYoutubeUrl.trim())}
            className="px-3 py-2 rounded-lg text-xs bg-[#7E9FA8]/20 hover:bg-[#7E9FA8]/30 text-[#7E9FA8] border border-[#7E9FA8]/30 transition-colors disabled:opacity-40 whitespace-nowrap"
          >
            {adding ? '...' : 'Agregar'}
          </button>
        </div>
        {err && <p className="text-xs text-red-400">{err}</p>}
      </form>
    </div>
  )
}

export default function DiscoForm({ disco, onGuardar, onCancelar }) {
  const esEdicion = Boolean(disco)
  const [form, setForm] = useState(
    disco
      ? {
          codigoInterno: disco.codigoInterno || '',
          artista: disco.artista || '',
          album: disco.album || '',
          genero: disco.genero || '',
          selloDiscografico: disco.selloDiscografico || '',
          descripcion: disco.descripcion || '',
          anio: disco.anio || '',
          condicion: disco.condicion || 'NUEVO',
          tipoDisco: disco.tipoDisco || 'VINILO',
          formato: disco.formato || '',
          costo: disco.costo || '',
          precioVenta: disco.precioVenta || '',
          pricingMode: disco.pricingMode || 'AUTO',
          estado: disco.estado || 'DISPONIBLE',
          imagenUrl: disco.imagenUrl || '',
        }
      : VACIO
  )
  const [error, setError] = useState('')
  const [cargando, setCargando] = useState(false)

  function set(field, value) {
    setForm(f => ({ ...f, [field]: value }))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!form.artista.trim()) { setError('El artista es obligatorio'); return }
    if (!form.album.trim()) { setError('El álbum es obligatorio'); return }

    setError('')
    setCargando(true)
    try {
      const payload = {
        ...form,
        anio: form.anio ? parseInt(form.anio) : null,
        costo: form.costo ? parseFloat(form.costo) : null,
        precioVenta: form.precioVenta ? parseFloat(form.precioVenta) : null,
      }
      const initialPrice = disco?.precioVenta != null ? Number(disco.precioVenta) : null
      const nextPrice = form.precioVenta !== '' ? Number(form.precioVenta) : null
      payload.pricingMode = form.pricingMode || 'AUTO'
      if ((nextPrice != null && initialPrice == null) || (nextPrice != null && initialPrice != null && nextPrice !== initialPrice)) {
        payload.pricingMode = 'MANUAL'
      }
      await onGuardar(payload)
    } catch (err) {
      setError(err.message)
    } finally {
      setCargando(false)
    }
  }

  return (
    <div
      className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 px-4 py-6 overflow-y-auto"
      onClick={onCancelar}
    >
      <div
        className="bg-white dark:bg-stone-900 rounded-2xl shadow-2xl border border-slate-200 dark:border-stone-700 w-full max-w-lg my-auto"
        onClick={e => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 dark:border-stone-800">
          <div>
            <h2 className="text-slate-900 dark:text-white font-bold text-base">
              {esEdicion ? 'Editar disco' : 'Agregar disco'}
            </h2>
            <p className="text-slate-400 dark:text-stone-500 text-xs mt-0.5">
              {esEdicion ? 'Modificá los datos del registro' : 'Completá los datos del nuevo registro'}
            </p>
          </div>
          <button
            onClick={onCancelar}
            className="p-1.5 rounded-lg text-slate-400 dark:text-stone-500 hover:text-slate-600 dark:hover:text-stone-300 hover:bg-slate-100 dark:hover:bg-gray-800 transition-colors"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18 18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-stone-400 mb-1.5 uppercase tracking-wide">
                Artista <span className="text-red-400">*</span>
              </label>
              <input value={form.artista} onChange={e => set('artista', e.target.value)} className="input" placeholder="Pink Floyd" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-stone-400 mb-1.5 uppercase tracking-wide">
                Álbum <span className="text-red-400">*</span>
              </label>
              <input value={form.album} onChange={e => set('album', e.target.value)} className="input" placeholder="The Wall" />
            </div>
          </div>

          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-stone-400 mb-1.5 uppercase tracking-wide">Código</label>
              <input value={form.codigoInterno} onChange={e => set('codigoInterno', e.target.value)} className="input" placeholder="ABC001" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-stone-400 mb-1.5 uppercase tracking-wide">Género</label>
              <input value={form.genero} onChange={e => set('genero', e.target.value)} className="input" placeholder="Techno" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-stone-400 mb-1.5 uppercase tracking-wide">Año</label>
              <input value={form.anio} onChange={e => set('anio', e.target.value)} className="input" placeholder="1994" type="number" min="1900" max="2099" />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-stone-400 mb-1.5 uppercase tracking-wide">Sello discográfico</label>
              <input value={form.selloDiscografico} onChange={e => set('selloDiscografico', e.target.value)} className="input" placeholder="Warp Records" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-stone-400 mb-1.5 uppercase tracking-wide">Descripción</label>
              <input value={form.descripcion} onChange={e => set('descripcion', e.target.value)} className="input" placeholder="Edición, notas, palabras clave..." />
            </div>
          </div>

          <div className="grid grid-cols-3 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-stone-400 mb-1.5 uppercase tracking-wide">Condición</label>
              <select value={form.condicion} onChange={e => set('condicion', e.target.value)} className="input">
                {CONDICIONES.map(c => <option key={c}>{c}</option>)}
              </select>
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-stone-400 mb-1.5 uppercase tracking-wide">Tipo</label>
              <select value={form.tipoDisco} onChange={e => set('tipoDisco', e.target.value)} className="input">
                {TIPOS.map(t => <option key={t}>{t}</option>)}
              </select>
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-stone-400 mb-1.5 uppercase tracking-wide">Formato</label>
              <input value={form.formato} onChange={e => set('formato', e.target.value)} className="input" placeholder='LP, 2x12", 3LP, Box Set' />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-stone-400 mb-1.5 uppercase tracking-wide">Estado</label>
              <select value={form.estado} onChange={e => set('estado', e.target.value)} className="input">
                {ESTADOS.map(s => <option key={s}>{s}</option>)}
              </select>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-stone-400 mb-1.5 uppercase tracking-wide">Costo (EUR)</label>
              <input value={form.costo} onChange={e => set('costo', e.target.value)} className="input" placeholder="0.00" type="number" step="0.01" min="0" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-stone-400 mb-1.5 uppercase tracking-wide">Precio venta (UYU)</label>
              <input value={form.precioVenta} onChange={e => set('precioVenta', e.target.value)} className="input" placeholder="0.00" type="number" step="0.01" min="0" />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-600 dark:text-stone-400 mb-1.5 uppercase tracking-wide">Portada</label>
            <CoverUpload value={form.imagenUrl} onChange={v => set('imagenUrl', v)} />
          </div>

          {esEdicion && disco.idDisco && (
            <AudioPreviewsSection discoId={disco.idDisco} initialPreviews={disco.audioPreviews || []} />
          )}

          {error && (
            <div className="bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-800 text-red-600 dark:text-red-300 text-sm rounded-lg px-4 py-3">
              {error}
            </div>
          )}

          <div className="flex gap-3 pt-1">
            <button type="button" onClick={onCancelar} className="btn-secondary flex-1 py-2.5">Cancelar</button>
            <button type="submit" disabled={cargando} className="btn-primary flex-1 py-2.5">
              {cargando ? 'Guardando...' : esEdicion ? 'Guardar cambios' : 'Agregar disco'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
