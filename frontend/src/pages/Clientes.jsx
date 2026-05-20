import { useEffect, useMemo, useRef, useState } from 'react'
import { api } from '../api/sonograma'

function Spinner() {
  return (
    <div className="flex items-center justify-center py-24 gap-3">
      <svg className="animate-spin w-5 h-5 text-[#7E9FA8]" viewBox="0 0 24 24" fill="none">
        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
      </svg>
      <span className="text-slate-500 dark:text-stone-400 text-sm">Cargando clientes...</span>
    </div>
  )
}

function formatFecha(iso) {
  if (!iso) return '—'
  const d = new Date(iso)
  return d.toLocaleDateString('es-UY', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

export default function Clientes() {
  const [clientes, setClientes] = useState([])
  const [ventas, setVentas] = useState([])
  const [loading, setLoading] = useState(true)
  const [busqueda, setBusqueda] = useState('')
  const [clienteDetalle, setClienteDetalle] = useState(null)
  const debounceRef = useRef(null)

  useEffect(() => {
    Promise.all([api.clientes.todos(), api.ventas.todas()])
      .then(([cs, vs]) => { setClientes(cs); setVentas(vs) })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  const ventasPorCliente = useMemo(() => {
    const map = {}
    ventas.forEach(v => {
      if (!map[v.idCliente]) map[v.idCliente] = []
      map[v.idCliente].push(v)
    })
    return map
  }, [ventas])

  function onBusquedaChange(e) {
    const q = e.target.value
    setBusqueda(q)
    clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(async () => {
      if (!q.trim()) {
        const data = await api.clientes.todos()
        setClientes(data)
        return
      }
      try {
        const data = await api.clientes.buscar(q.trim())
        setClientes(data)
      } catch { /* ignore */ }
    }, 300)
  }

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 py-6 space-y-5">

      {/* Header */}
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-900 dark:text-white">Clientes</h1>
          <p className="text-slate-400 dark:text-stone-500 text-sm mt-0.5">
            {!loading && `${clientes.length} ${clientes.length === 1 ? 'cliente registrado' : 'clientes registrados'}`}
          </p>
        </div>
      </div>

      {/* Búsqueda */}
      <div className="relative max-w-sm">
        <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 dark:text-stone-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.607 10.607Z" />
        </svg>
        <input
          value={busqueda}
          onChange={onBusquedaChange}
          placeholder="Buscar por nombre o apellido..."
          className="input pl-9"
        />
      </div>

      {/* Tabla */}
      <div className="card overflow-hidden">
        {loading ? (
          <Spinner />
        ) : clientes.length === 0 ? (
          <div className="text-center py-16 text-slate-500 dark:text-stone-400 text-sm">
            No se encontraron clientes
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-100 dark:border-stone-800">
                  <th className="text-left px-5 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider">Nombre</th>
                  <th className="text-left px-5 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider hidden md:table-cell">Cédula</th>
                  <th className="text-left px-5 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider hidden lg:table-cell">Instagram</th>
                  <th className="text-left px-5 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider hidden xl:table-cell">Dirección</th>
                  <th className="text-right px-5 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider">Compras</th>
                  <th className="text-right px-5 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider hidden sm:table-cell">Total gastado</th>
                  <th className="text-right px-5 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider hidden md:table-cell">Última compra</th>
                  <th className="px-5 py-3"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-stone-800/60">
                {clientes.map(c => {
                  const vs = ventasPorCliente[c.idCliente] || []
                  const totalGastado = vs.reduce((sum, v) => sum + Number(v.total || 0), 0)
                  const ultimaCompra = vs.length > 0
                    ? vs.reduce((latest, v) => v.fechaVenta > latest ? v.fechaVenta : latest, vs[0].fechaVenta)
                    : null
                  return (
                    <tr
                      key={c.idCliente}
                      className="hover:bg-slate-50 dark:hover:bg-stone-900/40 transition-colors cursor-pointer"
                      onClick={() => setClienteDetalle(clienteDetalle?.idCliente === c.idCliente ? null : c)}
                    >
                      <td className="px-5 py-4">
                        <div className="font-semibold text-slate-900 dark:text-white">{c.nombre} {c.apellido}</div>
                        <div className="text-slate-400 dark:text-stone-500 text-xs mt-0.5">{c.telefono || '—'}</div>
                      </td>
                      <td className="px-5 py-4 text-slate-600 dark:text-stone-400 font-mono text-xs hidden md:table-cell">{c.cedula || '—'}</td>
                      <td className="px-5 py-4 hidden lg:table-cell">
                        {c.instagramUsuario
                          ? <span className="text-[#5C7D87] dark:text-[#7E9FA8] text-xs">{c.instagramUsuario}</span>
                          : <span className="text-slate-300 dark:text-stone-600">—</span>}
                      </td>
                      <td className="px-5 py-4 text-slate-500 dark:text-stone-400 text-xs hidden xl:table-cell max-w-[200px] truncate">
                        {c.direccion || '—'}
                      </td>
                      <td className="px-5 py-4 text-right">
                        <span className={`inline-flex items-center justify-center w-7 h-7 rounded-full text-xs font-bold ${
                          vs.length > 0
                            ? 'bg-[#7E9FA8]/15 text-[#5C7D87] dark:text-[#7E9FA8]'
                            : 'bg-slate-100 dark:bg-stone-900 text-slate-400 dark:text-stone-600'
                        }`}>
                          {vs.length}
                        </span>
                      </td>
                      <td className="px-5 py-4 text-right font-semibold text-slate-900 dark:text-white tabular-nums hidden sm:table-cell">
                        {totalGastado > 0
                          ? `$${totalGastado.toLocaleString('es-AR', { maximumFractionDigits: 0 })}`
                          : <span className="text-slate-300 dark:text-stone-600 font-normal">—</span>}
                      </td>
                      <td className="px-5 py-4 text-right text-slate-500 dark:text-stone-400 text-xs hidden md:table-cell">
                        {formatFecha(ultimaCompra)}
                      </td>
                      <td className="px-5 py-4 text-right">
                        <svg className={`w-4 h-4 text-slate-300 dark:text-stone-600 transition-transform ${clienteDetalle?.idCliente === c.idCliente ? 'rotate-180' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                          <path strokeLinecap="round" strokeLinejoin="round" d="m19 9-7 7-7-7" />
                        </svg>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Detalle cliente expandido */}
      {clienteDetalle && (
        <div className="card p-5 space-y-4">
          <div className="flex items-start justify-between">
            <div>
              <h2 className="font-bold text-slate-900 dark:text-white text-base">
                {clienteDetalle.nombre} {clienteDetalle.apellido}
              </h2>
              <p className="text-slate-400 dark:text-stone-500 text-sm mt-0.5">{clienteDetalle.email || '—'}</p>
            </div>
            <button
              onClick={() => setClienteDetalle(null)}
              className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-stone-800 transition-colors"
            >
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18 18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-sm">
            <div>
              <p className="text-xs uppercase tracking-wider text-slate-400 dark:text-stone-500 mb-1">Cédula</p>
              <p className="text-slate-700 dark:text-stone-300 font-mono">{clienteDetalle.cedula || '—'}</p>
            </div>
            <div>
              <p className="text-xs uppercase tracking-wider text-slate-400 dark:text-stone-500 mb-1">Teléfono</p>
              <p className="text-slate-700 dark:text-stone-300">{clienteDetalle.telefono || '—'}</p>
            </div>
            <div>
              <p className="text-xs uppercase tracking-wider text-slate-400 dark:text-stone-500 mb-1">Instagram</p>
              <p className="text-[#5C7D87] dark:text-[#7E9FA8]">{clienteDetalle.instagramUsuario || '—'}</p>
            </div>
            <div>
              <p className="text-xs uppercase tracking-wider text-slate-400 dark:text-stone-500 mb-1">Alta</p>
              <p className="text-slate-700 dark:text-stone-300">{formatFecha(clienteDetalle.fechaAlta)}</p>
            </div>
          </div>

          {clienteDetalle.direccion && (
            <div>
              <p className="text-xs uppercase tracking-wider text-slate-400 dark:text-stone-500 mb-1">Dirección</p>
              <p className="text-slate-700 dark:text-stone-300 text-sm">{clienteDetalle.direccion}</p>
            </div>
          )}

          {clienteDetalle.observaciones && (
            <div>
              <p className="text-xs uppercase tracking-wider text-slate-400 dark:text-stone-500 mb-1">Observaciones</p>
              <p className="text-slate-600 dark:text-stone-400 text-sm italic">{clienteDetalle.observaciones}</p>
            </div>
          )}

          {/* Historial de compras */}
          {(() => {
            const vs = ventasPorCliente[clienteDetalle.idCliente] || []
            if (vs.length === 0) return (
              <p className="text-slate-400 dark:text-stone-600 text-sm">Sin compras registradas.</p>
            )
            return (
              <div>
                <p className="text-xs uppercase tracking-wider text-slate-400 dark:text-stone-500 mb-2">Historial de compras</p>
                <div className="space-y-2">
                  {vs.sort((a, b) => b.fechaVenta?.localeCompare(a.fechaVenta)).map(v => (
                    <div key={v.idVenta} className="flex items-center justify-between py-2 border-b border-slate-100 dark:border-stone-800 last:border-0">
                      <div>
                        <span className="font-medium text-slate-800 dark:text-stone-200 text-sm">{v.artista} — {v.album}</span>
                        <span className="ml-2 text-xs text-slate-400 dark:text-stone-500">{formatFecha(v.fechaVenta)}</span>
                      </div>
                      <span className="font-semibold text-slate-900 dark:text-white tabular-nums text-sm">
                        ${Number(v.total).toLocaleString('es-AR', { maximumFractionDigits: 0 })}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )
          })()}
        </div>
      )}
    </div>
  )
}
