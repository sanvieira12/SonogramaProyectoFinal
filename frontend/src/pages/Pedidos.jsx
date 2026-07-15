import { Fragment, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import CompactPlayer from '../components/CompactPlayer'
import { stopAllPreviews } from '../components/audioPreviewPlayback'
import { api, resolveApiUrl } from '../api/sonograma'

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

function eur(value) {
  if (value == null) return '—'
  return `€ ${Number(value).toLocaleString('es-UY', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function uyu(value) {
  if (value == null) return '—'
  return `UYU $${Number(value).toLocaleString('es-UY', { maximumFractionDigits: 0 })}`
}

function DiskPanel({ disco, item, error, onClose }) {
  useEffect(() => () => stopAllPreviews(), [])

  if (!disco && !item) return null
  const previews = disco?.audioPreviews || []
  return (
    <aside className="fixed inset-y-0 right-0 z-50 w-full max-w-md bg-white dark:bg-stone-950 border-l border-slate-200 dark:border-stone-800 shadow-2xl overflow-y-auto">
      <div className="sticky top-0 bg-white/95 dark:bg-stone-950/95 backdrop-blur px-5 py-4 border-b border-slate-100 dark:border-stone-800 flex items-start justify-between gap-3">
        <div>
          <h2 className="font-bold text-slate-900 dark:text-white">{disco?.artista || item?.artista || 'Ítem sin vincular'}</h2>
          <p className="text-sm text-slate-500 dark:text-stone-400">{disco?.album || item?.titulo || '—'}</p>
        </div>
        <button onClick={onClose} className="text-slate-400 hover:text-slate-700 dark:hover:text-white">✕</button>
      </div>
      <div className="p-5 space-y-4">
        {disco?.imagenUrl ? (
          <img src={resolveApiUrl(disco.imagenUrl)} alt={`${disco.artista} - ${disco.album}`} className="w-full max-w-56 mx-auto aspect-square rounded-xl object-cover bg-slate-100 dark:bg-stone-800" />
        ) : item?.portadaUrl ? (
          <img src={resolveApiUrl(item.portadaUrl)} alt="" className="w-full max-w-56 mx-auto aspect-square rounded-xl object-cover bg-slate-100 dark:bg-stone-800" />
        ) : (
          <div className="w-full max-w-56 mx-auto aspect-square rounded-xl bg-slate-100 dark:bg-stone-800 flex items-center justify-center text-sm text-slate-400">Sin portada</div>
        )}

        {error && (
          <p className="text-sm text-amber-700 dark:text-amber-300 rounded-lg border border-amber-200 dark:border-amber-900/60 bg-amber-50 dark:bg-amber-950/30 px-3 py-2">
            Este ítem todavía no está vinculado al catálogo.
          </p>
        )}

        <div className="grid grid-cols-2 gap-3">
          {[
            ['Código', disco?.codigoInterno || item?.codigo],
            ['Condición', disco?.condicion],
            ['Stock', disco?.cantidadCopias],
            ['Precio venta', disco?.precioVenta != null ? uyu(disco.precioVenta) : null],
            ['Formato', disco?.tipoDisco || item?.formato],
            ['Precio pedido', item?.precioUnitarioEur != null ? eur(item.precioUnitarioEur) : null],
          ].map(([label, value]) => (
            <div key={label} className="rounded-lg border border-slate-100 dark:border-stone-800 bg-slate-50 dark:bg-stone-900 px-3 py-2">
              <p className="text-[10px] uppercase tracking-wider text-slate-400 dark:text-stone-500">{label}</p>
              <p className="text-sm text-slate-700 dark:text-stone-300 truncate">{value ?? '—'}</p>
            </div>
          ))}
        </div>

        {previews.length > 0 && (
          <div>
            <p className="text-[10px] uppercase tracking-wider text-slate-400 dark:text-stone-500 mb-2">Audio previews</p>
            <div className="space-y-1.5 max-h-52 overflow-y-auto pr-1">
              {previews.map(p => (
                <CompactPlayer
                  key={p.id}
                  audioUrl={p.audioUrl}
                  youtubeUrl={p.youtubeUrl}
                  trackName={p.trackName}
                  trackPosition={p.trackPosition}
                />
              ))}
            </div>
          </div>
        )}
      </div>
    </aside>
  )
}

export default function Pedidos() {
  const [pedidos, setPedidos] = useState([])
  const [detalles, setDetalles] = useState({})
  const [expandido, setExpandido] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [panel, setPanel] = useState({ disco: null, item: null, error: '' })

  useEffect(() => {
    api.pedidos.listar('vinylfuture')
      .then(setPedidos)
      .catch(e => setError(e.message || 'No se pudieron cargar los pedidos'))
      .finally(() => setLoading(false))
  }, [])

  async function togglePedido(pedido) {
    if (expandido === pedido.idPedido) {
      setExpandido(null)
      return
    }
    setExpandido(pedido.idPedido)
    if (detalles[pedido.idPedido]) return
    try {
      const data = await api.pedidos.porId(pedido.idPedido)
      setDetalles(prev => ({ ...prev, [pedido.idPedido]: data }))
    } catch (e) {
      setError(e.message || 'No se pudo cargar el detalle del pedido')
    }
  }

  async function abrirItem(item) {
    stopAllPreviews()
    setPanel({ disco: null, item, error: '' })
    try {
      if (item.idDisco) {
        const disco = await api.discos.porId(item.idDisco)
        setPanel({ disco, item, error: '' })
        return
      }
      if (item.codigo) {
        const matches = await api.discos.buscar(item.codigo)
        const disco = matches.find(d => String(d.codigoInterno || '').toLowerCase() === String(item.codigo).toLowerCase()) || matches[0]
        if (disco) {
          setPanel({ disco, item, error: '' })
          return
        }
      }
      setPanel({ disco: null, item, error: 'sin-vinculo' })
    } catch {
      setPanel({ disco: null, item, error: 'sin-vinculo' })
    }
  }

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 py-6 space-y-5">
      <div>
        <h1 className="text-xl font-bold text-slate-900 dark:text-white">Pedidos de importación</h1>
        <p className="text-slate-400 dark:text-stone-500 text-sm mt-0.5">Pedidos importados y desglose por ítem</p>
      </div>

      {error && (
        <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-600 dark:text-red-300 text-sm rounded-xl px-4 py-3">
          {error}
        </div>
      )}

      <div className="rounded-xl border border-slate-200 dark:border-stone-800 overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center py-16 text-slate-500 text-sm">Cargando pedidos…</div>
        ) : pedidos.length === 0 ? (
          <div className="px-4 py-10 text-slate-500 text-sm">Sin pedidos importados.</div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200 dark:border-stone-800 bg-slate-50 dark:bg-stone-950">
                {['Nº pedido', 'Fecha', 'Proveedor', 'Ítems', 'Cantidad', 'Total en euros', 'Estado', ''].map(h => (
                  <th key={h} className="text-left px-4 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-stone-800">
              {pedidos.map(p => {
                const detalle = detalles[p.idPedido]
                const abierto = expandido === p.idPedido
                const items = detalle?.items || []
                return (
                  <Fragment key={p.idPedido}>
                    <tr
                      key={p.idPedido}
                      className="hover:bg-slate-50 dark:hover:bg-stone-900/60 cursor-pointer transition-colors"
                      onClick={() => togglePedido(p)}
                    >
                      <td className="px-4 py-3 font-mono text-slate-800 dark:text-stone-200">{p.numeroFactura || p.nombreArchivo || '—'}</td>
                      <td className="px-4 py-3 text-slate-500 dark:text-stone-400">{p.fechaFactura || '—'}</td>
                      <td className="px-4 py-3 text-slate-700 dark:text-stone-300">{p.proveedor || '—'}</td>
                      <td className="px-4 py-3 text-slate-700 dark:text-stone-300 tabular-nums">{p.totalItemsCount}</td>
                      <td className="px-4 py-3 text-slate-700 dark:text-stone-300 tabular-nums">{p.sumCantidadItems}</td>
                      <td className="px-4 py-3 text-slate-900 dark:text-white tabular-nums font-medium">{eur(p.total)}</td>
                      <td className="px-4 py-3"><StatusBadge status={p.importStatus} /></td>
                      <td className="px-4 py-3 text-right">
                        <span className="text-xs text-slate-400">{abierto ? 'Cerrar' : 'Abrir'}</span>
                      </td>
                    </tr>
                    {abierto && (
                      <tr key={`${p.idPedido}-detalle`}>
                        <td colSpan={8} className="bg-white dark:bg-stone-950 px-4 py-4">
                          <div className="flex items-center justify-between mb-3">
                            <p className="text-xs font-semibold text-slate-500 dark:text-stone-400 uppercase tracking-wider">Desglose del pedido</p>
                            <Link to={`/pedidos/${p.idPedido}`} className="text-xs text-[#5C7D87] dark:text-[#7E9FA8] hover:underline">Ver detalle completo</Link>
                          </div>
                          {items.length === 0 ? (
                            <p className="text-sm text-slate-400 dark:text-stone-500">Cargando ítems…</p>
                          ) : (
                            <div className="overflow-x-auto">
                              <table className="w-full text-xs">
                                <thead>
                                  <tr className="border-b border-slate-100 dark:border-stone-800">
                                    {['Nº pedido', 'Fecha', 'Descripción', 'Precio unitario', 'Cantidad', 'Suma'].map(h => (
                                      <th key={h} className="text-left px-3 py-2 text-[10px] font-semibold text-slate-400 dark:text-stone-500 uppercase tracking-wider">{h}</th>
                                    ))}
                                  </tr>
                                </thead>
                                <tbody className="divide-y divide-slate-100 dark:divide-stone-800/70">
                                  {items.map(item => (
                                    <tr key={item.idPedidoItem} onClick={() => abrirItem(item)} className="hover:bg-slate-50 dark:hover:bg-stone-900/60 cursor-pointer">
                                      <td className="px-3 py-2 font-mono text-slate-600 dark:text-stone-400">{p.numeroFactura || p.nombreArchivo || '—'}</td>
                                      <td className="px-3 py-2 text-slate-500 dark:text-stone-400">{p.fechaFactura || '—'}</td>
                                      <td className="px-3 py-2 text-slate-800 dark:text-stone-200">
                                        <div className="font-medium">{[item.artista, item.titulo].filter(Boolean).join(' — ') || item.codigo || 'Sin descripción'}</div>
                                        <div className="text-slate-400 dark:text-stone-500">{item.codigo || item.formato || '—'}</div>
                                      </td>
                                      <td className="px-3 py-2 text-slate-700 dark:text-stone-300 tabular-nums">{eur(item.precioUnitarioEur)}</td>
                                      <td className="px-3 py-2 text-slate-700 dark:text-stone-300 tabular-nums">{item.cantidad || 1}</td>
                                      <td className="px-3 py-2 text-slate-900 dark:text-white tabular-nums font-semibold">{eur(item.totalLineaEur)}</td>
                                    </tr>
                                  ))}
                                </tbody>
                              </table>
                            </div>
                          )}
                        </td>
                      </tr>
                    )}
                  </Fragment>
                )
              })}
            </tbody>
          </table>
        )}
      </div>

      <DiskPanel
        disco={panel.disco}
        item={panel.item}
        error={panel.error}
        onClose={() => {
          stopAllPreviews()
          setPanel({ disco: null, item: null, error: '' })
        }}
      />
    </div>
  )
}
