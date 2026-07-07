import { useEffect, useMemo, useState } from 'react'
import { api } from '../api/sonograma'

function fmtMoney(value) {
  return `UYU $${Number(value || 0).toLocaleString('es-UY', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

export default function GastosTienda() {
  const [items, setItems] = useState([])
  const [editingId, setEditingId] = useState(null)
  const [form, setForm] = useState({
    fecha: new Date().toISOString().slice(0, 10),
    descripcion: '',
    monto: '',
  })
  const [error, setError] = useState('')

  useEffect(() => {
    api.gastosTienda.listar().then(setItems).catch(() => setItems([]))
  }, [])

  const totalMes = useMemo(() => {
    const now = new Date()
    const month = now.toISOString().slice(0, 7)
    return items
      .filter(item => String(item.fecha || '').startsWith(month))
      .reduce((sum, item) => sum + Number(item.monto || 0), 0)
  }, [items])

  async function submit(e) {
    e.preventDefault()
    setError('')
    try {
      const payload = { ...form, monto: Number(form.monto) }
      if (editingId) {
        const updated = await api.gastosTienda.actualizar(editingId, payload)
        setItems(prev => prev.map(item => item.idGasto === editingId ? updated : item))
      } else {
        const created = await api.gastosTienda.crear(payload)
        setItems(prev => [created, ...prev])
      }
      setEditingId(null)
      setForm({ fecha: new Date().toISOString().slice(0, 10), descripcion: '', monto: '' })
    } catch (err) {
      setError(err.message || 'No se pudo guardar el gasto')
    }
  }

  async function remove(id) {
    try {
      await api.gastosTienda.eliminar(id)
      setItems(prev => prev.filter(item => item.idGasto !== id))
      if (editingId === id) {
        setEditingId(null)
        setForm({ fecha: new Date().toISOString().slice(0, 10), descripcion: '', monto: '' })
      }
    } catch (err) {
      setError(err.message || 'No se pudo eliminar el gasto')
    }
  }

  function edit(item) {
    setEditingId(item.idGasto)
    setForm({
      fecha: item.fecha,
      descripcion: item.descripcion,
      monto: String(item.monto ?? ''),
    })
  }

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 py-6 space-y-5">
      <div className="flex items-center justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-xl font-bold text-slate-900 dark:text-white">Gastos de tienda</h1>
          <p className="text-slate-400 dark:text-stone-500 text-sm mt-0.5">Registro manual de gastos del local.</p>
        </div>
        <div className="card px-4 py-3">
          <p className="text-xs uppercase tracking-wider text-slate-500 dark:text-stone-500">Total del mes</p>
          <p className="mt-1 text-xl font-bold text-slate-900 dark:text-white">{fmtMoney(totalMes)}</p>
        </div>
      </div>

      <div className="grid gap-5 lg:grid-cols-[380px_1fr]">
        <form onSubmit={submit} className="card p-5 space-y-4">
          <div>
            <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Fecha</label>
            <input className="input" type="date" value={form.fecha} onChange={e => setForm(prev => ({ ...prev, fecha: e.target.value }))} />
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Motivo</label>
            <input className="input" value={form.descripcion} onChange={e => setForm(prev => ({ ...prev, descripcion: e.target.value }))} />
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Monto</label>
            <input className="input" type="number" min="0" step="0.01" value={form.monto} onChange={e => setForm(prev => ({ ...prev, monto: e.target.value }))} />
          </div>
          {error && <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-300">{error}</div>}
          <div className="flex gap-2">
            <button className="btn-primary flex-1">{editingId ? 'Guardar cambios' : 'Agregar gasto'}</button>
            {editingId && <button type="button" className="btn-secondary" onClick={() => { setEditingId(null); setForm({ fecha: new Date().toISOString().slice(0, 10), descripcion: '', monto: '' }) }}>Cancelar</button>}
          </div>
        </form>

        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-100 dark:border-stone-800">
                  {['Fecha', 'Motivo', 'Monto', 'Acciones'].map(label => (
                    <th key={label} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-stone-500">{label}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-stone-800">
                {items.map(item => (
                  <tr key={item.idGasto}>
                    <td className="px-4 py-3 text-slate-600 dark:text-stone-400">{item.fecha}</td>
                    <td className="px-4 py-3 text-slate-900 dark:text-white">{item.descripcion}</td>
                    <td className="px-4 py-3 tabular-nums text-white">{fmtMoney(item.monto)}</td>
                    <td className="px-4 py-3">
                      <div className="flex gap-2">
                        <button className="btn-secondary text-sm" onClick={() => edit(item)}>Editar</button>
                        <button className="btn-secondary text-sm text-red-600 dark:text-red-400" onClick={() => remove(item.idGasto)}>Eliminar</button>
                      </div>
                    </td>
                  </tr>
                ))}
                {items.length === 0 && (
                  <tr>
                    <td colSpan={4} className="px-4 py-10 text-center text-slate-400 dark:text-stone-500">No hay gastos registrados.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  )
}
