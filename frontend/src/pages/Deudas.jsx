import { useCallback, useEffect, useState } from 'react'
import { api, FINANCIAL_DATA_CHANGED_EVENT, resolveApiUrl } from '../api/sonograma'
import ConfirmModal from '../components/ConfirmModal'

const ESTADO_STYLES = {
  PENDIENTE: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-white',
  PARCIAL: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-white',
  PAGADO: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-white',
}

const EMPTY_DEUDA = {
  idCliente: '',
  nombreDeudorManual: '',
  mailManual: '',
  instagramManual: '',
  ciManual: '',
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

function nuevoIdempotencyKey() {
  return globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function buildForm(deuda) {
  if (!deuda) return EMPTY_DEUDA
  return {
    idCliente: deuda.idCliente || '',
    nombreDeudorManual: deuda.nombreDeudorManual || '',
    mailManual: deuda.mailManual || '',
    instagramManual: deuda.instagramManual || '',
    ciManual: deuda.ciManual || '',
    descripcion: deuda.descripcion || '',
    montoTotal: deuda.montoTotal ?? '',
    montoPagado: deuda.montoPagado ?? '0',
    fechaDeuda: deuda.fechaDeuda || deuda.fechaVenta || new Date().toISOString().slice(0, 10),
    estadoPago: deuda.estadoPago || 'PENDIENTE',
    notas: deuda.notas || '',
  }
}

function DeudaPanel({ deuda, clientes, onClose, onSaved, onPaid, onDelete }) {
  const [mode, setMode] = useState(deuda?.idDeuda ? 'view' : 'edit')
  const [movimientoId, setMovimientoId] = useState(() => deuda?.movimientos?.[0]?.idDeuda || deuda?.idDeuda)
  const [form, setForm] = useState(() => buildForm(deuda?.movimientos?.[0] || deuda))
  const [payment, setPayment] = useState(() => ({ monto: '', notas: '', numeroRecibo: '', idempotencyKey: nuevoIdempotencyKey() }))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setForm(buildForm(deuda?.movimientos?.[0] || deuda))
      setMovimientoId(deuda?.movimientos?.[0]?.idDeuda || deuda?.idDeuda)
      setMode(deuda?.idDeuda ? 'view' : 'edit')
      setError('')
      setMessage('')
    }, 0)
    return () => window.clearTimeout(timer)
  }, [deuda])

  const movimientos = deuda?.movimientos?.length ? deuda.movimientos : (deuda?.idDeuda ? [deuda] : [])
  const movimiento = movimientos.find(m => m.idDeuda === movimientoId) || movimientos[0] || null

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
      const saved = movimiento?.idDeuda
        ? await api.deudas.actualizar(movimiento.idDeuda, payload())
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
      const updated = await api.deudas.registrarPago(
        movimiento.idDeuda,
        monto,
        payment.notas || null,
        payment.numeroRecibo.trim() || null,
        payment.idempotencyKey,
      )
      onPaid(updated)
      setPayment({ monto: '', notas: '', numeroRecibo: '', idempotencyKey: nuevoIdempotencyKey() })
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
          <p className="text-sm text-slate-400 dark:text-white/60">{deuda?.cantidadMovimientos ? `${deuda.cantidadMovimientos} movimientos asociados` : 'Deuda manual editable'}</p>
        </div>
        <div className="flex items-center gap-2">
          {deuda?.idDeuda && movimiento && <button onClick={() => { setForm(buildForm(movimiento)); setMode(mode === 'edit' ? 'view' : 'edit') }} className="btn-secondary text-sm">{mode === 'edit' ? 'Ver' : 'Editar'}</button>}
          <button onClick={onClose} className="p-1.5 rounded-lg text-slate-400 dark:text-white/70 hover:text-slate-600 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-stone-800">✕</button>
        </div>
      </div>

      <div className="p-5 space-y-5">
        {message && <p className="text-xs text-emerald-600 dark:text-white bg-emerald-50 dark:bg-emerald-900/20 border border-emerald-200 dark:border-emerald-800 rounded-lg px-3 py-2">{message}</p>}
        {error && <p className="text-xs text-red-600 dark:text-white bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg px-3 py-2">{error}</p>}

        {mode === 'edit' ? (
          <form onSubmit={save} className="space-y-4">
            {movimientos.length > 1 && (
              <div>
                <label className="block text-xs text-slate-500 dark:text-white/70 mb-1">Movimiento a editar</label>
                <select className="input w-full" value={movimiento?.idDeuda || ''} onChange={e => { const next = movimientos.find(m => m.idDeuda === Number(e.target.value)); setMovimientoId(Number(e.target.value)); setForm(buildForm(next)) }}>
                  {movimientos.map(m => <option key={m.idDeuda} value={m.idDeuda}>{fmtDate(m.fechaDeuda || m.fechaVenta)} · {m.numeroRecibo ? `Boleta ${m.numeroRecibo}` : `Deuda #${m.idDeuda}`} · {fmt(m.montoPendiente)}</option>)}
                </select>
              </div>
            )}
            <div>
              <label className="block text-xs text-slate-500 dark:text-white/70 mb-1">Cliente existente</label>
              <select className="input w-full" value={form.idCliente || ''} onChange={e => set('idCliente', e.target.value)}>
                <option value="">Sin cliente asociado</option>
                {clientes.map(c => <option key={c.idCliente} value={c.idCliente}>{c.nombre} {c.apellido || ''}</option>)}
              </select>
            </div>
            {!form.idCliente && (
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs text-slate-500 dark:text-white/70 mb-1">Nombre deudor *</label>
                  <input className="input w-full" value={form.nombreDeudorManual} onChange={e => set('nombreDeudorManual', e.target.value)} />
                </div>
                <div>
                  <label className="block text-xs text-slate-500 dark:text-white/70 mb-1">CI</label>
                  <input className="input w-full" value={form.ciManual} onChange={e => set('ciManual', e.target.value)} />
                </div>
                <div>
                  <label className="block text-xs text-slate-500 dark:text-white/70 mb-1">Mail</label>
                  <input className="input w-full" value={form.mailManual} onChange={e => set('mailManual', e.target.value)} />
                </div>
                <div>
                  <label className="block text-xs text-slate-500 dark:text-white/70 mb-1">Instagram</label>
                  <input className="input w-full" value={form.instagramManual} onChange={e => set('instagramManual', e.target.value)} />
                </div>
              </div>
            )}
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs text-slate-500 dark:text-white/70 mb-1">Fecha deuda</label>
                <input type="date" className="input w-full" value={form.fechaDeuda} onChange={e => set('fechaDeuda', e.target.value)} />
              </div>
              <div>
                <label className="block text-xs text-slate-500 dark:text-white/70 mb-1">Total *</label>
                <input type="number" step="0.01" min="0" className="input w-full" value={form.montoTotal} onChange={e => set('montoTotal', e.target.value)} />
              </div>
              <div>
                <label className="block text-xs text-slate-500 dark:text-white/70 mb-1">Pagado</label>
                <input type="number" step="0.01" min="0" className="input w-full" value={form.montoPagado} onChange={e => set('montoPagado', e.target.value)} />
              </div>
            </div>
            <div>
              <label className="block text-xs text-slate-500 dark:text-white/70 mb-1">Descripción</label>
              <textarea className="input w-full min-h-20 resize-y" value={form.descripcion} onChange={e => set('descripcion', e.target.value)} />
            </div>
            <div>
              <label className="block text-xs text-slate-500 dark:text-white/70 mb-1">Notas</label>
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
              <div className="card p-4"><p className="text-xs text-slate-400 dark:text-white/60">Total consolidado</p><p className="text-lg font-bold text-slate-900 dark:text-white">{fmt(current.montoTotal)}</p></div>
              <div className="card p-4"><p className="text-xs text-slate-400 dark:text-white/60">Pendiente</p><p className="text-lg font-bold text-red-600 dark:text-white">{fmt(current.montoPendiente)}</p></div>
            </div>
            <div className="grid grid-cols-2 gap-3 text-sm">
              {[['Mail', current.mailManual], ['Instagram', current.instagramManual], ['CI', current.ciManual], ['Movimientos', current.cantidadMovimientos]].map(([label, value]) => (
                <div key={label}><p className="text-xs uppercase tracking-wider text-slate-400 dark:text-white/60 mb-1">{label}</p><p className="text-slate-700 dark:text-white">{value || '—'}</p></div>
              ))}
            </div>
            <div className="space-y-4">
              {movimientos.map(m => (
                <div key={m.idDeuda} className="rounded-xl border border-slate-200 dark:border-stone-800 p-4 space-y-3">
                  <div className="flex items-start justify-between gap-3">
                    <div><p className="text-sm font-semibold text-slate-800 dark:text-white">Movimiento #{m.idDeuda}</p><p className="text-xs text-slate-400 dark:text-white/60">{fmtDate(m.fechaDeuda || m.fechaVenta)} · {m.numeroRecibo ? `Boleta ${m.numeroRecibo}` : 'Sin número de boleta'}</p></div>
                    <div className="flex items-center gap-3">{m.estadoPago !== 'PAGADO' && <button type="button" onClick={() => { setMovimientoId(m.idDeuda); setPayment({ monto: '', notas: '', numeroRecibo: '', idempotencyKey: nuevoIdempotencyKey() }) }} className="text-xs text-emerald-700 dark:text-white hover:underline font-medium">Pago</button>}<button type="button" onClick={() => onDelete(m)} className="text-xs text-red-600 dark:text-white hover:underline font-medium">Eliminar</button></div>
                  </div>
                  <div className="grid grid-cols-3 gap-2 text-sm"><div><p className="text-xs text-slate-400 dark:text-white/60">Original</p><p className="font-semibold text-slate-900 dark:text-white">{fmt(m.montoTotal)}</p></div><div><p className="text-xs text-slate-400 dark:text-white/60">Pagado</p><p className="font-semibold text-slate-900 dark:text-white">{fmt(m.montoPagado)}</p></div><div><p className="text-xs text-slate-400 dark:text-white/60">Restante</p><p className="font-semibold text-red-600 dark:text-white">{fmt(m.montoPendiente)}</p></div></div>
                  {m.detalles?.length > 0 && <div className="space-y-2"><p className="text-xs font-semibold text-slate-500 dark:text-white/70 uppercase tracking-wider">Discos vendidos</p>{m.detalles.map((detalle, index) => <div key={detalle.idDetalle || detalle.idDisco || index} className="rounded-lg border border-slate-100 dark:border-stone-800 px-3 py-2 flex items-center gap-3">{detalle.imagenUrl && <img src={resolveApiUrl(detalle.imagenUrl)} alt={detalle.album || 'Portada del disco'} className="w-14 h-14 rounded-lg object-cover bg-slate-100 dark:bg-stone-800 flex-shrink-0" />}<div className="min-w-0"><p className="text-sm font-medium text-slate-800 dark:text-white">{detalle.manualItem ? detalle.descripcion : `${detalle.artista} — ${detalle.album}`}</p><p className="text-xs text-slate-400 dark:text-white/60">{detalle.codigoInterno || 'Sin código'} · Cant. {detalle.cantidad || 1} · {fmt(detalle.precioUnitario)}</p></div></div>)}</div>}
                  {(m.descripcion || m.notas) && <div className="space-y-1">{m.descripcion && <p className="text-sm text-slate-600 dark:text-white/80 whitespace-pre-wrap">{m.descripcion}</p>}{m.notas && <p className="text-sm text-slate-500 dark:text-white/60 whitespace-pre-wrap">{m.notas}</p>}</div>}
                  {m.estadoPago !== 'PAGADO' && movimientoId === m.idDeuda && <form onSubmit={pay} className="rounded-xl bg-slate-50 dark:bg-stone-900/60 p-3 space-y-3"><h3 className="text-xs font-semibold text-slate-500 dark:text-white/70 uppercase tracking-wider">Registrar pago</h3><div className="grid grid-cols-2 gap-3"><input type="number" step="0.01" min="0.01" max={m.montoPendiente} className="input w-full" placeholder={`Máx. ${fmt(m.montoPendiente)}`} value={payment.monto} onChange={e => setPayment(p => ({ ...p, monto: e.target.value }))} /><input className="input w-full" placeholder="Notas" value={payment.notas} onChange={e => setPayment(p => ({ ...p, notas: e.target.value }))} /><label className="col-span-2 block text-xs text-slate-500 dark:text-white/70">Número de Boleta <span className="text-slate-400 dark:text-white/60">(Opcional)</span><input className="input w-full mt-1" placeholder="Ingresar número de boleta" value={payment.numeroRecibo} onChange={e => setPayment(p => ({ ...p, numeroRecibo: e.target.value }))} /></label></div><button type="submit" disabled={saving} className="btn-primary text-sm disabled:opacity-50">{saving ? 'Registrando…' : 'Confirmar pago'}</button></form>}
                  <div><p className="text-xs font-semibold text-slate-500 dark:text-white/70 uppercase tracking-wider mb-2">Historial de pagos</p>{(m.pagos || []).length === 0 ? <p className="text-sm text-slate-400 dark:text-white/60">Sin pagos registrados.</p> : <div className="space-y-2">{m.pagos.map(p => <div key={p.idPagoDeuda} className="rounded-lg border border-slate-100 dark:border-stone-800 px-3 py-2 flex items-center justify-between gap-3 text-sm"><div><p className="font-medium text-slate-800 dark:text-white">{fmt(p.monto)}</p><p className="text-xs text-slate-400 dark:text-white/60">{p.numeroRecibo ? `Número de boleta: ${p.numeroRecibo}` : 'Sin número de boleta'}</p>{p.notas && <p className="text-xs text-slate-400 dark:text-white/60">{p.notas}</p>}</div><span className="text-xs text-slate-400 dark:text-white/60">{fmtDate(p.fechaPago)}</span></div>)}</div>}</div>
                </div>
              ))}
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

  const cargar = useCallback(async (query = search, focusMovementId = null) => {
    setLoading(true)
    setError('')
    try {
      const [d, r, c] = await Promise.all([
        api.deudas.listar(query),
        api.deudas.resumen(),
        api.clientes.todos(),
      ])
      setDeudas(d)
      if (focusMovementId != null) {
        const focused = d.find(row => row.movimientos?.some(m => m.idDeuda === focusMovementId))
        setPanelDeuda(focused || null)
      }
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
    function refreshFromFinancialChange() {
      cargar()
    }
    window.addEventListener(FINANCIAL_DATA_CHANGED_EVENT, refreshFromFinancialChange)
    return () => {
      window.clearTimeout(timer)
      window.removeEventListener(FINANCIAL_DATA_CHANGED_EVENT, refreshFromFinancialChange)
    }
  }, [cargar])

  function buscar() {
    setSearch(q)
    cargar(q)
  }

  async function upsert(updated) {
    setCreating(false)
    await cargar(search, updated?.idDeuda)
  }

  async function eliminarDeuda() {
    if (!deudaEliminar) return
    setEliminando(true)
    setError('')
    try {
      await api.deudas.eliminar(deudaEliminar.idDeuda)
      setDeudaEliminar(null)
      setPanelDeuda(null)
      await cargar(search)
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
          <p className="text-slate-400 dark:text-white/60 text-sm mt-0.5">Registro individual de deudas y pagos</p>
        </div>
        <button onClick={() => { setCreating(true); setPanelDeuda(null) }} className="btn-primary text-sm">Nueva deuda</button>
      </div>

      {error && <p className="text-sm text-red-500 dark:text-white bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-xl px-4 py-3">{error}</p>}

      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
        {[
          { label: 'Total adeudado', value: fmt(resumen?.totalPendiente), desc: 'Suma de deudas activas' },
          { label: 'Deudores', value: resumen?.cantDeudores ?? '—', desc: 'Clientes o deudores manuales' },
          { label: 'Deudas activas', value: resumen?.cantDeudas ?? '—', desc: 'Deudas sin saldar' },
          { label: 'Mayor deuda', value: fmt(resumen?.mayorDeuda), desc: 'Deuda individual más alta' },
        ].map(({ label, value, desc }) => (
          <div key={label} className="card p-5">
            <p className="text-xs uppercase tracking-wider text-slate-400 dark:text-white/60 mb-1">{label}</p>
            <p className="text-2xl font-bold text-slate-900 dark:text-white tabular-nums">{value}</p>
            <p className="text-xs text-slate-400 dark:text-white/60 mt-1">{desc}</p>
          </div>
        ))}
      </div>

      <div className="relative max-w-md flex gap-2">
        <input
          placeholder="Buscar cliente, deudor o recibo…"
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
              <th className="w-24 text-left px-4 py-3 text-xs font-semibold text-slate-500 dark:text-white/70 uppercase tracking-wider">Fecha</th>
              <th className="text-left px-4 py-3 text-xs font-semibold text-slate-500 dark:text-white/70 uppercase tracking-wider">Cliente / deudor</th>
              <th className="hidden md:table-cell text-left px-4 py-3 text-xs font-semibold text-slate-500 dark:text-white/70 uppercase tracking-wider">Descripción</th>
              <th className="w-32 text-right px-4 py-3 text-xs font-semibold text-slate-500 dark:text-white/70 uppercase tracking-wider">Deuda actual</th>
              <th className="w-24 text-left px-4 py-3 text-xs font-semibold text-slate-500 dark:text-white/70 uppercase tracking-wider">Estado</th>
              <th className="w-32 text-right px-4 py-3 text-xs font-semibold text-slate-500 dark:text-white/70 uppercase tracking-wider">Acciones</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 dark:divide-stone-800">
            {loading ? (
              <tr><td colSpan={6} className="px-5 py-12 text-center text-slate-400 dark:text-white/70">Cargando…</td></tr>
            ) : deudas.length === 0 ? (
              <tr><td colSpan={6} className="px-5 py-16 text-center text-slate-400 dark:text-white/60 text-sm">{search ? 'No se encontraron deudas para esa búsqueda' : 'No hay deudas registradas'}</td></tr>
            ) : deudas.map(d => (
              <tr key={d.grupoKey || d.idDeuda} className="hover:bg-slate-50 dark:hover:bg-stone-900/50 transition-colors">
                <td className="px-4 py-3 text-slate-600 dark:text-white/70">{fmtDate(d.fechaDeuda || d.fechaVenta)}</td>
                <td className="px-4 py-3 font-medium text-slate-800 dark:text-white truncate">{d.nombreCliente || 'Sin cliente'}</td>
                <td className="hidden md:table-cell px-4 py-3 text-slate-600 dark:text-white/70 truncate">{d.cantidadMovimientos} movimiento{d.cantidadMovimientos === 1 ? '' : 's'}</td>
                <td className="px-4 py-3 tabular-nums text-right font-semibold text-red-600 dark:text-white">{fmt(d.montoPendiente)}</td>
                <td className="px-4 py-3"><span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${ESTADO_STYLES[d.estadoPago] || ''}`}>{d.estadoPago}</span></td>
                <td className="px-4 py-3">
                  <div className="flex items-center justify-end gap-2 flex-wrap">
                    <button onClick={() => { setPanelDeuda(d); setCreating(false) }} className="text-xs text-[#5C7D87] dark:text-white hover:underline font-medium">Ver</button>
                    {d.estadoPago !== 'PAGADO' && (
                      <button onClick={() => { setPanelDeuda(d); setCreating(false) }} className="text-xs text-emerald-700 dark:text-white hover:underline font-medium">Pago</button>
                    )}
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
          onDelete={setDeudaEliminar}
        />
      )}
      {deudaEliminar && <ConfirmModal titulo="Eliminar movimiento" mensaje={`¿Seguro que querés ocultar este movimiento de ${deudaEliminar.nombreCliente || 'este deudor'}? No se borrarán pagos ni ventas relacionadas.`} onConfirmar={eliminarDeuda} onCancelar={() => setDeudaEliminar(null)} cargando={eliminando} />}
    </div>
  )
}
