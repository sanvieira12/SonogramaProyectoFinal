import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/sonograma'
import ConfirmModal from '../components/ConfirmModal'

const ESTADO_STYLES = {
  PENDIENTE: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
  PARCIAL: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400',
  PAGADO: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400',
}

const EMPTY_DEUDA = {
  idCliente: '',
  nombreDeudorManual: '',
  mailManual: '',
  instagramManual: '',
  ciManual: '',
  numeroFactura: '',
  descripcion: '',
  montoTotal: '',
  montoPagado: '0',
  fechaDeuda: new Date().toISOString().slice(0, 10),
  estadoPago: 'PENDIENTE',
  notas: '',
}

function fmt(n) {
  if (n == null) return '$0'
  return `$${Number(n).toLocaleString('es-UY', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function fmtDate(s) {
  if (!s) return '—'
  return new Date(`${s}T00:00:00`).toLocaleDateString('es-UY', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

function buildForm(deuda) {
  if (!deuda) return EMPTY_DEUDA
  return {
    idCliente: deuda.idCliente || '',
    nombreDeudorManual: deuda.nombreDeudorManual || '',
    mailManual: deuda.mailManual || '',
    instagramManual: deuda.instagramManual || '',
    ciManual: deuda.ciManual || '',
    numeroFactura: deuda.numeroFactura || '',
    descripcion: deuda.descripcion || '',
    montoTotal: deuda.montoTotal ?? '',
    montoPagado: deuda.montoPagado ?? '0',
    fechaDeuda: deuda.fechaDeuda || deuda.fechaVenta || new Date().toISOString().slice(0, 10),
    estadoPago: deuda.estadoPago || 'PENDIENTE',
    notas: deuda.notas || '',
  }
}

function DeudaPanel({ deuda, clientes, onClose, onSaved, onPaid }) {
  const [mode, setMode] = useState(deuda?.idDeuda ? 'view' : 'edit')
  const [form, setForm] = useState(() => buildForm(deuda))
  const [payment, setPayment] = useState({ monto: '', notas: '' })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setForm(buildForm(deuda))
      setMode(deuda?.idDeuda ? 'view' : 'edit')
      setError('')
      setMessage('')
    }, 0)
    return () => window.clearTimeout(timer)
  }, [deuda])

  function set(field, value) {
    setForm(prev => ({ ...prev, [field]: value }))
  }

  function payload() {
    return {
      idCliente: form.idCliente ? Number(form.idCliente) : null,
      nombreDeudorManual: form.idCliente ? null : form.nombreDeudorManual || null,
      mailManual: form.mailManual || null,
      instagramManual: form.instagramManual || null,
      ciManual: form.ciManual || null,
      numeroFactura: form.numeroFactura || null,
      descripcion: form.descripcion || null,
      montoTotal: form.montoTotal ? Number(form.montoTotal) : null,
      montoPagado: form.montoPagado ? Number(form.montoPagado) : 0,
      fechaDeuda: form.fechaDeuda || null,
      fechaVenta: form.fechaDeuda || null,
      estadoPago: form.estadoPago || null,
      notas: form.notas || null,
    }
  }

  async function save(e) {
    e.preventDefault()
    setSaving(true)
    setError('')
    setMessage('')
    try {
      const saved = deuda?.idDeuda
        ? await api.deudas.actualizar(deuda.idDeuda, payload())
        : await api.deudas.crear(payload())
      onSaved(saved)
      setMode('view')
      setMessage('Deuda guardada')
    } catch (e) {
      setError(e.message || 'No se pudo guardar la deuda')
    } finally {
      setSaving(false)
    }
  }

  async function pay(e) {
    e.preventDefault()
    const monto = Number(payment.monto)
    if (!monto || monto <= 0) {
      setError('Ingresá un monto válido')
      return
    }
    setSaving(true)
    setError('')
    setMessage('')
    try {
      const updated = await api.deudas.registrarPago(deuda.idDeuda, monto, payment.notas || null)
      onPaid(updated)
      setPayment({ monto: '', notas: '' })
      setMessage('Pago registrado')
    } catch (e) {
      setError(e.message || 'No se pudo registrar el pago')
    } finally {
      setSaving(false)
    }
  }

  const current = deuda || {}

  return (
    <aside className="fixed inset-y-0 right-0 z-50 w-full max-w-xl bg-white dark:bg-stone-950 border-l border-slate-200 dark:border-stone-800 shadow-2xl overflow-y-auto">
      <div className="sticky top-0 bg-white/95 dark:bg-stone-950/95 backdrop-blur border-b border-slate-100 dark:border-stone-800 px-5 py-4 flex items-start justify-between gap-4">
        <div>
          <h2 className="text-base font-bold text-slate-900 dark:text-white">{deuda?.idDeuda ? current.nombreCliente : 'Nueva deuda'}</h2>
          <p className="text-sm text-slate-400 dark:text-stone-500">{current.numeroRecibo ? `Recibo ${current.numeroRecibo}` : current.numeroFactura ? `Factura ${current.numeroFactura}` : 'Deuda manual editable'}</p>
        </div>
        <div className="flex items-center gap-2">
          {deuda?.idDeuda && <button onClick={() => setMode(mode === 'edit' ? 'view' : 'edit')} className="btn-secondary text-sm">{mode === 'edit' ? 'Ver' : 'Editar'}</button>}
          <button onClick={onClose} className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-stone-800">✕</button>
        </div>
      </div>

      <div className="p-5 space-y-5">
        {message && <p className="text-xs text-emerald-600 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-900/20 border border-emerald-200 dark:border-emerald-800 rounded-lg px-3 py-2">{message}</p>}
        {error && <p className="text-xs text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg px-3 py-2">{error}</p>}

        {mode === 'edit' ? (
          <form onSubmit={save} className="space-y-4">
            <div>
              <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Cliente existente</label>
              <select className="input w-full" value={form.idCliente || ''} onChange={e => set('idCliente', e.target.value)}>
                <option value="">Sin cliente asociado</option>
                {clientes.map(c => <option key={c.idCliente} value={c.idCliente}>{c.nombre} {c.apellido || ''}</option>)}
              </select>
            </div>
            {!form.idCliente && (
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Nombre deudor *</label>
                  <input className="input w-full" value={form.nombreDeudorManual} onChange={e => set('nombreDeudorManual', e.target.value)} />
                </div>
                <div>
                  <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">CI</label>
                  <input className="input w-full" value={form.ciManual} onChange={e => set('ciManual', e.target.value)} />
                </div>
                <div>
                  <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Mail</label>
                  <input className="input w-full" value={form.mailManual} onChange={e => set('mailManual', e.target.value)} />
                </div>
                <div>
                  <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Instagram</label>
                  <input className="input w-full" value={form.instagramManual} onChange={e => set('instagramManual', e.target.value)} />
                </div>
              </div>
            )}
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Factura</label>
                <input className="input w-full" value={form.numeroFactura} onChange={e => set('numeroFactura', e.target.value)} />
              </div>
              <div>
                <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Fecha deuda</label>
                <input type="date" className="input w-full" value={form.fechaDeuda} onChange={e => set('fechaDeuda', e.target.value)} />
              </div>
              <div>
                <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Total *</label>
                <input type="number" step="0.01" min="0" className="input w-full" value={form.montoTotal} onChange={e => set('montoTotal', e.target.value)} />
              </div>
              <div>
                <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Pagado</label>
                <input type="number" step="0.01" min="0" className="input w-full" value={form.montoPagado} onChange={e => set('montoPagado', e.target.value)} />
              </div>
            </div>
            <div>
              <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Descripción</label>
              <textarea className="input w-full min-h-20 resize-y" value={form.descripcion} onChange={e => set('descripcion', e.target.value)} />
            </div>
            <div>
              <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Notas</label>
              <textarea className="input w-full min-h-20 resize-y" value={form.notas} onChange={e => set('notas', e.target.value)} />
            </div>
            <div className="flex justify-end gap-2">
              <button type="button" onClick={onClose} className="btn-secondary text-sm">Cancelar</button>
              <button type="submit" disabled={saving || !form.montoTotal || (!form.idCliente && !form.nombreDeudorManual.trim())} className="btn-primary text-sm disabled:opacity-50">
                {saving ? 'Guardando…' : 'Guardar deuda'}
              </button>
            </div>
          </form>
        ) : (
          <>
            <div className="grid grid-cols-2 gap-3">
              <div className="card p-4"><p className="text-xs text-slate-400 dark:text-stone-500">Total</p><p className="text-lg font-bold text-slate-900 dark:text-white">{fmt(current.montoTotal)}</p></div>
              <div className="card p-4"><p className="text-xs text-slate-400 dark:text-stone-500">Pendiente</p><p className="text-lg font-bold text-red-600 dark:text-red-400">{fmt(current.montoPendiente)}</p></div>
            </div>
            <div className="grid grid-cols-2 gap-3 text-sm">
              {[
                ['Número de recibo', current.numeroRecibo],
                ['Factura', current.numeroFactura],
                ['Fecha', fmtDate(current.fechaDeuda || current.fechaVenta)],
                ['Mail', current.mailManual],
                ['Instagram', current.instagramManual],
                ['CI', current.ciManual],
                ['Último pago', fmtDate(current.fechaUltimoPago)],
              ].map(([label, value]) => (
                <div key={label}>
                  <p className="text-xs uppercase tracking-wider text-slate-400 dark:text-stone-500 mb-1">{label}</p>
                  <p className="text-slate-700 dark:text-stone-300">{value || '—'}</p>
                </div>
              ))}
            </div>
            {(current.descripcion || current.notas) && (
              <div className="space-y-3">
                {current.descripcion && <p className="text-sm text-slate-600 dark:text-stone-400 whitespace-pre-wrap">{current.descripcion}</p>}
                {current.notas && <p className="text-sm text-slate-500 dark:text-stone-500 whitespace-pre-wrap">{current.notas}</p>}
              </div>
            )}
            {current.detalles?.length > 0 && (
              <div>
                <h3 className="text-xs font-semibold text-slate-500 dark:text-stone-400 uppercase tracking-wider mb-2">Discos vendidos</h3>
                <div className="space-y-2">
                  {current.detalles.map((detalle, index) => (
                    <div key={detalle.idDetalle || detalle.idDisco || index} className="w-full text-left rounded-lg border border-slate-100 dark:border-stone-800 px-3 py-2">
                      <p className="text-sm font-medium text-slate-800 dark:text-stone-200">{detalle.manualItem ? detalle.descripcion : `${detalle.artista} — ${detalle.album}`}</p>
                      <p className="text-xs text-slate-400 dark:text-stone-500">{detalle.codigoInterno || 'Sin código'} · Cant. {detalle.cantidad || 1} · {fmt(detalle.precioUnitario)}</p>
                    </div>
                  ))}
                </div>
              </div>
            )}
            {current.estadoPago !== 'PAGADO' && (
              <form onSubmit={pay} className="rounded-xl border border-slate-100 dark:border-stone-800 p-4 space-y-3">
                <h3 className="text-xs font-semibold text-slate-500 dark:text-stone-400 uppercase tracking-wider">Registrar pago</h3>
                <div className="grid grid-cols-2 gap-3">
                  <input type="number" step="0.01" min="0.01" max={current.montoPendiente} className="input w-full" placeholder={`Máx. ${fmt(current.montoPendiente)}`} value={payment.monto} onChange={e => setPayment(p => ({ ...p, monto: e.target.value }))} />
                  <input className="input w-full" placeholder="Notas" value={payment.notas} onChange={e => setPayment(p => ({ ...p, notas: e.target.value }))} />
                </div>
                <button disabled={saving} className="btn-primary text-sm disabled:opacity-50">{saving ? 'Registrando…' : 'Confirmar pago'}</button>
              </form>
            )}
            <div>
              <h3 className="text-xs font-semibold text-slate-500 dark:text-stone-400 uppercase tracking-wider mb-2">Historial de pagos</h3>
              {(current.pagos || []).length === 0 ? (
                <p className="text-sm text-slate-400 dark:text-stone-600">Sin pagos registrados.</p>
              ) : (
                <div className="space-y-2">
                  {current.pagos.map(p => (
                    <div key={p.idPagoDeuda} className="rounded-lg border border-slate-100 dark:border-stone-800 px-3 py-2 flex items-center justify-between gap-3 text-sm">
                      <div>
                        <p className="font-medium text-slate-800 dark:text-stone-200">{fmt(p.monto)}</p>
                        {p.notas && <p className="text-xs text-slate-400 dark:text-stone-500">{p.notas}</p>}
                      </div>
                      <span className="text-xs text-slate-400 dark:text-stone-500">{fmtDate(p.fechaPago)}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </>
        )}
      </div>
    </aside>
  )
}

export default function Deudas() {
  const [deudas, setDeudas] = useState([])
  const [clientes, setClientes] = useState([])
  const [resumen, setResumen] = useState(null)
  const [loading, setLoading] = useState(true)
  const [q, setQ] = useState('')
  const [search, setSearch] = useState('')
  const [panelDeuda, setPanelDeuda] = useState(null)
  const [creating, setCreating] = useState(false)
  const [deudaEliminar, setDeudaEliminar] = useState(null)
  const [eliminando, setEliminando] = useState(false)
  const [error, setError] = useState('')

  const cargar = useCallback(async (query = search) => {
    setLoading(true)
    setError('')
    try {
      const [d, r, c] = await Promise.all([
        api.deudas.listar(query),
        api.deudas.resumen(),
        api.clientes.todos(),
      ])
      setDeudas(d)
      setResumen(r)
      setClientes(c)
    } catch (e) {
      setError(e.message || 'No se pudieron cargar las deudas')
    } finally {
      setLoading(false)
    }
  }, [search])

  useEffect(() => {
    const timer = window.setTimeout(() => cargar(''), 0)
    return () => window.clearTimeout(timer)
  }, [cargar])

  function buscar() {
    setSearch(q)
    cargar(q)
  }

  function upsert(updated) {
    setDeudas(prev => {
      const exists = prev.some(d => d.idDeuda === updated.idDeuda)
      return exists ? prev.map(d => d.idDeuda === updated.idDeuda ? updated : d) : [updated, ...prev]
    })
    setPanelDeuda(updated)
    setCreating(false)
    api.deudas.resumen().then(setResumen).catch(() => {})
    api.clientes.todos().then(setClientes).catch(() => {})
  }

  async function eliminarDeuda() {
    if (!deudaEliminar) return
    setEliminando(true)
    setError('')
    try {
      await api.deudas.eliminar(deudaEliminar.idDeuda)
      setDeudas(prev => prev.filter(d => d.idDeuda !== deudaEliminar.idDeuda))
      setPanelDeuda(prev => prev?.idDeuda === deudaEliminar.idDeuda ? null : prev)
      setDeudaEliminar(null)
      const [r, c] = await Promise.all([api.deudas.resumen(), api.clientes.todos()])
      setResumen(r)
      setClientes(c)
    } catch (e) {
      setError(e.message || 'No se pudo eliminar la deuda')
    } finally {
      setEliminando(false)
    }
  }

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 py-6 space-y-5">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-900 dark:text-white">Deudas</h1>
          <p className="text-slate-400 dark:text-stone-500 text-sm mt-0.5">Registro individual de deudas y pagos</p>
        </div>
        <button onClick={() => { setCreating(true); setPanelDeuda(null) }} className="btn-primary text-sm">Nueva deuda</button>
      </div>

      {error && <p className="text-sm text-red-500 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-xl px-4 py-3">{error}</p>}

      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
        {[
          { label: 'Total adeudado', value: fmt(resumen?.totalPendiente), desc: 'Suma de deudas activas' },
          { label: 'Deudores', value: resumen?.cantDeudores ?? '—', desc: 'Clientes o deudores manuales' },
          { label: 'Deudas activas', value: resumen?.cantDeudas ?? '—', desc: 'Deudas sin saldar' },
          { label: 'Mayor deuda', value: fmt(resumen?.mayorDeuda), desc: 'Deuda individual más alta' },
        ].map(({ label, value, desc }) => (
          <div key={label} className="card p-5">
            <p className="text-xs uppercase tracking-wider text-slate-400 dark:text-stone-500 mb-1">{label}</p>
            <p className="text-2xl font-bold text-slate-900 dark:text-white tabular-nums">{value}</p>
            <p className="text-xs text-slate-400 dark:text-stone-500 mt-1">{desc}</p>
          </div>
        ))}
      </div>

      <div className="relative max-w-md flex gap-2">
        <input
          placeholder="Buscar cliente, deudor o factura…"
          className="input w-full"
          value={q}
          onChange={e => setQ(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && buscar()}
        />
        <button onClick={buscar} className="btn-secondary text-sm">Buscar</button>
      </div>

      <div className="card overflow-hidden">
        <table className="w-full table-fixed text-sm">
          <thead>
            <tr className="border-b border-slate-100 dark:border-stone-800">
              <th className="w-24 text-left px-4 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider">Fecha</th>
              <th className="text-left px-4 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider">Cliente / deudor</th>
              <th className="hidden md:table-cell text-left px-4 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider">Descripción</th>
              <th className="w-32 text-right px-4 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider">Deuda actual</th>
              <th className="w-24 text-left px-4 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider">Estado</th>
              <th className="w-32 text-right px-4 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider">Acciones</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 dark:divide-stone-800">
            {loading ? (
              <tr><td colSpan={6} className="px-5 py-12 text-center text-slate-400">Cargando…</td></tr>
            ) : deudas.length === 0 ? (
              <tr><td colSpan={6} className="px-5 py-16 text-center text-slate-400 dark:text-stone-500 text-sm">{search ? 'No se encontraron deudas para esa búsqueda' : 'No hay deudas registradas'}</td></tr>
            ) : deudas.map(d => (
              <tr key={d.idDeuda} className="hover:bg-slate-50 dark:hover:bg-stone-900/50 transition-colors">
                <td className="px-4 py-3 text-slate-600 dark:text-stone-400">{fmtDate(d.fechaDeuda || d.fechaVenta)}</td>
                <td className="px-4 py-3 font-medium text-slate-800 dark:text-stone-200 truncate">{d.nombreCliente || 'Sin cliente'}</td>
                <td className="hidden md:table-cell px-4 py-3 text-slate-600 dark:text-stone-400 truncate" title={d.descripcion || ''}>{d.descripcion || 'Sin descripción'}</td>
                <td className="px-4 py-3 tabular-nums text-right font-semibold text-red-600 dark:text-red-400">{fmt(d.montoPendiente)}</td>
                <td className="px-4 py-3"><span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${ESTADO_STYLES[d.estadoPago] || ''}`}>{d.estadoPago}</span></td>
                <td className="px-4 py-3">
                  <div className="flex items-center justify-end gap-2 flex-wrap">
                    <button onClick={() => { setPanelDeuda(d); setCreating(false) }} className="text-xs text-[#5C7D87] hover:underline font-medium">Ver</button>
                    {d.estadoPago !== 'PAGADO' && (
                      <button onClick={() => { setPanelDeuda(d); setCreating(false) }} className="text-xs text-emerald-700 dark:text-emerald-400 hover:underline font-medium">Pago</button>
                    )}
                    <button onClick={() => setDeudaEliminar(d)} className="text-xs text-red-600 dark:text-red-400 hover:underline font-medium">Eliminar</button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {(panelDeuda || creating) && (
        <DeudaPanel
          deuda={creating ? null : panelDeuda}
          clientes={clientes}
          onClose={() => { setPanelDeuda(null); setCreating(false) }}
          onSaved={upsert}
          onPaid={upsert}
        />
      )}
      {deudaEliminar && (
        <ConfirmModal
          titulo="Eliminar deuda"
          mensaje={`¿Seguro que querés eliminar la deuda de ${deudaEliminar.nombreCliente || 'este deudor'}? Se ocultará del listado sin borrar pagos ni ventas relacionadas.`}
          onConfirmar={eliminarDeuda}
          onCancelar={() => setDeudaEliminar(null)}
          cargando={eliminando}
        />
      )}
    </div>
  )
}
