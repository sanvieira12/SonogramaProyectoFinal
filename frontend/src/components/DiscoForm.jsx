import { useState } from 'react'

const CONDICIONES = ['NUEVO', 'USADO', 'CONSIGNACION', 'CATALOGO']
const TIPOS = ['VINILO', 'CD', 'DIGITAL']
const ESTADOS = ['DISPONIBLE', 'RESERVADO', 'VENDIDO', 'DESCONTINUADO']

const VACIO = {
  codigoInterno: '', artista: '', album: '', genero: '', anio: '',
  condicion: 'NUEVO', tipoDisco: 'VINILO', costo: '', precioVenta: '', estado: 'DISPONIBLE',
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
          anio: disco.anio || '',
          condicion: disco.condicion || 'NUEVO',
          tipoDisco: disco.tipoDisco || 'VINILO',
          costo: disco.costo || '',
          precioVenta: disco.precioVenta || '',
          estado: disco.estado || 'DISPONIBLE',
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
        className="bg-white dark:bg-gray-900 rounded-2xl shadow-2xl border border-slate-200 dark:border-gray-700 w-full max-w-lg my-auto"
        onClick={e => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 dark:border-gray-800">
          <div>
            <h2 className="text-slate-900 dark:text-white font-bold text-base">
              {esEdicion ? 'Editar disco' : 'Agregar disco'}
            </h2>
            <p className="text-slate-400 dark:text-gray-500 text-xs mt-0.5">
              {esEdicion ? 'Modificá los datos del registro' : 'Completá los datos del nuevo registro'}
            </p>
          </div>
          <button
            onClick={onCancelar}
            className="p-1.5 rounded-lg text-slate-400 dark:text-gray-500 hover:text-slate-600 dark:hover:text-gray-300 hover:bg-slate-100 dark:hover:bg-gray-800 transition-colors"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18 18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-gray-400 mb-1.5 uppercase tracking-wide">
                Artista <span className="text-red-400">*</span>
              </label>
              <input value={form.artista} onChange={e => set('artista', e.target.value)} className="input" placeholder="Pink Floyd" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-gray-400 mb-1.5 uppercase tracking-wide">
                Álbum <span className="text-red-400">*</span>
              </label>
              <input value={form.album} onChange={e => set('album', e.target.value)} className="input" placeholder="The Wall" />
            </div>
          </div>

          <div className="grid grid-cols-3 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-gray-400 mb-1.5 uppercase tracking-wide">Código</label>
              <input value={form.codigoInterno} onChange={e => set('codigoInterno', e.target.value)} className="input" placeholder="ABC001" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-gray-400 mb-1.5 uppercase tracking-wide">Género</label>
              <input value={form.genero} onChange={e => set('genero', e.target.value)} className="input" placeholder="Techno" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-gray-400 mb-1.5 uppercase tracking-wide">Año</label>
              <input value={form.anio} onChange={e => set('anio', e.target.value)} className="input" placeholder="1994" type="number" min="1900" max="2099" />
            </div>
          </div>

          <div className="grid grid-cols-3 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-gray-400 mb-1.5 uppercase tracking-wide">Condición</label>
              <select value={form.condicion} onChange={e => set('condicion', e.target.value)} className="input">
                {CONDICIONES.map(c => <option key={c}>{c}</option>)}
              </select>
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-gray-400 mb-1.5 uppercase tracking-wide">Tipo</label>
              <select value={form.tipoDisco} onChange={e => set('tipoDisco', e.target.value)} className="input">
                {TIPOS.map(t => <option key={t}>{t}</option>)}
              </select>
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-gray-400 mb-1.5 uppercase tracking-wide">Estado</label>
              <select value={form.estado} onChange={e => set('estado', e.target.value)} className="input">
                {ESTADOS.map(s => <option key={s}>{s}</option>)}
              </select>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-gray-400 mb-1.5 uppercase tracking-wide">Costo (EUR)</label>
              <input value={form.costo} onChange={e => set('costo', e.target.value)} className="input" placeholder="0.00" type="number" step="0.01" min="0" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-gray-400 mb-1.5 uppercase tracking-wide">Precio venta (UYU)</label>
              <input value={form.precioVenta} onChange={e => set('precioVenta', e.target.value)} className="input" placeholder="0.00" type="number" step="0.01" min="0" />
            </div>
          </div>

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
