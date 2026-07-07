import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell
} from 'recharts'
import { api } from '../api/sonograma'
import { useTheme } from '../context/useTheme'

const ESTADO_COLORS = {
  DISPONIBLE: '#5B8C7D',
  RESERVADO:  '#B8975E',
  VENDIDO:    '#6B7280',
  SIN_STOCK:  '#94a3b8',
}

const GRAFICAS = [
  { key: 'inventarioPorEstado',      label: 'Inventario por estado',          unidad: 'discos' },
  { key: 'inventarioPorGenero',      label: 'Inventario por género',           unidad: 'discos' },
  { key: 'inventarioPorAnio',        label: 'Inventario por año',              unidad: 'discos' },
  { key: 'inventarioPorSello',       label: 'Inventario por sello',            unidad: 'discos' },
  { key: 'generosMasVendidos',       label: 'Géneros más vendidos',            unidad: 'ventas' },
  { key: 'aniosMusicaMasVendidos',   label: 'Años de música más vendidos',     unidad: 'ventas' },
  { key: 'artistasMasVendidos',      label: 'Top artistas vendidos',           unidad: 'ventas' },
  { key: 'sellosMasVendidos',        label: 'Top sellos vendidos',             unidad: 'ventas' },
  { key: 'ventasPorSemana',          label: 'Ventas por semana',               unidad: 'ventas' },
  { key: 'ventasPorMes',             label: 'Ventas por mes',                  unidad: 'ventas' },
  { key: 'ventasPorAnio',            label: 'Ventas por año',                  unidad: 'ventas' },
  { key: 'gananciaPorMes',           label: 'Ganancia por mes',                unidad: 'UYU'   },
]

const ESTADO_PAGO_STYLE = {
  PAGADO:    { bg: 'bg-emerald-50 dark:bg-emerald-900/20', text: 'text-emerald-700 dark:text-emerald-400' },
  PARCIAL:   { bg: 'bg-amber-50 dark:bg-amber-900/20',     text: 'text-amber-700 dark:text-amber-400' },
  PENDIENTE: { bg: 'bg-red-50 dark:bg-red-900/20',         text: 'text-red-600 dark:text-red-400' },
}

function StatCard({ label, value, sublabel, color, icon }) {
  return (
    <div className="card p-5 flex items-start gap-4">
      <div className={`w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0 ${color}`}>
        {icon}
      </div>
      <div>
        <div className="text-2xl font-bold text-slate-900 dark:text-white tabular-nums">{value}</div>
        <div className="text-slate-600 dark:text-stone-400 text-sm font-medium mt-0.5">{label}</div>
        {sublabel && <div className="text-slate-400 dark:text-stone-600 text-xs mt-0.5">{sublabel}</div>}
      </div>
    </div>
  )
}

export default function Dashboard() {
  const { dark } = useTheme()
  const navigate = useNavigate()
  const [discos, setDiscos] = useState([])
  const [loading, setLoading] = useState(true)
  const [ventasPorMes, setVentasPorMes] = useState([])
  const [estadisticas, setEstadisticas] = useState(null)
  const [gastosMesActual, setGastosMesActual] = useState(0)
  const [graficaSeleccionada, setGraficaSeleccionada] = useState('inventarioPorEstado')
  const [ultimasVentas, setUltimasVentas] = useState([])
  const [paginaVentas, setPaginaVentas] = useState(1)
  const VENTAS_POR_PAGINA = 10

  useEffect(() => {
    let cancelado = false
    api.discos.todos()
      .then(data => { if (!cancelado) setDiscos(data) })
      .catch(() => { if (!cancelado) setDiscos([]) })
      .finally(() => { if (!cancelado) setLoading(false) })
    return () => { cancelado = true }
  }, [])

  useEffect(() => {
    api.ventas.estadisticasPorMes()
      .then(setVentasPorMes)
      .catch(() => setVentasPorMes([]))
    api.estadisticas.catalogo()
      .then(setEstadisticas)
      .catch(() => setEstadisticas(null))
    api.libro.listar({})
      .then(data => setUltimasVentas(Array.isArray(data) ? data : []))
      .catch(() => setUltimasVentas([]))
    api.gastosTienda.resumen()
      .then(data => setGastosMesActual(Number(data?.totalMesActual || 0)))
      .catch(() => setGastosMesActual(0))
  }, [])

  const stats = {
    disponibles: discos
      .filter(d => d.estado === 'DISPONIBLE')
      .reduce((sum, d) => sum + Number(d.cantidadCopias ?? 1), 0),
  }

  const valorTotal = discos
    .filter(d => d.estado === 'DISPONIBLE' && d.precioVenta)
    .reduce((sum, d) => sum + Number(d.precioVenta) * Number(d.cantidadCopias ?? 1), 0)

  // Venta total del mes actual usando la clave "YYYY-MM" de estadisticas.ventasPorMes
  const mesActual = (() => {
    const now = new Date()
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  })()
  const ventaMes = estadisticas?.ventasPorMes?.find(v => v.clave === mesActual)
  const ventaTotalMes = ventaMes ? Number(ventaMes.totalMonto || 0) : 0
  const semanaActual = (() => {
    const date = new Date()
    const utc = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()))
    const day = utc.getUTCDay() || 7
    utc.setUTCDate(utc.getUTCDate() + 4 - day)
    const yearStart = new Date(Date.UTC(utc.getUTCFullYear(), 0, 1))
    const week = Math.ceil((((utc - yearStart) / 86400000) + 1) / 7)
    return `${utc.getUTCFullYear()}-S${String(week).padStart(2, '0')}`
  })()
  const ventaSemana = estadisticas?.ventasPorSemana?.find(v => v.clave === semanaActual)
  const ventaTotalSemana = Number(ventaSemana?.totalMonto || 0)
  const graficaActual = GRAFICAS.find(g => g.key === graficaSeleccionada) || GRAFICAS[0]
  const datosGrafica = (estadisticas?.[graficaActual.key] || []).slice(0, 12).map((item, i) => ({
    etiqueta: item.etiqueta,
    cantidad: graficaActual.unidad === 'UYU'
      ? Number(item.gananciaEstimada || 0)
      : Number(item.cantidad || 0),
    fill: ESTADO_COLORS[item.clave] || ['#7E9FA8', '#5B8C7D', '#B8975E', '#A66363', '#6B7280'][i % 5],
  }))

  const axisColor = dark ? '#78716c' : '#94a3b8'
  const gridColor = dark ? '#1c1917' : '#f1f5f9'

  const ventasPagina = ultimasVentas.slice((paginaVentas - 1) * VENTAS_POR_PAGINA, paginaVentas * VENTAS_POR_PAGINA)
  const totalPaginasVentas = Math.ceil(ultimasVentas.length / VENTAS_POR_PAGINA)

  function fmtDate(fechaStr) {
    if (!fechaStr) return '—'
    return new Date(fechaStr).toLocaleDateString('es-UY', { day: '2-digit', month: '2-digit', year: '2-digit' })
  }

  function fmtMonto(monto) {
    if (!monto && monto !== 0) return '—'
    return `UYU $${Number(monto).toLocaleString('es-UY', { maximumFractionDigits: 0 })}`
  }

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 py-6 space-y-6">

      {/* Stats */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <StatCard
          label="Ventas del mes"
          value={fmtMonto(ventaTotalMes)}
          sublabel={ventaMes ? `${ventaMes.cantidad} ventas` : 'Sin ventas este mes'}
          color="bg-[#7E9FA8]/15"
          icon={
            <svg className="w-5 h-5 text-[#5C7D87] dark:text-[#7E9FA8]" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 18.75a60.07 60.07 0 0 1 15.797 2.101c.727.198 1.453-.342 1.453-1.096V18.75M3.75 4.5v.75A.75.75 0 0 1 3 6h-.75m0 0v-.375c0-.621.504-1.125 1.125-1.125H20.25M2.25 6v9m18-10.5v.75c0 .414.336.75.75.75h.75m-1.5-1.5h.375c.621 0 1.125.504 1.125 1.125v9.75c0 .621-.504 1.125-1.125 1.125h-.375m1.5-1.5H21a.75.75 0 0 0-.75.75v.75m0 0H3.75m0 0h-.375a1.125 1.125 0 0 1-1.125-1.125V15m1.5 1.5v-.75A.75.75 0 0 0 3 15h-.75M15 10.5a3 3 0 1 1-6 0 3 3 0 0 1 6 0Zm3 0h.008v.008H18V10.5Zm-12 0h.008v.008H6V10.5Z" />
            </svg>
          }
        />
        <StatCard
          label="Disponibles"
          value={loading ? '…' : stats.disponibles}
          sublabel={!loading && valorTotal > 0 ? `${fmtMonto(valorTotal)} en stock` : undefined}
          color="bg-emerald-50 dark:bg-emerald-900/20"
          icon={
            <svg className="w-5 h-5 text-[#5B8C7D]" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75 11.25 15 15 9.75M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
            </svg>
          }
        />
        <StatCard
          label="Ventas esta semana"
          value={fmtMonto(ventaTotalSemana)}
          sublabel={ventaSemana ? `${ventaSemana.cantidad} ventas` : 'Sin ventas esta semana'}
          color="bg-amber-50 dark:bg-amber-900/20"
          icon={
            <svg className="w-5 h-5 text-[#B8975E]" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
            </svg>
          }
        />
        <StatCard
          label="Gastos de tienda del mes"
          value={fmtMonto(gastosMesActual)}
          sublabel="Egresos registrados"
          color="bg-slate-100 dark:bg-stone-900"
          icon={
            <svg className="w-5 h-5 text-[#6B7280]" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 9l10.5-3m0 6.553v3.75a2.25 2.25 0 01-1.632 2.163l-1.32.377a1.803 1.803 0 11-.99-3.467l2.31-.66a2.25 2.25 0 001.632-2.163zm0 0V2.25L9 5.25v10.303m0 0v3.75a2.25 2.25 0 01-1.632 2.163l-1.32.377a1.803 1.803 0 01-.99-3.467l2.31-.66A2.25 2.25 0 009 15.553z" />
            </svg>
          }
        />
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="card p-5">
          <div className="flex items-center justify-between gap-3 mb-4">
            <h3 className="text-sm font-semibold text-slate-700 dark:text-stone-300">{graficaActual.label}</h3>
            <select
              value={graficaSeleccionada}
              onChange={e => setGraficaSeleccionada(e.target.value)}
              className="input max-w-[230px] py-1.5 text-xs"
            >
              {GRAFICAS.map(g => <option key={g.key} value={g.key}>{g.label}</option>)}
            </select>
          </div>
          {datosGrafica.length === 0 ? (
            <div className="h-[180px] flex items-center justify-center text-slate-400 dark:text-stone-600 text-sm">
              Sin datos suficientes
            </div>
          ) : (
            <ResponsiveContainer width="100%" height={180}>
              <BarChart data={datosGrafica} margin={{ top: 0, right: 0, left: -20, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke={gridColor} vertical={false} />
                <XAxis dataKey="etiqueta" tick={{ fontSize: 10, fill: axisColor }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 11, fill: axisColor }} axisLine={false} tickLine={false} allowDecimals={false} />
                <Tooltip
                  contentStyle={{
                    background: dark ? '#0c0a09' : '#fff',
                    border: `1px solid ${dark ? '#292524' : '#e2e8f0'}`,
                    borderRadius: '0.5rem',
                    fontSize: '12px',
                  }}
                  formatter={(value) => graficaActual.unidad === 'UYU'
                    ? [`$ ${Number(value).toLocaleString('es-UY')}`, 'Ganancia']
                    : [`${value} ${graficaActual.unidad}`, 'Cantidad']
                  }
                />
                <Bar dataKey="cantidad" radius={[4, 4, 0, 0]} maxBarSize={48}>
                  {datosGrafica.map((entry, i) => (
                    <Cell key={i} fill={entry.fill} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>

        <div className="card p-5">
          <div className="flex items-center justify-between mb-1">
            <h3 className="text-sm font-semibold text-slate-700 dark:text-stone-300">Ventas por mes</h3>
            <span className="text-xs text-slate-400 dark:text-stone-600">
              {ventasPorMes.length} {ventasPorMes.length === 1 ? 'mes' : 'meses'} con ventas
            </span>
          </div>
          {ventasPorMes.length === 0 ? (
            <div className="h-[180px] flex items-center justify-center text-slate-400 dark:text-stone-600 text-sm">
              Sin ventas registradas
            </div>
          ) : (
            <ResponsiveContainer width="100%" height={180}>
              <BarChart data={ventasPorMes} margin={{ top: 0, right: 0, left: -20, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke={gridColor} vertical={false} />
                <XAxis dataKey="etiqueta" tick={{ fontSize: 10, fill: axisColor }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 11, fill: axisColor }} axisLine={false} tickLine={false} allowDecimals={false} />
                <Tooltip
                  contentStyle={{
                    background: dark ? '#0c0a09' : '#fff',
                    border: `1px solid ${dark ? '#292524' : '#e2e8f0'}`,
                    borderRadius: '0.5rem',
                    fontSize: '12px',
                  }}
                  formatter={(value, name) => name === 'cantidad'
                    ? [`${value} ventas`, 'Cantidad']
                    : [`$ ${Number(value).toLocaleString('es-UY')}`, 'Monto']
                  }
                />
                <Bar dataKey="cantidad" fill="#7E9FA8" radius={[4, 4, 0, 0]} maxBarSize={40} name="cantidad" />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>

      {/* Libro de ventas resumido */}
      <div className="card overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-100 dark:border-stone-800 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-slate-700 dark:text-stone-300">Libro de ventas</h2>
          <button
            onClick={() => navigate('/libro-ventas')}
            className="text-xs text-[#5C7D87] dark:text-[#7E9FA8] hover:underline font-medium"
          >
            Ver todo →
          </button>
        </div>

        {ultimasVentas.length === 0 ? (
          <div className="py-12 text-center text-slate-400 dark:text-stone-600 text-sm">
            Sin ventas registradas
          </div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-100 dark:border-stone-800">
                    <th className="text-left px-5 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider hidden sm:table-cell">Fecha</th>
                    <th className="text-left px-5 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider">Cliente</th>
                    <th className="text-left px-5 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider hidden md:table-cell">Artista / Álbum</th>
                    <th className="text-right px-5 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider">Total</th>
                    <th className="text-left px-5 py-3 text-xs font-semibold text-slate-500 dark:text-stone-500 uppercase tracking-wider">Pago</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-stone-800/60">
                  {ventasPagina.map(v => {
                    const ep = ESTADO_PAGO_STYLE[v.estadoPago] || ESTADO_PAGO_STYLE.PENDIENTE
                    const nombreCliente = v.clienteNombreSnapshot
                      || `${v.nombreCliente || ''} ${v.apellidoCliente || ''}`.trim()
                      || '—'
                    return (
                      <tr
                        key={v.idVenta}
                        onClick={() => navigate('/libro-ventas')}
                        className="hover:bg-slate-50 dark:hover:bg-stone-900/40 transition-colors cursor-pointer"
                      >
                        <td className="px-5 py-3 text-slate-500 dark:text-stone-500 text-xs hidden sm:table-cell">{fmtDate(v.fechaVenta)}</td>
                        <td className="px-5 py-3 text-slate-700 dark:text-stone-300 font-medium">{nombreCliente}</td>
                        <td className="px-5 py-3 text-slate-500 dark:text-stone-400 text-xs hidden md:table-cell">
                          {v.artista ? `${v.artista}${v.album ? ` — ${v.album}` : ''}` : '—'}
                        </td>
                        <td className="px-5 py-3 text-right font-semibold text-slate-900 dark:text-white tabular-nums">{fmtMonto(v.totalFinal)}</td>
                        <td className="px-5 py-3">
                          <span className={`inline-flex text-xs px-2 py-0.5 rounded-full font-medium ${ep.bg} ${ep.text}`}>
                            {v.estadoPago === 'PAGADO' ? 'Pagado' : v.estadoPago === 'PARCIAL' ? 'Parcial' : 'Pendiente'}
                          </span>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>

            {totalPaginasVentas > 1 && (
              <div className="px-5 py-3 border-t border-slate-100 dark:border-stone-800 flex items-center gap-2">
                <button
                  onClick={() => setPaginaVentas(p => Math.max(1, p - 1))}
                  disabled={paginaVentas === 1}
                  className="px-2.5 py-1 rounded-lg text-xs bg-slate-100 dark:bg-stone-900 text-slate-600 dark:text-stone-400 disabled:opacity-40"
                >←</button>
                {Array.from({ length: totalPaginasVentas }, (_, i) => i + 1).map(p => (
                  <button
                    key={p}
                    onClick={() => setPaginaVentas(p)}
                    className={`px-2.5 py-1 rounded-lg text-xs font-medium transition-colors ${
                      paginaVentas === p
                        ? 'bg-[#7E9FA8] text-white'
                        : 'bg-slate-100 dark:bg-stone-900 text-slate-600 dark:text-stone-400 hover:bg-slate-200 dark:hover:bg-stone-800'
                    }`}
                  >{p}</button>
                ))}
                <button
                  onClick={() => setPaginaVentas(p => Math.min(totalPaginasVentas, p + 1))}
                  disabled={paginaVentas === totalPaginasVentas}
                  className="px-2.5 py-1 rounded-lg text-xs bg-slate-100 dark:bg-stone-900 text-slate-600 dark:text-stone-400 disabled:opacity-40"
                >→</button>
                <span className="text-xs text-slate-400 dark:text-stone-600 ml-1">
                  {ultimasVentas.length} ventas total
                </span>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
