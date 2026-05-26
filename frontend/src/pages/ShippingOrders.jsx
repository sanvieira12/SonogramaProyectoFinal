import { useState, useEffect } from 'react'
import { api } from '../api/sonograma'

const ESTADO_STYLES = {
  PENDIENTE: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400',
  ENVIADO:   'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
  RECIBIDO:  'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400',
  CANCELADO: 'bg-slate-100 text-slate-500 dark:bg-stone-800 dark:text-stone-500',
}

function fmt(n) {
  if (n == null) return '—'
  return `$${Number(n).toLocaleString('es-UY', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function fmtDate(s) {
  if (!s) return '—'
  return new Date(s + 'T00:00:00').toLocaleDateString('es-UY', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

function NuevaOrdenModal({ onClose, onCreada }) {
  const [form, setForm] = useState({
    proveedor: 'Vinyl Future',
    fechaOrden: new Date().toISOString().split('T')[0],
    notas: '',
  })
  const [items, setItems] = useState([{ artista: '', album: '', descripcion: '', cantidad: 1, precioUnitario: '' }])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  function setItem(i, field, value) {
    setItems(prev => prev.map((it, idx) => idx === i ? { ...it, [field]: value } : it))
  }

  function addItem() {
    setItems(prev => [...prev, { artista: '', album: '', descripcion: '', cantidad: 1, precioUnitario: '' }])
  }

  function removeItem(i) {
    setItems(prev => prev.filter((_, idx) => idx !== i))
  }

  async function submit(e) {
    e.preventDefault()
    setLoading(true)
    setError(null)
    try {
      const payload = {
        ...form,
        items: items.map(it => ({
          artista: it.artista || null,
          album: it.album || null,
          descripcion: it.descripcion || null,
          cantidad: Number(it.cantidad) || 1,
          precioUnitario: it.precioUnitario ? Number(it.precioUnitario) : null,
          subtotal: it.precioUnitario ? Number(it.precioUnitario) * (Number(it.cantidad) || 1) : null,
        }))
      }
      const created = await api.shippingOrders.crear(payload)
      onCreada(created)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/50 p-4 overflow-y-auto">
      <div className="bg-white dark:bg-stone-900 rounded-xl shadow-2xl w-full max-w-2xl my-8">
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 dark:border-stone-800">
          <h2 className="font-semibold text-slate-900 dark:text-white">Nueva orden de compra</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600 dark:hover:text-white">✕</button>
        </div>
        <form onSubmit={submit} className="p-6 space-y-4">
          {error && <p className="text-red-500 text-sm">{error}</p>}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Proveedor</label>
              <input className="input w-full" value={form.proveedor}
                onChange={e => setForm(f => ({ ...f, proveedor: e.target.value }))} />
            </div>
            <div>
              <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Fecha</label>
              <input type="date" className="input w-full" value={form.fechaOrden}
                onChange={e => setForm(f => ({ ...f, fechaOrden: e.target.value }))} />
            </div>
          </div>
          <div>
            <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Notas</label>
            <textarea className="input w-full h-16 resize-none" value={form.notas}
              onChange={e => setForm(f => ({ ...f, notas: e.target.value }))} />
          </div>

          <div>
            <div className="flex items-center justify-between mb-2">
              <p className="text-xs font-semibold text-slate-600 dark:text-stone-400 uppercase tracking-wider">Ítems</p>
              <button type="button" onClick={addItem}
                className="text-xs text-[#5C7D87] hover:underline">+ Agregar ítem</button>
            </div>
            <div className="space-y-2">
              {items.map((it, i) => (
                <div key={i} className="grid grid-cols-12 gap-2 items-center">
                  <input placeholder="Artista" className="input text-xs col-span-3" value={it.artista}
                    onChange={e => setItem(i, 'artista', e.target.value)} />
                  <input placeholder="Álbum" className="input text-xs col-span-3" value={it.album}
                    onChange={e => setItem(i, 'album', e.target.value)} />
                  <input placeholder="Descripción" className="input text-xs col-span-2" value={it.descripcion}
                    onChange={e => setItem(i, 'descripcion', e.target.value)} />
                  <input type="number" placeholder="Cant." min={1} className="input text-xs col-span-1 text-center"
                    value={it.cantidad} onChange={e => setItem(i, 'cantidad', e.target.value)} />
                  <input type="number" placeholder="Precio" min={0} step="0.01" className="input text-xs col-span-2"
                    value={it.precioUnitario} onChange={e => setItem(i, 'precioUnitario', e.target.value)} />
                  <button type="button" onClick={() => removeItem(i)}
                    className="col-span-1 text-slate-300 hover:text-red-500 text-center transition-colors">✕</button>
                </div>
              ))}
            </div>
          </div>

          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose} className="btn-secondary">Cancelar</button>
            <button type="submit" disabled={loading} className="btn-primary disabled:opacity-50">
              {loading ? 'Guardando…' : 'Crear orden'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

function DetalleModal({ order, onClose }) {
  function exportar() {
    const token = localStorage.getItem('token')
    const url = api.shippingOrders.exportarUrl(order.idShippingOrder)
    fetch(url, { headers: { Authorization: `Bearer ${token}` } })
      .then(r => r.blob())
      .then(blob => {
        const a = document.createElement('a')
        a.href = URL.createObjectURL(blob)
        a.download = `orden-${order.numero}.xlsx`
        a.click()
        URL.revokeObjectURL(a.href)
      })
  }

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/50 p-4 overflow-y-auto">
      <div className="bg-white dark:bg-stone-900 rounded-xl shadow-2xl w-full max-w-2xl my-8">
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 dark:border-stone-800">
          <div>
            <h2 className="font-semibold text-slate-900 dark:text-white">{order.numero}</h2>
            <p className="text-xs text-slate-400">{order.proveedor} · {fmtDate(order.fechaOrden)}</p>
          </div>
          <div className="flex items-center gap-3">
            <button onClick={exportar}
              className="text-xs flex items-center gap-1.5 text-[#5C7D87] hover:underline">
              <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M3 16.5v2.25A2.25 2.25 0 0 0 5.25 21h13.5A2.25 2.25 0 0 0 21 18.75V16.5M16.5 12 12 16.5m0 0L7.5 12m4.5 4.5V3" />
              </svg>
              Excel
            </button>
            <button onClick={onClose} className="text-slate-400 hover:text-slate-600 dark:hover:text-white">✕</button>
          </div>
        </div>
        <div className="p-6">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-100 dark:border-stone-800">
                {['Artista', 'Álbum', 'Descripción', 'Cant.', 'Precio', 'Subtotal'].map(h => (
                  <th key={h} className="text-left py-2 px-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-stone-800">
              {(order.items || []).map((it, i) => (
                <tr key={i} className="text-slate-700 dark:text-stone-300">
                  <td className="py-2 px-3">{it.artista || '—'}</td>
                  <td className="py-2 px-3">{it.album || '—'}</td>
                  <td className="py-2 px-3 text-slate-500">{it.descripcion || '—'}</td>
                  <td className="py-2 px-3 text-center">{it.cantidad}</td>
                  <td className="py-2 px-3 tabular-nums">{fmt(it.precioUnitario)}</td>
                  <td className="py-2 px-3 tabular-nums font-semibold">{fmt(it.subtotal)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr className="border-t border-slate-200 dark:border-stone-700">
                <td colSpan={5} className="py-2 px-3 text-right text-xs font-semibold text-slate-500 uppercase">Total</td>
                <td className="py-2 px-3 tabular-nums font-bold text-slate-900 dark:text-white">{fmt(order.costoTotal)}</td>
              </tr>
            </tfoot>
          </table>
          {order.notas && (
            <p className="mt-4 text-xs text-slate-500 dark:text-stone-500 italic">{order.notas}</p>
          )}
        </div>
      </div>
    </div>
  )
}

export default function ShippingOrders() {
  const [orders, setOrders] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [modalNuevo, setModalNuevo] = useState(false)
  const [detalle, setDetalle] = useState(null)

  useEffect(() => {
    api.shippingOrders.listar()
      .then(setOrders)
      .catch(e => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 py-6 space-y-5">
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-900 dark:text-white">Órdenes de Compra</h1>
          <p className="text-slate-400 dark:text-stone-500 text-sm mt-0.5">Pedidos a Vinyl Future y otros proveedores</p>
        </div>
        <button onClick={() => setModalNuevo(true)} className="btn-primary flex items-center gap-2">
          <span className="text-lg leading-none">+</span>
          Nueva orden
        </button>
      </div>

      <div className="card overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-100 dark:border-stone-800">
                {['Número', 'Proveedor', 'Fecha', 'Estado', 'Ítems', 'Total', 'Acciones'].map(h => (
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
              ) : orders.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-4 py-16 text-center text-slate-400 dark:text-stone-500">
                    No hay órdenes de compra. Creá la primera.
                  </td>
                </tr>
              ) : orders.map(o => (
                <tr key={o.idShippingOrder}
                  className="hover:bg-slate-50 dark:hover:bg-stone-900/50 cursor-pointer transition-colors"
                  onClick={() => setDetalle(o)}>
                  <td className="px-4 py-3 font-mono text-xs font-semibold text-slate-700 dark:text-stone-300">
                    {o.numero}
                  </td>
                  <td className="px-4 py-3 text-slate-700 dark:text-stone-300">{o.proveedor}</td>
                  <td className="px-4 py-3 text-slate-600 dark:text-stone-400 whitespace-nowrap">{fmtDate(o.fechaOrden)}</td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${ESTADO_STYLES[o.estado] || ''}`}>
                      {o.estado}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-center text-slate-600 dark:text-stone-400">
                    {(o.items || []).length}
                  </td>
                  <td className="px-4 py-3 tabular-nums font-semibold text-slate-800 dark:text-stone-200">
                    {fmt(o.costoTotal)}
                  </td>
                  <td className="px-4 py-3" onClick={e => e.stopPropagation()}>
                    <button
                      onClick={() => setDetalle(o)}
                      className="text-xs text-[#5C7D87] hover:underline"
                    >
                      Ver detalle
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {modalNuevo && (
        <NuevaOrdenModal
          onClose={() => setModalNuevo(false)}
          onCreada={nueva => {
            setOrders(prev => [nueva, ...prev])
            setModalNuevo(false)
          }}
        />
      )}

      {detalle && <DetalleModal order={detalle} onClose={() => setDetalle(null)} />}
    </div>
  )
}
