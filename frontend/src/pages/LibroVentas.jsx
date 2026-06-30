import { useState, useEffect, useCallback } from 'react'
import { api, resolveApiUrl } from '../api/sonograma'
import { redirectIfUnauthorized } from '../api/session'
import ConfirmModal from '../components/ConfirmModal'

const ESTADO_PAGO_STYLES = {
  PAGADO:   'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400',
  PARCIAL:  'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400',
  PENDIENTE:'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
}

function SalePanel({ venta, selectedDisk, onDiskClick, onClose, onEdit, onCancel }) {
  if (!venta) return null
  const detalles = venta.detalles?.length ? venta.detalles : [{
    idDisco: venta.idDisco,
    artista: venta.artista,
    album: venta.album,
    codigoInterno: '',
    precioUnitario: venta.precioVenta,
  }]
  const cover = selectedDisk?.imagenUrl
  return (
    <aside className="fixed inset-y-0 right-0 z-50 w-full max-w-lg bg-white dark:bg-stone-950 border-l border-slate-200 dark:border-stone-800 shadow-2xl overflow-y-auto">
      <div className="sticky top-0 bg-white/95 dark:bg-stone-950/95 backdrop-blur px-5 py-4 border-b border-slate-100 dark:border-stone-800 flex items-start justify-between gap-3">
        <div>
          <h2 className="font-bold text-slate-900 dark:text-white">{venta.clienteNombreSnapshot || `${venta.nombreCliente || ''} ${venta.apellidoCliente || ''}`.trim()}</h2>
          <p className="text-sm text-slate-400 dark:text-stone-500">{fmtDate(venta.fechaVenta)}</p>
        </div>
        <button onClick={onClose} className="text-slate-400 hover:text-slate-700 dark:hover:text-white">✕</button>
      </div>
      <div className="p-5 space-y-5">
        {cover && <img src={resolveApiUrl(cover)} alt="" className="w-40 h-40 rounded-xl object-cover bg-slate-100 dark:bg-stone-800 mx-auto" />}
        <div className="grid grid-cols-2 gap-3">
          {[
            ['Total', fmt(venta.totalFinal)],
            ['Método de pago', venta.medioPago],
            ['Estado pago', venta.estadoPago],
            ['Descuento', venta.descuentoPorcentaje != null ? `${venta.descuentoPorcentaje}%` : '0%'],
            ['Monto pagado', fmt(venta.montoPagado)],
            ['Deuda pendiente', fmt(venta.montoDeuda)],
          ].map(([label, value]) => (
            <div key={label} className="rounded-lg border border-slate-100 dark:border-stone-800 bg-slate-50 dark:bg-stone-900 px-3 py-2">
              <p className="text-[10px] uppercase tracking-wider text-slate-400 dark:text-stone-500">{label}</p>
              <p className="text-sm text-slate-800 dark:text-stone-200">{value || '—'}</p>
            </div>
          ))}
        </div>
        {venta.observaciones && <p className="text-sm text-slate-500 dark:text-stone-400 whitespace-pre-wrap">{venta.observaciones}</p>}
        <div>
          <p className="text-xs font-semibold text-slate-500 dark:text-stone-400 uppercase tracking-wider mb-2">Discos vendidos</p>
          <div className="space-y-2">
            {detalles.map(d => (
              <button key={d.idDetalle || d.idDisco} onClick={() => onDiskClick(d)} className="w-full text-left rounded-lg border border-slate-100 dark:border-stone-800 px-3 py-2 hover:border-[#7E9FA8]/50">
                <p className="text-sm font-medium text-slate-800 dark:text-stone-200">{d.artista} — {d.album}</p>
                <p className="text-xs text-slate-400 dark:text-stone-500">{d.codigoInterno || 'Sin código'} · {fmt(d.precioUnitario)}</p>
              </button>
            ))}
          </div>
        </div>
        <div className="flex gap-2">
          <button onClick={onEdit} className="btn-primary flex-1">Editar</button>
          <button onClick={onCancel} className="btn-secondary flex-1 text-red-600 dark:text-red-400">Cancelar venta</button>
        </div>
      </div>
    </aside>
  )
}

function EditSaleModal({ venta, onClose, onSaved }) {
  const detallesIniciales = venta.detalles?.length ? venta.detalles : [{
    idDisco: venta.idDisco,
    artista: venta.artista,
    album: venta.album,
    precioUnitario: venta.precioVenta,
  }]
  const [form, setForm] = useState({
    descuentoPorcentaje: venta.descuentoPorcentaje ?? 0,
    medioPago: venta.medioPago || '',
    montoPagado: venta.montoPagado ?? '',
    observaciones: venta.observaciones || '',
    detalles: detallesIniciales.map(d => ({ ...d, precioUnitario: d.precioUnitario ?? 0 })),
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  function setDetalle(idDisco, value) {
    setForm(prev => ({
      ...prev,
      detalles: prev.detalles.map(d => d.idDisco === idDisco ? { ...d, precioUnitario: value } : d),
    }))
  }

  async function submit(e) {
    e.preventDefault()
    setSaving(true)
    setError('')
    try {
      const subtotal = form.detalles.reduce((sum, d) => sum + Number(d.precioUnitario || 0), 0)
      const descuento = subtotal * Number(form.descuentoPorcentaje || 0) / 100
      const total = subtotal - descuento
      const payload = {
        idCliente: venta.idCliente,
        canalVenta: venta.canalVenta || 'LOCAL',
        total,
        costoEnvio: Number(venta.costoEnvio || 0),
        tipoEntrega: venta.tipoEntrega || 'RETIRO',
        descuentoPorcentaje: Number(form.descuentoPorcentaje || 0),
        medioPago: form.medioPago || null,
        montoPagado: form.montoPagado === '' ? undefined : Number(form.montoPagado),
        observaciones: form.observaciones || null,
        detalles: form.detalles.length > 1 ? form.detalles.map(d => ({
          idDisco: d.idDisco,
          precioUnitario: Number(d.precioUnitario || 0),
        })) : undefined,
        idDisco: form.detalles.length === 1 ? form.detalles[0].idDisco : undefined,
        precioVenta: form.detalles.length === 1 ? Number(form.detalles[0].precioUnitario || 0) : undefined,
      }
      onSaved(await api.ventas.actualizar(venta.idVenta, payload))
    } catch (e) {
      setError(e.message || 'No se pudo editar la venta')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-[60] bg-black/60 flex items-center justify-center p-4" onClick={onClose}>
      <form onSubmit={submit} className="w-full max-w-lg bg-white dark:bg-stone-950 rounded-xl border border-slate-200 dark:border-stone-800 shadow-2xl p-5 space-y-4" onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between">
          <h2 className="font-bold text-slate-900 dark:text-white">Editar venta</h2>
          <button type="button" onClick={onClose} className="text-slate-400 hover:text-white">✕</button>
        </div>
        {error && <p className="text-xs text-red-500 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg px-3 py-2">{error}</p>}
        <div className="space-y-2">
          {form.detalles.map(d => (
            <div key={d.idDisco} className="grid grid-cols-[1fr_120px] gap-3 items-center">
              <p className="text-sm text-slate-700 dark:text-stone-300 truncate">{d.artista} — {d.album}</p>
              <input type="number" min="0" step="0.01" className="input text-right" value={d.precioUnitario} onChange={e => setDetalle(d.idDisco, e.target.value)} />
            </div>
          ))}
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Descuento %</label>
            <input type="number" min="0" step="0.01" className="input w-full" value={form.descuentoPorcentaje} onChange={e => setForm(f => ({ ...f, descuentoPorcentaje: e.target.value }))} />
          </div>
          <div>
            <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Monto pagado</label>
            <input type="number" min="0" step="0.01" className="input w-full" value={form.montoPagado} onChange={e => setForm(f => ({ ...f, montoPagado: e.target.value }))} />
          </div>
          <div className="col-span-2">
            <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Método de pago</label>
            <select className="input w-full" value={form.medioPago} onChange={e => setForm(f => ({ ...f, medioPago: e.target.value }))}>
              <option value="">Sin definir</option>
              <option value="EFECTIVO">Efectivo</option>
              <option value="TRANSFERENCIA">Transferencia</option>
              <option value="MERCADOPAGO">Mercado Pago</option>
              <option value="TARJETA">Tarjeta</option>
              <option value="OTRO">Otro</option>
            </select>
          </div>
        </div>
        <textarea className="input w-full min-h-20 resize-y" value={form.observaciones} onChange={e => setForm(f => ({ ...f, observaciones: e.target.value }))} placeholder="Observaciones" />
        <div className="flex justify-end gap-2">
          <button type="button" onClick={onClose} className="btn-secondary text-sm">Cancelar</button>
          <button disabled={saving} className="btn-primary text-sm disabled:opacity-50">{saving ? 'Guardando…' : 'Guardar cambios'}</button>
        </div>
      </form>
    </div>
  )
}

function fmt(n) {
  if (n == null) return '—'
  return `$${Number(n).toLocaleString('es-UY', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function fmtDate(s) {
  if (!s) return '—'
  return new Date(s).toLocaleDateString('es-UY', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

export default function LibroVentas() {
  const [ventas, setVentas] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [filters, setFilters] = useState({ desde: '', hasta: '', q: '' })
  const [applied, setApplied] = useState({})
  const [ventaPanel, setVentaPanel] = useState(null)
  const [selectedDisk, setSelectedDisk] = useState(null)
  const [ventaCancelar, setVentaCancelar] = useState(null)
  const [cancelando, setCancelando] = useState(false)
  const [editando, setEditando] = useState(null)

  const cargar = useCallback(async (params) => {
    setLoading(true)
    setError(null)
    try {
      const data = await api.libro.listar(params)
      setVentas(data)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [])

  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { cargar({}) }, [cargar])

  function aplicar() {
    const params = {}
    if (filters.desde) params.desde = filters.desde
    if (filters.hasta) params.hasta = filters.hasta
    if (filters.q) params.q = filters.q
    setApplied(params)
    cargar(params)
  }

  function limpiar() {
    setFilters({ desde: '', hasta: '', q: '' })
    setApplied({})
    cargar({})
  }

  function exportar() {
    const token = localStorage.getItem('token')
    const url = api.libro.exportarUrl(applied)
    const a = document.createElement('a')
    a.href = url
    a.setAttribute('download', 'libro-ventas.xlsx')
    // attach token via fetch + blob since we can't set headers on anchor
    fetch(url, { headers: { Authorization: `Bearer ${token}` } })
      .then(r => {
        if (redirectIfUnauthorized(r)) throw new Error('Tu sesión venció. Ingresá nuevamente.')
        if (!r.ok) throw new Error('No se pudo exportar el libro de ventas')
        return r.blob()
      })
      .then(blob => {
        const blobUrl = URL.createObjectURL(blob)
        a.href = blobUrl
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)
        URL.revokeObjectURL(blobUrl)
      })
      .catch(e => setError(e.message))
  }

  const totalFinal = ventas.reduce((s, v) => s + (v.totalFinal || 0), 0)
  const totalGanancia = ventas.reduce((s, v) => s + (v.gananciaEstimada || 0), 0)

  async function cancelarVenta() {
    if (!ventaCancelar) return
    setCancelando(true)
    setError(null)
    try {
      await api.ventas.cancelar(ventaCancelar.idVenta)
      setVentas(prev => prev.filter(v => v.idVenta !== ventaCancelar.idVenta))
      setVentaPanel(prev => prev?.idVenta === ventaCancelar.idVenta ? null : prev)
      setVentaCancelar(null)
    } catch (e) {
      setError(e.message || 'No se pudo cancelar la venta')
    } finally {
      setCancelando(false)
    }
  }

  async function seleccionarDiscoDetalle(detalle) {
    setSelectedDisk(detalle)
    if (!detalle?.idDisco) return
    try {
      const disco = await api.discos.porId(detalle.idDisco)
      setSelectedDisk({ ...detalle, imagenUrl: disco.imagenUrl })
    } catch {
      setSelectedDisk(detalle)
    }
  }

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 py-6 space-y-5">
      <div className="flex items-center justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-xl font-bold text-slate-900 dark:text-white">Libro de Ventas</h1>
          <p className="text-slate-400 dark:text-stone-500 text-sm mt-0.5">
            Registro completo de todas las transacciones
          </p>
        </div>
        <button
          onClick={exportar}
          disabled={ventas.length === 0}
          className="btn-primary flex items-center gap-2 disabled:opacity-40"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M3 16.5v2.25A2.25 2.25 0 0 0 5.25 21h13.5A2.25 2.25 0 0 0 21 18.75V16.5M16.5 12 12 16.5m0 0L7.5 12m4.5 4.5V3" />
          </svg>
          Exportar Excel
        </button>
      </div>

      {/* Filtros */}
      <div className="card p-4">
        <div className="flex flex-wrap gap-3 items-end">
          <div>
            <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Desde</label>
            <input
              type="date"
              value={filters.desde}
              onChange={e => setFilters(f => ({ ...f, desde: e.target.value }))}
              className="input text-sm"
            />
          </div>
          <div>
            <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Hasta</label>
            <input
              type="date"
              value={filters.hasta}
              onChange={e => setFilters(f => ({ ...f, hasta: e.target.value }))}
              className="input text-sm"
            />
          </div>
          <div>
            <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Buscar</label>
            <input
              type="text"
              placeholder="Cliente, artista, álbum…"
              value={filters.q}
              onChange={e => setFilters(f => ({ ...f, q: e.target.value }))}
              onKeyDown={e => e.key === 'Enter' && aplicar()}
              className="input text-sm w-52"
            />
          </div>
          <button onClick={aplicar} className="btn-primary text-sm">Filtrar</button>
          <button onClick={limpiar} className="btn-secondary text-sm">Limpiar</button>
        </div>
      </div>

      {/* Totales */}
      {ventas.length > 0 && (
        <div className="grid grid-cols-3 gap-4">
          {[
            { label: 'Ventas', value: ventas.length },
            { label: 'Total facturado', value: fmt(totalFinal) },
            { label: 'Ganancia estimada', value: fmt(totalGanancia) },
          ].map(({ label, value }) => (
            <div key={label} className="card p-4 text-center">
              <p className="text-xs uppercase tracking-wider text-slate-400 dark:text-stone-500">{label}</p>
              <p className="text-xl font-bold text-slate-900 dark:text-white mt-1 tabular-nums">{value}</p>
            </div>
          ))}
        </div>
      )}

      {/* Tabla */}
      <div className="card overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-100 dark:border-stone-800">
                {['Fecha', 'Cliente', 'Artista / Álbum', 'Medio Pago', 'Total', 'Estado Pago', 'Ganancia'].map(h => (
                  <th key={h} className="text-left px-4 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider whitespace-nowrap">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-stone-800">
              {loading ? (
                <tr><td colSpan={7} className="px-4 py-12 text-center text-slate-400">Cargando…</td></tr>
              ) : error ? (
                <tr><td colSpan={7} className="px-4 py-12 text-center text-red-500">{error}</td></tr>
              ) : ventas.length === 0 ? (
                <tr><td colSpan={7} className="px-4 py-12 text-center text-slate-400 dark:text-stone-500">No hay ventas</td></tr>
              ) : ventas.map(v => (
                <tr key={v.idVenta} onClick={() => { setVentaPanel(v); setSelectedDisk(null) }} className="hover:bg-slate-50 dark:hover:bg-stone-900/50 transition-colors cursor-pointer">
                  <td className="px-4 py-3 whitespace-nowrap text-slate-700 dark:text-stone-300">
                    {fmtDate(v.fechaVenta)}
                  </td>
                  <td className="px-4 py-3 text-slate-700 dark:text-stone-300">
                    {v.clienteNombreSnapshot || `${v.nombreCliente} ${v.apellidoCliente || ''}`.trim()}
                  </td>
                  <td className="px-4 py-3">
                    {v.detalles && v.detalles.length > 1 ? (
                      <div>
                        <div className="font-medium text-slate-800 dark:text-stone-200 text-xs">Varios ({v.detalles.length} discos)</div>
                        <div className="text-slate-400 dark:text-stone-500 text-xs truncate max-w-[160px]">{v.detalles.map(d => d.artista).join(', ')}</div>
                      </div>
                    ) : (
                      <>
                        <div className="font-medium text-slate-800 dark:text-stone-200 text-xs">{v.artista}</div>
                        <div className="text-slate-500 dark:text-stone-500 text-xs">{v.album}</div>
                      </>
                    )}
                  </td>
                  <td className="px-4 py-3 text-slate-600 dark:text-stone-400 whitespace-nowrap">
                    {v.medioPago || '—'}
                  </td>
                  <td className="px-4 py-3 font-mono tabular-nums font-semibold text-slate-800 dark:text-stone-200 whitespace-nowrap">
                    {fmt(v.totalFinal)}
                  </td>
                  <td className="px-4 py-3">
                    {v.estadoPago ? (
                      <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${ESTADO_PAGO_STYLES[v.estadoPago] || ''}`}>
                        {v.estadoPago}
                      </span>
                    ) : '—'}
                  </td>
                  <td className="px-4 py-3 font-mono tabular-nums text-emerald-600 dark:text-emerald-400 whitespace-nowrap">
                    {fmt(v.gananciaEstimada)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
      <SalePanel
        venta={ventaPanel}
        selectedDisk={selectedDisk}
        onDiskClick={seleccionarDiscoDetalle}
        onClose={() => { setVentaPanel(null); setSelectedDisk(null) }}
        onEdit={() => setEditando(ventaPanel)}
        onCancel={() => setVentaCancelar(ventaPanel)}
      />
      {editando && (
        <EditSaleModal
          venta={editando}
          onClose={() => setEditando(null)}
          onSaved={(updated) => {
            setVentas(prev => prev.map(v => v.idVenta === updated.idVenta ? updated : v))
            setVentaPanel(updated)
            setEditando(null)
          }}
        />
      )}
      {ventaCancelar && (
        <ConfirmModal
          titulo="Cancelar venta"
          mensaje="¿Seguro que querés cancelar esta venta? Se restaurará el stock y se ocultará la deuda asociada si existe."
          onConfirmar={cancelarVenta}
          onCancelar={() => setVentaCancelar(null)}
          cargando={cancelando}
        />
      )}
    </div>
  )
}
