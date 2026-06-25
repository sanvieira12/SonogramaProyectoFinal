import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/sonograma'

const EMPTY_NOTA = {
  titulo: '',
  contenido: '',
  tags: '',
  fechaNota: new Date().toISOString().slice(0, 10),
  tipoRelacion: 'GENERAL',
  relatedId: '',
  pinned: false,
}

function fmtDate(value) {
  if (!value) return '—'
  return new Date(`${value}T00:00:00`).toLocaleDateString('es-UY', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  })
}

function NotaPanel({ nota, onClose, onSaved, onArchived }) {
  const [form, setForm] = useState(() => ({
    ...EMPTY_NOTA,
    ...(nota || {}),
    relatedId: nota?.relatedId || '',
  }))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  function set(field, value) {
    setForm(prev => ({ ...prev, [field]: value }))
  }

  async function submit(e) {
    e.preventDefault()
    setSaving(true)
    setError('')
    try {
      const payload = {
        titulo: form.titulo.trim(),
        contenido: form.contenido || null,
        tags: form.tags || null,
        fechaNota: form.fechaNota || new Date().toISOString().slice(0, 10),
        tipoRelacion: form.tipoRelacion || 'GENERAL',
        relatedId: form.relatedId ? Number(form.relatedId) : null,
        pinned: Boolean(form.pinned),
      }
      const saved = nota?.idNota
        ? await api.notas.actualizar(nota.idNota, payload)
        : await api.notas.crear(payload)
      onSaved(saved)
    } catch (e) {
      setError(e.message || 'No se pudo guardar la nota')
    } finally {
      setSaving(false)
    }
  }

  async function archive() {
    if (!nota?.idNota) return
    setSaving(true)
    setError('')
    try {
      await api.notas.archivar(nota.idNota)
      onArchived(nota.idNota)
    } catch (e) {
      setError(e.message || 'No se pudo archivar la nota')
    } finally {
      setSaving(false)
    }
  }

  return (
    <aside className="fixed inset-y-0 right-0 z-50 w-full max-w-md bg-white dark:bg-stone-950 border-l border-slate-200 dark:border-stone-800 shadow-2xl overflow-y-auto">
      <form onSubmit={submit} className="min-h-full flex flex-col">
        <div className="px-5 py-4 border-b border-slate-100 dark:border-stone-800 flex items-center justify-between">
          <div>
            <h2 className="text-sm font-semibold text-slate-900 dark:text-white">{nota?.idNota ? 'Editar nota' : 'Nueva nota'}</h2>
            <p className="text-xs text-slate-400 dark:text-stone-500 mt-0.5">Notas globales de gestión</p>
          </div>
          <button type="button" onClick={onClose} className="text-slate-400 hover:text-slate-700 dark:hover:text-white">✕</button>
        </div>

        <div className="p-5 space-y-4 flex-1">
          {error && <p className="text-xs text-red-500 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg px-3 py-2">{error}</p>}
          <div>
            <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Título *</label>
            <input className="input w-full" value={form.titulo} onChange={e => set('titulo', e.target.value)} autoFocus />
          </div>
          <div>
            <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Contenido</label>
            <textarea className="input w-full min-h-40 resize-y" value={form.contenido || ''} onChange={e => set('contenido', e.target.value)} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Fecha</label>
              <input type="date" className="input w-full" value={form.fechaNota || ''} onChange={e => set('fechaNota', e.target.value)} />
            </div>
            <div>
              <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Relación</label>
              <select className="input w-full" value={form.tipoRelacion || 'GENERAL'} onChange={e => set('tipoRelacion', e.target.value)}>
                {['GENERAL', 'CLIENTE', 'DEUDA', 'PEDIDO', 'DISCO', 'VENTA'].map(t => <option key={t} value={t}>{t}</option>)}
              </select>
            </div>
          </div>
          <label className="flex items-center gap-2 text-sm text-slate-600 dark:text-stone-300">
            <input type="checkbox" checked={Boolean(form.pinned)} onChange={e => set('pinned', e.target.checked)} />
            Fijada
          </label>
          <div>
            <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Tags</label>
            <input className="input w-full" value={form.tags || ''} onChange={e => set('tags', e.target.value)} placeholder="proveedor, cliente, urgente" />
          </div>
        </div>

        <div className="px-5 py-4 border-t border-slate-100 dark:border-stone-800 flex items-center justify-between gap-3">
          {nota?.idNota ? (
            <button type="button" onClick={archive} disabled={saving} className="btn-secondary text-sm text-red-500 disabled:opacity-50">Archivar</button>
          ) : <span />}
          <div className="flex gap-2">
            <button type="button" onClick={onClose} className="btn-secondary text-sm">Cancelar</button>
            <button type="submit" disabled={saving || !form.titulo.trim()} className="btn-primary text-sm disabled:opacity-50">
              {saving ? 'Guardando…' : 'Guardar'}
            </button>
          </div>
        </div>
      </form>
    </aside>
  )
}

export default function Notas() {
  const [notas, setNotas] = useState([])
  const [search, setSearch] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [panelNota, setPanelNota] = useState(null)
  const [creating, setCreating] = useState(false)

  const load = useCallback(async (q = '') => {
    setLoading(true)
    setError('')
    try {
      setNotas(await api.notas.listar(q.trim()))
    } catch (e) {
      setError(e.message || 'No se pudieron cargar las notas')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    const timer = window.setTimeout(() => load(''), 0)
    return () => window.clearTimeout(timer)
  }, [load])

  function onSaved(saved) {
    setNotas(prev => {
      const exists = prev.some(n => n.idNota === saved.idNota)
      const next = exists ? prev.map(n => n.idNota === saved.idNota ? saved : n) : [saved, ...prev]
      return next.sort((a, b) => Number(Boolean(b.pinned)) - Number(Boolean(a.pinned)) || (b.fechaNota || '').localeCompare(a.fechaNota || ''))
    })
    setPanelNota(null)
    setCreating(false)
  }

  function onArchived(id) {
    setNotas(prev => prev.filter(n => n.idNota !== id))
    setPanelNota(null)
  }

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 py-6 space-y-5">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-900 dark:text-white">Notas</h1>
          <p className="text-slate-400 dark:text-stone-500 text-sm mt-0.5">Registro global ordenado por fecha</p>
        </div>
        <button onClick={() => { setCreating(true); setPanelNota(null) }} className="btn-primary text-sm">Nueva nota</button>
      </div>

      <div className="relative max-w-md flex gap-2">
        <input
          className="input w-full"
          value={search}
          onChange={e => setSearch(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && load(search)}
          placeholder="Buscar en título, contenido o tags..."
        />
        <button onClick={() => load(search)} className="btn-secondary text-sm">Buscar</button>
      </div>

      {error && <p className="text-sm text-red-500 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-xl px-4 py-3">{error}</p>}

      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
        {loading ? (
          <div className="col-span-full text-center py-16 text-slate-400 dark:text-stone-500 text-sm">Cargando notas…</div>
        ) : notas.length === 0 ? (
          <div className="col-span-full text-center py-16 text-slate-400 dark:text-stone-500 text-sm">
            {search ? 'No se encontraron notas para esa búsqueda' : 'No hay notas registradas'}
          </div>
        ) : notas.map(nota => (
          <button
            key={nota.idNota}
            onClick={() => { setPanelNota(nota); setCreating(false) }}
            className="card p-4 text-left hover:border-[#7E9FA8]/50 transition-colors min-h-44"
          >
            <div className="flex items-start justify-between gap-3">
              <h2 className="font-semibold text-slate-900 dark:text-white text-sm line-clamp-2">{nota.titulo}</h2>
              {nota.pinned && <span className="text-xs text-[#7E9FA8]">Fijada</span>}
            </div>
            <p className="text-xs text-slate-400 dark:text-stone-500 mt-1">{fmtDate(nota.fechaNota)} · {nota.tipoRelacion || 'GENERAL'}</p>
            <p className="text-sm text-slate-600 dark:text-stone-400 mt-3 line-clamp-4 whitespace-pre-wrap">{nota.contenido || 'Sin contenido'}</p>
            {nota.tags && <p className="text-xs text-[#5C7D87] dark:text-[#7E9FA8] mt-3 truncate">{nota.tags}</p>}
          </button>
        ))}
      </div>

      {(panelNota || creating) && (
        <NotaPanel
          nota={creating ? null : panelNota}
          onClose={() => { setPanelNota(null); setCreating(false) }}
          onSaved={onSaved}
          onArchived={onArchived}
        />
      )}
    </div>
  )
}
