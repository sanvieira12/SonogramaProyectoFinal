import { useState, useEffect, useCallback } from 'react'
import { api, FINANCIAL_DATA_CHANGED_EVENT, resolveApiUrl } from '../api/sonograma'
import { redirectIfUnauthorized } from '../api/session'
import ConfirmModal from '../components/ConfirmModal'
import { businessDateInMontevideo } from '../utils/businessDate'

const ESTADO_PAGO_STYLES = {
  PAGADO:   'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400',
  PARCIAL:  'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400',
  PENDIENTE:'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
}

function fechaInputLocal(date = new Date()) {
  return businessDateInMontevideo(date)
}

function rangoPeriodo(mes, hoy = fechaInputLocal()) {
  const [year, month] = mes.split('-').map(Number)
  const hasta = mes === hoy.slice(0, 7)
    ? hoy
    : `${mes}-${String(new Date(year, month, 0).getDate()).padStart(2, '0')}`
  return { desde: `${mes}-01`, hasta }
}

function detallesParaEditar(venta) {
  return venta?.detalles?.length ? venta.detalles : [{
    idDisco: venta?.idDisco,
    artista: venta?.artista,
    album: venta?.album,
    precioUnitario: venta?.precioVenta,
  }]
}

function formularioVenta(venta) {
  return {
    descuentoPorcentaje: venta?.descuentoPorcentaje ?? 0,
    medioPago: venta?.medioPago || '',
    numeroRecibo: venta?.numeroRecibo || '',
    montoPagado: venta?.montoPagado ?? '',
    observaciones: venta?.observaciones || '',
    detalles: detallesParaEditar(venta).map(d => ({ ...d, precioUnitario: d.precioUnitario ?? 0 })),
  }
}

function numeroCantidad(detalle) {
  const cantidad = Number(detalle?.cantidad)
  return Number.isFinite(cantidad) && cantidad > 0 ? cantidad : 1
}

function redondearMoneda(value) {
  return Math.round((Number(value || 0) + Number.EPSILON) * 100) / 100
}

function vistaPreviaVenta(form) {
  const subtotal = redondearMoneda(form.detalles.reduce((sum, detalle) => (
    sum + Number(detalle.precioUnitario || 0) * numeroCantidad(detalle)
  ), 0))
  const descuentoPorcentaje = Number(form.descuentoPorcentaje || 0)
  const descuento = redondearMoneda(subtotal * descuentoPorcentaje / 100)
  const total = redondearMoneda(subtotal - descuento)
  const montoPagado = form.montoPagado === '' ? total : Number(form.montoPagado || 0)
  const deuda = redondearMoneda(Math.max(total - montoPagado, 0))
  const estadoPago = deuda === 0
    ? 'PAGADO'
    : montoPagado === 0 && deuda > 0
      ? 'PENDIENTE'
      : 'PARCIAL'
  return { subtotal, descuento, total, montoPagado, deuda, estadoPago }
}

function validarFormularioVenta(form, preview) {
  for (let index = 0; index < form.detalles.length; index += 1) {
    const value = form.detalles[index].precioUnitario
    if (value === '' || !Number.isFinite(Number(value)) || Number(value) <= 0) {
      return `Ingresá un precio de venta válido para el ítem ${index + 1}.`
    }
  }

  const descuento = Number(form.descuentoPorcentaje || 0)
  if (!Number.isFinite(descuento) || descuento < 0 || descuento > 100) {
    return 'El descuento debe estar entre 0% y 100%.'
  }

  if (form.montoPagado !== '') {
    const montoPagado = Number(form.montoPagado)
    if (!Number.isFinite(montoPagado) || montoPagado < 0) {
      return 'El monto pagado no puede ser negativo.'
    }
    if (montoPagado > preview.total) {
      return 'El monto pagado no puede superar el total de la venta.'
    }
  }
  return ''
}

function SalePanel({ venta, selectedDisk, onDiskClick, onClose, onEdit, onEditCancel, onSaved, onCancel, onDelete, isEditing }) {
  const [form, setForm] = useState(() => formularioVenta(venta))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  if (!venta) return null
  const esPagoDeuda = venta.tipoMovimiento === 'PAGO_DEUDA'
  const esPreVenta = venta.tipoMovimiento === 'PRE_VENTA'
  const detalles = venta.detalles?.length ? venta.detalles : [{
    idDisco: venta.idDisco,
    artista: venta.artista,
    album: venta.album,
    codigoInterno: '',
    precioUnitario: venta.precioVenta,
    importeVentaReal: venta.importeVentaReal ?? venta.precioVenta,
    grossProfit: venta.grossProfit ?? venta.gananciaNeta,
    gananciaNeta: venta.gananciaNeta,
    estadoGanancia: venta.estadoGanancia,
  }]
  const cover = selectedDisk?.imagenUrl

  async function submit(e) {
    e.preventDefault()
    const preview = vistaPreviaVenta(form)
    const validationError = validarFormularioVenta(form, preview)
    if (validationError) {
      setError(validationError)
      return
    }

    setSaving(true)
    setError('')
    try {
      const payload = {
        idCliente: venta.idCliente,
        canalVenta: venta.canalVenta || 'LOCAL',
        total: preview.total,
        costoEnvio: Number(venta.costoEnvio || 0),
        tipoEntrega: venta.tipoEntrega || 'RETIRO',
        descuentoPorcentaje: Number(form.descuentoPorcentaje || 0),
        medioPago: form.medioPago || null,
        numeroRecibo: form.numeroRecibo || null,
        montoPagado: form.montoPagado === '' ? undefined : Number(form.montoPagado),
        observaciones: form.observaciones || null,
        detalles: form.detalles.map(d => ({
          idDisco: d.idDisco,
          descripcion: d.descripcion || null,
          artista: d.artista || null,
          album: d.album || null,
          codigo: d.codigoInterno || null,
          cantidad: Number(d.cantidad || 1),
          manualItem: Boolean(d.manualItem) || !d.idDisco,
          precioUnitario: Number(d.precioUnitario || 0),
        })),
      }
      const updated = await api.ventas.actualizar(venta.idVenta, payload)
      await onSaved(updated)
    } catch (e) {
      setError(e.message || 'No se pudo editar la venta')
    } finally {
      setSaving(false)
    }
  }

  function setDetalle(index, value) {
    setForm(prev => ({
      ...prev,
      detalles: prev.detalles.map((d, i) => i === index ? { ...d, precioUnitario: value } : d),
    }))
    setError('')
  }

  function updateForm(field, value) {
    setForm(prev => ({ ...prev, [field]: value }))
    setError('')
  }

  const preview = isEditing ? vistaPreviaVenta(form) : null

  return (
    <aside className="fixed inset-y-0 right-0 z-50 w-full max-w-lg bg-white dark:bg-stone-950 border-l border-slate-200 dark:border-stone-800 shadow-2xl overflow-y-auto" role="dialog" aria-modal="true" aria-labelledby="sale-panel-title">
      <div className="sticky top-0 bg-white/95 dark:bg-stone-950/95 backdrop-blur px-5 py-4 border-b border-slate-100 dark:border-stone-800 flex items-start justify-between gap-3">
        <div>
          <h2 id="sale-panel-title" className="font-bold text-slate-900 dark:text-white">{venta.clienteNombreSnapshot || `${venta.nombreCliente || ''} ${venta.apellidoCliente || ''}`.trim()}</h2>
          <p className="text-sm text-slate-400 dark:text-stone-500">{fmtDate(venta.fechaVenta)}</p>
        </div>
        <button onClick={onClose} className="text-slate-400 hover:text-slate-700 dark:hover:text-white">✕</button>
      </div>
      <div className="p-5 space-y-5">
        {cover && <img src={resolveApiUrl(cover)} alt="" className="w-40 h-40 rounded-xl object-cover bg-slate-100 dark:bg-stone-800 mx-auto" />}
        {isEditing ? (
          <form onSubmit={submit} className="space-y-5">
            <div>
              <p className="text-xs font-semibold text-slate-500 dark:text-stone-400 uppercase tracking-wider">Editar venta</p>
              <p className="text-xs text-slate-400 dark:text-stone-500 mt-1">El cliente y la fecha se mantienen sin cambios.</p>
            </div>

            {error && <p role="alert" className="text-xs text-red-500 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg px-3 py-2">{error}</p>}

            <label className="block text-xs text-slate-500 dark:text-stone-400">
              Número de recibo
              <input className="input mt-1" value={form.numeroRecibo} onChange={e => updateForm('numeroRecibo', e.target.value)} />
            </label>

            {!esPagoDeuda && (
              <section>
                <p className="text-xs font-semibold text-slate-500 dark:text-stone-400 uppercase tracking-wider mb-2">Discos vendidos</p>
                <div className="space-y-3">
                  {form.detalles.map((d, index) => {
                    const detalleTexto = d.manualItem ? d.descripcion : `${d.artista} — ${d.album}`
                    return (
                      <div key={d.idDetalle || d.idDisco || index} className="rounded-lg border border-slate-100 dark:border-stone-800 px-3 py-3 space-y-2">
                        <button type="button" onClick={() => onDiskClick(d)} className="w-full text-left hover:text-[#5C7D87] dark:hover:text-[#7E9FA8]">
                          <p className="text-sm font-medium text-slate-800 dark:text-stone-200 truncate">{detalleTexto}</p>
                          <p className="text-xs text-slate-400 dark:text-stone-500 truncate">{d.codigoInterno || 'Sin código'} · Cant. {numeroCantidad(d)}</p>
                        </button>
                        <label className="block text-xs text-slate-500 dark:text-stone-400">
                          Precio de venta
                          <input
                            type="number"
                            min="0"
                            step="0.01"
                            className="input mt-1 text-right"
                            value={d.precioUnitario}
                            onChange={e => setDetalle(index, e.target.value)}
                            aria-label={`Precio de venta ${detalleTexto}`}
                          />
                        </label>
                      </div>
                    )
                  })}
                </div>
              </section>
            )}

            <section>
              <p className="text-xs font-semibold text-slate-500 dark:text-stone-400 uppercase tracking-wider mb-2">Venta</p>
              <label className="block text-xs text-slate-500 dark:text-stone-400">
                Descuento %
                <input type="number" min="0" step="0.01" className="input mt-1" value={form.descuentoPorcentaje} onChange={e => updateForm('descuentoPorcentaje', e.target.value)} />
              </label>
            </section>

            <section>
              <p className="text-xs font-semibold text-slate-500 dark:text-stone-400 uppercase tracking-wider mb-2">Pago</p>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <label className="block text-xs text-slate-500 dark:text-stone-400 sm:col-span-2">
                  Método de pago
                  <select className="input mt-1" value={form.medioPago} onChange={e => updateForm('medioPago', e.target.value)}>
                    <option value="">Sin definir</option>
                    <option value="EFECTIVO">Efectivo</option>
                    <option value="TRANSFERENCIA">Transferencia</option>
                    <option value="MERCADOPAGO">Mercado Pago</option>
                    <option value="TARJETA">Tarjeta</option>
                    <option value="OTRO">Otro</option>
                  </select>
                </label>
                <label className="block text-xs text-slate-500 dark:text-stone-400 sm:col-span-2">
                  Monto pagado
                  <input type="number" min="0" step="0.01" className="input mt-1" value={form.montoPagado} onChange={e => updateForm('montoPagado', e.target.value)} />
                </label>
              </div>
            </section>

            <section>
              <p className="text-xs font-semibold text-slate-500 dark:text-stone-400 uppercase tracking-wider mb-2">Resumen financiero</p>
              <div className="grid grid-cols-2 gap-3">
                {[
                  ['Subtotal', fmt(preview.subtotal)],
                  ['Descuento', fmt(preview.descuento)],
                  ['Total venta', fmt(preview.total)],
                  ['Monto pagado', fmt(preview.montoPagado)],
                  ['Deuda pendiente', fmt(preview.deuda)],
                  ['Estado pago', preview.estadoPago],
                  ['Ganancia bruta de la venta', esPagoDeuda ? '—' : fmtProfit(venta.grossProfit ?? venta.gananciaNeta, venta.estadoGanancia), esPagoDeuda ? '' : profitToneClass(venta.estadoGanancia, venta.grossProfit ?? venta.gananciaNeta)],
                ].map(([label, value, valueClass]) => (
                  <div key={label} className="rounded-lg border border-slate-100 dark:border-stone-800 bg-slate-50 dark:bg-stone-900 px-3 py-2">
                    <p className="text-[10px] uppercase tracking-wider text-slate-400 dark:text-stone-500">{label}</p>
                    <p className={`text-sm text-slate-800 dark:text-stone-200 ${valueClass || ''}`}>{value || '—'}</p>
                  </div>
                ))}
              </div>
              <p className="text-[11px] text-slate-400 dark:text-stone-500 mt-2">La ganancia bruta se actualiza con los valores del servidor al guardar.</p>
            </section>

            <label className="block text-xs text-slate-500 dark:text-stone-400">
              Observaciones
              <textarea className="input mt-1 min-h-20 resize-y" value={form.observaciones} onChange={e => updateForm('observaciones', e.target.value)} />
            </label>

            <div className="flex flex-col-reverse sm:flex-row sm:justify-end gap-2">
              <button type="button" onClick={onEditCancel} className="btn-secondary text-sm">Cancelar edición</button>
              <button disabled={saving} className="btn-primary text-sm disabled:opacity-50">{saving ? 'Guardando…' : 'Guardar cambios'}</button>
            </div>
          </form>
        ) : (
          <>
            <div className="grid grid-cols-2 gap-3">
              {[
                ['Movimiento', venta.descripcionMovimiento || (esPagoDeuda ? 'Pago de deuda' : 'Venta')],
                ['Ingreso', fmt(venta.montoMovimiento ?? venta.montoPagado ?? venta.totalFinal)],
                ['Total venta', fmt(venta.totalFinal)],
                ['Ganancia bruta de la venta', esPagoDeuda ? '—' : fmtProfit(venta.grossProfit ?? venta.gananciaNeta, venta.estadoGanancia), esPagoDeuda ? '' : profitToneClass(venta.estadoGanancia, venta.grossProfit ?? venta.gananciaNeta)],
                ['Método de pago', venta.medioPago],
                [esPagoDeuda ? 'Número de boleta' : 'Número de recibo', venta.numeroRecibo],
                ['Estado pago', venta.estadoPago],
                ['Descuento', venta.descuentoPorcentaje != null ? `${venta.descuentoPorcentaje}%` : '0%'],
                ['Monto pagado', fmt(venta.montoPagado)],
                ['Deuda pendiente', fmt(venta.montoDeuda)],
              ].map(([label, value, valueClass]) => (
                <div key={label} className="rounded-lg border border-slate-100 dark:border-stone-800 bg-slate-50 dark:bg-stone-900 px-3 py-2">
                  <p className="text-[10px] uppercase tracking-wider text-slate-400 dark:text-stone-500">{label}</p>
                  <p className={`text-sm text-slate-800 dark:text-stone-200 ${valueClass || ''}`}>{value || '—'}</p>
                </div>
              ))}
            </div>
            {venta.observaciones && <p className="text-sm text-slate-500 dark:text-stone-400 whitespace-pre-wrap">{venta.observaciones}</p>}
            {!esPagoDeuda && (
            <div>
              <p className="text-xs font-semibold text-slate-500 dark:text-stone-400 uppercase tracking-wider mb-2">Discos vendidos</p>
              <div className="space-y-2">
                {detalles.map((d, index) => (
                  <button key={d.idDetalle || d.idDisco || index} onClick={() => onDiskClick(d)} className="w-full text-left rounded-lg border border-slate-100 dark:border-stone-800 px-3 py-2 hover:border-[#7E9FA8]/50">
                    <p className="text-sm font-medium text-slate-800 dark:text-stone-200">{d.manualItem ? d.descripcion : `${d.artista} — ${d.album}`}</p>
                    <p className="text-xs text-slate-400 dark:text-stone-500">{d.codigoInterno || 'Sin código'} · Cant. {d.cantidad || 1} · {fmt(d.importeVentaReal ?? d.precioUnitario)}</p>
                    <p className={`text-xs font-mono tabular-nums ${profitToneClass(d.estadoGanancia, d.grossProfit ?? d.gananciaNeta)}`}>
                      {d.estadoGanancia === 'UNAVAILABLE' || (d.grossProfit ?? d.gananciaNeta) == null
                        ? 'Ganancia no disponible'
                        : fmtProfit(d.grossProfit ?? d.gananciaNeta, d.estadoGanancia)}
                    </p>
                  </button>
                ))}
              </div>
            </div>
            )}
            <div className="flex flex-col sm:flex-row gap-2">
              {!esPagoDeuda && <button onClick={onEdit} className="btn-primary flex-1">Editar</button>}
              {!esPreVenta && <button onClick={onCancel} className="btn-secondary flex-1 text-red-600 dark:text-red-400">
                {esPagoDeuda ? 'Anular pago' : 'Cancelar venta'}
              </button>}
              {esPreVenta && <button onClick={onDelete} className="btn-secondary flex-1 text-red-600 dark:text-red-400">Eliminar</button>}
            </div>
          </>
        )}
      </div>
    </aside>
  )
}

function fechaHoraInput(value) {
  return value ? value.slice(0, 16) : ''
}

function EditPreVentaPaymentModal({ venta, onClose, onSaved }) {
  const detalle = venta.detalles?.[0]
  const [form, setForm] = useState({
    precio: venta.totalFinal ?? venta.montoPagado ?? '',
    cantidad: detalle?.cantidad ?? 1,
    fechaPago: fechaHoraInput(venta.fechaVenta),
    medioPago: venta.medioPago || 'OTRO',
    numeroRecibo: venta.numeroRecibo || '',
    observaciones: venta.observaciones || '',
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  async function submit(e) {
    e.preventDefault()
    setSaving(true)
    setError('')
    try {
      await onSaved({
        precio: Number(form.precio),
        cantidad: Number(form.cantidad),
        fechaPago: form.fechaPago,
        medioPago: form.medioPago || null,
        numeroRecibo: form.numeroRecibo || null,
        observaciones: form.observaciones || null,
      })
    } catch (e) {
      setError(e.message || 'No se pudo editar el cobro de pre-venta')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-[60] bg-black/60 flex items-center justify-center p-4" onClick={onClose}>
      <form onSubmit={submit} className="w-full max-w-lg bg-white dark:bg-stone-950 rounded-xl border border-slate-200 dark:border-stone-800 shadow-2xl p-5 space-y-4" onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between">
          <h2 className="font-bold text-slate-900 dark:text-white">Editar cobro de pre-venta</h2>
          <button type="button" onClick={onClose} className="text-slate-400 hover:text-white">✕</button>
        </div>
        {error && <p className="text-xs text-red-500 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg px-3 py-2">{error}</p>}
        <div className="grid grid-cols-2 gap-3">
          <label className="block text-xs text-slate-500 dark:text-stone-400">Importe total
            <input required type="number" min="0.01" step="0.01" className="input w-full mt-1" value={form.precio} onChange={e => setForm(f => ({ ...f, precio: e.target.value }))} />
          </label>
          <label className="block text-xs text-slate-500 dark:text-stone-400">Cantidad
            <input required type="number" min="1" step="1" className="input w-full mt-1" value={form.cantidad} onChange={e => setForm(f => ({ ...f, cantidad: e.target.value }))} />
          </label>
          <label className="block text-xs text-slate-500 dark:text-stone-400">Fecha de pago
            <input required type="datetime-local" className="input w-full mt-1" value={form.fechaPago} onChange={e => setForm(f => ({ ...f, fechaPago: e.target.value }))} />
          </label>
          <label className="block text-xs text-slate-500 dark:text-stone-400">Número de recibo
            <input className="input w-full mt-1" value={form.numeroRecibo} onChange={e => setForm(f => ({ ...f, numeroRecibo: e.target.value }))} />
          </label>
        </div>
        <label className="block text-xs text-slate-500 dark:text-stone-400">Método de pago
          <select className="input w-full mt-1" value={form.medioPago} onChange={e => setForm(f => ({ ...f, medioPago: e.target.value }))}>
            <option value="">Sin definir</option>
            <option value="EFECTIVO">Efectivo</option>
            <option value="TRANSFERENCIA">Transferencia</option>
            <option value="MERCADOPAGO">Mercado Pago</option>
            <option value="TARJETA">Tarjeta</option>
            <option value="OTRO">Otro</option>
          </select>
        </label>
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
  return `UYU $${Number(n).toLocaleString('es-UY', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function profitState(value, status) {
  if (status === 'UNAVAILABLE' || value == null || Number.isNaN(Number(value))) return 'UNAVAILABLE'
  const amount = Number(value)
  if (amount > 0) return 'POSITIVE'
  if (amount < 0) return 'NEGATIVE'
  return 'ZERO'
}

function fmtProfit(value, status) {
  const state = profitState(value, status)
  if (state === 'UNAVAILABLE') return '—'
  const amount = Number(value)
  if (state === 'POSITIVE') return `+ ${fmt(amount)}`
  if (state === 'NEGATIVE') return `- ${fmt(Math.abs(amount))}`
  return fmt(0)
}

function profitToneClass(valueStatus, value) {
  const state = profitState(value, valueStatus)
  if (state === 'POSITIVE') return 'text-emerald-600 dark:text-emerald-400'
  if (state === 'NEGATIVE') return 'text-red-600 dark:text-red-400'
  return 'text-slate-500 dark:text-stone-400'
}

function fmtDate(s) {
  if (!s) return '—'
  return new Date(s).toLocaleDateString('es-UY', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

function nombreClienteMovimiento(v) {
  return v.clienteNombreSnapshot || `${v.nombreCliente || ''} ${v.apellidoCliente || ''}`.trim() || '—'
}

function detallePrincipal(v) {
  return v.detalles?.length === 1 ? v.detalles[0] : null
}

function textoDetalle(detalle) {
  if (!detalle) return ''
  if (detalle.manualItem) return detalle.descripcion || [detalle.artista, detalle.album].filter(Boolean).join(' ')
  return [detalle.artista, detalle.album].filter(Boolean).join(' ')
}

function discoMovimiento(v) {
  if (v.tipoMovimiento === 'PAGO_DEUDA') {
    const primary = v.descripcionMovimiento || 'Pago de deuda'
    const secondary = v.numeroFactura || ''
    return { primary, secondary, title: [primary, secondary].filter(Boolean).join(' · ') }
  }

  if (v.detalles?.length > 1) {
    const resumen = v.detalles.map(textoDetalle).filter(Boolean).join(', ')
    return {
      primary: `Varios (${v.detalles.length} ítems)`,
      secondary: resumen,
      title: resumen ? `Varios (${v.detalles.length} ítems): ${resumen}` : `Varios (${v.detalles.length} ítems)`,
    }
  }

  const detalle = detallePrincipal(v)
  const esManual = Boolean(detalle?.manualItem)
  const primary = esManual
    ? (detalle?.descripcion || v.artista || '—')
    : (detalle?.artista || v.artista || '—')
  const secondary = esManual
    ? (detalle?.album || v.album || '')
    : (detalle?.album || v.album || '')

  return { primary, secondary, title: [primary, secondary].filter(Boolean).join(' · ') }
}

function numeroBoletaMovimiento(v) {
  return v.tipoMovimiento === 'VENTA' || v.tipoMovimiento === 'PAGO_DEUDA' || v.tipoMovimiento === 'PRE_VENTA'
    ? (v.numeroRecibo || '—')
    : '—'
}

export default function LibroVentas() {
  const [ventas, setVentas] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const hoy = fechaInputLocal()
  const primerDiaMes = `${hoy.slice(0, 8)}01`
  const periodoActual = hoy.slice(0, 7)
  const [periodo, setPeriodo] = useState(periodoActual)
  const [filters, setFilters] = useState({ q: '' })
  const [applied, setApplied] = useState(rangoPeriodo(periodoActual, hoy))
  const [resumen, setResumen] = useState(null)
  const [ventaPanel, setVentaPanel] = useState(null)
  const [selectedDisk, setSelectedDisk] = useState(null)
  const [ventaCancelar, setVentaCancelar] = useState(null)
  const [cancelando, setCancelando] = useState(false)
  const [editMode, setEditMode] = useState(false)
  const [editandoPreVenta, setEditandoPreVenta] = useState(null)
  const [eliminandoPreVenta, setEliminandoPreVenta] = useState(null)
  const [eliminando, setEliminando] = useState(false)
  const [success, setSuccess] = useState('')

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

  const cargarResumen = useCallback(async (mes) => {
    try {
      setResumen(await api.ventas.resumenMensual(mes))
    } catch (e) {
      setError(e.message)
    }
  }, [])

  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    cargar({ desde: primerDiaMes, hasta: hoy })
    cargarResumen(periodoActual)
  }, [cargar, cargarResumen, primerDiaMes, hoy, periodoActual])
  /* eslint-enable react-hooks/set-state-in-effect */

  function aplicar() {
    const params = rangoPeriodo(periodo, hoy)
    if (filters.q) params.q = filters.q
    setApplied(params)
    cargar(params)
    cargarResumen(periodo)
  }

  function limpiar() {
    setPeriodo(periodoActual)
    setFilters({ q: '' })
    const params = rangoPeriodo(periodoActual, hoy)
    setApplied(params)
    cargar(params)
    cargarResumen(periodoActual)
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

  async function cancelarMovimiento() {
    if (!ventaCancelar) return
    setCancelando(true)
    setError(null)
    try {
      const esPagoDeuda = ventaCancelar.tipoMovimiento === 'PAGO_DEUDA'
      if (esPagoDeuda) {
        await api.deudas.eliminarPago(ventaCancelar.idPagoDeuda)
      } else {
        await api.ventas.cancelar(ventaCancelar.idVenta)
      }
      await cargar(applied)
      window.dispatchEvent(new Event(FINANCIAL_DATA_CHANGED_EVENT))
      setVentaPanel(null)
      setVentaCancelar(null)
      setSuccess(esPagoDeuda
        ? 'Pago anulado. El saldo de la deuda y los ingresos fueron actualizados.'
        : 'Venta cancelada correctamente.')
    } catch (e) {
      setError(e.message || 'No se pudo cancelar la venta')
    } finally {
      setCancelando(false)
    }
  }

  async function guardarPagoPreVenta(payload) {
    await api.preVentas.actualizarPago(editandoPreVenta.idPreVentaOrigen, payload)
    await cargar(applied)
    await cargarResumen(periodo)
    window.dispatchEvent(new Event(FINANCIAL_DATA_CHANGED_EVENT))
    setVentaPanel(null)
    setEditandoPreVenta(null)
    setSuccess('Cobro de pre-venta actualizado correctamente.')
  }

  async function eliminarPagoPreVenta() {
    if (!eliminandoPreVenta) return
    setEliminando(true)
    setError(null)
    try {
      await api.preVentas.eliminarPago(eliminandoPreVenta.idPreVentaOrigen)
      await cargar(applied)
      await cargarResumen(periodo)
      window.dispatchEvent(new Event(FINANCIAL_DATA_CHANGED_EVENT))
      setVentaPanel(null)
      setEliminandoPreVenta(null)
      setSuccess('Cobro de pre-venta eliminado permanentemente.')
    } catch (e) {
      setError(e.message || 'No se pudo eliminar el cobro de pre-venta')
    } finally {
      setEliminando(false)
    }
  }

  async function guardarVentaActualizada(updated) {
    setVentas(prev => prev.map(v => v.idVenta === updated.idVenta ? updated : v))
    setVentaPanel(updated)
    setEditMode(false)
    setSelectedDisk(current => {
      if (!current) return null
      const detalleActualizado = updated.detalles?.find(d => (
        (current.idDetalle && d.idDetalle === current.idDetalle)
          || (current.idDisco && d.idDisco === current.idDisco)
      ))
      return detalleActualizado
        ? { ...detalleActualizado, imagenUrl: current.imagenUrl || detalleActualizado.imagenUrl }
        : null
    })
    await cargarResumen(periodo)
    window.dispatchEvent(new Event(FINANCIAL_DATA_CHANGED_EVENT))
    setSuccess('Venta actualizada correctamente.')
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
      {success && <p className="text-sm text-emerald-700 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-900/20 border border-emerald-200 dark:border-emerald-800 rounded-lg px-4 py-3">{success}</p>}

      {/* Filtros */}
      <div className="card p-4">
        <div className="flex flex-wrap gap-3 items-end">
          <div>
            <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Mes a analizar</label>
            <input
              type="month"
              value={periodo}
              max={periodoActual}
              onChange={e => {
                const mes = e.target.value || periodoActual
                setPeriodo(mes)
              }}
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

      {/* Resumen mensual */}
      {resumen && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          {[
            { label: 'Ventas', value: resumen.cantidadVentas ?? 0 },
            { label: 'Ítems vendidos', value: resumen.cantidadItems ?? 0 },
            { label: 'Total ventas', value: fmt(resumen.totalVentas) },
            { label: 'Ingresos registrados', value: fmt(resumen.ingresosRegistrados) },
            { label: 'Ganancia bruta de ítems', value: fmtProfit(resumen.gananciaItems, null), tone: profitToneClass(null, resumen.gananciaItems) },
            { label: 'Gastos', value: fmt(resumen.gastos) },
            { label: 'Balance final', value: fmt(resumen.balanceFinal), tone: profitToneClass(null, resumen.balanceFinal) },
          ].map(({ label, value, tone }) => (
            <div key={label} className="card p-4 text-center">
              <p className="text-xs uppercase tracking-wider text-slate-400 dark:text-stone-500">{label}</p>
              <p className={`text-xl font-bold mt-1 tabular-nums ${tone || 'text-slate-900 dark:text-white'}`}>{value}</p>
            </div>
          ))}
        </div>
      )}

      {/* Tabla */}
      <div className="card overflow-hidden">
        <div>
          <table className="w-full table-fixed text-[13px] sm:text-[13.5px]">
            <colgroup>
              <col className="w-[9%]" />
              <col className="w-[14%]" />
              <col className="w-[13%]" />
              <col className="w-[24%]" />
              <col className="w-[12%]" />
              <col className="w-[11%]" />
              <col className="w-[7%]" />
              <col className="w-[10%]" />
            </colgroup>
            <thead>
              <tr className="border-b border-slate-100 dark:border-stone-800">
                {['Fecha', 'Movimiento', 'Cliente', 'Artista / Álbum', 'Ganancia bruta', 'Ingreso', 'N° Boleta', 'Estado Pago'].map(h => (
                  <th key={h} className="text-left px-2 py-3 text-[11px] font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-[0.02em] whitespace-nowrap">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-stone-800">
              {loading ? (
                <tr><td colSpan={8} className="px-2.5 py-12 text-center text-slate-400">Cargando…</td></tr>
              ) : error ? (
                <tr><td colSpan={8} className="px-2.5 py-12 text-center text-red-500">{error}</td></tr>
              ) : ventas.length === 0 ? (
                <tr><td colSpan={8} className="px-2.5 py-12 text-center text-slate-400 dark:text-stone-500">No hay ventas</td></tr>
              ) : ventas.map(v => {
                const cliente = nombreClienteMovimiento(v)
                const disco = discoMovimiento(v)
                const boleta = numeroBoletaMovimiento(v)

                return (
                  <tr key={`${v.tipoMovimiento || 'VENTA'}-${v.idPagoDeuda || v.idVenta}`} onClick={() => { setVentaPanel(v); setSelectedDisk(null) }} className="hover:bg-slate-50 dark:hover:bg-stone-900/50 transition-colors cursor-pointer">
                    <td className="px-2 py-3 whitespace-nowrap text-slate-700 dark:text-stone-300">
                      {fmtDate(v.fechaVenta)}
                    </td>
                    <td className="px-2 py-3 whitespace-nowrap overflow-hidden">
                      <span className={`inline-flex max-w-full items-center px-1.5 py-0.5 rounded-full text-[11px] sm:text-xs font-medium whitespace-nowrap ${
                        v.tipoMovimiento === 'PAGO_DEUDA'
                          ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300'
                          : v.tipoMovimiento === 'PRE_VENTA'
                            ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300'
                          : 'bg-slate-100 text-slate-600 dark:bg-stone-800 dark:text-stone-300'
                      }`}>
                        {v.descripcionMovimiento || 'Venta'}
                      </span>
                    </td>
                    <td className="px-2 py-3 text-slate-700 dark:text-stone-300">
                      <div className="truncate" title={cliente}>{cliente}</div>
                    </td>
                    <td className="px-2 py-3 text-slate-800 dark:text-stone-200" title={disco.title}>
                      <div className="min-w-0">
                        <p className="font-medium truncate leading-5">{disco.primary}</p>
                        {disco.secondary && (
                          <p className="text-[12px] leading-4 text-slate-500 dark:text-stone-500 truncate">{disco.secondary}</p>
                        )}
                      </div>
                    </td>
                    <td className={`px-2 py-3 font-mono tabular-nums font-semibold whitespace-nowrap ${profitToneClass(v.estadoGanancia, v.grossProfit ?? v.gananciaNeta)}`}>
                      <div className="truncate" title={v.tipoMovimiento === 'PAGO_DEUDA' ? 'No aplica' : fmtProfit(v.grossProfit ?? v.gananciaNeta, v.estadoGanancia)}>
                        {v.tipoMovimiento === 'PAGO_DEUDA' ? '—' : fmtProfit(v.grossProfit ?? v.gananciaNeta, v.estadoGanancia)}
                      </div>
                    </td>
                    <td className="px-2 py-3 font-mono tabular-nums font-semibold text-slate-800 dark:text-stone-200 whitespace-nowrap">
                      {fmt(v.montoMovimiento ?? v.montoPagado ?? v.totalFinal)}
                    </td>
                    <td className="px-2 py-3 font-mono tabular-nums text-slate-700 dark:text-stone-300 whitespace-nowrap">
                      <div className="truncate" title={boleta}>{boleta}</div>
                    </td>
                    <td className="px-2 py-3 whitespace-nowrap overflow-hidden">
                      {v.estadoPago ? (
                        <span className={`inline-flex max-w-full items-center px-1.5 py-0.5 rounded-full text-[11px] sm:text-xs font-medium ${ESTADO_PAGO_STYLES[v.estadoPago] || ''}`}>
                          {v.estadoPago}
                        </span>
                      ) : '—'}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </div>
      <SalePanel
        key={`${ventaPanel?.tipoMovimiento || 'none'}-${ventaPanel?.idPagoDeuda || ventaPanel?.idVenta || 'none'}-${editMode ? 'edit' : 'view'}`}
        venta={ventaPanel}
        selectedDisk={selectedDisk}
        onDiskClick={seleccionarDiscoDetalle}
        isEditing={editMode && ventaPanel?.tipoMovimiento === 'VENTA'}
        onClose={() => { setEditMode(false); setVentaPanel(null); setSelectedDisk(null) }}
        onEdit={() => ventaPanel.tipoMovimiento === 'PRE_VENTA' ? setEditandoPreVenta(ventaPanel) : setEditMode(true)}
        onEditCancel={() => setEditMode(false)}
        onSaved={guardarVentaActualizada}
        onCancel={() => setVentaCancelar(ventaPanel)}
        onDelete={() => setEliminandoPreVenta(ventaPanel)}
      />
      {editandoPreVenta && (
        <EditPreVentaPaymentModal
          venta={editandoPreVenta}
          onClose={() => setEditandoPreVenta(null)}
          onSaved={guardarPagoPreVenta}
        />
      )}
      {ventaCancelar && (
        <ConfirmModal
          titulo={ventaCancelar.tipoMovimiento === 'PAGO_DEUDA' ? 'Anular pago de deuda' : 'Cancelar venta'}
          mensaje={ventaCancelar.tipoMovimiento === 'PAGO_DEUDA'
            ? '¿Eliminar este pago de deuda? El importe volverá a sumarse al saldo pendiente del cliente y dejará de contar como ingreso.'
            : '¿Seguro que querés cancelar esta venta? Se restaurará el stock y se ocultará la deuda asociada si existe.'}
          onConfirmar={cancelarMovimiento}
          onCancelar={() => setVentaCancelar(null)}
          cargando={cancelando}
        />
      )}
      {eliminandoPreVenta && (
        <ConfirmModal
          titulo="Eliminar cobro de pre-venta"
          mensaje="¿Seguro que querés eliminar permanentemente este cobro? Desaparecerá del Libro de Ventas y la pre-venta volverá a quedar pendiente."
          confirmarTexto="Eliminar permanentemente"
          onConfirmar={eliminarPagoPreVenta}
          onCancelar={() => setEliminandoPreVenta(null)}
          cargando={eliminando}
        />
      )}
    </div>
  )
}
