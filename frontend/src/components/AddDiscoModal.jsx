import { useState } from 'react'
import { api } from '../api/sonograma'

const CONDICIONES = ['NUEVO', 'USADO', 'CONSIGNACION', 'CATALOGO']
const TIPOS = ['VINILO', 'CD', 'DIGITAL', 'CASSETTE', 'OTRO']

function XIcon() {
  return (
    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M6 18 18 6M6 6l12 12" />
    </svg>
  )
}

export default function AddDiscoModal({ onClose, onCreado }) {
  const [form, setForm] = useState({
    artista: '', album: '', genero: '', anio: '',
    condicion: 'NUEVO', tipoDisco: 'VINILO',
    precioVenta: '', costo: '',
  })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  function set(field, value) {
    setForm(f => ({ ...f, [field]: value }))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const payload = {
        ...form,
        anio: form.anio ? parseInt(form.anio) : null,
        precioVenta: form.precioVenta ? parseFloat(form.precioVenta) : null,
        costo: form.costo ? parseFloat(form.costo) : null,
      }
      const disco = await api.discos.crear(payload)
      onCreado(disco)
      onClose()
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 px-4"
      onClick={onClose}
    >
      <div
        className="bg-white dark:bg-stone-900 rounded-2xl shadow-2xl border border-slate-200 dark:border-stone-700 w-full max-w-lg"
        onClick={e => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 dark:border-stone-800">
          <div>
            <h2 className="text-slate-900 dark:text-white font-bold text-base">Agregar disco</h2>
            <p className="text-slate-400 dark:text-stone-500 text-xs mt-0.5">Completá los datos del nuevo registro</p>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 dark:text-stone-500 hover:text-slate-600 dark:hover:text-gray-300 hover:bg-slate-100 dark:hover:bg-stone-800 transition-colors"
          >
            <XIcon />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">

          {/* Artista / Álbum */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-stone-400 mb-1.5 uppercase tracking-wide">
                Artista <span className="text-red-400">*</span>
              </label>
              <input
                value={form.artista}
                onChange={e => set('artista', e.target.value)}
                className="input"
                placeholder="Pink Floyd"
                required
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-stone-400 mb-1.5 uppercase tracking-wide">
                Álbum <span className="text-red-400">*</span>
              </label>
              <input
                value={form.album}
                onChange={e => set('album', e.target.value)}
                className="input"
                placeholder="The Wall"
                required
              />
            </div>
          </div>

          {/* Género / Año / Tipo */}
          <div className="grid grid-cols-3 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-stone-400 mb-1.5 uppercase tracking-wide">Género</label>
              <input
                value={form.genero}
                onChange={e => set('genero', e.target.value)}
                className="input"
                placeholder="Rock"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-stone-400 mb-1.5 uppercase tracking-wide">Año</label>
              <input
                value={form.anio}
                onChange={e => set('anio', e.target.value)}
                className="input"
                placeholder="1979"
                type="number"
                min="1900"
                max="2099"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-stone-400 mb-1.5 uppercase tracking-wide">Tipo</label>
              <select value={form.tipoDisco} onChange={e => set('tipoDisco', e.target.value)} className="input">
                {TIPOS.map(t => <option key={t}>{t}</option>)}
              </select>
            </div>
          </div>

          {/* Condición / Costo / Precio */}
          <div className="grid grid-cols-3 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-stone-400 mb-1.5 uppercase tracking-wide">Condición</label>
              <select value={form.condicion} onChange={e => set('condicion', e.target.value)} className="input">
                {CONDICIONES.map(c => <option key={c}>{c}</option>)}
              </select>
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-stone-400 mb-1.5 uppercase tracking-wide">Costo $</label>
              <input
                value={form.costo}
                onChange={e => set('costo', e.target.value)}
                className="input"
                placeholder="0.00"
                type="number"
                step="0.01"
                min="0"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-600 dark:text-stone-400 mb-1.5 uppercase tracking-wide">Precio venta $</label>
              <input
                value={form.precioVenta}
                onChange={e => set('precioVenta', e.target.value)}
                className="input"
                placeholder="0.00"
                type="number"
                step="0.01"
                min="0"
              />
            </div>
          </div>

          {error && (
            <div className="bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-800 text-red-600 dark:text-red-300 text-sm rounded-lg px-4 py-3">
              {error}
            </div>
          )}

          <div className="flex gap-3 pt-1">
            <button type="button" onClick={onClose} className="btn-secondary flex-1 py-2.5">
              Cancelar
            </button>
            <button type="submit" disabled={loading} className="btn-primary flex-1 py-2.5">
              {loading ? (
                <span className="flex items-center justify-center gap-2">
                  <svg className="animate-spin w-4 h-4" viewBox="0 0 24 24" fill="none">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/>
                  </svg>
                  Guardando...
                </span>
              ) : 'Guardar disco'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
