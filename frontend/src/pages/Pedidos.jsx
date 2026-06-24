import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/sonograma'

const STATUS_LABEL = {
  PARSING: 'Parseando',
  PARSED: 'Parseado',
  ENRICHING: 'Enriqueciendo',
  AWAITING_REVIEW: 'En revisión',
  IMPORTING_TO_CATALOG: 'Importando',
  COMPLETED: 'Completado',
  FAILED: 'Fallido',
  PARTIALLY_COMPLETED: 'Parcial',
}

const STATUS_COLOR = {
  PARSED: 'bg-blue-500/10 text-blue-400',
  ENRICHING: 'bg-amber-500/10 text-amber-400',
  AWAITING_REVIEW: 'bg-violet-500/10 text-violet-400',
  IMPORTING_TO_CATALOG: 'bg-cyan-500/10 text-cyan-400',
  COMPLETED: 'bg-emerald-500/10 text-emerald-400',
  FAILED: 'bg-red-500/10 text-red-400',
  PARTIALLY_COMPLETED: 'bg-orange-500/10 text-orange-400',
}

function StatusBadge({ status }) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_COLOR[status] || 'bg-stone-800 text-stone-400'}`}>
      {STATUS_LABEL[status] || status}
    </span>
  )
}

function UploadZone({ onFile, archivo, accept, emptyLabel }) {
  const [dragging, setDragging] = useState(false)
  const inputRef = useRef(null)

  function handleFile(file) {
    if (file) onFile(file)
  }

  return (
    <div
      className={`relative rounded-xl border-2 border-dashed transition-colors cursor-pointer
        ${dragging ? 'border-[#7E9FA8] bg-[#7E9FA8]/5' :
          archivo ? 'border-[#7E9FA8]/50 bg-[#7E9FA8]/5' :
          'border-stone-700 hover:border-[#7E9FA8]/50 hover:bg-stone-900/50'}`}
      onClick={() => inputRef.current?.click()}
      onDragOver={e => { e.preventDefault(); setDragging(true) }}
      onDragLeave={() => setDragging(false)}
      onDrop={e => { e.preventDefault(); setDragging(false); handleFile(e.dataTransfer.files[0]) }}
    >
      <input ref={inputRef} type="file" accept={accept} className="hidden"
        onChange={e => handleFile(e.target.files[0])} />
      <div className="flex flex-col items-center justify-center gap-2 py-8 px-6 text-center pointer-events-none">
        {archivo ? (
          <>
            <span className="text-[#7E9FA8] font-medium text-sm">{archivo.name}</span>
            <span className="text-xs text-stone-500">{(archivo.size / 1024).toFixed(0)} KB · clic para cambiar</span>
          </>
        ) : (
          <>
            <svg className="w-8 h-8 text-stone-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 16.5V9.75m0 0 3 3m-3-3-3 3M6.75 19.5a4.5 4.5 0 0 1-1.41-8.775 5.25 5.25 0 0 1 10.233-2.33 3 3 0 0 1 3.758 3.848A3.752 3.752 0 0 1 18 19.5H6.75Z" />
            </svg>
            <p className="text-sm text-stone-400">{emptyLabel}</p>
          </>
        )}
      </div>
    </div>
  )
}

function ManualOrderModal({ onClose, onCreada }) {
  const [form, setForm] = useState({
    proveedor: 'Vinyl Future',
    fechaOrden: new Date().toISOString().split('T')[0],
    notas: '',
  })
  const [items, setItems] = useState([{ artista: '', album: '', descripcion: '', cantidad: 1, precioUnitario: '' }])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  function setItem(index, field, value) {
    setItems(prev => prev.map((item, i) => i === index ? { ...item, [field]: value } : item))
  }

  async function submit(e) {
    e.preventDefault()
    setLoading(true)
    setError('')
    try {
      const payload = {
        ...form,
        items: items.map(item => ({
          artista: item.artista || null,
          album: item.album || null,
          descripcion: item.descripcion || null,
          cantidad: Number(item.cantidad) || 1,
          precioUnitario: item.precioUnitario ? Number(item.precioUnitario) : null,
          subtotal: item.precioUnitario ? Number(item.precioUnitario) * (Number(item.cantidad) || 1) : null,
        })),
      }
      onCreada(await api.shippingOrders.crear(payload))
    } catch (e) {
      setError(e.message || 'No se pudo crear el pedido manual')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/60 p-4 overflow-y-auto">
      <div className="bg-white dark:bg-stone-950 border border-slate-200 dark:border-stone-800 rounded-xl shadow-2xl w-full max-w-3xl my-8">
        <div className="px-6 py-4 border-b border-slate-100 dark:border-stone-800 flex items-center justify-between">
          <h2 className="font-semibold text-slate-900 dark:text-white">Pedido manual</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-white">✕</button>
        </div>
        <form onSubmit={submit} className="p-6 space-y-4">
          {error && <p className="text-xs text-red-400 bg-red-900/20 border border-red-800 rounded-lg px-3 py-2">{error}</p>}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Proveedor</label>
              <input className="input w-full" value={form.proveedor} onChange={e => setForm(f => ({ ...f, proveedor: e.target.value }))} />
            </div>
            <div>
              <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Fecha</label>
              <input type="date" className="input w-full" value={form.fechaOrden} onChange={e => setForm(f => ({ ...f, fechaOrden: e.target.value }))} />
            </div>
          </div>
          <div>
            <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Notas</label>
            <textarea className="input w-full h-16 resize-none" value={form.notas} onChange={e => setForm(f => ({ ...f, notas: e.target.value }))} />
          </div>
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <p className="text-xs font-semibold text-slate-500 dark:text-stone-400 uppercase tracking-wider">Ítems</p>
              <button type="button" onClick={() => setItems(prev => [...prev, { artista: '', album: '', descripcion: '', cantidad: 1, precioUnitario: '' }])} className="text-xs text-[#7E9FA8] hover:underline">Agregar ítem</button>
            </div>
            {items.map((item, i) => (
              <div key={i} className="grid grid-cols-12 gap-2">
                <input className="input text-xs col-span-3" placeholder="Artista" value={item.artista} onChange={e => setItem(i, 'artista', e.target.value)} />
                <input className="input text-xs col-span-3" placeholder="Álbum" value={item.album} onChange={e => setItem(i, 'album', e.target.value)} />
                <input className="input text-xs col-span-2" placeholder="Descripción" value={item.descripcion} onChange={e => setItem(i, 'descripcion', e.target.value)} />
                <input className="input text-xs col-span-1" type="number" min="1" value={item.cantidad} onChange={e => setItem(i, 'cantidad', e.target.value)} />
                <input className="input text-xs col-span-2" type="number" min="0" step="0.01" placeholder="Precio" value={item.precioUnitario} onChange={e => setItem(i, 'precioUnitario', e.target.value)} />
                <button type="button" onClick={() => setItems(prev => prev.filter((_, idx) => idx !== i))} className="col-span-1 text-slate-400 hover:text-red-400">✕</button>
              </div>
            ))}
          </div>
          <div className="flex justify-end gap-2">
            <button type="button" onClick={onClose} className="btn-secondary text-sm">Cancelar</button>
            <button disabled={loading} className="btn-primary text-sm disabled:opacity-50">{loading ? 'Guardando…' : 'Crear pedido manual'}</button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default function Pedidos() {
  const navigate = useNavigate()
  const [pedidos, setPedidos] = useState([])
  const [manualOrders, setManualOrders] = useState([])
  const [loading, setLoading] = useState(true)
  const [archivo, setArchivo] = useState(null)
  const [plantilla, setPlantilla] = useState(null)
  const [uploading, setUploading] = useState(false)
  const [uploadError, setUploadError] = useState('')
  const [manualModal, setManualModal] = useState(false)

  useEffect(() => {
    Promise.all([api.pedidos.listar(), api.shippingOrders.listar()])
      .then(([pedidosData, manualData]) => {
        setPedidos(pedidosData)
        setManualOrders(manualData)
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  async function handleUpload() {
    if (!archivo) return
    setUploading(true)
    setUploadError('')
    try {
      const result = await api.pedidos.uploadControl(archivo, plantilla)
      const url = URL.createObjectURL(result.blob)
      const link = document.createElement('a')
      link.href = url
      link.download = result.filename
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)
      if (result.pedidoId) navigate(`/pedidos/${result.pedidoId}`)
    } catch (e) {
      setUploadError(e.message || 'Error al procesar el PDF')
      setUploading(false)
    }
  }

  return (
    <div className="max-w-6xl mx-auto px-6 py-8 space-y-8">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">Pedidos de importación</h1>
          <p className="text-sm text-stone-400 mt-1">Facturas deejay.de / Vinyl Future</p>
        </div>
        <button onClick={() => setManualModal(true)} className="px-4 py-2 rounded-lg border border-stone-700 text-stone-300 hover:bg-stone-900 text-sm font-medium transition-colors">
          Pedido manual
        </button>
      </div>

      {/* Upload card */}
      <div className="rounded-2xl border border-stone-800 bg-stone-950 p-6 space-y-4">
        <h2 className="text-sm font-semibold text-stone-300 uppercase tracking-wider">Nueva factura</h2>
        <div className="grid gap-4 md:grid-cols-2">
          <div className="space-y-2">
            <p className="text-xs text-stone-500">Factura PDF</p>
            <UploadZone
              onFile={setArchivo}
              archivo={archivo}
              accept="application/pdf"
              emptyLabel="Arrastrá la factura PDF o hacé clic"
            />
          </div>
          <div className="space-y-2">
            <p className="text-xs text-stone-500">
              Plantilla Excel{' '}
              <span className="text-stone-600">(opcional — usa la plantilla por defecto si se omite)</span>
            </p>
            <UploadZone
              onFile={setPlantilla}
              archivo={plantilla}
              accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              emptyLabel="Arrastrá una plantilla .xlsx para sobreescribir la por defecto"
            />
            {plantilla && (
              <button
                type="button"
                onClick={() => setPlantilla(null)}
                className="text-xs text-stone-500 hover:text-red-400 transition-colors"
              >
                ✕ Quitar plantilla personalizada
              </button>
            )}
          </div>
        </div>
        {uploadError && (
          <p className="text-xs text-red-400 bg-red-900/20 border border-red-800 rounded-lg px-3 py-2">{uploadError}</p>
        )}
        <button
          onClick={handleUpload}
          disabled={!archivo || uploading}
          className="px-5 py-2.5 rounded-lg bg-[#5C7D87] hover:bg-[#4a6a74] disabled:opacity-40 disabled:cursor-not-allowed text-white text-sm font-medium transition-colors"
        >
          {uploading ? 'Generando…' : 'Generar Excel e importar'}
        </button>
      </div>

      {/* List */}
      <div className="rounded-2xl border border-stone-800 overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center py-16 text-stone-500 text-sm">Cargando pedidos…</div>
        ) : pedidos.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-stone-500 gap-2">
            <svg className="w-10 h-10 text-stone-700" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h3.75M9 15h3.75M9 18h3.75m3 .75H18a2.25 2.25 0 0 0 2.25-2.25V6.108c0-1.135-.845-2.098-1.976-2.192a48.424 48.424 0 0 0-1.123-.08m-5.801 0c-.065.21-.1.433-.1.664 0 .414.336.75.75.75h4.5a.75.75 0 0 0 .75-.75 2.25 2.25 0 0 0-.1-.664m-5.8 0A2.251 2.251 0 0 1 13.5 2.25H15c1.012 0 1.867.668 2.15 1.586m-5.8 0c-.376.023-.75.05-1.124.08C9.095 4.01 8.25 4.973 8.25 6.108V8.25m0 0H4.875c-.621 0-1.125.504-1.125 1.125v11.25c0 .621.504 1.125 1.125 1.125h9.75c.621 0 1.125-.504 1.125-1.125V9.375c0-.621-.504-1.125-1.125-1.125H8.25Z" />
            </svg>
            <p className="text-sm">No hay pedidos todavía</p>
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-stone-800 bg-stone-950">
                {['Nº Factura', 'Fecha', 'Proveedor', 'Ítems', 'Cantidad', 'Total EUR', 'Estado'].map(h => (
                  <th key={h} className="text-left px-4 py-3 text-xs font-semibold text-stone-500 uppercase tracking-wider">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-stone-800">
              {pedidos.map(p => (
                <tr
                  key={p.idPedido}
                  className="hover:bg-stone-900/60 cursor-pointer transition-colors"
                  onClick={() => navigate(`/pedidos/${p.idPedido}`)}
                >
                  <td className="px-4 py-3 font-mono text-stone-200">{p.numeroFactura || <span className="text-stone-600">—</span>}</td>
                  <td className="px-4 py-3 text-stone-400">{p.fechaFactura || '—'}</td>
                  <td className="px-4 py-3 text-stone-300">{p.proveedor || '—'}</td>
                  <td className="px-4 py-3 text-stone-300 tabular-nums">{p.totalItemsCount}</td>
                  <td className="px-4 py-3 text-stone-300 tabular-nums">{p.sumCantidadItems}</td>
                  <td className="px-4 py-3 text-stone-200 tabular-nums font-medium">
                    {p.total ? `€ ${Number(p.total).toLocaleString('es-AR', { minimumFractionDigits: 2 })}` : '—'}
                  </td>
                  <td className="px-4 py-3"><StatusBadge status={p.importStatus} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="rounded-2xl border border-stone-800 overflow-hidden">
        <div className="px-4 py-3 border-b border-stone-800 bg-stone-950">
          <h2 className="text-sm font-semibold text-stone-300">Pedidos manuales</h2>
        </div>
        {manualOrders.length === 0 ? (
          <div className="py-10 text-center text-stone-500 text-sm">No hay pedidos manuales</div>
        ) : (
          <table className="w-full text-sm">
            <tbody className="divide-y divide-stone-800">
              {manualOrders.map(order => (
                <tr key={order.idShippingOrder} className="hover:bg-stone-900/60 transition-colors">
                  <td className="px-4 py-3 font-mono text-stone-300">{order.numero}</td>
                  <td className="px-4 py-3 text-stone-300">{order.proveedor}</td>
                  <td className="px-4 py-3 text-stone-400">{order.fechaOrden || '—'}</td>
                  <td className="px-4 py-3 text-stone-400">{(order.items || []).reduce((sum, item) => sum + Number(item.cantidad || 0), 0)} ítems</td>
                  <td className="px-4 py-3 text-stone-200">{order.costoTotal ? `UYU $${Number(order.costoTotal).toLocaleString('es-UY')}` : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {manualModal && (
        <ManualOrderModal
          onClose={() => setManualModal(false)}
          onCreada={order => {
            setManualOrders(prev => [order, ...prev])
            setManualModal(false)
          }}
        />
      )}
    </div>
  )
}
