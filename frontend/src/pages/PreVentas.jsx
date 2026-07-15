import { useEffect, useMemo, useState } from 'react'
import { api } from '../api/sonograma'
import ConfirmModal from '../components/ConfirmModal'

const emptyForm = () => ({ idCliente: '', idDisco: '', descripcion: '', codigoDisco: '', cantidad: '1', precio: '', fecha: new Date().toISOString().slice(0, 10), notas: '' })
const money = value => value == null || value === '' ? '—' : `UYU $${Number(value).toLocaleString('es-UY', { maximumFractionDigits: 2 })}`

export default function PreVentas() {
  const [preVentas, setPreVentas] = useState([])
  const [clientes, setClientes] = useState([])
  const [discos, setDiscos] = useState([])
  const [form, setForm] = useState(emptyForm)
  const [saving, setSaving] = useState(false)
  const [busyId, setBusyId] = useState(null)
  const [deleting, setDeleting] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api.preVentas.listar().then(setPreVentas).catch(() => setPreVentas([]))
    api.clientes.todos().then(setClientes).catch(() => setClientes([]))
    api.discos.todos().then(setDiscos).catch(() => setDiscos([]))
  }, [])

  const discoSeleccionado = useMemo(() => discos.find(d => String(d.idDisco) === String(form.idDisco)), [discos, form.idDisco])

  function seleccionarDisco(idDisco) {
    const disco = discos.find(d => String(d.idDisco) === String(idDisco))
    setForm(prev => ({ ...prev, idDisco, codigoDisco: disco?.codigoInterno || (idDisco ? '' : prev.codigoDisco) }))
  }

  async function submit(e) {
    e.preventDefault(); setSaving(true); setError('')
    try {
      const created = await api.preVentas.crear({
        idCliente: Number(form.idCliente), idDisco: form.idDisco ? Number(form.idDisco) : null,
        descripcion: form.idDisco ? null : form.descripcion, codigoDisco: form.codigoDisco || null,
        cantidad: Number(form.cantidad || 1), precio: Number(form.precio), fecha: form.fecha, notas: form.notas || null,
      })
      setPreVentas(prev => [created, ...prev]); setForm(emptyForm())
    } catch (err) { setError(err.message || 'No se pudo registrar la pre-venta') } finally { setSaving(false) }
  }

  async function marcarPagada(item) {
    setBusyId(item.idPreVenta); setError('')
    try {
      const updated = await api.preVentas.marcarPagada(item.idPreVenta)
      setPreVentas(prev => prev.map(p => p.idPreVenta === updated.idPreVenta ? updated : p))
    } catch (err) { setError(err.message || 'No se pudo marcar la pre-venta como pagada') } finally { setBusyId(null) }
  }

  async function eliminar() {
    if (!deleting) return
    setBusyId(deleting.idPreVenta); setError('')
    try {
      await api.preVentas.eliminar(deleting.idPreVenta)
      setPreVentas(prev => prev.filter(p => p.idPreVenta !== deleting.idPreVenta)); setDeleting(null)
    } catch (err) { setError(err.message || 'No se pudo eliminar la pre-venta') } finally { setBusyId(null) }
  }

  return <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6 space-y-5">
    <div><h1 className="text-xl font-bold text-slate-900 dark:text-white">Pre-ventas</h1><p className="text-slate-400 dark:text-stone-500 text-sm mt-0.5">Reservas y pedidos anticipados sin tocar el stock físico actual.</p></div>
    {error && <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-300">{error}</div>}
    <div className="grid gap-5 xl:grid-cols-[390px_minmax(0,1fr)]">
      <form onSubmit={submit} className="card p-5 space-y-3.5 self-start">
        <Field label="Cliente"><select required className="input" value={form.idCliente} onChange={e => setForm(p => ({ ...p, idCliente: e.target.value }))}><option value="">Seleccioná cliente…</option>{clientes.map(c => <option key={c.idCliente} value={c.idCliente}>{[c.nombre, c.apellido].filter(Boolean).join(' ')}</option>)}</select></Field>
        <Field label="Disco del catálogo"><select className="input" value={form.idDisco} onChange={e => seleccionarDisco(e.target.value)}><option value="">Sin seleccionar</option>{discos.map(d => <option key={d.idDisco} value={d.idDisco}>{d.artista} — {d.album}</option>)}</select></Field>
        {!discoSeleccionado && <Field label="Descripción"><input required className="input" value={form.descripcion} onChange={e => setForm(p => ({ ...p, descripcion: e.target.value }))} placeholder="Título o release esperado" /></Field>}
        <Field label="Código del disco"><input className="input" value={form.codigoDisco} onChange={e => setForm(p => ({ ...p, codigoDisco: e.target.value }))} placeholder="Future, Discogs o proveedor" /></Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Cantidad"><input required className="input" type="number" min="1" step="1" value={form.cantidad} onChange={e => setForm(p => ({ ...p, cantidad: e.target.value }))} /></Field>
          <Field label="Precio"><input required className="input" type="number" min="0.01" step="0.01" value={form.precio} onChange={e => setForm(p => ({ ...p, precio: e.target.value }))} /></Field>
          <Field label="Fecha"><input required className="input" type="date" value={form.fecha} onChange={e => setForm(p => ({ ...p, fecha: e.target.value }))} /></Field>
          <Field label="Estado inicial"><div className="input flex items-center text-sm text-amber-700 dark:text-amber-300">Pendiente</div></Field>
        </div>
        <Field label="Notas"><textarea className="input min-h-14 resize-y" rows={2} value={form.notas} onChange={e => setForm(p => ({ ...p, notas: e.target.value }))} /></Field>
        <button className="btn-primary w-full" disabled={saving}>{saving ? 'Guardando…' : 'Registrar pre-venta'}</button>
      </form>

      <div className="card overflow-hidden min-w-0"><div className="px-5 py-4 border-b border-slate-100 dark:border-stone-800"><h2 className="font-semibold text-slate-900 dark:text-white">Listado</h2></div>
        <div className="overflow-x-auto"><table className="w-full text-sm"><thead><tr className="border-b border-slate-100 dark:border-stone-800">{['Fecha', 'Cliente', 'Disco / descripción', 'Código', 'Cant.', 'Precio', 'Estado', 'Acciones'].map(label => <th key={label} className="px-3 py-2.5 text-left text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-stone-500 whitespace-nowrap">{label}</th>)}</tr></thead>
          <tbody className="divide-y divide-slate-100 dark:divide-stone-800">{preVentas.map(item => {
            const pagada = item.estado === 'PAGADA'; const busy = busyId === item.idPreVenta
            return <tr key={item.idPreVenta} className="align-middle">
              <td className="px-3 py-3 text-slate-600 dark:text-stone-400 whitespace-nowrap">{item.fecha || '—'}</td>
              <td className="px-3 py-3 text-slate-900 dark:text-white min-w-28">{item.clienteNombre}</td>
              <td className="px-3 py-3 text-slate-600 dark:text-stone-400 min-w-48 max-w-64"><span title={item.idDisco ? `${item.artista} — ${item.album}` : item.descripcion} className="line-clamp-2">{item.idDisco ? `${item.artista} — ${item.album}` : item.descripcion}</span></td>
              <td className="px-3 py-3 text-slate-600 dark:text-stone-400 whitespace-nowrap max-w-36 truncate" title={item.codigoDisco}>{item.codigoDisco || '—'}</td>
              <td className="px-3 py-3 tabular-nums text-slate-600 dark:text-stone-400">{item.cantidad}</td><td className="px-3 py-3 tabular-nums text-slate-900 dark:text-white whitespace-nowrap">{money(item.precio)}</td>
              <td className="px-3 py-3"><span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${pagada ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300' : 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300'}`}>{pagada ? 'Pagada' : 'Pendiente'}</span></td>
              <td className="px-3 py-3"><div className="flex flex-wrap gap-1.5 min-w-32">{!pagada && <button type="button" disabled={busy} onClick={() => marcarPagada(item)} className="rounded-lg bg-emerald-600 px-2.5 py-1.5 text-xs font-medium text-white hover:bg-emerald-700 disabled:opacity-50">{busy ? 'Procesando…' : 'Marcar pagada'}</button>}<button type="button" disabled={busy || pagada} onClick={() => setDeleting(item)} title={pagada ? 'Las pre-ventas pagadas no se pueden eliminar' : 'Eliminar pre-venta'} aria-label="Eliminar pre-venta" className="rounded-lg border border-red-200 p-1.5 text-red-600 hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-35 dark:border-red-900 dark:hover:bg-red-950/30"><TrashIcon /></button></div></td>
            </tr>})}{preVentas.length === 0 && <tr><td colSpan={8} className="px-4 py-10 text-center text-slate-400 dark:text-stone-500">No hay pre-ventas registradas.</td></tr>}</tbody></table></div>
      </div>
    </div>
    {deleting && <ConfirmModal titulo="Eliminar pre-venta" mensaje="¿Seguro que querés eliminar esta pre-venta pendiente? No se registrará ningún ingreso ni se modificará el stock." onConfirmar={eliminar} onCancelar={() => setDeleting(null)} cargando={busyId === deleting.idPreVenta} />}
  </div>
}

function Field({ label, children }) { return <div><label className="block text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider mb-1.5">{label}</label>{children}</div> }
function TrashIcon() { return <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="1.8"><path strokeLinecap="round" strokeLinejoin="round" d="m14.74 9-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166M18.16 5.79 17.41 19.5a2.25 2.25 0 0 1-2.244 2.126H8.834A2.25 2.25 0 0 1 6.59 19.5L5.84 5.79m12.32 0a48.108 48.108 0 0 0-3.478-.397m-8.842.397a48.11 48.11 0 0 1 3.478-.397m5.364 0V4.477c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 0 0-1.184 0c-1.18.037-2.09 1.022-2.09 2.201v.916m5.364 0a48.667 48.667 0 0 0-5.364 0" /></svg> }
