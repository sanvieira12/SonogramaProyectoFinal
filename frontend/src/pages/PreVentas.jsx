import { useEffect, useMemo, useState } from 'react'
import { api } from '../api/sonograma'

function money(value) {
  if (value == null || value === '') return '—'
  return `UYU $${Number(value).toLocaleString('es-UY', { maximumFractionDigits: 2 })}`
}

export default function PreVentas() {
  const [preVentas, setPreVentas] = useState([])
  const [clientes, setClientes] = useState([])
  const [discos, setDiscos] = useState([])
  const [form, setForm] = useState({
    idCliente: '',
    idDisco: '',
    descripcion: '',
    cantidad: '1',
    precio: '',
    fecha: new Date().toISOString().slice(0, 10),
    estado: 'PENDIENTE',
    notas: '',
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    api.preVentas.listar().then(setPreVentas).catch(() => setPreVentas([]))
    api.clientes.todos().then(setClientes).catch(() => setClientes([]))
    api.discos.todos().then(setDiscos).catch(() => setDiscos([]))
  }, [])

  const discoSeleccionado = useMemo(
    () => discos.find(d => String(d.idDisco) === String(form.idDisco)),
    [discos, form.idDisco]
  )

  async function submit(e) {
    e.preventDefault()
    setSaving(true)
    setError('')
    try {
      const created = await api.preVentas.crear({
        idCliente: Number(form.idCliente),
        idDisco: form.idDisco ? Number(form.idDisco) : null,
        descripcion: form.idDisco ? null : form.descripcion,
        cantidad: Number(form.cantidad || 1),
        precio: Number(form.precio),
        fecha: form.fecha,
        estado: form.estado,
        notas: form.notas || null,
      })
      setPreVentas(prev => [created, ...prev])
      setForm({
        idCliente: '',
        idDisco: '',
        descripcion: '',
        cantidad: '1',
        precio: '',
        fecha: new Date().toISOString().slice(0, 10),
        estado: 'PENDIENTE',
        notas: '',
      })
    } catch (err) {
      setError(err.message || 'No se pudo registrar la pre-venta')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 py-6 space-y-5">
      <div>
        <h1 className="text-xl font-bold text-slate-900 dark:text-white">Pre-ventas</h1>
        <p className="text-slate-400 dark:text-stone-500 text-sm mt-0.5">Reservas y pedidos anticipados sin tocar el stock físico actual.</p>
      </div>

      <div className="grid gap-5 lg:grid-cols-[420px_1fr]">
        <form onSubmit={submit} className="card p-5 space-y-4">
          <div>
            <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Cliente</label>
            <select className="input" value={form.idCliente} onChange={e => setForm(prev => ({ ...prev, idCliente: e.target.value }))}>
              <option value="">Seleccioná cliente…</option>
              {clientes.map(cliente => (
                <option key={cliente.idCliente} value={cliente.idCliente}>
                  {[cliente.nombre, cliente.apellido].filter(Boolean).join(' ')}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Disco del catálogo</label>
            <select className="input" value={form.idDisco} onChange={e => setForm(prev => ({ ...prev, idDisco: e.target.value }))}>
              <option value="">Sin seleccionar</option>
              {discos.map(disco => (
                <option key={disco.idDisco} value={disco.idDisco}>
                  {disco.artista} — {disco.album}
                </option>
              ))}
            </select>
          </div>

          {!discoSeleccionado && (
            <div>
              <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Descripción</label>
              <input className="input" value={form.descripcion} onChange={e => setForm(prev => ({ ...prev, descripcion: e.target.value }))} placeholder="Título o release esperado" />
            </div>
          )}

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Cantidad</label>
              <input className="input" type="number" min="1" step="1" value={form.cantidad} onChange={e => setForm(prev => ({ ...prev, cantidad: e.target.value }))} />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Precio</label>
              <input className="input" type="number" min="0" step="0.01" value={form.precio} onChange={e => setForm(prev => ({ ...prev, precio: e.target.value }))} />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Fecha</label>
              <input className="input" type="date" value={form.fecha} onChange={e => setForm(prev => ({ ...prev, fecha: e.target.value }))} />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Estado</label>
              <select className="input" value={form.estado} onChange={e => setForm(prev => ({ ...prev, estado: e.target.value }))}>
                <option value="PENDIENTE">Pendiente</option>
                <option value="CONFIRMADA">Confirmada</option>
                <option value="AVISAR_AL_LLEGAR">Avisar al llegar</option>
              </select>
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">Notas</label>
            <textarea className="input min-h-24 resize-y" value={form.notas} onChange={e => setForm(prev => ({ ...prev, notas: e.target.value }))} />
          </div>

          {error && <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-300">{error}</div>}

          <button className="btn-primary w-full" disabled={saving}>{saving ? 'Guardando…' : 'Registrar pre-venta'}</button>
        </form>

        <div className="card overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-100 dark:border-stone-800">
            <h2 className="font-semibold text-slate-900 dark:text-white">Listado</h2>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-100 dark:border-stone-800">
                  {['Fecha', 'Cliente', 'Disco / descripción', 'Cant.', 'Precio', 'Estado', 'Notas'].map(label => (
                    <th key={label} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-stone-500">{label}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-stone-800">
                {preVentas.map(item => (
                  <tr key={item.idPreVenta}>
                    <td className="px-4 py-3 text-slate-600 dark:text-stone-400">{item.fecha || '—'}</td>
                    <td className="px-4 py-3 text-slate-900 dark:text-white">{item.clienteNombre}</td>
                    <td className="px-4 py-3 text-slate-600 dark:text-stone-400">{item.idDisco ? `${item.artista} — ${item.album}` : item.descripcion}</td>
                    <td className="px-4 py-3 tabular-nums text-slate-600 dark:text-stone-400">{item.cantidad}</td>
                    <td className="px-4 py-3 tabular-nums text-white">{money(item.precio)}</td>
                    <td className="px-4 py-3 text-slate-600 dark:text-stone-400">{item.estado}</td>
                    <td className="px-4 py-3 text-slate-600 dark:text-stone-400">{item.notas || '—'}</td>
                  </tr>
                ))}
                {preVentas.length === 0 && (
                  <tr>
                    <td colSpan={7} className="px-4 py-10 text-center text-slate-400 dark:text-stone-500">No hay pre-ventas registradas.</td>
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
