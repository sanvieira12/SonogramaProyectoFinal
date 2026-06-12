import { useState, useEffect, useCallback } from 'react'
import { api } from '../api/sonograma'
import { redirectIfUnauthorized } from '../api/session'

const CANAL_LABELS = {
  INSTAGRAM: 'Instagram',
  WHATSAPP: 'WhatsApp',
  MERCADOLIBRE: 'MercadoLibre',
  PRESENCIAL: 'Presencial',
  WEB: 'Web',
  OTRO: 'Otro',
}

const ESTADO_PAGO_STYLES = {
  PAGADO:   'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400',
  PARCIAL:  'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400',
  PENDIENTE:'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
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
  const [filters, setFilters] = useState({ desde: '', hasta: '', canal: '', q: '' })
  const [applied, setApplied] = useState({})

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
    if (filters.canal) params.canal = filters.canal
    if (filters.q) params.q = filters.q
    setApplied(params)
    cargar(params)
  }

  function limpiar() {
    setFilters({ desde: '', hasta: '', canal: '', q: '' })
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
            <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Canal</label>
            <select
              value={filters.canal}
              onChange={e => setFilters(f => ({ ...f, canal: e.target.value }))}
              className="input text-sm"
            >
              <option value="">Todos</option>
              {Object.entries(CANAL_LABELS).map(([k, v]) => (
                <option key={k} value={k}>{v}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs text-slate-500 dark:text-stone-400 mb-1">Buscar</label>
            <input
              type="text"
              placeholder="Cliente, artista, factura…"
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
                {['Factura', 'Fecha', 'Cliente', 'Artista / Álbum', 'Canal', 'Medio Pago', 'Total', 'Estado Pago', 'Ganancia'].map(h => (
                  <th key={h} className="text-left px-4 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider whitespace-nowrap">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-stone-800">
              {loading ? (
                <tr><td colSpan={9} className="px-4 py-12 text-center text-slate-400">Cargando…</td></tr>
              ) : error ? (
                <tr><td colSpan={9} className="px-4 py-12 text-center text-red-500">{error}</td></tr>
              ) : ventas.length === 0 ? (
                <tr><td colSpan={9} className="px-4 py-12 text-center text-slate-400 dark:text-stone-500">No hay ventas</td></tr>
              ) : ventas.map(v => (
                <tr key={v.idVenta} className="hover:bg-slate-50 dark:hover:bg-stone-900/50 transition-colors">
                  <td className="px-4 py-3 font-mono text-xs text-slate-600 dark:text-stone-400 whitespace-nowrap">
                    {v.numeroFactura || '—'}
                  </td>
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
                    {CANAL_LABELS[v.canalVenta] || v.canalVenta || '—'}
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
    </div>
  )
}
