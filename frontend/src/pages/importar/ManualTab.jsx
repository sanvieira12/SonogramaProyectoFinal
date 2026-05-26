import { useState } from 'react'
import { api } from '../../api/sonograma'

const CONDICIONES = ['NUEVO', 'USADO', 'CONSIGNACION', 'CATALOGO']
const FORMATOS = ['VINILO', 'CD', 'CASSETTE', 'DIGITAL', 'OTRO']
const ESTADOS = ['DISPONIBLE', 'RESERVADO', 'VENDIDO', 'SIN_STOCK']
const PROCEDENCIAS = ['NUEVO', 'USADO', 'CONSIGNACION', 'IMPORTADO', 'OTRO']

const DEPARTAMENTOS_UY = [
  'Artigas', 'Canelones', 'Cerro Largo', 'Colonia', 'Durazno', 'Flores',
  'Florida', 'Lavalleja', 'Maldonado', 'Montevideo', 'Paysandú',
  'Río Negro', 'Rivera', 'Rocha', 'Salto', 'San José', 'Soriano',
  'Tacuarembó', 'Treinta y Tres',
]

function Field({ label, required, children, error }) {
  return (
    <div>
      <label className="block text-xs font-medium text-slate-500 dark:text-stone-400 mb-1">
        {label}{required && <span className="text-red-400 ml-0.5">*</span>}
      </label>
      {children}
      {error && <p className="text-xs text-red-500 mt-1">{error}</p>}
    </div>
  )
}

function generarCodigo(artista, anio) {
  const initials = (artista || '')
    .split(/\s+/)
    .map(w => w[0] || '')
    .join('')
    .toUpperCase()
    .slice(0, 4)
  const year = anio || new Date().getFullYear()
  const rand = Math.floor(Math.random() * 900 + 100)
  return `${initials}-${year}-${rand}`
}

const EMPTY = {
  artista: '', album: '', selloDiscografico: '', codigoInterno: '',
  anio: '', pais: '', genero: '', estilo: '',
  tipoDisco: 'VINILO', condicion: 'USADO', estado: 'DISPONIBLE',
  costo: '', precioVenta: '', procedencia: '',
  imagenUrl: '', tracklist: '', notas: '', discogsUrl: '',
}

export default function ManualTab() {
  const [form, setForm] = useState(EMPTY)
  const [errores, setErrores] = useState({})
  const [estado, setEstado] = useState('idle') // idle | saving | done | error
  const [errorMsg, setErrorMsg] = useState('')

  function set(field, value) {
    setForm(prev => ({ ...prev, [field]: value }))
    if (errores[field]) setErrores(prev => { const n = { ...prev }; delete n[field]; return n })
  }

  function validar() {
    const e = {}
    if (!form.artista.trim()) e.artista = 'El artista es obligatorio'
    if (!form.album.trim()) e.album = 'El álbum es obligatorio'
    return e
  }

  function autoCode() {
    set('codigoInterno', generarCodigo(form.artista, form.anio))
  }

  async function guardar(e) {
    e.preventDefault()
    const errs = validar()
    if (Object.keys(errs).length) { setErrores(errs); return }

    setEstado('saving')
    setErrorMsg('')
    try {
      const payload = {
        artista: form.artista.trim(),
        album: form.album.trim(),
        selloDiscografico: form.selloDiscografico || undefined,
        codigoInterno: form.codigoInterno || undefined,
        anio: form.anio ? parseInt(form.anio) : undefined,
        pais: form.pais || undefined,
        genero: form.genero || undefined,
        estilo: form.estilo || undefined,
        tipoDisco: form.tipoDisco,
        condicion: form.condicion,
        costo: form.costo ? parseFloat(form.costo) : undefined,
        precioVenta: form.precioVenta ? parseFloat(form.precioVenta) : undefined,
        procedencia: form.procedencia || undefined,
        imagenUrl: form.imagenUrl || undefined,
        tracklist: form.tracklist || undefined,
        notas: form.notas || undefined,
        discogsUrl: form.discogsUrl || undefined,
      }
      await api.discos.crear(payload)
      setEstado('done')
      setTimeout(() => { setForm(EMPTY); setEstado('idle') }, 2500)
    } catch (err) {
      setErrorMsg(err.message || 'Error al guardar el disco')
      setEstado('error')
    }
  }

  return (
    <form onSubmit={guardar} className="space-y-6">

      {estado === 'done' && (
        <div className="p-4 rounded-xl bg-emerald-50 dark:bg-emerald-900/20 border border-emerald-200 dark:border-emerald-800">
          <p className="text-sm font-medium text-emerald-700 dark:text-emerald-400">✓ Disco guardado correctamente. El formulario se limpiará en un momento.</p>
        </div>
      )}

      {estado === 'error' && (
        <div className="p-4 rounded-xl bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800">
          <p className="text-sm font-medium text-red-700 dark:text-red-400">Error al guardar</p>
          {errorMsg && <p className="text-xs text-red-600 dark:text-red-300 mt-1">{errorMsg}</p>}
        </div>
      )}

      {/* Identificación */}
      <div>
        <p className="text-xs font-semibold uppercase tracking-wider text-slate-400 dark:text-stone-500 mb-3">Identificación</p>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <Field label="Artista" required error={errores.artista}>
            <input className="input w-full" value={form.artista}
              onChange={e => set('artista', e.target.value)} placeholder="Nombre del artista" />
          </Field>
          <Field label="Álbum" required error={errores.album}>
            <input className="input w-full" value={form.album}
              onChange={e => set('album', e.target.value)} placeholder="Título del álbum" />
          </Field>
          <Field label="Código interno">
            <div className="flex gap-2">
              <input className="input flex-1" value={form.codigoInterno}
                onChange={e => set('codigoInterno', e.target.value)} placeholder="Ej: PF-1973-001" />
              <button type="button" onClick={autoCode}
                className="px-3 py-2 rounded-lg text-xs bg-slate-100 dark:bg-stone-900 text-slate-600 dark:text-stone-400 hover:bg-slate-200 dark:hover:bg-stone-700 transition-colors whitespace-nowrap">
                Auto
              </button>
            </div>
          </Field>
          <Field label="Sello discográfico">
            <input className="input w-full" value={form.selloDiscografico}
              onChange={e => set('selloDiscografico', e.target.value)} placeholder="Ej: Atlantic Records" />
          </Field>
        </div>
      </div>

      {/* Datos musicales */}
      <div>
        <p className="text-xs font-semibold uppercase tracking-wider text-slate-400 dark:text-stone-500 mb-3">Datos musicales</p>
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
          <Field label="Año">
            <input type="number" className="input w-full" value={form.anio}
              onChange={e => set('anio', e.target.value)} placeholder="1973" min="1900" max="2100" />
          </Field>
          <Field label="País">
            <input className="input w-full" value={form.pais}
              onChange={e => set('pais', e.target.value)} placeholder="Ej: Uruguay" />
          </Field>
          <Field label="Género">
            <input className="input w-full" value={form.genero}
              onChange={e => set('genero', e.target.value)} placeholder="Ej: Rock" />
          </Field>
          <Field label="Estilo">
            <input className="input w-full" value={form.estilo}
              onChange={e => set('estilo', e.target.value)} placeholder="Ej: Post-punk" />
          </Field>
          <Field label="Formato">
            <select className="input w-full" value={form.tipoDisco}
              onChange={e => set('tipoDisco', e.target.value)}>
              {FORMATOS.map(f => <option key={f} value={f}>{f}</option>)}
            </select>
          </Field>
          <Field label="Condición">
            <select className="input w-full" value={form.condicion}
              onChange={e => set('condicion', e.target.value)}>
              {CONDICIONES.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
          </Field>
        </div>
      </div>

      {/* Comercial */}
      <div>
        <p className="text-xs font-semibold uppercase tracking-wider text-slate-400 dark:text-stone-500 mb-3">Comercial</p>
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
          <Field label="Estado">
            <select className="input w-full" value={form.estado}
              onChange={e => set('estado', e.target.value)}>
              {ESTADOS.map(s => <option key={s} value={s}>{s}</option>)}
            </select>
          </Field>
          <Field label="Precio compra ($)">
            <input type="number" className="input w-full" value={form.costo}
              onChange={e => set('costo', e.target.value)} placeholder="0.00" min="0" step="0.01" />
          </Field>
          <Field label="Precio venta ($)">
            <input type="number" className="input w-full" value={form.precioVenta}
              onChange={e => set('precioVenta', e.target.value)} placeholder="0.00" min="0" step="0.01" />
          </Field>
          <Field label="Procedencia">
            <select className="input w-full" value={form.procedencia}
              onChange={e => set('procedencia', e.target.value)}>
              <option value="">— Sin especificar —</option>
              {PROCEDENCIAS.map(p => <option key={p} value={p}>{p}</option>)}
            </select>
          </Field>
        </div>
      </div>

      {/* Multimedia */}
      <div>
        <p className="text-xs font-semibold uppercase tracking-wider text-slate-400 dark:text-stone-500 mb-3">Multimedia y notas</p>
        <div className="space-y-4">
          <Field label="URL de imagen (portada)">
            <input type="url" className="input w-full" value={form.imagenUrl}
              onChange={e => set('imagenUrl', e.target.value)} placeholder="https://..." />
          </Field>
          <Field label="Link de Discogs">
            <input type="url" className="input w-full" value={form.discogsUrl}
              onChange={e => set('discogsUrl', e.target.value)} placeholder="https://www.discogs.com/release/..." />
          </Field>
          <Field label="Tracklist">
            <textarea rows={4} className="input w-full resize-none" value={form.tracklist}
              onChange={e => set('tracklist', e.target.value)} placeholder="A1. Título&#10;A2. Título&#10;B1. Título" />
          </Field>
          <Field label="Notas internas">
            <textarea rows={3} className="input w-full resize-none" value={form.notas}
              onChange={e => set('notas', e.target.value)} placeholder="Observaciones, detalles de estado, etc." />
          </Field>
        </div>
      </div>

      {/* Fecha ingreso (informativo) */}
      <div>
        <p className="text-xs text-slate-400 dark:text-stone-500">
          Fecha de ingreso: <span className="font-medium text-slate-600 dark:text-stone-300">hoy ({new Date().toLocaleDateString('es-UY')})</span> — se registra automáticamente.
        </p>
      </div>

      <button
        type="submit"
        disabled={estado === 'saving'}
        className="px-6 py-2.5 rounded-lg bg-[#5C7D87] hover:bg-[#4a6a74] disabled:opacity-40 disabled:cursor-not-allowed text-white text-sm font-medium transition-colors"
      >
        {estado === 'saving' ? 'Guardando…' : 'Guardar disco'}
      </button>
    </form>
  )
}
