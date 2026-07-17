import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/sonograma'

// ── Small shared components ───────────────────────────────────────────────────

function Field({ label, value }) {
  return (
    <div>
      <p className="text-xs text-stone-500 uppercase tracking-wider mb-0.5">{label}</p>
      <p className="text-sm text-stone-200">{value || <span className="text-stone-600">—</span>}</p>
    </div>
  )
}

function Money({ value, prefix = '€' }) {
  if (value == null) return <span className="text-stone-600">—</span>
  return <span>{prefix} {Number(value).toLocaleString('es-AR', { minimumFractionDigits: 2 })}</span>
}

function Spinner({ text }) {
  return (
    <div className="flex items-center gap-2 text-stone-400 text-sm">
      <svg className="animate-spin w-4 h-4" viewBox="0 0 24 24" fill="none">
        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
      </svg>
      {text && <span>{text}</span>}
    </div>
  )
}

// ── Tab: Resumen ──────────────────────────────────────────────────────────────

function TabResumen({ pedido }) {
  const sumItems = pedido.sumCantidadItems ?? 0
  const cantPdf  = pedido.cantidadTotalPdf

  return (
    <div className="space-y-6">
      {/* Header fields */}
      <div className="rounded-xl border border-stone-800 bg-stone-950 p-5 grid grid-cols-2 md:grid-cols-4 gap-4">
        <Field label="Nº Factura"  value={pedido.numeroFactura} />
        <Field label="Fecha"       value={pedido.fechaFactura} />
        <Field label="Proveedor"   value={pedido.proveedor} />
        <Field label="Moneda"      value={pedido.moneda} />
        <Field label="Envío"       value={pedido.envio} />
        <Field label="Pago"        value={pedido.pago} />
        <Field label={`Peso (${pedido.unidadPeso || 'kg'})`} value={pedido.pesoTotalKg} />
        <Field label="Arancel"     value={pedido.codigoArancel} />
        <Field label="EORI"        value={pedido.eoriNo} />
        <Field label="Términos"    value={pedido.terminosVenta} />
        <Field label="Archivo"     value={pedido.nombreArchivo} />
      </div>

      {/* Financial summary */}
      <div className="rounded-xl border border-stone-800 bg-stone-950 p-5">
        <h3 className="text-xs font-semibold text-stone-500 uppercase tracking-wider mb-4">Resumen financiero</h3>
        <div className="grid grid-cols-2 md:grid-cols-5 gap-4 text-sm">
          <div><p className="text-xs text-stone-500 mb-0.5">Mercadería</p><p className="text-stone-200 tabular-nums font-medium"><Money value={pedido.merchandiseTotal} /></p></div>
          <div><p className="text-xs text-stone-500 mb-0.5">Franqueo</p><p className="text-stone-200 tabular-nums"><Money value={pedido.franqueo} /></p></div>
          <div><p className="text-xs text-stone-500 mb-0.5">Tarifas</p><p className="text-stone-200 tabular-nums"><Money value={pedido.tarifas} /></p></div>
          <div><p className="text-xs text-stone-500 mb-0.5">Neto</p><p className="text-stone-200 tabular-nums"><Money value={pedido.neto} /></p></div>
          <div><p className="text-xs text-stone-500 mb-0.5">IVA</p><p className="text-stone-200 tabular-nums"><Money value={pedido.iva} /></p></div>
          <div><p className="text-xs text-stone-500 mb-0.5">Total factura</p><p className="text-[#7E9FA8] tabular-nums font-bold"><Money value={pedido.total} /></p></div>
        </div>
      </div>

      {/* Quantity check */}
      {cantPdf != null && (
        <div className={`rounded-xl border px-4 py-3 text-sm flex items-center gap-2 ${
          pedido.advertenciaCantidad
            ? 'border-amber-700 bg-amber-900/20 text-amber-400'
            : 'border-emerald-800 bg-emerald-900/20 text-emerald-400'
        }`}>
          {pedido.advertenciaCantidad
            ? `⚠ Cantidad en PDF: ${cantPdf} — suma de ítems: ${sumItems} (¡diferencia!)`
            : `✓ Cantidad coincide: ${sumItems} unidades`
          }
        </div>
      )}

      <div className="rounded-xl border border-stone-800 bg-stone-950 p-5 space-y-4">
        <h3 className="text-xs font-semibold text-stone-500 uppercase tracking-wider">Configuración de costos</h3>
        <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
          {[
            ['Tipo cambio (€→$)', 50],
            ['Extra costo Single (€)', 5],
            ['Extra costo Double (€)', 8],
            ['Markup Single (×)', 1.6],
            ['Markup Double (×)', 1.4],
          ].map(([label, value]) => (
            <div key={label}>
              <p className="text-xs text-stone-500 mb-1">{label}</p>
              <p className="text-sm text-stone-200 tabular-nums">{value}</p>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

// ── Tab: Ítems ────────────────────────────────────────────────────────────────

function TabItems({ pedido }) {
  const items = pedido.items ?? []

  if (items.length === 0) {
    return <p className="text-stone-500 text-sm py-8 text-center">No hay ítems</p>
  }

  return (
    <div className="space-y-4">
      <div className="overflow-x-auto rounded-xl border border-stone-800">
        <table className="w-full text-xs">
          <thead>
            <tr className="border-b border-stone-800 bg-stone-950">
              {['Code', 'Date', 'Description', 'Unit Price', 'Quantity', 'Line Total'].map(h => (
                <th key={h} className="text-left px-3 py-2.5 font-semibold text-stone-500 uppercase tracking-wider whitespace-nowrap">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-stone-800/60">
            {items.map(item => (
              <tr key={item.idPedidoItem} className="hover:bg-stone-900/40 transition-colors">
                <td className="px-3 py-2 font-mono text-stone-400 whitespace-nowrap">{item.codigo || '—'}</td>
                <td className="px-3 py-2 text-stone-500 whitespace-nowrap">{pedido.fechaFactura || '—'}</td>
                <td className="px-3 py-2 text-stone-200 max-w-[420px]">{item.descripcionOriginal || [item.artista, item.titulo].filter(Boolean).join(' - ') || '—'}</td>
                <td className="px-3 py-2 text-stone-300 tabular-nums">{item.precioUnitarioEur != null ? `€${Number(item.precioUnitarioEur).toFixed(2)}` : '—'}</td>
                <td className="px-3 py-2 text-stone-300 tabular-nums">{item.cantidad ?? '—'}</td>
                <td className="px-3 py-2 text-stone-300 tabular-nums">{item.totalLineaEur != null ? `€${Number(item.totalLineaEur).toFixed(2)}` : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

    </div>
  )
}

// ── Tab: Texto bruto ──────────────────────────────────────────────────────────

function TabTexto({ pedido }) {
  const [open, setOpen] = useState(false)
  return (
    <div className="space-y-3">
      <p className="text-xs text-stone-500">
        Archivo: <span className="text-stone-300">{pedido.nombreArchivo || '—'}</span>
      </p>
      <button onClick={() => setOpen(o => !o)}
        className="flex items-center gap-2 text-sm text-[#7E9FA8] hover:text-white transition-colors">
        <svg className={`w-4 h-4 transition-transform ${open ? 'rotate-90' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M8.25 4.5l7.5 7.5-7.5 7.5" />
        </svg>
        {open ? 'Ocultar texto extraído' : 'Ver texto extraído del PDF'}
      </button>
      {open && (
        <pre className="w-full bg-stone-950 border border-stone-800 rounded-xl p-4 text-xs text-stone-400 overflow-auto max-h-[600px] whitespace-pre-wrap">
          {pedido.textoExtraido || 'Sin texto disponible'}
        </pre>
      )}
    </div>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function PedidoDetalle() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [pedido, setPedido] = useState(null)
  const [tab, setTab] = useState('resumen')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [actionMsg, setActionMsg] = useState('')
  const pollRef = useRef(null)

  const load = useCallback(() => {
    api.pedidos.porId(id)
      .then(p => { setPedido(p); setLoading(false) })
      .catch(e => { setError(e.message); setLoading(false) })
  }, [id])

  useEffect(() => {
    load()
  }, [load])

  // Poll while enriching
  const importStatus = pedido?.importStatus
  useEffect(() => {
    if (!importStatus) return
    if (importStatus === 'ENRICHING') {
      pollRef.current = setInterval(load, 4000)
    } else {
      clearInterval(pollRef.current)
    }
    return () => clearInterval(pollRef.current)
  }, [importStatus, load])

  async function handleEnriquecer() {
    setActionMsg('')
    try {
      await api.pedidos.enriquecer(id)
      setActionMsg('Enriquecimiento lanzado en background…')
      load()
    } catch (e) {
      setActionMsg('Error: ' + e.message)
    }
  }

  async function handleImportar() {
    setActionMsg('Importando al catálogo…')
    try {
      const updated = await api.pedidos.importarCatalogo(id)
      setPedido(updated)
      setActionMsg('Importación completada')
    } catch (e) {
      setActionMsg('Error: ' + e.message)
    }
  }

  async function handlePdf() {
    setActionMsg('')
    try {
      const blob = await api.pedidos.descargarPdf(id)
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = pedido.pdfOriginalFilename || `pedido-${id}.pdf`
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)
    } catch (e) {
      setActionMsg('Error: ' + e.message)
    }
  }

  if (loading) return (
    <div className="flex items-center justify-center min-h-[50vh]">
      <Spinner text="Cargando pedido…" />
    </div>
  )

  if (error) return (
    <div className="max-w-6xl mx-auto px-6 py-8">
      <p className="text-red-400 text-sm">{error}</p>
    </div>
  )

  if (!pedido) return null

  return (
    <div className="max-w-7xl mx-auto px-6 py-8 space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div className="space-y-1">
          <button onClick={() => navigate('/pedidos')}
            className="text-xs text-stone-500 hover:text-stone-300 flex items-center gap-1 mb-2 transition-colors">
            ← Pedidos
          </button>
          <h1 className="text-xl font-bold text-white">
            Pedido {pedido.numeroFactura || `#${pedido.idPedido}`}
          </h1>
          <p className="text-xs text-stone-500">
            {pedido.proveedor} · {pedido.fechaFactura || 'Sin fecha'} · {pedido.nombreArchivo}
          </p>
        </div>
        <div className="flex items-center gap-3 flex-wrap">
          {pedido.pdfDisponible && (
            <button onClick={handlePdf}
              className="px-4 py-2 rounded-lg border border-stone-700 text-stone-300 hover:bg-stone-900 text-sm font-medium transition-colors">
              PDF original
            </button>
          )}
          {['PARSED','AWAITING_REVIEW','PARTIALLY_COMPLETED'].includes(pedido.importStatus) && (
            <button onClick={handleEnriquecer}
              className="px-4 py-2 rounded-lg border border-[#7E9FA8]/40 text-[#7E9FA8] hover:bg-[#7E9FA8]/10 text-sm font-medium transition-colors">
              Enriquecer portadas
            </button>
          )}
          {['AWAITING_REVIEW','ENRICHING','PARTIALLY_COMPLETED','COMPLETED'].includes(pedido.importStatus) && (
            <button onClick={handleImportar}
              className="px-4 py-2 rounded-lg bg-[#5C7D87] hover:bg-[#4a6a74] text-white text-sm font-medium transition-colors">
              Importar al catálogo
            </button>
          )}
          {pedido.importStatus === 'ENRICHING' && <Spinner />}
        </div>
      </div>

      {actionMsg && (
        <div className="text-xs text-stone-400 bg-stone-900 border border-stone-800 rounded-lg px-3 py-2">
          {actionMsg}
        </div>
      )}

      {/* Tabs */}
      <div className="flex gap-1 border-b border-stone-800">
        {[['resumen','Resumen'], ['items',`Ítems (${(pedido.items ?? []).length})`], ['texto','Texto bruto']].map(([key, label]) => (
          <button
            key={key}
            onClick={() => setTab(key)}
            className={`px-4 py-2.5 text-sm font-medium transition-colors border-b-2 -mb-px ${
              tab === key
                ? 'border-[#7E9FA8] text-[#7E9FA8]'
                : 'border-transparent text-stone-500 hover:text-stone-300'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {/* Tab content */}
      <div>
        {tab === 'resumen' && (
          <TabResumen pedido={pedido} />
        )}
        {tab === 'items' && (
          <TabItems pedido={pedido} />
        )}
        {tab === 'texto' && (
          <TabTexto pedido={pedido} />
        )}
      </div>
    </div>
  )
}
